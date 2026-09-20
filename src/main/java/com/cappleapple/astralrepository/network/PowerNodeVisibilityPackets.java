package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import net.minecraft.network.FriendlyByteBuf;
import com.cappleapple.astralrepository.platform.StreamCodec;
import com.cappleapple.astralrepository.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import com.cappleapple.astralrepository.platform.PacketDistributor;
import com.cappleapple.astralrepository.platform.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid=AstralRepository.MOD_ID)
public final class PowerNodeVisibilityPackets {
    private static MinecraftServer lastServer;
    private static boolean lastEnabled;

    public record Settings(boolean powerEnabled) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = new Type<>(new ResourceLocation(AstralRepository.MOD_ID, "power_visibility"));
        public static final StreamCodec<FriendlyByteBuf, Settings> CODEC = StreamCodec.of(
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

    @SubscribeEvent public static void tick(ServerTickEvent event) {if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        boolean enabled = AstralConfig.powerEnabled.get();
        if (lastServer == event.getServer() && lastEnabled == enabled) return;
        lastServer = event.getServer();
        lastEnabled = enabled;
        PacketDistributor.sendToAllPlayers(new Settings(enabled));
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { lastServer = null; }

    private PowerNodeVisibilityPackets() {}
}
