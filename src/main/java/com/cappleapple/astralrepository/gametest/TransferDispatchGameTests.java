package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.AstralServerConfig;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.DirectRuneTransfers;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.cappleapple.astralrepository.network.NetworkPackets;
import com.cappleapple.astralrepository.network.TransferVisualBatch;
import com.cappleapple.astralrepository.network.TransferVisualDispatcher;
import com.cappleapple.astralrepository.network.TransferVisuals;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.tick.ServerTickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class TransferDispatchGameTests {
    private record Receiver(FakePlayer player, List<TransferVisualBatch> batches, List<Integer> bytes) {}

    private static Receiver receiver(ServerLevel level, BlockPos pos) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "visual-dispatch"));
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        var batches = new ArrayList<TransferVisualBatch>(); var sizes = new ArrayList<Integer>();
        player.connection = new ServerGamePacketListenerImpl(player.getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (!(packet instanceof ClientboundCustomPayloadPacket custom) || !(custom.payload() instanceof TransferVisualBatch batch)) return;
                var buffer = new FriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                try {
                    TransferVisualBatch.CODEC.encode(buffer, batch); sizes.add(buffer.readableBytes());
                    batches.add(TransferVisualBatch.CODEC.decode(buffer));
                } finally { buffer.release(); }
            }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
        };
        // Exercise the real level-audience/dispatch path without loading extra chunks for fake players.
        level.players().add(player);
        return new Receiver(player, batches, sizes);
    }

    private static void flush(ServerLevel level) { TransferVisualDispatcher.flush(new ServerTickEvent.Post(() -> true, level.getServer())); }
    private static List<GlobalPos> route(ServerLevel level, BlockPos from, BlockPos to) {
        return List.of(GlobalPos.of(level.dimension(), from), GlobalPos.of(level.dimension(), to));
    }

    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop")
    public static void tenThousandTransfersUseOneBoundedBatchPerNearbyObserver(GameTestHelper helper) {
        var level = helper.getLevel(); var start = helper.absolutePos(new BlockPos(2, 2, 2));
        int oldCount = AstralServerConfig.maxVisualsPerPlayerTick.get(), oldBytes = AstralServerConfig.maxVisualBytesPerPlayerTick.get();
        double density = AstralConfig.particleDensity.get();
        var observers = new ArrayList<Receiver>();
        try {
            AstralServerConfig.maxVisualsPerPlayerTick.set(128); AstralServerConfig.maxVisualBytesPerPlayerTick.set(65536); AstralConfig.particleDensity.set(1.0);
            flush(level);
            observers.add(receiver(level, start)); observers.add(receiver(level, start.east(512))); observers.add(receiver(level, start.north(4096)));
            var path = route(level, start, start.east(1024)); var source = new ItemStack(Items.IRON_INGOT, 64);
            long began = System.nanoTime();
            for (int i = 0; i < 10000; i++) TransferVisuals.send(level.getServer(), path, source, 0x8866dd, -1 - i % 4);
            double offerMillis = (System.nanoTime() - began) / 1000000.0;
            began = System.nanoTime(); flush(level); double flushMillis = (System.nanoTime() - began) / 1000000.0;
            for (int i = 0; i < 2; i++) {
                var observer = observers.get(i);
                helper.assertTrue(observer.batches.size() == 1, "One animation payload replaces ten thousand individual sends per observer");
                var batch = observer.batches.get(0);
                helper.assertTrue(batch.visuals().size() == 128 && !batch.resetStations(), "Only the bounded fair sample is serialized");
                helper.assertTrue(observer.bytes.get(0) <= 65536, "Encoded animation work respects the configured byte budget");
                for (int style = -4; style <= -1; style++) {
                    int medium = style;
                    helper.assertTrue(batch.visuals().stream().anyMatch(v -> v.slot() == medium), "Every transferred resource medium remains represented");
                }
            }
            helper.assertTrue(observers.get(2).batches.isEmpty(), "Distant players receive no animation payload");
            helper.assertTrue(source.getCount() == 64, "Cosmetic sampling never mutates the real item stack");
            flush(level);
            helper.assertTrue(observers.get(0).batches.size() == 1, "There is no deferred animation backlog on the next tick");
            AstralRepository.LOGGER.info("Visual dispatch stress: 10000 offers, 2 nearby observers including a mid-leg observer, 1 distant observer; 128 flights and {} bytes each; offer={} ms, flush+codec={} ms",
                    observers.get(0).bytes.get(0), offerMillis, flushMillis);
            helper.succeed();
        } finally {
            for (var observer : observers) level.players().remove(observer.player);
            flush(level); AstralServerConfig.maxVisualsPerPlayerTick.set(oldCount); AstralServerConfig.maxVisualBytesPerPlayerTick.set(oldBytes); AstralConfig.particleDensity.set(density);
        }
    }

    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop")
    public static void stationOverloadClearsOldDisplaysAndOversizedIconsCannotOverflowWireBudget(GameTestHelper helper) {
        var level = helper.getLevel(); var start = helper.absolutePos(new BlockPos(2, 2, 2));
        int oldBytes = AstralServerConfig.maxVisualBytesPerPlayerTick.get(); double density = AstralConfig.particleDensity.get();
        Receiver receiver = null;
        try {
            AstralServerConfig.maxVisualBytesPerPlayerTick.set(4096); AstralConfig.particleDensity.set(1.0); flush(level);
            receiver = receiver(level, start);
            for (int i = 0; i < 700; i++) {
                var station = start.offset(i % 32, 0, i / 32);
                TransferVisuals.send(level.getServer(), route(level, station, station), new ItemStack(Items.IRON_INGOT), 0xffffff, 0);
            }
            TransferVisuals.send(level.getServer(), route(level, start, start), ItemStack.EMPTY, 0xffffff, 0);
            flush(level);
            helper.assertTrue(receiver.batches.size() == 1 && receiver.batches.get(0).resetStations(), "Dropped station updates explicitly clear obsolete client displays");
            helper.assertTrue(receiver.bytes.get(0) <= 4096, "Station traffic shares the hard per-player wire budget");
            receiver.batches.clear(); receiver.bytes.clear();
            var tag = new CompoundTag(); tag.putString("oversized_icon", "x".repeat(300000));
            var oversized = new ItemStack(Items.DIAMOND); oversized.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            var path = route(level, start, start.east(8));
            TransferVisuals.send(level.getServer(), path, oversized, 0xffffff, -1);
            TransferVisuals.send(level.getServer(), path, new ItemStack(Items.IRON_INGOT), 0xffffff, -1);
            flush(level);
            helper.assertTrue(receiver.batches.size() == 1 && receiver.batches.get(0).visuals().size() == 1, "An oversized icon is omitted while ordinary animation still arrives");
            helper.assertTrue(oversized.get(DataComponents.CUSTOM_DATA).copyTag().getString("oversized_icon").length() == 300000, "The transferred item data is untouched");
            helper.succeed();
        } finally {
            if (receiver != null) level.players().remove(receiver.player);
            flush(level); AstralServerConfig.maxVisualBytesPerPlayerTick.set(oldBytes); AstralConfig.particleDensity.set(density);
        }
    }

    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop")
    public static void componentHeavyStationFloodHasBoundedEncodingWork(GameTestHelper helper) {
        var level = helper.getLevel(); var start = helper.absolutePos(new BlockPos(2, 2, 2));
        int oldBytes = AstralServerConfig.maxVisualBytesPerPlayerTick.get(); double density = AstralConfig.particleDensity.get();
        Receiver receiver = null;
        try {
            AstralServerConfig.maxVisualBytesPerPlayerTick.set(4096); AstralConfig.particleDensity.set(1.0); flush(level);
            receiver = receiver(level, start);
            var tag = new CompoundTag(); tag.putString("large_icon", "x".repeat(8192));
            var source = new ItemStack(Items.DIAMOND, 64); source.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            for (int i = 0; i < 1000; i++) {
                var pos = start.offset(i % 32, 0, i / 32);
                TransferVisuals.send(level.getServer(), route(level, pos, pos), source, 0xffffff, 0);
            }
            var framesField = TransferVisualDispatcher.class.getDeclaredField("FRAMES"); framesField.setAccessible(true);
            var frame = ((java.util.Map<?, ?>) framesField.get(null)).get(level.getServer());
            var queuesField = frame.getClass().getDeclaredField("queues"); queuesField.setAccessible(true);
            var queue = ((java.util.Map<?, ?>) queuesField.get(frame)).get(receiver.player);
            var stationsField = queue.getClass().getDeclaredField("stations"); stationsField.setAccessible(true);
            var retained = new ArrayList<>(((java.util.Map<?, ?>) stationsField.get(queue)).values());
            helper.assertTrue(retained.size() == 512, "Station candidates have a fixed memory bound");
            flush(level);
            int attempts = 0, workBytes = 0;
            for (var entry : retained) {
                var encodedField = entry.getClass().getDeclaredField("encoded"); encodedField.setAccessible(true);
                var costField = entry.getClass().getDeclaredField("encodingBytes"); costField.setAccessible(true);
                if (encodedField.getBoolean(entry)) attempts++;
                workBytes += costField.getInt(entry);
            }
            helper.assertTrue(attempts <= 2 && workBytes <= 8192, "Encoding stops at two wire budgets, including failed oversized icons");
            helper.assertTrue(receiver.batches.size() == 1 && receiver.batches.get(0).resetStations()
                    && receiver.batches.get(0).visuals().isEmpty() && receiver.bytes.get(0) <= 4096, "The bounded reset removes stale displays when every icon is too large");
            helper.assertTrue(source.getCount() == 64 && source.get(DataComponents.CUSTOM_DATA).copyTag().getString("large_icon").length() == 8192, "Cosmetic overload leaves source quantities and data unchanged");
            AstralRepository.LOGGER.info("Visual dispatch heavy-component stress: 1000 station offers, 512 retained, {} encode attempts, {} encoded-work bytes, {} wire bytes", attempts, workBytes, receiver.bytes.get(0));
            helper.succeed();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally {
            if (receiver != null) level.players().remove(receiver.player);
            flush(level); AstralServerConfig.maxVisualBytesPerPlayerTick.set(oldBytes); AstralConfig.particleDensity.set(density);
        }
    }

    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop")
    public static void tenThousandObserverlessTransfersCreateNoDispatchFrame(GameTestHelper helper) {
        var level = helper.getLevel(); double density = AstralConfig.particleDensity.get();
        try {
            AstralConfig.particleDensity.set(1.0); flush(level);
            helper.assertTrue(level.players().isEmpty(), "This isolated dedicated fixture has no observing players");
            var start = helper.absolutePos(new BlockPos(2, 2, 2)); var path = route(level, start, start.east(8));
            long began = System.nanoTime();
            for (int i = 0; i < 10000; i++) TransferVisuals.send(level.getServer(), path, ItemStack.EMPTY, 0xffffff, -2);
            double millis = (System.nanoTime() - began) / 1000000.0;
            var frames = TransferVisualDispatcher.class.getDeclaredField("FRAMES"); frames.setAccessible(true);
            helper.assertTrue(!((java.util.Map<?, ?>) frames.get(null)).containsKey(level.getServer()), "Unobserved transfers allocate no queued icons or server dispatch frame");
            AstralRepository.LOGGER.info("Visual dispatch stress: 10000 observerless transfers, no frames or packets, {} ms", millis);
            helper.succeed();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally { flush(level); AstralConfig.particleDensity.set(density); }
    }
    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop")
    public static void runeGeometryIsResolvedOnlyForAdmittedObserversAndPreservesCustomPlacement(GameTestHelper helper) {
        var level = helper.getLevel(); var relative = new BlockPos(3, 2, 3); var start = helper.absolutePos(relative);
        int oldCount = AstralServerConfig.maxVisualsPerPlayerTick.get(); double density = AstralConfig.particleDensity.get();
        var observers = new ArrayList<Receiver>();
        try {
            AstralServerConfig.maxVisualsPerPlayerTick.set(128); AstralConfig.particleDensity.set(1.0); flush(level);
            helper.setBlock(relative, net.minecraft.world.level.block.Blocks.CHEST);
            var surface = new com.cappleapple.astralrepository.content.RuneSurface(level.getServer(),
                    new com.cappleapple.astralrepository.network.AnchorAddress(GlobalPos.of(level.dimension(), start), net.minecraft.core.Direction.NORTH));
            var first = surface.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH), com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH);
            var rune = surface.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PULL), com.cappleapple.astralrepository.content.RuneLayer.Mode.PULL);
            first.position(-.24, -.08); rune.position(.24, .08);
            var expected = TransferVisuals.rune(rune); int[] resolutions = {0}, samples = {0}; long owner = Thread.currentThread().threadId();
            var source = new ItemStack(Items.IRON_INGOT, 64);
            java.util.function.Supplier<ItemStack> sample = () -> {
                helper.assertTrue(Thread.currentThread().threadId() == owner, "Item samples stay on the server owner thread");
                samples[0]++; return source;
            };
            java.util.function.Supplier<TransferVisuals.Endpoint> endpoint = () -> {
                helper.assertTrue(Thread.currentThread().threadId() == owner, "Rune shape lookup stays on the server owner thread");
                resolutions[0]++; return TransferVisuals.rune(rune);
            };
            var path = route(level, start, start.east(8));
            TransferVisuals.sendWithEndpoints(level.getServer(), path, sample, 0xffffff, -1, endpoint, null, null);
            helper.assertTrue(resolutions[0] == 0 && samples[0] == 0, "An observerless transfer never resolves geometry or samples an item");
            observers.add(receiver(level, start.north(4096)));
            for (int i = 0; i < 10000; i++) TransferVisuals.sendWithEndpoints(level.getServer(), path, sample, 0xffffff, -1, endpoint, null, null);
            helper.assertTrue(resolutions[0] == 0 && samples[0] == 0, "Ten thousand out-of-range transfers never resolve geometry or sample an item");
            // The dispatcher snapshots the audience once per tick; new observers belong to the next frame.
            flush(level);
            observers.add(receiver(level, start)); observers.add(receiver(level, start.east(1)));
            TransferVisuals.sendWithEndpoints(level.getServer(), path, sample, 0xffffff, -1, endpoint, endpoint, null);
            helper.assertTrue(resolutions[0] == 1 && samples[0] == 1, "One admitted visual resolves its item sample and shared endpoint once across both observers");
            rune.position(.12, -.08); source.setCount(17); flush(level);
            for (int i = 1; i < observers.size(); i++) {
                var visual = observers.get(i).batches.get(0).visuals().get(0);
                helper.assertTrue(visual.stack().is(Items.IRON_INGOT) && visual.stack().getCount() == 1 && source.getCount() == 17,
                        "Each observer receives the copied one-item snapshot despite later source changes");
                helper.assertTrue(visual.departure().face() == expected.face() && visual.arrival().face() == expected.face()
                        && visual.departure().offset().distanceTo(expected.offset()) < .000001 && visual.arrival().offset().distanceTo(expected.offset()) < .000001, "The packet snapshots the selected custom rune position before later edits");
                helper.assertTrue(TransferVisuals.position(visual, 0).distanceTo(start.getCenter().add(expected.offset())) < .000001,
                        "Flights begin on the fitted chest rune, not the container center or another layer");
                observers.get(i).batches.clear();
            }
            int before = resolutions[0];
            for (int i = 0; i < 10000; i++) TransferVisuals.sendWithEndpoints(level.getServer(), path, sample, 0xffffff, -1, endpoint, null, null);
            helper.assertTrue(resolutions[0] - before < 10000 && samples[0] == resolutions[0], "Reservoir-rejected transfers skip both item sampling and custom-layout work");
            flush(level);
            helper.assertTrue(observers.get(0).batches.isEmpty(), "The distant observer receives no rune transfers");
            for (int i = 1; i < observers.size(); i++) helper.assertTrue(observers.get(i).batches.get(0).visuals().size() == 128, "Each nearby observer retains the configured bounded sample");
            helper.succeed();
        } finally {
            for (var observer : observers) level.players().remove(observer.player);
            flush(level); AstralServerConfig.maxVisualsPerPlayerTick.set(oldCount); AstralConfig.particleDensity.set(density);
        }
    }
    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop", batch = "visual_route_reuse")
    public static void committedPushAndPullVisualsFollowTheValidatedRouteAndCurrentRuneEndpoint(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var server = level.getServer();
        var homeRelative = new BlockPos(2, 2, 2); var remoteRelative = new BlockPos(30, 2, 2); var nearRelative = new BlockPos(2, 2, 6);
        var home = helper.absolutePos(homeRelative); var remote = helper.absolutePos(remoteRelative); var near = helper.absolutePos(nearRelative);
        double oldRange = AstralServerConfig.wandBindingRange.get(), density = AstralConfig.particleDensity.get();
        int oldMinimum = AstralServerConfig.minItemTransferTicks.get(); Receiver observer = null; RuneSurface surface = null;
        var relays = new ArrayList<BlockPos>();
        try {
            AstralServerConfig.wandBindingRange.set(4.0); AstralServerConfig.minItemTransferTicks.set(1); AstralConfig.particleDensity.set(1.0);
            for (var position : List.of(homeRelative, remoteRelative, nearRelative)) {
                level.getChunkAt(helper.absolutePos(position)); helper.setBlock(position, net.minecraft.world.level.block.Blocks.BARREL);
            }
            for (int x : new int[]{5, 17, 27}) {
                var relative = new BlockPos(x, 3, 2); var position = helper.absolutePos(relative); relays.add(position);
                level.getChunkAt(position); helper.setBlock(relative, AstralContent.RELAY_CRYSTAL.get());
                ((CrystalNodeBlockEntity) level.getBlockEntity(position)).setChannel(15);
            }
            surface = RuneSurfaces.getOrCreate(level, home, net.minecraft.core.Direction.NORTH); surface.setChannel(15);
            var rune = surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH), RuneLayer.Mode.PUSH); rune.position(.18, .12); rune.setEnabled(false);
            var cadence = RuneCadence.DEFAULT;
            for (var kind : RuneCadence.Kind.values()) cadence = cadence.with(kind, new RuneCadence.Rate(kind == RuneCadence.Kind.ITEMS ? 4 : 0, 1));
            rune.setCadence(cadence);
            helper.assertTrue(surface.toggleTarget(rune.id(), GlobalPos.of(level.dimension(), remote), net.minecraft.core.Direction.UP).assigned(), "Distant bare barrel accepts its target binding");
            var manager = NetworkManager.get(server); var managerTick = NetworkManager.class.getDeclaredMethod("tick"); managerTick.setAccessible(true); managerTick.invoke(manager);
            var forward = manager.route(GlobalPos.of(level.dimension(), home), GlobalPos.of(level.dimension(), remote), 15, 4);
            helper.assertTrue(forward.size() == 5, "Fixture uses three relay hops between distant containers");
            var host = (net.minecraft.world.level.block.entity.BarrelBlockEntity) level.getBlockEntity(home);
            var far = (net.minecraft.world.level.block.entity.BarrelBlockEntity) level.getBlockEntity(remote);
            var close = (net.minecraft.world.level.block.entity.BarrelBlockEntity) level.getBlockEntity(near);
            host.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
            var worker = new DirectRuneTransfers(server); worker.track(surface); rune.setEnabled(true);
            flush(level); observer = receiver(level, home);
            worker.tick(); flush(level);
            helper.assertTrue(host.getItem(0).getCount() == 4 && far.getItem(0).getCount() == 4 && rune.transferredItems() == 4, "Push commits exactly four real items before emitting its cosmetic route");
            var visual = observer.batches.get(0).visuals().get(0); var endpoint = TransferVisuals.rune(rune);
            helper.assertTrue(visual.path().equals(forward.stream().map(GlobalPos::pos).toList()) && visual.from().equals(home) && visual.to().equals(remote), "Push uses the transaction route in source-to-destination order");
            helper.assertTrue(visual.departure() != null && visual.arrival() == null && visual.departure().face() == surface.facing()
                    && visual.departure().offset().distanceTo(endpoint.offset()) < .000001, "Push leaves the selected custom rune position on its own face");
            observer.batches.clear(); rune.setMode(RuneLayer.Mode.PULL); rune.position(-.18, -.12);
            worker.tick(); flush(level);
            helper.assertTrue(host.getItem(0).getCount() == 8 && far.getItem(0).isEmpty() && rune.transferredItems() == 8, "Pull returns the same four real items without duplication");
            visual = observer.batches.get(0).visuals().get(0); endpoint = TransferVisuals.rune(rune);
            helper.assertTrue(visual.path().equals(forward.reversed().stream().map(GlobalPos::pos).toList()) && visual.from().equals(remote) && visual.to().equals(home), "Pull reverses the transaction's relay path");
            helper.assertTrue(visual.departure() == null && visual.arrival() != null && visual.arrival().face() == surface.facing()
                    && visual.arrival().offset().distanceTo(endpoint.offset()) < .000001, "Pull arrives at the edited rune position rather than a cached departure");
            observer.batches.clear(); rune.setMode(RuneLayer.Mode.PUSH); surface.clearTarget(rune.id());
            helper.assertTrue(surface.toggleTarget(rune.id(), GlobalPos.of(level.dimension(), near), net.minecraft.core.Direction.UP).assigned(), "The same rune rebinds to the nearby barrel");
            worker.tick(); flush(level);
            helper.assertTrue(host.getItem(0).getCount() == 4 && close.getItem(0).getCount() == 4 && far.getItem(0).isEmpty() && rune.transferredItems() == 12, "Rebound transfer conserves all eight items");
            visual = observer.batches.get(0).visuals().get(0);
            helper.assertTrue(visual.path().equals(List.of(home, near)), "A new direct transaction never reuses the previous relayed route");
            helper.succeed();
        } finally {
            if (observer != null) level.players().remove(observer.player);
            flush(level); if (surface != null) RuneSurfaces.remove(level, home, surface.facing());
            for (var relay : relays) level.setBlockAndUpdate(relay, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            AstralServerConfig.wandBindingRange.set(oldRange); AstralServerConfig.minItemTransferTicks.set(oldMinimum); AstralConfig.particleDensity.set(density);
        }
    }
    @GameTest(templateNamespace = "astral_repository", template = "empty_workshop")
    public static void distantRouteOffersDoNotEvictNearbyAudienceOrConstructCosmetics(GameTestHelper helper) throws Exception {
        var level=helper.getLevel();var start=helper.absolutePos(new BlockPos(2,2,2));
        double density=AstralConfig.particleDensity.get();Receiver observer=null;
        try{
            AstralConfig.particleDensity.set(1.0);flush(level);observer=receiver(level,start);
            var nearby=route(level,start,start.east(8));int[] samples={0};
            java.util.function.Supplier<ItemStack> sample=()->{samples[0]++;return ItemStack.EMPTY;};
            TransferVisuals.sendWithEndpoints(level.getServer(),nearby,sample,0xffffff,-2,null,null,null);
            var frames=TransferVisualDispatcher.class.getDeclaredField("FRAMES");frames.setAccessible(true);
            var frame=((Map<?,?>)frames.get(null)).get(level.getServer());
            var audiences=frame.getClass().getDeclaredField("audiences");audiences.setAccessible(true);
            var audience=((Map<?,?>)audiences.get(frame)).get(level);
            var routes=audience.getClass().getDeclaredField("routes");routes.setAccessible(true);
            var cached=(Map<?,?>)routes.get(audience);helper.assertTrue(cached.size()==1,"Nearby route is cached before distant traffic");
            for(int i=0;i<10000;i++){
                var from=start.offset(2048+i*2,0,5000);
                TransferVisuals.sendWithEndpoints(level.getServer(),route(level,from,from.east(8)),sample,0xffffff,-2,null,null,null);
            }
            helper.assertTrue(samples[0]==1&&cached.size()==1&&cached.containsKey(List.of(start,start.east(8))),
                    "Ten thousand distant routes create no cosmetic samples or cache entries and retain the nearby route");
            TransferVisuals.sendWithEndpoints(level.getServer(),nearby,sample,0xffffff,-2,null,null,null);flush(level);
            helper.assertTrue(samples[0]==2&&observer.batches.size()==1&&observer.batches.get(0).visuals().size()==2,
                    "The nearby observer still receives both flights around the distant flood");helper.succeed();
        }finally{if(observer!=null)level.players().remove(observer.player);flush(level);AstralConfig.particleDensity.set(density);}
    }
    private TransferDispatchGameTests() {}
}
