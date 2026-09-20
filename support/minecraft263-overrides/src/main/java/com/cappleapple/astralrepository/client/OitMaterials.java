package com.cappleapple.astralrepository.client;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;

/** Matches custom translucent materials to all three vanilla improved-transparency phases. */
public final class OitMaterials {
    private static final Map<RenderPipeline,OitPipelineSet> SETS=new IdentityHashMap<>();
    public static RenderSetup.RenderSetupBuilder setup(RenderPipeline pipeline){
        var result=RenderSetup.builder(pipeline);
        if(pipeline.getShaderDefines().flags().contains("ASTRAL_INTERFACE"))return result;
        if(pipeline.getColorTargetStates().stream().noneMatch(state->state.blendFunction().isPresent()))return result;
        return result.setOitPipelines(SETS.computeIfAbsent(pipeline,OitMaterials::create));
    }
    private static OitPipelineSet create(RenderPipeline pipeline){
        var builder=RenderPipeline.builder().withVertexShader(pipeline.getShaders().get(ShaderType.VERTEX))
                .withFragmentShader(pipeline.getShaders().get(ShaderType.FRAGMENT)).withCull(pipeline.isCull())
                .withPrimitiveTopology(pipeline.getPrimitiveTopology());
        for(int i=0;i<pipeline.getVertexFormatBindings().size();i++)builder.withVertexBinding(i,pipeline.getVertexFormatBinding(i));
        pipeline.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
        pipeline.getShaderDefines().flags().forEach(builder::withShaderDefine);
        pipeline.getShaderDefines().values().forEach((key,value)->builder.withShaderDefine(key,Float.parseFloat(value)));
        if(pipeline.getColorTargetStates().stream().anyMatch(state->state.blendFunction().orElse(null)==BlendFunction.LIGHTNING))builder.withShaderDefine("OIT_ADDITIVE");
        var id=pipeline.getLocation();
        return OitPipelineSet.builder("astral_repository_"+id.getPath(),builder)
                .withDepthBoundsModifier(b->b.withLocation(id.withSuffix("_oit_depth")))
                .withTransmittanceModifier(b->b.withLocation(id.withSuffix("_oit_transmittance")))
                .withAccumulateModifier(b->b.withLocation(id.withSuffix("_oit_accumulate"))).build();
    }
    public static void register(Consumer<RenderPipeline> register){
        setup(ResourceTransferRenderer.ITEM_PIPELINE);
        for(var set:SETS.values()){register.accept(set.depthBoundsPipeline());register.accept(set.transmittancePipeline());register.accept(set.accumulatePipeline());}
    }
    private OitMaterials(){}
}
