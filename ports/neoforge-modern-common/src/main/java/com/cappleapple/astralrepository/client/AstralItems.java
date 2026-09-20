package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

final class AstralItems {
    private AstralItems() {}
    static void render(ItemStack stack,ItemDisplayContext context,int light,int overlay,PoseStack pose,AstralBufferSource buffers,Level level,int seed){
        var state=new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state,stack,context,level,null,seed);
        var transform=new org.joml.Matrix4f(pose.last().pose());
        buffers.enqueue((parent,collector)->{parent.pushPose();parent.mulPose(transform);state.submit(parent,collector,light,overlay,0);parent.popPose();});
    }
}
