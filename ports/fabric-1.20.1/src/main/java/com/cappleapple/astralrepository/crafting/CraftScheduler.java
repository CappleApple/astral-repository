package com.cappleapple.astralrepository.crafting;

import java.util.*;

/** Server-thread DAG scheduler with item escrow, processor ownership and recoverable cancellation. */
public final class CraftScheduler<K, P> {
    public enum State { WAITING, RUNNING, COMPLETE, CANCELLED, FAILED }
    public interface Processor<K, P> {
        boolean supports(P processor, CraftPlan.Node<K> node);
        /** Return null without side effects when busy. Throw only before accepting the supplied inputs. */
        Operation<K> start(P processor, CraftPlan.Node<K> node);
    }
    public interface Operation<K> {
        /** Null means still working. A non-null map transfers ownership of actual results to escrow. */
        Map<K, Long> poll();
        /** Transfer back only items still owned; consumed fuel and externally removed items are not recreated. */
        Map<K, Long> cancel();
        /** Inputs held outside a persistent physical container, for save recovery. */
        default Map<K, Long> recoverable() { return Map.of(); }
    }
    private record Running<K, P>(P processor, CraftPlan.Node<K> node, Operation<K> operation) {}
    private final CraftPlan<K> plan;
    private final Processor<K, P> driver;
    private final Map<K, Long> escrow = new LinkedHashMap<>();
    private final Map<Integer, Running<K, P>> running = new LinkedHashMap<>();
    private final Map<Integer, List<Integer>> dependents = new HashMap<>();
    private final Map<Integer, Integer> unmet = new HashMap<>();
    private final ArrayDeque<Integer> ready = new ArrayDeque<>();
    private final Set<Integer> completed = new HashSet<>();
    private State state = State.WAITING;
    private String error = "";

    public CraftScheduler(CraftPlan<K> plan, Map<K, Long> extracted, Processor<K, P> driver) {
        this.plan = plan;
        this.driver = driver;
        this.escrow.putAll(extracted);
        for (var entry : plan.reservations().entrySet())
            if (escrow.getOrDefault(entry.getKey(), 0L) < entry.getValue())
                throw new IllegalArgumentException("Reservation extraction is incomplete");
        for (var node : plan.nodes()) {
            unmet.put(node.id(), node.dependencies().size());
            if (node.dependencies().isEmpty()) ready.add(node.id());
            for (int dependency : node.dependencies())
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(node.id());
        }
        if (plan.nodes().isEmpty()) state = State.COMPLETE;
    }
    public void tick(List<P> processors, int parallelism) {
        if (terminal()) return;
        try {
            for (var entry : new ArrayList<>(running.entrySet())) {
                Running<K, P> work = entry.getValue();
                Map<K, Long> produced = work.operation().poll();
                if (produced == null) continue;
                running.remove(entry.getKey());
                add(produced);
                if (produced.getOrDefault(work.node().recipe().output(), 0L) < work.node().recipe().outputCount())
                    throw new IllegalStateException("Processor returned an unexpected result for " + work.node().recipe().id());
                completed.add(work.node().id());
                for (int dependent : dependents.getOrDefault(work.node().id(), List.of()))
                    if (unmet.compute(dependent, (ignored, count) -> count - 1) == 0) ready.add(dependent);
            }
            int tries = Math.min(ready.size(), 256);
            Set<P> busy = new HashSet<>();
            running.values().forEach(work -> busy.add(work.processor()));
            while (tries-- > 0 && running.size() < parallelism && !ready.isEmpty()) {
                int id = ready.removeFirst();
                CraftPlan.Node<K> node = plan.nodes().get(id);
                boolean started = false;
                for (P processor : processors) {
                    if (busy.contains(processor) || !driver.supports(processor, node)) continue;
                    Map<K, Long> inputs = node.inputs();
                    if (!contains(inputs)) throw new IllegalStateException("Reserved intermediate is missing");
                    Operation<K> operation = driver.start(processor, node);
                    if (operation == null) continue;
                    subtract(inputs);
                    running.put(id, new Running<>(processor, node, operation));
                    busy.add(processor);
                    started = true;
                    break;
                }
                if (!started) ready.addLast(id);
            }
            state = completed.size() == plan.nodes().size() ? State.COMPLETE : running.isEmpty() ? State.WAITING : State.RUNNING;
            if (state == State.COMPLETE && escrow.getOrDefault(plan.target(), 0L) < plan.count())
                throw new IllegalStateException("Final result missing from escrow");
        } catch (RuntimeException failure) {
            error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            stop(State.FAILED);
        }
    }
    public void cancel() { if (!terminal()) stop(State.CANCELLED); }
    private void stop(State finalState) {
        for (var work : running.values()) {
            try { add(work.operation().cancel()); }
            catch (RuntimeException failure) { error += "; processor cleanup: " + failure.getClass().getSimpleName(); }
        }
        running.clear();
        ready.clear();
        state = finalState;
    }
    public Map<K, Long> drain() {
        if (!terminal()) throw new IllegalStateException("Cannot drain an active craft");
        Map<K, Long> result = Map.copyOf(escrow);
        escrow.clear();
        return result;
    }
    public Map<K, Long> recoverySnapshot() {
        Map<K, Long> result = new LinkedHashMap<>(escrow);
        for (var work : running.values()) work.operation().recoverable().forEach((key, count) -> result.merge(key, count, Math::addExact));
        return Map.copyOf(result);
    }
    private boolean contains(Map<K, Long> wanted) {
        return wanted.entrySet().stream().allMatch(entry -> escrow.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
    }
    private void subtract(Map<K, Long> wanted) {
        wanted.forEach((key, count) -> {
            long left = escrow.getOrDefault(key, 0L) - count;
            if (left == 0) escrow.remove(key); else escrow.put(key, left);
        });
    }
    private void add(Map<K, Long> items) {
        items.forEach((key, count) -> {
            if (count < 0) throw new IllegalArgumentException("Negative processor output");
            if (count > 0) escrow.merge(key, count, Math::addExact);
        });
    }
    public State state() { return state; }
    public boolean terminal() { return state == State.COMPLETE || state == State.CANCELLED || state == State.FAILED; }
    public int active() { return running.size(); }
    public int completed() { return completed.size(); }
    public int total() { return plan.nodes().size(); }
    public String error() { return error; }
}
