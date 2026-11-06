package com.cappleapple.astralrepository.crafting;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBytecodeTest {
    private static final class Contract {
        private static final Object PROVIDER = new Object();
        private Map<String, List<Object>> recipes;
        static Object lookup(int mode) {
            return switch (mode) { case 0, 1, 2 -> PROVIDER; default -> null; };
        }
        static int sparse(int value) {
            return switch (value) { case -1 -> 10; case 150 -> 20; case 10000 -> 30; default -> 0; };
        }
        static Runnable finish() { return () -> lookup(1); }
    }

    @Test void discoversTypedFieldsAndExactProviderReadsWithoutMachineNames() {
        var shape = NativeBytecode.inspect(Contract.class);
        assertEquals(Contract.class.getName(), shape.name());
        var field = shape.fields().stream().filter(value -> value.name().equals("recipes")).findFirst().orElseThrow();
        assertEquals("Ljava/util/Map;", field.descriptor());
        assertTrue(field.signature().contains("Ljava/util/List<Ljava/lang/Object;>;"));
        var method = shape.methods().stream().filter(value -> value.name().equals("lookup")).findFirst().orElseThrow();
        var provider = method.references().stream().filter(value -> value.staticRead() && value.name().equals("PROVIDER")).findFirst().orElseThrow();
        assertSame(Contract.PROVIDER, provider.readStatic(Contract.class.getClassLoader()));
        assertTrue(method.instructions().stream().anyMatch(value -> value.opcode() == 170));
        assertTrue(shape.methods().stream().filter(value -> value.name().equals("sparse")).findFirst().orElseThrow()
                .instructions().stream().anyMatch(value -> value.opcode() == 171));
        assertTrue(shape.methods().stream().filter(value -> value.name().equals("finish")).findFirst().orElseThrow()
                .references().stream().anyMatch(value -> value.opcode() == 186 && value.owner().equals(Contract.class.getName()) && value.name().startsWith("lambda$")));
    }

    @Test void rejectsMalformedClassData() {
        assertThrows(IOException.class, () -> NativeBytecode.read(new ByteArrayInputStream(new byte[]{1, 2, 3, 4})));
        assertThrows(IOException.class, () -> NativeBytecode.read(new ByteArrayInputStream(new byte[]{(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe})));
    }
}
