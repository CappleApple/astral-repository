package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.*;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import net.minecraft.client.renderer.feature.submit.BatchableSubmit;
import net.minecraft.client.renderer.rendertype.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;

/** Keeps material state attached to its geometry throughout deferred rendering. */
@EventBusSubscriber(modid="astral_repository",value=Dist.CLIENT)
public final class AstralMaterialFeatureRenderer implements FeatureRenderer<AstralMaterialFeatureRenderer.Submit> {
    public static final FeatureRendererType<Submit> TYPE=FeatureRendererType.create("astral_repository:material");
    public record Submit(PoseStack.Pose pose,RenderType renderType,SubmitNodeCollector.CustomGeometryRenderer geometry,AstralPlaneRenderType.Snapshot snapshot) implements BatchableSubmit {
        @Override public FeatureRendererType<Submit> featureType(){return TYPE;}
        @Override public Object batchKey(){return renderType;}
    }
    private record Draw(PreparedRenderType material,StagedVertexBuffer.Draw vertices,AstralPlaneRenderType.Snapshot snapshot,boolean transfer,PreparedRenderType depth) {}
    private final List<List<Draw>> groups=new ArrayList<>();
    @SubscribeEvent public static void register(RegisterFeatureRenderersEvent event){event.register(TYPE,new AstralMaterialFeatureRenderer());}
    public static void submit(PoseStack pose,SubmitNodeCollector collector,RenderType type,SubmitNodeCollector.CustomGeometryRenderer geometry,AstralPlaneRenderType.Snapshot snapshot){
        if(snapshot==null&&!TransferRenderPass.isTransfer(type)){collector.submitCustomGeometry(pose,type,geometry);return;}
        collector.submitSpecial(type.hasBlending()?RenderPhaseKeys.TRANSLUCENT_CUSTOM_GEOMETRY:RenderPhaseKeys.SOLID,new Submit(pose.last().copy(),type,geometry,snapshot));
    }
    @Override public void prepareGroup(FeatureFrameContext context,List<Submit> submits,boolean ordered){
        var draws=new ArrayList<Draw>();
        for(var submit:submits){
            var type=submit.renderType();
            var draw=context.stagedVertexBuffer().appendDraw(type.format(),type.primitiveTopology(),type.sortOnUpload()?RenderSystem.getProjectionType().vertexSorting():null);
            draws.add(new Draw(type.prepare(),draw,submit.snapshot()==null?null:submit.snapshot().withBobMatrices(),TransferRenderPass.isTransfer(type),type==BindingBeamRenderType.BEAM?BindingBeamRenderType.DEPTH.prepare():null));
            submit.geometry().render(submit.pose(),context.stagedVertexBuffer().getVertexBuilder(draw));
        }
        groups.add(draws);
    }
    @Override public void executeGroup(FeatureFrameContext context,int groupIndex,List<Submit> submits,boolean ordered){
        for(var draw:groups.get(groupIndex)){
            var info=context.stagedVertexBuffer().getExecuteInfo(draw.vertices());
            if(info==null)continue;
            if(draw.transfer()){
                TransferRenderPass.draw(draw.material(),draw.depth(),info);continue;
            }
            var previous=AstralPlaneRenderType.useSnapshot(draw.snapshot());
            try{draw.material().drawFromBuffer(info);}finally{AstralPlaneRenderType.useSnapshot(previous);}
        }
    }
    @Override public void finishExecute(FeatureFrameContext context){groups.clear();TransferRenderPass.clear();}
}
