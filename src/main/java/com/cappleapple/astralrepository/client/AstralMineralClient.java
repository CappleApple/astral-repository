package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.AstralContent;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = AstralContent.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AstralMineralClient {
    public static final ModelResourceLocation GEM_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(AstralContent.MOD_ID, "item/astral_gem_geometry"));
    public static final ModelResourceLocation WAND_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(AstralContent.MOD_ID, "item/attunement_wand_geometry"));
    public static final ModelResourceLocation REMOTE_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(AstralContent.MOD_ID, "item/astral_nexus_geometry"));
    public static final ModelResourceLocation GOGGLES_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(AstralContent.MOD_ID, "item/astral_goggles_geometry"));
    public static final ModelResourceLocation GOGGLES_ICON_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(AstralContent.MOD_ID, "item/astral_goggles_icon_geometry"));

    public static ModelResourceLocation gogglesModel(net.minecraft.world.item.ItemDisplayContext context) {
        return context == net.minecraft.world.item.ItemDisplayContext.HEAD ? GOGGLES_MODEL : GOGGLES_ICON_MODEL;
    }

    @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(AstralContent.MOD_ID, "astral_plane"), DefaultVertexFormat.NEW_ENTITY), AstralPlaneRenderType::loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(AstralContent.MOD_ID, "resource_transfer"), DefaultVertexFormat.NEW_ENTITY), ResourceTransferRenderer::loaded);
    }
    @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event) { AstralTrimItemModel.clearCache(); ResourceTransferRenderer.clearCache(); TransferItemSprites.clearCache(); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        event.register(GEM_MODEL);
        event.register(GOGGLES_MODEL);
        event.register(GOGGLES_ICON_MODEL);
        event.register(WAND_MODEL);
        event.register(REMOTE_MODEL);
        for (var node : AstralContent.NODES) event.register(CrystalModelRenderer.modelLocation(node.get()));
        for(String name:java.util.List.of("moon_storage_crystal","star_storage_crystal","remote_crystal","gateway_crystal"))event.register(CrystalModelRenderer.modelLocation(name));
        for (String name : java.util.List.of("astral_geode", "budding_astral", "small_astral_bud", "medium_astral_bud", "large_astral_bud", "astral_cluster"))
            event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(AstralContent.MOD_ID, "item/" + name + "_geometry")));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(AstralContent.MINERAL_ENTITY.get(), AstralMineralRenderer::new); }
    @SubscribeEvent public static void items(RegisterClientExtensionsEvent event) {
        IClientItemExtensions extension = new IClientItemExtensions() {
            private AstralMineralItemRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { if (renderer == null) renderer = new AstralMineralItemRenderer(); return renderer; }
        };
        event.registerItem(extension, AstralContent.ASTRAL_GEODE.get().asItem(), AstralContent.BUDDING_ASTRAL.get().asItem(),
                AstralContent.SMALL_ASTRAL_BUD.get().asItem(), AstralContent.MEDIUM_ASTRAL_BUD.get().asItem(), AstralContent.LARGE_ASTRAL_BUD.get().asItem(),
                AstralContent.ASTRAL_CLUSTER.get().asItem(), AstralContent.ASTRAL_GEM.get(), AstralContent.ATTUNEMENT_WAND.get(), AstralContent.ASTRAL_NEXUS.get(), AstralContent.RESONANCE_GOGGLES.get());
        for (var node : AstralContent.NODES) event.registerItem(extension, node.get().asItem());
    }
    private AstralMineralClient() {}
}

