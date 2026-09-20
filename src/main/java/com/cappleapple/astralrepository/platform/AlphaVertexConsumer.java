package com.cappleapple.astralrepository.platform;
import com.mojang.blaze3d.vertex.VertexConsumer;
/** Preserve translucent lens alpha on the vanilla 1.20 bulk-quad path. */
public record AlphaVertexConsumer(VertexConsumer delegate,float alpha) implements VertexConsumer {
 public VertexConsumer vertex(double x,double y,double z){delegate.vertex(x,y,z);return this;}
 public VertexConsumer color(int r,int g,int b,int a){delegate.color(r,g,b,Math.round(a*alpha));return this;}
 public VertexConsumer uv(float u,float v){delegate.uv(u,v);return this;}
 public VertexConsumer overlayCoords(int u,int v){delegate.overlayCoords(u,v);return this;}
 public VertexConsumer uv2(int u,int v){delegate.uv2(u,v);return this;}
 public VertexConsumer normal(float x,float y,float z){delegate.normal(x,y,z);return this;}
 public void endVertex(){delegate.endVertex();}public void defaultColor(int r,int g,int b,int a){delegate.defaultColor(r,g,b,Math.round(a*alpha));}public void unsetDefaultColor(){delegate.unsetDefaultColor();}
}
