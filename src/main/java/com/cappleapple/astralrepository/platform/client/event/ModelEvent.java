package com.cappleapple.astralrepository.platform.client.event;
public final class ModelEvent {public static class BakingCompleted {}public record RegisterAdditional(net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.Context context){public void register(net.minecraft.client.resources.model.ModelResourceLocation model){context.addModels(model.id());}}}
