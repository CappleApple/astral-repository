package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.ResourceKey;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/** Cached optional API calls. No optional class is linked during ordinary startup. */
public final class NativeBackendReflection {
    private NativeBackendReflection() {}
    private record Signature(String name, List<Class<?>> arguments) {}
    private record Call(MethodHandle handle, Class<?>[] parameters, boolean isStatic) {}
    private record Access(VarHandle handle, boolean isStatic) {}
    private static final ClassValue<Map<Signature, Optional<Call>>> METHODS = new ClassValue<>() {
        @Override protected Map<Signature, Optional<Call>> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<Map<String, Optional<Access>>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Optional<Access>> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<Set<String>> CONTRACTS = new ClassValue<>() {
        @Override protected Set<String> computeValue(Class<?> type) {
            Set<String> names = new HashSet<>(); names.add(type.getName());
            if (type.getSuperclass() != null) names.addAll(get(type.getSuperclass()));
            for (Class<?> implemented : type.getInterfaces()) names.addAll(get(implemented));
            return Set.copyOf(names);
        }
    };

    static boolean contract(Object value, String name) { return value != null && contract(value.getClass(), name); }
    static boolean contract(Class<?> type, String name) {
        return type != null && CONTRACTS.get(type).contains(name);
    }
    static Class<?> optionalClass(String name) {
        try { return Class.forName(name, false, NativeBackendReflection.class.getClassLoader()); }
        catch (ClassNotFoundException | LinkageError unavailable) { return null; }
    }
    static boolean has(Object value, String name, Object... arguments) { return value != null && method(value, name, arguments).isPresent(); }
    static Object optional(Object value, String name, Object... arguments) {
        return value == null || !has(value, name, arguments) ? null : call(value, name, arguments);
    }
    public static Object call(Object value, String name, Object... arguments) {
        Call call = method(value, name, arguments).orElseThrow(() -> new IllegalStateException("Missing native API " + type(value).getName() + "." + name));
        List<Object> converted = new ArrayList<>();
        if (!call.isStatic()) converted.add(value);
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index]; Class<?> target = call.parameters()[index];
            if (argument instanceof Number number) {
                if (target == int.class || target == Integer.class) argument = number.intValue();
                else if (target == long.class || target == Long.class) argument = number.longValue();
                else if (target == float.class || target == Float.class) argument = number.floatValue();
                else if (target == double.class || target == Double.class) argument = number.doubleValue();
            }
            converted.add(argument);
        }
        try { return call.handle().asFixedArity().invokeWithArguments(converted); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Native API failed: " + type(value).getName() + "." + name, failure); }
    }
    private static Class<?> type(Object value) { return value instanceof Class<?> type ? type : value.getClass(); }
    private static Optional<Call> method(Object value, String name, Object... arguments) {
        Class<?> type = type(value);
        Signature signature = new Signature(name, Arrays.stream(arguments).map(argument -> argument == null ? Void.class : argument.getClass()).toList());
        return METHODS.get(type).computeIfAbsent(signature, ignored -> find(type, name, arguments, new HashSet<>()));
    }
    private static Optional<Call> find(Class<?> type, String name, Object[] arguments, Set<Class<?>> visited) {
        if (type == null || type == Object.class || !visited.add(type)) return Optional.empty();
        List<NativeBytecode.Method> declared;
        try { declared = NativeBytecode.inspect(type).methods(); }
        catch (RuntimeException hiddenClass) { declared = List.of(); }
        Set<String> runtimeNames = NativeMinecraftCalls.runtimeNames(type, name);
        for (NativeBytecode.Method candidate : declared) {
            if (!runtimeNames.contains(candidate.name())) continue;
            try {
                MethodType method = MethodType.fromMethodDescriptorString(candidate.descriptor(), type.getClassLoader());
                if (method.parameterCount() != arguments.length) continue;
                boolean matches = true;
                for (int index = 0; index < arguments.length; index++) {
                    Class<?> target = method.parameterType(index); Object argument = arguments[index];
                    if (argument == null ? target.isPrimitive() : !(boxed(target).isInstance(argument)
                            || argument instanceof Number && Number.class.isAssignableFrom(boxed(target)))) { matches = false; break; }
                }
                if (!matches) continue;
                boolean isStatic = (candidate.access() & 8) != 0;
                MethodHandle handle;
                try {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
                    handle = isStatic ? lookup.findStatic(type, candidate.name(), method) : lookup.findVirtual(type, candidate.name(), method);
                } catch (IllegalAccessException inaccessibleModule) {
                    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                    handle = isStatic ? lookup.findStatic(type, candidate.name(), method) : lookup.findVirtual(type, candidate.name(), method);
                }
                return Optional.of(new Call(handle, method.parameterArray(), isStatic));
            } catch (ReflectiveOperationException | LinkageError unavailable) { /* Try another declared overload. */ }
        }
        Optional<Call> parent = find(type.getSuperclass(), name, arguments, visited);
        if (parent.isPresent()) return parent;
        for (Class<?> implemented : type.getInterfaces()) {
            Optional<Call> candidate = find(implemented, name, arguments, visited);
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }
    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class; if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class; if (type == float.class) return Float.class;
        if (type == double.class) return Double.class; if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class; return type;
    }
    static Object field(Object value, String name) {
        if (value == null) return null;
        Optional<Access> field = FIELDS.get(type(value)).computeIfAbsent(name, ignored -> {
            for (Class<?> current = type(value); current != null && current != Object.class; current = current.getSuperclass()) {
                for (NativeBytecode.Field candidate : NativeBytecode.inspect(current).fields()) if (candidate.name().equals(name)) {
                    try {
                        Class<?> fieldType = MethodType.fromMethodDescriptorString("()" + candidate.descriptor(), current.getClassLoader()).returnType();
                        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(current, MethodHandles.lookup());
                        boolean isStatic = (candidate.access() & 8) != 0;
                        return Optional.of(new Access(isStatic ? lookup.findStaticVarHandle(current, name, fieldType) : lookup.findVarHandle(current, name, fieldType), isStatic));
                    } catch (ReflectiveOperationException | LinkageError unavailable) { return Optional.empty(); }
                }
            }
            return Optional.empty();
        });
        return field.isEmpty() ? null : field.get().isStatic() ? field.get().handle().get() : field.get().handle().get(value);
    }
    static List<?> list(Object value) {
        if (value == null) return List.of();
        if (value instanceof Optional<?> optional) return optional.map(NativeBackendReflection::list).orElseGet(List::of);
        if (value instanceof Collection<?> values) return List.copyOf(values);
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < java.lang.reflect.Array.getLength(value); index++) values.add(java.lang.reflect.Array.get(value, index));
            return List.copyOf(values);
        }
        return List.of(value);
    }
    static long number(Object value, long fallback) { return value instanceof Number number ? number.longValue() : fallback; }
    static ItemStack item(Object value) {
        if (value instanceof ItemStack stack) return stack.copy();
        Object created = optional(value, "create");
        return created instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }
    record ItemStock(NativeStockSamples samples) {}
    static ItemStock itemStock(Map<ResourceKey, Long> stock) {
        return new ItemStock(new NativeStockSamples(stock));
    }
    static CraftRecipe.Ingredient<ResourceKey> ingredient(Ingredient ingredient, long count, ItemStock stock) {
        Set<ResourceKey> alternatives = new LinkedHashSet<>(stock.samples().matching(ingredient));
        for (ItemStack display : stock.samples().representations(ingredient)) if (!display.isEmpty()) {
            if (ingredient.test(display)) alternatives.add(new ItemKey(display));
        }
        if (alternatives.isEmpty()) throw new IllegalArgumentException("Native ingredient has no registered alternatives");
        return new CraftRecipe.Ingredient<>(List.copyOf(alternatives), count, VanillaProcessingAdapter.ingredientTag(ingredient));
    }
}
