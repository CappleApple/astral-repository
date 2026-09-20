package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Read world depth, but never let the coplanar glow and core write depth against each other. */
public final class BindingBeamRenderType extends RenderType {
    public static final RenderType BEAM=create("astral_repository:binding_beam",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL).setOutputState(PARTICLES_TARGET).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    // Replay depth only after the glow/core finish, avoiding coplanar self-occlusion.
    public static final RenderType DEPTH=create("astral_repository:binding_beam_depth",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setCullState(NO_CULL).setOutputState(PARTICLES_TARGET).setWriteMaskState(DEPTH_WRITE).createCompositeState(false));
    private BindingBeamRenderType(String name,com.mojang.blaze3d.vertex.VertexFormat format,VertexFormat.Mode mode,int size,boolean crumbling,boolean sorted,Runnable setup,Runnable clear){super(name,format,mode,size,crumbling,sorted,setup,clear);}
}
