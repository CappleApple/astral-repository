package com.cappleapple.astralrepository.crafting;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMinecraftCallsTest {
    @Test void derivesRuntimeAliasesFromTheCompiledMinecraftContract() {
        var shim = NativeBytecode.inspect(NativeMinecraftCalls.class).methods().stream().filter(method -> method.name().equals("minecraft$getCount")).findFirst().orElseThrow();
        var reference = shim.references().stream().filter(value -> value.opcode() == 182).findFirst().orElseThrow();
        assertEquals("getCount", NativeMinecraftCalls.name(reference));
        assertTrue(NativeMinecraftCalls.runtimeNames(ItemStack.class, "getCount").contains(reference.name()));
        assertEquals("unknownNativeMethod", NativeMinecraftCalls.name(new NativeBytecode.Reference(182, "optional.machine.Api", "unknownNativeMethod", "()V")));
    }
}
