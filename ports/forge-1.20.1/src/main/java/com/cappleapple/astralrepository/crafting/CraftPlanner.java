package com.cappleapple.astralrepository.crafting;

import java.util.*;
import java.util.function.Function;

/** Bounded dependency search. Failed alternatives never retain inventory claims or partial nodes. */
public final class CraftPlanner<K> {
    private final Map<K, List<CraftRecipe<K>>> recipes = new HashMap<>();
    private final int maxNodes;
    private final int maxDepth;
    private final int searchBudget;
    private record SearchKey<K>(K item, Set<String> processes) {}
    private final Map<SearchKey<K>, List<CraftRecipe<K>>> candidateCache = new HashMap<>();
    private final Map<SearchKey<K>, Set<K>> relevantCache = new HashMap<>();
    private int attempts;
    private List<CraftRecipe<K>> candidates(K key,Set<String> processes) {
        return candidateCache.computeIfAbsent(new SearchKey<>(key,Set.copyOf(processes)), ignored ->
            recipes.getOrDefault(key,List.of()).stream().filter(r->processes.contains(r.process()))
                .sorted(Comparator.<CraftRecipe<K>>comparingInt(CraftRecipe::priority).reversed().thenComparingInt(CraftRecipe::durationTicks).thenComparing(CraftRecipe::id)).toList());
    }
    public synchronized int cachedSearches(){return candidateCache.size();}
    private int activeSearch;
    public CraftPlanner(Collection<CraftRecipe<K>> recipes) { this(recipes, 8192, 128, 100000); }
    public CraftPlanner(Collection<CraftRecipe<K>> recipes, int maxNodes, int maxDepth, int searchBudget) {
        this.maxNodes = maxNodes; this.maxDepth = maxDepth; this.searchBudget = searchBudget;
        for (CraftRecipe<K> recipe : recipes)
            this.recipes.computeIfAbsent(recipe.output(), ignored -> new ArrayList<>()).add(recipe);
    }
    public synchronized CraftPlan<K> plan(K target, long count, Map<K, Long> inventory, Set<String> processes) {
        return plan(target,count,inventory,processes,true);
    }
    /** Produce a net increase; only declared guaranteed-growth recipes may reuse owned target seeds. */
    public synchronized CraftPlan<K> planAdditional(K target,long count,Map<K,Long> inventory,Set<String> processes) {
        return plan(target,count,inventory,processes,false);
    }
    private CraftPlan<K> plan(K target,long count,Map<K,Long> inventory,Set<String> processes,boolean useExistingTarget) {
        if (count < 1) throw new IllegalArgumentException("Request quantity must be positive");
        Set<K> relevant = relevantCache.computeIfAbsent(new SearchKey<>(target,Set.copyOf(processes)), ignored -> relevantKeys(target, processes));
        State initial = new State();
        inventory.forEach((key, amount) -> {
            if (amount > 0 && relevant.contains(key)) initial.supplies.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Supply(amount, -1));
        });
        attempts = 0; activeSearch = 0;
        State outcome = fulfill(target, count, initial, new LinkedHashSet<>(), processes, result -> result.state, useExistingTarget);
        return new CraftPlan<>(target, count, outcome.reservations, outcome.nodes, !useExistingTarget);
    }
    private Set<K> relevantKeys(K target, Set<String> processes) {
        Set<K> result = new HashSet<>();
        ArrayDeque<K> pending = new ArrayDeque<>();
        result.add(target); pending.add(target);
        while (!pending.isEmpty()) {
            K key = pending.removeFirst();
            for (CraftRecipe<K> recipe : candidates(key, processes)) {
                if (!processes.contains(recipe.process())) continue;
                for (var ingredient : recipe.ingredients()) for (K alternative : ingredient.alternatives()) {
                    if (result.add(alternative)) pending.addLast(alternative);
                    if (result.size() > searchBudget) throw new PlanningException("Recipe knowledge exceeds the search budget", true);
                }
            }
        }
        return result;
    }
    private State fulfill(K key, long count, State initial, LinkedHashSet<K> path, Set<String> processes, Function<Outcome, State> continuation) {
        return fulfill(key,count,initial,path,processes,continuation,true);
    }
    private State fulfill(K key,long count,State initial,LinkedHashSet<K> path,Set<String> processes,Function<Outcome,State> continuation,boolean useExisting) {
        return fulfill(key, count, initial, path, processes, continuation, useExisting, Set.of());
    }
    private State fulfill(K key,long count,State initial,LinkedHashSet<K> path,Set<String> processes,
                          Function<Outcome,State> continuation,boolean useExisting,Set<String> usedRecipes) {
        budget();
        if (++activeSearch > 192) { activeSearch--; throw new PlanningException("Craft search is too complex; request a smaller batch", true); }
        try {
            // A stored ancestor cannot bootstrap a compression/decompression loop.
            // Checking before consumption also guarantees net additional final output.
            if (path.contains(key)) throw new PlanningException("Cyclic production path: " + path + " -> " + key);
            State state = new State(initial);
            Set<Integer> dependencies = new LinkedHashSet<>();
            long missing = useExisting ? consume(key, count, state, dependencies, true) : count;
            if (missing == 0) return continuation.apply(new Outcome(state, dependencies));
            if (path.size() >= maxDepth) throw new PlanningException("Recipe depth exceeds " + maxDepth);
            LinkedHashSet<K> nextPath = new LinkedHashSet<>(path);
            nextPath.add(key);
            List<CraftRecipe<K>> candidates = new ArrayList<>(candidates(key, processes));
            candidates.sort(Comparator.<CraftRecipe<K>>comparingInt(CraftRecipe::priority).reversed()
                    .thenComparingLong(recipe -> missingScore(recipe, state))
                    .thenComparingInt(CraftRecipe::durationTicks).thenComparing(CraftRecipe::id));
            String failure = "Missing ingredient or available processor for " + key + " x" + missing;
            for (CraftRecipe<K> recipe : candidates) {
                if (usedRecipes.contains(recipe.id())) continue;
                try {
                    if (recipe.returnedSeedCount() > 0) {
                        long credit = initial.supplies.getOrDefault(key, List.of()).stream().filter(supply -> supply.credit).mapToLong(supply -> supply.count).sum();
                        long uncredited = available(key, initial) - credit;
                        long borrowedCredit = Math.max(0, recipe.returnedSeedCount() - uncredited);
                        long required = useExisting ? Math.max(0, count - available(key, initial)) : Math.max(0, Math.addExact(count - credit, borrowedCredit));
                        long operations = required == 0 ? 0 : Math.floorDiv(required - 1, recipe.outputCount()) + 1;
                        if (operations > maxNodes - initial.nodes.size()) throw new PlanningException("Craft plan exceeds " + maxNodes + " operations");
                        return resolveOperations(recipe, operations, new State(initial), nextPath, processes, branch -> {
                            Set<Integer> producedBy = new LinkedHashSet<>();
                            if (consume(key, count, branch, producedBy, useExisting) != 0) throw new PlanningException("Seed production did not supply the requested net output");
                            return continuation.apply(new Outcome(branch, producedBy));
                        });
                    }
                    long operations = Math.floorDiv(missing - 1, recipe.outputCount()) + 1;
                    if (operations > maxNodes - state.nodes.size()) throw new PlanningException("Craft plan exceeds " + maxNodes + " operations");
                    State batch = new State(state);
                    long completed = 0;
                    for (; completed < operations; completed++) {
                        budget();
                        try {
                            batch = resolveIngredients(recipe, 0, batch, nextPath, processes,
                                    new ArrayList<>(), new LinkedHashSet<>(), Function.identity());
                        } catch (PlanningException error) {
                            if (error.exhausted) throw error;
                            failure = error.getMessage();
                            break;
                        }
                    }
                    Set<String> nextUsedRecipes = new HashSet<>(usedRecipes);
                    nextUsedRecipes.add(recipe.id());
                    Function<State, State> finish = branch -> finishProduction(key, missing, branch, dependencies,
                            path, processes, continuation, useExisting, nextUsedRecipes);
                    if (completed == operations) {
                        try { return finish.apply(batch); }
                        catch (PlanningException error) { if (error.exhausted) throw error; failure = error.getMessage(); }
                        try { return resolveOperations(recipe, operations, new State(state), nextPath, processes, finish); }
                        catch (PlanningException error) { if (error.exhausted) throw error; failure = error.getMessage(); }
                    }
                    // Keep the useful prefix when an input runs out, then search again for
                    // the remaining output. This spends stored blocks before using ore.
                    for (long prefix = Math.min(completed, operations - 1); prefix > 0; prefix--) {
                        if (prefix == completed) {
                            try { return finish.apply(new State(batch)); }
                            catch (PlanningException error) { if (error.exhausted) throw error; failure = error.getMessage(); }
                        }
                        try { return resolveOperations(recipe, prefix, new State(state), nextPath, processes, finish); }
                        catch (PlanningException error) { if (error.exhausted) throw error; failure = error.getMessage(); }
                    }
                } catch (PlanningException error) {
                    if (error.exhausted) throw error;
                    failure = error.getMessage();
                }
            }
            throw new PlanningException(failure);
        } finally { activeSearch--; }
    }
    private State finishProduction(K key, long missing, State initial, Set<Integer> dependencies,
                                   LinkedHashSet<K> path, Set<String> processes,
                                   Function<Outcome, State> continuation, boolean useExisting, Set<String> usedRecipes) {
        State state = new State(initial);
        Set<Integer> producedBy = new LinkedHashSet<>(dependencies);
        long remaining = consume(key, missing, state, producedBy, useExisting);
        if (remaining == 0) return continuation.apply(new Outcome(state, producedBy));
        if (available(key, initial) > 0 && candidates(key, processes).stream().anyMatch(recipe -> recipe.returnedSeedCount() > 0 && !usedRecipes.contains(recipe.id())))
            return fulfill(key, missing, new State(initial), path, processes, continuation, useExisting, usedRecipes);
        if (remaining == missing) throw new PlanningException("Recipe did not supply its declared output");
        return fulfill(key, remaining, state, path, processes, outcome -> {
            Set<Integer> allDependencies = new LinkedHashSet<>(producedBy);
            allDependencies.addAll(outcome.dependencies);
            return continuation.apply(new Outcome(outcome.state, allDependencies));
        }, useExisting, usedRecipes);
    }
    private State resolveOperations(CraftRecipe<K> recipe, long operations, State state, LinkedHashSet<K> path,
                                    Set<String> processes, Function<State, State> continuation) {
        if (operations == 0) return continuation.apply(state);
        // Repeated operations use bounded stack depth, including recursively crafted inputs.
        // If the greedy sequence blocks a later input, retry with the full backtracking continuation.
        try{
            State batch=new State(state);
            for(long operation=0;operation<operations;operation++){
                budget();
                batch=resolveIngredients(recipe,0,batch,path,processes,new ArrayList<>(),new LinkedHashSet<>(),Function.identity());
            }
            return continuation.apply(batch);
        }catch(PlanningException error){if(error.exhausted)throw error;}
        return resolveIngredients(recipe, 0, state, path, processes, new ArrayList<>(), new LinkedHashSet<>(),
                next -> resolveOperations(recipe, operations - 1, next, path, processes, continuation));
    }
    private State resolveIngredients(CraftRecipe<K> recipe, int index, State state, LinkedHashSet<K> path,
                                     Set<String> processes, List<CraftPlan.Selection<K>> selected, Set<Integer> dependencies,
                                     Function<State, State> continuation) {
        budget();
        if (index == recipe.ingredients().size()) {
            if (state.nodes.size() >= maxNodes) throw new PlanningException("Craft plan exceeds " + maxNodes + " operations");
            recordProduction(recipe, selected, state);
            int id = state.nodes.size();
            state.nodes.add(new CraftPlan.Node<>(id, recipe, selected, dependencies));
            state.supplies.computeIfAbsent(recipe.output(), ignored -> new ArrayList<>()).add(new Supply(recipe.outputCount(), id));
            if (recipe.returnedSeedCount() > 0) state.supplies.get(recipe.output()).add(new Supply(recipe.returnedSeedCount(), id, false));
            return continuation.apply(state);
        }
        CraftRecipe.Ingredient<K> ingredient = recipe.ingredients().get(index);
        List<K> alternatives = new ArrayList<>(ingredient.alternatives());
        alternatives.sort(Comparator.<K>comparingLong(key -> available(key, state)).reversed());
        String failure = "No usable alternative for " + ingredient.alternatives();
        for (K alternative : alternatives) {
            try {
                if (recipe.returnedSeedCount() > 0 && Objects.equals(alternative, recipe.output())) {
                    State branch = new State(state); Set<Integer> seedDependencies = new LinkedHashSet<>(dependencies);
                    if (consumeSeed(alternative, ingredient.count(), branch, seedDependencies) != 0) throw new PlanningException("Missing initial seed for " + alternative);
                    List<CraftPlan.Selection<K>> nextSelected = new ArrayList<>(selected); nextSelected.add(new CraftPlan.Selection<>(alternative, ingredient.count()));
                    return resolveIngredients(recipe, index + 1, branch, path, processes, nextSelected, seedDependencies, continuation);
                }
                rejectProductionCycle(recipe.output(), alternative, state);
                return fulfill(alternative, ingredient.count(), state, path, processes, outcome -> {
                    List<CraftPlan.Selection<K>> nextSelected = new ArrayList<>(selected);
                    nextSelected.add(new CraftPlan.Selection<>(alternative, ingredient.count()));
                    Set<Integer> nextDependencies = new LinkedHashSet<>(dependencies);
                    nextDependencies.addAll(outcome.dependencies);
                    return resolveIngredients(recipe, index + 1, outcome.state, path, processes, nextSelected, nextDependencies, continuation);
                });
            } catch (PlanningException error) {
                if (error.exhausted) throw error;
                failure = error.getMessage();
            }
        }
        throw new PlanningException(failure);
    }
    private void rejectProductionCycle(K output, K input, State state) {
        if (state.productionInputs.getOrDefault(output, Set.of()).contains(input)) return;
        Set<K> visited = new HashSet<>();
        ArrayDeque<K> pending = new ArrayDeque<>();
        pending.add(input);
        while (!pending.isEmpty()) {
            budget();
            K key = pending.removeFirst();
            if (Objects.equals(key, output)) throw new PlanningException("Cyclic production path through " + output);
            if (visited.add(key)) pending.addAll(state.productionInputs.getOrDefault(key, Set.of()));
        }
    }
    private void recordProduction(CraftRecipe<K> recipe, List<CraftPlan.Selection<K>> selected, State state) {
        K output = recipe.output();
        Set<K> inputs = new LinkedHashSet<>(state.productionInputs.getOrDefault(output, Set.of()));
        for (var selection : selected) {
            K input = selection.key();
            if (recipe.returnedSeedCount() > 0 && Objects.equals(input, output)) continue;
            if (inputs.contains(input)) continue;
            rejectProductionCycle(output, input, state);
            inputs.add(input);
        }
        state.productionInputs.put(output, Set.copyOf(inputs));
    }
    private long missingScore(CraftRecipe<K> recipe, State state) {
        long total = 0;
        for (var ingredient : recipe.ingredients()) {
            long stock = ingredient.alternatives().stream().mapToLong(key -> available(key, state)).max().orElse(0);
            total += Math.max(0, ingredient.count() - stock);
        }
        return total;
    }
    private long available(K key, State state) {
        return state.supplies.getOrDefault(key, List.of()).stream().mapToLong(supply -> supply.count).sum();
    }
    private long consume(K key, long count, State state, Set<Integer> dependencies, boolean useExisting) {
        long remaining = count;
        for (Supply supply : state.supplies.getOrDefault(key, List.of())) {
            if (!useExisting && !supply.credit) continue;
            long take = Math.min(remaining, supply.count);
            if (take == 0) continue;
            supply.count -= take; remaining -= take;
            if (supply.producer < 0) state.reservations.merge(key, take, Math::addExact);
            else dependencies.add(supply.producer);
            if (remaining == 0) break;
        }
        return remaining;
    }
    private long consumeSeed(K key, long count, State state, Set<Integer> dependencies) {
        var supplies = new ArrayList<>(state.supplies.getOrDefault(key, List.of()));
        supplies.sort(Comparator.comparingInt(supply -> !supply.credit && supply.producer >= 0 ? 0 : supply.producer < 0 ? 1 : 2));
        long remaining = count;
        for (Supply supply : supplies) {
            long taken = Math.min(remaining, supply.count); if (taken <= 0) continue;
            supply.count -= taken; remaining -= taken;
            if (supply.producer < 0) state.reservations.merge(key, taken, Math::addExact); else dependencies.add(supply.producer);
            if (remaining == 0) break;
        }
        return remaining;
    }
    private void budget() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        if (++attempts > searchBudget) throw new PlanningException("Recipe search budget exceeded; simplify the requested batch", true);
    }
    private final class State {
        final Map<K, List<Supply>> supplies = new LinkedHashMap<>();
        final Map<K, Long> reservations = new LinkedHashMap<>();
        final Map<K, Set<K>> productionInputs = new HashMap<>();
        final List<CraftPlan.Node<K>> nodes = new ArrayList<>();
        State() {}
        State(State source) {
            source.supplies.forEach((key, supplies) -> this.supplies.put(key, new ArrayList<>(supplies.stream().map(Supply::new).toList())));
            reservations.putAll(source.reservations); productionInputs.putAll(source.productionInputs); nodes.addAll(source.nodes);
        }
    }
    private static final class Supply {
        long count; final int producer; final boolean credit;
        Supply(long count, int producer) { this(count, producer, producer >= 0); }
        Supply(long count, int producer, boolean credit) { this.count = count; this.producer = producer; this.credit = credit; }
        Supply(Supply source) { this(source.count, source.producer, source.credit); }
    }
    private final class Outcome {
        final State state; final Set<Integer> dependencies;
        Outcome(State state, Set<Integer> dependencies) { this.state = state; this.dependencies = dependencies; }
    }
    public static final class PlanningException extends RuntimeException {
        private final boolean exhausted;
        public PlanningException(String message) { this(message, false); }
        private PlanningException(String message, boolean exhausted) { super(message); this.exhausted = exhausted; }
    }
}
