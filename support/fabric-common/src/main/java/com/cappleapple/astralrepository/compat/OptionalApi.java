package com.cappleapple.astralrepository.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reflection stays within compatibility modules so common classes never link optional types. */
final class OptionalApi {
    private static final Map<String,Class<?>> TYPES = new ConcurrentHashMap<>();
    private static final Map<String,Method> METHODS = new ConcurrentHashMap<>();
    private OptionalApi() { }
    static Class<?> type(String name) {
        return TYPES.computeIfAbsent(name, key -> {
            try { return Class.forName(key); }
            catch (ClassNotFoundException failure) { throw new IllegalStateException("Optional API unavailable: " + key, failure); }
        });
    }
    static Object field(String name, String field) {
        try { return type(name).getField(field).get(null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(name + "." + field, failure); }
    }
    static Object call(Object target, String declaringType, String name, Class<?>[] types, Object... arguments) {
        String signature = declaringType + "#" + name + Arrays.toString(types);
        Method method = METHODS.computeIfAbsent(signature, key -> {
            try { return type(declaringType).getMethod(name, types); }
            catch (NoSuchMethodException failure) { throw new IllegalStateException("Optional API changed: " + key, failure); }
        });
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(signature, cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(signature, failure); }
    }
    static Object call(Object target, String declaringType, String name) {
        return call(target, declaringType, name, new Class<?>[0]);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object value(String enumType, String name) { return Enum.valueOf((Class)type(enumType), name); }
}