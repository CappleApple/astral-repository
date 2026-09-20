package com.cappleapple.astralrepository.platform.network;
import net.minecraft.server.MinecraftServer;import net.minecraft.server.level.ServerPlayer;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public final class PacketDistributor {
 public static MinecraftServer server;
 public static void sendToPlayer(ServerPlayer player,CustomPacketPayload payload){net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,payload);}
 public static void sendToServer(CustomPacketPayload payload){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);}
 public static void sendToAllPlayers(CustomPacketPayload payload){if(server!=null)for(var player:server.getPlayerList().getPlayers())sendToPlayer(player,payload);}
}
