package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.RuneDesign;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Cached GPU compositions preserve both authored pixels and real item-model transforms. */
public final class RuneDesignRenderer {
    private static final Map<String,RuneDesign> designs=new LinkedHashMap<>();
    private static final LinkedHashMap<String,Identifier> textures=new LinkedHashMap<>(32,.75f,true);
    private static long nextTexture;
    public static void receive(com.cappleapple.astralrepository.network.RunePackets.Art art) { var design=RuneDesign.load(art.design()); designs.put(design.key(),design); while(designs.size()>2048)designs.remove(designs.keySet().iterator().next()); }
    public static RuneDesign find(String key) { return designs.get(key); }
    private static Identifier id() { return Identifier.fromNamespaceAndPath("astral_repository","dynamic/rune/"+nextTexture++); }
    public static Identifier texture(RuneDesign design,int resolution) {
        String key=design.key()+"/"+resolution; var cached=textures.get(key); if(cached!=null)return cached;
        var mc=Minecraft.getInstance(); var manager=mc.getTextureManager(); var pixels=new NativeImage(resolution,resolution,true);
        for(int y=0;y<resolution;y++)for(int x=0;x<resolution;x++)pixels.setPixel(x,y,design.argb(x,y));
        var base=id(); manager.register(base,new DynamicTexture(()->"Astral rune pixels",pixels));
        if(design.icons().isEmpty()) { cache(key,base); return base; }
        var target=new TextureTarget("Astral rune composition",resolution,resolution,true,GpuFormat.RGBA8_UNORM);
        var state=new GuiRenderState();
        var graphics=new GuiGraphicsExtractor(mc,state,0,0);
        graphics.blit(RenderPipelines.GUI_TEXTURED,base,0,0,0,0,resolution,resolution,resolution,resolution);
        for(var icon:design.icons()) {
            var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(icon.item())); if(stack.isEmpty())continue;
            graphics.pose().pushMatrix(); graphics.pose().translate(icon.x(),icon.y()); graphics.pose().rotate((float)Math.toRadians(icon.rotation()));
            float scale=icon.scale()/16; graphics.pose().scale(scale,scale); graphics.item(stack,-8,-8); graphics.pose().popMatrix();
        }
        var projection=RenderSystem.getProjectionMatrixBuffer(); var projectionType=RenderSystem.getProjectionType();
        boolean world=AstralPlaneRenderType.setWorldSpace(false); boolean adopted=false;
        try(var renderer=new GuiRenderer(state,mc.gameRenderer.featureRenderDispatcher(),List.of())) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(),new org.joml.Vector4f(0),target.getDepthTexture(),0.0);
            RuneCompositeTarget.begin(target); renderer.render(); renderer.endFrame();
            var result=id(); manager.register(result,new ComposedTexture(target)); adopted=true; cache(key,result); return result;
        } finally {
            RuneCompositeTarget.end(); AstralPlaneRenderType.setWorldSpace(world); manager.release(base);
            if(projection!=null)RenderSystem.setProjectionMatrix(projection,projectionType);
            if(!adopted)target.destroyBuffers();
        }
    }
    private static final class ComposedTexture extends AbstractTexture {
        private final TextureTarget target;
        ComposedTexture(TextureTarget target) { this.target=target; texture=target.getColorTexture(); textureView=target.getColorTextureView(); }
        @Override public void close() { target.destroyBuffers(); texture=null; textureView=null; }
    }
    private static void cache(String key,Identifier texture) { textures.put(key,texture); while(textures.size()>512) { var first=textures.entrySet().iterator(); var entry=first.next(); Minecraft.getInstance().getTextureManager().release(entry.getValue()); first.remove(); } }
    public static void clearTextures() { var manager=Minecraft.getInstance().getTextureManager(); for(var texture:textures.values())manager.release(texture); textures.clear(); }
    public static void disconnect() { clearTextures(); designs.clear(); }
    public static void draw(GuiGraphicsExtractor graphics,RuneDesign design,int resolution,int x,int y,int size) {
        graphics.blit(RenderPipelines.GUI_TEXTURED,texture(design,resolution),x,y,0,0,size,size,resolution,resolution,resolution,resolution);
    }
    private RuneDesignRenderer() {}
}
