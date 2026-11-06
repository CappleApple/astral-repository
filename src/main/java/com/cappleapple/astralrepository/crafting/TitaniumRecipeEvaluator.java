package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import com.cappleapple.astralrepository.port.storage.IItemHandler;

/** A bounded interpreter of native Titanium transforms. No native lifecycle callback is invoked. */
final class TitaniumRecipeEvaluator {
    private static final String ACTIVE = "com.hrznstudio.titanium.block.tile.ActiveTile";
    private static final String TANK = "com.hrznstudio.titanium.component.fluid.FluidTankComponent";
    private static final String INVENTORY = "com.hrznstudio.titanium.component.inventory.InventoryComponent";
    record Address(Object owner, int slot, boolean fluid) {}
    record Quantity(ResourceKey key, long count) {}
    record Fuel(Result resources, int ticks, int powerPerTick) {}
    record Batch(Result resources,int cycles,List<Object> pooledAfter) {}
    record Result(Map<Address, Quantity> before, Map<Address, Quantity> after, Map<String, List<Object>> choices,List<Object> nativeSources) {
        Result { before = Map.copyOf(before); after = Map.copyOf(after); choices = Map.copyOf(choices); nativeSources=List.copyOf(nativeSources); }
        Map<Address, Quantity> consumed() { return difference(before, after); }
        Map<Address, Quantity> produced() { return difference(after, before); }
        private static Map<Address, Quantity> difference(Map<Address, Quantity> first, Map<Address, Quantity> second) {
            Map<Address, Quantity> result = new LinkedHashMap<>();
            first.forEach((address, quantity) -> {
                Quantity other = second.get(address);
                long delta = quantity.count() - (other != null && other.key().equals(quantity.key()) ? other.count() : 0);
                if (delta > 0) result.put(address, new Quantity(quantity.key(), delta));
            });
            return Map.copyOf(result);
        }
    }
    private static final class Allocation { final String type; Object value; Allocation(String type) { this.type = type; } }
    private static final class ShadowArray { final Object[] values; ShadowArray(int length) { if (length < 0 || length > 1024) throw unsupported("shadow array capacity exceeds1024"); values = new Object[length]; } }
    private record Closure(NativeBytecode.Reference implementation, List<Object> captured) {}
    private record ShadowStream(List<Object> values) {}
    private record ShadowCollector(Closure key, Closure value) {}
    private enum WorldToken { INSTANCE }
    private enum RecipeManagerToken { INSTANCE }
    private enum RandomToken { INSTANCE }
    private record FieldKey(Object instance, String owner, String name) {}
    private final Object tile;
    private final Class<?> entryType;
    private final ClassLoader loader;
    private final Map<Object, ItemStack[]> items = new IdentityHashMap<>();
    private final Map<Object, FluidStack> fluids = new IdentityHashMap<>();
    private final Map<FieldKey, Object> shadowFields = new HashMap<>();
    private final Set<Object> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> outputComponents = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> nativeSources=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, Integer> forcedChoices;
    private final Map<String, List<Object>> choices = new LinkedHashMap<>();
    private final Map<String, Integer> choiceOccurrences = new HashMap<>();
    private final Map<Object, String> tagDomains = new IdentityHashMap<>();
    private final Map<Object, Integer> pooledProgress = new IdentityHashMap<>();
    private final Map<Object,NativeBytecode.Reference> nativeFunctions=new IdentityHashMap<>();
    private final Map<String,List<NativeBytecode.Instruction>> slicedBodies=new HashMap<>();
    private final Set<Object> subprocessInputs=Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean subprocessProbe;
    private net.minecraft.server.level.ServerLevel level;
    private net.minecraft.core.RegistryAccess registries;
    private NativeEnchantmentPreview enchanting;
    private boolean roleProbe;
    private int budget = 20000;

    TitaniumRecipeEvaluator(Object tile, Map<Address, Quantity> initial) {
        this(tile, initial, Map.of());
    }
    TitaniumRecipeEvaluator(Object tile, Map<Address, Quantity> initial, Map<String, Integer> forcedChoices) {
        this.tile = tile; entryType = tile.getClass(); loader = tile.getClass().getClassLoader();
        this.forcedChoices = Map.copyOf(forcedChoices);
        if (tile instanceof net.minecraft.world.level.block.entity.BlockEntity entity && entity.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) inLevel(serverLevel);
        boolean augmented = false;
        for (Class<?> current = tile.getClass(); current != null; current = current.getSuperclass()) if (current.getName().equals("com.hrznstudio.titanium.block.tile.MachineTile")) augmented = true;
        Object augment = augmented ? call(tile, "com.hrznstudio.titanium.block.tile.MachineTile", "getAugmentInventory") : null;
        Object inventories = call(tile, ACTIVE, "getMultiInventoryComponent");
        for (Object inventory : inventories == null ? List.of() : (Collection<?>) call(inventories, "com.hrznstudio.titanium.component.inventory.MultiInventoryComponent", "getInventoryHandlers")) {
            if (inventory == augment || !(inventory instanceof IItemHandler handler)) continue;
            ItemStack[] stacks = new ItemStack[handler.getSlots()];
            for (int slot = 0; slot < stacks.length; slot++) stacks[slot] = emptyItem();
            items.put(inventory, stacks);
        }
        Object tanks = call(tile, ACTIVE, "getMultiTankComponent");
        for (Object tank : tanks == null ? List.of() : (Collection<?>) call(tanks, "com.hrznstudio.titanium.component.fluid.MultiTankComponent", "getTanks")) fluids.put(tank, FluidStack.EMPTY);
        initial.forEach((address, quantity) -> {
            if (address.fluid()) {
                if (!fluids.containsKey(address.owner()) || !(quantity.key() instanceof FluidKey fluid)) throw unsupported("foreign fluid buffer");
                FluidStack stack = fluid.sample().copyWithAmount(Math.toIntExact(quantity.count())); owned.add(stack); fluids.put(address.owner(), stack);
            } else {
                if (!items.containsKey(address.owner()) || !(quantity.key() instanceof ItemKey item)) throw unsupported("foreign item buffer");
                ItemStack stack = item.sample().copyWithCount(Math.toIntExact(quantity.count())); owned.add(stack); items.get(address.owner())[address.slot()] = stack;
            }
        });
    }
    TitaniumRecipeEvaluator inLevel(net.minecraft.server.level.ServerLevel level) { this.level = level; registries = level.registryAccess(); return this; }
    TitaniumRecipeEvaluator enchanting(NativeEnchantmentPreview context) { enchanting=context; return this; }
    TitaniumRecipeEvaluator fullyFundedPools() {
        for(Class<?> type=tile.getClass();type!=null && !type.getName().equals("com.buuz135.industrial.block.tile.IndustrialProcessingTile");type=type.getSuperclass()) {
            var shape=NativeBytecode.inspect(type);
            for(var field:shape.fields()) if((field.access()&8)==0 && field.descriptor().endsWith("/ProgressBarComponent;")) {
                Object component=new NativeBytecode.Reference(180,shape.name(),field.name(),field.descriptor()).readInstance(tile);
                if(component!=null) pooledProgress.put(component,0);
            }
        }
        return this;
    }
    private TitaniumRecipeEvaluator(Class<?> entryType, Object target) { this.tile = target; this.entryType = entryType; loader = entryType.getClassLoader(); forcedChoices = Map.of(); }
    /** Exercises the same bounded interpreter without an optional machine runtime. */
    static Object evaluatePure(Class<?> entryType, String name, String descriptor, Object target, List<Object> arguments) {
        return new TitaniumRecipeEvaluator(entryType, target).execute(entryType.getName(), name, descriptor, target, arguments, 0);
    }
    static Set<Object> outputComponents(Object tile, net.minecraft.server.level.ServerLevel level) {
        return outputComponents(tile,level,null);
    }
    static Set<Object> outputComponents(Object tile,net.minecraft.server.level.ServerLevel level,NativeEnchantmentPreview context) {
        var evaluator = new TitaniumRecipeEvaluator(tile, Map.of()).inLevel(level).enchanting(context);
        evaluator.roleProbe=true;
        evaluator.complete();
        return Collections.unmodifiableSet(evaluator.outputComponents);
    }
    static Set<Object> referencedComponents(Object tile) {
        var evaluator = new TitaniumRecipeEvaluator(tile, Map.of());
        Set<Object> result = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> owners = new HashSet<>();
        for (Class<?> current = tile.getClass(); current != null; current = current.getSuperclass()) owners.add(current.getName());
        ArrayDeque<NativeBytecode.Method> pending = new ArrayDeque<>(); Set<String> visited = new HashSet<>();
        pending.add(evaluator.method(tile.getClass(), "canIncrease", "()Z"));
        pending.add(evaluator.method(tile.getClass(), "onFinish", "()Ljava/lang/Runnable;"));
        while (!pending.isEmpty()) {
            var method = pending.removeFirst(); String identity = method.name() + method.descriptor(); if (!visited.add(identity)) continue;
            if (visited.size() > 128) throw unsupported("native component-reference graph exceeds128 methods");
            for (var reference : method.references()) if (owners.contains(reference.owner())) {
                if (reference.opcode() == 180) {
                    Object value = reference.readInstance(tile);
                    if (value != null && value.getClass().getName().equals("com.hrznstudio.titanium.component.bundle.LockableInventoryBundle")) value = call(value, value.getClass().getName(), "getInventory");
                    if (evaluator.items.containsKey(value) || evaluator.fluids.containsKey(value)) result.add(value);
                } else if (reference.opcode() >= 182 && !reference.name().equals("<init>")) {
                    try { pending.add(evaluator.method(Class.forName(reference.owner(), false, evaluator.loader), reference.name(), reference.descriptor())); }
                    catch (ClassNotFoundException | UnsupportedOperationException absent) { /* An inherited native API is not a tile-owned body. */ }
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }
    static Set<Object> subprocessInputs(Object tile,net.minecraft.server.level.ServerLevel level) {
        var evaluator=new TitaniumRecipeEvaluator(tile,Map.of()).inLevel(level); evaluator.subprocessProbe=true;
        try { evaluator.complete(); }
        catch(UnsupportedOperationException unsupported) { if(evaluator.subprocessInputs.isEmpty()) return Set.of(); }
        return Collections.unmodifiableSet(evaluator.subprocessInputs);
    }

    static List<Address> inputs(Object tile) {
        var evaluator = new TitaniumRecipeEvaluator(tile, Map.of());
        List<Address> result = new ArrayList<>();
        for (var entry : evaluator.items.entrySet()) for (int slot = 0; slot < entry.getValue().length; slot++) result.add(new Address(entry.getKey(), slot, false));
        for (Object tank : evaluator.fluids.keySet()) {
            Object action = call(tank, TANK, "getTankAction");
            if (Boolean.TRUE.equals(call(action, TANK + "$Action", "canFill"))) result.add(new Address(tank, 0, true));
        }
        return List.copyOf(result);
    }
    static boolean accepts(Address address, ResourceKey key) {
        if (!address.fluid() && key instanceof ItemKey item) return ((IItemHandler) address.owner()).isItemValid(address.slot(), item.sample());
        if (address.fluid() && key instanceof FluidKey fluid)
            return Boolean.TRUE.equals(IndustrialForegoingBackend.call(address.owner(), TANK, "isFluidValid", new Class<?>[]{FluidStack.class}, fluid.sample()));
        return false;
    }
    static int capacity(Address address) {
        return address.fluid() ? ((Number) call(address.owner(), TANK, "getCapacity")).intValue() : ((IItemHandler) address.owner()).getSlotLimit(address.slot());
    }

    Result evaluate() {
        Map<Address, Quantity> before = snapshot();
        deriveCachedGuards();
        boolean ready = false;
        for (int tick = 0; tick < 128; tick++) {
            Map<Address, Quantity> resources = snapshot(); var progress = new IdentityHashMap<>(pooledProgress); var fields = new HashMap<>(shadowFields);
            Object condition = execute(tile.getClass().getName(), "canIncrease", "()Z", tile, List.of(), 0);
            if (truth(condition)) { ready = true; break; }
            if (resources.equals(snapshot()) && progress.equals(pooledProgress) && fields.equals(shadowFields)) return null;
        }
        if (!ready) throw unsupported("native pooled input preparation exceeds128 ticks");
        complete();
        return new Result(before, snapshot(), choices,List.copyOf(nativeSources));
    }
    Batch evaluateBatch() {
        Map<Address,Quantity> before=snapshot(),harvested=new LinkedHashMap<>(); List<Object> initialPools=pooledSnapshot(); int cycles=0;
        for(int attempt=0;attempt<128;attempt++) {
            Result next=evaluate();
            if(next==null) return cycles==0?null:new Batch(aggregate(before,harvested),cycles,pooledSnapshot());
            cycles++;
            for(var entry:next.produced().entrySet()) {
                Address address=entry.getKey(); Quantity quantity=entry.getValue();
                if(before.containsKey(address)) throw unsupported("pooled batch changes a consumed/setup buffer into output");
                Quantity previous=harvested.get(address);
                if(previous!=null && !previous.key().equals(quantity.key())) throw unsupported("pooled batch changes output identity within one buffer");
                harvested.put(address,new Quantity(quantity.key(),Math.addExact(previous==null?0:previous.count(),quantity.count())));
                if(address.fluid()) fluids.get(address.owner()).shrink(Math.toIntExact(quantity.count()));
                else items.get(address.owner())[address.slot()].shrink(Math.toIntExact(quantity.count()));
            }
            Map<Address,Quantity> current=snapshot();
            boolean ownedInputsGone=before.entrySet().stream().allMatch(entry->{ Quantity actual=current.get(entry.getKey()); return actual==null || !actual.key().equals(entry.getValue().key()); });
            if(ownedInputsGone && pooledSnapshot().equals(initialPools)) return new Batch(aggregate(before,harvested),cycles,pooledSnapshot());
        }
        throw unsupported("native owned pooled batch exceeds128 cycles");
    }
    private Result aggregate(Map<Address,Quantity> before,Map<Address,Quantity> harvested) {
        Map<Address,Quantity> after=new LinkedHashMap<>(snapshot());
        for(var entry:harvested.entrySet()) {
            Quantity previous=after.get(entry.getKey()); Quantity quantity=entry.getValue();
            if(previous!=null && !previous.key().equals(quantity.key())) throw unsupported("native pooled output identity conflicts with retained data");
            after.put(entry.getKey(),new Quantity(quantity.key(),Math.addExact(previous==null?0:previous.count(),quantity.count())));
        }
        return new Result(before,after,choices,List.copyOf(nativeSources));
    }
    List<Object> pooledSnapshot() { return pooledState(tile,pooledProgress); }
    /** Evaluate only pure post-processing cached predicates, never the native processing/world tick. */
    private void deriveCachedGuards() {
        NativeBytecode.Method guard=method(tile.getClass(),"canIncrease","()Z");
        Set<String> booleans=new HashSet<>();
        for(var reference:guard.references()) if(reference.opcode()==180 && reference.descriptor().equals("Z")) booleans.add(reference.owner()+"/"+reference.name());
        if(booleans.isEmpty()) return;
        for(Class<?> current=tile.getClass();current!=null && current.getName().startsWith("com.buuz135.industrial.");current=current.getSuperclass()) {
            var shape=NativeBytecode.inspect(current);
            for(var candidate:shape.methods()) {
                if(!candidate.name().equals("serverTick") || (candidate.access()&64)!=0) continue;
                boolean writes=candidate.references().stream().anyMatch(reference->reference.opcode()==181 && booleans.contains(reference.owner()+"/"+reference.name()));
                if(!writes) continue;
                int start=-1;
                for(int index=0;index<candidate.instructions().size();index++) {
                    var instruction=candidate.instructions().get(index); var reference=instruction.reference();
                    if(instruction.opcode()==183 && reference!=null && reference.owner().equals("com.buuz135.industrial.block.tile.IndustrialProcessingTile") && reference.name().equals("serverTick")) { start=index+1; break; }
                }
                if(start<0) throw unsupported("cached native guard has no isolated post-processing derivation");
                for(int index=0;index<start-1;index++) {
                    int opcode=candidate.instructions().get(index).opcode();
                    if(opcode!=0 && !(opcode>=21 && opcode<=45)) throw unsupported("native cached guard tick has work before its processing boundary");
                }
                String key=shape.name()+candidate.name()+candidate.descriptor(); slicedBodies.put(key,candidate.instructions().subList(start,candidate.instructions().size()));
                Map<Address,Quantity> resources=snapshot(); Map<FieldKey,Object> fields=new HashMap<>(shadowFields);
                var entity=(net.minecraft.world.level.block.entity.BlockEntity)tile;
                try { execute(shape.name(),candidate.name(),candidate.descriptor(),tile,List.of(WorldToken.INSTANCE,entity.getBlockPos(),entity.getBlockState(),tile),0); }
                finally { slicedBodies.remove(key); }
                if(!resources.equals(snapshot())) throw unsupported("native cached guard derivation mutates resource buffers");
                for(var field:shadowFields.entrySet()) if(!Objects.equals(fields.get(field.getKey()),field.getValue()) && !booleans.contains(field.getKey().owner()+"/"+field.getKey().name()))
                    throw unsupported("native cached guard derivation writes unrelated state");
            }
        }
    }

    static List<Object> pooledState(Object tile) {
        return pooledState(tile,Map.of());
    }
    private static List<Object> pooledState(Object tile,Map<Object,Integer> shadow) {
        List<Object> result = new ArrayList<>();
        for (Class<?> type = tile.getClass(); type != null && !type.getName().equals("com.buuz135.industrial.block.tile.IndustrialProcessingTile"); type = type.getSuperclass()) {
            var shape = NativeBytecode.inspect(type);
            for (var field : shape.fields()) if ((field.access() & 8) == 0 && field.descriptor().endsWith("/ProgressBarComponent;")) {
                Object component = new NativeBytecode.Reference(180, shape.name(), field.name(), field.descriptor()).readInstance(tile);
                if (component != null) result.add(List.of(shape.name(), field.name(),shadow.containsKey(component)?shadow.get(component):call(component, "com.hrznstudio.titanium.component.progress.ProgressBarComponent", "getProgress"), call(component, "com.hrznstudio.titanium.component.progress.ProgressBarComponent", "getMaxProgress")));
            }
        }
        return List.copyOf(result);
    }
    /** Supplies only immutable recipe/cache data; the native completion still runs solely in this interpreter. */
    Result stored(Object recipe, net.minecraft.core.RegistryAccess registries, int selectedSlot) {
        this.registries = registries; int injected = 0;
        for (Class<?> current = tile.getClass(); current != null && current.getName().startsWith("com.buuz135.industrial."); current = current.getSuperclass()) {
            var shape = NativeBytecode.inspect(current);
            for (var field : shape.fields()) {
                if ((field.access() & 8) != 0) continue;
                String descriptor = field.descriptor(); boolean array = descriptor.startsWith("[");
                String component = array ? descriptor.substring(1) : descriptor;
                if (!component.startsWith("L") || !component.endsWith(";")) continue;
                try {
                    Class<?> contract = Class.forName(component.substring(1, component.length()-1).replace('/', '.'), false, loader);
                    if (!net.minecraft.world.item.crafting.Recipe.class.isAssignableFrom(contract) || !contract.isInstance(recipe)) continue;
                    var ref = new NativeBytecode.Reference(180, shape.name(), field.name(), descriptor); Object value = recipe;
                    if (array) {
                        Object nativeArray = ref.readInstance(tile); int length = nativeArray == null ? selectedSlot + 1 : Array.getLength(nativeArray);
                        if (selectedSlot >= length) throw unsupported("native recipe cache has no selected input index");
                        ShadowArray replacement = new ShadowArray(length); replacement.values[selectedSlot] = recipe; value = replacement;
                    }
                    shadowFields.put(new FieldKey(tile, shape.name(), field.name()), value); injected++;
                } catch (ClassNotFoundException unavailable) { throw unsupported("native recipe cache contract unavailable: " + component); }
            }
        }
        if (injected == 0) throw unsupported("native completion has no typed stored-recipe cache");
        Map<Address, Quantity> before = snapshot(); complete(); return new Result(before, snapshot(), choices,List.copyOf(nativeSources));
    }
    Fuel fuel(Object nativeType, Object nativeInputs) {
        Map<Address, Quantity> before = snapshot();
        var methods = NativeBytecode.inspect(nativeType.getClass()).methods();
        var ready = methods.stream().filter(method -> method.name().equals("canStart") && method.descriptor().startsWith("([")).findFirst().orElseThrow(() -> unsupported("native generator readiness contract unavailable"));
        if (!truth(execute(nativeType.getClass().getName(), ready.name(), ready.descriptor(), nativeType, List.of(nativeInputs), 0))) return null;
        var consume = methods.stream().filter(method -> method.name().equals("getTimeAndPowerGeneration") && method.descriptor().startsWith("([")).findFirst().orElseThrow(() -> unsupported("native generator fuel contract unavailable"));
        Object result = execute(nativeType.getClass().getName(), consume.name(), consume.descriptor(), nativeType, List.of(nativeInputs), 0);
        if (!(result instanceof Map.Entry<?,?> values)) throw unsupported("native generator time/power tuple unavailable");
        return new Fuel(new Result(before, snapshot(), choices,List.copyOf(nativeSources)), integer(values.getKey()), integer(values.getValue()));
    }
    private void complete() {
        NativeBytecode.Method finish = method(tile.getClass(), "onFinish", "()Ljava/lang/Runnable;");
        List<NativeBytecode.Reference> bodies = finish.references().stream().filter(reference -> reference.opcode() == 186).toList();
        if (bodies.size() != 1) throw unsupported("onFinish requires one native LambdaMetafactory implementation, found " + bodies.size());
        var body = bodies.getFirst();
        if (!arguments(body.descriptor()).isEmpty()) throw unsupported("completion lambda has opaque captured arguments: " + body.owner() + "." + body.name());
        execute(body.owner(), body.name(), body.descriptor(), tile, List.of(), 0);
    }

    private Map<Address, Quantity> snapshot() {
        Map<Address, Quantity> result = new LinkedHashMap<>();
        items.forEach((inventory, stacks) -> { for (int slot = 0; slot < stacks.length; slot++) if (!stacks[slot].isEmpty()) result.put(new Address(inventory, slot, false), new Quantity(new ItemKey(stacks[slot]), stacks[slot].getCount())); });
        fluids.forEach((tank, stack) -> { if (!stack.isEmpty()) result.put(new Address(tank, 0, true), new Quantity(new FluidKey(stack), stack.getAmount())); });
        return result;
    }
    private NativeBytecode.Method method(Class<?> type, String name, String descriptor) {
        for (Class<?> current = type; current != null && (current == entryType || current.getName().startsWith("com.buuz135.industrial.")); current = current.getSuperclass())
            for (var method : NativeBytecode.inspect(current).methods()) if (method.name().equals(name) && method.descriptor().equals(descriptor)) return method;
        throw unsupported("method body unavailable: " + type.getName() + "." + name + descriptor);
    }
    private NativeBytecode.Reference enumFunction(Enum<?> constant,String functionalDescriptor) {
        Class<?> type=constant.getDeclaringClass(); var shape=NativeBytecode.inspect(type);
        var initializer=shape.methods().stream().filter(method->method.name().equals("<clinit>")).findFirst().orElseThrow(()->unsupported("native enum initializer unavailable"));
        List<NativeBytecode.Reference> candidates=new ArrayList<>();
        for(var instruction:initializer.instructions()) {
            if(instruction.opcode()==186 && instruction.constant() instanceof NativeBytecode.Dynamic dynamic && instruction.reference()!=null
                    && dynamic.descriptor().endsWith(")"+functionalDescriptor)) {
                if(!arguments(dynamic.descriptor()).isEmpty()) throw unsupported("native enum function has captured constructor state");
                candidates.add(instruction.reference());
            }
            var reference=instruction.reference();
            if(instruction.opcode()==179 && reference!=null && reference.owner().equals(type.getName()) && reference.descriptor().equals("L"+type.getName().replace('.','/')+";")) {
                Object value=new NativeBytecode.Reference(178,reference.owner(),reference.name(),reference.descriptor()).readStatic(loader);
                if(value==constant) {
                    if(candidates.size()!=1) throw unsupported("native enum functional role is ambiguous: " + type.getName() + "/" + constant.name());
                    return candidates.getFirst();
                }
                candidates.clear();
            }
        }
        throw unsupported("native enum function was not associated with its actual constant");
    }
    private NativeBytecode.Reference tableFunction(Object value,String functionalDescriptor) {
        String dataType=value.getClass().getName(),arrayDescriptor="[L"+dataType.replace('.','/')+";";
        Set<Class<?>> owners=new LinkedHashSet<>(); owners.add(entryType); owners.add(value.getClass());
        if(value.getClass().getEnclosingClass()!=null) owners.add(value.getClass().getEnclosingClass());
        for(Class<?> owner:owners) {
            var shape=NativeBytecode.inspect(owner);
            for(var field:shape.fields()) if((field.access()&8)!=0 && field.descriptor().equals(arrayDescriptor)) {
                Object table=new NativeBytecode.Reference(178,shape.name(),field.name(),field.descriptor()).readStatic(loader); if(table==null) continue;
                int wanted=-1; for(int index=0;index<Array.getLength(table);index++) if(Array.get(table,index)==value) { wanted=index; break; }
                if(wanted<0) continue;
                var initializer=shape.methods().stream().filter(method->method.name().equals("<clinit>")).findFirst().orElseThrow(()->unsupported("native static process table initializer unavailable"));
                int constructed=-1; boolean finished=false; List<NativeBytecode.Reference> candidates=new ArrayList<>();
                var code=initializer.instructions();
                for(int index=0;index<code.size();index++) {
                    var instruction=code.get(index); var reference=instruction.reference();
                    if(instruction.opcode()==187 && dataType.equals(instruction.constant())) {
                        if(index<2 || code.get(index-2).opcode()!=89 || !(code.get(index-1).constant() instanceof Integer literal))
                            throw unsupported("native process table has a dynamic construction index");
                        constructed=literal; candidates.clear(); finished=false;
                    }
                    if(constructed>=0 && instruction.opcode()==186 && instruction.constant() instanceof NativeBytecode.Dynamic dynamic && reference!=null
                            && dynamic.descriptor().endsWith(")"+functionalDescriptor)) {
                        if(!arguments(dynamic.descriptor()).isEmpty()) throw unsupported("native process table callback captures constructor state");
                        candidates.add(reference);
                    }
                    if(constructed>=0 && instruction.opcode()==183 && reference!=null && reference.owner().equals(dataType) && reference.name().equals("<init>")) finished=true;
                    if(finished && instruction.opcode()==83) {
                        if(constructed==wanted) {
                            if(candidates.size()!=1) throw unsupported("native process table callback role is ambiguous");
                            return candidates.getFirst();
                        }
                        constructed=-1; finished=false; candidates.clear();
                    }
                }
            }
        }
        throw unsupported("native callback is not associated with an immutable static process table");
    }
    private Object execute(String owner, String name, String descriptor, Object target, List<Object> parameters, int depth) {
        if (depth > 12) throw unsupported("native call depth exceeded at " + owner + "." + name);
        try {
            var method = method(Class.forName(owner, false, loader), name, descriptor);
            Object[] locals = new Object[256]; int local = 0;
            if ((method.access() & 8) == 0) locals[local++] = target;
            List<String> types = arguments(descriptor);
            for (int index = 0; index < parameters.size(); index++) { locals[local] = parameters.get(index); local += types.get(index).equals("J") || types.get(index).equals("D") ? 2 : 1; }
            List<Object> stack = new ArrayList<>(); var code = slicedBodies.getOrDefault(owner+name+descriptor,method.instructions()); Map<Integer, Integer> offsets = new HashMap<>();
            for (int index = 0; index < code.size(); index++) offsets.put(code.get(index).offset(), index);
            for (int pc = 0; pc < code.size(); pc++) {
                if (--budget < 0) throw unsupported("native instruction budget exceeded at " + owner + "." + name);
                var instruction = code.get(pc); int opcode = instruction.opcode();
                if (opcode == 0) continue;
                if (opcode == 1) { stack.add(null); continue; }
                if (opcode >= 2 && opcode <= 20) { stack.add(instruction.constant()); continue; }
                if (opcode >= 21 && opcode <= 25) { stack.add(locals[instruction.operand()]); continue; }
                if (opcode >= 26 && opcode <= 45) { stack.add(locals[(opcode - 26) % 4]); continue; }
                if (opcode >= 46 && opcode <= 53) { int index = integer(pop(stack)); Object array = unwrap(pop(stack)); stack.add(array instanceof ShadowArray shadow ? shadow.values[index] : Array.get(array, index)); continue; }
                if (opcode >= 54 && opcode <= 58) { locals[instruction.operand()] = pop(stack); continue; }
                if (opcode >= 59 && opcode <= 78) { locals[(opcode - 59) % 4] = pop(stack); continue; }
                if (opcode == 83) { Object value = pop(stack); int index = integer(pop(stack)); Object array = pop(stack); if (!(array instanceof ShadowArray shadow)) throw unsupported("write to a non-shadow array"); shadow.values[index] = value; continue; }
                if (opcode == 87) { pop(stack); continue; }
                if (opcode == 89) { stack.add(stack.getLast()); continue; }
                if (opcode >= 96 && opcode <= 115) {
                    Number right = (Number)pop(stack), left = (Number)pop(stack); int operation = (opcode - 96) / 4, kind = (opcode - 96) % 4;
                    if (kind >= 2) {
                        double a = left.doubleValue(), b = right.doubleValue(); double value = switch (operation) { case 0 -> a+b; case 1 -> a-b; case 2 -> a*b; case 3 -> a/b; default -> a%b; };
                        stack.add(kind == 2 ? (float)value : value);
                    } else {
                        long a = left.longValue(), b = right.longValue(); long value = switch (operation) { case 0 -> a+b; case 1 -> a-b; case 2 -> a*b; case 3 -> a/b; default -> a%b; };
                        stack.add(kind == 0 ? (int)value : value);
                    }
                    continue;
                }
                if (opcode >= 116 && opcode <= 119) { Number value = (Number)pop(stack); stack.add(opcode == 116 ? -value.intValue() : opcode == 117 ? -value.longValue() : -value.doubleValue()); continue; }
                if (opcode >= 120 && opcode <= 131) {
                    long right = number(pop(stack)), left = number(pop(stack));
                    long value = switch (opcode) { case 96,97 -> left+right; case 100,101 -> left-right; case 104,105 -> left*right; case 108,109 -> left/right; case 112,113 -> left%right; case 120,121 -> left << right; case 122,123 -> left >> right; case 124,125 -> left >>> right; case 126,127 -> left & right; case 128,129 -> left | right; case 130,131 -> left ^ right; default -> throw unsupported("numeric opcode " + opcode); };
                    stack.add(opcode % 2 == 0 ? (int)value : value); continue;
                }
                if (opcode == 132) { locals[instruction.operand()] = integer(locals[instruction.operand()]) + integer(instruction.constant()); continue; }
                if (opcode == 133) { stack.add(number(pop(stack))); continue; }
                if (opcode >= 134 && opcode <= 144) {
                    Number value = (Number)pop(stack);
                    stack.add(switch (opcode) { case 136,139,142 -> value.intValue(); case 140,143 -> value.longValue(); case 134,137,144 -> value.floatValue(); default -> value.doubleValue(); });
                    continue;
                }
                if (opcode >= 148 && opcode <= 152) {
                    Number right = (Number)pop(stack), left = (Number)pop(stack);
                    if (opcode == 148) stack.add(Long.compare(left.longValue(), right.longValue()));
                    else {
                        double a = left.doubleValue(), b = right.doubleValue();
                        stack.add(Double.isNaN(a) || Double.isNaN(b) ? (opcode == 149 || opcode == 151 ? -1 : 1) : a > b ? 1 : a == b ? 0 : -1);
                    }
                    continue;
                }
                if (opcode >= 153 && opcode <= 166 || opcode == 198 || opcode == 199) {
                    Object right = pop(stack), left = opcode >= 159 && opcode <= 166 ? pop(stack) : null;
                    boolean branch = switch (opcode) { case 153 -> number(right)==0; case 154 -> number(right)!=0; case 155 -> number(right)<0; case 156 -> number(right)>=0; case 157 -> number(right)>0; case 158 -> number(right)<=0; case 159 -> number(left)==number(right); case 160 -> number(left)!=number(right); case 161 -> number(left)<number(right); case 162 -> number(left)>=number(right); case 163 -> number(left)>number(right); case 164 -> number(left)<=number(right); case 165 -> unwrap(left)==unwrap(right); case 166 -> unwrap(left)!=unwrap(right); case 198 -> right==null; case 199 -> right!=null; default -> false; };
                    if (branch) pc = jump(offsets, instruction.operand()) - 1; continue;
                }
                if (opcode == 167 || opcode == 200) { pc = jump(offsets, instruction.operand()) - 1; continue; }
                if(opcode==170 || opcode==171) {
                    int selected=integer(pop(stack)); var targets=(NativeBytecode.SwitchTargets)instruction.constant();
                    pc=jump(offsets,targets.targets().getOrDefault(selected,instruction.operand()))-1; continue;
                }
                if (opcode >= 172 && opcode <= 176) return unwrap(pop(stack));
                if (opcode == 177) return null;
                if (opcode == 178) { stack.add(instruction.reference().readStatic(loader)); continue; }
                if (opcode == 180) {
                    Object object = unwrap(pop(stack)); var ref = instruction.reference(); FieldKey key = new FieldKey(object, ref.owner(), ref.name());
                    if (object == WorldToken.INSTANCE) {
                        if (NativeMinecraftCalls.name(ref).equals("worldRandom")) { stack.add(RandomToken.INSTANCE); continue; }
                        throw unsupported("world field " + ref.owner() + "." + ref.name());
                    }
                    if (object == tile && ref.descriptor().equals("L" + net.minecraft.world.level.Level.class.getName().replace('.', '/') + ";")) { stack.add(WorldToken.INSTANCE); continue; }
                    Object value = shadowFields.containsKey(key) ? shadowFields.get(key) : ref.readInstance(object);
                    if(ref.descriptor().startsWith("Ljava/util/function/") && value!=null) {
                        if(object instanceof Enum<?> constant) nativeFunctions.put(value,enumFunction(constant,ref.descriptor()));
                        else if(object!=null && object.getClass().getName().startsWith("com.buuz135.industrial.")) nativeFunctions.put(value,tableFunction(object,ref.descriptor()));
                    }
                    if (value instanceof net.minecraft.world.level.Level || value instanceof net.minecraft.world.entity.Entity) throw unsupported("world/entity field " + ref.owner() + "." + ref.name());
                    stack.add(value); continue;
                }
                if (opcode == 181) {
                    Object value = unwrap(pop(stack)), object = unwrap(pop(stack)); var ref = instruction.reference();
                    if (object != tile && !ref.owner().startsWith("com.buuz135.industrial.")) throw unsupported("write to non-shadow native state " + ref.owner() + "." + ref.name());
                    shadowFields.put(new FieldKey(object, ref.owner(), ref.name()), value); continue;
                }
                if (opcode >= 182 && opcode <= 185) {
                    var ref = instruction.reference(); List<String> argumentTypes = arguments(ref.descriptor()); List<Object> values = new ArrayList<>();
                    for (int index = 0; index < argumentTypes.size(); index++) values.addFirst(unwrap(pop(stack)));
                    Object receiver = opcode == 184 ? null : pop(stack);
                    Object value;
                    try { value = invoke(ref, receiver, values, depth + 1); }
                    catch (UnsupportedOperationException known) { throw known; }
                    catch (RuntimeException failure) { throw unsupported("at " + ref.owner() + "." + ref.name() + ref.descriptor() + ": " + failure.getMessage()); }
                    if (!ref.descriptor().endsWith(")V")) stack.add(value); continue;
                }
                if (opcode == 186 && instruction.constant() instanceof NativeBytecode.Dynamic dynamic && instruction.reference() != null) {
                    List<Object> captured = new ArrayList<>();
                    for (int index = 0; index < arguments(dynamic.descriptor()).size(); index++) captured.addFirst(unwrap(pop(stack)));
                    stack.add(new Closure(instruction.reference(), Collections.unmodifiableList(captured))); continue;
                }
                if (opcode == 187) { stack.add(new Allocation((String) instruction.constant())); continue; }
                if (opcode == 189) { stack.add(new ShadowArray(integer(pop(stack)))); continue; }
                if (opcode == 190) { Object array = unwrap(pop(stack)); stack.add(array instanceof ShadowArray shadow ? shadow.values.length : Array.getLength(array)); continue; }
                if (opcode == 192) continue;
                if (opcode == 193) { Object value = unwrap(pop(stack)); stack.add(Class.forName((String)instruction.constant(), false, loader).isInstance(value)); continue; }
                throw unsupported("unsupported opcode " + opcode + " at " + owner + "." + name + ":" + instruction.offset());
            }
            throw unsupported("native method has no return: " + owner + "." + name);
        } catch (ClassNotFoundException absent) { throw unsupported("native class unavailable: " + owner); }
    }

    private Object invoke(NativeBytecode.Reference ref, Object receiver, List<Object> args, int depth) {
        Object target = unwrap(receiver); String name = NativeMinecraftCalls.name(ref), owner = ref.owner();
        if(target instanceof net.minecraft.world.item.crafting.Recipe<?> && Set.of("getResultItem","assemble","canIncrease","consume").contains(name)) nativeSources.add(target);
        if(subprocessProbe && target==tile && args.size()==4 && items.containsKey(args.get(0)) && items.containsKey(args.get(1))
                && args.get(2)!=null && (args.get(3) instanceof Boolean || args.get(3) instanceof Number)) {
            subprocessInputs.add(args.getFirst()); return false;
        }
        if(nativeFunctions.containsKey(target) && Set.of("apply","test","get","accept").contains(name)) {
            var implementation=nativeFunctions.get(target);
            if(implementation.owner().startsWith("com.buuz135.industrial.")) return execute(implementation.owner(),implementation.name(),implementation.descriptor(),null,args,depth+1);
            return invoke(new NativeBytecode.Reference(184,implementation.owner(),implementation.name(),implementation.descriptor()),null,args,depth+1);
        }
        if (target == tile && name.equals("markForUpdate") && ref.descriptor().equals("()V")) return null;
        if (NativeBackendReflection.contract(target, "com.hrznstudio.titanium.component.progress.ProgressBarComponent")) {
            if (name.equals("getProgress")) return pooledProgress.computeIfAbsent(target, component -> integer(call(component, "com.hrznstudio.titanium.component.progress.ProgressBarComponent", "getProgress")));
            if (name.equals("getMaxProgress")) return call(target, "com.hrznstudio.titanium.component.progress.ProgressBarComponent", name);
            if (name.equals("setProgress") && args.size() == 1) { pooledProgress.put(target, integer(args.getFirst())); return null; }
        }
        if (owner.equals("com.hrznstudio.titanium.util.RecipeUtil") && name.equals("getRecipes") && args.size() == 2 && args.getFirst() == WorldToken.INSTANCE) {
            if (level == null) throw unsupported("native recipe-provider lookup requires server context");
            return NativeRecipeSource.allFor(level, args.get(1)).stream().map(NativeRecipeSource.Entry::value).toList();
        }
        if(owner.equals("com.hrznstudio.titanium.util.RecipeUtil") && name.equals("getSmelingRecipeFor") && args.size()==2 && args.getFirst()==WorldToken.INSTANCE && args.get(1) instanceof ItemStack input) {
            if(level==null) throw unsupported("native smelting lookup requires server context");
            return NativeRecipeSource.allFor(level,net.minecraft.world.item.crafting.RecipeType.SMELTING).stream().map(NativeRecipeSource.Entry::value)
                    .filter(recipe->!NativeRecipeSource.ingredients(recipe).isEmpty() && NativeRecipeSource.ingredients(recipe).getFirst().test(input)).findFirst().orElse(null);
        }
        if (target == WorldToken.INSTANCE && name.equals("registryAccess") && NativeMinecraftCalls.known(ref)) {
            if (registries == null) throw unsupported("native recipe preview requires registry access");
            return registries;
        }
        if(target==WorldToken.INSTANCE && name.equals("getRecipeManager") && NativeMinecraftCalls.known(ref)) return RecipeManagerToken.INSTANCE;
        if(target==RecipeManagerToken.INSTANCE && name.equals("getAllRecipesFor") && NativeMinecraftCalls.known(ref)) {
            if(level==null) throw unsupported("native recipe lookup requires server context");
            return NativeRecipeSource.allFor(level,args.getFirst()).stream().map(NativeRecipeSource::nativeHolder).toList();
        }
        var nativeEntry=NativeRecipeSource.entry(target);
        if(nativeEntry!=null && NativeMinecraftCalls.known(ref) && args.isEmpty()) {
            if(name.equals("id")) return nativeEntry.id();
            if(name.equals("value")) return nativeEntry.value();
        }
        if(target==WorldToken.INSTANCE && enchanting!=null) return enchanting.world(ref,args);
        if(target instanceof net.minecraft.core.BlockPos position && name.equals("above") && NativeMinecraftCalls.known(ref)) return position.above();
        if(owner.equals(net.minecraft.world.item.enchantment.EnchantmentHelper.class.getName()) && name.equals("enchantItem")) {
            if(enchanting==null) throw unsupported("native random enchanting requires an exact requested/taught target");
            ItemStack preview=enchanting.enchant(args); owned.add(preview); return preview;
        }
        if(owner.equals("java.util.Optional") && name.equals("empty")) return Optional.empty();
        if(owner.equals("java.util.Optional") && name.equals("of")) return Optional.of(args.getFirst());
        if(owner.equals("java.util.Optional") && name.equals("ofNullable")) return Optional.ofNullable(args.getFirst());
        if(owner.equals("java.lang.Boolean") && name.equals("valueOf")) return truth(args.getFirst());
        if(target instanceof Boolean value && name.equals("booleanValue")) return value;
        if(owner.equals(net.minecraft.world.item.enchantment.EnchantmentHelper.class.getName()) && Set.of("getEnchantmentsForCrafting","getEnchantments").contains(name)) {
            Object result=NativeBackendReflection.call(net.minecraft.world.item.enchantment.EnchantmentHelper.class,name,args.toArray());
            if(result instanceof Map<?,?> values) {
                if(values.size()>1024) throw unsupported("shadow native enchantment map exceeds1024 entries");
                Map<Object,Object> copy=new LinkedHashMap<>(); for(var entry:values.entrySet()) copy.put(entry.getKey(),entry.getValue());
                owned.add(copy); return copy;
            }
            return result;
        }
        if(owner.equals(net.minecraft.world.item.enchantment.EnchantmentHelper.class.getName()) && name.equals("setEnchantments")) {
            if(args.size()!=2 || args.stream().noneMatch(ItemStack.class::isInstance)) throw unsupported("native enchantment mutation has no owned stack");
            for(Object argument:args) if(argument instanceof ItemStack stack) own(stack);
            return NativeBackendReflection.call(net.minecraft.world.item.enchantment.EnchantmentHelper.class,name,args.toArray());
        }
        if(target instanceof net.minecraft.world.item.crafting.Recipe<?> recipe && name.equals("getId") && args.isEmpty()) {
            if(level==null) throw unsupported("native recipe identity lookup requires server context");
            return NativeRecipeSource.entries(level).stream().filter(entry->entry.value()==recipe).map(NativeRecipeSource.Entry::id).findFirst().orElseThrow(()->unsupported("native recipe is not registered"));
        }
        if (target instanceof net.minecraft.world.item.crafting.Recipe<?> recipe && NativeMinecraftCalls.known(ref)) {
            if (name.equals("getResultItem")) { ItemStack output = NativeRecipeSource.output(recipe, registries).copy(); owned.add(output); return output; }
            if (name.equals("getExperience")) return NativeBackendReflection.call(recipe, name);
        }
        if (name.equals("<init>") && receiver instanceof Allocation allocation) {
            if (allocation.type.equals(ItemStack.class.getName()) && args.getFirst() instanceof ItemLike item) allocation.value = new ItemStack(item, args.size() == 1 ? 1 : integer(args.get(1)));
            else if (allocation.type.equals(ItemStack.class.getName()) && args.getFirst() instanceof net.minecraft.core.Holder<?> holder && holder.value() instanceof net.minecraft.world.item.Item item) allocation.value = new ItemStack(item, args.size() == 1 ? 1 : integer(args.get(1)));
            else if (allocation.type.equals(FluidStack.class.getName()) && args.getFirst() instanceof Fluid fluid) allocation.value = new FluidStack(fluid, integer(args.get(1)));
            else if(allocation.type.equals(java.util.concurrent.atomic.AtomicInteger.class.getName())) allocation.value=new java.util.concurrent.atomic.AtomicInteger(args.isEmpty()?0:integer(args.getFirst()));
            else if(allocation.type.equals(Identifier.class.getName())) allocation.value=Identifier.parse(args.size()==1?(String)args.getFirst():args.getFirst()+":"+args.get(1));
            else if(allocation.type.equals(NativeEnchantmentTargets.mutableType()) && args.size()==1) allocation.value=NativeEnchantmentTargets.mutableCopy(args.getFirst());
            else throw unsupported("allocation " + allocation.type + ref.descriptor());
            owned.add(allocation.value); return null;
        }
        if (items.containsKey(target)) {
            ItemStack[] stacks = items.get(target);
            return switch (name) {
                case "getSlots" -> stacks.length;
                case "getStackInSlot" -> stacks[integer(args.getFirst())];
                case "getSlotLimit" -> ((IItemHandler) target).getSlotLimit(integer(args.getFirst()));
                case "insertItem" -> { outputComponents.add(target); yield insert(target, integer(args.getFirst()), (ItemStack) args.get(1), truth(args.get(2))); }
                case "setStackInSlot" -> { int slot = integer(args.getFirst()); ItemStack copy = ((ItemStack)args.get(1)).copy(); owned.add(copy); stacks[slot] = copy; if(!copy.isEmpty()) outputComponents.add(target); yield null; }
                case "isItemValid" -> ((IItemHandler)target).isItemValid(integer(args.getFirst()), (ItemStack)args.get(1));
                default -> throw unsupported("component inventory call " + owner + "." + name + ref.descriptor());
            };
        }
        if (fluids.containsKey(target)) {
            FluidStack stack = fluids.get(target);
            return switch (name) {
                case "getFluid" -> stack;
                case "isEmpty" -> stack.isEmpty();
                case "getFluidAmount" -> stack.getAmount();
                case "getCapacity" -> call(target, TANK, "getCapacity");
                case "drain", "drainForced" -> {
                    int count = args.getFirst() instanceof Number amount ? amount.intValue() : ((FluidStack) args.getFirst()).getAmount();
                    if (args.getFirst() instanceof FluidStack requested && !stack.isEmpty() && !FluidStack.isSameFluidSameComponents(stack, requested)) yield FluidStack.EMPTY;
                    int amount = Math.min(count, stack.getAmount()); FluidStack result = stack.copyWithAmount(amount); owned.add(result);
                    if (args.getLast().toString().equals("EXECUTE") && !stack.isEmpty()) { own(stack); stack.shrink(amount); }
                    yield result;
                }
                case "fill", "fillForced" -> {
                    outputComponents.add(target);
                    FluidStack offered = (FluidStack) args.getFirst();
                    if (!stack.isEmpty() && !FluidStack.isSameFluidSameComponents(stack, offered)) yield 0;
                    int amount = Math.min(offered.getAmount(), integer(call(target, TANK, "getCapacity")) - stack.getAmount());
                    if (args.getLast().toString().equals("EXECUTE")) { FluidStack updated = offered.copyWithAmount(stack.getAmount() + amount); owned.add(updated); fluids.put(target, updated); }
                    yield amount;
                }
                default -> throw unsupported("component tank call " + owner + "." + name + ref.descriptor());
            };
        }
        if (target instanceof ItemStack stack) {
            return switch (name) {
                case "isEmpty" -> stack.isEmpty(); case "getItem" -> stack.getItem(); case "getCount" -> stack.getCount();
                case "getMaxStackSize" -> stack.getMaxStackSize();
                case "isEnchanted", "has", "hasTag", "getTagElement", "getComponentsPatch", "getFoodProperties", "getDamageValue", "getMaxDamage", "isDamageableItem", "getOrDefault", "supportsEnchantment", "isBookEnchantable", "isValidRepairItem", "getBaseRepairCost" -> NativeBackendReflection.call(stack, name, args.toArray());
                case "set", "remove", "setDamageValue", "removeTagKey", "setRepairCost" -> { own(stack); yield NativeBackendReflection.call(stack,name,args.toArray()); }
                case "getBurnTime" -> com.cappleapple.astralrepository.port.FuelCompat.burnTicks(stack, (net.minecraft.server.level.ServerLevel)((net.minecraft.world.level.block.entity.BlockEntity)tile).getLevel(), ((net.minecraft.world.level.block.entity.BlockEntity)tile).getBlockPos(), (net.minecraft.world.item.crafting.RecipeType<?>)args.getFirst());
                case "hasCraftingRemainingItem" -> !com.cappleapple.astralrepository.port.RecipeCompat.remainder(stack).isEmpty();
                case "getCraftingRemainingItem" -> { ItemStack copy = com.cappleapple.astralrepository.port.RecipeCompat.remainder(stack).copy(); owned.add(copy); yield copy; }
                case "get" -> NativeBackendReflection.call(stack, "get", args.getFirst());
                case "copy", "copyWithCount" -> { ItemStack copy = name.equals("copy") ? stack.copy() : stack.copyWithCount(integer(args.getFirst())); owned.add(copy); yield copy; }
                case "is" -> {
                    if (args.getFirst() instanceof net.minecraft.tags.TagKey<?> tag) { @SuppressWarnings("unchecked") var typed = (net.minecraft.tags.TagKey<net.minecraft.world.item.Item>) tag; yield stack.is(typed); }
                    if (args.getFirst() instanceof net.minecraft.world.item.Item item) yield stack.is(item);
                    throw unsupported("item predicate " + ref.descriptor());
                }
                case "shrink", "grow", "setCount" -> { own(stack); int count = integer(args.getFirst()); if (name.equals("shrink")) stack.shrink(count); else if (name.equals("grow")) stack.grow(count); else stack.setCount(count); yield null; }
                default -> throw unsupported("item call " + owner + "." + name + ref.descriptor());
            };
        }
        if (target instanceof FluidStack stack) {
            return switch (name) {
                case "isEmpty" -> stack.isEmpty(); case "getFluid" -> stack.getFluid(); case "getAmount" -> stack.getAmount();
                case "copy", "copyWithAmount" -> { FluidStack copy = name.equals("copy") ? stack.copy() : stack.copyWithAmount(integer(args.getFirst())); owned.add(copy); yield copy; }
                default -> throw unsupported("fluid call " + owner + "." + name + ref.descriptor());
            };
        }
        if (target instanceof Fluid fluid && name.equals("isSame")) return fluid.isSame((Fluid) args.getFirst());
        if(target instanceof Fluid fluid && name.equals("is") && NativeMinecraftCalls.known(ref)) return NativeBackendReflection.call(fluid,name,args.toArray());
        if(target instanceof java.util.concurrent.atomic.AtomicInteger atomic) {
            if(!owned.contains(atomic)) throw unsupported("access to a non-shadow atomic counter");
            return switch(name) {
                case "get" -> atomic.get(); case "getAndIncrement" -> atomic.getAndIncrement(); case "incrementAndGet" -> atomic.incrementAndGet();
                case "addAndGet" -> atomic.addAndGet(integer(args.getFirst())); case "getAndAdd" -> atomic.getAndAdd(integer(args.getFirst()));
                case "set" -> { atomic.set(integer(args.getFirst())); yield null; }
                default -> throw unsupported("shadow atomic counter call " + name);
            };
        }
        if(target!=null && target.getClass().getName().equals(NativeEnchantmentTargets.mutableType())) {
            if(!owned.contains(target)) throw unsupported("access to a non-shadow enchantment builder");
            if(name.equals("removeIf") && args.getFirst() instanceof Closure predicate) {
                for(Object key:new ArrayList<>(NativeBackendReflection.list(NativeBackendReflection.call(target,"keySet"))))
                    if(truth(closure(predicate,List.of(key),depth))) NativeBackendReflection.call(target,"set",key,0);
                return null;
            }
            if(Set.of("set","upgrade","getLevel","toImmutable","keySet").contains(name)) return NativeBackendReflection.call(target,name,args.toArray());
            throw unsupported("unclassified shadow enchantment builder operation: " + name);
        }
        if(owner.equals(net.minecraft.world.item.DyeColor.class.getName()) && name.equals("byId") && NativeMinecraftCalls.known(ref)) return NativeBackendReflection.call(net.minecraft.world.item.DyeColor.class,name,args.toArray());
        if (target instanceof net.minecraft.world.item.Item item) {
            if (name.equals("hasCraftingRemainingItem")) return item.getCraftingRemainder() != null;
            if (name.equals("getCraftingRemainingItem")) return item.getCraftingRemainder();
            if (name.equals("getFoodProperties")) return NativeBackendReflection.call(item, name, args.toArray());
            if(name.equals("isValidRepairItem") && NativeMinecraftCalls.known(ref)) return NativeBackendReflection.call(item,name,args.toArray());
        }
        if(target instanceof net.minecraft.core.Holder<?> holder) {
            if(name.equals("equals")) return holder.equals(args.getFirst());
            if(name.equals("is") && NativeMinecraftCalls.known(ref)) return NativeBackendReflection.call(holder,name,args.toArray());
        }
        if(owner.equals(net.minecraft.world.inventory.AnvilMenu.class.getName()) && name.equals("calculateIncreasedRepairCost") && NativeMinecraftCalls.known(ref))
            return NativeBackendReflection.call(net.minecraft.world.inventory.AnvilMenu.class,name,args.toArray());
        if(target instanceof Enum<?> value && name.equals("ordinal")) return value.ordinal();
        if (NativeMinecraftCalls.known(ref) && Set.of("nutrition", "saturation", "keySet", "entrySet", "getLevel", "definition", "weight", "getMaxLevel", "value", "getComponentType", "flightDuration", "explosions", "shape", "colors", "fadeColors", "hasTrail", "hasTwinkle", "byId", "byColor", "getEnchantments", "getRarity", "getWeight", "getMinCost", "isCurse", "canEnchant", "getAnvilCost", "areCompatible", "isCompatibleWith").contains(name)) {
            if (target != null) return NativeBackendReflection.call(target, name, args.toArray());
            try { return NativeBackendReflection.call(Class.forName(owner, false, loader), name, args.toArray()); }
            catch (ClassNotFoundException unavailable) { throw unsupported("native immutable data contract unavailable: " + owner + "." + name); }
        }
        if (Set.of("getAllEffects", "getPotion", "getEffects").contains(name) && NativeMinecraftCalls.known(ref)) {
            if (target != null) return NativeBackendReflection.call(target, name, args.toArray());
            try { return NativeBackendReflection.call(Class.forName(owner, false, loader), name, args.toArray()); }
            catch (ClassNotFoundException unavailable) { throw unsupported("native potion data contract unavailable"); }
        }
        if (owner.startsWith("net.minecraft.nbt.") && NativeMinecraftCalls.known(ref)
                && Set.of("getCompound", "getList", "getInt", "getByte", "getBoolean", "getIntArray").contains(name))
            return NativeBackendReflection.call(target, name, args.toArray());
        if (target instanceof net.minecraft.world.effect.MobEffectInstance effect) return switch (name) { case "getDuration" -> effect.getDuration(); case "getAmplifier" -> effect.getAmplifier(); default -> throw unsupported("effect query " + name); };
        if (target instanceof Iterable<?> iterable && name.equals("iterator")) return iterable.iterator();
        if (target instanceof Collection<?> values && name.equals("stream")) return new ShadowStream(new ArrayList<>(values));
        if(target instanceof Collection<?> values && name.equals("size")) return values.size();
        if(target instanceof Collection<?> values && name.equals("isEmpty")) return values.isEmpty();
        if (target instanceof Collection<?> values && name.equals("size")) return values.size();
        if (target instanceof Collection<?> values && name.equals("isEmpty")) return values.isEmpty();
        if (target instanceof Map<?,?> values) return switch (name) {
            case "size" -> values.size(); case "isEmpty" -> values.isEmpty(); case "get" -> values.get(args.getFirst());
            case "getOrDefault" -> {
                if(args.size()!=2) throw unsupported("native map default lookup arity");
                yield values.containsKey(args.getFirst())?values.get(args.getFirst()):args.get(1);
            }
            case "containsKey" -> values.containsKey(args.getFirst()); case "keySet" -> values.keySet(); case "entrySet" -> values.entrySet();
            case "put" -> {
                if(!owned.contains(values)) throw unsupported("write to a non-shadow native map");
                if(args.size()!=2 || !values.containsKey(args.getFirst()) && values.size()>=1024) throw unsupported("shadow native map exceeds1024 entries");
                @SuppressWarnings("unchecked") Map<Object,Object> mutable=(Map<Object,Object>)values;
                yield mutable.put(args.getFirst(),args.get(1));
            }
            default -> throw unsupported("map mutation/query " + name);
        };
        if (target instanceof ShadowStream stream) {
            if (name.equals("toList")) return List.copyOf(stream.values());
            if (name.equals("findFirst")) return stream.values().stream().findFirst();
            if (name.equals("filter") && args.getFirst() instanceof Closure predicate) {
                List<Object> result = new ArrayList<>(); for (Object value : stream.values()) if (truth(closure(predicate, List.of(value), depth))) result.add(value); return new ShadowStream(result);
            }
            if (name.equals("map") && args.getFirst() instanceof Closure mapper) {
                List<Object> result = new ArrayList<>(); for (Object value : stream.values()) result.add(closure(mapper, List.of(value), depth)); return new ShadowStream(result);
            }
            if (name.equals("allMatch") && args.getFirst() instanceof Closure predicate) { for (Object value : stream.values()) if (!truth(closure(predicate, List.of(value), depth))) return false; return true; }
            if (name.equals("collect") && args.getFirst() instanceof ShadowCollector collector) {
                if (collector.key() == null) return new ArrayList<>(stream.values());
                Map<Object,Object> result = new LinkedHashMap<>(); for (Object value : stream.values()) {
                    Object key = closure(collector.key(), List.of(value), depth), mapped = closure(collector.value(), List.of(value), depth);
                    if (result.putIfAbsent(key, mapped) != null) throw unsupported("duplicate native collector key");
                } owned.add(result); return result;
            }
            throw unsupported("stream mutation/query " + name);
        }
        if (target instanceof Iterator<?> iterator) return switch (name) { case "hasNext" -> iterator.hasNext(); case "next" -> iterator.next(); default -> throw unsupported("iterator mutation " + name); };
        if (target instanceof net.minecraft.core.Registry<?> registry && name.equals("getRandomElementOf") && args.getLast() == RandomToken.INSTANCE) {
            @SuppressWarnings({"rawtypes", "unchecked"}) Optional<? extends net.minecraft.core.HolderSet<?>> entries = ((net.minecraft.core.Registry)registry).get((net.minecraft.tags.TagKey)args.getFirst());
            List<Object> values = entries.isPresent() ? entries.get().stream().map(value -> (Object)value).toList() : List.of();
            return choose(((net.minecraft.tags.TagKey<?>)args.getFirst()).toString(), values);
        }
        if (target instanceof net.minecraft.core.Registry<?> registry && name.equals("getTag")) {
            @SuppressWarnings({"rawtypes", "unchecked"}) Optional<? extends net.minecraft.core.HolderSet<?>> entries = ((net.minecraft.core.Registry)registry).get((net.minecraft.tags.TagKey)args.getFirst());
            entries.ifPresent(values -> tagDomains.put(values, args.getFirst().toString())); return entries;
        }
        if (target instanceof net.minecraft.core.HolderSet<?> values && name.equals("getRandomElement") && args.getLast() == RandomToken.INSTANCE) {
            List<Object> entries = values.stream().map(value -> (Object)value).toList();
            String domain = tagDomains.getOrDefault(values, entries.stream().map(Object::toString).sorted().toList().toString());
            return choose(domain, entries);
        }
        if (owner.equals("net.minecraftforge.registries.IForgeRegistry") && name.equals("tags") && args.isEmpty())
            return NativeBackendReflection.call(target, name);
        if (owner.equals("net.minecraftforge.registries.tags.ITagManager") && name.equals("getTag") && args.size() == 1 && args.getFirst() instanceof net.minecraft.tags.TagKey<?>) {
            Object values = NativeBackendReflection.call(target, name, args.getFirst());
            tagDomains.put(values, args.getFirst().toString()); return values;
        }
        if (owner.equals("net.minecraftforge.registries.tags.ITag") && name.equals("getRandomElement") && args.size() == 1 && args.getFirst() == RandomToken.INSTANCE && target instanceof Iterable<?> values) {
            List<Object> entries = new ArrayList<>(); for (Object value : values) entries.add(value);
            return choose(tagDomains.getOrDefault(values, entries.stream().map(Object::toString).sorted().toList().toString()), entries);
        }
        if (target instanceof Optional<?> optional) {
            if (name.equals("isEmpty")) return optional.isEmpty();
            if (name.equals("isPresent")) return optional.isPresent();
            if (name.equals("orElse")) return optional.orElse(null) == null ? args.getFirst() : optional.get();
            if (name.equals("ifPresent") && args.getFirst() instanceof Closure closure) { if (optional.isPresent()) closure(closure, List.of(optional.get()), depth); return null; }
            if ((name.equals("map") || name.equals("flatMap")) && args.getFirst() instanceof Closure closure) {
                if (optional.isEmpty()) return Optional.empty();
                Object result = closure(closure, List.of(optional.get()), depth);
                if (name.equals("map")) return Optional.ofNullable(result);
                if (result instanceof Optional<?>) return result;
                throw unsupported("native Optional.flatMap returned a non-optional value");
            }
        }
        if (owner.equals("net.neoforged.neoforge.items.ItemHandlerHelper") && name.equals("insertItem")) {
            outputComponents.add(args.getFirst());
            ItemStack remainder = ((ItemStack) args.get(1)).copy(); owned.add(remainder);
            ItemStack[] stacks = items.get(args.getFirst()); if (stacks == null) throw unsupported("foreign native output inventory");
            for (int slot = 0; slot < stacks.length && !remainder.isEmpty(); slot++) remainder = insert(args.getFirst(), slot, remainder, truth(args.getLast()));
            return remainder;
        }
        if (owner.equals("com.hrznstudio.titanium.util.ItemHandlerUtil") && name.equals("getFirstItem")) {
            ItemStack[] stacks = items.get(args.getFirst()); if (stacks == null) throw unsupported("foreign native item query");
            return Arrays.stream(stacks).filter(stack -> !stack.isEmpty()).findFirst().orElseGet(this::emptyItem);
        }
        if (owner.equals("com.hrznstudio.titanium.util.InventoryUtil") && name.equals("getStacks")) {
            ItemStack[] stacks = items.get(args.getFirst()); if (stacks == null) throw unsupported("foreign native item query");
            return Arrays.stream(stacks).filter(stack -> !stack.isEmpty()).toList();
        }
        if (target instanceof List<?> values) return switch (name) { case "get" -> values.get(integer(args.getFirst())); case "size" -> values.size(); case "isEmpty" -> values.isEmpty(); default -> throw unsupported("collection mutation/query " + owner + "." + name); };
        if (target instanceof Map.Entry<?,?> tuple) return switch (name) { case "getLeft", "getKey" -> tuple.getKey(); case "getRight", "getValue" -> tuple.getValue(); case "getIntValue" -> ((Number)tuple.getValue()).intValue(); default -> throw unsupported("tuple mutation " + name); };
        if (owner.equals("org.apache.commons.lang3.tuple.Pair") && name.equals("of")) return new AbstractMap.SimpleImmutableEntry<>(args.getFirst(), args.get(1));
        if (owner.equals("java.util.Collections") && name.equals("singletonList")) return List.of(args.getFirst());
        if (owner.equals("java.util.Collections") && name.equals("singletonMap") && args.size() == 2) return Map.of(args.getFirst(), args.get(1));
        if (owner.equals("java.util.stream.Collectors") && name.equals("toList") && args.isEmpty()) return new ShadowCollector(null, null);
        if (owner.equals("java.util.stream.Collectors") && name.equals("toMap") && args.size() == 2 && args.getFirst() instanceof Closure key && args.get(1) instanceof Closure value) return new ShadowCollector(key, value);
        if (owner.equals("java.util.Arrays") && name.equals("asList") && args.getFirst() instanceof ShadowArray array) return Arrays.asList(array.values);
        if (owner.startsWith("[") && name.equals("clone") && args.isEmpty() && target != null && target.getClass().isArray()) {
            ShadowArray copy = new ShadowArray(Array.getLength(target));
            for (int index = 0; index < copy.values.length; index++) copy.values[index] = Array.get(target, index);
            return copy;
        }
        if (target instanceof Closure closure && name.equals("test")) return closure(closure, args, depth);
        if (target instanceof java.util.function.Supplier<?> supplier && (owner.startsWith("net.neoforged.neoforge.registries.") || owner.startsWith("net.minecraftforge.registries."))) return supplier.get();
        if (owner.equals("com.hrznstudio.titanium.fluid.TitaniumFluidInstance") && name.equals("getSourceFluid")) return call(target,owner,name);
        if (target instanceof Number number) return switch (name) { case "intValue" -> number.intValue(); case "longValue" -> number.longValue(); default -> throw unsupported("numeric call " + name); };
        if (owner.equals("java.lang.Math")) return switch (name) { case "min" -> Math.min(number(args.getFirst()), number(args.get(1))); case "max" -> Math.max(number(args.getFirst()), number(args.get(1))); case "pow" -> Math.pow(((Number)args.getFirst()).doubleValue(), ((Number)args.get(1)).doubleValue()); default -> throw unsupported("math call " + name); };
        if (name.equals("equals") && (target instanceof net.minecraft.world.item.Item || target instanceof Identifier || target instanceof String || target instanceof Number)) return target.equals(args.getFirst());
        if(target instanceof Identifier location && name.equals("toString")) return location.toString();
        if (owner.equals("java.lang.Integer") && name.equals("valueOf")) return integer(args.getFirst());
        if ((owner.equals("net.minecraftforge.common.ForgeHooks") || owner.equals("net.neoforged.neoforge.common.CommonHooks")) && name.equals("getBurnTime")
                && args.size() == 2 && args.getFirst() instanceof ItemStack stack && (args.get(1) == null || args.get(1) instanceof net.minecraft.world.item.crafting.RecipeType<?>))
            return pure(ref,args);
        if (owner.equals("com.hrznstudio.titanium.component.bundle.LockableInventoryBundle") && name.equals("getInventory")) return call(target, owner, name);
        // These server-only native helpers construct copies or read item/tag preference data.
        if (owner.equals("com.buuz135.industrial.utils.ItemStackUtils") && name.equals("getOreTag")
                || owner.equals("com.buuz135.industrial.fluid.OreTitaniumFluidType") && (name.equals("getFluidTag") || name.equals("getFluidWithTag"))) {
            Object value = pure(ref, args); if (value instanceof ItemStack || value instanceof FluidStack) owned.add(value); return value;
        }
        if (owner.equals("com.buuz135.industrial.fluid.OreTitaniumFluidType") && name.equals("getOutputDust")) {
            if(roleProbe && !args.isEmpty() && MekanismResourceSupport.amount(args.getFirst())==0) return emptyItem();
            // The native helper caches TagKeys in a mutable static map. Read the same native
            // preference contract without modifying that cache during catalog discovery.
            String tag = (String) pure(new NativeBytecode.Reference(184, owner, "getFluidTag", "(L" + FluidStack.class.getName().replace('.', '/') + ";)Ljava/lang/String;"), args);
            var key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(nativeReplacement(owner, name, tag)));
            Object value = pure(new NativeBytecode.Reference(184, "com.hrznstudio.titanium.util.TagUtil", "getItemWithPreference", "(Lnet/minecraft/tags/TagKey;)Lnet/minecraft/world/item/ItemStack;"), List.of(key));
            ItemStack copy = ((ItemStack)value).copy(); owned.add(copy); return copy;
        }
        if (owner.equals(Identifier.class.getName()) && name.equals("parse")) return Identifier.parse((String) args.getFirst());
        if ((owner.equals(entryType.getName()) || owner.startsWith("com.buuz135.industrial.")) && !name.equals("<init>")) return execute(owner, name, ref.descriptor(), target, args, depth);
        throw unsupported("native call " + owner + "." + name + ref.descriptor());
    }

    private String nativeReplacement(String owner, String name, String value) {
        try {
            for (var method : NativeBytecode.inspect(Class.forName(owner, false, loader)).methods()) if (method.name().equals(name)) {
                var code = method.instructions();
                for (int index = 2; index < code.size(); index++) {
                    var call = code.get(index).reference();
                    if (call != null && call.owner().equals("java.lang.String") && call.name().equals("replace")
                            && code.get(index - 2).constant() instanceof String from && code.get(index - 1).constant() instanceof String to)
                        return value.replace(from, to);
                }
            }
        } catch (ClassNotFoundException absent) { throw unsupported("native data helper unavailable: " + owner); }
        throw unsupported("native data helper has no direct immutable string replacement: " + owner + "." + name);
    }

    private ItemStack insert(Object owner, int slot, ItemStack offered, boolean simulate) {
        ItemStack[] stacks = items.get(owner); ItemStack before = stacks[slot];
        if (!before.isEmpty() && !ItemStack.isSameItemSameComponents(before, offered)) return offered.copy();
        int capacity = Math.min(((IItemHandler)owner).getSlotLimit(slot), offered.getMaxStackSize());
        int count = Math.max(0, Math.min(offered.getCount(), capacity - before.getCount()));
        if (!simulate && count > 0) { ItemStack updated = offered.copyWithCount(before.getCount() + count); owned.add(updated); stacks[slot] = updated; }
        ItemStack rest = offered.copyWithCount(offered.getCount() - count); owned.add(rest); return rest;
    }
    private ItemStack emptyItem() { ItemStack empty = new ItemStack(net.minecraft.world.item.Items.AIR, 0); owned.add(empty); return empty; }
    private Object pure(NativeBytecode.Reference ref, List<Object> args) {
        try {
            Class<?> owner = Class.forName(ref.owner(), false, loader);
            return NativeBackendReflection.call(owner,NativeMinecraftCalls.name(ref),args.toArray());
        } catch (ReflectiveOperationException | LinkageError unavailable) { throw unsupported("pure native data contract unavailable: " + ref.owner() + "." + ref.name()); }
    }
    private Object closure(Closure closure, List<Object> supplied, int depth) {
        try {
            var implementation = closure.implementation();
            var method = NativeBytecode.hierarchy(Class.forName(implementation.owner(), false, loader)).stream().flatMap(shape -> shape.methods().stream())
                    .filter(candidate -> candidate.name().equals(implementation.name()) && candidate.descriptor().equals(implementation.descriptor())).findFirst()
                    .orElseThrow(() -> unsupported("native lambda declaration unavailable: " + implementation.owner() + "." + implementation.name()));
            List<Object> args = new ArrayList<>(closure.captured()); args.addAll(supplied); Object target = null;
            if ((method.access() & 8) == 0) { if (args.isEmpty()) throw unsupported("unbound native lambda receiver"); target = args.removeFirst(); }
            return invoke(implementation, target, args, depth + 1);
        } catch (ClassNotFoundException unavailable) { throw unsupported("native lambda class unavailable"); }
    }
    private Optional<Object> choose(String domain, List<Object> values) {
        if (values.size() > 256) throw unsupported("native tag-choice alternatives exceed256");
        String id = domain + "#" + choiceOccurrences.merge(domain, 1, Integer::sum); choices.put(id, values);
        if (values.isEmpty()) return Optional.empty();
        int selected = forcedChoices.getOrDefault(id, 0); if (selected < 0 || selected >= values.size()) throw unsupported("invalid native symbolic tag choice");
        return Optional.of(values.get(selected));
    }
    private static List<String> arguments(String descriptor) {
        List<String> result = new ArrayList<>();
        for (int index = 1; descriptor.charAt(index) != ')';) {
            int start = index; while (descriptor.charAt(index) == '[') index++;
            if (descriptor.charAt(index++) == 'L') index = descriptor.indexOf(';', index) + 1;
            result.add(descriptor.substring(start, index));
        }
        return result;
    }
    private static int jump(Map<Integer, Integer> offsets, int offset) { Integer index = offsets.get(offset); if (index == null) throw unsupported("invalid native branch target"); return index; }
    private static Object unwrap(Object value) { return value instanceof Allocation allocation ? allocation.value : value; }
    private static Object pop(List<Object> stack) { if (stack.isEmpty()) throw unsupported("invalid native operand stack"); return stack.removeLast(); }
    private static long number(Object value) { if (value instanceof Boolean flag) return flag ? 1 : 0; if (value instanceof Number number) return number.longValue(); throw unsupported("non-numeric native operand"); }
    private static int integer(Object value) { return Math.toIntExact(number(value)); }
    private static boolean truth(Object value) { return number(value) != 0; }
    private void own(Object value) { if (!owned.contains(value) || value == ItemStack.EMPTY || value == FluidStack.EMPTY) throw unsupported("attempted mutation outside shadow resource storage"); }
    private static Object call(Object target, String owner, String method) { return IndustrialForegoingBackend.call(target, owner, method); }
    private static UnsupportedOperationException unsupported(String reason) { return new UnsupportedOperationException("Titanium finite evaluator: " + reason); }
}
