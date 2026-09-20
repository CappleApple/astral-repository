package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.AstralContent;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=AstralContent.MOD_ID,value=Dist.CLIENT)
public final class AstralMineralClient {
    private static AstralModels.Key key(String name){return AstralModels.Key.standalone(Identifier.fromNamespaceAndPath(AstralContent.MOD_ID,"item/"+name+"_geometry"));}
    public static final AstralModels.Key GEM_MODEL=key("astral_gem"), WAND_MODEL=key("attunement_wand"), REMOTE_MODEL=key("astral_nexus"), GOGGLES_MODEL=key("astral_goggles"), GOGGLES_ICON_MODEL=key("astral_goggles_icon");
    public static AstralModels.Key gogglesModel(net.minecraft.world.item.ItemDisplayContext context){return context==net.minecraft.world.item.ItemDisplayContext.HEAD?GOGGLES_MODEL:GOGGLES_ICON_MODEL;}
    @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event){AstralTrimItemModel.clearCache();ResourceTransferRenderer.clearCache();TransferItemSprites.clearCache();}
    @SubscribeEvent public static void models(ModelEvent.RegisterStandalone event){
        for(var model:java.util.List.of(GEM_MODEL,WAND_MODEL,REMOTE_MODEL,GOGGLES_MODEL,GOGGLES_ICON_MODEL))AstralModels.register(event,model);
        for(var node:AstralContent.NODES)AstralModels.register(event,CrystalModelRenderer.modelLocation(node.get()));
        for(String name:java.util.List.of("moon_storage_crystal","star_storage_crystal","remote_crystal","gateway_crystal","astral_geode","budding_astral","small_astral_bud","medium_astral_bud","large_astral_bud","astral_cluster"))AstralModels.register(event,key(name));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(AstralContent.MINERAL_ENTITY.get(),AstralMineralRenderer::new);}
    @SubscribeEvent public static void items(RegisterSpecialModelRendererEvent event){event.register(Identifier.fromNamespaceAndPath(AstralContent.MOD_ID,"astral_mineral"),AstralMineralItemRenderer.Unbaked.CODEC);}
    private AstralMineralClient(){}
}
