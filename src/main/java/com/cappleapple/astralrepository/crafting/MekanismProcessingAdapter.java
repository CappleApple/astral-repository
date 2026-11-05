package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.cappleapple.astralrepository.crafting.MekanismRecipeDiscovery.*;

/** Plans native Mekanism recipe contracts and waits for real machine production. */
public final class MekanismProcessingAdapter implements ResourceProcessingAdapter {
    private record Binding(String process, Object recipe, List<Input> inputs, Map<ResourceKey, Long> guaranteed,
                           Map<ResourceKey, Long> possible, boolean perTick, boolean shortOutput, Map<String, Number> costState) {}
    private record MachineCost(long budget, Map<String, Number> state, boolean continuous) {}
    private record UsageSource(Class<?> type, Map<String, Number> state, boolean continuous) {}
    private record StockPrototype(ResourceKey key, Object sample) {}
    private record Choice(List<ResourceKey> alternatives, long count) {}
    private final Map<String, Binding> bindings = new HashMap<>();
    private final Set<String> diagnostics = new HashSet<>();
    private final Set<Object> unavailable = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override public List<?> catalogState(CraftingService.NetworkAccess access) {
        List<Object> result = new ArrayList<>();
        for (GlobalPos position : access.workstations()) {
            Object handler = at(access, position); if (handler == null) continue;
            Object provider = hasMethod(handler, "getRecipeType") ? invoke(handler, "getRecipeType") : null;
            Object recipes = provider == null ? List.of() : invoke(provider, "getRecipes", new Class<?>[]{net.minecraft.world.level.Level.class}, access.level());
            result.add(List.of(position, MekanismMachineAccess.process(handler), MekanismUsageProfile.catalogState(handler),
                    hasMethod(handler, "getMode") ? invoke(handler, "getMode") : "", MekanismMachineAccess.identity(handler), List.copyOf((List<?>) recipes)));
        }
        return List.copyOf(result);
    }
    @Override public List<CraftRecipe<ResourceKey>> recipes(CraftingService.NetworkAccess access) {
        bindings.clear();
        List<CraftRecipe<ResourceKey>> result = new ArrayList<>();
        Map<String, Map<UsageSource, Object>> sources = new HashMap<>();
        List<Object> handlers = new ArrayList<>();
        for (GlobalPos position : access.workstations()) {
            Object handler = at(access, position); if (handler != null) {
                handlers.add(handler); String process = MekanismMachineAccess.process(handler);
                var source = new UsageSource(handler.getClass(), MekanismUsageProfile.catalogState(handler), continuous(handler));
                sources.computeIfAbsent(process, ignored -> new LinkedHashMap<>()).putIfAbsent(source, handler);
            }
        }
        List<Entry> entries = MekanismRecipeDiscovery.recipes(access.level(), handlers);
        if (entries.isEmpty()) return List.of();
        Map<String, List<StockPrototype>> stock = stockPrototypes(access.resourceSnapshot());
        for (var entry : entries) {
            try {
                List<Input> inputs = inputs(entry.recipe());
                if (inputs.isEmpty()) { diagnostic(entry.id(), "recipe exposes no native InputIngredient contract"); continue; }
                boolean explicit = hasMethod(entry.recipe(), "perTickUsage");
                boolean explicitValue = explicit && Boolean.TRUE.equals(invoke(entry.recipe(), "perTickUsage"));
                Set<MachineCost> available = new LinkedHashSet<>();
                for (var source : sources.getOrDefault(entry.type(), Map.of()).entrySet()) {
                    boolean perTick = explicit ? explicitValue : source.getKey().continuous() || legacyDissolution(entry.recipe());
                    var profile = perTick ? MekanismUsageProfile.profile(entry.recipe(), source.getValue()) : new MekanismUsageProfile.Profile(1, Map.of());
                    available.add(new MachineCost(profile.budget(), profile.state(), source.getKey().continuous()));
                }
                if (available.isEmpty()) available.add(new MachineCost(1, Map.of(), false));
                if (hasMethod(entry.recipe(), "hasFluidToChemical") || hasMethod(entry.recipe(), "hasChemicalToFluid") || hasMethod(entry.recipe(), "hasFluidToGas") || hasMethod(entry.recipe(), "hasGasToFluid")) {
                    String forward = hasMethod(entry.recipe(), "hasFluidToChemical") ? "hasFluidToChemical" : "hasFluidToGas";
                    String reverse = hasMethod(entry.recipe(), "hasChemicalToFluid") ? "hasChemicalToFluid" : "hasGasToFluid";
                    String chemicalOutput = hasMethod(entry.recipe(), "getChemicalOutputDefinition") ? "getChemicalOutputDefinition" : "getGasOutputDefinition";
                    if (Boolean.TRUE.equals(invoke(entry.recipe(), forward))) add(entry, inputs.stream().filter(input -> input.resource().equals("fluid")).toList(), chemicalOutput, false, null, stock, result, "fluid_to_chemical");
                    if (Boolean.TRUE.equals(invoke(entry.recipe(), reverse))) add(entry, inputs.stream().filter(input -> input.resource().equals("chemical")).toList(), "getFluidOutputDefinition", false, null, stock, result, "chemical_to_fluid");
                } else {
                    boolean onceAdded = false;
                    for (MachineCost cost : available) {
                        boolean perTick = explicit ? explicitValue : cost.continuous() || legacyDissolution(entry.recipe());
                        if (!perTick && onceAdded) continue;
                        add(entry, inputs, hasMethod(entry.recipe(), "getMainOutputDefinition") ? "getMainOutputDefinition" : "getOutputDefinition", perTick, perTick ? cost : null, stock, result, "native");
                        onceAdded |= !perTick;
                    }
                }
            } catch (RuntimeException failure) { diagnostic(entry.id(), failure.getMessage()); }
        }
        return List.copyOf(result);
    }

    private void add(Entry entry, List<Input> inputs, String outputGetter, boolean perTick, MachineCost cost,
                     Map<String, List<StockPrototype>> stock, List<CraftRecipe<ResourceKey>> result, String direction) {
        if (inputs.isEmpty() || inputs.size() > 9 || inputs.stream().anyMatch(input -> input.representations().isEmpty())) { diagnostic(entry.id(), "native input representations are empty or exceed the planner limit"); return; }
        List<?> definitions = definitions(entry.recipe(), outputGetter);
        if (definitions.isEmpty()) { diagnostic(entry.id(), "native output definitions are unavailable"); return; }
        Map<ResourceKey, Long> possible = new LinkedHashMap<>();
        definitions.forEach(output -> MekanismResourceSupport.flatten(output).forEach((key, amount) -> possible.merge(key, amount, Math::max)));
        Map<ResourceKey, Long> secondary = new LinkedHashMap<>();
        double chance = 0;
        if (hasMethod(entry.recipe(), "getSecondaryOutputDefinition")) {
            chance = ((Number) invoke(entry.recipe(), "getSecondaryChance")).doubleValue();
            for (Object output : definitions(entry.recipe(), "getSecondaryOutputDefinition")) MekanismResourceSupport.flatten(output).forEach((key, amount) -> secondary.merge(key, amount, Math::max));
            double roll = chance; secondary.forEach((key, amount) -> possible.merge(key, Math.multiplyExact(amount, Math.max(1, (long) Math.ceil(roll))), Math::addExact));
        }
        List<List<Choice>> choices = new ArrayList<>();
        for (Input input : inputs) {
            Map<Long, LinkedHashSet<ResourceKey>> groups = new LinkedHashMap<>();
            for (Object representation : input.representations()) {
                if (MekanismResourceSupport.amount(representation) <= 0) continue;
                ResourceKey key = MekanismResourceSupport.key(representation);
                long count = input.amount(representation);
                if (perTick && input.resource().equals("chemical")) count = Math.multiplyExact(count, Math.max(1, cost.budget()));
                groups.computeIfAbsent(count, ignored -> new LinkedHashSet<>()).add(key);
            }
            for (StockPrototype prototype : stock.getOrDefault(input.resource(), List.of())) {
                ResourceKey key = prototype.key(); Object sample = prototype.sample();
                if (!input.accepts(sample)) continue;
                long count = input.amount(sample);
                if (perTick && input.resource().equals("chemical")) count = Math.multiplyExact(count, Math.max(1, cost.budget()));
                groups.computeIfAbsent(count, ignored -> new LinkedHashSet<>()).add(key);
            }
            choices.add(groups.entrySet().stream().map(group -> new Choice(List.copyOf(group.getValue()), group.getKey())).toList());
        }
        List<List<Choice>> combinations = new ArrayList<>();
        combinations(choices, 0, new ArrayList<>(), combinations);
        int variant = 0;
        for (List<Choice> combination : combinations) {
            for (Object definition : definitions) {
                Map<ResourceKey, Long> guaranteed = new LinkedHashMap<>(MekanismResourceSupport.flatten(definition));
                if (chance >= 1) { long guaranteedRolls = (long) Math.floor(chance); secondary.forEach((key, amount) -> guaranteed.merge(key, Math.multiplyExact(amount, guaranteedRolls), Math::addExact)); }
                if (guaranteed.isEmpty() && secondary.isEmpty()) { diagnostic(entry.id(), "native output definitions contain no produced resource"); continue; }
                List<List<Choice>> refined = new ArrayList<>();
                if (definitions.size() == 1) refined.add(combination);
                else refine(entry.recipe(), combination, guaranteed, 0, new ArrayList<>(), refined, new int[]{4096});
                for (List<Choice> selected : refined) {
                    List<CraftRecipe.Ingredient<ResourceKey>> ingredients = selected.stream().map(choice -> new CraftRecipe.Ingredient<>(choice.alternatives(), choice.count())).toList();
                    Map<ResourceKey, Long> aliases = new LinkedHashMap<>(guaranteed);
                    if (chance > 0 && chance < 1) secondary.forEach((key, amount) -> aliases.putIfAbsent(key, amount));
                    for (var output : aliases.entrySet()) {
                        String costKey = perTick ? cost.state().isEmpty() ? "unbound" : costKey(cost) : "once";
                        String id = "astral_repository:mekanism/" + entry.type().replace(':', '/') + "/" + direction + "/" + costKey + "/" + variant++ + "/" + entry.id();
                        result.add(new CraftRecipe<>(id, output.getKey(), output.getValue(), "mekanism/" + entry.type() + "/" + direction + "/" + costKey, ingredients, 0, (int) Math.min(Integer.MAX_VALUE, duration(entry.recipe()))));
                        bindings.put(id, new Binding(entry.type(), entry.recipe(), inputs, Map.copyOf(guaranteed), Map.copyOf(possible), perTick, !guaranteed.containsKey(output.getKey()), perTick ? cost.state() : Map.of()));
                    }
                }
            }
        }
    }
    private static Map<String, List<StockPrototype>> stockPrototypes(Map<ResourceKey, Long> snapshot) {
        Map<String, List<StockPrototype>> result = new HashMap<>();
        for (ResourceKey key : snapshot.keySet()) {
            String kind = MekanismResourceSupport.kind(key);
            if (kind.equals("energy")) continue;
            result.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(new StockPrototype(key, MekanismResourceSupport.sample(key, 1)));
        }
        result.replaceAll((kind, values) -> List.copyOf(values)); return Map.copyOf(result);
    }
    private static boolean continuous(Object handler) { return implementsContract(handler.getClass(), "mekanism.common.recipe.lookup.IRecipeLookupHandler$ConstantUsageRecipeLookupHandler"); }
    private static boolean legacyDissolution(Object recipe) { return implementsContract(recipe.getClass(), "mekanism.api.recipes.ChemicalDissolutionRecipe"); }
    private static String costKey(MachineCost cost) {
        String state = cost.budget() + "/" + cost.continuous() + "/" + new TreeMap<>(cost.state());
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(state.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0, 8); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("Missing SHA-256", impossible); }
    }
    private static void refine(Object recipe, List<Choice> choices, Map<ResourceKey, Long> expected, int index,
                               List<Choice> current, List<List<Choice>> result, int[] budget) {
        if (--budget[0] < 0) throw new IllegalStateException("conditional native output combinations exceed 4096");
        if (index < choices.size()) {
            Choice choice = choices.get(index);
            for (ResourceKey key : choice.alternatives()) {
                current.add(new Choice(List.of(key), choice.count())); refine(recipe, choices, expected, index + 1, current, result, budget); current.remove(current.size() - 1);
            }
            return;
        }
        List<Object> samples = current.stream().map(choice -> MekanismResourceSupport.sample(choice.alternatives().getFirst(), choice.count())).toList();
        for (Method method : recipe.getClass().getMethods()) {
            if (!method.getName().equals("getOutput") || method.isBridge() || method.getParameterCount() != samples.size()) continue;
            // ChanceOutput access can roll RNG. Only native resource/record results are evaluated.
            if (method.getReturnType().getSimpleName().contains("Chance")) continue;
            Object[] args = new Object[samples.size()]; Set<Integer> used = new HashSet<>(); boolean matches = true;
            for (int i = 0; i < args.length; i++) {
                for (int j = 0; j < samples.size(); j++) if (!used.contains(j) && method.getParameterTypes()[i].isInstance(samples.get(j))) { args[i] = samples.get(j); used.add(j); break; }
                if (args[i] == null) { matches = false; break; }
            }
            if (!matches) continue;
            Map<ResourceKey, Long> actual = MekanismResourceSupport.flatten(invoke(recipe, "getOutput", method.getParameterTypes(), args));
            if (expected.entrySet().stream().allMatch(output -> actual.getOrDefault(output.getKey(), 0L) >= output.getValue())) result.add(List.copyOf(current));
            return;
        }
        throw new IllegalStateException("conditional output has no deterministic native getOutput contract");
    }
    private static List<?> definitions(Object recipe, String getter) {
        Object value = invoke(recipe, getter);
        if (value instanceof List<?> list) return list;
        if (value != null && value.getClass().isArray()) { List<Object> list = new ArrayList<>(); for (int i = 0; i < Array.getLength(value); i++) list.add(Array.get(value, i)); return list; }
        return List.of();
    }
    private static void combinations(List<List<Choice>> choices, int index, List<Choice> current, List<List<Choice>> output) {
        if (output.size() > 4096) throw new IllegalStateException("native input quantity combinations exceed 4096");
        if (index == choices.size()) { output.add(List.copyOf(current)); return; }
        for (Choice choice : choices.get(index)) { current.add(choice); combinations(choices, index + 1, current, output); current.remove(current.size() - 1); }
    }
    private static long duration(Object recipe) { return hasMethod(recipe, "getDuration") ? Math.max(1, ((Number) invoke(recipe, "getDuration")).longValue()) : 200; }
    private void diagnostic(String recipe, String message) { if (diagnostics.add(recipe + ":" + message)) CraftingService.LOGGER.warn("Mekanism recipe {} unavailable: {}", recipe, message); }

    @Override public boolean isProcessingPosition(CraftingService.NetworkAccess access, GlobalPos position) { return at(access, position) != null; }
    @Override public Object processorIdentity(CraftingService.NetworkAccess access, GlobalPos position) { Object handler = at(access, position); return handler == null ? position : MekanismMachineAccess.identity(handler); }
    @Override public boolean supports(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node) {
        Binding binding = bindings.get(node.recipe().id()); Object handler = at(access, position);
        if (binding == null || handler == null || unavailable.contains(handler)) return false;
        if (binding.process().equals(MekanismMachineAccess.process(handler))) {
            boolean actualContinuous = hasMethod(binding.recipe(), "perTickUsage") ? Boolean.TRUE.equals(invoke(binding.recipe(), "perTickUsage")) : continuous(handler) || legacyDissolution(binding.recipe());
            if (actualContinuous != binding.perTick() || binding.perTick() && (binding.costState().isEmpty() || !binding.costState().equals(MekanismUsageProfile.profile(binding.recipe(), handler).state()))) return false;
            if (!hasMethod(handler, "getMode")) return true;
            // Recipes for each direction have separate process IDs. The native insertion
            // predicates describe the currently selected mode without changing the machine.
            var inputs = MekanismMachineAccess.ports(handler).stream().filter(port -> port.input && !port.converter && !port.kind.equals("energy")).toList();
            return MachineSlotAssignment.assign(java.util.stream.IntStream.range(0, inputs.size()).boxed().toList(),
                    java.util.stream.IntStream.range(0, node.recipe().ingredients().size()).boxed().toList(), (slot, ingredient) ->
                            acceptsRole(binding.inputs().get(ingredient).name(), inputs.get(slot), inputs)
                                    && node.recipe().ingredients().get(ingredient).alternatives().stream().anyMatch(key -> inputs.get(slot).insert(key, 1, true) > 0)) != null;
        }
        return !converters(handler, node, binding).isEmpty();
    }
    @Override public CraftScheduler.Operation<ResourceKey> start(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node) {
        if (!supports(access, position, node) || node.selected().isEmpty()) return null;
        Object handler = at(access, position); Binding binding = bindings.get(node.recipe().id());
        List<MekanismMachineAccess.Port> ports = MekanismMachineAccess.ports(handler);
        boolean conversion = !binding.process().equals(MekanismMachineAccess.process(handler));
        List<MekanismMachineAccess.Port> inputs = conversion ? converters(handler, node, binding)
                : ports.stream().filter(port -> port.input && !port.kind.equals("energy") && !port.converter).toList();
        List<MekanismMachineAccess.Port> outputs = conversion ? ports.stream().filter(port -> inputs.stream().anyMatch(input -> input.linksTo(port))).toList()
                : ports.stream().filter(port -> port.output && !port.kind.equals("energy") && !port.converter).toList();
        if (inputs.isEmpty() || outputs.isEmpty() || inputs.stream().anyMatch(port -> port.amount() > 0) || !conversion && outputs.stream().anyMatch(port -> port.amount() > 0)) return null;
        if (conversion && ports.stream().anyMatch(port -> port.input && !port.converter && !port.kind.equals("energy") && port.amount() > 0 && !outputs.contains(port))) return null;
        List<Integer> assigned = MachineSlotAssignment.assign(java.util.stream.IntStream.range(0, inputs.size()).boxed().toList(), java.util.stream.IntStream.range(0, node.selected().size()).boxed().toList(),
                (index, ingredient) -> {
                    var selected = node.selected().get(ingredient); var port = inputs.get(index);
                    return acceptsRole(binding.inputs().get(ingredient).name(), port, inputs)
                            && port.insert(selected.key(), selected.key() instanceof ItemKey ? selected.count() : Math.min(selected.count(), 1), true) > 0;
                });
        if (assigned == null) return null;
        return new NativeOperation(access, position, node, handler, binding, inputs, outputs, assigned);
    }
    private static boolean acceptsRole(String ingredient, MekanismMachineAccess.Port port, List<MekanismMachineAccess.Port> inputs) {
        if (ingredient.contains("Main") && inputs.stream().anyMatch(candidate -> candidate.semanticRoles.contains("EXTRA"))) return !port.semanticRoles.contains("EXTRA");
        if (ingredient.contains("Extra") && inputs.stream().anyMatch(candidate -> candidate.semanticRoles.contains("EXTRA"))) return port.semanticRoles.contains("EXTRA");
        if (ingredient.contains("Left") && inputs.stream().anyMatch(candidate -> candidate.semanticRoles.contains("INPUT_1"))) return port.semanticRoles.contains("INPUT_1");
        if (ingredient.contains("Right") && inputs.stream().anyMatch(candidate -> candidate.semanticRoles.contains("INPUT_2"))) return port.semanticRoles.contains("INPUT_2");
        return true;
    }
    private static List<MekanismMachineAccess.Port> converters(Object handler, CraftPlan.Node<ResourceKey> node, Binding binding) {
        if (binding.inputs().size() != 1 || !binding.inputs().get(0).resource().equals("item") || binding.guaranteed().keySet().stream().anyMatch(ItemKey.class::isInstance)) return List.of();
        var ports = MekanismMachineAccess.ports(handler);
        var ingredient = node.recipe().ingredients().getFirst();
        return ports.stream().filter(port -> port.converter && ingredient.alternatives().stream().anyMatch(key -> port.insert(key, Math.min(ingredient.count(), 1), true) > 0)
                && ports.stream().anyMatch(output -> port.linksTo(output) && binding.guaranteed().keySet().stream().anyMatch(key -> MekanismResourceSupport.kind(key).equals(output.kind)))).toList();
    }
    private static Object at(CraftingService.NetworkAccess access, GlobalPos position) {
        ServerLevel level = access.level().getServer().getLevel(position.dimension());
        if (level == null || !level.hasChunkAt(position.pos())) return null;
        try { return MekanismMachineAccess.handler(level.getBlockEntity(position.pos())); }
        catch (RuntimeException unsupported) { return null; }
    }

    private final class NativeOperation implements CraftScheduler.Operation<ResourceKey> {
        private final CraftingService.NetworkAccess access;
        private final GlobalPos position;
        private final CraftPlan.Node<ResourceKey> node;
        private final Object handler;
        private final Binding binding;
        private final List<MekanismMachineAccess.Port> inputPorts, outputPorts;
        private final List<Integer> assigned;
        private final Map<ResourceKey, Long> pending = new LinkedHashMap<>(), committed = new LinkedHashMap<>(), collected = new LinkedHashMap<>();
        private final Map<ResourceKey, Long> baseline = new HashMap<>();
        private final Map<Object, Map<ResourceKey, Long>> baselinePorts = new IdentityHashMap<>();
        private final Map<ResourceKey, Integer> pendingDelay = new HashMap<>();
        private RuntimeException failure;
        private boolean settled;
        private int ticks;
        NativeOperation(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node, Object handler, Binding binding,
                        List<MekanismMachineAccess.Port> inputPorts, List<MekanismMachineAccess.Port> outputPorts, List<Integer> assigned) {
            this.access = access; this.position = position; this.node = node; this.handler = handler; this.binding = binding;
            this.inputPorts = inputPorts; this.outputPorts = outputPorts; this.assigned = assigned;
            for (var port : outputPorts) if (port.amount() > 0) {
                baseline.merge(port.key(), port.amount(), Math::addExact);
                baselinePorts.put(port.nativePort, Map.of(port.key(), port.amount()));
            }
            pending.putAll(node.inputs()); feed();
        }
        private void feed() {
            for (int i = 0; i < node.selected().size() && failure == null; i++) {
                var selection = node.selected().get(i); long remaining = pending.getOrDefault(selection.key(), 0L); if (remaining <= 0) continue;
                if (pendingDelay.getOrDefault(selection.key(), 0) > 0) continue;
                var port = inputPorts.get(assigned.get(i));
                long offered = selection.key() instanceof ItemKey ? Math.min(remaining, selection.count()) : Math.min(remaining, Integer.MAX_VALUE);
                try {
                    long accepted = port.insert(selection.key(), offered, false);
                    if (accepted < 0 || accepted > offered) throw new IllegalStateException("Native machine returned an invalid insertion amount");
                    pending.computeIfPresent(selection.key(), (key, amount) -> amount == accepted ? null : amount - accepted);
                    if (accepted > 0) committed.merge(selection.key(), accepted, Math::addExact);
                } catch (RuntimeException uncertain) {
                    pending.computeIfPresent(selection.key(), (key, amount) -> amount == offered ? null : amount - offered);
                    CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(selection.key(), offered));
                    unavailable.add(handler); failure = uncertain;
                }
            }
        }
        @Override public Map<ResourceKey, Long> poll() {
            if (settled) return Map.of();
            ServerLevel level = access.level().getServer().getLevel(position.dimension());
            if (level == null || !level.hasChunkAt(position.pos())) return null;
            if (at(access, position) != handler) throw new IllegalStateException("Mekanism processor changed; physical contents remain in-world");
            if (failure != null) throw failure;
            Map<ResourceKey, Long> ready = new HashMap<>();
            for (var port : outputPorts) if (port.amount() > 0) ready.merge(port.key(), port.amount(), Math::addExact);
            baseline.forEach((key, amount) -> ready.merge(key, -amount, Long::sum));
            boolean produced = !binding.guaranteed().isEmpty() ? binding.guaranteed().entrySet().stream().allMatch(output -> ready.getOrDefault(output.getKey(), 0L) >= output.getValue())
                    : !committed.isEmpty() && pending.isEmpty() && inputPorts.stream().noneMatch(port -> port.amount() > 0 && committed.containsKey(port.key()));
            if (!binding.process().equals(MekanismMachineAccess.process(handler)))
                produced &= pending.isEmpty() && !committed.isEmpty() && inputPorts.stream().noneMatch(port -> port.amount() > 0 && committed.containsKey(port.key()));
            if (produced) {
                collect(); reclaimInputs(); collected.putAll(merge(collected, pending)); pending.clear(); settled = true; return Map.copyOf(collected);
            }
            pendingDelay.replaceAll((key, delay) -> Math.max(0, delay - 1)); feed();
            if (binding.perTick()) topUp();
            if (++ticks > Math.max(7200, node.recipe().durationTicks() * 32L)) throw new IllegalStateException("Mekanism processor stalled; check native power, heat, mode and resources at " + position.pos());
            return null;
        }
        private void topUp() {
            for (int i = 0; i < node.selected().size(); i++) {
                var selection = node.selected().get(i);
                if (!(selection.key() instanceof ChemicalKey) || pending.getOrDefault(selection.key(), 0L) > 0) continue;
                var profile = MekanismUsageProfile.profile(binding.recipe(), handler);
                long ticks = Math.max(1, profile.state().getOrDefault("effectiveTicks", 200).longValue());
                long perTick = profile.state().containsKey("maxPerTick") ? profile.state().get("maxPerTick").longValue() : Math.max(1, (profile.budget() + ticks - 1) / ticks);
                long ingredient = binding.inputs().get(i).amount(MekanismResourceSupport.sample(selection.key(), 1));
                long headroom = Math.max(1, Math.multiplyExact(ingredient, Math.max(1, perTick)));
                if (inputPorts.get(assigned.get(i)).amount() >= headroom) continue;
                Map<GlobalPos, Long> receipts = new LinkedHashMap<>();
                long amount = access.reserveResource(selection.key(), Math.max(1, selection.count()), (from, count) -> receipts.merge(from, count, Math::addExact));
                if (amount > 0) {
                    // Receipt callbacks only record ownership. Timing/cosmetics cannot prevent escrow capture.
                    pending.merge(selection.key(), amount, Math::addExact);
                    for (var receipt : receipts.entrySet()) {
                        int travel;
                        try { travel = Math.max(0, access.travelTicks(receipt.getKey(), position)); }
                        catch (RuntimeException invalidRoute) { failure = invalidRoute; break; }
                        pendingDelay.merge(selection.key(), travel, Math::max);
                        try { access.visualResource(receipt.getKey(), position, selection.key(), receipt.getValue(), travel); }
                        catch (RuntimeException cosmetic) { CraftingService.LOGGER.debug("Mekanism supplemental transport visual unavailable", cosmetic); }
                    }
                }
            }
        }
        private void collect() {
            for (var port : outputPorts) {
                ResourceKey key = port.key(); if (key == null) continue;
                long ownedDelta = port.amount() - baselinePorts.getOrDefault(port.nativePort, Map.of()).getOrDefault(key, 0L);
                long allowed = Math.min(binding.possible().getOrDefault(key, 0L) - collected.getOrDefault(key, 0L), ownedDelta);
                if (allowed > 0) { long extracted = port.extract(Math.min(allowed, port.amount()), false); if (extracted > 0) collected.merge(key, extracted, Math::addExact); }
            }
        }
        private void reclaimInputs() {
            Map<ResourceKey, Long> recovered = new HashMap<>();
            for (var port : inputPorts) {
                ResourceKey key = port.key(); if (key == null) continue;
                long allowed = committed.getOrDefault(key, 0L) - recovered.getOrDefault(key, 0L);
                if (allowed > 0) { long extracted = port.extract(Math.min(allowed, port.amount()), false); if (extracted > 0) { recovered.merge(key, extracted, Math::addExact); collected.merge(key, extracted, Math::addExact); } }
            }
        }
        @Override public Map<ResourceKey, Long> cancel() {
            if (settled) return Map.of();
            settled = true;
            if (at(access, position) == handler) {
                try { reclaimInputs(); collect(); }
                catch (RuntimeException incomplete) { unavailable.add(handler); CraftingService.LOGGER.error("Mekanism cleanup incomplete; remaining native contents stay in-world at {}", position, incomplete); }
            }
            Map<ResourceKey, Long> result = merge(collected, pending); pending.clear(); collected.clear(); return result;
        }
        @Override public Map<ResourceKey, Long> recoverable() { return settled ? Map.of() : merge(collected, pending); }
        @Override public boolean mayReturnShortOutput() { return binding.shortOutput(); }
    }
    private static Map<ResourceKey, Long> merge(Map<ResourceKey, Long> first, Map<ResourceKey, Long> second) { Map<ResourceKey, Long> result = new LinkedHashMap<>(first); second.forEach((key, amount) -> result.merge(key, amount, Math::addExact)); return Map.copyOf(result); }
}
