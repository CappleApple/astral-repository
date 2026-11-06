package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.CompatConfig;
import java.util.*;
import java.lang.invoke.MethodType;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import com.cappleapple.astralrepository.platform.capabilities.Capabilities;
import com.cappleapple.astralrepository.platform.items.IItemHandler;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Create recipe providers, basins, transport behaviours, processing buffers and airflow contracts. */
public final class CreateBackend implements NativeMachineBackend {
    private static final String SMART = "com.simibubi.create.foundation.blockEntity.SmartBlockEntity";
    private static final String PROCESSING = "com.simibubi.create.content.processing.recipe.ProcessingRecipe";
    private static final String BUFFER = "com.simibubi.create.content.processing.recipe.ProcessingInventory";
    private static final String BASIN = "com.simibubi.create.content.processing.basin.BasinBlockEntity";
    private static final String AIR = "com.simibubi.create.content.kinetics.fan.IAirCurrentSource";
    private static final String BELT = "com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour";
    private static final String PRESS = "com.simibubi.create.content.kinetics.press.PressingBehaviour$PressingBehaviourSpecifics";
    private record Bound(NativeRecipeSource.Entry entry, boolean keepHeld, Object heat, CreateSequencedRecipes.Step step) {}
    private record Source(NativeRecipeSource.Entry entry, CreateSequencedRecipes.Step step) {}
    private record Work(Class<?> owner, String method, String descriptor, int depth) {}
    private record Provider(Object type, Object info) {}
    private enum Kind { BASIN, BUFFER, TRANSPORT, DRAIN, AIR }
    public record Classification(String recipe, String type, String status, String reason) {}
    private List<Classification> audit = List.of();
    private final CreateContainerRecipes containers = new CreateContainerRecipes();
    public List<Classification> audit() { return audit; }
    @Override public String id() { return "create"; }
    @Override public boolean usesRequestedOutputs() { return true; }

    private static final ClassValue<List<Provider>> PROVIDERS = new ClassValue<>() {
        @Override protected List<Provider> computeValue(Class<?> type) {
            Map<Object, Provider> result = new LinkedHashMap<>(); Deque<Work> pending = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            for (var shape : NativeBytecode.hierarchy(type)) for (var method : shape.methods())
                if (Set.of("tick", "serverTick", "getRecipe", "findRecipe", "getMatchingRecipes", "matchStaticFilters", "addBehaviours",
                        "whenItemHeld", "tryProcessOnBelt", "tryProcessInWorld", "process", "applyRecipe", "applyBasinRecipe", "canProcess").contains(method.name()))
                    pending.add(new Work(optionalClass(shape.name()), method.name(), method.descriptor(), 0));
            for (int budget = 2048; !pending.isEmpty() && budget-- > 0;) {
                Work work = pending.removeFirst();
                if (work.owner() == null || work.depth() > 8 || !visited.add(work.owner().getName() + work.method() + work.descriptor())) continue;
                for (var method : NativeBytecode.inspect(work.owner()).methods()) if (method.name().equals(work.method()) && method.descriptor().equals(work.descriptor())) {
                    for (var reference : method.references()) {
                        if (reference.staticRead() && (reference.descriptor().contains("RecipeType") || recipeField(reference))) {
                            try {
                                Object info = reference.readStatic(type.getClassLoader()), recipeType = info;
                                if (contract(info, "com.simibubi.create.foundation.recipe.IRecipeTypeInfo")) recipeType = call(info, "getType");
                                else if (info instanceof java.util.function.Supplier<?> supplier) recipeType = supplier.get();
                                if (recipeType instanceof RecipeType<?>) result.putIfAbsent(recipeType, new Provider(recipeType, info));
                            } catch (RuntimeException | LinkageError unrelated) { /* A client/helper reference is not a recipe provider. */ }
                        }
                        if (!reference.staticRead() && reference.opcode() != 180 && !reference.owner().startsWith("java.")
                                && !reference.owner().startsWith("net.minecraft.") && !reference.owner().startsWith("net.neoforged."))
                            pending.addLast(new Work(optionalClass(reference.owner()), reference.name(), reference.descriptor(), work.depth() + 1));
                    }
                }
            }
            return List.copyOf(result.values());
        }
    };
    private static boolean recipeField(NativeBytecode.Reference reference) {
        Class<?> owner = optionalClass(reference.owner()); if (owner == null) return false;
        return NativeBytecode.inspect(owner).fields().stream().anyMatch(field -> field.name().equals(reference.name())
                && field.signature() != null && field.signature().contains("RecipeType"));
    }
    static List<Object> nativeRecipeTypes(Class<?> owner) { return PROVIDERS.get(owner).stream().map(Provider::type).distinct().toList(); }
    private static Object behaviour(Object owner, String contract) {
        Class<?> api = optionalClass(contract); if (api == null) return null;
        Object type = field(api, "TYPE"); return type == null ? null : optional(owner, "getBehaviour", type);
    }
    @Override public Object identityAt(ServerLevel level, BlockPos position) {
        Host host = at(level, position); return host == null ? null : host.identity();
    }
    @Override public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
        return at(access.level().getServer().getLevel(position.dimension()), position.pos());
    }
    private static Host at(ServerLevel level, BlockPos position) {
        if (!CompatConfig.create.get() || level == null || !level.hasChunkAt(position)) return null;
        BlockEntity entity = level.getBlockEntity(position); if (!contract(entity, SMART)) return null;
        if (behaviour(entity, "com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour") != null) {
            BlockPos operating = entity.getBlockPos().above(2);
            if (!level.hasChunkAt(operating)) return null;
            BlockEntity actor = level.getBlockEntity(operating);
            if (behaviour(actor, BELT) != null || contract(actor, PRESS)) entity = actor;
            else return null;
        }
        if (contract(entity, BASIN)) {
            Object operator = optional(entity, "getOperator");
            if (operator instanceof Optional<?> value && value.orElse(null) instanceof BlockEntity owner) entity = owner;
            else return null;
        }
        if (contract(entity, AIR)) return new Host(level, entity, entity, Kind.AIR);
        if (has(entity, "getHeldItemStack") && behaviour(entity, "com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour") != null
                && field(entity, "internalTank") != null && !PROVIDERS.get(entity.getClass()).isEmpty())
            return new Host(level, entity, entity, Kind.DRAIN);
        Object basin = optional(entity, "getBasin");
        if (basin instanceof Optional<?> value && value.orElse(null) instanceof BlockEntity contents)
            return new Host(level, entity, contents, Kind.BASIN);
        Object processing = field(entity, "inventory");
        if (contract(processing, BUFFER) || !PROVIDERS.get(entity.getClass()).isEmpty() && field(entity, "inputInv") != null)
            return new Host(level, entity, entity, Kind.BUFFER);
        if (behaviour(entity, BELT) != null || contract(entity, PRESS)) {
            // This is Create's shared belt-processing space, used by pressing, deploying and spouting behaviours.
            BlockPos carrierPos = entity.getBlockPos().below(2);
            if (!level.hasChunkAt(carrierPos)) return null;
            BlockEntity carrier = level.getBlockEntity(carrierPos);
            if (carrier != null && behaviour(carrier, "com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour") != null)
                return new Host(level, entity, carrier, Kind.TRANSPORT);
        }
        return null;
    }

    @Override public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> loadedMachines) {
        return recipes(access, loadedMachines, List.of());
    }
    @Override public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> loadedMachines, List<ResourceKey> requestedOutputs) {
        List<Recipe> result = new ArrayList<>(); List<Classification> classified = new ArrayList<>();
        List<Host> hosts = loadedMachines.stream().filter(Host.class::isInstance).map(Host.class::cast).toList();
        Map<ResourceKey, Long> stock = access.resourceSnapshot();
        ItemStock indexedItems = itemStock(stock);
        List<Source> sources = new ArrayList<>();
        for (var entry : NativeRecipeSource.entries(access.level())) sources.add(new Source(entry, null));
        var assembly = CreateSequencedRecipes.discover(access);
        assembly.steps().forEach(step -> sources.add(new Source(step.entry(), step)));
        assembly.diagnostics().forEach((id, reason) -> classified.add(new Classification(id, "create:sequenced_assembly", "unsupported-contract", reason)));
        for (var source : sources) {
            var entry = source.entry(); var step = source.step();
            Object raw = entry.value(); boolean processing = contract(raw, PROCESSING);
            List<Host> matching = hosts.stream().filter(host -> host.acceptsEntry(entry)).toList();
            if (!processing && matching.isEmpty()) continue;
            try {
                boolean keep = Boolean.TRUE.equals(optional(raw, "shouldKeepHeldItem"));
                List<CraftRecipe.Ingredient<ResourceKey>> inputs = new ArrayList<>();
                List<Ingredient> itemInputs = NativeRecipeSource.ingredients(raw);
                for (int i = 0; i < itemInputs.size(); i++) {
                    if (keep && i > 0) continue;
                    inputs.add(i == 0 && step != null ? step.primary() : ingredient(itemInputs.get(i), 1, indexedItems));
                }
                for (Object fluid : list(optional(raw, "getFluidIngredients"))) {
                    Set<ResourceKey> alternatives = new LinkedHashSet<>(); long count = number(optional(fluid, "amount"), 0);
                    for (Object sample : list(optional(fluid, "getFluids"))) if (MekanismResourceSupport.amount(sample) > 0) {
                        alternatives.add(MekanismResourceSupport.key(sample)); if (count <= 0) count = MekanismResourceSupport.amount(sample);
                    }
                    for (ResourceKey key : stock.keySet()) if (key instanceof FluidKey && Boolean.TRUE.equals(optional(fluid, "test", MekanismResourceSupport.sample(key, Math.max(1, count))))) alternatives.add(key);
                    if (alternatives.isEmpty() || count <= 0) throw new IllegalArgumentException("Native fluid ingredient has no finite registered alternatives");
                    inputs.add(new CraftRecipe.Ingredient<>(List.copyOf(alternatives), count));
                }
                Map<ResourceKey, Long> guaranteed = new LinkedHashMap<>(), possible = new LinkedHashMap<>();
                if (processing) {
                    if (step != null) { guaranteed.putAll(step.guaranteed()); possible.putAll(step.possible()); }
                    else for (Object roll : list(call(raw, "getRollableResults"))) {
                        ItemStack stack = item(call(roll, "getStack")); double chance = ((Number) call(roll, "getChance")).doubleValue();
                        if (stack.isEmpty() || chance <= 0) continue;
                        possible.merge(new ItemKey(stack), (long) stack.getCount(), Math::addExact);
                        if (chance >= 1) guaranteed.merge(new ItemKey(stack), (long) stack.getCount(), Math::addExact);
                    }
                    for (Object stack : list(call(raw, "getFluidResults"))) if (MekanismResourceSupport.amount(stack) > 0) {
                        ResourceKey key = MekanismResourceSupport.key(stack); long count = MekanismResourceSupport.amount(stack);
                        possible.merge(key, count, Math::addExact); guaranteed.merge(key, count, Math::addExact);
                    }
                } else {
                    ItemStack output = NativeRecipeSource.output(raw, access.level().registryAccess());
                    if (!output.isEmpty()) { guaranteed.put(new ItemKey(output), (long) output.getCount()); possible.putAll(guaranteed); }
                }
                if (inputs.isEmpty() || possible.isEmpty()) throw new IllegalArgumentException("Native manual/world recipe exposes no consumed inventory cycle");
                int duration = (int) Math.max(1, number(optional(raw, "getProcessingDuration"), NativeRecipeSource.cookingTime(raw)));
                Object heat = optional(raw, "getRequiredHeat");
                String type = entry.type().toString();
                String id = step == null ? "create/native/" + entry.id() : "create/assembly/" + step.index() + "/" + step.assemblyId();
                result.add(new Recipe(id, type, inputs, guaranteed, possible, duration, Math.max(7200, duration * 32), new Bound(entry, keep, heat, step), !guaranteed.equals(possible)));
                classified.add(new Classification(entry.id().toString(), type, step != null ? "native-sequenced-step" : matching.isEmpty() ? "declared-native-recipe" : "physical-native-host",
                        matching.isEmpty() ? "Requires the matching configured native processor/transport/world context" : "Shared native recipe and physical port contracts"));
            } catch (RuntimeException | LinkageError unsupported) {
                classified.add(new Classification(entry.id().toString(), entry.type().toString(), "unsupported-contract", String.valueOf(unsupported.getMessage())));
            }
        }
        if (hosts.stream().anyMatch(host -> host.kind == Kind.DRAIN || host.kind == Kind.TRANSPORT)) {
            Set<ResourceKey> declared = new LinkedHashSet<>();
            declared.addAll(requestedOutputs);
            for (var recipe : result) {
                declared.addAll(recipe.possible().keySet());
                recipe.inputs().forEach(input -> declared.addAll(input.alternatives()));
            }
            for (ItemStack product : access.exposedProducts()) if (!product.isEmpty()) declared.add(new ItemKey(product));
            var capacities = hosts.stream().flatMap(host -> host.containerInputCapacities().stream()).distinct().toList();
            var containerCatalog = containers.discover(access.level(), stock, declared, capacities);
            for (var cycle : containerCatalog.cycles()) result.add(cycle.recipe());
            containerCatalog.diagnostics().forEach(value -> classified.add(new Classification(value.subject(), "create:container", value.status(), value.reason())));
        }
        audit = List.copyOf(classified); return List.copyOf(result);
    }

    private static final class Host implements Machine {
        final ServerLevel level; final BlockEntity entity, contents; final Kind kind;
        final Object air; NativeWorldOutputs.WorldInput worldInput;
        Host(ServerLevel level, BlockEntity entity, BlockEntity contents, Kind kind) {
            this.level = level; this.entity = entity; this.contents = contents; this.kind = kind;
            air = kind == Kind.AIR ? optional(entity, "getAirCurrent") : null;
        }
        public Object identity() { return contents; }
        public Object instance() { return entity; }
        public boolean valid() { return !entity.isRemoved() && !contents.isRemoved() && level.getBlockEntity(entity.getBlockPos()) == entity && level.getBlockEntity(contents.getBlockPos()) == contents; }
        List<Provider> providers() {
            if (kind != Kind.AIR) return PROVIDERS.get(entity.getClass());
            List<Provider> result = new ArrayList<>();
            if (air != null) for (Object segment : list(field(air, "segments"))) {
                Object type = field(segment, "type"); if (type != null) result.addAll(PROVIDERS.get(type.getClass()));
            }
            return List.copyOf(result);
        }
        boolean acceptsEntry(NativeRecipeSource.Entry entry) {
            Object recipe = entry.value();
            if (providers().stream().noneMatch(provider -> provider.type() == NativeRecipeSource.type(recipe))) return false;
            if (kind == Kind.BASIN) {
                if (!Boolean.TRUE.equals(call(entity, "matchStaticFilters", NativeRecipeSource.nativeHolder(entry)))) return false;
            }
            if (kind == Kind.TRANSPORT && NativeRecipeSource.ingredients(recipe).size() > 1 && field(entity, "player") == null) return false;
            return true;
        }
        public boolean accepts(Recipe recipe) {
            if (recipe.nativeRecipe() instanceof CreateContainerRecipes.Cycle cycle)
                return providers().stream().anyMatch(provider -> provider.type() == cycle.nativeType())
                        && (cycle.kind() == CreateContainerRecipes.Kind.EMPTY ? kind == Kind.DRAIN || kind == Kind.BUFFER : kind == Kind.TRANSPORT || kind == Kind.BUFFER);
            return recipe.nativeRecipe() instanceof Bound bound && acceptsEntry(bound.entry());
        }
        public boolean coalesceInputs() { return kind == Kind.BASIN; }
        public boolean emitsWorldItems() { return kind == Kind.AIR || kind == Kind.DRAIN || kind == Kind.BUFFER && contract(field(entity, "inventory"), BUFFER); }
        public boolean ready(Recipe recipe, List<CraftPlan.Selection<ResourceKey>> selected) {
            if (!accepts(recipe)) return false;
            Object speed = optional(entity, "getSpeed"); if (speed instanceof Number value && value.doubleValue() == 0) return false;
            if (kind == Kind.BASIN && recipe.nativeRecipe() instanceof Bound bound) {
                if (Boolean.TRUE.equals(optional(entity, "isRunning")) || !Boolean.TRUE.equals(optional(contents, "canContinueProcessing"))) return false;
                Object heat = bound.heat();
                if (heat != null && !Boolean.TRUE.equals(optional(heat, "testBlazeBurner", call(contents, "getHeatLevel")))) return false;
            }
            if (kind == Kind.AIR && airLocation(((Bound) recipe.nativeRecipe()).entry().value()) == null) return false;
            if (recipe.nativeRecipe() instanceof Bound bound && bound.keepHeld()) {
                Object player = field(entity, "player");
                if (!(player instanceof net.minecraft.world.entity.LivingEntity living)) return false;
                var ingredients = NativeRecipeSource.ingredients(bound.entry().value());
                if (ingredients.size() > 1 && !ingredients.get(1).test(living.getMainHandItem())) return false;
            }
            return true;
        }
        public Object catalogState() {
            List<Object> state = new ArrayList<>(); state.add(kind.name()); state.add(providers().stream().map(provider -> provider.type().toString()).distinct().toList());
            state.add(contents.getBlockState());
            if (air != null) state.add(List.of(String.valueOf(field(air, "direction")), number(field(air, "maxDistance"), 0),
                    list(field(air, "segments")).stream().map(segment -> String.valueOf(field(segment, "type"))).toList()));
            Object filter = optional(contents, "getFilter"); if (filter == null) filter = field(entity, "filtering");
            Object stack = optional(filter, "getFilter"); if (stack instanceof ItemStack item) state.add(item.isEmpty() ? "empty" : new ItemKey(item));
            return List.copyOf(state);
        }
        public boolean completed(Recipe recipe, int polls) {
            if (kind == Kind.AIR) return worldInput != null && worldInput.processed();
            Object buffer = field(entity, "inventory");
            if (contract(buffer, BUFFER)) return polls > 0 && number(field(buffer, "remainingTime"), 1) <= 0;
            Object timer = field(entity, "timer");
            return kind == Kind.BUFFER && polls > 0 && timer instanceof Number value && value.intValue() <= 0;
        }
        private Vec3 airLocation(Object recipe) {
            if (air == null || !(field(air, "direction") instanceof Direction direction)) return null;
            double maximum = number(field(air, "maxDistance"), 0);
            for (float distance = 1.25F; distance < maximum; distance += 0.5F) {
                Object type = optional(air, "getTypeAt", distance);
                if (type == null || PROVIDERS.get(type.getClass()).stream().noneMatch(provider -> provider.type() == NativeRecipeSource.type(recipe))) continue;
                Vec3 position = Vec3.atCenterOf(entity.getBlockPos()).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(distance));
                if (level.hasChunkAt(BlockPos.containing(position)) && level.getBlockState(BlockPos.containing(position)).isAir()) return position;
            }
            return null;
        }
        public List<GlobalPos> claimedPositions() {
            Set<GlobalPos> result = new LinkedHashSet<>(); result.add(GlobalPos.of(level.dimension(), entity.getBlockPos())); result.add(GlobalPos.of(level.dimension(), contents.getBlockPos()));
            if (kind == Kind.AIR && field(air, "bounds") instanceof AABB box) {
                if (box.getXsize() * box.getYsize() * box.getZsize() > 4096) return List.copyOf(result);
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
                    result.add(GlobalPos.of(level.dimension(), pos.immutable()));
            }
            BlockPos output = outputPosition(); if (output != null && level.hasChunkAt(output) && level.getBlockEntity(output) != null) result.add(GlobalPos.of(level.dimension(), output));
            return List.copyOf(result);
        }
        private BlockPos outputPosition() {
            if (kind == Kind.DRAIN) {
                Direction side = drainSide(); return side == null ? null : entity.getBlockPos().relative(side.getOpposite());
            }
            if (kind == Kind.BASIN || kind == Kind.BUFFER && contract(field(entity, "inventory"), BUFFER)) {
                BlockEntity owner = kind == Kind.BASIN ? contents : entity;
                for (var property : owner.getBlockState().getProperties()) if (property.getName().equals("facing") && owner.getBlockState().getValue(property) instanceof Direction direction && direction.getAxis().isHorizontal())
                    return owner.getBlockPos().below().relative(direction);
            }
            return null;
        }
        private Direction drainSide() {
            for (Direction direction : Direction.values()) if (Capabilities.find(level, Capabilities.ItemHandler.BLOCK, entity.getBlockPos(), direction) != null) return direction;
            return null;
        }
        private List<Long> containerInputCapacities() {
            if (kind != Kind.TRANSPORT && kind != Kind.BUFFER) return List.of();
            Object behaviour = field(entity, "tank"); if (behaviour == null) return List.of();
            List<Long> result = new ArrayList<>();
            for (Object segment : list(optional(behaviour, "getTanks"))) {
                Object tank = optional(segment, "getTank"); if (tank == null) tank = field(segment, "tank");
                long capacity = number(optional(tank, "getCapacity"), 0); if (capacity > 0) result.add(capacity);
            }
            if (result.isEmpty()) {
                Object handler = optional(behaviour, "getPrimaryHandler");
                int count = (int)number(optional(handler, "getTanks"), 0);
                for (int index = 0; index < count; index++) { long capacity = number(optional(handler, "getTankCapacity", index), 0); if (capacity > 0) result.add(capacity); }
            }
            return List.copyOf(result);
        }
        public List<Port> ports(Recipe recipe) {
            Bound bound = recipe.nativeRecipe() instanceof Bound value ? value : null; List<Port> result = new ArrayList<>();
            if (kind == Kind.AIR) {
                Vec3 location = airLocation(bound.entry().value()); if (location == null) return List.of();
                worldInput = NativeWorldOutputs.input(level, GlobalPos.of(level.dimension(), entity.getBlockPos()), location,
                        stack -> !NativeRecipeSource.ingredients(bound.entry().value()).isEmpty() && NativeRecipeSource.ingredients(bound.entry().value()).getFirst().test(stack));
                result.add(worldInput); return List.copyOf(result);
            }
            if (kind == Kind.BASIN) {
                addItems(result, field(contents, "inputInventory"), recipe, true, false);
                addItems(result, field(contents, "outputInventory"), recipe, false, true);
                addFluids(result, field(contents, "inputTank"), true, false);
                addFluids(result, field(contents, "outputTank"), false, true);
            } else {
                IItemHandler handler = Capabilities.find(level, Capabilities.ItemHandler.BLOCK, contents.getBlockPos(), kind == Kind.DRAIN ? drainSide() : null);
                if (handler != null) addItems(result, handler, recipe, true, true);
                if (kind == Kind.DRAIN) addFluids(result, field(entity, "internalTank"), false, true);
                if (kind == Kind.TRANSPORT) {
                    if (bound != null && !bound.keepHeld() && NativeRecipeSource.ingredients(bound.entry().value()).size() > 1) {
                        IItemHandler held = Capabilities.find(level, Capabilities.ItemHandler.BLOCK, entity.getBlockPos(), null);
                        if (held != null) addItems(result, held, recipe, true, true);
                    }
                    addFluids(result, field(entity, "tank"), true, false);
                }
            }
            BlockPos output = outputPosition();
            if (output != null && level.hasChunkAt(output)) {
                IItemHandler destination = Capabilities.find(level, Capabilities.ItemHandler.BLOCK, output, null);
                if (destination != null) addItems(result, destination, recipe, false, true);
                Object tank = Capabilities.find(level, Capabilities.FluidHandler.BLOCK, output, null);
                if (tank != null && number(optional(tank, "getTanks"), 0) == 1) addSingleFluid(result, tank, false, true);
            }
            return List.copyOf(result);
        }
    }
    private static void addItems(List<Port> result, Object handler, Recipe recipe, boolean input, boolean output) {
        if (handler == null || !has(handler, "getSlots")) return;
        int slots = (int) number(call(handler, "getSlots"), 0);
        for (int slot = 0; slot < slots; slot++) {
            int index = slot;
            boolean accepts = input && recipe.inputs().stream().flatMap(value -> value.alternatives().stream()).anyMatch(key ->
                    key instanceof ItemKey item && Boolean.TRUE.equals(optional(handler, "isItemValid", index, item.sample())));
            var raw = NativeMachinePorts.itemSlot(handler, slot, accepts, output);
            result.add(NativeMachinePorts.callbacks(raw.identity(), accepts, output, raw::snapshot, (key, amount, simulate) -> {
                if (!(key instanceof ItemKey item)) return 0;
                if (Boolean.FALSE.equals(field(handler, "insertionAllowed")) || !Boolean.TRUE.equals(optional(handler, "isItemValid", index, item.sample()))) return 0;
                if (!simulate) return raw.insert(key, amount, false);
                ItemStack existing = (ItemStack) call(handler, "getStackInSlot", index);
                if (!existing.isEmpty() && !new ItemKey(existing).equals(key)) return 0;
                long capacity = number(call(handler, "getSlotLimit", index), item.sample().getMaxStackSize());
                if (!Boolean.TRUE.equals(field(handler, "stackNonStackables"))) capacity = Math.min(capacity, item.sample().getMaxStackSize());
                return Math.max(0, Math.min(amount, capacity - existing.getCount()));
            }, raw::extract));
        }
    }
    private static void addFluids(List<Port> result, Object behaviour, boolean input, boolean output) {
        if (behaviour == null) return;
        Object segments = optional(behaviour, "getTanks");
        if (segments != null) for (Object segment : list(segments)) {
            Object tank = optional(segment, "getTank"); if (tank == null) tank = field(segment, "tank");
            if (tank != null) addSingleFluid(result, tank, input, output);
        } else {
            Object tank = optional(behaviour, "getPrimaryHandler"); if (tank != null) addSingleFluid(result, tank, input, output);
        }
    }
    private static void addSingleFluid(List<Port> result, Object tank, boolean input, boolean output) {
        if (has(tank, "getFluid")) result.add(NativeMachinePorts.fluidTank(tank, input, output));
        else if (number(optional(tank, "getTanks"), 0) == 1) {
            Class<?> actions = optionalClass("com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler$FluidAction");
            result.add(NativeMachinePorts.callbacks(tank, input, output, () -> {
                Object fluid = call(tank, "getFluidInTank", 0); long amount = MekanismResourceSupport.amount(fluid);
                return amount > 0 ? Map.of(MekanismResourceSupport.key(fluid), amount) : Map.of();
            }, (key, amount, simulate) -> key instanceof FluidKey ? number(call(tank, "fill", MekanismResourceSupport.sample(key, Math.min(amount, Integer.MAX_VALUE)), action(actions, simulate)), 0) : 0,
                (key, amount, simulate) -> key instanceof FluidKey ? MekanismResourceSupport.amount(call(tank, "drain", MekanismResourceSupport.sample(key, Math.min(amount, Integer.MAX_VALUE)), action(actions, simulate))) : 0));
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static Object action(Class<?> type, boolean simulate) { return Enum.valueOf((Class) type, simulate ? "SIMULATE" : "EXECUTE"); }
}
