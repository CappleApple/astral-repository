package com.cappleapple.astralrepository.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class AstralWorldMaterial {
    static void begin(Vec3 camera) {
        var rotation=new Quaternionf(Minecraft.getInstance().gameRenderer.mainCamera().rotation()).conjugate();
        AstralPlaneRenderType.beginWorld(new Matrix4f().rotation(rotation),camera);
    }
    private AstralWorldMaterial() {}
}
