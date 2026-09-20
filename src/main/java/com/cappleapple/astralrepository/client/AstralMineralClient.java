package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.AstralContent;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;


@EventBusSubscriber(modid = AstralContent.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AstralMineralClient {
    public static final net.minecraft.resources.ResourceLocation GEM_MODEL = com.cappleapple.astralrepository.platform.ClientBackport.standalone(new ResourceLocation(AstralContent.MOD_ID, "item/astral_gem_geometry"));
    public static final net.minecraft.resources.ResourceLocation WAND_MODEL = com.cappleapple.astralrepository.platform.ClientBackport.standalone(new ResourceLocation(AstralContent.MOD_ID, "item/attunement_wand_geometry"));
    public static final net.minecraft.resources.ResourceLocation REMOTE_MODEL = com.cappleapple.astralrepository.platform.ClientBackport.standalone(new ResourceLocation(AstralContent.MOD_ID, "item/astral_nexus_geometry"));
    public static final net.minecraft.resources.ResourceLocation GOGGLES_MODEL = com.cappleapple.astralrepository.platform.ClientBackport.standalone(new ResourceLocation(AstralContent.MOD_ID, "item/astral_goggles_geometry"));
    public static final net.minecraft.resources.ResourceLocation GOGGLES_ICON_MODEL = com.cappleapple.astralrepository.platform.ClientBackport.standalone(new ResourceLocation(AstralContent.MOD_ID, "item/astral_goggles_icon_geometry"));

    public static net.minecraft.resources.ResourceLocation gogglesModel(net.minecraft.world.item.ItemDisplayContext context) {
        return context == net.minecraft.world.item.ItemDisplayContext.HEAD ? GOGGLES_MODEL : GOGGLES_ICON_MODEL;
    }

    @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(AstralContent.MOD_ID, "astral_plane"), DefaultVertexFormat.NEW_ENTITY), AstralPlaneRenderType::loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(AstralContent.MOD_ID, "resource_transfer"), DefaultVertexFormat.NEW_ENTITY), ResourceTransferRenderer::loaded);
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
            event.register(com.cappleapple.astralrepository.platform.ClientBackport.standalone(new ResourceLocation(AstralContent.MOD_ID, "item/" + name + "_geometry")));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(AstralContent.MINERAL_ENTITY.get(), AstralMineralRenderer::new); }
    public static IClientItemExtensions itemExtension() {
        return new IClientItemExtensions(){private AstralMineralItemRenderer renderer;@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new AstralMineralItemRenderer();return renderer;}};
    }
    private AstralMineralClient() {}
}
