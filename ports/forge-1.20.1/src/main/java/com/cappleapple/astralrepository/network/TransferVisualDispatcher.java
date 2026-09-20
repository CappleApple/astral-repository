package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.AstralServerConfig;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import com.cappleapple.astralrepository.platform.PacketDistributor;

/** Bounded cosmetic work, independent of transfer commits and machine timing. */
@EventBusSubscriber(modid = AstralRepository.MOD_ID)
public final class TransferVisualDispatcher {
    private static final int MAX_STATION_CHANGES = 512;
    private static final int MAX_CACHED_ROUTES = 1024;
    private static final Map<MinecraftServer, Frame> FRAMES = new IdentityHashMap<>();

    private static final class Entry {
        private final NetworkPackets.Visual visual;
        private byte[] bytes;
        private boolean encoded;
        private int encodingBytes;

        private Entry(NetworkPackets.Visual visual) { this.visual = visual; }

        byte[] bytes(RegistryAccess registries, int maximum) {
            if (encoded) return bytes;
            encoded = true;
            // Bound both the output allocation and failed work on component-heavy item icons.
            var buffer = new FriendlyByteBuf(Unpooled.buffer(Math.min(256, maximum), maximum));
            try {
                NetworkPackets.Visual.CODEC.encode(buffer, visual);
                bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); encodingBytes = bytes.length;
            } catch (RuntimeException ignored) {
                encodingBytes = maximum;
                // This is only an icon. The real stack and committed transfer remain untouched.
            } finally { buffer.release(); }
            return bytes;
        }
    }

    private record Station(BlockPos position, int slot) {}
    private static final class Audience {
        private final VisualAudienceIndex<ServerPlayer> index;
        private final Map<List<BlockPos>, List<ServerPlayer>> routes = new LinkedHashMap<>(128, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<List<BlockPos>, List<ServerPlayer>> eldest) { return size() > MAX_CACHED_ROUTES; }
        };

        private Audience(ServerLevel level) {
            index = new VisualAudienceIndex<>(level.players().stream().map(p -> new VisualAudienceIndex.Observer<>(p, p.position())).toList());
        }

        private List<ServerPlayer> nearby(List<BlockPos> path) {
            var cached=routes.get(path);if(cached!=null)return cached;
            var nearby=index.nearby(path);
            // Empty audiences are cheap to reject and must not evict nearby routes on busy distant networks.
            if(!nearby.isEmpty())routes.put(path,nearby);
            return nearby;
        }
    }

    private static final class PlayerQueue {
        private final VisualSampler<Entry> ordinary;
        private final Map<Station, Entry> stations = new LinkedHashMap<>();
        private boolean resetStations;

        private PlayerQueue(int capacity, long seed) { ordinary = new VisualSampler<>(capacity, seed); }

        private void station(Entry entry) {
            var key = new Station(entry.visual.from(), entry.visual.slot());
            // Removing first retains the order of the latest changes across related table slots.
            stations.remove(key); stations.put(key, entry);
            if (stations.size() > MAX_STATION_CHANGES) {
                stations.remove(stations.keySet().iterator().next()); resetStations = true;
            }
        }

        TransferVisualBatch batch(RegistryAccess registries, int byteBudget) {
            var entries = new ArrayList<byte[]>(); int used = TransferVisualBatch.HEADER_BYTES;
            int workRemaining = byteBudget * 2;
            for (var station : stations.values()) {
                if (byteBudget - used < 32 || !station.encoded && workRemaining < 32) { resetStations = true; continue; }
                boolean cached = station.encoded;
                byte[] bytes = station.bytes(registries, Math.min(byteBudget - TransferVisualBatch.HEADER_BYTES, workRemaining));
                if (!cached) workRemaining -= station.encodingBytes;
                if (bytes == null || bytes.length > byteBudget - used) { resetStations = true; continue; }
                entries.add(bytes); used += bytes.length;
            }
            for (var visual : ordinary.entries()) {
                if (byteBudget - used < 32) break;
                if (!visual.encoded && workRemaining < 32) continue;
                boolean cached = visual.encoded;
                byte[] bytes = visual.bytes(registries, Math.min(byteBudget - TransferVisualBatch.HEADER_BYTES, workRemaining));
                if (!cached) workRemaining -= visual.encodingBytes;
                if (bytes == null || bytes.length > byteBudget - used) continue;
                entries.add(bytes); used += bytes.length;
            }
            return entries.isEmpty() && !resetStations ? null : TransferVisualBatch.encoded(resetStations, entries);
        }
    }

    private static final class Frame {
        private final int capacity = AstralServerConfig.maxVisualsPerPlayerTick.get();
        private final int byteBudget = AstralServerConfig.maxVisualBytesPerPlayerTick.get();
        private final Map<ServerLevel, Audience> audiences = new IdentityHashMap<>();
        private final Map<ServerPlayer, PlayerQueue> queues = new IdentityHashMap<>();
        private final long seed;
        private Frame(MinecraftServer server) { seed = server.overworld().getGameTime(); }
    }

    static void enqueue(ServerLevel level, List<BlockPos> path, int slot, Supplier<NetworkPackets.Visual> visual) {
        if (level.players().isEmpty()) return;
        var frame = FRAMES.computeIfAbsent(level.getServer(), Frame::new);
        var audience = frame.audiences.computeIfAbsent(level, Audience::new).nearby(path);
        Entry entry = null;
        for (int i=0;i<audience.size();i++) {
            var player=audience.get(i);var queue=frame.queues.get(player);
            if(queue==null){queue=new PlayerQueue(frame.capacity,frame.seed^player.getUUID().getLeastSignificantBits());frame.queues.put(player,queue);}
            if (slot >= 0) {
                if (entry == null) entry = new Entry(visual.get());
                queue.station(entry);
            } else {
                int index = queue.ordinary.reserve();
                if (index < 0) continue;
                if (entry == null) entry = new Entry(visual.get());
                queue.ordinary.put(index, entry);
            }
        }
    }

    // Run after NetworkManager and other tick listeners have committed this tick's transfers.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void flush(ServerTickEvent event) {if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var frame = FRAMES.remove(event.getServer()); if (frame == null) return;
        for (var audience : frame.queues.entrySet()) {
            var player = audience.getKey();
            var batch = audience.getValue().batch(player.level().registryAccess(), frame.byteBudget);
            if (batch == null) continue;
            try { PacketDistributor.sendToPlayer(player, batch); }
            catch (RuntimeException failure) { AstralRepository.LOGGER.debug("Cosmetic batch skipped: {}", failure.toString()); }
        }
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { FRAMES.remove(event.getServer()); }
    private TransferVisualDispatcher() {}
}
