package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralTrimItemModel;
import com.cappleapple.astralrepository.content.AstralTrims;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public abstract class AstralTrimItemMixin {
    @Inject(method="getModel", at=@At("RETURN"), cancellable=true)
    private void astral$trimIcon(ItemStack stack, Level level, LivingEntity wearer, int seed,
            CallbackInfoReturnable<BakedModel> ci) {
        if (AstralTrims.isAstral(stack.get(DataComponents.TRIM)))
            ci.setReturnValue(AstralTrimItemModel.wrap(ci.getReturnValue()));
    }
}
