package com.cappleapple.astralrepository.platform.network.registration;
import java.util.*;import java.util.function.BiConsumer;import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.world.entity.player.Player;import net.fabricmc.fabric.api.networking.v1.*;
public final class PayloadRegistrar {
 public record Context(Player player) {}
 private static final List<Runnable> CLIENT=new ArrayList<>();
 public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){PayloadTypeRegistry.serverboundPlay().register(type,codec);ServerPlayNetworking.registerGlobalReceiver(type,(payload,ctx)->ctx.server().execute(()->handler.accept(payload,new Context(ctx.player()))));}
 public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){PayloadTypeRegistry.clientboundPlay().register(type,codec);CLIENT.add(()->ClientRegistration.register(type,handler));}
 public static void registerClient(){CLIENT.forEach(Runnable::run);CLIENT.clear();}
 private static class ClientRegistration {static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,BiConsumer<T,Context> handler){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(type,(payload,ctx)->ctx.client().execute(()->handler.accept(payload,new Context(ctx.player()))));}}
}
