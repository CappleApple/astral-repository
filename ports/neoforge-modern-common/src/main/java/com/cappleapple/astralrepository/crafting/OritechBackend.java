package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Oritech's recipe, slot-assignment and native storage contracts, independent of block IDs. */
public final class OritechBackend implements NativeMachineBackend {
    private static final String MACHINE = "rearth.oritech.block.base.entity.MachineBlockEntity";
    private static final String NATIVE_RECIPE = "rearth.oritech.init.recipes.OritechRecipe";
    private record Binding(Object recipe, Object type, Map<ResourceKey, Long> outputs) {}
    private record ItemPortIdentity(Object inventory, int slot) {}
    private final Map<String, String> diagnostics = new LinkedHashMap<>();
    @Override public String id() { return "oritech"; }

    @Override public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
        ServerLevel level = access.level().getServer().getLevel(position.dimension());
        Object owner = owner(level, position.pos());
        return owner instanceof BlockEntity entity && active(entity) ? new Host(level, entity) : null;
    }
    @Override public Object identityAt(ServerLevel level, BlockPos position) { return owner(level, position); }
    private static Object owner(ServerLevel level, BlockPos position) {
        if (level == null || !level.hasChunkAt(position)) return null;
        Object candidate = level.getBlockEntity(position);
        for (int depth = 0; candidate != null && depth < 4; depth++) {
            if (contract(candidate, MACHINE)) return candidate;
            if (!candidate.getClass().getName().startsWith("rearth.oritech.")
                    && !contract(candidate, "rearth.oritech.block.entity.MachineCoreEntity")
                    && !contract(candidate, "rearth.oritech.util.MultiblockMachineController")) return null;
            Object positionValue = optional(candidate, "getControllerPos");
            if (positionValue instanceof BlockPos next && !level.hasChunkAt(next)) return null;
            Object controller = positionValue instanceof BlockPos next ? level.getBlockEntity(next) : optional(candidate, "getCachedController");
            if (controller == null) controller = field(candidate, "owningRefinery");
            if (controller == candidate) return null;
            candidate = controller;
        }
        return null;
    }
    private static boolean active(BlockEntity owner) {
        return !owner.isRemoved() && Boolean.TRUE.equals(optional(owner, "isActive", owner.getBlockState()));
    }

    @Override public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> loadedMachines) {
        diagnostics.clear();
        Map<String, Recipe> result = new LinkedHashMap<>();
        Map<ResourceKey, Long> stock = access.resourceSnapshot();
        ItemStock itemStock = itemStock(stock);
        Set<Object> nativeTypes = new HashSet<>();
        for (Machine machine : loadedMachines) if (machine instanceof Host host) nativeTypes.addAll(host.types());
        for (var holder : NativeRecipeSource.entries(access.level())) {
            Object recipe = holder.value();
            String nativeId = holder.id().toString();
            if (!contract(recipe, NATIVE_RECIPE)) {
                var typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.type());
                if (typeId != null && typeId.getNamespace().equals("oritech"))
                    diagnostics.put(nativeId, "Native player/research data has no transferable machine production contract: " + recipe.getClass().getName());
                if (!nativeTypes.contains(holder.type())) continue;
            }
            try {
                List<CraftRecipe.Ingredient<ResourceKey>> inputs = inputs(recipe, stock, itemStock);
                Object projectionHost = contract(recipe, NATIVE_RECIPE) ? null : loadedMachines.stream().filter(Host.class::isInstance).map(Host.class::cast)
                        .filter(host -> host.types().contains(holder.type())).map(host -> host.entity).findFirst().orElse(null);
                Map<ResourceKey, Long> base = outputs(recipe, projectionHost);
                if (inputs.isEmpty()) { diagnostics.put(nativeId, "Native recipe has no consumed transferable input contract"); continue; }
                if (base.isEmpty()) { diagnostics.put(nativeId, "Native burn/world recipe has no declared item or fluid product; energy/world execution is not an inventory cycle"); continue; }
                Object type = NativeRecipeSource.type(recipe);
                long duration = number(has(recipe, "getTime") ? call(recipe, "getTime") : NativeRecipeSource.cookingTime(recipe), 1);
                add(result, access.level(), nativeId, recipe, type, inputs, base, Math.max(1, duration));
                for (Machine machine : loadedMachines) if (machine instanceof Host host && host.types().contains(type)) {
                    Map<ResourceKey, Long> projected = outputs(recipe, host.entity);
                    if (!projected.isEmpty()) add(result, access.level(), nativeId, recipe, type, inputs, projected,
                            Math.max(1, Math.round(duration * speed(host.entity))));
                }
                diagnostics.put(nativeId, "Native item/fluid recipe projected; execution requires a matching formed native host and roles");
            } catch (RuntimeException unsupported) { diagnostics.put(nativeId, unsupported.getMessage() == null ? unsupported.getClass().getName() : unsupported.getMessage()); }
        }
        return List.copyOf(result.values());
    }
    /** Every native recipe receives a reason, including non-inventory world/generator records. */
    public Map<String, String> diagnostics() { return Map.copyOf(diagnostics); }
    private static void add(Map<String, Recipe> result, ServerLevel level, String nativeId, Object nativeRecipe, Object type,
                            List<CraftRecipe.Ingredient<ResourceKey>> inputs, Map<ResourceKey, Long> outputs, long duration) {
        String variant = NativeMachineAdapter.profile(level, outputs);
        String id = "oritech/native/" + variant + "/" + nativeId;
        int ticks = (int)Math.min(Integer.MAX_VALUE / 32, duration);
        result.putIfAbsent(id, new Recipe(id, "oritech/" + type + "/" + variant, inputs, outputs, outputs,
                ticks, Math.max(7200, ticks * 32), new Binding(nativeRecipe, type, outputs), false));
    }
    private static float speed(Object host) { Object speed = optional(host, "getSpeedMultiplier"); return speed instanceof Number number ? Math.max(0.001F, number.floatValue()) : 1; }
    private static List<CraftRecipe.Ingredient<ResourceKey>> inputs(Object recipe, Map<ResourceKey, Long> stock, ItemStock itemStock) {
        List<CraftRecipe.Ingredient<ResourceKey>> result = new ArrayList<>();
        for (Object input : list(has(recipe, "getInputs") ? call(recipe, "getInputs") : NativeRecipeSource.ingredients(recipe))) {
            if (!(input instanceof Ingredient ingredient)) throw new IllegalArgumentException("Unknown native item ingredient contract");
            if (ingredient.isEmpty()) continue;
            result.add(ingredient(ingredient, 1, itemStock));
        }
        Object fluid = optional(recipe, "getFluidInput");
        long amount = fluid == null ? 0 : number(optional(fluid, "amount"), 0);
        if (amount > 0) {
            Set<ResourceKey> alternatives = new LinkedHashSet<>();
            for (Object display : list(call(fluid, "getFluidStacks"))) { FluidKey key = fluidKey(display); if (key != null) alternatives.add(key); }
            for (ResourceKey key : stock.keySet()) if (key instanceof FluidKey candidate && Boolean.TRUE.equals(optional(fluid, "matchesFluid", nativeFluid(candidate, 1)))) alternatives.add(candidate);
            long milliBuckets = toMilliBuckets(amount);
            if (alternatives.isEmpty() || milliBuckets < 1) throw new IllegalArgumentException("Native fluid ingredient has no exact transferable alternatives");
            String tag = Boolean.TRUE.equals(optional(fluid, "hasTag")) ? String.valueOf(call(fluid, "getTag")) : "";
            result.add(new CraftRecipe.Ingredient<>(List.copyOf(alternatives), milliBuckets, tag));
        }
        return List.copyOf(result);
    }
    private static Map<ResourceKey, Long> outputs(Object recipe, Object host) {
        Map<ResourceKey, Long> result = new LinkedHashMap<>();
        Object itemOutputs;
        if (host != null && has(host, "getCraftingResults", recipe)) itemOutputs = call(host, "getCraftingResults", recipe);
        else if (has(recipe, "getResults")) itemOutputs = call(recipe, "getResults");
        else if (host instanceof BlockEntity entity) itemOutputs = NativeRecipeSource.output(recipe, entity.getLevel().registryAccess());
        else return Map.of();
        for (Object output : list(itemOutputs)) { ItemStack stack = item(output); if (!stack.isEmpty()) result.merge(new ItemKey(stack), (long)stack.getCount(), Math::addExact); }
        Object fluidOutputs = host != null && has(host, "calculateOutputFluids", recipe) ? call(host, "calculateOutputFluids", recipe)
                : host != null && has(host, "calculateOutputFluid", recipe) ? call(host, "calculateOutputFluid", recipe) : optional(recipe, "getFluidOutputs");
        if (host != null && !has(host, "calculateOutputFluids", recipe) && !has(host, "calculateOutputFluid", recipe)
                && NativeBytecode.hierarchy(host.getClass()).stream().flatMap(shape -> shape.methods().stream())
                    .filter(method -> method.name().equals("craftFluids"))
                    .flatMap(method -> method.references().stream()).anyMatch(reference -> reference.owner().equals("java.util.List") && reference.name().equals("getFirst"))) {
            List<?> declared = list(fluidOutputs); fluidOutputs = declared.isEmpty() ? List.of() : List.of(declared.getFirst());
        }
        for (Object output : list(fluidOutputs)) {
            FluidKey key = fluidKey(output); long amount = toMilliBuckets(number(optional(output, "getAmount"), 0));
            if (key != null && amount > 0) result.merge(key, amount, Math::addExact);
        }
        return Map.copyOf(result);
    }

    private static final class Host implements Machine {
        final ServerLevel level; final BlockEntity entity;
        Host(ServerLevel level, BlockEntity entity) { this.level = level; this.entity = entity; }
        @Override public Object identity() { return entity; }
        @Override public Object instance() { return entity; }
        @Override public boolean valid() { return level.hasChunkAt(entity.getBlockPos()) && level.getBlockEntity(entity.getBlockPos()) == entity && active(entity); }
        @Override public boolean accepts(Recipe recipe) {
            return recipe.nativeRecipe() instanceof Binding binding && types().contains(binding.type()) && binding.outputs().equals(outputs(binding.recipe(), entity));
        }
        @Override public boolean ready(Recipe recipe, List<CraftPlan.Selection<ResourceKey>> selected) {
            return valid() && !Boolean.TRUE.equals(field(entity, "disabledViaRedstone")) && number(field(entity, "progress"), 0) == 0
                    && accepts(recipe) && exactOutputIdentity((Binding)recipe.nativeRecipe());
        }
        private boolean exactOutputIdentity(Binding binding) {
            Object recipe = binding.recipe();
            if (has(entity, "canOutputRecipe", recipe) && !Boolean.TRUE.equals(call(entity, "canOutputRecipe", recipe))) return false;
            Object inventory = field(entity, "inventory"), assignment = call(entity, "getSlotAssignments");
            int start = Math.toIntExact(number(call(assignment, "outputStart"), 0)), count = Math.toIntExact(number(call(assignment, "outputCount"), 0));
            Object projectedItems = has(entity, "getCraftingResults", recipe) ? call(entity, "getCraftingResults", recipe)
                    : has(recipe, "getResults") ? call(recipe, "getResults") : NativeRecipeSource.output(recipe, level.registryAccess());
            List<?> items = list(projectedItems);
            if (items.size() > count) return false;
            for (int index = 0; index < items.size(); index++) {
                ItemStack expected = item(items.get(index)), present = item(call(inventory, "getStackInSlot", start + index));
                if (!present.isEmpty() && (expected.isEmpty() || !new ItemKey(present).equals(new ItemKey(expected)))) return false;
            }
            Object projectedFluids = has(entity, "calculateOutputFluids", recipe) ? call(entity, "calculateOutputFluids", recipe)
                    : has(entity, "calculateOutputFluid", recipe) ? call(entity, "calculateOutputFluid", recipe) : optional(recipe, "getFluidOutputs");
            List<?> fluids = list(projectedFluids);
            Object storage = optional(entity, "getFluidStorage", (Object)null);
            if (!fluids.isEmpty() && storage == null) return false;
            for (int index = 0; index < fluids.size(); index++) {
                FluidKey expected = fluidKey(fluids.get(index)); if (expected == null) continue;
                Object output = has(entity, "getOutputStorage", index) ? call(entity, "getOutputStorage", index) : optional(storage, "getOutputContainer");
                if (output == null) { if (index > 0) return false; output = storage; }
                for (Object existing : list(call(output, "getContent"))) { FluidKey present = fluidKey(existing); if (present != null && !present.equals(expected)) return false; }
            }
            return true;
        }
        Set<Object> types() { return executedTypes(entity); }
        @Override public Object catalogState() {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("types", List.copyOf(types())); state.put("speed", speed(entity));
            for (String method : List.of("getModuleCount", "getOutputMultiplier", "getBaseAddonData")) { Object value = optional(entity, method); if (value != null) state.put(method, value); }
            Object selected = field(entity, "selectedOutput"); if (selected != null) state.put("selectedOutput", selected);
            return Map.copyOf(state);
        }
        @Override public List<GlobalPos> claimedPositions() {
            Set<GlobalPos> positions = new LinkedHashSet<>(); positions.add(GlobalPos.of(level.dimension(), entity.getBlockPos()));
            for (Object pos : list(optional(entity, "getConnectedCores"))) if (pos instanceof BlockPos block && level.hasChunkAt(block)) positions.add(GlobalPos.of(level.dimension(), block));
            // Refinery output modules are native destinations, discovered from their owner relationship.
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos candidate = entity.getBlockPos().above(dy);
                if (level.hasChunkAt(candidate) && owner(level, candidate) == entity) positions.add(GlobalPos.of(level.dimension(), candidate));
            }
            return List.copyOf(positions);
        }
        @Override public List<Port> ports(Recipe recipe) {
            if (!(recipe.nativeRecipe() instanceof Binding binding) || !accepts(recipe)) return List.of();
            List<Port> ports = new ArrayList<>();
            Object inventory = optional(entity, "getInventoryStorage", (Object)null);
            if (inventory == null) inventory = field(entity, "inventory");
            Object assignment = call(entity, "getSlotAssignments");
            int inputs = Math.toIntExact(number(call(assignment, "inputCount"), 0)), inputStart = Math.toIntExact(number(call(assignment, "inputStart"), 0));
            int outputs = Math.toIntExact(number(call(assignment, "outputCount"), 0)), outputStart = Math.toIntExact(number(call(assignment, "outputStart"), 0));
            int count = Math.toIntExact(number(optional(inventory, "getSlotCount"), 0));
            if (count > 4096 || inputStart < 0 || outputStart < 0 || inputStart + inputs > count || outputStart + outputs > count) throw new IllegalStateException("Invalid native slot assignment");
            for (int slot = 0; slot < count; slot++) {
                boolean input = slot >= inputStart && slot < inputStart + inputs, output = slot >= outputStart && slot < outputStart + outputs;
                if (input || output) ports.add(itemPort(inventory, slot, input, output));
            }
            Object storage = optional(entity, "getFluidStorage", (Object)null);
            if (storage != null) {
                Object in = optional(storage, "getInputContainer"), out = optional(storage, "getOutputContainer");
                if (in != null) ports.add(fluidPort(in, true, false));
                if (out != null) ports.add(fluidPort(out, false, true));
                if (in == null && out == null) ports.add(fluidPort(storage, true, binding.outputs().keySet().stream().anyMatch(FluidKey.class::isInstance)));
                if (has(entity, "getOutputStorage", 0)) {
                    int projected = list(has(entity, "calculateOutputFluids", binding.recipe()) ? call(entity, "calculateOutputFluids", binding.recipe()) : call(binding.recipe(), "getFluidOutputs")).size();
                    for (int index = 0; index < projected; index++) {
                        Object output = call(entity, "getOutputStorage", index);
                        if (output != out) ports.add(fluidPort(output, false, true));
                    }
                }
            }
            return List.copyOf(ports);
        }
    }

    private record Providers(List<NativeBytecode.Reference> constants, boolean dynamicType) {}
    private static final ClassValue<Providers> PROVIDERS = new ClassValue<>() {
        @Override protected Providers computeValue(Class<?> type) { return providerShape(type); }
    };
    private static Set<Object> executedTypes(Object host) {
        Providers providers = PROVIDERS.get(host.getClass()); Set<Object> types = new LinkedHashSet<>();
        for (var reference : providers.constants()) { Object value = reference.readStatic(host.getClass().getClassLoader()); if (value instanceof RecipeType<?>) types.add(value); }
        if (providers.dynamicType()) { Object value = optional(host, "getOwnRecipeType"); if (value != null) types.add(value); }
        return Set.copyOf(types);
    }
    /** Follow executable native recipe-provider calls once per class; an unused getter cannot classify a host. */
    private static Providers providerShape(Class<?> hostType) {
        record MethodKey(Class<?> owner, String name, String descriptor) {}
        Set<NativeBytecode.Reference> constants = new LinkedHashSet<>(); boolean dynamicType = false;
        Set<MethodKey> visited = new HashSet<>(); Deque<MethodKey> queue = new ArrayDeque<>();
        for (var shape : NativeBytecode.hierarchy(hostType)) {
            Optional<NativeBytecode.Method> entry = shape.methods().stream().filter(method -> method.name().equals("serverTick")).findFirst();
            if (entry.isPresent()) { Class<?> owner = optionalClass(shape.name()); if (owner != null) queue.add(new MethodKey(owner, entry.get().name(), entry.get().descriptor())); break; }
        }
        while (!queue.isEmpty() && visited.size() < 256) {
            MethodKey key = queue.removeFirst(); if (!visited.add(key)) continue;
            if (key.name().equals("getOwnRecipeType")) { dynamicType = true; continue; }
            NativeBytecode.Method method = NativeBytecode.inspect(key.owner()).methods().stream().filter(candidate -> candidate.name().equals(key.name()) && candidate.descriptor().equals(key.descriptor())).findFirst().orElse(null);
            if (method == null) continue;
            for (var reference : method.references()) {
                if (reference.staticRead() && (reference.descriptor().contains("RecipeType;") || reference.descriptor().contains("OritechRecipeType;"))) constants.add(reference);
                else if (reference.opcode() >= 182 && reference.opcode() <= 185 && contract(hostType, reference.owner())) {
                    Class<?> owner = reference.opcode() == 183 ? optionalClass(reference.owner()) : hostType;
                    for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
                        boolean declared = NativeBytecode.inspect(current).methods().stream().anyMatch(candidate -> candidate.name().equals(reference.name()) && candidate.descriptor().equals(reference.descriptor()));
                        if (declared) { queue.add(new MethodKey(current, reference.name(), reference.descriptor())); break; }
                    }
                }
            }
        }
        return new Providers(List.copyOf(constants), dynamicType);
    }

    private static Port itemPort(Object inventory, int slot, boolean input, boolean output) {
        return new Port() {
            @Override public Object identity() { return new ItemPortIdentity(inventory, slot); }
            @Override public boolean input() { return input; }
            @Override public boolean output() { return output; }
            @Override public Map<ResourceKey, Long> snapshot() { ItemStack stack = item(call(inventory, "getStackInSlot", slot)); return stack.isEmpty() ? Map.of() : Map.of(new ItemKey(stack), (long)stack.getCount()); }
            @Override public long insert(ResourceKey key, long amount, boolean simulate) {
                if (!input || !(key instanceof ItemKey item) || amount < 1) return 0;
                return number(call(inventory, "insertToSlot", item.sample().copyWithCount((int)Math.min(Integer.MAX_VALUE, amount)), slot, simulate), 0);
            }
            @Override public long extract(ResourceKey key, long amount, boolean simulate) {
                if (!(key instanceof ItemKey item) || amount < 1) return 0;
                ItemStack stack = item(call(inventory, "getStackInSlot", slot));
                if (stack.isEmpty() || !new ItemKey(stack).equals(key)) return 0;
                int wanted = (int)Math.min(amount, stack.getCount());
                if (input && inventory instanceof Container container) {
                    if (simulate) return wanted;
                    ItemStack taken = container.removeItem(slot, wanted);
                    if (!taken.isEmpty() && (!new ItemKey(taken).equals(key) || taken.getCount() > wanted))
                        throw new NativeMachinePorts.ExtractedResources("Native input recovery changed identity", Map.of(new ItemKey(taken), (long)taken.getCount()));
                    return taken.getCount();
                }
                return number(call(inventory, "extractFromSlot", item.sample().copyWithCount(wanted), slot, simulate), 0);
            }
        };
    }
    private static Port fluidPort(Object storage, boolean input, boolean output) {
        return new Port() {
            @Override public Object identity() { return storage; }
            @Override public boolean input() { return input; }
            @Override public boolean output() { return output; }
            @Override public Map<ResourceKey, Long> snapshot() {
                Map<ResourceKey, Long> result = new HashMap<>();
                for (Object stack : list(call(storage, "getContent"))) { FluidKey key = fluidKey(stack); long count = toMilliBuckets(number(optional(stack, "getAmount"), 0)); if (key != null && count > 0) result.merge(key, count, Math::addExact); }
                return Map.copyOf(result);
            }
            @Override public long insert(ResourceKey key, long amount, boolean simulate) { return input && key instanceof FluidKey fluid ? transfer("insert", fluid, amount, simulate) : 0; }
            @Override public long extract(ResourceKey key, long amount, boolean simulate) {
                if (!(key instanceof FluidKey fluid) || !snapshot().containsKey(key)) return 0;
                return transfer("extract", fluid, amount, simulate);
            }
            private long transfer(String method, FluidKey key, long amount, boolean simulate) {
                if (amount < 1) return 0;
                long moved = number(call(storage, method, nativeFluid(key, amount), simulate), 0);
                if (moved > 0 && !simulate) call(storage, "update");
                return toMilliBuckets(moved);
            }
        };
    }
    private static long bucketAmount() { Class<?> nativeType = optionalClass("dev.architectury.fluid.FluidStack"); return nativeType == null ? 1000 : number(call(nativeType, "bucketAmount"), 1000); }
    private static long toMilliBuckets(long nativeAmount) {
        long bucket = bucketAmount(); if (nativeAmount == 0) return 0;
        long scaled = Math.multiplyExact(nativeAmount, 1000); if (scaled % bucket != 0) throw new IllegalArgumentException("Native fluid amount is below one exact millibucket"); return scaled / bucket;
    }
    private static FluidKey fluidKey(Object nativeStack) {
        if (nativeStack == null || Boolean.TRUE.equals(optional(nativeStack, "isEmpty"))) return null;
        Object fluid = optional(nativeStack, "getFluid"); if (!(fluid instanceof Fluid value) || number(optional(nativeStack, "getAmount"), 0) <= 0) return null;
        FluidStack sample = new FluidStack(value, 1); Object patch = optional(nativeStack, "getPatch"); if (patch instanceof DataComponentPatch components) sample.applyComponents(components);
        return new FluidKey(sample);
    }
    private static Object nativeFluid(FluidKey key, long amount) {
        long scaled = Math.multiplyExact(amount, bucketAmount()); if (scaled % 1000 != 0) throw new IllegalArgumentException("Fluid amount has no exact native representation");
        FluidStack sample = key.sample(); Class<?> type = optionalClass("dev.architectury.fluid.FluidStack");
        if (type == null) throw new IllegalStateException("Oritech architectury fluid contract is unavailable");
        return call(type, "create", sample.getFluid(), scaled / 1000, sample.getComponentsPatch());
    }
}
