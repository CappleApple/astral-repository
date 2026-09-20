package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.NetworkPackets;
import com.mojang.blaze3d.vertex.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Four vertices per sprite; the fragment shader crops and feathers the circle. */
final class ResourceTransferRenderer extends RenderType {
    private record Sprite(float u0,float v0,float u1,float v1,int color) {}
    private static final Map<ResourceLocation,Sprite> FLUIDS=new HashMap<>();
    private static Sprite source;
    private static final Sprite ENERGY_SPRITE=new Sprite(0,0,1,1,0xffffd65c);
    private static ShaderInstance shader;
    private static final ResourceLocation ENERGY=ResourceLocation.withDefaultNamespace("textures/particle/glow.png");
    private static final RenderType ATLAS=material(TextureAtlas.LOCATION_BLOCKS), POWER=material(ENERGY);
    static void loaded(ShaderInstance value){shader=value;clearCache();}
    static void clearCache(){FLUIDS.clear();source=null;}
    static boolean ready(){return shader!=null;}
    private static RenderType material(ResourceLocation texture){
        return create("astral_repository:resource_transfer/"+texture,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,262144,false,true,
                CompositeState.builder().setShaderState(new ShaderStateShard(()->shader))
                        .setTextureState(new TextureStateShard(texture,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
    static void flush(MultiBufferSource.BufferSource buffers,boolean energy){buffers.endBatch(energy?POWER:ATLAS);}
    static void render(NetworkPackets.Visual packet,Vec3 position,PoseStack pose,MultiBufferSource buffers,org.joml.Quaternionf camera){render(packet,position,pose,buffers,camera,.16F,1);}
    static void render(NetworkPackets.Visual packet,Vec3 position,PoseStack pose,MultiBufferSource buffers,org.joml.Quaternionf camera,float size,float opacity){
        if(shader==null)return;
        boolean energy=packet.slot()==-3,circle=packet.slot()==-2||energy;
        Sprite sprite=energy?ENERGY_SPRITE:packet.slot()==-2?fluid(packet.fluid()):source();
        var vertices=buffers.getBuffer(energy?POWER:ATLAS);
        int color=(sprite.color&0xffffff)|((int)((sprite.color>>>24)*Math.clamp(opacity,0,1))<<24);
        pose.pushPose();pose.translate(position.x,position.y,position.z);pose.mulPose(camera);
        vertex(vertices,pose,-size,-size,sprite.u0,sprite.v1,color,circle?0:-1,0);
        vertex(vertices,pose,size,-size,sprite.u1,sprite.v1,color,circle?32767:-1,0);
        vertex(vertices,pose,size,size,sprite.u1,sprite.v0,color,circle?32767:-1,32767);
        vertex(vertices,pose,-size,size,sprite.u0,sprite.v0,color,circle?0:-1,32767);
        pose.popPose();
    }
    private static Sprite fluid(ResourceLocation id){
        var key=id==null?ResourceLocation.withDefaultNamespace("water"):id;
        return FLUIDS.computeIfAbsent(key,k->{
            var fluid=net.minecraft.core.registries.BuiltInRegistries.FLUID.get(k);
            var properties=com.cappleapple.astralrepository.platform.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
            var texture=properties.getFlowingTexture();if(texture==null)texture=properties.getStillTexture();
            if(texture==null)texture=MissingTextureAtlasSprite.getLocation();
            var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
            return new Sprite(sprite.getU0(),sprite.getV0(),sprite.getU(.5F),sprite.getV(.5F),properties.getTintColor());
        });
    }
    private static Sprite source(){
        if(source==null){var atlas=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            var sprite=atlas.apply(ResourceLocation.parse("ars_nouveau:block/mana_still"));
            if(sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()))sprite=atlas.apply(ResourceLocation.withDefaultNamespace("block/amethyst_block"));
            source=new Sprite(sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1(),0xffffffff);}
        return source;
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float u,float v,int color,int maskX,int maskY){
        out.addVertex(pose.last().pose(),x,y,0).setColor(color).setUv(u,v).setUv1(maskX,maskY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
    }
    private ResourceTransferRenderer(){super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,0,false,false,()->{},()->{});}
}
