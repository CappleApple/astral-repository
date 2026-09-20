package com.cappleapple.astralrepository.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemRenderStateAccessor {
    @Accessor("activeLayerCount") int astral$activeLayerCount();
    @Accessor("layers") ItemStackRenderState.LayerRenderState[] astral$layers();
}
