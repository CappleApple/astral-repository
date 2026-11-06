package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.crafting.NativeWorldOutputs;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
abstract class NativeItemMergeMixin {
    @Inject(method = "isMergable", at = @At("HEAD"), cancellable = true)
    private void astral$preserveNativeOwnership(CallbackInfoReturnable<Boolean> result) {
        if (NativeWorldOutputs.exclusivelyOwned((ItemEntity)(Object)this)) result.setReturnValue(false);
    }
}
