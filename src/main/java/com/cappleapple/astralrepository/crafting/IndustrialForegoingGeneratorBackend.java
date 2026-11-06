package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.lang.reflect.Array;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Native Mycelial fuel providers; production and fuel consumption remain in the real generator. */
public final class IndustrialForegoingGeneratorBackend implements NativeMachineBackend {
    private static final String TYPE = "com.buuz135.industrial.block.generator.mycelial.IMycelialGeneratorType";
    private static final String GENERATOR = "com.hrznstudio.titanium.block.tile.GeneratorTile";
    private static final String INVENTORY = "com.hrznstudio.titanium.component.inventory.InventoryComponent";
    private static final String TANK = "com.hrznstudio.titanium.component.fluid.FluidTankComponent";
    private static final String PROGRESS = "com.hrznstudio.titanium.component.progress.ProgressBarComponent";
    private record Surface(NativeBytecode.Reference type, NativeBytecode.Reference inputs, NativeBytecode.Reference progress) {}
    private record Role(String name, int slot, boolean fluid) {}
    private record Binding(String type, Map<Role, TitaniumRecipeEvaluator.Quantity> inputs,
                           Map<Role, TitaniumRecipeEvaluator.Quantity> remainders, int ticks, int power, Object state) {}
    public record Diagnostic(String subject, String status, String reason) {}
    public record Audit(int nativeTypes, int physicalHostTypes, int declaredDefinitions, int catalogProfiles, List<Diagnostic> diagnostics) {}
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int declaredDefinitions;
    private static final ClassValue<Surface> SURFACES = new ClassValue<>() {
        @Override protected Surface computeValue(Class<?> type) {
            if (!contract(type, GENERATOR)) return new Surface(null, null, null);
            NativeBytecode.Reference provider = null, inputs = null;
            for (Class<?> current = type; current != null && !current.getName().equals(GENERATOR); current = current.getSuperclass()) {
                var shape = NativeBytecode.inspect(current);
                for (var field : shape.fields()) {
                    if ((field.access() & 8) != 0) continue;
                    if (field.descriptor().equals("L" + TYPE.replace('.', '/') + ";")) provider = new NativeBytecode.Reference(180, shape.name(), field.name(), field.descriptor());
                    if (field.descriptor().startsWith("[") && field.descriptor().endsWith("/common/util/INBTSerializable;")) inputs = new NativeBytecode.Reference(180, shape.name(), field.name(), field.descriptor());
                }
            }
            NativeBytecode.Reference progress = null;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) if (current.getName().equals(GENERATOR)) {
                var shape = NativeBytecode.inspect(current);
                for (var field : shape.fields()) if ((field.access() & 8) == 0 && field.descriptor().equals("L" + PROGRESS.replace('.', '/') + ";"))
                    progress = new NativeBytecode.Reference(180, shape.name(), field.name(), field.descriptor());
            }
            return new Surface(provider, inputs, progress);
        }
    };
    @Override public String id() { return "industrialforegoing_generators"; }
    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }
    @Override public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
        ServerLevel level = access.level().getServer().getLevel(position.dimension()); return find(level, position.pos());
    }
    @Override public Object identityAt(ServerLevel level, net.minecraft.core.BlockPos position) { var host = find(level, position); return host == null ? null : host.identity(); }
    private Host find(ServerLevel level, net.minecraft.core.BlockPos position) {
        if (level == null || !level.hasChunkAt(position)) return null;
        BlockEntity tile = level.getBlockEntity(position); if (tile == null) return null;
        Surface surface = SURFACES.get(tile.getClass());
        if (surface.type() == null || surface.inputs() == null) return null;
        Object type = surface.type().readInstance(tile), inputs = surface.inputs().readInstance(tile);
        return type != null && inputs != null ? new Host(level, tile, type, inputs) : null;
    }
    @Override public List<NativeMachineBackend.Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> machines) {
        return catalog(access.level(), access.resourceSnapshot(), machines);
    }
    private List<NativeMachineBackend.Recipe> catalog(ServerLevel level, Map<ResourceKey,Long> stock, List<Machine> machines) {
        diagnostics.clear(); declaredDefinitions = 0; List<NativeMachineBackend.Recipe> result = new ArrayList<>();
        NativeStockSamples indexed = new NativeStockSamples(stock);
        Set<String> seen = new HashSet<>();
        for (Machine machine : machines) if (machine instanceof Host host) {
            try {
                Object definitions = NativeBackendReflection.optional(host.type, "getRecipes", level.registryAccess());
                if (definitions == null) definitions = NativeBackendReflection.call(host.type, "getRecipes");
                int index = 0;
                for (Object definition : NativeBackendReflection.list(definitions)) {
                    declaredDefinitions++;
                    String subject = host.typeName() + "/" + index++;
                    try {
                        var choices = choices(host, definition, indexed);
                        if (choices.isEmpty()) throw new UnsupportedOperationException("No declared consumed item/fluid fuel; ambient generation is a native resource source");
                        enumerate(host, subject, choices, 0, new LinkedHashMap<>(), result, seen);
                    } catch (RuntimeException unsupported) { diagnostics.add(new Diagnostic(subject, "UNSUPPORTED", reason(unsupported))); }
                }
            } catch (RuntimeException unsupported) { diagnostics.add(new Diagnostic(host.typeName(), "PROVIDER_UNAVAILABLE", reason(unsupported))); }
        }
        return List.copyOf(result);
    }
    /** Audits native provider data using detached, un-ticked native host buffers. */
    public Audit audit(ServerLevel level) {
        List<Machine> machines = new ArrayList<>(); Set<Object> types = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Diagnostic> unavailable = new ArrayList<>();
        for (var block : BuiltInRegistries.BLOCK) if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("industrialforegoing") && block instanceof net.minecraft.world.level.block.EntityBlock factory) {
            try {
                BlockEntity tile = factory.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()); if (tile == null) continue;
                Surface surface = SURFACES.get(tile.getClass()); if (surface.type() == null || surface.inputs() == null) continue;
                Object type = surface.type().readInstance(tile), inputs = surface.inputs().readInstance(tile);
                if (type != null && inputs != null && types.add(type)) machines.add(new Host(level, tile, type, inputs));
            } catch (RuntimeException | LinkageError failure) { unavailable.add(new Diagnostic(BuiltInRegistries.BLOCK.getKey(block).toString(), "HOST_UNAVAILABLE", reason(failure))); }
        }
        var profiles = catalog(level, Map.of(), machines);
        int registered = 0;
        try {
            Object registry = new NativeBytecode.Reference(178, TYPE, "TYPES", "Ljava/util/List;").readStatic(getClass().getClassLoader());
            for (Object type : NativeBackendReflection.list(registry)) {
                registered++;
                if (!types.contains(type)) unavailable.add(new Diagnostic(call(type, TYPE, "getName").toString(), "NO_REGISTERED_HOST", "Native fuel provider has no discoverable registered generator buffers"));
            }
        } catch (RuntimeException failure) { unavailable.add(new Diagnostic(TYPE, "REGISTRY_UNAVAILABLE", reason(failure))); }
        List<Diagnostic> combined = new ArrayList<>(diagnostics); combined.addAll(unavailable);
        return new Audit(registered, types.size(), declaredDefinitions, profiles.size(), List.copyOf(combined));
    }
    private record Choices(TitaniumRecipeEvaluator.Address address, List<TitaniumRecipeEvaluator.Quantity> alternatives) {}
    private static List<Choices> choices(Host host, Object definition, NativeStockSamples stock) {
        List<?> itemDefinitions = NativeBackendReflection.list(NativeBackendReflection.call(definition, "getInputItems"));
        List<?> fluidDefinitions = NativeBackendReflection.list(NativeBackendReflection.call(definition, "getFluidItems"));
        List<TitaniumRecipeEvaluator.Address> items = host.addresses().values().stream().filter(address -> !address.fluid()).toList();
        List<TitaniumRecipeEvaluator.Address> fluids = host.addresses().values().stream().filter(TitaniumRecipeEvaluator.Address::fluid).toList();
        List<TitaniumRecipeEvaluator.Address> nativeOrder = List.copyOf(host.addresses().values());
        boolean itemPositions = itemDefinitions.size() == nativeOrder.size(), fluidPositions = fluidDefinitions.size() == nativeOrder.size();
        List<Choices> result = new ArrayList<>(); long combinations = 1;
        for (int slot = 0; slot < itemDefinitions.size(); slot++) {
            Map<ResourceKey, Long> alternatives = new LinkedHashMap<>();
            for (Object candidate : NativeBackendReflection.list(itemDefinitions.get(slot))) if (candidate instanceof Ingredient ingredient) {
                for (ItemStack stack : stock.representations(ingredient)) {
                    ItemStack normalized = NativeRecipeSource.normalizeDisplay(stack, host.level.registryAccess());
                    alternatives.put(new ItemKey(normalized), (long)Math.max(1, normalized.getCount()));
                }
                for (ItemKey key : stock.matching(ingredient)) alternatives.putIfAbsent(key, 1L);
            }
            if (alternatives.isEmpty()) continue; // Positional native displays retain empty fluid/output placeholders.
            if (itemPositions ? nativeOrder.get(slot).fluid() : slot >= items.size()) throw new UnsupportedOperationException("Native fuel display item role disagrees with actual native buffers");
            var address = itemPositions ? nativeOrder.get(slot) : items.get(slot);
            alternatives.entrySet().removeIf(entry -> !TitaniumRecipeEvaluator.accepts(address, entry.getKey()));
            if (alternatives.isEmpty()) continue; // Native output/remainder slots use an empty ingredient.
            combinations = Math.multiplyExact(combinations, alternatives.size());
            result.add(new Choices(address, alternatives.entrySet().stream().map(entry -> new TitaniumRecipeEvaluator.Quantity(entry.getKey(), entry.getValue())).toList()));
        }
        for (int slot = 0; slot < fluidDefinitions.size(); slot++) {
            Map<ResourceKey, Long> alternatives = new LinkedHashMap<>();
            for (Object candidate : NativeBackendReflection.list(fluidDefinitions.get(slot))) if (candidate instanceof FluidStack stack && !stack.isEmpty()) alternatives.put(new FluidKey(stack), (long)stack.getAmount());
            if (alternatives.isEmpty()) continue;
            if (fluidPositions ? !nativeOrder.get(slot).fluid() : slot >= fluids.size()) throw new UnsupportedOperationException("Native fuel display fluid role disagrees with actual native buffers");
            var address = fluidPositions ? nativeOrder.get(slot) : fluids.get(slot); alternatives.entrySet().removeIf(entry -> !TitaniumRecipeEvaluator.accepts(address, entry.getKey()));
            if (alternatives.isEmpty()) continue;
            combinations = Math.multiplyExact(combinations, alternatives.size());
            result.add(new Choices(address, alternatives.entrySet().stream().map(entry -> new TitaniumRecipeEvaluator.Quantity(entry.getKey(), entry.getValue())).toList()));
        }
        if (combinations > 8192) throw new UnsupportedOperationException("Native fuel candidate product exceeds8192");
        return List.copyOf(result);
    }
    private void enumerate(Host host, String subject, List<Choices> choices, int index, Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> seed,
                           List<NativeMachineBackend.Recipe> recipes, Set<String> seen) {
        if (index < choices.size()) {
            Choices input = choices.get(index);
            for (var alternative : input.alternatives()) { seed.put(input.address(), alternative); enumerate(host, subject, choices, index + 1, seed, recipes, seen); }
            seed.remove(input.address()); return;
        }
        String phase = "native fuel preview";
        try {
            var fuel = new TitaniumRecipeEvaluator(host.tile, seed).fuel(host.type, host.inputs);
            if (fuel == null || fuel.ticks() < 1 || fuel.powerPerTick() < 1) throw new UnsupportedOperationException("Native generator rejects this exact fuel identity");
            phase = "native buffer roles";
            var consumed = roles(fuel.resources().consumed());
            var remainders = roles(fuel.resources().produced());
            if (consumed.isEmpty()) throw new UnsupportedOperationException("Native generation has no consumed inventory fuel");
            // A display may provide excess fuel. The actual native contract determines one-cycle ownership.
            Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> exact = new LinkedHashMap<>();
            fuel.resources().consumed().forEach(exact::put);
            phase = "minimal native fuel preview";
            var checked = new TitaniumRecipeEvaluator(host.tile, exact).fuel(host.type, host.inputs);
            if (checked == null || checked.ticks() != fuel.ticks() || checked.powerPerTick() != fuel.powerPerTick() || !checked.resources().consumed().equals(fuel.resources().consumed()) || !checked.resources().produced().equals(fuel.resources().produced()))
                throw new UnsupportedOperationException("Native fuel depends on additional retained setup or variable batch contents");
            Map<ResourceKey, Long> outputs = new LinkedHashMap<>(); outputs.put(ResourceKinds.FE, Math.multiplyExact((long)fuel.ticks(), fuel.powerPerTick()));
            fuel.resources().produced().values().forEach(value -> outputs.merge(value.key(), value.count(), Math::addExact));
            List<CraftRecipe.Ingredient<ResourceKey>> inputs = consumed.values().stream().map(value -> new CraftRecipe.Ingredient<>(List.of(value.key()), value.count())).toList();
            phase = "native machine state";
            Object state = host.catalogState();
            List<Object> signatures = consumed.entrySet().stream().map(entry -> (Object)Map.of("role", entry.getKey().toString(), "resource", entry.getValue().key(), "count", entry.getValue().count())).toList();
            String fingerprint = NativeMachineAdapter.profile(host.level, Map.of("state", state, "inputs", signatures, "outputs", outputs, "ticks", fuel.ticks()));
            String id = "industrialforegoing:generator/" + host.typeName() + "/" + fingerprint;
            if (seen.add(id)) recipes.add(new NativeMachineBackend.Recipe(id, host.typeName() + "/" + fingerprint, inputs, outputs, outputs, fuel.ticks(), Math.max(7200, fuel.ticks() * 4), new Binding(host.typeName(), consumed, remainders, fuel.ticks(), fuel.powerPerTick(), state), false));
            diagnostics.add(new Diagnostic(subject, "CATALOGUED", id));
        } catch (RuntimeException unsupported) { diagnostics.add(new Diagnostic(subject, "UNSUPPORTED", phase + ": " + reason(unsupported))); }
    }
    private static Map<Role, TitaniumRecipeEvaluator.Quantity> roles(Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> values) {
        Map<Role, TitaniumRecipeEvaluator.Quantity> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Comparator.comparing(entry -> role(entry.getKey()).toString())).forEach(entry -> result.put(role(entry.getKey()), entry.getValue()));
        return Collections.unmodifiableMap(result);
    }
    private static Role role(TitaniumRecipeEvaluator.Address address) { return new Role(call(address.owner(), address.fluid() ? TANK : INVENTORY, "getName").toString(), address.slot(), address.fluid()); }
    private final class Host implements Machine {
        final ServerLevel level; final BlockEntity tile; final Object type, inputs; boolean observedRunning;
        Host(ServerLevel level, BlockEntity tile, Object type, Object inputs) { this.level = level; this.tile = tile; this.type = type; this.inputs = inputs; }
        String typeName() { return call(type, TYPE, "getName").toString(); }
        @Override public Object identity() { return tile; }
        @Override public Object instance() { return tile; }
        @Override public boolean valid() { return !tile.isRemoved() && level.hasChunkAt(tile.getBlockPos()) && level.getBlockEntity(tile.getBlockPos()) == tile; }
        @Override public boolean accepts(NativeMachineBackend.Recipe recipe) { return recipe.nativeRecipe() instanceof Binding binding && typeName().equals(binding.type()) && catalogState().equals(binding.state()); }
        @Override public boolean ready(NativeMachineBackend.Recipe recipe, List<CraftPlan.Selection<ResourceKey>> selected) {
            Object bar = progress();
            return ((Number)call(bar, PROGRESS, "getProgress")).intValue() == 0;
        }
        @Override public Object catalogState() { return List.of(type.getClass().getName(), typeName(), ((Number)NativeBackendReflection.call(energy(), "getMaxEnergyStored")).longValue()); }
        @Override public boolean requiresNativeCompletion(NativeMachineBackend.Recipe recipe) { return true; }
        @Override public boolean completed(NativeMachineBackend.Recipe recipe, int polls, Map<ResourceKey, Long> produced) {
            Object bar = progress(); int progress = ((Number)call(bar, PROGRESS, "getProgress")).intValue();
            observedRunning |= progress > 0;
            return observedRunning && progress == 0 && polls > 0;
        }
        @Override public List<GlobalPos> claimedPositions() { return List.of(GlobalPos.of(level.dimension(), tile.getBlockPos())); }
        Object progress() {
            var reference = SURFACES.get(tile.getClass()).progress();
            if (reference == null) throw new IllegalStateException("Native generator progress component unavailable");
            Object component = reference.readInstance(tile);
            if (component == null) throw new IllegalStateException("Native generator progress component is uninitialized");
            return component;
        }
        Object energy() { return call(tile, "com.hrznstudio.titanium.block.tile.PoweredTile", "getEnergyStorage"); }
        Map<Role, TitaniumRecipeEvaluator.Address> addresses() {
            Map<Role, TitaniumRecipeEvaluator.Address> result = new LinkedHashMap<>();
            for (int index = 0; index < Array.getLength(inputs); index++) {
                Object input = Array.get(inputs, index);
                if (input instanceof IItemHandler inventory) for (int slot = 0; slot < inventory.getSlots(); slot++) { var address = new TitaniumRecipeEvaluator.Address(input, slot, false); result.put(role(address), address); }
                else { var address = new TitaniumRecipeEvaluator.Address(input, 0, true); result.put(role(address), address); }
            }
            return result;
        }
        @Override public List<Port> ports(NativeMachineBackend.Recipe recipe) {
            Binding binding = (Binding)recipe.nativeRecipe(); Map<Role, TitaniumRecipeEvaluator.Address> addresses = addresses(); List<Port> result = new ArrayList<>();
            Set<Role> roles = new LinkedHashSet<>(binding.inputs().keySet()); roles.addAll(binding.remainders().keySet());
            for (Role role : roles) {
                var address = addresses.get(role); if (address == null) throw new IllegalStateException("Native generator role disappeared: " + role);
                var required = binding.inputs().get(role); boolean input = required != null, output = binding.remainders().containsKey(role);
                Port delegate = port(address, input, output);
                result.add(NativeMachinePorts.callbacks(delegate.identity(), input, output, delegate::snapshot,
                        (key, count, simulate) -> required != null && required.key().equals(key) && count <= required.count() ? delegate.insert(key, count, simulate) : 0, delegate::extract));
            }
            result.add(NativeMachinePorts.streaming(NativeMachinePorts.energy(energy(), false, true)));
            return List.copyOf(result);
        }
    }
    private static Port port(TitaniumRecipeEvaluator.Address address, boolean input, boolean output) {
        if (!address.fluid()) {
            IItemHandler inventory = (IItemHandler)address.owner(); Port delegate = NativeMachinePorts.itemSlot(inventory, address.slot(), input, output);
            return NativeMachinePorts.callbacks(delegate.identity(), input, output, delegate::snapshot,
                    (key, amount, simulate) -> key instanceof ItemKey item && inventory.isItemValid(address.slot(), item.sample()) ? delegate.insert(key, amount, simulate) : 0, delegate::extract);
        }
        Object tank = address.owner();
        return NativeMachinePorts.callbacks(NativeMachinePorts.identity(tank, 0, "fluid"), input, output, () -> {
            FluidStack stack = (FluidStack)call(tank, TANK, "getFluid"); return stack.isEmpty() ? Map.of() : Map.of(new FluidKey(stack), (long)stack.getAmount());
        }, (key, amount, simulate) -> key instanceof FluidKey fluid ? ((Number)IndustrialForegoingBackend.call(tank, TANK, "fill", new Class<?>[]{FluidStack.class,IFluidHandler.FluidAction.class}, fluid.sample().copyWithAmount((int)Math.min(Integer.MAX_VALUE, amount)), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE)).longValue() : 0,
        (key, amount, simulate) -> {
            if (!(key instanceof FluidKey fluid)) return 0;
            FluidStack actual = (FluidStack)IndustrialForegoingBackend.call(tank, TANK, "drainForced", new Class<?>[]{FluidStack.class,IFluidHandler.FluidAction.class}, fluid.sample().copyWithAmount((int)Math.min(Integer.MAX_VALUE, amount)), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (!actual.isEmpty() && (!new FluidKey(actual).equals(key) || actual.getAmount() > amount)) {
                if (!simulate) throw new NativeMachinePorts.ExtractedResources("Native generator extracted a different fluid", Map.of(new FluidKey(actual), (long)actual.getAmount()));
                throw new IllegalStateException("Native generator fluid extraction simulation mismatch");
            }
            return actual.getAmount();
        });
    }
    private static boolean contract(Class<?> type, String name) { if (type == null) return false; if (type.getName().equals(name)) return true; for (Class<?> api : type.getInterfaces()) if (contract(api, name)) return true; return contract(type.getSuperclass(), name); }
    private static Object call(Object target, String owner, String method) { return IndustrialForegoingBackend.call(target, owner, method); }
    private static String reason(Throwable failure) { String text = failure.getMessage(); return text == null ? failure.getClass().getName() : text.length() > 800 ? text.substring(0,800) : text; }
}
