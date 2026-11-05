package com.cappleapple.astralrepository.crafting;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import static com.cappleapple.astralrepository.crafting.MekanismRecipeDiscovery.*;

/** Per-machine operation multipliers. Ingredient quantities multiply the returned budget once. */
public final class MekanismUsageProfile {
    public record Profile(long budget,Map<String,Number> state) {
        public Profile { if(budget<1)throw new IllegalArgumentException("Non-positive usage budget");state=Collections.unmodifiableMap(new LinkedHashMap<>(state)); }
    }
    private record Oracle(Object value,String field){}
    private record Reader(VarHandle field,String name){}
    private record Suppliers(LongSupplier total,IntSupplier ticks){}
    private static final ClassValue<List<Reader>> ORACLES=new ClassValue<>() {
        @Override protected List<Reader> computeValue(Class<?> type){
            List<Reader> result=new ArrayList<>();
            for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())for(String api:List.of(
                    "mekanism.api.recipes.cache.ItemStackConstantChemicalToObjectCachedRecipe$ChemicalUsageMultiplier",
                    "mekanism.api.recipes.cache.ItemStackConstantChemicalToItemStackCachedRecipe$ChemicalUsageMultiplier")){
                Class<?> contract;try{contract=Class.forName(api,false,type.getClassLoader());}catch(ClassNotFoundException|LinkageError absent){continue;}
                for(String name:List.of("gasUsageMultiplier","chemicalUsageMultiplier","injectUsageMultiplier"))try{
                    result.add(new Reader(MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).findVarHandle(owner,name,contract),name));
                }catch(ReflectiveOperationException absent){}
            }
            return List.copyOf(result);
        }
    };
    private static final ClassValue<Optional<MethodHandle>> RANDOM_MODE=new ClassValue<>() {
        @Override protected Optional<MethodHandle> computeValue(Class<?> type){
            for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try{
                return Optional.of(MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).findVirtual(owner,"useStatisticalMechanics",MethodType.methodType(boolean.class)));
            }catch(ReflectiveOperationException absent){}
            return Optional.empty();
        }
    };
    private MekanismUsageProfile(){}

    /** Includes native configuration so catalogues invalidate after configuration changes. */
    public static Map<String,Number> catalogState(Object handler){
        Map<String,Number> state=new LinkedHashMap<>();
        if(handler!=null)state.putAll(MekanismMachineAccess.usageState(handler));
        state.put("randomizedConsumption",randomizedConsumption());
        return Collections.unmodifiableMap(state);
    }
    public static Profile profile(Object recipe,Object handler){
        if(recipe==null)return new Profile(1,catalogState(handler));
        boolean perTick=hasMethod(recipe,"perTickUsage")?Boolean.TRUE.equals(invoke(recipe,"perTickUsage")):
                (handler!=null&&implementsContract(handler.getClass(),"mekanism.common.recipe.lookup.IRecipeLookupHandler$ConstantUsageRecipeLookupHandler"))
                ||implementsContract(recipe.getClass(),"mekanism.api.recipes.ChemicalDissolutionRecipe");
        if(!perTick)return new Profile(1,Map.of("perTick",0));
        if(handler==null)throw new IllegalArgumentException("A per-tick recipe requires a native machine profile");
        Map<String,Number> state=new LinkedHashMap<>(catalogState(handler));state.put("perTick",1);
        long machineTicks=state.getOrDefault("ticks",0).longValue();
        long recipeTicks=hasMethod(recipe,"getDuration")?((Number)invoke(recipe,"getDuration")).longValue():0;
        long ticks=recipeTicks>0&&!state.containsKey("baseTotalUsage")?recipeTicks:machineTicks;
        if(ticks<1)throw new IllegalStateException("Native machine has no positive operation duration");
        state.put("ticks",ticks);state.put("effectiveTicks",ticks);if(recipeTicks>0)state.put("recipeTicks",recipeTicks);
        Oracle oracle=oracle(handler);Suppliers suppliers=oracle==null?null:suppliers(oracle.value());
        // The current native constantUse lambda captures two pure numerical suppliers.
        if(suppliers!=null){
            long total=suppliers.total().getAsLong();long suppliedTicks=suppliers.ticks().getAsInt();
            if(recipeTicks>0&&!state.containsKey("baseTotalUsage")&&total==suppliedTicks)total=recipeTicks;
            state.put("random",0);state.put("operationMultiplier",total);
            return new Profile(Math.max(1,total),effectiveState(state));
        }
        Boolean nativeRandom=randomMode(handler);
        boolean random=nativeRandom!=null?nativeRandom:state.containsKey("injectUsage")
                ||oracle!=null&&!state.containsKey("baseTotalUsage")&&(state.containsKey("gasPerTickMeanMultiplier")||state.containsKey("chemicalPerTickMeanMultiplier"));
        if(random){
            String rateField=oracle!=null&&oracle.field().equals("gasUsageMultiplier")?"gasPerTickMeanMultiplier":
                    oracle!=null&&oracle.field().equals("chemicalUsageMultiplier")?"chemicalPerTickMeanMultiplier":
                    state.containsKey("injectUsage")?"injectUsage":state.containsKey("chemicalPerTickMeanMultiplier")?"chemicalPerTickMeanMultiplier":"gasPerTickMeanMultiplier";
            Number value=state.get(rateField);if(value==null)throw new IllegalStateException("Native random usage has no readable rate");
            double mean=value.doubleValue();if(!Double.isFinite(mean)||mean<0)throw new IllegalStateException("Invalid native chemical usage mean");
            // Native inversePoisson caps each draw at three times ceil(mean).
            long perTickBound=mean>=Long.MAX_VALUE/3D?Long.MAX_VALUE:Math.multiplyExact(3L,(long)Math.ceil(mean));
            long total=multiply(ticks,perTickBound);
            state.put("random",1);state.put("meanPerTick",mean);state.put("maxPerTick",perTickBound);state.put("operationMultiplier",total);
            return new Profile(Math.max(1,total),effectiveState(state));
        }
        // Legacy constant lambdas capture the machine instead of separate suppliers.
        long total=state.containsKey("baseTotalUsage")?state.get("baseTotalUsage").longValue():ticks;
        state.put("random",0);state.put("operationMultiplier",total);
        return new Profile(Math.max(1,total),effectiveState(state));
    }
    private static Oracle oracle(Object handler){
        for(Reader reader:ORACLES.get(handler.getClass())){Object value=reader.field().get(handler);if(value!=null)return new Oracle(value,reader.name());}
        return null;
    }
    private static Suppliers suppliers(Object oracle){
        LongSupplier total=null;IntSupplier ticks=null;
        for(Field field:oracle.getClass().getDeclaredFields()){
            if(!LongSupplier.class.isAssignableFrom(field.getType())&&!IntSupplier.class.isAssignableFrom(field.getType()))continue;
            try{if(!field.trySetAccessible())continue;Object value=field.get(oracle);
                if(value instanceof LongSupplier supplier)total=supplier;else if(value instanceof IntSupplier supplier)ticks=supplier;
            }catch(ReflectiveOperationException unavailable){}
        }
        return total==null||ticks==null?null:new Suppliers(total,ticks);
    }
    private static Boolean randomMode(Object handler){
        var method=RANDOM_MODE.get(handler.getClass());if(method.isEmpty())return null;
        try{return (boolean)method.get().invoke(handler);}catch(Throwable failure){throw new IllegalStateException("Cannot read native statistical usage mode",failure);}
    }
    private static int randomizedConsumption(){
        try{Class<?> config=Class.forName("mekanism.common.config.MekanismConfig"),usageType=Class.forName("mekanism.common.config.UsageConfig");
            Class<?> valueType=Class.forName("mekanism.common.config.value.CachedBooleanValue");
            Object usage=MethodHandles.publicLookup().findStaticVarHandle(config,"usage",usageType).get();
            Object value=MethodHandles.publicLookup().findVarHandle(usageType,"randomizedConsumption",valueType).get(usage);
            return Boolean.TRUE.equals(invoke(value,"get"))?1:0;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError absent){return -1;}
    }
    private static Map<String,Number> effectiveState(Map<String,Number> source){
        Map<String,Number> result=new LinkedHashMap<>();
        for(String key:List.of("perTick","ticks","effectiveTicks","recipeTicks","random","meanPerTick","maxPerTick","operationMultiplier"))
            if(source.containsKey(key))result.put(key,source.get(key));
        return result;
    }
    private static long multiply(long a,long b){try{return Math.multiplyExact(a,b);}catch(ArithmeticException tooLarge){return Long.MAX_VALUE;}}
}
