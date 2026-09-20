package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import java.util.*;

public final class CrystalRenderer implements BlockEntityRenderer<CrystalNodeBlockEntity> {
    public CrystalRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(CrystalNodeBlockEntity node,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(node.getLevel()==null)return;long ticks=node.getLevel().getGameTime();
        double orbit=AnimationTime.radians(ticks,partial,.025),bob=AnimationTime.radians(ticks,partial,.06);
        var state = node.getBlockState();
        pose.pushPose();pose.translate(.5,.5,.5);
        switch(state.getValue(CrystalNodeBlock.FACING)){
            case UP->pose.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH->pose.mulPose(Axis.XP.rotationDegrees(90));
            case SOUTH->pose.mulPose(Axis.XP.rotationDegrees(-90));
            case WEST->pose.mulPose(Axis.ZP.rotationDegrees(-90));
            case EAST->pose.mulPose(Axis.ZP.rotationDegrees(90));
            default->{}
        }
        pose.translate(-.5,-.5,-.5);
        String upgrade=CrystalModelRenderer.upgradedModel(node.kind(),node.storageTier(),node.longRange(),node.dimensional());
        var model=upgrade==null?Minecraft.getInstance().getBlockRenderer().getBlockModel(state):Minecraft.getInstance().getModelManager().getModel(CrystalModelRenderer.modelLocation(upgrade));
        CrystalModelRenderer.render(pose, buffers, state, model, node.channel(), light, overlay);
        List<FilterRules.Entry> filters=new ArrayList<>(node.insertionFilter().entries());filters.addAll(node.extractionFilter().entries());
        for(int i=0;i<Math.min(8,filters.size());i++){
            var filter=filters.get(i);ItemStack item=filter.sample();
            if(filter.kind()==FilterRules.Kind.ITEM_TAG){var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,new net.minecraft.resources.ResourceLocation(filter.id()));var candidates=BuiltInRegistries.ITEM.getTag(tag);if(candidates.isPresent()&&candidates.get().size()>0)item=new ItemStack(candidates.get().get(Math.floorMod(Math.floorDiv(ticks,30),candidates.get().size())));}
            if(item.isEmpty())continue;
            double angle=orbit+i*(Math.PI*2)/Math.min(8,filters.size());pose.pushPose();pose.translate(.5+Math.cos(angle)*.55,1.05+Math.sin(bob+i)*.08,.5+Math.sin(angle)*.55);pose.scale(.25f,.25f,.25f);pose.mulPose(Axis.YP.rotationDegrees((float)(-angle*180/Math.PI)));
            Minecraft.getInstance().getItemRenderer().renderStatic(item,ItemDisplayContext.GROUND,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,buffers,node.getLevel(),i);pose.popPose();
        }
        pose.popPose();
    }
}

