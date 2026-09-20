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
    }
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void astral$endWorld(DeltaTracker ticks, CallbackInfo ci) {
        AstralViewBobbing.endWorld();
    }
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void astral$beginHand(Camera camera, float tick, Matrix4f view, CallbackInfo ci) {
        AstralViewBobbing.beginHand();
    }
    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void astral$endHand(Camera camera, float tick, Matrix4f view, CallbackInfo ci) {
        AstralViewBobbing.endHand();
    }
    @Inject(method = "bobView", at = @At("HEAD"))
    private void astral$beforeBob(PoseStack poses, float tick, CallbackInfo ci) {
        AstralViewBobbing.beforeBob(RenderSystem.getModelViewMatrix(), poses.last().pose());
    }
    @Inject(method = "bobView", at = @At("RETURN"))
    private void astral$afterBob(PoseStack poses, float tick, CallbackInfo ci) {
        AstralViewBobbing.afterBob(RenderSystem.getModelViewMatrix(), poses.last().pose());
    }
}
