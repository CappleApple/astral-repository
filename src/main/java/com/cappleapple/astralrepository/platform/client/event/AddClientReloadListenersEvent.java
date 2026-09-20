package com.cappleapple.astralrepository.platform.client.event;
public final class AddClientReloadListenersEvent {public void addListener(net.minecraft.resources.Identifier id,net.minecraft.server.packs.resources.PreparableReloadListener listener){net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(id,listener);}}
