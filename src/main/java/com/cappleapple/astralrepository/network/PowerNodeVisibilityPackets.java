package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.cappleapple.astralrepository.platform.event.entity.player.PlayerEvent;
import com.cappleapple.astralrepository.platform.event.server.ServerStoppedEvent;
import com.cappleapple.astralrepository.platform.event.tick.ServerTickEvent;
import com.cappleapple.astralrepository.platform.network.PacketDistributor;
import com.cappleapple.astralrepository.platform.network.event.RegisterPayloadHandlersEvent;

public final class PowerNodeVisibilityPackets {
    private static MinecraftServer lastServer;
    private static boolean lastEnabled;

    public record Settings(boolean powerEnabled) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID, "power_visibility"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeBoolean(value.powerEnabled), buffer -> new Settings(buffer.readBoolean()));
        @Override public Type<Settings> type() { return TYPE; }
    }

    public static final class Registration {
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Settings.TYPE, Settings.CODEC,
                    (packet, context) -> PowerNodeVisibility.acceptServerState(packet.powerEnabled()));
        }
    }

    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.sendToPlayer(player, new Settings(AstralConfig.powerEnabled.get()));
    }

    public static void tick(ServerTickEvent.Post event) {
        boolean enabled = AstralConfig.powerEnabled.get();
        if (lastServer == event.getServer() && lastEnabled == enabled) return;
        lastServer = event.getServer();
        lastEnabled = enabled;
        PacketDistributor.sendToAllPlayers(new Settings(enabled));
    }

    public static void stop(ServerStoppedEvent event) { lastServer = null; }

    private PowerNodeVisibilityPackets() {}
}
