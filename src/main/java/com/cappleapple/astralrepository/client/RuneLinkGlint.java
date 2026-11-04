package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/** The vanilla animated glint with its texture intensity tinted to a rune's exact RGB average. */
public final class RuneLinkGlint extends RenderType {
    private record Material(ResourceLocation texture,RenderType type) {}
    private static final LinkedHashMap<Integer,Material> materials=new LinkedHashMap<>(32,.75F,true);
    private record SideBuffer(net.minecraft.client.renderer.MultiBufferSource.BufferSource source,com.mojang.blaze3d.vertex.ByteBufferBuilder backing,Map<RenderType,com.mojang.blaze3d.vertex.ByteBufferBuilder> fixed) {}
    private static final Map<net.minecraft.client.renderer.MultiBufferSource.BufferSource,SideBuffer> sideBuffers=new IdentityHashMap<>();
    private static final Set<RenderType> materialTypes=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<net.minecraft.client.renderer.MultiBufferSource.BufferSource> supplemental=Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean owns(RenderType type){return materialTypes.contains(type);}
    /** Keep foil and base vertices in independent builders until the original source draws its base geometry. */
    public static com.mojang.blaze3d.vertex.VertexConsumer buffer(net.minecraft.client.renderer.MultiBufferSource.BufferSource source,RenderType type){
        if(!owns(type)||supplemental.contains(source))return null;
        var side=sideBuffers.computeIfAbsent(source,ignored->{var backing=new com.mojang.blaze3d.vertex.ByteBufferBuilder(1536);var fixed=new LinkedHashMap<RenderType,com.mojang.blaze3d.vertex.ByteBufferBuilder>();var extra=net.minecraft.client.renderer.MultiBufferSource.immediateWithBuffers(fixed,backing);supplemental.add(extra);return new SideBuffer(extra,backing,fixed);});
        side.fixed().computeIfAbsent(type,ignored->new com.mojang.blaze3d.vertex.ByteBufferBuilder(1536));
        return side.source().getBuffer(type);
    }
    public static void flush(net.minecraft.client.renderer.MultiBufferSource.BufferSource source){var side=sideBuffers.get(source);if(side!=null){side.source().endBatch();var entries=side.fixed().entrySet().iterator();while(entries.hasNext()){var entry=entries.next();if(!owns(entry.getKey())){entry.getValue().close();entries.remove();}}}}
    public static boolean flush(net.minecraft.client.renderer.MultiBufferSource.BufferSource source,RenderType type){var side=sideBuffers.get(source);if(side==null||!owns(type))return false;side.source().endBatch(type);return true;}
    private RuneLinkGlint(){super("astral_repository:linking_glint",DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
    public static RenderType type(int rgb){
        rgb&=0xffffff;var old=materials.get(rgb);if(old!=null)return old.type();
        var mc=Minecraft.getInstance();
        var original=ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
        try(var stream=mc.getResourceManager().getResourceOrThrow(original).open();var pixels=NativeImage.read(stream)){
            var tinted=new NativeImage(pixels.getWidth(),pixels.getHeight(),true);
            for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){
                int pixel=pixels.getPixelRGBA(x,y),value=Math.max(pixel&255,Math.max(pixel>>8&255,pixel>>16&255));
                int red=(rgb>>16&255)*value/255,green=(rgb>>8&255)*value/255,blue=(rgb&255)*value/255;
                tinted.setPixelRGBA(x,y,(pixel&0xff000000)|blue<<16|green<<8|red);
            }
            var texture=mc.getTextureManager().register("astral_linking_glint",new DynamicTexture(tinted));
            var type=create("astral_repository:linking_glint/"+Integer.toHexString(rgb),DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,1536,
                CompositeState.builder().setShaderState(RENDERTYPE_GLINT_SHADER).setTextureState(new TextureStateShard(texture,true,false))
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setDepthTestState(EQUAL_DEPTH_TEST)
                    .setTransparencyState(GLINT_TRANSPARENCY).setTexturingState(GLINT_TEXTURING).createCompositeState(false));
            materials.put(rgb,new Material(texture,type));materialTypes.add(type);
            if(materials.size()>256){var first=materials.entrySet().iterator();var removed=first.next().getValue();first.remove();materialTypes.remove(removed.type());mc.getTextureManager().release(removed.texture());}
            return type;
        }catch(java.io.IOException failure){return RenderType.glint();}
    }
    public static net.minecraft.client.renderer.MultiBufferSource buffers(net.minecraft.world.item.ItemStack stack,net.minecraft.client.renderer.MultiBufferSource buffers){
        var tinted=type(com.cappleapple.astralrepository.content.RuneProgramming.linkingColor(stack));
        return renderType->buffers.getBuffer(renderType==RenderType.glint()?tinted:renderType);
    }
    public static void clear(){for(var side:sideBuffers.values()){side.backing().close();for(var buffer:side.fixed().values())buffer.close();}sideBuffers.clear();supplemental.clear();materialTypes.clear();var manager=Minecraft.getInstance().getTextureManager();for(var material:materials.values())manager.release(material.texture());materials.clear();}
}
