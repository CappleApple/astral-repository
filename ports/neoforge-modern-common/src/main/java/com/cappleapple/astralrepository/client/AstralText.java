package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

final class AstralText {
    static void draw(String text, float x, float y, int color, boolean shadow, PoseStack pose, AstralBufferSource buffers,
            Font.DisplayMode mode, int background, int light) {
        var transform = new Matrix4f(pose.last().pose());
        var sequence = Component.literal(text).getVisualOrderText();
        buffers.enqueue((parent, collector) -> {
            parent.pushPose(); parent.mulPose(transform);
            collector.submitText(parent, x, y, sequence, shadow, mode, light, color, background, 0);
            parent.popPose();
        });
    }
    private AstralText() {}
}
