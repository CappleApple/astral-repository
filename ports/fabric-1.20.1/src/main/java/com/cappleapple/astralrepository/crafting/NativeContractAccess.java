package com.cappleapple.astralrepository.crafting;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/** Exact optional-contract access; unrelated client-only fields and methods are never resolved. */
final class NativeContractAccess {
    private NativeContractAccess() {}
    static Object call(Object owner, String name, Object... arguments) {
        return NativeBackendReflection.optional(owner,name,arguments);
    }
    static Object value(Object owner,String... names){for(String name:names){Object result=call(owner,name);if(result!=null)return result;}return null;}
    static Object unwrap(Object value){
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(value!=null&&seen.add(value)){
            if(value instanceof Optional<?> optional)value=optional.orElse(null);
            else if(value instanceof java.util.function.Supplier<?> supplier)value=supplier.get();
            else break;
        }
        return value;
    }
    static Object field(Object owner, String name) {
        return NativeBackendReflection.field(owner,name);
    }
    static Object staticField(Class<?> type, String name) {
        return NativeBackendReflection.field(type,name);
    }
    static Object staticCall(Class<?> type,String name,Object... arguments) {
        return NativeBackendReflection.optional(type,name,arguments);
    }
    static Object construct(Class<?> type,Object... arguments){
        for(var method:NativeBytecode.inspect(type).methods())if(method.name().equals("<init>")){
            try{
                MethodType signature=MethodType.fromMethodDescriptorString(method.descriptor(),type.getClassLoader());
                if(signature.parameterCount()!=arguments.length)continue;
                boolean accepts=true;for(int i=0;i<arguments.length;i++)if(arguments[i]!=null&&!boxed(signature.parameterType(i)).isInstance(arguments[i]))accepts=false;
                if(accepts)return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).findConstructor(type,signature).invokeWithArguments(arguments);
            }catch(Throwable failure){throw new IllegalStateException(type.getName()+" native input constructor",failure);}
        }
        throw new IllegalArgumentException("No matching native input constructor: "+type.getName());
    }
    static List<Object> fields(Object owner, java.util.function.Predicate<NativeBytecode.Field> filter) {
        List<Object> values = new ArrayList<>();
        for (Class<?> type : hierarchy(owner.getClass())) for (var field : NativeBytecode.inspect(type).fields())
            if ((field.access() & 8) == 0 && filter.test(field)) { Object value = field(owner, field.name()); if (value != null) values.add(value); }
        return List.copyOf(values);
    }
    static List<Class<?>> hierarchy(Class<?> type) {
        LinkedHashSet<Class<?>> result = new LinkedHashSet<>(); collect(type, result); return List.copyOf(result);
    }
    private static void collect(Class<?> type, Set<Class<?>> result) {
        if (type == null || type == Object.class || !result.add(type)) return;
        collect(type.getSuperclass(), result); for (Class<?> contract : type.getInterfaces()) collect(contract, result);
    }
    static boolean contract(Object value, String name) { return value != null && hierarchy(value.getClass()).stream().anyMatch(type -> type.getName().equals(name)); }
    static Class<?> optional(String name) { try { return Class.forName(name, false, NativeContractAccess.class.getClassLoader()); } catch (ClassNotFoundException | LinkageError absent) { return null; } }
    static List<?> list(Object value) { return NativeBackendReflection.list(value); }
    static long number(Object value, long fallback) { return value instanceof Number number ? number.longValue() : fallback; }
    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class; if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class; if (type == float.class) return Float.class;
        if (type == double.class) return Double.class; if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class; if (type == char.class) return Character.class;
        return Void.class;
    }
}
