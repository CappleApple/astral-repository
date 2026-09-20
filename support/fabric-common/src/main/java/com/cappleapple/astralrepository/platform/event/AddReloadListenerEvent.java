package com.cappleapple.astralrepository.platform.event;
public final class AddReloadListenerEvent {
 public void addListener(net.minecraft.resources.Identifier id,net.minecraft.server.packs.resources.PreparableReloadListener listener){net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.SERVER_DATA).registerReloadListener(id,listener);}
}
