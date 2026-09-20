package com.cappleapple.astralrepository.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransferVisualProjectionTest {
    private static GlobalPos at(int x, int y, int z) { return GlobalPos.of(Level.OVERWORLD, new BlockPos(x, y, z)); }

    @Test void immutableRoutesShareBackingWithoutAllocatingProjectedPointArrays() throws Exception {
        var route = List.of(at(1, 2, 3), at(9, 4, 7), at(17, 2, 1));
        var projected = TransferVisuals.projectedPath(route);
        var backing = projected.getClass().getDeclaredField("route"); backing.setAccessible(true);
        assertSame(route, backing.get(projected));
        assertSame(route.get(0).pos(), projected.get(0));
        var expected = List.of(route.get(0).pos(), route.get(1).pos(), route.get(2).pos());
        assertEquals(expected, projected); assertEquals(projected, expected); assertEquals(expected.hashCode(), projected.hashCode());
        assertEquals(com.cappleapple.astralrepository.platform.Backport.reverse(expected), TransferVisuals.projectedPath(com.cappleapple.astralrepository.platform.Backport.reverse(route)));
        assertThrows(UnsupportedOperationException.class, () -> projected.set(0, BlockPos.ZERO));
        assertThrows(UnsupportedOperationException.class, () -> projected.add(BlockPos.ZERO));
        assertThrows(UnsupportedOperationException.class, () -> projected.remove(0));
    }

    @Test void mutableCallerChangesCannotCorruptRetainedAudienceCacheKeys() {
        var source = new ArrayList<>(List.of(at(-3, 8, 1), at(128, 8, 1)));
        var projected = TransferVisuals.projectedPath(source);
        var expected = List.of(source.get(0).pos(), source.get(1).pos());
        var audienceCache = new HashMap<List<BlockPos>, String>(); audienceCache.put(projected, "original audience");
        int hash = projected.hashCode(); source.set(0, at(5000, 8, 1)); source.clear();
        assertEquals(expected, projected); assertEquals(hash, projected.hashCode());
        assertEquals("original audience", audienceCache.get(expected));
        assertEquals("original audience", audienceCache.get(projected));
    }

    @Test void acceptedPacketsKeepIndependentOrderedPathSnapshotsForBothDirections() {
        var source = new ArrayList<>(List.of(at(1, 2, 3), at(8, 4, 5), at(16, 2, 7)));
        for (var route : List.of(source, com.cappleapple.astralrepository.platform.Backport.reverse(source))) {
            var view = TransferVisuals.projectedPath(route);
            var packet = new NetworkPackets.Visual(view.get(0), view.get(view.size()-1), ItemStack.EMPTY, 0xffffff,
                    TransferVisuals.duration(view), -2, view);
            assertEquals(view, packet.path()); assertNotSame(view, packet.path());
            assertEquals(packet.from(), packet.path().get(0)); assertEquals(packet.to(), packet.path().get(packet.path().size()-1));
            assertThrows(UnsupportedOperationException.class, () -> packet.path().set(0, BlockPos.ZERO));
        }
        var reversed = TransferVisuals.projectedPath(com.cappleapple.astralrepository.platform.Backport.reverse(source)); var expected = List.copyOf(reversed);
        source.clear(); assertEquals(expected, reversed);
    }
}
