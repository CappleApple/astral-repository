package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.CrystalNodeBlockEntity;
import com.cappleapple.astralrepository.network.NetworkManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class CachedRouteGameTests {
    private record Fixture(ServerLevel level, NetworkManager manager, Method tick, GlobalPos from, GlobalPos to, List<BlockPos> relays) {
        List<GlobalPos> route() { return manager.route(from, to, 12, 3); }
        void refresh() throws Exception { tick.invoke(manager); }
        CrystalNodeBlockEntity middle() { return (CrystalNodeBlockEntity) level.getBlockEntity(relays.get(1)); }
        void close() { for (var pos : relays) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); }
    }
    private static Fixture fixture(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var positions = new ArrayList<BlockPos>();
        for (int x : new int[]{2, 16, 30}) {
            var pos = helper.absolutePos(new BlockPos(x, 2, 2)); positions.add(pos); level.getChunkAt(pos);
            level.setBlockAndUpdate(pos, AstralContent.RELAY_CRYSTAL.get().defaultBlockState());
            ((CrystalNodeBlockEntity) level.getBlockEntity(pos)).setChannel(12);
        }
        var from = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(0, 2, 2)));
        var to = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(32, 2, 2))); level.getChunkAt(from.pos()); level.getChunkAt(to.pos());
        var manager = NetworkManager.get(level.getServer()); var tick = NetworkManager.class.getDeclaredMethod("tick"); tick.setAccessible(true);
        var fixture = new Fixture(level, manager, tick, from, to, List.copyOf(positions)); fixture.refresh();
        helper.assertTrue(fixture.route().size() == 5, "The fixture requires all three relays"); return fixture;
    }
    private static Map<?, ?> entries(NetworkManager manager) throws Exception {
        var routesField = NetworkManager.class.getDeclaredField("routes"); routesField.setAccessible(true); var cache = routesField.get(manager);
        var entriesField = cache.getClass().getDeclaredField("entries"); entriesField.setAccessible(true); return (Map<?, ?>) entriesField.get(cache);
    }
    private static Object cached(NetworkManager manager, List<GlobalPos> path) throws Exception {
        for (var value : entries(manager).values()) {
            var points = value.getClass().getDeclaredMethod("points"); points.setAccessible(true);
            if (points.invoke(value).equals(path)) return value;
        }
        throw new AssertionError("Expected route was not retained");
    }
    private static List<?> relayReferences(Object entry) throws Exception {
        var method = entry.getClass().getDeclaredMethod("relays"); method.setAccessible(true); return (List<?>) method.invoke(entry);
    }

    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop", batch = "cached_route_lifecycle")
    public static void warmRoutesReplaceRemovedInstancesAndRejectLiveDisableOrRetune(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper);
        try {
            var path = fixture.route(); var retained = cached(fixture.manager, path);
            helper.assertTrue(fixture.route() == path && cached(fixture.manager, path) == retained, "Warm routes reuse immutable points and relay references");
            var references = relayReferences(retained); helper.assertTrue(references.size() == 3 && references.get(1) == fixture.middle(), "Each intermediate position retains the actual live relay instance");
            var weight = retained.getClass().getDeclaredMethod("weight"); weight.setAccessible(true);
            helper.assertTrue((int) weight.invoke(retained) == 11, "Cache weight counts five path points, three relay references and three chunk references");
            var previous = fixture.middle(); var replacement = new CrystalNodeBlockEntity(previous.getBlockPos(), previous.getBlockState()); replacement.setChannel(12);
            fixture.level.setBlockEntity(replacement);
            helper.assertTrue(previous.isRemoved(), "Vanilla block-entity replacement retires the previous instance immediately");
            var after = fixture.route(); var replaced = cached(fixture.manager, after);
            helper.assertTrue(after.equals(path) && replaced != retained && relayReferences(replaced).get(1) == replacement,
                    "A same-position replacement captures the new live instance before queued topology refresh");
            replacement.toggleEnabled(); helper.assertTrue(fixture.route().isEmpty(), "Disabled relay references cannot carry a cached transfer");
            replacement.toggleEnabled(); fixture.refresh(); helper.assertTrue(fixture.route().size() == 5, "Re-enabled network returns after topology refresh");
            replacement.setChannel(13); helper.assertTrue(fixture.route().isEmpty(), "Retuned relay references reject the old channel immediately");
            replacement.setChannel(12); fixture.refresh(); helper.assertTrue(fixture.route().size() == 5, "Restored channel rebuilds the reachable route");
            fixture.level.setBlockAndUpdate(replacement.getBlockPos(), Blocks.AIR.defaultBlockState());
            helper.assertTrue(fixture.route().isEmpty(), "Removed relay references never outlive their physical block"); helper.succeed();
        } finally { fixture.close(); }
    }

    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop", batch = "cached_route_lifecycle")
    @SuppressWarnings("unchecked")
    public static void liveChunkDemotionRejectsWarmAndRecapturedRoutesBeforeUnload(GameTestHelper helper) throws Exception {
        var fixture=fixture(helper);var chunk=fixture.level.getChunkAt(fixture.relays.get(1));
        var statusField=net.minecraft.world.level.chunk.LevelChunk.class.getDeclaredField("fullStatus");statusField.setAccessible(true);
        var previous=(java.util.function.Supplier<net.minecraft.server.level.FullChunkStatus>)statusField.get(chunk);
        try {
            var path=fixture.route();var retained=cached(fixture.manager,path);
            chunk.setFullStatus(()->net.minecraft.server.level.FullChunkStatus.INACCESSIBLE);
            helper.assertTrue(!fixture.middle().isRemoved(),"Demotion happens while the retained relay instance is still alive");
            helper.assertTrue(cached(fixture.manager,path)==retained,"No unload callback or topology refresh has cleared the warm entry");
            helper.assertTrue(fixture.route().isEmpty(),"Warm routing refuses a demoted intermediate chunk before delayed unload");
            helper.assertTrue(fixture.route().isEmpty(),"Cold recapture also refuses the demoted chunk even if the chunk cache still returns it");
            chunk.setFullStatus(previous);
            helper.assertTrue(fixture.route().size()==5,"Restoring accessible chunk status permits the same live relay instances again");
            helper.succeed();
        } finally {chunk.setFullStatus(previous);fixture.close();}
    }

    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop", batch = "cached_route_lifecycle")
    public static void chunkLifecycleCallbacksAndOcclusionEditsRetireCachedReferencesImmediately(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper); var obstruction = helper.absolutePos(new BlockPos(9, 2, 2));
        try {
            helper.assertTrue(!entries(fixture.manager).isEmpty(), "A warm route retains references before the lifecycle event");
            var chunk = fixture.level.getChunkAt(fixture.relays.get(1));
            NetworkManager.chunkUnload(new ChunkEvent.Unload(chunk));
            helper.assertTrue(entries(fixture.manager).isEmpty(), "Unload callback clears references synchronously before queued node cleanup");
            // Exercise the paired load callback without actually unloading the GameTest's own chunk.
            NetworkManager.chunkLoad(new ChunkEvent.Load(chunk, false)); fixture.refresh();
            helper.assertTrue(fixture.route().size() == 5, "Reload discovery installs current relay instances");
            NetworkManager.chunkLoad(new ChunkEvent.Load(chunk, false));
            helper.assertTrue(entries(fixture.manager).isEmpty(), "Load callback also retires any older retained route references");
            fixture.refresh(); helper.assertTrue(fixture.route().size() == 5, "The same graph is reachable after another lifecycle refresh");
            fixture.level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            helper.assertTrue(entries(fixture.manager).isEmpty(), "An opaque edit invalidates retained references before topology processing");
            helper.assertTrue(fixture.route().isEmpty(), "Cached paths cannot bypass a newly opaque relay edge");
            fixture.level.setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState()); fixture.refresh();
            helper.assertTrue(fixture.route().size() == 5, "Removing the obstruction restores the route"); helper.succeed();
        } finally { fixture.level.setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState()); fixture.close(); }
    }
    private CachedRouteGameTests() {}
}
