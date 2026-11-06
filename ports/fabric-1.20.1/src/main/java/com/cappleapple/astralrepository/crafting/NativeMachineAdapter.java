package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ResourceKey;
import java.util.*;
import java.util.stream.IntStream;
import net.minecraft.core.GlobalPos;
import com.cappleapple.astralrepository.api.ResourceKeyCodec;
import net.minecraft.nbt.*;

/** One physical native recipe attempt, shared by independent per-mod discovery backends. */
public final class NativeMachineAdapter implements ResourceProcessingAdapter {
    private record Binding(NativeMachineBackend.Recipe recipe, boolean shortOutput, String workstation) {}
    private final NativeMachineBackend backend;
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final LinkedHashSet<ResourceKey> requestedOutputs = new LinkedHashSet<>();
    private final Set<Object> uncertain;

    public NativeMachineAdapter(NativeMachineBackend backend) { this.backend = Objects.requireNonNull(backend); this.uncertain = new HashSet<>(); }
    private NativeMachineAdapter(NativeMachineAdapter source) {
        this.backend = source.backend; this.uncertain = source.uncertain; this.bindings.putAll(source.bindings); this.requestedOutputs.addAll(source.requestedOutputs);
    }
    @Override public ResourceProcessingAdapter capturePlan() { return new NativeMachineAdapter(this); }
    @Override public void requestOutput(ResourceKey output) {
        if (!backend.usesRequestedOutputs()) return;
        if (requestedOutputs.contains(output)) return;
        requestedOutputs.add(output);
        if (requestedOutputs.size() > 128) requestedOutputs.remove(requestedOutputs.iterator().next());
    }
    @Override public void requestOutput(CraftingService.NetworkAccess access, ResourceKey output) {
        if (backend.usesRequestedOutputs() && !machines(access).isEmpty()) requestOutput(output);
    }

    private List<NativeMachineBackend.Machine> machines(CraftingService.NetworkAccess access) {
        Map<Object, NativeMachineBackend.Machine> result = new LinkedHashMap<>();
        for (GlobalPos position : access.workstations()) {
            var machine = backend.machine(access, position);
            if (machine != null && machine.valid()) result.putIfAbsent(machine.identity(), machine);
        }
        return List.copyOf(result.values());
    }

    @Override public Object catalogState(CraftingService.NetworkAccess access) {
        var machines = machines(access);
        var hosts = machines.stream().map(machine -> List.of(machine.identity(), machine.catalogState())).toList();
        Set<ResourceKey> desired = new HashSet<>();
        if (backend.usesRequestedOutputs() && !machines.isEmpty()) {
            desired.addAll(requestedOutputs);
            for (var product : access.exposedProducts()) if (!product.isEmpty()) desired.add(new com.cappleapple.astralrepository.api.ItemKey(product));
        }
        return List.of(hosts, backend.usesUnlistedStock() && !machines.isEmpty()
                ? Set.copyOf(access.resourceSnapshot().keySet()) : Set.of(), Set.copyOf(desired));
    }

    @Override public List<CraftRecipe<ResourceKey>> recipes(CraftingService.NetworkAccess access) {
        bindings.clear();
        List<CraftRecipe<ResourceKey>> result = new ArrayList<>();
        List<NativeMachineBackend.Machine> machines = machines(access);
        Map<List<Integer>, String> labels = new HashMap<>();
        for (var original : backend.recipes(access, machines, List.copyOf(requestedOutputs))) {
            for (var output : original.possible().entrySet()) for (var recipe : seedVariants(original, output.getKey())) {
                List<Integer> hosts = IntStream.range(0, machines.size()).filter(index -> machines.get(index).accepts(recipe)).boxed().toList();
                String workstation = labels.computeIfAbsent(hosts, key -> workstation(access, key.stream().map(machines::get).toList()));
                String process = "native/" + backend.id() + "/" + recipe.process() + "/hosts/" + hosts;
                boolean chance = recipe.guaranteed().getOrDefault(output.getKey(), 0L) <= 0;
                long count = chance ? output.getValue() : recipe.guaranteed().get(output.getKey());
                long selfInput = recipe.inputs().stream().filter(input -> input.alternatives().contains(output.getKey())).mapToLong(CraftRecipe.Ingredient::count).reduce(0, Math::addExact);
                long seed = !chance && count > selfInput ? selfInput : 0;
                String id = "astral_repository:native/" + backend.id() + "/" + fingerprint(access, recipe, output.getKey()) + "/" + recipe.id();
                if (bindings.containsKey(id)) continue;
                result.add(new CraftRecipe<>(id, output.getKey(), count - seed, process,
                        recipe.inputs(), 0, recipe.durationTicks(), seed));
                bindings.put(id, new Binding(recipe, chance || recipe.chanceOutput(), workstation));
            }
        }
        return List.copyOf(result);
    }
    private static List<NativeMachineBackend.Recipe> seedVariants(NativeMachineBackend.Recipe recipe, ResourceKey output) {
        long maximum = recipe.inputs().stream().filter(input -> input.alternatives().contains(output)).mapToLong(CraftRecipe.Ingredient::count).reduce(0, Math::addExact);
        if (maximum == 0 || recipe.guaranteed().getOrDefault(output, 0L) <= maximum) return List.of(recipe);
        List<List<CraftRecipe.Ingredient<ResourceKey>>> variants = new ArrayList<>(); variants.add(List.of());
        for (var input : recipe.inputs()) {
            List<CraftRecipe.Ingredient<ResourceKey>> choices = List.of(input);
            if (input.alternatives().size() > 1 && input.alternatives().contains(output)) choices = List.of(
                    new CraftRecipe.Ingredient<>(List.of(output), input.count(), input.tag()),
                    new CraftRecipe.Ingredient<>(input.alternatives().stream().filter(key -> !key.equals(output)).toList(), input.count(), input.tag()));
            if ((long)variants.size()*choices.size() > 8192) return List.of(recipe);
            List<List<CraftRecipe.Ingredient<ResourceKey>>> expanded = new ArrayList<>();
            for (var prefix : variants) for (var choice : choices) { var next = new ArrayList<>(prefix); next.add(choice); expanded.add(List.copyOf(next)); }
            variants = expanded;
        }
        return variants.stream().map(inputs -> new NativeMachineBackend.Recipe(recipe.id(), recipe.process(), inputs, recipe.guaranteed(), recipe.possible(),
                recipe.durationTicks(), recipe.timeoutTicks(), recipe.nativeRecipe(), recipe.chanceOutput(), recipe.sourceRecipes())).toList();
    }
    @Override public Set<String> sourceRecipes(String id) {
        Binding binding = bindings.get(id); return binding == null ? Set.of(id) : binding.recipe().sourceRecipes();
    }
    @Override public String workstationLabel(CraftingService.NetworkAccess access, String id) {
        Binding binding = bindings.get(id); return binding == null ? "" : binding.workstation();
    }
    private String workstation(CraftingService.NetworkAccess access, List<NativeMachineBackend.Machine> machines) {
        Set<String> names = new TreeSet<>();
        for (var machine : machines) {
            net.minecraft.world.level.block.entity.BlockEntity entity = machine.instance() instanceof net.minecraft.world.level.block.entity.BlockEntity tile ? tile : null;
            if (entity == null) for (var position : machine.claimedPositions()) {
                var level = access.level().getServer().getLevel(position.dimension());
                if (level != null && level.hasChunkAt(position.pos())) { entity = level.getBlockEntity(position.pos()); if (entity != null) break; }
            }
            if (entity == null) continue;
            var block = entity.getBlockState().getBlock(); String name = block.getName().getString();
            if (name.startsWith("block.")) name = title(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath());
            names.add(name);
        }
        String mod = switch (backend.id()) {
            case "industrialforegoing", "industrialforegoing_generators", "industrialforegoing_brewing" -> "Industrial Foregoing";
            case "immersiveengineering" -> "Immersive Engineering";
            case "enderio" -> "Ender IO";
            default -> title(backend.id());
        };
        return names.isEmpty() ? "" : mod + " / " + String.join(" or ", names.stream().limit(3).toList());
    }
    private static String title(String name) {
        String[] words = name.replace('_', ' ').replace('/', ' ').split(" "); StringBuilder result = new StringBuilder();
        for (String word : words) if (!word.isEmpty()) { if (!result.isEmpty()) result.append(' '); result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)); }
        return result.toString();
    }

    private static String fingerprint(CraftingService.NetworkAccess access, NativeMachineBackend.Recipe recipe, ResourceKey output) {
        java.util.function.Function<ResourceKey, String> key = value -> canonical(ResourceKeyCodec.save(value, access.level().registryAccess()));
        StringBuilder value = new StringBuilder(recipe.process()).append('/').append(recipe.durationTicks()).append('/').append(key.apply(output));
        value.append('/').append(recipe.sourceRecipes().stream().sorted().toList());
        for (var input : recipe.inputs()) value.append('|').append(input.count()).append(':')
                .append(input.alternatives().stream().map(key).sorted().toList());
        recipe.guaranteed().entrySet().stream().map(entry -> key.apply(entry.getKey()) + "=" + entry.getValue()).sorted().forEach(value::append);
        value.append('/');
        recipe.possible().entrySet().stream().map(entry -> key.apply(entry.getKey()) + "=" + entry.getValue()).sorted().forEach(value::append);
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0, 12); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) return compound.getAllKeys().stream().sorted()
                .map(key -> key + ":" + canonical(compound.get(key))).toList().toString();
        if (tag instanceof ListTag list) { List<String> values = new ArrayList<>(); for (Tag value : list) values.add(canonical(value)); return values.toString(); }
        return tag.toString();
    }
    /** Stable profile IDs survive process restarts and preserve exact resource components. */
    public static String profile(net.minecraft.server.level.ServerLevel level, Object value) {
        String serialized = state(level, value);
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0, 12); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static String state(net.minecraft.server.level.ServerLevel level, Object value) {
        if (value == null) return "null";
        if (value instanceof ResourceKey key) return canonical(ResourceKeyCodec.save(key, level.registryAccess()));
        if (value instanceof net.minecraft.world.item.ItemStack item) return item.isEmpty() ? "empty" : item.getCount() + "*" + state(level, new com.cappleapple.astralrepository.api.ItemKey(item));
        if (value instanceof Tag tag) return canonical(tag);
        if (value instanceof net.minecraft.world.level.block.state.BlockState block) return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock())
                + block.getValues().entrySet().stream().map(entry -> entry.getKey().getName() + "=" + propertyValue(entry.getKey(), entry.getValue())).sorted().toList().toString();
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().map(entry -> state(level, entry.getKey()) + "=" + state(level, entry.getValue())).sorted().toList().toString();
        if (value instanceof Set<?> values) return values.stream().map(entry -> state(level, entry)).sorted().toList().toString();
        if (value instanceof Collection<?> values) return values.stream().map(entry -> state(level, entry)).toList().toString();
        if (value.getClass().isRecord() && value.getClass().getName().startsWith("com.cappleapple.astralrepository.")) {
            List<String> fields = new ArrayList<>();
            for (var component : value.getClass().getRecordComponents()) {
                try {
                    var accessor = component.getAccessor(); accessor.setAccessible(true);
                    fields.add(component.getName() + "=" + state(level, accessor.invoke(value)));
                } catch (ReflectiveOperationException unavailable) { throw new IllegalArgumentException("Native profile record unavailable", unavailable); }
            }
            return value.getClass().getName() + fields;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence || value instanceof Enum<?>
                || value instanceof net.minecraft.resources.ResourceLocation || value instanceof GlobalPos || value instanceof net.minecraft.core.BlockPos)
            return value.toString();
        throw new IllegalArgumentException("Native profile must contain immutable value data: " + value.getClass().getName());
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(net.minecraft.world.level.block.state.properties.Property property, Comparable value) { return property.getName(value); }

    @Override public boolean isProcessingPosition(CraftingService.NetworkAccess access, GlobalPos position) {
        return backend.machine(access, position) != null;
    }
    @Override public Object processorIdentity(CraftingService.NetworkAccess access, GlobalPos position) {
        var machine = backend.machine(access, position);
        return machine == null ? position : machine.identity();
    }
    @Override public List<GlobalPos> claimedPositions(CraftingService.NetworkAccess access, GlobalPos position,
                                                     CraftPlan.Node<ResourceKey> node) {
        var machine = backend.machine(access, position);
        Set<GlobalPos> positions = new LinkedHashSet<>(); positions.add(position);
        if (machine != null) positions.addAll(machine.claimedPositions());
        return List.copyOf(positions);
    }
    @Override public boolean supports(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node) {
        Binding binding = bindings.get(node.recipe().id());
        if (binding == null) return false;
        var machine = backend.machine(access, position);
        return machine != null && machine.valid() && !uncertain.contains(machine.identity()) && machine.accepts(binding.recipe());
    }

    @Override public CraftScheduler.Operation<ResourceKey> start(CraftingService.NetworkAccess access, GlobalPos position,
                                                                CraftPlan.Node<ResourceKey> node) {
        Binding binding = bindings.get(node.recipe().id());
        var machine = backend.machine(access, position);
        if (binding == null || machine == null || !machine.valid() || uncertain.contains(machine.identity())
                || !machine.accepts(binding.recipe()) || !machine.ready(binding.recipe(), node.selected())) return null;
        List<NativeMachineBackend.Port> ports = distinct(machine.ports(binding.recipe()));
        List<NativeMachineBackend.Port> inputs = ports.stream().filter(NativeMachineBackend.Port::input).toList();
        List<NativeMachineBackend.Port> outputs = new ArrayList<>(ports.stream().filter(NativeMachineBackend.Port::output).toList());
        if (machine.emitsWorldItems()) {
            var level = access.level().getServer().getLevel(position.dimension());
            if (level == null) return null;
            outputs.add(NativeWorldOutputs.output(level, position));
        }
        if (inputs.stream().anyMatch(port -> !port.snapshot().equals(port.retainedInputBaseline())) || outputs.isEmpty()) return null;
        List<CraftPlan.Selection<ResourceKey>> selected = node.selected();
        if (machine.coalesceInputs()) {
            Map<ResourceKey, Long> merged = new LinkedHashMap<>();
            selected.forEach(value -> merged.merge(value.key(), value.count(), Math::addExact));
            selected = merged.entrySet().stream().map(entry -> new CraftPlan.Selection<>(entry.getKey(), entry.getValue())).toList();
        }
        List<Integer> assigned = MachineSlotAssignment.assign(IntStream.range(0, inputs.size()).boxed().toList(), selected,
                (slot, selection) -> inputs.get(slot).insert(selection.key(), selection.count(), true) == selection.count());
        if (assigned == null) return null;
        return new Attempt(access, position, node, machine, binding, inputs, outputs, assigned, selected);
    }

    private static List<NativeMachineBackend.Port> distinct(List<NativeMachineBackend.Port> ports) {
        Map<Object, NativeMachineBackend.Port> result = new LinkedHashMap<>();
        for (var port : ports) result.merge(port.identity(), port, (first, second) -> {
            if (first.input() != second.input() || first.output() != second.output()
                    || !first.retainedInputBaseline().equals(second.retainedInputBaseline()))
                throw new IllegalStateException("Conflicting native roles for one physical container");
            return first;
        });
        return List.copyOf(result.values());
    }

    private final class Attempt implements CraftScheduler.Operation<ResourceKey> {
        private final CraftingService.NetworkAccess access;
        private final GlobalPos position;
        private final NativeMachineBackend.Machine machine;
        private final Binding binding;
        private final List<NativeMachineBackend.Port> inputs, outputs;
        private final CraftPlan.Node<ResourceKey> node;
        private final List<Integer> assigned;
        private final List<CraftPlan.Selection<ResourceKey>> selected;
        private final Set<GlobalPos> destinations;
        private final Map<Integer, Long> pending = new LinkedHashMap<>();
        private final Map<Object, Map<ResourceKey, Long>> committed = new HashMap<>(), baseline = new HashMap<>();
        private final Map<Object, Map<ResourceKey, Long>> inputBaseline = new HashMap<>();
        private final Map<ResourceKey, Long> collected = new LinkedHashMap<>();
        private final Map<ResourceKey, Long> preexisting = new LinkedHashMap<>();
        private final Set<Object> unknownPorts = new HashSet<>();
        private final Map<Object, Supply> supplies = new LinkedHashMap<>();
        private int polls;
        private RuntimeException failure;
        private boolean settled, staged;
        private final class Supply {
            final NativeMachineBackend.Port port;
            final ResourceKey key;
            final long baseline;
            long pending, committed;
            int due;
            Supply(NativeMachineBackend.Supplemental request) {
                port = request.port(); key = request.key(); baseline = port.snapshot().getOrDefault(key, 0L);
            }
        }

        Attempt(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node,
                NativeMachineBackend.Machine machine, Binding binding, List<NativeMachineBackend.Port> inputs,
                List<NativeMachineBackend.Port> outputs, List<Integer> assigned, List<CraftPlan.Selection<ResourceKey>> selected) {
            this.access = access; this.position = position; this.node = node; this.machine = machine;
            this.binding = binding; this.inputs = inputs; this.outputs = outputs; this.assigned = assigned; this.selected = List.copyOf(selected);
            this.destinations = Set.copyOf(machine.claimedPositions());
            for (int i = 0; i < selected.size(); i++) pending.put(i, selected.get(i).count());
            for (var port : inputs) inputBaseline.put(port.identity(), Map.copyOf(port.retainedInputBaseline()));
            for (var port : outputs) baseline.put(port.identity(), new LinkedHashMap<>(port.snapshot()));
        }

        private boolean live() {
            var current = backend.machine(access, position);
            return machine.valid() && current != null && current.valid() && current.instance() == machine.instance()
                    && current.identity().equals(machine.identity()) && destinations.equals(Set.copyOf(current.claimedPositions()));
        }
        private void feed() {
            for (int i = 0; i < selected.size() && failure == null; i++) {
                long offered = pending.getOrDefault(i, 0L); if (offered == 0) continue;
                var selection = selected.get(i); var port = inputs.get(assigned.get(i));
                try {
                    long inserted = port.insert(selection.key(), offered, false);
                    if (inserted < 0 || inserted > offered) throw new IllegalStateException("Invalid native insertion quantity");
                    if (inserted > 0) {
                        if (inserted == offered) pending.remove(i); else pending.put(i, offered - inserted);
                        committed.computeIfAbsent(port.identity(), ignored -> new HashMap<>()).merge(selection.key(), inserted, Math::addExact);
                    }
                } catch (RuntimeException unknownCommit) {
                    pending.remove(i);
                    CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(selection.key(), offered));
                    unknownPorts.add(port.identity());
                    uncertain.add(machine.identity()); failure = unknownCommit;
                }
            }
            staged |= pending.isEmpty();
        }

        private void supplemental() {
            for (var request : machine.supplemental(access, binding.recipe(), node.selected())) {
                if (request.key() instanceof com.cappleapple.astralrepository.api.ItemKey item && !access.mayUseFuel(item)) continue;
                Supply existing = supplies.get(request.port().identity());
                if (existing != null && (existing.pending > 0 || !existing.key.equals(request.key()))) continue;
                Supply supply = existing == null ? new Supply(request) : existing;
                supplies.putIfAbsent(request.port().identity(), supply);
                Map<GlobalPos, Long> receipts = new LinkedHashMap<>();
                long taken;
                try { taken = access.reserveResource(request.key(), request.count(), (from, count) -> receipts.merge(from, count, Math::addExact)); }
                catch (RuntimeException unknownExtraction) {
                    CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(request.key(), request.count()));
                    failure = unknownExtraction; return;
                }
                if (taken < 0 || taken > request.count()) { failure = new IllegalStateException("Invalid native fuel reservation quantity"); return; }
                supply.pending = Math.addExact(supply.pending, taken);
                for (var receipt : receipts.entrySet()) {
                    int delay;
                    try { delay = Math.max(0, access.travelTicks(receipt.getKey(), position)); }
                    catch (RuntimeException invalidRoute) { failure = invalidRoute; return; }
                    supply.due = Math.max(supply.due, polls + delay);
                    try { access.visualResource(receipt.getKey(), position, request.key(), receipt.getValue(), delay); }
                    catch (RuntimeException cosmetic) { CraftingService.LOGGER.debug("Native fuel visual unavailable", cosmetic); }
                }
            }
            for (Supply supply : supplies.values()) if (supply.pending > 0 && polls >= supply.due && !unknownPorts.contains(supply.port.identity())) {
                long offered = supply.pending;
                try {
                    long inserted = supply.port.insert(supply.key, offered, false);
                    if (inserted < 0 || inserted > offered) throw new IllegalStateException("Invalid native fuel insertion quantity");
                    supply.pending -= inserted; supply.committed = Math.addExact(supply.committed, inserted);
                } catch (RuntimeException unknownCommit) {
                    supply.pending = 0; unknownPorts.add(supply.port.identity()); uncertain.add(machine.identity());
                    CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(supply.key, offered));
                    failure = unknownCommit; return;
                }
            }
        }

        private Map<ResourceKey, Long> produced() {
            Map<ResourceKey, Long> result = new HashMap<>(collected);
            for (var port : outputs) port.snapshot().forEach((key, amount) -> {
                long delta = amount - baseline.getOrDefault(port.identity(), Map.of()).getOrDefault(key, 0L);
                if (delta > 0) result.merge(key, delta, Math::addExact);
            });
            return result;
        }
        private boolean prepareStreaming() {
            boolean ready = true;
            for (var port : outputs) if (port.streamingOutput() && !unknownPorts.contains(port.identity())) {
                for (var entry : new ArrayList<>(baseline.get(port.identity()).entrySet())) {
                    if (entry.getValue() <= 0) continue;
                    long amount = Math.min(entry.getValue(), port.snapshot().getOrDefault(entry.getKey(), 0L));
                    if (amount < entry.getValue()) throw new IllegalStateException("Native streaming baseline changed before input delivery");
                    long taken;
                    try { taken = port.extract(entry.getKey(), amount, false); }
                    catch (NativeMachinePorts.ExtractedResources actual) {
                        actual.resources().forEach((key, count) -> preexisting.merge(key, count, Math::addExact));
                        unknownPorts.add(port.identity()); uncertain.add(machine.identity()); throw actual;
                    } catch (RuntimeException unknownCommit) {
                        unknownPorts.add(port.identity()); uncertain.add(machine.identity());
                        CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(entry.getKey(), amount));
                        throw unknownCommit;
                    }
                    if (taken < 0 || taken > amount) throw new IllegalStateException("Invalid native streaming baseline extraction");
                    if (taken > 0) {
                        preexisting.merge(entry.getKey(), taken, Math::addExact);
                        baseline.get(port.identity()).put(entry.getKey(), entry.getValue() - taken);
                    }
                    if (taken < amount) ready = false;
                }
            }
            return ready;
        }
        private boolean inputsConsumed() {
            return inputs.stream().allMatch(port -> committed.getOrDefault(port.identity(), Map.of()).entrySet().stream()
                    .allMatch(entry -> entry.getValue() <= 0 || port.snapshot().getOrDefault(entry.getKey(), 0L)
                            <= inputBaseline.getOrDefault(port.identity(), Map.of()).getOrDefault(entry.getKey(), 0L)));
        }
        @Override public Map<ResourceKey, Long> poll() {
            if (settled) return Map.of();
            var level = access.level().getServer().getLevel(position.dimension());
            if (level == null || !level.hasChunkAt(position.pos())) return null;
            if (!live()) throw new IllegalStateException("Native processor changed; physical contents remain in-world");
            if (failure != null) throw failure;
            inputs.forEach(NativeMachineBackend.Port::tickOwned);
            if (!staged && !prepareStreaming()) {
                if (++polls > machine.timeoutTicks(binding.recipe())) throw new IllegalStateException("Native streaming output could not be prepared");
                return null;
            }
            collect(true);
            Map<ResourceKey, Long> produced = produced();
            boolean guaranteed = !binding.recipe().guaranteed().isEmpty() && binding.recipe().guaranteed().entrySet().stream()
                    .allMatch(entry -> produced.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
            boolean anyProduced = binding.recipe().guaranteed().isEmpty() && produced.entrySet().stream()
                    .anyMatch(entry -> entry.getValue() > 0 && binding.recipe().possible().containsKey(entry.getKey()));
            boolean nativeComplete = machine.completed(binding.recipe(), polls, Map.copyOf(produced));
            boolean finished = staged && (inputsConsumed() || machine.allowsUnusedInputs(binding.recipe()) && nativeComplete) && (guaranteed || anyProduced || nativeComplete)
                    && (!machine.requiresNativeCompletion(binding.recipe()) || nativeComplete);
            if (finished) {
                collect(false);
                boolean remaining = outputs.stream().anyMatch(port -> port.snapshot().entrySet().stream().anyMatch(entry ->
                        entry.getValue() > baseline.getOrDefault(port.identity(), Map.of()).getOrDefault(entry.getKey(), 0L)));
                if (remaining) {
                    if (++polls > machine.timeoutTicks(binding.recipe())) throw new IllegalStateException("Native output is blocked at " + position.pos());
                    return null;
                }
                reclaim();
                if (!inputsConsumed()) {
                    if (++polls > machine.timeoutTicks(binding.recipe())) throw new IllegalStateException("Native unused inputs could not be recovered");
                    return null;
                }
                for (var port : inputs) for (var entry : inputBaseline.get(port.identity()).entrySet())
                    if (port.snapshot().getOrDefault(entry.getKey(), 0L) < entry.getValue())
                        throw new IllegalStateException("Native retained setup changed during processing");
                Map<ResourceKey, Long> result = owned();
                settled = true; pending.clear(); supplies.clear(); collected.clear(); preexisting.clear(); return result;
            }
            supplemental(); feed();
            if (++polls > machine.timeoutTicks(binding.recipe()))
                throw new IllegalStateException("Native processor stalled; check its power, mode, structure and setup at " + position.pos());
            return null;
        }
        private void collect(boolean streamingOnly) {
            for (var port : outputs) for (var entry : port.snapshot().entrySet()) {
                if (streamingOnly && !port.streamingOutput()) continue;
                if (unknownPorts.contains(port.identity())) continue;
                ResourceKey key = entry.getKey();
                long delta = entry.getValue() - baseline.getOrDefault(port.identity(), Map.of()).getOrDefault(key, 0L);
                long allowed = delta;
                if (allowed <= 0) continue;
                long taken;
                try { taken = port.extract(key, allowed, false); }
                catch (NativeMachinePorts.ExtractedResources actual) {
                    actual.resources().forEach((owned, amount) -> collected.merge(owned, amount, Math::addExact));
                    unknownPorts.add(port.identity()); uncertain.add(machine.identity()); throw actual;
                } catch (RuntimeException unknownCommit) {
                    unknownPorts.add(port.identity()); uncertain.add(machine.identity());
                    CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(key, allowed));
                    throw unknownCommit;
                }
                if (taken < 0 || taken > allowed) throw new IllegalStateException("Invalid native output quantity");
                if (taken > 0) collected.merge(key, taken, Math::addExact);
            }
        }
        private void reclaim() {
            for (var port : inputs) for (var entry : committed.getOrDefault(port.identity(), Map.of()).entrySet()) {
                if (unknownPorts.contains(port.identity())) continue;
                long amount = Math.min(entry.getValue(), Math.max(0, port.snapshot().getOrDefault(entry.getKey(), 0L)
                        - inputBaseline.getOrDefault(port.identity(), Map.of()).getOrDefault(entry.getKey(), 0L)));
                if (amount <= 0) continue;
                long taken;
                try { taken = port.extract(entry.getKey(), amount, false); }
                catch (NativeMachinePorts.ExtractedResources actual) {
                    actual.resources().forEach((owned, count) -> collected.merge(owned, count, Math::addExact));
                    unknownPorts.add(port.identity()); uncertain.add(machine.identity()); throw actual;
                } catch (RuntimeException unknownCommit) {
                    unknownPorts.add(port.identity()); uncertain.add(machine.identity());
                    CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(entry.getKey(), amount));
                    throw unknownCommit;
                }
                if (taken < 0 || taken > amount) throw new IllegalStateException("Invalid native recovery quantity");
                if (taken > 0) {
                    collected.merge(entry.getKey(), taken, Math::addExact);
                    committed.get(port.identity()).put(entry.getKey(), entry.getValue() - taken);
                }
            }
            for (Supply supply : supplies.values()) {
                if (unknownPorts.contains(supply.port.identity())) continue;
                long amount = Math.min(supply.committed, supply.port.snapshot().getOrDefault(supply.key, 0L) - supply.baseline);
                if (amount <= 0) continue;
                try {
                    long taken = supply.port.extract(supply.key, amount, false);
                    if (taken < 0 || taken > amount) throw new IllegalStateException("Invalid native fuel recovery quantity");
                    if (taken > 0) { collected.merge(supply.key, taken, Math::addExact); supply.committed -= taken; }
                } catch (NativeMachinePorts.ExtractedResources actual) {
                    actual.resources().forEach((owned, count) -> collected.merge(owned, count, Math::addExact));
                    unknownPorts.add(supply.port.identity()); uncertain.add(machine.identity()); throw actual;
                } catch (RuntimeException unknownCommit) {
                    unknownPorts.add(supply.port.identity()); uncertain.add(machine.identity());
                    CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(supply.key, amount));
                    throw unknownCommit;
                }
            }
        }
        private Map<ResourceKey, Long> owned() {
            Map<ResourceKey, Long> result = new LinkedHashMap<>(collected);
            preexisting.forEach((key, count) -> result.merge(key, count, Math::addExact));
            pending.forEach((index, count) -> result.merge(selected.get(index).key(), count, Math::addExact));
            supplies.values().forEach(supply -> { if (supply.pending > 0) result.merge(supply.key, supply.pending, Math::addExact); });
            return Map.copyOf(result);
        }
        @Override public Map<ResourceKey, Long> cancel() {
            if (settled) return Map.of();
            if (live()) {
                try { collect(false); reclaim(); }
                catch (RuntimeException incomplete) {
                    uncertain.add(machine.identity());
                    CraftingService.LOGGER.error("Native {} cleanup incomplete at {}; remaining resources stay in-world", backend.id(), position, incomplete);
                }
            }
            Map<ResourceKey, Long> result = owned(); settled = true; pending.clear(); supplies.clear(); collected.clear(); preexisting.clear(); return result;
        }
        @Override public Map<ResourceKey, Long> recoverable() { return settled ? Map.of() : owned(); }
        @Override public boolean mayReturnShortOutput() { return binding.shortOutput(); }
    }
}
