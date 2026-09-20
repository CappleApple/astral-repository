package com.cappleapple.astralrepository.client;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3f;

/** Captures geometry during extraction and submits immutable vertices later. */
public final class AstralBufferSource {
    private final Map<RenderType, Vertices> buffers = new LinkedHashMap<>();
    private final List<java.util.function.BiConsumer<PoseStack,SubmitNodeCollector>> draws = new ArrayList<>();
    public Vertices getBuffer(RenderType type) { return buffers.computeIfAbsent(type,key->new Vertices()); }
    public void enqueue(java.util.function.BiConsumer<PoseStack,SubmitNodeCollector> draw) { draws.add(draw); }
    public void endBatch() { freeze(); }
    public void endBatch(RenderType type) { freeze(type); }
    private void freeze(RenderType type) {
        var vertices=buffers.remove(type);if(vertices==null)return;
        var data=vertices.snapshot();
        var material=AstralPlaneRenderType.snapshot(type);
        draws.add((pose,collector)->AstralMaterialFeatureRenderer.submit(pose,collector,type,(transform,target)->{
            for(Vertex vertex:data)vertex.emit(transform,target);
        },material));
    }
    public void addFoil() {
        var data=new ArrayList<Vertex>();for(var vertices:buffers.values())data.addAll(vertices.snapshot());
        var immutable=List.copyOf(data);freeze();
        draws.add((pose,collector)->collector.order(1).submitCustomGeometry(pose,net.minecraft.client.renderer.rendertype.RenderTypes.entityGlint(),(transform,target)->{for(var vertex:immutable)vertex.emit(transform,target);}));
    }
    public void freeze() { for(var type:List.copyOf(buffers.keySet()))freeze(type); }
    public void submit(PoseStack pose,SubmitNodeCollector collector) { freeze();for(var draw:draws)draw.accept(pose,collector); }
    private record Vertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz,float width) {
        void emit(PoseStack.Pose pose,VertexConsumer out) { out.addVertex(pose,x,y,z).setColor(color).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose,nx,ny,nz).setLineWidth(width); }
    }
    public static final class Vertices implements VertexConsumer {
        private final List<Vertex> vertices=new ArrayList<>();
        private boolean active;private float x,y,z,u,v,nx,ny=1,nz,width=1;private int color=-1,overlay,light;
        private void finish(){if(active)vertices.add(new Vertex(x,y,z,color,u,v,overlay,light,nx,ny,nz,width));active=false;}
        private List<Vertex> snapshot(){finish();return List.copyOf(vertices);}
        @Override public VertexConsumer addVertex(float x,float y,float z){finish();active=true;this.x=x;this.y=y;this.z=z;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return setColor((a&255)<<24|(r&255)<<16|(g&255)<<8|b&255);}
        @Override public VertexConsumer setColor(int argb){color=argb;return this;}
        @Override public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){overlay=u|v<<16;return this;}
        @Override public VertexConsumer setUv2(int u,int v){light=u|v<<16;return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        @Override public VertexConsumer setLineWidth(float value){width=value;return this;}
        public void putBulkData(PoseStack.Pose pose,BakedQuad quad,float red,float green,float blue,float alpha,int light,int overlay){putBulkData(pose,quad,new float[]{1,1,1,1},red,green,blue,alpha,new int[]{light,light,light,light},overlay,true);}
        public void putBulkData(PoseStack.Pose pose,BakedQuad quad,float[] brightness,float red,float green,float blue,float alpha,int[] lights,int overlay,boolean colors){
            for(int i=0;i<4;i++){
                int base=colors?quad.bakedColors().color(i):-1;float scale=brightness[i];
                var position=quad.position(i);long uv=quad.packedUV(i);
                int packedNormal=quad.bakedNormals().normal(i);var normal=net.neoforged.neoforge.client.model.quad.BakedNormals.isUnspecified(packedNormal)?new Vector3f(quad.direction().getStepX(),quad.direction().getStepY(),quad.direction().getStepZ()):net.neoforged.neoforge.client.model.quad.BakedNormals.unpack(packedNormal,new Vector3f());
                addVertex(pose,position).setColor((base>>16&255)/255f*red*scale,(base>>8&255)/255f*green*scale,(base&255)/255f*blue*scale,(base>>>24)/255f*alpha)
                    .setUv(net.minecraft.client.model.geom.builders.UVPair.unpackU(uv),net.minecraft.client.model.geom.builders.UVPair.unpackV(uv)).setOverlay(overlay).setLight(lights[i]).setNormal(pose,normal.x(),normal.y(),normal.z());
            }
        }
    }
}
