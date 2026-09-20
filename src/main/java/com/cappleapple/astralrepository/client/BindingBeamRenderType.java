package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;

/** Additive beam color reads world depth without writing depth against overlapping glow. */
public final class BindingBeamRenderType {
    static final RenderPipeline PIPELINE=RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("astral_repository","pipeline/binding_beam"))
        .withVertexShader("core/rendertype_lightning").withFragmentShader("core/rendertype_lightning")
        .withVertexBinding(0,DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false)).build();
    public static final RenderType BEAM=RenderType.create("astral_repository:binding_beam",RenderSetup.builder(PIPELINE).setOutputTarget(TransferRenderPass.TARGET).createRenderSetup());
    static final RenderPipeline DEPTH_PIPELINE=RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("astral_repository","pipeline/binding_beam_depth"))
        .withVertexShader("core/rendertype_lightning").withFragmentShader("core/rendertype_lightning")
        .withVertexBinding(0,DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withColorTargetState(new ColorTargetState(java.util.Optional.empty(),com.mojang.blaze3d.GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_NONE)).withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true)).build();
    static final RenderType DEPTH=RenderType.create("astral_repository:binding_beam_depth",RenderSetup.builder(DEPTH_PIPELINE).setOutputTarget(TransferRenderPass.TARGET).createRenderSetup());
    private BindingBeamRenderType(){}
}
