package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.lang.reflect.Proxy;
import java.lang.invoke.MethodType;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Thermal's grouped storage and native recipe-manager contracts, without a machine list. */
public final class ThermalBackend implements NativeMachineBackend {
    private static final String MACHINE = "cofh.thermal.lib.common.block.entity.MachineBlockEntity";
    private static final String MANAGER = "cofh.thermal.lib.util.managers.IRecipeManager";
    private static final String INVENTORY = "cofh.thermal.lib.util.recipes.IMachineInventory";
    private record Bound(Object manager, Object recipe, List<Object> configuration) {}
    public record Classification(String manager, String recipe, String status, String reason) {}
    private List<Classification> audit = List.of();
    public List<Classification> audit() { return audit; }
    @Override public String id() { return "thermal"; }
    @Override public Object identityAt(ServerLevel level, BlockPos position) {
        if (level == null || !level.hasChunkAt(position)) return null;
        Object entity = level.getBlockEntity(position);
        return contract(entity, MACHINE) ? entity : null;
    }
    @Override public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
        var level = access.level().getServer().getLevel(position.dimension());
        Object entity = identityAt(level, position.pos());
        return entity instanceof BlockEntity tile ? new Host(tile) : null;
    }

    private static final ClassValue<List<NativeBytecode.Reference>> MANAGERS = new ClassValue<>() {
        @Override protected List<NativeBytecode.Reference> computeValue(Class<?> type) {
            List<NativeBytecode.Reference> result = new ArrayList<>();
            for (var shape : NativeBytecode.hierarchy(type)) for (var method : shape.methods()) {
                if (!method.name().equals("cacheRecipe")) continue;
                for (var reference : method.references()) {
                    if (reference.opcode() != 184 || !reference.descriptor().startsWith("()L")) continue;
                    try {
                        Class<?> returns = MethodType.fromMethodDescriptorString(reference.descriptor(), type.getClassLoader()).returnType();
                        if (contract(returns, MANAGER)) result.add(reference);
                    } catch (LinkageError | RuntimeException unavailable) { /* Unsupported native provider is not guessed. */ }
                }
                if (!result.isEmpty()) return List.copyOf(result); // The most-derived native cache is authoritative.
            }
            return List.copyOf(result);
        }
    };
    private static List<Object> managers(Object entity) {
        return MANAGERS.get(entity.getClass()).stream().map(reference -> {
            Class<?> owner = optionalClass(reference.owner());
            return owner == null ? null : call(owner, reference.name());
        }).filter(Objects::nonNull).distinct().toList();
    }

    private static List<Object> configuration(Object entity) {
        List<Object> result = new ArrayList<>();
        Object properties = call(entity, "getMachineProperties");
        for (String getter : List.of("getPrimaryMod", "getSecondaryMod", "getEnergyMod", "getMinOutputChance", "getUseChance"))
            result.add(call(properties, getter));
        Object inventory = field(entity, "inventory");
        for (Object catalyst : list(field(inventory, "catalystSlots"))) {
            ItemStack stack = item(call(catalyst, "getItemStack"));
            result.add(stack.isEmpty() ? "empty" : new ItemKey(stack));
        }
        return List.copyOf(result);
    }

    @Override public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> loadedMachines) {
        List<Recipe> result = new ArrayList<>(); List<Classification> classified = new ArrayList<>();
        Set<List<Object>> seen = new HashSet<>();
        var registered = NativeRecipeSource.entries(access.level());
        Map<Object, List<NativeRecipeSource.Entry>> byManager = new IdentityHashMap<>();
        for (var candidate : loadedMachines) if (candidate instanceof Host host) for (Object manager : host.managers) {
            if (!seen.add(List.of(manager, host.configuration))) continue;
            var originalRecipes = byManager.computeIfAbsent(manager, key -> originalRecipes(key, registered));
            for (Object nativeRecipe : list(call(manager, "getRecipeList"))) {
                String provider = manager.getClass().getName();
                try {
                    List<?> itemInputs = list(call(nativeRecipe, "getInputItems"));
                    List<?> fluidInputs = list(call(nativeRecipe, "getInputFluids"));
                    List<CraftRecipe.Ingredient<ResourceKey>> inputs = new ArrayList<>();
                    for (Object value : itemInputs) {
                        ItemStack stack = item(value);
                        if (!stack.isEmpty()) inputs.add(new CraftRecipe.Ingredient<>(List.of(new ItemKey(stack)), stack.getCount()));
                    }
                    for (Object value : fluidInputs) if (MekanismResourceSupport.amount(value) > 0)
                        inputs.add(new CraftRecipe.Ingredient<>(List.of(MekanismResourceSupport.key(value)), MekanismResourceSupport.amount(value)));
                    if (inputs.isEmpty()) throw new IllegalArgumentException("Native producer has no consumed inventory resource");
                    Object preview = preview(host.entity, itemInputs, fluidInputs);
                    List<?> items = list(call(nativeRecipe, "getOutputItems", preview));
                    List<?> chances = list(call(nativeRecipe, "getOutputItemChances", preview));
                    Map<ResourceKey, Long> guaranteed = new LinkedHashMap<>(), possible = new LinkedHashMap<>();
                    for (int index = 0; index < items.size(); index++) {
                        ItemStack stack = item(items.get(index)); if (stack.isEmpty()) continue;
                        double chance = index < chances.size() ? ((Number) chances.get(index)).doubleValue() : 1;
                        if (!Double.isFinite(chance) || chance < 0) throw new IllegalArgumentException("Invalid native output chance");
                        long minimum = Math.multiplyExact(stack.getCount(), (long) Math.floor(chance));
                        long maximum = Math.multiplyExact(stack.getCount(), (long) Math.ceil(chance));
                        if (maximum > 0) possible.merge(new ItemKey(stack), maximum, Math::addExact);
                        if (minimum > 0) guaranteed.merge(new ItemKey(stack), minimum, Math::addExact);
                    }
                    for (Object value : list(call(nativeRecipe, "getOutputFluids", preview))) if (MekanismResourceSupport.amount(value) > 0) {
                        ResourceKey key = MekanismResourceSupport.key(value); long count = MekanismResourceSupport.amount(value);
                        guaranteed.merge(key, count, Math::addExact); possible.merge(key, count, Math::addExact);
                    }
                    if (possible.isEmpty()) throw new IllegalArgumentException("Native recipe produces no transferable output");
                    String original = originalRecipeId(access, manager, nativeRecipe, itemInputs, fluidInputs, originalRecipes);
                    int duration = Math.max(1, (int) Math.ceil(((Number) call(nativeRecipe, "getEnergy", preview)).doubleValue() / Math.max(1, number(call(host.entity, "getCurSpeed"), 1))));
                    String process = provider + "/" + NativeMachineAdapter.profile(access.level(), host.configuration);
                    result.add(new Recipe(original, process, inputs, guaranteed, possible, duration, Math.max(7200, duration * 32),
                            new Bound(manager, nativeRecipe, host.configuration), !guaranteed.equals(possible)));
                    classified.add(new Classification(provider, original, "native", "shared recipe-manager and grouped port contracts"));
                } catch (RuntimeException | LinkageError unsupported) {
                    classified.add(new Classification(provider, nativeRecipe.getClass().getName(), "unsupported", String.valueOf(unsupported.getMessage())));
                }
            }
        }
        audit = List.copyOf(classified); return List.copyOf(result);
    }

    private static Object preview(Object machine, List<?> inputItems, List<?> inputFluids) {
        Class<?> api = optionalClass(INVENTORY);
        List<Object> slots = new ArrayList<>();
        for (Object source : list(call(machine, "inputSlots"))) slots.add(copyStorage(source));
        Set<Object> catalysts = Collections.newSetFromMap(new IdentityHashMap<>());
        catalysts.addAll(list(field(field(machine, "inventory"), "catalystSlots")));
        List<?> realSlots = list(call(machine, "inputSlots")); int next = 0;
        for (int index = 0; index < slots.size(); index++) {
            if (catalysts.contains(realSlots.get(index))) continue;
            call(slots.get(index), "setItemStack", next < inputItems.size() ? item(inputItems.get(next++)) : ItemStack.EMPTY);
        }
        List<Object> tanks = new ArrayList<>();
        for (Object source : list(call(machine, "inputTanks"))) tanks.add(copyStorage(source));
        for (int index = 0; index < tanks.size(); index++) if (index < inputFluids.size()) call(tanks.get(index), "setFluidStack", inputFluids.get(index));
        return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) -> switch (method.getName()) {
            case "inputSlots" -> slots;
            case "inputTanks" -> tanks;
            case "getMachineProperties" -> call(machine, "getMachineProperties");
            case "toString" -> "Astral native Thermal preview";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new IllegalStateException("Unsupported native preview call " + method.getName());
        });
    }
    private static Object copyStorage(Object source) {
        try {
            if (has(source, "getItemStack")) {
                Object result = optionalClass("cofh.lib.common.inventory.ItemStorageCoFH").getConstructor().newInstance();
                call(result, "setItemStack", item(call(source, "getItemStack"))); return result;
            }
            Object result = optionalClass("cofh.lib.common.fluid.FluidStorageCoFH").getConstructor(int.class).newInstance(Integer.MAX_VALUE);
            Object fluid = call(source, "getFluidStack"); call(result, "setFluidStack", call(fluid, "copy")); return result;
        } catch (ReflectiveOperationException unavailable) { throw new IllegalStateException("Native detached storage unavailable", unavailable); }
    }

    private static List<NativeRecipeSource.Entry> originalRecipes(Object manager, List<NativeRecipeSource.Entry> registered) {
        List<NativeRecipeSource.Entry> candidates = new ArrayList<>();
        for (Object converted : list(optional(manager, "getConvertedRecipes"))) {
            var entry = NativeRecipeSource.entry(converted); if (entry != null) candidates.add(entry);
        }
        candidates.addAll(registered);
        Set<Object> nativeTypes = recipeTypes(manager);
        return candidates.stream().filter(holder -> nativeTypes.isEmpty() || nativeTypes.contains(NativeRecipeSource.type(holder.value())))
                .filter(holder -> has(holder.value(), "getInputItems") && has(holder.value(), "getInputFluids")).toList();
    }
    private static String originalRecipeId(CraftingService.NetworkAccess access, Object manager, Object nativeRecipe,
                                            List<?> items, List<?> fluids, List<NativeRecipeSource.Entry> candidates) {
        for (var holder : candidates) {
            Object value = holder.value();
            List<?> itemIngredients = list(call(value, "getInputItems")), fluidIngredients = list(call(value, "getInputFluids"));
            if (itemIngredients.size() != items.size() || fluidIngredients.size() != fluids.size()) continue;
            boolean matches = true;
            for (int i = 0; i < items.size() && matches; i++) matches = Boolean.TRUE.equals(optional(itemIngredients.get(i), "test", items.get(i)));
            for (int i = 0; i < fluids.size() && matches; i++) matches = Boolean.TRUE.equals(optional(fluidIngredients.get(i), "test", fluids.get(i)));
            if (!matches) continue;
            return holder.id().toString();
        }
        // Converted/dynamic manager knowledge has no datapack ID; keep its own stable native identity.
        List<Object> inputs = new ArrayList<>();
        for (Object value : items) { ItemStack stack = item(value); if (!stack.isEmpty()) inputs.add(List.of(new ItemKey(stack), stack.getCount())); }
        for (Object value : fluids) if (MekanismResourceSupport.amount(value) > 0) inputs.add(List.of(MekanismResourceSupport.key(value), MekanismResourceSupport.amount(value)));
        return "thermal:manager/" + manager.getClass().getSimpleName().toLowerCase(Locale.ROOT) + "/" + NativeMachineAdapter.profile(access.level(), inputs);
    }
    private static Set<Object> recipeTypes(Object manager) {
        Set<Object> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var shape : NativeBytecode.hierarchy(manager.getClass())) for (var method : shape.methods()) {
            if (!method.name().equals("refresh")) continue;
            for (var reference : method.references()) if (reference.staticRead()) {
                try {
                    Object candidate = reference.readStatic(manager.getClass().getClassLoader());
                    if (candidate instanceof java.util.function.Supplier<?> supplier) candidate = supplier.get();
                    if (candidate instanceof net.minecraft.world.item.crafting.RecipeType<?>) result.add(candidate);
                } catch (RuntimeException | LinkageError unrelated) { /* Only the actual manager's recipe providers apply. */ }
            }
            if (!result.isEmpty()) return result;
        }
        return result;
    }

    private static final class Host implements Machine {
        final BlockEntity entity; final List<Object> managers, configuration;
        final boolean initiallyActive;
        Host(BlockEntity entity) {
            this.entity = entity; managers = managers(entity); configuration = configuration(entity);
            initiallyActive = Boolean.TRUE.equals(field(entity, "isActive"));
        }
        public Object identity() { return entity; }
        public Object instance() { return entity; }
        public boolean valid() { return !entity.isRemoved() && entity.getLevel() != null && entity.getLevel().getBlockEntity(entity.getBlockPos()) == entity; }
        public boolean accepts(Recipe recipe) { return recipe.nativeRecipe() instanceof Bound bound && managers.contains(bound.manager()) && configuration.equals(bound.configuration()); }
        public boolean ready(Recipe recipe, List<CraftPlan.Selection<ResourceKey>> selected) { return !Boolean.TRUE.equals(field(entity, "isActive")) && number(field(entity, "process"), 0) <= 0; }
        public List<GlobalPos> claimedPositions() { return List.of(GlobalPos.of(entity.getLevel().dimension(), entity.getBlockPos())); }
        public Object catalogState() { return List.of(configuration, managers.stream().map(manager -> List.copyOf(list(call(manager, "getRecipeList")))).toList()); }
        public boolean completed(Recipe recipe, int polls) { return polls > 0 && !Boolean.TRUE.equals(field(entity, "isActive")) && number(field(entity, "process"), 0) <= 0; }
        public List<Port> ports(Recipe recipe) {
            List<Port> ports = new ArrayList<>();
            Set<Object> catalysts = Collections.newSetFromMap(new IdentityHashMap<>());
            catalysts.addAll(list(field(field(entity, "inventory"), "catalystSlots")));
            for (Object slot : list(call(entity, "inputSlots"))) if (!catalysts.contains(slot)) ports.add(NativeMachinePorts.itemSlot(slot, 0, true, false));
            for (Object slot : list(call(entity, "outputSlots"))) ports.add(NativeMachinePorts.itemSlot(slot, 0, false, true));
            for (Object tank : list(call(entity, "inputTanks"))) ports.add(fluid(tank, true, false));
            for (Object tank : list(call(entity, "outputTanks"))) ports.add(fluid(tank, false, true));
            return List.copyOf(ports);
        }
    }
    private static Port fluid(Object tank, boolean input, boolean output) {
        Class<?> action = optionalClass("net.neoforged.neoforge.fluids.capability.IFluidHandler$FluidAction");
        if (action == null) action = optionalClass("net.minecraftforge.fluids.capability.IFluidHandler$FluidAction");
        Class<?> actions = action;
        return NativeMachinePorts.callbacks(tank, input, output, () -> {
            Object stack = call(tank, "getFluidStack"); long amount = MekanismResourceSupport.amount(stack);
            return amount > 0 ? Map.of(MekanismResourceSupport.key(stack), amount) : Map.of();
        }, (key, amount, simulate) -> {
            if (!(key instanceof FluidKey)) return 0;
            Object nativeAction = enumValue(actions, simulate ? "SIMULATE" : "EXECUTE");
            return number(call(tank, "fill", MekanismResourceSupport.sample(key, Math.min(amount, Integer.MAX_VALUE)), nativeAction), 0);
        }, (key, amount, simulate) -> {
            if (!(key instanceof FluidKey)) return 0;
            Object stack = call(tank, "drain", MekanismResourceSupport.sample(key, Math.min(amount, Integer.MAX_VALUE)), enumValue(actions, simulate ? "SIMULATE" : "EXECUTE"));
            long actual = MekanismResourceSupport.amount(stack);
            if (actual > 0 && !MekanismResourceSupport.key(stack).equals(key)) throw new IllegalStateException("Native Thermal fluid identity changed");
            return actual;
        });
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static Object enumValue(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }
}
