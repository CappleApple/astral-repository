package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.RuneLinkGlint;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom foil must survive base-material switches and draw after the source's fixed base buffers. */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class LinkingGlintBufferMixin {
    @Inject(method="getBuffer",at=@At("HEAD"),cancellable=true)
    private void astral$linkingBuffer(RenderType type,CallbackInfoReturnable<VertexConsumer> callback){
        var buffer=RuneLinkGlint.buffer((MultiBufferSource.BufferSource)(Object)this,type);if(buffer!=null)callback.setReturnValue(buffer);
    }
    @Inject(method="endBatch()V",at=@At("TAIL"))
    private void astral$flushLinkingBuffers(CallbackInfo callback){RuneLinkGlint.flush((MultiBufferSource.BufferSource)(Object)this);}
    @Inject(method="endBatch(Lnet/minecraft/client/renderer/RenderType;)V",at=@At("HEAD"),cancellable=true)
    private void astral$flushLinkingBuffer(RenderType type,CallbackInfo callback){if(RuneLinkGlint.flush((MultiBufferSource.BufferSource)(Object)this,type))callback.cancel();}
}
