package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralServerConfig;
import com.cappleapple.astralrepository.network.TransferVisualBatch;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Codec-consuming observers exercise real audience/dispatch work without a client renderer or retained packet history. */
final class RuneStressObservers {
    private final ServerLevel level;
    private final List<FakePlayer> players=new ArrayList<>();
    private long batches,visuals,bytes;
    RuneStressObservers(ServerLevel level,int count,BlockPos pos){
        if(count<0||count>16)throw new IllegalArgumentException("Observer count must be in 0..16");
        this.level=level;
        for(int i=0;i<count;i++){
            var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"rune-stress-"+i));
            player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            player.connection=new ServerGamePacketListenerImpl(player.getServer(),player.connection.getConnection(),player,CommonListenerCookie.createInitial(player.getGameProfile(),false)){
                @Override public void send(Packet<?> packet){
                    if(!(packet instanceof ClientboundCustomPayloadPacket custom)||!(custom.payload() instanceof TransferVisualBatch batch))return;
                    var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
                    try{
                        TransferVisualBatch.CODEC.encode(buffer,batch);int size=buffer.readableBytes();
                        var decoded=TransferVisualBatch.CODEC.decode(buffer);
                        if(size>AstralServerConfig.maxVisualBytesPerPlayerTick.get())throw new AssertionError("Visual byte budget exceeded");
                        if(decoded.visuals().size()>AstralServerConfig.maxVisualsPerPlayerTick.get())throw new AssertionError("Moving visual count budget exceeded");
                        batches++;bytes+=size;visuals+=decoded.visuals().size();
                    }finally{buffer.release();}
                }
                @Override public void send(Packet<?> packet,PacketSendListener listener){send(packet);}
            };
            level.players().add(player);players.add(player);
        }
    }
    int size(){return players.size();}
    long batches(){return batches;}
    String summary(){return "observers="+size()+" encoded_batches="+batches+" encoded_visuals="+visuals+" encoded_bytes="+bytes;}
    void reset(){batches=visuals=bytes=0;}
    void close(){for(var player:players)level.players().remove(player);players.clear();}
}
