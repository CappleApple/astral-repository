package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralServerConfig;
import com.cappleapple.astralrepository.content.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import com.cappleapple.astralrepository.platform.StreamCodec;
import com.cappleapple.astralrepository.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import com.cappleapple.astralrepository.platform.Capabilities;
import com.cappleapple.astralrepository.platform.PacketDistributor;
import com.cappleapple.astralrepository.platform.PayloadRegistrar;

/** Only the selected hotbar wand receives route previews; unchanged geometry is refreshed once per second. */
public final class BindingPreviewPackets {
    public record Preview(UUID rune,int color,List<NetworkPackets.Visual> paths,Vec3 particleOrigin,Direction particleFace) implements CustomPacketPayload {
        public static final Type<Preview> TYPE=new Type<>(new ResourceLocation("astral_repository","binding_preview"));
        public static final Preview EMPTY=new Preview(null,0,List.of(),null,null);
        public Preview{if((particleOrigin==null)!=(particleFace==null)||particleOrigin!=null&&!Double.isFinite(particleOrigin.lengthSqr()))throw new IllegalArgumentException("Invalid particle source");paths=List.copyOf(paths);if(paths.size()>RuneLayer.MAX_TARGETS+1)throw new IllegalArgumentException("Too many binding paths");}
        public static final StreamCodec<FriendlyByteBuf,Preview> CODEC=StreamCodec.of((b,p)->{
            b.writeBoolean(p.rune!=null);if(p.rune!=null)b.writeUUID(p.rune);b.writeInt(p.color);b.writeVarInt(p.paths.size());for(var path:p.paths)NetworkPackets.Visual.CODEC.encode(b,path);b.writeBoolean(p.particleOrigin!=null);if(p.particleOrigin!=null){b.writeDouble(p.particleOrigin.x);b.writeDouble(p.particleOrigin.y);b.writeDouble(p.particleOrigin.z);b.writeEnum(p.particleFace);}
        },b->{UUID rune=b.readBoolean()?b.readUUID():null;int color=b.readInt(),count=b.readVarInt();if(count<0||count>RuneLayer.MAX_TARGETS+1)throw new IllegalArgumentException("Too many binding paths");List<NetworkPackets.Visual> paths=new ArrayList<>();for(int i=0;i<count;i++)paths.add(NetworkPackets.Visual.CODEC.decode(b));Vec3 origin=null;Direction face=null;if(b.readBoolean()){origin=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());face=b.readEnum(Direction.class);}return new Preview(rune,color,paths,origin,face);});
        public Type<Preview> type(){return TYPE;}
    }
    public static Consumer<Preview> receiver=p->{};
    private static final Map<ServerPlayer,Preview> last=new WeakHashMap<>();
    public static void register(PayloadRegistrar registrar){registrar.playToClient(Preview.TYPE,Preview.CODEC,(p,c)->receiver.accept(p));}
    public static void tick(ServerPlayer player){
        Preview next=snapshot(player),before=last.get(player);
        if(!next.equals(before)||(next.rune()!=null&&player.server.getTickCount()%20==0)){PacketDistributor.sendToPlayer(player,next);last.put(player,next);}
    }
    public static Preview snapshot(ServerPlayer player){
        ItemStack wand=player.getMainHandItem();if(!wand.is(AstralContent.ATTUNEMENT_WAND.get()))return Preview.EMPTY;
        var selection=RuneProgramming.selection(wand);if(selection==null||selection.layer()==null||!selection.address().position().dimension().equals(player.level().dimension()))return Preview.EMPTY;
        var pos=selection.address().position();if(!player.serverLevel().hasChunkAt(pos.pos()))return Preview.EMPTY;
        var surface=RuneSurfaces.get(player.serverLevel(),pos.pos(),selection.address().face());var rune=surface==null?null:surface.get(selection.layer());if(rune==null)return Preview.EMPTY;
        int color=rune.design().averageColor(AstralServerConfig.runeResolution.get());List<NetworkPackets.Visual> paths=new ArrayList<>();
        for(var target:rune.targets()){var path=route(player,rune,target,color);if(path!=null)paths.add(path);}
        var eye=player.getEyePosition();var look=player.getViewVector(1);
        double reach=AstralServerConfig.wandBindingRange.get();
        for(double distance=0;distance<=reach;distance+=.5)if(!player.serverLevel().hasChunkAt(BlockPos.containing(eye.add(look.scale(distance))))){reach=Math.max(0,distance-.5);break;}
        HitResult hit=player.level().clip(new net.minecraft.world.level.ClipContext(eye,eye.add(look.scale(reach)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK&&player.serverLevel().hasChunkAt(block.getBlockPos())&&!pos.pos().equals(block.getBlockPos())){
            boolean crystal=player.level().getBlockEntity(block.getBlockPos()) instanceof CrystalNodeBlockEntity;
            if(crystal||RuneProgramming.isContainer(player.serverLevel(),block.getBlockPos(),block.getDirection())){
                var target=new RuneLayer.Target(GlobalPos.of(player.level().dimension(),block.getBlockPos()),crystal?null:block.getDirection());
                if(!rune.targets().contains(target)){var path=route(player,rune,target,color);if(path!=null)paths.add(path);}
            }
        }
        boolean unbound=rune.targets().isEmpty();
        return new Preview(rune.id(),color,paths,unbound?pos.pos().getCenter().add(TransferVisuals.rune(rune).offset()):null,unbound?surface.facing():null);
    }
    private static NetworkPackets.Visual route(ServerPlayer player,RuneLayer rune,RuneLayer.Target target,int color){
        var source=rune.surface().address().position();var route=NetworkManager.get(player.server).route(source,target.position(),rune.surface().channel(),AstralServerConfig.wandBindingRange.get());
        if(route.size()<2)return null;
        var positions=route.stream().map(GlobalPos::pos).toList();
        var arrival=target.face()==null?null:new TransferVisuals.Endpoint(Vec3.atLowerCornerOf(target.face().getNormal()).scale(.501),target.face());
        return new NetworkPackets.Visual(positions.get(0),positions.get(positions.size()-1),ItemStack.EMPTY,color,TransferVisuals.duration(positions),-1,positions,TransferVisuals.rune(rune),arrival);
    }
    private BindingPreviewPackets(){}
}
