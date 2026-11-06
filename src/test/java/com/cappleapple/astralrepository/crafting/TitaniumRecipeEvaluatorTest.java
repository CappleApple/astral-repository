package com.cappleapple.astralrepository.crafting;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TitaniumRecipeEvaluatorTest {
    private static final class NativeLikeContract {
        private int amount = 50;
        int shadowChange(int drain) { amount -= drain; return amount; }
        static int forbidden() { System.setProperty("astral_repository.evaluator_forbidden", "executed"); return 1; }
        static int unbounded() { int value = 0; while (true) value++; }
        static void foreignArray(Object[] values) { values[0] = null; }
        static Object[] oversizedArray() { return new Object[2048]; }
        static int localCounter() { return new java.util.concurrent.atomic.AtomicInteger(3).addAndGet(4); }
        static int foreignCounter(java.util.concurrent.atomic.AtomicInteger value) { return value.incrementAndGet(); }
        static Object cloneAndEdit(Object[] values) { Object[] copy = values.clone(); copy[0] = null; return values[0]; }
        static int compare(long first, long second) { return first > second ? 1 : first == second ? 0 : -1; }
        static boolean less(double first, double second) { return first < second; }
        static int denseSwitch(int value) { return switch(value) { case -1 -> 5;case 0 -> 7;case 1 -> 11;case 2 -> 13;default -> 17; }; }
        static int sparseSwitch(int value) { return switch(value) { case -700 -> 19;case 101 -> 23;case 9000 -> 29;default -> 31; }; }
        static java.util.Map<Object,Object> copyEntries(java.util.Map<Object,Object> input) { return input.entrySet().stream().collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue)); }
        static Object mapDefault(java.util.Map<Object,Object> input,Object key,Object fallback) { return input.getOrDefault(key,fallback); }
        static Object mutateMap(java.util.Map<Object,Object> input) { return input.put("extra",7); }
        static Object mutateCopiedMap(java.util.Map<Object,Object> input) {
            var copy=input.entrySet().stream().collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,java.util.Map.Entry::getValue));
            copy.put("extra",7); return copy;
        }
    }
    @Test void evaluatesNativeFieldChangesOnlyInShadowState() {
        var nativeState = new NativeLikeContract();
        Object amount = TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "shadowChange", "(I)I", nativeState, List.of(7));
        assertEquals(43, ((Number)amount).intValue());
        assertEquals(50, nativeState.amount);
    }
    @Test void rejectsAnUnclassifiedNativeCallBeforeExecutingIt() {
        String before = System.getProperty("astral_repository.evaluator_forbidden");
        var failure = assertThrows(UnsupportedOperationException.class, () -> TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "forbidden", "()I", null, List.of()));
        assertTrue(failure.getMessage().contains("java.lang.System.setProperty"));
        assertEquals(before, System.getProperty("astral_repository.evaluator_forbidden"));
    }
    @Test void boundsNativeLoopsByAnInstructionBudget() {
        var failure = assertThrows(UnsupportedOperationException.class, () -> TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "unbounded", "()I", null, List.of()));
        assertTrue(failure.getMessage().contains("instruction budget exceeded"));
    }
    @Test void rejectsWritesToTheRealNativeInputArray() {
        Object marker = new Object(); Object[] values = { marker };
        var failure = assertThrows(UnsupportedOperationException.class, () -> TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "foreignArray", "([Ljava/lang/Object;)V", null, List.of((Object)values)));
        assertTrue(failure.getMessage().contains("non-shadow array"));
        assertSame(marker, values[0]);
    }
    @Test void boundsShadowArrayAllocation() {
        var failure = assertThrows(UnsupportedOperationException.class, () -> TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "oversizedArray", "()[Ljava/lang/Object;", null, List.of()));
        assertTrue(failure.getMessage().contains("array capacity"));
    }
    @Test void mutatesOnlyNewShadowAtomicCounters() {
        assertEquals(7,((Number)TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"localCounter","()I",null,List.of())).intValue());
        var actual=new java.util.concurrent.atomic.AtomicInteger(11);
        var failure=assertThrows(UnsupportedOperationException.class,()->TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"foreignCounter","(Ljava/util/concurrent/atomic/AtomicInteger;)I",null,List.of(actual)));
        assertTrue(failure.getMessage().contains("non-shadow atomic")); assertEquals(11,actual.get());
    }
    @Test void nativeArrayClonesCanChangeWithoutMutatingTheirSource() {
        Object marker = new Object(); Object[] values = { marker };
        Object result = TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "cloneAndEdit", "([Ljava/lang/Object;)Ljava/lang/Object;", null, List.of((Object)values));
        assertSame(marker, result); assertSame(marker, values[0]);
    }
    @Test void nativeNumericComparisonsPreserveLongRangeAndJvmNanSemantics() {
        assertEquals(-1, ((Number)TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "compare", "(JJ)I", null, List.of(Long.MIN_VALUE, Long.MAX_VALUE))).intValue());
        assertEquals(1, ((Number)TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "compare", "(JJ)I", null, List.of(Long.MAX_VALUE, Long.MIN_VALUE))).intValue());
        assertEquals(0, ((Number)TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "less", "(DD)Z", null, List.of(Double.NaN, 1.0))).intValue());
        assertEquals(0, ((Number)TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "less", "(DD)Z", null, List.of(-0.0, 0.0))).intValue());
    }
    @Test void nativeReadonlyCollectorsUseBoundedMethodReferencesWithoutMutatingTheirSource() {
        var input = new java.util.HashMap<Object,Object>(); input.put("key", 3);
        Object result = TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class, "copyEntries", "(Ljava/util/Map;)Ljava/util/Map;", null, List.of(input));
        assertEquals(java.util.Map.of("key", 3), result); assertNotSame(input, result); assertEquals(java.util.Map.of("key", 3), input);
    }
    @Test void nativeSwitchesFollowDenseSparseAndDefaultBranches() {
        for(int value:List.of(-1,0,1,2,42)) assertEquals(NativeLikeContract.denseSwitch(value),((Number)TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"denseSwitch","(I)I",null,List.of(value))).intValue());
        for(int value:List.of(-700,101,9000,42)) assertEquals(NativeLikeContract.sparseSwitch(value),((Number)TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"sparseSwitch","(I)I",null,List.of(value))).intValue());
    }
    @Test void nativeMapDefaultsPreserveAbsentAndPresentNullWithoutCallingAnOverride() {
        int[] calls={0};
        var input=new java.util.HashMap<Object,Object>() {
            @Override public Object getOrDefault(Object key,Object fallback) { calls[0]++; throw new AssertionError("Native override must remain unexecuted"); }
        };
        input.put("present",17); input.put("null",null);
        var before=new java.util.HashMap<>(input); Object fallback=new Object();
        String signature="(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
        assertEquals(17,TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"mapDefault",signature,null,List.of(input,"present",fallback)));
        assertNull(TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"mapDefault",signature,null,List.of(input,"null",fallback)));
        assertSame(fallback,TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"mapDefault",signature,null,List.of(input,"missing",fallback)));
        assertEquals(before,input); assertEquals(0,calls[0]);
    }
    @Test void nativeMapWritesRequireAnOwnedShadowCopy() {
        var original=new java.util.HashMap<Object,Object>(); original.put("before",3);
        String signature="(Ljava/util/Map;)Ljava/lang/Object;";
        var failure=assertThrows(UnsupportedOperationException.class,()->TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"mutateMap",signature,null,List.of(original)));
        assertTrue(failure.getMessage().contains("non-shadow native map"));
        assertEquals(java.util.Map.of("before",3,"extra",7),TitaniumRecipeEvaluator.evaluatePure(NativeLikeContract.class,"mutateCopiedMap",signature,null,List.of(original)));
        assertEquals(java.util.Map.of("before",3),original);
    }
}
