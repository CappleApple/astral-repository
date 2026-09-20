package com.cappleapple.astralrepository.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerStateAccessor {
    @Invoker("applyTransform") void astral$applyTransform(PoseStack.Pose pose);
    @Accessor("specialRenderer") SpecialModelRenderer<?> astral$specialRenderer();
}
