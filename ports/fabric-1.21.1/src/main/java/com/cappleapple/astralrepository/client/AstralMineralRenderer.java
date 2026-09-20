package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.AstralMineralBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

public final class AstralMineralRenderer implements BlockEntityRenderer<AstralMineralBlockEntity> {
    public AstralMineralRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(AstralMineralBlockEntity entity, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var state = entity.getBlockState();
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        var consumer = buffers.getBuffer(AstralPlaneRenderType.ready() ? AstralPlaneRenderType.ASTRAL_PLANE : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        var random = RandomSource.create(42);
        for (Direction side : Direction.values()) {
            if (entity.getLevel() != null && !Block.shouldRenderFace(state, entity.getLevel(), entity.getBlockPos(), side, entity.getBlockPos().relative(side))) continue;
            random.setSeed(42);
            for (var quad : model.getQuads(state, side, random)) consumer.putBulkData(poses.last(), quad, 1, 1, 1, 1, light, overlay);
        }
        random.setSeed(42);
        for (var quad : model.getQuads(state, null, random)) consumer.putBulkData(poses.last(), quad, 1, 1, 1, 1, light, overlay);
    }
}
