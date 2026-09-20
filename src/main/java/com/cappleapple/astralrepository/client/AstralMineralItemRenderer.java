package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class AstralMineralItemRenderer extends BlockEntityWithoutLevelRenderer {
    public AstralMineralItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var client = Minecraft.getInstance();
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof com.cappleapple.astralrepository.content.CrystalNodeBlock) {
            var block=(com.cappleapple.astralrepository.content.CrystalNodeBlock)blockItem.getBlock();var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);String upgrade=null;
            if(data!=null){var tag=data.copyTag();upgrade=CrystalModelRenderer.upgradedModel(block.kind(),tag.getInt("StorageTier"),tag.getBoolean("LongRange"),tag.getBoolean("Dimensional"));}
            var model=client.getModelManager().getModel(upgrade==null?CrystalModelRenderer.modelLocation(block):CrystalModelRenderer.modelLocation(upgrade));
            CrystalModelRenderer.render(poses, buffers, null, model, CrystalModelRenderer.channel(stack), light, overlay);
            return;
        }
        boolean goggles = stack.is(com.cappleapple.astralrepository.content.AstralContent.RESONANCE_GOGGLES.get());
        boolean wand = stack.is(com.cappleapple.astralrepository.content.AstralContent.ATTUNEMENT_WAND.get());
        boolean remote = stack.is(com.cappleapple.astralrepository.content.AstralContent.ASTRAL_NEXUS.get());
        var location = goggles ? AstralMineralClient.gogglesModel(context) : wand ? AstralMineralClient.WAND_MODEL : remote ? AstralMineralClient.REMOTE_MODEL : stack.getItem() instanceof BlockItem
                ? net.minecraft.client.resources.model.ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("astral_repository",
                        "item/" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + "_geometry"))
                : AstralMineralClient.GEM_MODEL;
        var model = client.getModelManager().getModel(location);
        var material = AstralPlaneRenderType.ready() ? ((wand || remote || goggles) ? AstralPlaneRenderType.ASTRAL_OVERLAY : stack.is(com.cappleapple.astralrepository.content.AstralContent.ASTRAL_GEM.get()) ? AstralPlaneRenderType.GEM_ITEM : AstralPlaneRenderType.ASTRAL_PLANE) : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        if (!wand && !goggles) {
            var consumer = remote ? net.minecraft.client.renderer.entity.ItemRenderer.getFoilBufferDirect(buffers, material, true, stack.hasFoil()) : buffers.getBuffer(material);
            client.getBlockRenderer().getModelRenderer().renderModel(poses.last(), consumer, null, model, 1, 1, 1, light, overlay);
            return;
        }
        // Tint index 0 marks the wand crystal or goggles lenses; frames retain their base material.
        if (goggles) material = AstralPlaneRenderType.ready() ? AstralPlaneRenderType.GOGGLES_LENS
                : RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        var random = net.minecraft.util.RandomSource.create(42);
        // Submit opaque frames before transparent lenses; lenses never write depth.
        for (int pass = 0; pass < 2; pass++) {
            for (int face = 0; face <= net.minecraft.core.Direction.values().length; face++) {
                var direction = face == net.minecraft.core.Direction.values().length ? null : net.minecraft.core.Direction.values()[face];
                random.setSeed(42);
                for (var quad : model.getQuads(null, direction, random)) {
                    boolean crystal = quad.isTinted() && quad.getTintIndex() == 0;
                    if (crystal != (pass == 1)) continue;
                    var consumer = net.minecraft.client.renderer.entity.ItemRenderer.getFoilBufferDirect(buffers,
                            crystal ? material : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS), true, stack.hasFoil());
                    consumer.putBulkData(poses.last(), quad, 1, 1, 1, goggles && crystal ? 0.55F : 1F, light, overlay);
                }
            }
        }
    }
}


