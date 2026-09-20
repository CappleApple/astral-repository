package com.cappleapple.astralrepository.platform;
public final class PacketDistributor {
 public static void sendToServer(CustomPacketPayload payload){PayloadRegistrar.CHANNEL.sendToServer(payload);}
 public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player,CustomPacketPayload payload){PayloadRegistrar.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),payload);}
 public static void sendToAllPlayers(CustomPacketPayload payload){PayloadRegistrar.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),payload);}
}
