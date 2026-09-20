package com.cappleapple.astralrepository.mixin;
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Minecraft.class)
public abstract class FabricAttackMixin {
 @org.spongepowered.asm.mixin.injection.Inject(method="startAttack",at=@org.spongepowered.asm.mixin.injection.At("HEAD"),cancellable=true)
 private void astral$attack(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci){var event=new com.cappleapple.astralrepository.platform.client.event.InputEvent.InteractionKeyMappingTriggered();com.cappleapple.astralrepository.client.RunePickupClient.attack(event);if(event.isCanceled())ci.setReturnValue(false);}
 @org.spongepowered.asm.mixin.injection.Inject(method="continueAttack",at=@org.spongepowered.asm.mixin.injection.At("HEAD"),cancellable=true)
 private void astral$continue(boolean held,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){if(!held)return;var event=new com.cappleapple.astralrepository.platform.client.event.InputEvent.InteractionKeyMappingTriggered();com.cappleapple.astralrepository.client.RunePickupClient.attack(event);if(event.isCanceled())ci.cancel();}
}
