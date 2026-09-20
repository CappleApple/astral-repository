package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.RuneDesign;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Client-only flattened render cache. Saved pixels and item transforms remain separate. */
public final class RuneDesignRenderer {
    private static final Map<String,RuneDesign> designs=new LinkedHashMap<>();
    private static final LinkedHashMap<String,ResourceLocation> textures=new LinkedHashMap<>(32,.75f,true);
    public static void receive(com.cappleapple.astralrepository.network.RunePackets.Art art){RuneDesign design=RuneDesign.load(art.design());designs.put(design.key(),design);while(designs.size()>2048)designs.remove(designs.keySet().iterator().next());}
    public static RuneDesign find(String key){return designs.get(key);}
    public static ResourceLocation texture(RuneDesign design,int resolution){
        String key=design.key()+"/"+resolution;ResourceLocation cached=textures.get(key);if(cached!=null)return cached;
        var mc=Minecraft.getInstance();NativeImage pixels=new NativeImage(resolution,resolution,true);
        for(int y=0;y<resolution;y++)for(int x=0;x<resolution;x++){int c=design.argb(x,y);pixels.setPixelRGBA(x,y,(c&0xff00ff00)|((c&255)<<16)|((c>>16)&255));}
        ResourceLocation base=mc.getTextureManager().register("astral_rune",new DynamicTexture(pixels));
        if(design.icons().isEmpty()){cache(key,base);return base;}
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        TextureTarget target=new TextureTarget(resolution,resolution,true,Minecraft.ON_OSX);
        boolean worldSpace=AstralPlaneRenderType.setWorldSpace(false);
        RenderSystem.getModelViewStack().pushPose();
        try{
            RenderSystem.disableScissor();target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,resolution,resolution,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().setIdentity();RenderSystem.getModelViewStack().translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            GuiGraphics graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());graphics.blit(base,0,0,0,0,resolution,resolution,resolution,resolution);graphics.flush();
            for(var icon:design.icons()){
                ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(icon.item()));if(stack.isEmpty())continue;
                graphics.pose().pushPose();graphics.pose().translate(icon.x(),icon.y(),0);graphics.pose().mulPose(Axis.ZP.rotationDegrees(icon.rotation()));float scale=icon.scale()/16;graphics.pose().scale(scale,scale,scale);graphics.renderItem(stack,-8,-8);graphics.flush();graphics.pose().popPose();
            }
            NativeImage composite=new NativeImage(resolution,resolution,true);RenderSystem.bindTexture(target.getColorTextureId());composite.downloadTexture(0,false);composite.flipY();ResourceLocation result=mc.getTextureManager().register("astral_rune_composite",new DynamicTexture(composite));cache(key,result);return result;
        }finally{
            AstralPlaneRenderType.setWorldSpace(worldSpace);mc.getTextureManager().release(base);target.destroyBuffers();RenderSystem.getModelViewStack().popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else RenderSystem.disableScissor();
        }
    }
    private static void cache(String key,ResourceLocation texture){textures.put(key,texture);while(textures.size()>512){var first=textures.entrySet().iterator();var entry=first.next();Minecraft.getInstance().getTextureManager().release(entry.getValue());first.remove();}}
    public static void clearTextures(){var manager=Minecraft.getInstance().getTextureManager();for(var texture:textures.values())manager.release(texture);textures.clear();}
    public static void disconnect(){clearTextures();designs.clear();}
    public static void draw(GuiGraphics g,RuneDesign design,int resolution,int x,int y,int size){g.flush();ResourceLocation texture=texture(design,resolution);g.blit(texture,x,y,size,size,0,0,resolution,resolution,resolution,resolution);}
    private RuneDesignRenderer(){}
}
