package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import java.util.*;
import net.minecraft.client.Minecraft;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import com.cappleapple.astralrepository.platform.client.event.*;
import org.joml.Vector3f;

/** Cosmetic preview of server-resolved routes. Only the actively held binding wand displays them. */
public final class BindingPreviewRenderer {
    private static BindingPreviewPackets.Preview preview=BindingPreviewPackets.Preview.EMPTY;
    private static List<List<Vec3>> curves=List.of();
    private static Object level;
    private static long received;
    public static int renderedSegments;
    public static long emittedParticles;
    public static void update(BindingPreviewPackets.Preview value){
        var mc=Minecraft.getInstance();
        if(level!=mc.level||!preview.equals(value))curves=value.paths().stream().map(path->{
            int steps=Math.min(256,Math.max(24,path.path().size()*12));List<Vec3> points=new ArrayList<>();
            for(int i=0;i<=steps;i++)points.add(TransferVisuals.position(path,i/(double)steps));return List.copyOf(points);
        }).toList();
        preview=value;level=mc.level;received=mc.level==null?0:mc.level.getGameTime();
    }
    public static BindingPreviewPackets.Preview active(){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=level||mc.player==null||mc.level.getGameTime()-received>40)return BindingPreviewPackets.Preview.EMPTY;
        var wand=mc.player.getMainHandItem();var selection=RuneProgramming.selection(wand);
        return wand.is(AstralContent.ATTUNEMENT_WAND.get())&&selection!=null&&selection.layer()!=null&&selection.layer().equals(preview.rune())&&selection.address().position().dimension().equals(mc.level.dimension())?preview:BindingPreviewPackets.Preview.EMPTY;
    }
    public static void tick(ClientTickEvent.Post event){
        var state=active();if(state.rune()==null||state.particleOrigin()==null)return;var mc=Minecraft.getInstance();if(mc.level.getGameTime()%3!=0)return;
        var normal=Vec3.atLowerCornerOf(state.particleFace().getUnitVec3i());
        var right=Math.abs(normal.y)>.5?new Vec3(1,0,0):normal.cross(new Vec3(0,1,0)).normalize();var up=normal.cross(right);
        var point=state.particleOrigin().add(normal.scale(.025)).add(right.scale((mc.level.getRandom().nextDouble()-.5)*.08)).add(up.scale((mc.level.getRandom().nextDouble()-.5)*.08));int color=state.color();
        mc.level.addParticle(new DustParticleOptions(color,.45f),
                point.x,point.y,point.z,normal.x*.006,normal.y*.006+.008,normal.z*.006);emittedParticles++;
    }
    public static void render(AstralWorldFrame event){
        
        renderedSegments=0;var state=active();if(state.rune()==null)return;
        var mc=Minecraft.getInstance();var buffers=event.buffers();var type=BindingBeamRenderType.BEAM;var vertex=buffers.getBuffer(type);var camera=event.getCamera().position();
        for(int path=0;path<curves.size();path++){
            var points=curves.get(path);int color=state.paths().get(path).color();
            List<Vec3> sides=new ArrayList<>(points.size());Vec3 previousSide=Vec3.ZERO;
            for(int i=0;i<points.size();i++){
                Vec3 tangent=points.get(Math.min(points.size()-1,i+1)).subtract(points.get(Math.max(0,i-1)));
                Vec3 side=points.get(i).subtract(camera).cross(tangent).normalize();
                if(side.lengthSqr()<.001)side=previousSide;
                else if(previousSide.lengthSqr()>0&&side.dot(previousSide)<0)side=side.scale(-1);
                sides.add(side);previousSide=side;
            }
            for(int i=1;i<points.size();i++){
                Vec3 a=points.get(i-1).subtract(camera),b=points.get(i).subtract(camera);if(a.lengthSqr()>65536&&b.lengthSqr()>65536)continue;
                ribbon(vertex,event.getPoseStack(),a,b,sides.get(i-1).scale(.035),sides.get(i).scale(.035),color,45);
                ribbon(vertex,event.getPoseStack(),a,b,sides.get(i-1).scale(.008),sides.get(i).scale(.008),color,210);renderedSegments++;
            }
        }
        buffers.endBatch(type);
    }
    private static void ribbon(VertexConsumer vertex,PoseStack pose,Vec3 a,Vec3 b,Vec3 startSide,Vec3 endSide,int color,int alpha){
        for(Vec3 point:List.of(a.add(startSide),b.add(endSide),b.subtract(endSide),a.subtract(startSide)))vertex.addVertex(pose.last().pose(),(float)point.x,(float)point.y,(float)point.z).setColor(color>>16&255,color>>8&255,color&255,alpha);
    }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event){preview=BindingPreviewPackets.Preview.EMPTY;curves=List.of();level=null;}
    private BindingPreviewRenderer(){}
}
