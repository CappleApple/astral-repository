package com.cappleapple.astralrepository.client;
import com.cappleapple.astralrepository.AstralClientConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
/** Submits panel PNGs through the procedural material before their controls. */
public final class AstralInterfaceRenderer {
    public static void blit(GuiGraphicsExtractor graphics,Identifier texture,int x,int y,int width,int height){
        var pipeline=AstralPlaneRenderType.ready()&&AstralClientConfig.astralInterfaceOverlayOpacity.get()>0?AstralPlaneRenderType.INTERFACE_PIPELINE:RenderPipelines.GUI_TEXTURED;
        graphics.blit(pipeline,texture,x,y,0,0,width,height,width,height);
    }
    private AstralInterfaceRenderer(){}
}
