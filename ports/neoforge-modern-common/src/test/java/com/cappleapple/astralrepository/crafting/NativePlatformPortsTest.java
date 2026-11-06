package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.CapacityInventory;
import java.lang.reflect.Proxy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePlatformPortsTest {
    @BeforeAll static void bootstrap() { com.cappleapple.astralrepository.port.PortTestBootstrap.initialize(); }
    @Test @SuppressWarnings("unchecked") void aliasFacadesSharePhysicalIdentityAndKeepTransactionalSimulationReadOnly() {
        var inventory = new CapacityInventory(() -> 64L, () -> {});
        var nativeHandler = (ResourceHandler<ItemResource>)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResourceHandler.class},
                (proxy, method, arguments) -> method.invoke(inventory, arguments));
        var first = NativePlatformPorts.items(nativeHandler); var alias = NativePlatformPorts.items(nativeHandler);
        assertSame(first, alias);
        var port = NativeMachinePorts.itemSlot(first, 0, true, true);
        assertEquals(port.identity(), NativeMachinePorts.itemSlot(alias, 0, true, true).identity());
        var key = new ItemKey(new ItemStack(Items.IRON_INGOT));
        assertEquals(3, port.insert(key, 3, true)); assertTrue(port.snapshot().isEmpty());
        assertEquals(3, port.insert(key, 3, false)); assertEquals(3L, port.snapshot().get(key));
        assertEquals(2, port.extract(key, 2, true)); assertEquals(3L, port.snapshot().get(key));
        assertEquals(2, port.extract(key, 2, false)); assertEquals(1L, port.snapshot().get(key));
    }
}
