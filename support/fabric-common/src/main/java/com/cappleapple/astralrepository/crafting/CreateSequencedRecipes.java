package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.lang.invoke.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Declarative native assembly stages. Discovery never enforces results or rolls native outputs. */
public final class CreateSequencedRecipes {
    private static final String ASSEMBLY = "com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe";
    private static final int MAX_SEQUENCE = 128, MAX_STAGES = 8192;
    public record Step(Identifier assemblyId, int index, int total, NativeRecipeSource.Entry entry,
                       CraftRecipe.Ingredient<ResourceKey> primary, Map<ResourceKey, Long> guaranteed,
                       Map<ResourceKey, Long> possible, boolean chanceOutput) {
        public Step { guaranteed = Map.copyOf(guaranteed); possible = Map.copyOf(possible); }
    }
    public record Expansion(List<Step> steps, Map<String, String> diagnostics) {
        public Expansion { steps = List.copyOf(steps); diagnostics = Map.copyOf(diagnostics); }
    }
    private record Pool(Map<ResourceKey, Long> guaranteed, Map<ResourceKey, Long> possible) {}
    private record Metadata(Class<?> owner, Map<String, VarHandle> fields, MethodHandle step, MethodHandle applies,
                            boolean appliesNeedsId, MethodHandle progressConstructor, Object component, boolean nbt) {}
    private static final ClassValue<Metadata> CONTRACTS = new ClassValue<>() {
        @Override protected Metadata computeValue(Class<?> type) { return inspect(type); }
    };
    private CreateSequencedRecipes() {}

    public static List<Step> expand(CraftingService.NetworkAccess access) { return discover(access).steps(); }
    public static Expansion discover(CraftingService.NetworkAccess access) {
        Map<ResourceKey, Long> snapshot = access.resourceSnapshot(); ItemStock samples = itemStock(snapshot);
        List<Step> result = new ArrayList<>(); Map<String, String> reasons = new LinkedHashMap<>();
        for (var entry : NativeRecipeSource.entries(access.level())) if (contract(entry.value(), ASSEMBLY)) {
            try {
                List<Step> stages = expand(entry, samples, snapshot);
                if (stages.size() > MAX_STAGES - result.size()) throw new IllegalArgumentException("Native assembly catalogue exceeds8192 exact stages");
                result.addAll(stages);
            } catch (RuntimeException | LinkageError unavailable) {
                reasons.put(entry.id().toString(), unavailable.getMessage() == null ? unavailable.getClass().getName() : unavailable.getMessage());
            }
        }
        return new Expansion(result, reasons);
    }
    private static List<Step> expand(NativeRecipeSource.Entry assembly, ItemStock samples, Map<ResourceKey, Long> stock) {
        Object raw = assembly.value(); Metadata metadata = CONTRACTS.get(raw.getClass());
        List<?> sequence = list(metadata.fields().get("sequence").get(raw));
        int loops = ((Number)metadata.fields().get("loops").get(raw)).intValue();
        if (sequence.isEmpty() || sequence.size() > MAX_SEQUENCE || loops < 1 || loops > MAX_STAGES / sequence.size())
            throw new IllegalArgumentException("Native assembly needs1..128 sequence entries and at most8192 expanded stages");
        int total = sequence.size() * loops;
        if (!(metadata.fields().get("ingredient").get(raw) instanceof Ingredient first)) throw new IllegalArgumentException("Unknown native assembly primary ingredient");
        Set<ResourceKey> alternatives = new LinkedHashSet<>();
        CraftRecipe.Ingredient<ResourceKey> initial = null;
        try { initial = ingredient(first, 1, samples); }
        catch (IllegalArgumentException empty) { if (!"Native ingredient has no registered alternatives".equals(empty.getMessage())) throw empty; }
        if (initial != null) for (ResourceKey key : initial.alternatives()) if (key instanceof ItemKey item
                && applies(metadata, raw, assembly.id(), item.sample()) && stage(metadata, raw, item.sample()) == 0) alternatives.add(key);
        // Native progress-bearing starts can use a different transitional item than the raw ingredient.
        for (ResourceKey key : stock.keySet()) if (key instanceof ItemKey item
                && applies(metadata, raw, assembly.id(), item.sample()) && stage(metadata, raw, item.sample()) == 0) alternatives.add(key);
        if (alternatives.isEmpty()) throw new IllegalArgumentException("Native assembly has no exact valid starting item");
        CraftRecipe.Ingredient<ResourceKey> primary = new CraftRecipe.Ingredient<>(List.copyOf(alternatives), 1, initial == null ? "" : initial.tag());
        Pool finalPool = pool(list(metadata.fields().get("resultPool").get(raw)));
        ItemStack transition = item(call(raw, "getTransitionalItem"));
        if (transition.isEmpty()) throw new IllegalArgumentException("Native assembly has no transitional item");
        List<Step> result = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            Object nativeStep = call(sequence.get(index % sequence.size()), "getRecipe");
            if (!(nativeStep instanceof Recipe<?> recipe)) throw new IllegalArgumentException("Unknown native assembly processing-recipe contract");
            Map<ResourceKey, Long> guaranteed, possible;
            if (index + 1 == total) { guaranteed = finalPool.guaranteed(); possible = finalPool.possible(); }
            else {
                ItemStack advanced = progress(metadata, transition, assembly.id(), index + 1, total);
                if (!applies(metadata, raw, assembly.id(), advanced) || stage(metadata, raw, advanced) != index + 1)
                    throw new IllegalArgumentException("Native assembly rejected its declarative progress metadata");
                guaranteed = possible = Map.of(new ItemKey(advanced), 1L);
            }
            result.add(new Step(assembly.id(), index, total, new NativeRecipeSource.Entry(assembly.id(), recipe), primary,
                    guaranteed, possible, !guaranteed.equals(possible)));
            if (index + 1 < total) primary = CraftRecipe.Ingredient.of(new ItemKey(progress(metadata, transition, assembly.id(), index + 1, total)));
        }
        return List.copyOf(result);
    }
    private static Pool pool(List<?> declared) {
        Map<ResourceKey, Long> possible = new LinkedHashMap<>(); ItemKey only = null; long minimum = Long.MAX_VALUE;
        boolean varying = false, empty = false; float totalWeight = 0;
        for (Object result : declared) {
            Object weightValue = call(result, "getChance");
            if (!(weightValue instanceof Number weightNumber)) throw new IllegalArgumentException("Unknown native assembly result weight");
            float weight = weightNumber.floatValue();
            if (!Float.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid native assembly result weight");
            if (weight == 0) continue;
            totalWeight += weight;
            ItemStack stack = item(call(result, "getStack"));
            if (stack.isEmpty()) { empty = true; continue; }
            ItemKey key = new ItemKey(stack); possible.merge(key, (long)stack.getCount(), Math::max);
            if (only == null) only = key; else if (!only.equals(key)) varying = true;
            minimum = Math.min(minimum, stack.getCount());
        }
        if (!Float.isFinite(totalWeight) || totalWeight <= 0 || possible.isEmpty()) throw new IllegalArgumentException("Native assembly has no finite positive output pool");
        Map<ResourceKey, Long> guaranteed = !empty && !varying && only != null ? Map.of(only, minimum) : Map.of();
        return new Pool(guaranteed, Map.copyOf(possible));
    }
    private static int stage(Metadata metadata, Object recipe, ItemStack input) { return ((Number)invoke(metadata.step(), recipe, input)).intValue(); }
    private static boolean applies(Metadata metadata, Object recipe, Identifier id, ItemStack input) {
        return Boolean.TRUE.equals(metadata.appliesNeedsId() ? invoke(metadata.applies(), recipe, id, input) : invoke(metadata.applies(), recipe, input));
    }
    private static ItemStack progress(Metadata metadata, ItemStack transition, Identifier id, int step, int total) {
        if (step < 1 || step >= total) throw new IllegalArgumentException("Only nonfinal assembly progress can be previewed");
        if (metadata.nbt()) return legacyProgress(transition, id, step, total);
        ItemStack advanced = transition.copyWithCount(1);
        Object value = invoke(metadata.progressConstructor(), id, step, (float)step / total);
        call(advanced, "set", metadata.component(), value);
        return advanced;
    }
    private static ItemStack legacyProgress(ItemStack transition, Identifier id, int step, int total) {
        throw new IllegalArgumentException("Native NBT assembly progress is unavailable on this edition");
    }
    private static Metadata inspect(Class<?> type) {
        Class<?> owner = type;
        while (owner != null && !owner.getName().equals(ASSEMBLY)) owner = owner.getSuperclass();
        if (owner == null) throw new IllegalArgumentException("Unknown native assembly recipe class");
        try {
            var lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup()); var shape = NativeBytecode.inspect(owner);
            Map<String, VarHandle> fields = new HashMap<>();
            for (var field : shape.fields()) if (Set.of("sequence", "loops", "ingredient", "resultPool").contains(field.name())) {
                Class<?> fieldType = MethodType.fromMethodDescriptorString("()" + field.descriptor(), owner.getClassLoader()).returnType();
                fields.put(field.name(), lookup.findVarHandle(owner, field.name(), fieldType));
            }
            if (fields.size() != 4) throw new IllegalArgumentException("Unknown native assembly data fields");
            MethodHandle getStep = lookup.findSpecial(owner, "getStep", MethodType.methodType(int.class, ItemStack.class), owner);
            MethodHandle applies; boolean withId;
            try { applies = lookup.findSpecial(owner, "appliesTo", MethodType.methodType(boolean.class, Identifier.class, ItemStack.class), owner); withId = true; }
            catch (NoSuchMethodException legacy) { applies = lookup.findSpecial(owner, "appliesTo", MethodType.methodType(boolean.class, ItemStack.class), owner); withId = false; }
            var advance = shape.methods().stream().filter(method -> method.name().equals("advance")).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Native assembly progress metadata contract is missing"));
            MethodHandle constructor = null; Object component = null;
            for (var reference : advance.references()) if (reference.name().equals("<init>")) {
                MethodType signature = MethodType.fromMethodDescriptorString(reference.descriptor(), owner.getClassLoader());
                if (signature.parameterCount() == 3 && signature.parameterType(0) == Identifier.class
                        && signature.parameterType(1) == int.class && signature.parameterType(2) == float.class) {
                    Class<?> progressType = Class.forName(reference.owner(), false, owner.getClassLoader());
                    constructor = MethodHandles.privateLookupIn(progressType, MethodHandles.lookup()).findConstructor(progressType, signature); break;
                }
            }
            if (constructor != null) for (var reference : advance.references()) if (reference.staticRead()) {
                Object value = reference.readStatic(owner.getClassLoader());
                if (value != null && has(new ItemStack(net.minecraft.world.item.Items.PAPER), "set", value, invoke(constructor, Identifier.withDefaultNamespace("air"), 1, 0.5F))) { component = value; break; }
            }
            Set<Object> constants = new HashSet<>(); advance.instructions().forEach(instruction -> { if (instruction.constant() != null) constants.add(instruction.constant()); });
            boolean nbt = constructor == null && constants.containsAll(Set.of("SequencedAssembly", "id", "Step", "Progress"));
            if (!nbt && (constructor == null || component == null)) throw new IllegalArgumentException("Unknown native assembly component/NBT progress encoding");
            return new Metadata(owner, Map.copyOf(fields), getStep, applies, withId, constructor, component, nbt);
        } catch (ReflectiveOperationException unavailable) { throw new IllegalArgumentException("Native assembly metadata API unavailable", unavailable); }
    }
    private static Object invoke(MethodHandle handle, Object... arguments) {
        try { return handle.asFixedArity().invokeWithArguments(arguments); }
        catch (RuntimeException failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalArgumentException("Native assembly metadata could not be read", failure); }
    }
}
