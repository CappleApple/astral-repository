package com.cappleapple.astralrepository.platform.client.event;
public final class RegisterColorHandlersEvent {
 public static class BlockTintSources {public void register(java.util.List<net.minecraft.client.color.block.BlockTintSource> colors,net.minecraft.world.level.block.Block... blocks){net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(colors,blocks);}}
 public static class ItemTintSources {public void register(net.minecraft.resources.Identifier id,com.mojang.serialization.MapCodec<? extends net.minecraft.client.color.item.ItemTintSource> codec){net.minecraft.client.color.item.ItemTintSources.ID_MAPPER.put(id,codec);}}
}
