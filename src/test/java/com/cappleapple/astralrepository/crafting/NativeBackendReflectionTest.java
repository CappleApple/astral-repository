package com.cappleapple.astralrepository.crafting;

import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBackendReflectionTest {
    private static class Parent { protected String value(long amount) { return "parent:" + amount; } }
    private static final class Child extends Parent { @Override protected String value(long amount) { return "child:" + amount; } }
    private static int count(String... values) { return values.length; }

    @Test void invokesOnlyTheSelectedOptionalSignatureAndKeepsVirtualDispatch() {
        assertEquals("child:7", NativeBackendReflection.call(new Child(), "value", 7L));
    }
    @Test void hiddenLambdaResolvesThroughItsPublicSupplierContract() {
        Supplier<String> supplier = () -> "native-provider";
        assertTrue(supplier.getClass().isHidden());
        assertEquals("native-provider", NativeBackendReflection.call(supplier, "get"));
    }
    @Test void publicCollectionContractDoesNotRequireOpeningJavaBase() {
        assertEquals(2, NativeBackendReflection.call(List.of("first", "second"), "size"));
        assertEquals("second", NativeBackendReflection.call(List.of("first", "second"), "get", 1));
    }
    @Test void nativeVarargsReceivesTheExactExplicitArray() {
        assertEquals(2, NativeBackendReflection.call(NativeBackendReflectionTest.class, "count", (Object)new String[]{"one", "two"}));
    }
    @Test void nativeArrayAndOptionalListsExposeTheirActualElements() {
        assertEquals(List.of("first", "second"), NativeBackendReflection.list(new String[]{"first", "second"}));
        assertEquals(List.of("value"), NativeBackendReflection.list(Optional.of(new String[]{"value"})));
        assertTrue(NativeBackendReflection.list(Optional.empty()).isEmpty());
    }
}
