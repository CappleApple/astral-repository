package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.NetworkPackets;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.rendertype.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Four vertices per sprite; the fragment shader crops and feathers the circle. */
final class ResourceTransferRenderer  {
    private record Sprite(float u0,float v0,float u1,float v1,int color) {}
    private static final Map<Identifier,Sprite> FLUIDS=new HashMap<>();
    private static Sprite source;
    private static final Sprite ENERGY_SPRITE=new Sprite(0,0,1,1,0xffffd65c);
    static final RenderPipeline PIPELINE=RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("astral_repository","pipeline/resource_transfer"))
        .withVertexShader(Identifier.fromNamespaceAndPath("astral_repository","core/resource_transfer"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("astral_repository","core/resource_transfer"))
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0,DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    static final RenderPipeline ITEM_PIPELINE=RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("astral_repository","pipeline/item_transfer"))
        .withVertexShader("core/entity").withFragmentShader("core/entity")
        .withShaderDefine("EMISSIVE").withShaderDefine("NO_OVERLAY").withShaderDefine("NO_CARDINAL_LIGHTING")
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0).withVertexBinding(0,DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    private static final Map<Identifier,RenderType> ITEM_MATERIALS=new HashMap<>();
    static RenderType itemMaterial(Identifier atlas){return ITEM_MATERIALS.computeIfAbsent(atlas,key->RenderType.create("astral_repository:item_transfer/"+key,RenderSetup.builder(ITEM_PIPELINE).withTexture("Sampler0",key).sortOnUpload().createRenderSetup()));}
    private static final Identifier ENERGY=Identifier.withDefaultNamespace("textures/particle/glow.png");
    private static final RenderType ATLAS=material(TextureAtlas.LOCATION_BLOCKS), POWER=material(ENERGY);
    static void clearCache(){FLUIDS.clear();source=null;}
    static boolean ready(){return AstralPlaneRenderType.ready();}
    private static RenderType material(Identifier texture){
        return RenderType.create("astral_repository:resource_transfer/"+texture,
            RenderSetup.builder(PIPELINE).withTexture("Sampler0",texture).sortOnUpload().createRenderSetup());
    }
    static void flush(AstralBufferSource buffers,boolean energy){buffers.endBatch(energy?POWER:ATLAS);}
    static void render(NetworkPackets.Visual packet,Vec3 position,PoseStack pose,AstralBufferSource buffers,org.joml.Quaternionf camera){render(packet,position,pose,buffers,camera,.16F,1);}
    static void render(NetworkPackets.Visual packet,Vec3 position,PoseStack pose,AstralBufferSource buffers,org.joml.Quaternionf camera,float size,float opacity){
        if(!ready())return;
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
    private static Sprite fluid(Identifier id){
        var key=id==null?Identifier.withDefaultNamespace("water"):id;
        return FLUIDS.computeIfAbsent(key,k->{
            var fluid=net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(k);
            var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            var sprite=model.flowingMaterial().sprite();
            int tint=net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering.getColor(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid));
            return new Sprite(sprite.getU0(),sprite.getV0(),sprite.getU(.5F),sprite.getV(.5F),tint);
        });
    }
    private static Sprite source(){
        if(source==null){var atlas=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
            var sprite=atlas.getSprite(Identifier.parse("ars_nouveau:block/mana_still"));
            if(sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()))sprite=atlas.getSprite(Identifier.withDefaultNamespace("block/amethyst_block"));
            source=new Sprite(sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1(),0xffffffff);}
        return source;
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float u,float v,int color,int maskX,int maskY){
        out.addVertex(pose.last().pose(),x,y,0).setColor(color).setUv(u,v).setUv1(maskX,maskY).setLight(0xF000F0).setNormal(pose.last(),0,0,1);
    }
    private ResourceTransferRenderer() {}
}
