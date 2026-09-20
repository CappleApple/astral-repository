package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.AstralMineralBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class AstralMineralRenderer implements BlockEntityRenderer<AstralMineralBlockEntity,AstralMineralRenderer.State> {
    public static final class State extends BlockEntityRenderState { AstralBufferSource geometry; }
    public AstralMineralRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(AstralMineralBlockEntity entity,State target,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(entity,target,partial,camera,breaking);
        AstralWorldMaterial.begin(camera);try {
        target.geometry=new AstralBufferSource();var pose=new PoseStack();var state=entity.getBlockState();var model=AstralModels.block(state);
        var consumer=target.geometry.getBuffer(AstralPlaneRenderType.ASTRAL_PLANE);var random=RandomSource.create(42);
        for(Direction side:Direction.values()){
            if(entity.getLevel()!=null&&!Block.shouldRenderFace(state,entity.getLevel().getBlockState(entity.getBlockPos().relative(side)),side))continue;
            for(var quad:model.getQuads(state,side,random))consumer.putBulkData(pose.last(),quad,1,1,1,1,target.lightCoords,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
        for(var quad:model.getQuads(state,null,random))consumer.putBulkData(pose.last(),quad,1,1,1,1,target.lightCoords,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        target.geometry.freeze();
        } finally { AstralPlaneRenderType.endWorld(); }
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){state.geometry.submit(pose,collector);}
}
