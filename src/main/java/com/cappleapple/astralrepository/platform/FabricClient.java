package com.cappleapple.astralrepository.platform;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.platform.client.event.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;

public final class FabricClient implements net.fabricmc.api.ClientModInitializer {
    @Override public void onInitializeClient(){
        ConfigRegistration.register("astral_repository",net.neoforged.fml.config.ModConfig.Type.CLIENT,
                com.cappleapple.astralrepository.AstralClientConfig.SPEC,"astral_repository-client.toml");
        com.cappleapple.astralrepository.platform.network.registration.PayloadRegistrar.registerClient();
        AstralClient.screens(new RegisterMenuScreensEvent());
        AstralClient.tooltips(new RegisterClientTooltipComponentFactoriesEvent());
        AstralClient.blockColors(new RegisterColorHandlersEvent.BlockTintSources());
        AstralClient.itemColors(new RegisterColorHandlersEvent.ItemTintSources());
        AstralClient.reload(new AddClientReloadListenersEvent());
        AstralClient.renderers(new EntityRenderersEvent.RegisterRenderers());
        AstralMineralClient.renderers(new EntityRenderersEvent.RegisterRenderers());
        AstralMineralClient.items(new RegisterSpecialModelRendererEvent());
        AstralPlaneRenderType.register(new RegisterRenderPipelinesEvent());
        AstralMaterialFeatureRenderer.register(new RegisterFeatureRenderersEvent());
        com.cappleapple.astralrepository.platform.client.FabricObjModels.register();
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context->{
            AstralMineralClient.models(new ModelEvent.RegisterStandalone(context));
        });
        new AddClientReloadListenersEvent().addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("astral_repository","model_caches"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->AstralMineralClient.baked(new ModelEvent.BakingCompleted()));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
            var event=new ClientTickEvent.Post();WorldVisuals.tick(event);BindingPreviewRenderer.tick(event);PowerNodeVisibilityClient.tick(event);RunePickupClient.tick(event);
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{
            var event=new ClientPlayerNetworkEvent.LoggingOut();RuneRenderer.disconnect(event);BindingPreviewRenderer.logout(event);PowerNodeVisibilityClient.logout(event);
        }));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD.register((level,chunk)->AstralMineralMigration.loaded(new com.cappleapple.astralrepository.platform.event.level.ChunkEvent.Load(level,chunk)));
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION.register(AstralWorldFrame::extract);
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(AstralWorldFrame::submit);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("astral_repository","rune_hud"),(graphics,delta)->RuneRenderer.hud(new RenderGuiEvent.Post(graphics)));
    }
}
