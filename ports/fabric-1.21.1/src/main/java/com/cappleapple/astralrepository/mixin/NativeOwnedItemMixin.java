package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.crafting.NativeWorldOutputs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class NativeOwnedItemMixin {
    @Inject(method = "discard", at = @At("HEAD"))
    private void astral$nativeConsumedItem(CallbackInfo ignored) {
        if ((Object) this instanceof ItemEntity item) NativeWorldOutputs.discarded(item);
    }
}
