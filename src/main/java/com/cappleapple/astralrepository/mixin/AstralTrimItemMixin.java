package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralTrimItemModel;
import com.cappleapple.astralrepository.content.AstralTrims;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class AstralTrimItemMixin {
    @Inject(method="appendItemLayers", at=@At("RETURN"))
    private void astral$trimIcon(ItemStackRenderState output, ItemStack stack, ItemDisplayContext context,
            Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        if (AstralTrims.isAstral(stack.get(DataComponents.TRIM))) AstralTrimItemModel.apply(output);
    }
}
