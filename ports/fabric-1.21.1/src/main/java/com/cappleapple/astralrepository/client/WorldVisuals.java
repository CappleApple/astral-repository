package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.GogglesEquipment;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.network.NetworkPackets;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import com.cappleapple.astralrepository.platform.client.event.RenderLevelStageEvent;
import com.cappleapple.astralrepository.platform.client.event.ClientTickEvent;
import org.joml.Vector3f;
import java.util.*;

/** Bounded, client-only presentation. These representations never own inventory contents. */
public final class WorldVisuals {
    private record Flight(NetworkPackets.Visual packet,long started,long seed,ResourceArrival arrival,ResourceTrailSchedule trailSchedule,PreparedTransferPath route,boolean detailedItem) {
        private Flight(NetworkPackets.Visual packet,long started,long seed,boolean detailedItem){
            this(packet,started,seed,ResourceArrival.create(packet,seed,com.cappleapple.astralrepository.AstralClientConfig.resourceArrivalVariance.get()),detailedItem);
        }
        private Flight(NetworkPackets.Visual packet,long started,long seed,ResourceArrival arrival,boolean detailedItem){
            this(packet,started,seed,arrival,packet.slot()>=-4&&packet.slot()<=-2?new ResourceTrailSchedule(started,seed):null,
                    packet.slot()<0&&!packet.from().equals(packet.to())?PreparedTransferPath.create(packet,seed,com.cappleapple.astralrepository.AstralClientConfig.transferPathVariation.get(),arrival):null,detailedItem);
        }
    }
    private static final List<Flight> flights=new ArrayList<>();
    private static final List<ResourceTrail> resourceTrails=new ArrayList<>();
    private static NetworkPackets.Diagnostics diagnostics=new NetworkPackets.Diagnostics(List.of(),List.of());
    private static long diagnosticsAt,visualTicks,admitted,sampled;
    private static int movingItems,stations,trailCursor;
    private static Object currentLevel;
    private static Flight[] visibleFlights=new Flight[0];
    private static Vec3[] visiblePoints=new Vec3[0];
    private static double[] visibleAges=new double[0];
    private static long lastRenderNanos;
    private static int lastRenderedItems,lastRenderedIcons,lastRenderedResources,lastRenderedTrails;
    private static void level(net.minecraft.client.multiplayer.ClientLevel level){
        if(level!=currentLevel){flights.clear();resourceTrails.clear();diagnostics=new NetworkPackets.Diagnostics(List.of(),List.of());
            currentLevel=level;visualTicks=level==null?0:level.getGameTime();movingItems=stations=trailCursor=0;admitted=sampled=0;}
    }
    public static void add(NetworkPackets.Visual packet){
        var world=Minecraft.getInstance().level;if(world==null)return;level(world);
        if(packet.duration()<=0||packet.path().size()<2||packet.path().size()>130)return;
        boolean detailedItem=true;
        if(packet.slot()>=0){
            int before=flights.size();
            flights.removeIf(f->f.packet.from().equals(packet.from())&&(packet.slot()<9?f.packet.slot()==packet.slot()||f.packet.slot()==packet.slot()+10:f.packet.slot()==packet.slot()));
            stations-=before-flights.size();
            if(packet.stack().isEmpty())return;
            if(stations>=512){sampled++;return;}
            stations++;
        }else{
            if(AstralConfig.particleDensity.get()<=0||flights.size()-stations>=com.cappleapple.astralrepository.AstralClientConfig.maxActiveTransfers.get()){sampled++;return;}
            if(packet.slot()==-1){detailedItem=movingItems<com.cappleapple.astralrepository.AstralClientConfig.maxItemTransferModels.get();if(detailedItem)movingItems++;}
        }
        flights.add(new Flight(packet,visualTicks,world.random.nextLong(),detailedItem));admitted++;
    }
    public static void clearStationDisplays(){flights.removeIf(f->f.packet.slot()>=0);stations=0;}
    public static void diagnostics(NetworkPackets.Diagnostics packet){level(Minecraft.getInstance().level);diagnostics=packet;diagnosticsAt=visualTicks;}
    /** Internal diagnostic snapshot; all limits affect presentation only. */
    public record Performance(int active,int trails,long admitted,long sampled,int itemsDrawn,int iconsDrawn,int resourcesDrawn,int trailsDrawn,int iconModelResolutions,long renderNanos) {}
    public static Performance performance(){return new Performance(flights.size(),resourceTrails.size(),admitted,sampled,lastRenderedItems,lastRenderedIcons,lastRenderedResources,lastRenderedTrails,TransferItemSprites.modelResolutions(),lastRenderNanos);}
    public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();level(mc.level);if(mc.level==null||mc.isPaused()||!mc.level.tickRateManager().runsNormally())return;
        // Server time-sync packets may correct gameTime. Existing flights use a local monotonic tick age.
        visualTicks++;
        flights.removeIf(f->visualTicks-f.started>f.packet.duration());
        movingItems=stations=0;for(var f:flights){if(f.packet.slot()==-1&&f.detailedItem)movingItems++;else if(f.packet.slot()>=0)stations++;}
        resourceTrails.removeIf(p->p.expired(visualTicks));
        double density=AstralConfig.particleDensity.get();if(density<=0){resourceTrails.clear();return;}
        int budget=(int)(com.cappleapple.astralrepository.AstralClientConfig.maxTransferTrailEmissions.get()*density);
        int count=flights.size(),visited=0;
        Vec3 eye=mc.gameRenderer.getMainCamera().getPosition();double distance=com.cappleapple.astralrepository.AstralClientConfig.transferRenderDistance.get();distance*=distance;
        while(visited<count&&budget>0){
            if(trailCursor>=count)trailCursor=0;
            Flight flight=flights.get(trailCursor++);visited++;
            var p=flight.packet;if(p.slot()>=0)continue;
            boolean resource=flight.trailSchedule!=null;
            double released=resource?flight.trailSchedule.poll(visualTicks):visualTicks;
            if(Double.isNaN(released))continue;
            if(resource&&resourceTrails.size()>=com.cappleapple.astralrepository.AstralClientConfig.maxResourceTrails.get())continue;
            double age=released-flight.started,t=Math.clamp(age/p.duration(),0,1);
            Vec3 point=position(flight,t);if(point.distanceToSqr(eye)>distance)continue;
            if(resource){
                float opacity=resourceOpacity(flight,point,age);if(opacity<=0)continue;
                Vec3 drift=position(flight,Math.min(1,t+1.0/p.duration())).subtract(point).scale(.12);
                resourceTrails.add(new ResourceTrail(p,point,drift,released,12+mc.level.random.nextInt(6),.04F+mc.level.random.nextFloat()*.025F,opacity));
            }else{
                int rgb=p.color();mc.level.addParticle(new DustParticleOptions(new Vector3f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f),.55f),point.x,point.y,point.z,0,.006,0);
            }
            budget--;
        }
    }
    private static float resourceSize(Flight flight,double ageTicks){
        return .16F*ResourceDeparture.scale(flight.packet,ageTicks);
    }
    private static float resourceOpacity(Flight flight,Vec3 point,double ageTicks){
        return ResourceDeparture.opacity(flight.packet,ageTicks)*(flight.arrival==null?1:flight.arrival.opacity(point));
    }
    private static Vec3 position(Flight flight,double t){return flight.route==null?position(flight.packet,t):flight.route.position(t);}
    private static Vec3 position(NetworkPackets.Visual p,double t){
        Vec3 from=p.from().getCenter(),to=p.to().getCenter();
        if(p.slot()>=0){int slot=p.slot();if(slot>=10){slot-=10;return to.add(((slot%3)-1)*.27,.8,((slot/3)-1)*.27);}if(slot==9)return to.add(0,.8,0);double converge=Math.max(0,(t-.65)/.35);return to.add(((slot%3)-1)*.27*(1-converge),.8+Math.sin(t*Math.PI)*.12,((slot/3)-1)*.27*(1-converge));}
        if(from.equals(to))return to.add(Math.cos(t*Math.PI*2)*.25,.4+Math.sin(t*Math.PI)*.5,Math.sin(t*Math.PI*2)*.25);
        return com.cappleapple.astralrepository.network.TransferVisuals.position(p,t);
    }
    public static void astralCoordinates(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY)
            AstralPlaneRenderType.beginWorld(event.getModelViewMatrix(), event.getCamera().getPosition());
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL)
            AstralPlaneRenderType.endWorld();
    }
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        PoseStack pose=event.getPoseStack();Vec3 camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        boolean profile=Boolean.getBoolean("astral_repository.transferStress");long renderStart=profile?System.nanoTime():0;
        lastRenderedItems=lastRenderedIcons=lastRenderedResources=lastRenderedTrails=0;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now=(double)visualTicks+partial;
        double distance=com.cappleapple.astralrepository.AstralClientConfig.transferRenderDistance.get();distance*=distance;
        int capacity=flights.size();if(visibleFlights.length<capacity){visibleFlights=new Flight[capacity];visiblePoints=new Vec3[capacity];visibleAges=new double[capacity];}
        int visible=0;
        for(Flight flight:flights){
            var p=flight.packet;double age=AnimationTime.elapsed(visualTicks,flight.started,partial),t=Math.clamp(age/p.duration(),0,1);
            if(p.slot()>=-1&&p.stack().isEmpty()||(p.slot()==9&&t<.85))continue;
            Vec3 point=position(flight,t);if(!visible(event,point,camera,distance,.5))continue;
            if(p.slot()<=-2||p.slot()==-1&&!flight.detailedItem){visibleFlights[visible]=flight;visiblePoints[visible]=point;visibleAges[visible++]=age;continue;}
            pose.pushPose();pose.translate(point.x-camera.x,point.y-camera.y,point.z-camera.z);pose.scale(.45f,.45f,.45f);
            pose.mulPose(Axis.YP.rotationDegrees(AnimationTime.rotation(visualTicks,partial,120)));
            mc.getItemRenderer().renderStatic(p.stack(),ItemDisplayContext.GROUND,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);pose.popPose();lastRenderedItems++;
        }
        TransferItemSprites.beginFrame();
        for(int i=0;i<visible;i++){
            var flight=visibleFlights[i];if(flight.packet.slot()!=-1)continue;
            if(TransferItemSprites.render(flight.packet.stack(),visiblePoints[i].subtract(camera),pose,buffers,event.getCamera().rotation(),.16F))lastRenderedIcons++;
        }
        TransferItemSprites.flush(buffers);
        // Keep each atlas contiguous: alternating fluid/energy packets must not flush one draw per sprite.
        for(int group=0;group<2;group++){
            boolean energy=group==1;
            for(int i=0;i<visible;i++){
                Flight flight=visibleFlights[i];if(flight.packet.slot()>-2||(flight.packet.slot()==-3)!=energy)continue;
                Vec3 point=visiblePoints[i];double age=visibleAges[i];float opacity=resourceOpacity(flight,point,age);
                if(opacity>0){ResourceTransferRenderer.render(flight.packet,point.subtract(camera),pose,buffers,event.getCamera().rotation(),resourceSize(flight,age),opacity);lastRenderedResources++;}
            }
            for(var trail:resourceTrails){
                if((trail.packet().slot()==-3)!=energy)continue;
                Vec3 point=trail.position(now);if(!visible(event,point,camera,distance,.15))continue;
                float opacity=trail.opacity(now);if(opacity<=0)continue;
                ResourceTransferRenderer.render(trail.packet(),point.subtract(camera),pose,buffers,event.getCamera().rotation(),trail.radius()*(.6F+.4F*opacity),opacity);lastRenderedTrails++;
            }
            ResourceTransferRenderer.flush(buffers,energy);
        }
        Arrays.fill(visibleFlights,0,visible,null);Arrays.fill(visiblePoints,0,visible,null);
        if(GogglesEquipment.isWearing(mc.player)&&now-diagnosticsAt<60){
            var lines=buffers.getBuffer(RenderType.lines());
            for(var edge:diagnostics.edges())line(lines,pose,edge.from().getCenter().subtract(camera),edge.to().getCenter().subtract(camera),edge.color());
            for(var node:diagnostics.nodes()){
                Vec3 point=node.pos().getCenter();if(point.distanceToSqr(camera)>144)continue;
                Vec3 look=point.subtract(camera).normalize();if(look.dot(Vec3.directionFromRotation(mc.player.getXRot(),mc.player.getYRot()))<.94)continue;
                pose.pushPose();pose.translate(point.x-camera.x,point.y-camera.y+1,point.z-camera.z);pose.mulPose(event.getCamera().rotation());pose.scale(-.018f,-.018f,.018f);
                int y=0;for(String text:node.text().split("\n")){mc.font.drawInBatch(text,-mc.font.width(text)/2f,y,0xFFE4EEE8,false,pose.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0x80121B22,LightTexture.FULL_BRIGHT);y+=10;}pose.popPose();
            }
        }
        buffers.endBatch();
        lastRenderNanos=profile?System.nanoTime()-renderStart:0;
    }
    private static boolean visible(RenderLevelStageEvent event,Vec3 point,Vec3 camera,double distance,double radius){
        return point.distanceToSqr(camera)<=distance&&event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(point.x-radius,point.y-radius,point.z-radius,point.x+radius,point.y+radius,point.z+radius));
    }
    private static void line(VertexConsumer vertex,PoseStack pose,Vec3 a,Vec3 b,int color){Vec3 n=b.subtract(a).normalize();for(Vec3 p:List.of(a,b))vertex.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).setColor(color>>16&255,color>>8&255,color&255,130).setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);}
    private WorldVisuals(){}
}


