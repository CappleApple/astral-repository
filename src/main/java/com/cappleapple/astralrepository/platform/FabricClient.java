package com.cappleapple.astralrepository.platform;
import com.cappleapple.astralrepository.client.*;import com.cappleapple.astralrepository.content.*;import com.cappleapple.astralrepository.platform.client.event.*;import net.fabricmc.fabric.api.client.rendering.v1.*;
public final class FabricClient implements net.fabricmc.api.ClientModInitializer {
 @Override public void onInitializeClient(){
  io.github.fabricators_of_create.porting_lib.config.ConfigRegistry.registerConfig("astral_repository",io.github.fabricators_of_create.porting_lib.config.ModConfig.Type.CLIENT,com.cappleapple.astralrepository.AstralClientConfig.SPEC,"astral_repository-client.toml");
  com.cappleapple.astralrepository.platform.network.registration.PayloadRegistrar.registerClient();
  com.cappleapple.astralrepository.compat.TrinketsGogglesClient.register();
  AstralClient.screens(new RegisterMenuScreensEvent());AstralClient.tooltips(new RegisterClientTooltipComponentFactoriesEvent());AstralClient.blockColors(new RegisterColorHandlersEvent.Block());AstralClient.itemColors(new RegisterColorHandlersEvent.Item());AstralClient.reload(new RegisterClientReloadListenersEvent());AstralClient.renderers(new EntityRenderersEvent.RegisterRenderers());AstralMineralClient.renderers(new EntityRenderersEvent.RegisterRenderers());AstralMineralClient.items(new com.cappleapple.astralrepository.platform.client.extensions.common.RegisterClientExtensionsEvent());
  CoreShaderRegistrationCallback.EVENT.register(AstralMineralClient::shaders);
  net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context->{AstralMineralClient.models(new ModelEvent.RegisterAdditional(context));context.modifyModelAfterBake().register((model,ctx)->{AstralMineralClient.baked(new ModelEvent.BakingCompleted());return model;});});
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{var event=new ClientTickEvent.Post();WorldVisuals.tick(event);BindingPreviewRenderer.tick(event);PowerNodeVisibilityClient.tick(event);RunePickupClient.tick(event);});
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{var event=new ClientPlayerNetworkEvent.LoggingOut();RuneRenderer.disconnect(event);BindingPreviewRenderer.logout(event);PowerNodeVisibilityClient.logout(event);}));
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD.register((level,chunk)->AstralMineralMigration.loaded(new com.cappleapple.astralrepository.platform.event.level.ChunkEvent.Load(level,chunk)));
  WorldRenderEvents.START.register(context->WorldVisuals.astralCoordinates(new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_SKY,context)));
  WorldRenderEvents.AFTER_ENTITIES.register(context->RuneRenderer.render(new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES,context)));
  WorldRenderEvents.AFTER_TRANSLUCENT.register(context->{var event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_PARTICLES,context);WorldVisuals.render(event);BindingPreviewRenderer.render(event);});
  WorldRenderEvents.END.register(context->WorldVisuals.astralCoordinates(new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_LEVEL,context)));
  HudRenderCallback.EVENT.register((graphics,delta)->RuneRenderer.hud(new RenderGuiEvent.Post(graphics)));
 }
}
