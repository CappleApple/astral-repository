package com.cappleapple.astralrepository.platform.network;
import net.minecraft.server.MinecraftServer;import net.minecraft.server.level.ServerPlayer;import com.cappleapple.astralrepository.platform.CustomPacketPayload;
public final class PacketDistributor {
 public static MinecraftServer server;
 public static void sendToPlayer(ServerPlayer player,CustomPacketPayload payload){net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,payload.type().id(),com.cappleapple.astralrepository.platform.network.registration.PayloadRegistrar.encode(payload));}
 public static void sendToServer(CustomPacketPayload payload){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload.type().id(),com.cappleapple.astralrepository.platform.network.registration.PayloadRegistrar.encode(payload));}
 public static void sendToAllPlayers(CustomPacketPayload payload){if(server!=null)for(var player:server.getPlayerList().getPlayers())sendToPlayer(player,payload);}
}
