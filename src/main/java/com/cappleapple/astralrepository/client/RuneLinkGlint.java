package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.RenderPipelines;

/** The vanilla animated glint with its texture intensity tinted to a rune's exact RGB average. */
public final class RuneLinkGlint {
    private record Material(Identifier texture,RenderType type) {}
    private static final LinkedHashMap<Integer,Material> materials=new LinkedHashMap<>(32,.75F,true);
    private RuneLinkGlint(){}
    public static RenderType type(int rgb){
        rgb&=0xffffff;var old=materials.get(rgb);if(old!=null)return old.type();
        var mc=Minecraft.getInstance();
        var original=Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
        try(var stream=mc.getResourceManager().getResourceOrThrow(original).open();var pixels=NativeImage.read(stream)){
            var tinted=new NativeImage(pixels.getWidth(),pixels.getHeight(),true);
            for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){
                int pixel=pixels.getPixel(x,y),value=Math.max(pixel&255,Math.max(pixel>>8&255,pixel>>16&255));
                int red=(rgb>>16&255)*value/255,green=(rgb>>8&255)*value/255,blue=(rgb&255)*value/255;
                tinted.setPixel(x,y,(pixel&0xff000000)|red<<16|green<<8|blue);
            }
            var texture=Identifier.fromNamespaceAndPath("astral_repository","dynamic/linking_glint/"+Integer.toHexString(rgb));
            mc.getTextureManager().register(texture,new DynamicTexture(()->"Astral linking glint",tinted));
            var type=RenderType.create("astral_repository:linking_glint/"+Integer.toHexString(rgb),
                RenderSetup.builder(RenderPipelines.GLINT).withTexture("Sampler0",texture)
                    .setTextureTransform(TextureTransform.ENTITY_GLINT_TEXTURING).createRenderSetup());
            materials.put(rgb,new Material(texture,type));
            if(materials.size()>256){var first=materials.entrySet().iterator();var removed=first.next().getValue();first.remove();mc.getTextureManager().release(removed.texture());}
            return type;
        }catch(java.io.IOException failure){return RenderTypes.entityGlint();}
    }
    public static void clear(){var manager=Minecraft.getInstance().getTextureManager();for(var material:materials.values())manager.release(material.texture());materials.clear();}
}
