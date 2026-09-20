package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One cosmetic flight follows the complete route; it never owns transferred resources. */
public final class TransferVisuals {
    public static int legTicks(BlockPos a,BlockPos b){return Math.clamp((int)Math.ceil(Math.sqrt(a.distSqr(b))*2.5),20,100);}
    public static int duration(List<BlockPos> path){int ticks=0;for(int i=1;i<path.size();i++)ticks+=legTicks(path.get(i-1),path.get(i));return ticks;}
    /** Offset is relative to the endpoint block center; normal points out of its rune face. */
    public record Endpoint(Vec3 offset,net.minecraft.core.Direction face){
        public Endpoint{if(face==null||!Double.isFinite(offset.lengthSqr())||Math.abs(offset.x)>1||Math.abs(offset.y)>1||Math.abs(offset.z)>1)throw new IllegalArgumentException("Invalid flight endpoint");}
    }
    public static Endpoint rune(com.cappleapple.astralrepository.content.RuneLayer layer){
        var surface=layer.surface();var cells=com.cappleapple.astralrepository.content.RuneLayout.placed(surface);int i=surface.layers().indexOf(layer);
        return i>=0&&i<cells.size()?new Endpoint(cells.get(i).center().subtract(surface.getBlockPos().getCenter()),surface.facing()):null;
    }
    public static Vec3 position(List<BlockPos> path,double progress){return position(path,progress,null,null);}
    public static Vec3 position(NetworkPackets.Visual packet,double progress){return position(packet.path(),progress,packet.departure(),packet.arrival());}
    /** Shared tangents round relay corners, with each influence point within half a block of its relay. */
    public static Vec3 position(List<BlockPos> path,double progress,Endpoint departure,Endpoint arrival){
        double elapsed=Math.clamp(progress,0,1)*duration(path);
        for(int i=1;i<path.size();i++){
            int ticks=legTicks(path.get(i-1),path.get(i));
            if(elapsed<=ticks||i==path.size()-1){
                double t=Math.clamp(elapsed/ticks,0,1),t2=t*t,t3=t2*t;
                Vec3 a=point(path,i-1,departure,arrival),b=point(path,i,departure,arrival);
                Vec3 m0=velocity(path,i-1,departure,arrival).scale(ticks),m1=velocity(path,i,departure,arrival).scale(ticks);
                return a.scale(2*t3-3*t2+1).add(m0.scale(t3-2*t2+t)).add(b.scale(-2*t3+3*t2)).add(m1.scale(t3-t2));
            }
            elapsed-=ticks;
        }
        return point(path,path.size()-1,departure,arrival);
    }
    public static Vec3 variedPosition(NetworkPackets.Visual packet,double progress,long seed,double variation){
        Vec3 base=position(packet,progress);if(variation<=0)return base;
        var path=packet.path();double elapsed=Math.clamp(progress,0,1)*duration(path);int leg=1;
        for(;leg<path.size()-1;leg++){int ticks=legTicks(path.get(leg-1),path.get(leg));if(elapsed<=ticks)break;elapsed-=ticks;}
        double t=Math.clamp(elapsed/legTicks(path.get(leg-1),path.get(leg)),0,1);
        // Shared random knots at relays and mid-leg positions. Only the source and
        // destination have zero offset: relay crossings keep the full variation.
        int knot=(leg-1)*2+(t<.5?0:1);
        double local=t<.5?t*2:t*2-1,blend=local*local*(3-2*local);
        Vec3 before=variationOffset(path,knot,seed,variation),after=variationOffset(path,knot+1,seed,variation);
        return base.add(before.lerp(after,blend));
    }
    private static Vec3 variationOffset(List<BlockPos> path,int knot,long seed,double variation){
        int last=(path.size()-1)*2;if(knot==0||knot==last)return Vec3.ZERO;
        int node=knot/2;
        Vec3 direction=(knot%2==0?path.get(node+1).getCenter().subtract(path.get(node-1).getCenter())
                :path.get(node+1).getCenter().subtract(path.get(node).getCenter())).normalize();
        if(direction.lengthSqr()<1e-6)direction=new Vec3(1,0,0);
        Vec3 side=direction.cross(new Vec3(0,1,0));if(side.lengthSqr()<1e-6)side=direction.cross(new Vec3(1,0,0));
        side=side.normalize();Vec3 up=direction.cross(side).normalize();
        var random=new java.util.Random(seed^(knot*0x9e3779b97f4a7c15L));
        double angle=random.nextDouble()*Math.PI*2,radius=Math.sqrt(random.nextDouble())*variation;
        return side.scale(Math.cos(angle)*radius).add(up.scale(Math.sin(angle)*radius));
    }
    private static Vec3 point(List<BlockPos> path,int i,Endpoint departure,Endpoint arrival){
        Vec3 p=path.get(i).getCenter();Endpoint e=i==0?departure:i==path.size()-1?arrival:null;if(e!=null)return p.add(e.offset());
        if(i>0&&i<path.size()-1){
            var before=path.get(i-1).getCenter().subtract(p);var after=path.get(i+1).getCenter().subtract(p);
            var bend=before.normalize().add(after.normalize());
            if(bend.lengthSqr()>1e-10)p=p.add(bend.normalize().scale(Math.min(.45,Math.min(before.length(),after.length())*.25)));
        }
        return p;
    }
    private static Vec3 velocity(List<BlockPos> path,int i,Endpoint departure,Endpoint arrival){
        Vec3 p=point(path,i,departure,arrival);int last=path.size()-1;
        if(i==0||i==last){
            boolean start=i==0;int other=start?1:last-1;Vec3 delta=point(path,other,departure,arrival).subtract(p);int ticks=legTicks(path.get(i),path.get(other));
            Endpoint e=start?departure:arrival;
            return e==null?delta.scale((start?1.0:-1.0)/ticks):Vec3.atLowerCornerOf(e.face().getNormal()).scale((start?1:-1)*Math.min(3,Math.max(.75,delta.length()*.35))/ticks);
        }
        Vec3 before=p.subtract(point(path,i-1,departure,arrival)),after=point(path,i+1,departure,arrival).subtract(p);
        int previous=legTicks(path.get(i-1),path.get(i)),next=legTicks(path.get(i),path.get(i+1));
        Vec3 tangent=before.add(after).scale(1.0/(previous+next));
        double limit=Math.min(before.length()/previous,after.length()/next);
        return tangent.length()>limit?tangent.normalize().scale(limit):tangent;
    }
    public static void send(MinecraftServer server,List<GlobalPos> route,ItemStack stack,int color,int style){send(server,route,stack,color,style,null,null);}
    public static void send(MinecraftServer server,List<GlobalPos> route,ItemStack stack,int color,int style,Endpoint departure,Endpoint arrival){send(server,route,stack,color,style,departure,arrival,null);}
    public static void send(MinecraftServer server,List<GlobalPos> route,ItemStack stack,int color,int style,Endpoint departure,Endpoint arrival,net.minecraft.resources.ResourceLocation fluid){
        if(route.size()<2||route.size()>130||AstralConfig.particleDensity.get()<=0)return;
        var level=server.getLevel(route.getFirst().dimension());if(level==null||level.players().isEmpty())return;
        var path=projectedPath(route);
        TransferVisualDispatcher.enqueue(level,path,style,()->new NetworkPackets.Visual(path.getFirst(),path.getLast(),stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1),color,duration(path),style,path,departure,arrival,fluid));
    }
    /** Suppliers run synchronously on the server thread, only after an observer admits the visual. */
    public static void sendWithEndpoints(MinecraftServer server,List<GlobalPos> route,ItemStack stack,int color,int style,
            java.util.function.Supplier<Endpoint> departure,java.util.function.Supplier<Endpoint> arrival,net.minecraft.resources.ResourceLocation fluid){
        sendWithEndpoints(server,route,()->stack,color,style,departure,arrival,fluid);
    }
    public static void sendWithEndpoints(MinecraftServer server,List<GlobalPos> route,java.util.function.Supplier<ItemStack> stack,int color,int style,
            java.util.function.Supplier<Endpoint> departure,java.util.function.Supplier<Endpoint> arrival,net.minecraft.resources.ResourceLocation fluid){
        if(route.size()<2||route.size()>130||AstralConfig.particleDensity.get()<=0)return;
        var level=server.getLevel(route.getFirst().dimension());if(level==null||level.players().isEmpty())return;
        var path=projectedPath(route);
        TransferVisualDispatcher.enqueue(level,path,style,()->{
            Endpoint from=departure==null?null:departure.get();
            Endpoint to=arrival==null?null:arrival==departure?from:arrival.get();
            ItemStack sample=stack.get();
            return new NetworkPackets.Visual(path.getFirst(),path.getLast(),sample.isEmpty()?ItemStack.EMPTY:sample.copyWithCount(1),color,duration(path),style,path,from,to,fluid);
        });
    }
    /** Shares immutable routes; mutable callers are snapshotted before the audience cache retains the view. */
    static List<BlockPos> projectedPath(List<GlobalPos> route){return new PointView(List.copyOf(route));}
    private static final class PointView extends java.util.AbstractList<BlockPos> implements java.util.RandomAccess {
        private final List<GlobalPos> route;
        private int hash;
        private boolean hashed;
        private PointView(List<GlobalPos> route){this.route=route;}
        @Override public BlockPos get(int index){return route.get(index).pos();}
        @Override public int size(){return route.size();}
        @Override public int hashCode(){
            if(!hashed){int value=1;for(int i=0;i<route.size();i++)value=31*value+route.get(i).pos().hashCode();hash=value;hashed=true;}
            return hash;
        }
    }
    private TransferVisuals(){}
}
