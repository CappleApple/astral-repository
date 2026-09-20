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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid=AstralRepository.MOD_ID)
public final class PowerNodeVisibilityPackets {
    private static MinecraftServer lastServer;
    private static boolean lastEnabled;

    public record Settings(boolean powerEnabled) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID, "power_visibility"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeBoolean(value.powerEnabled), buffer -> new Settings(buffer.readBoolean()));
        @Override public Type<Settings> type() { return TYPE; }
    }

    @EventBusSubscriber(modid=AstralRepository.MOD_ID, bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Settings.TYPE, Settings.CODEC,
                    (packet, context) -> PowerNodeVisibility.acceptServerState(packet.powerEnabled()));
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.sendToPlayer(player, new Settings(AstralConfig.powerEnabled.get()));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        boolean enabled = AstralConfig.powerEnabled.get();
        if (lastServer == event.getServer() && lastEnabled == enabled) return;
        lastServer = event.getServer();
        lastEnabled = enabled;
        PacketDistributor.sendToAllPlayers(new Settings(enabled));
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { lastServer = null; }

    private PowerNodeVisibilityPackets() {}
}
