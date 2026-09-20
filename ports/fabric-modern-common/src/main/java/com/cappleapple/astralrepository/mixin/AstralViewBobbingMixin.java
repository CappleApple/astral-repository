package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralViewBobbing;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class AstralViewBobbingMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void astral$beginWorld(DeltaTracker ticks, CallbackInfo ci) {
        AstralViewBobbing.beginWorld();
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        com.cappleapple.astralrepository.client.AstralPlaneRenderType.beginWorld(new Matrix4f(camera.viewRotationMatrix),camera.pos);
    }
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void astral$endWorld(DeltaTracker ticks, CallbackInfo ci) {
        AstralViewBobbing.endWorld();
        com.cappleapple.astralrepository.client.AstralPlaneRenderType.endWorld();
    }
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void astral$beginHand(net.minecraft.client.renderer.state.level.CameraRenderState camera, float tick, org.joml.Matrix4fc view, CallbackInfo ci) {
        AstralViewBobbing.beginHand();
    }
    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void astral$endHand(net.minecraft.client.renderer.state.level.CameraRenderState camera, float tick, org.joml.Matrix4fc view, CallbackInfo ci) {
        AstralViewBobbing.endHand();
    }
    @Inject(method = "bobView", at = @At("HEAD"))
    private void astral$beforeBob(net.minecraft.client.renderer.state.level.CameraRenderState camera, PoseStack poses, CallbackInfo ci) {
        AstralViewBobbing.beforeBob(RenderSystem.getModelViewStack(), poses.last().pose());
    }
    @Inject(method = "bobView", at = @At("RETURN"))
    private void astral$afterBob(net.minecraft.client.renderer.state.level.CameraRenderState camera, PoseStack poses, CallbackInfo ci) {
        AstralViewBobbing.afterBob(RenderSystem.getModelViewStack(), poses.last().pose());
    }
}
