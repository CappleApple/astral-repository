package com.cappleapple.astralrepository.mixin;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Opt-in development clients never acquire the operating-system pointer. */
@Mixin(MouseHandler.class)
public abstract class TestMouseMixin {
    @Inject(method="grabMouse",at=@At("HEAD"),cancellable=true)
    private void astral$leavePointerFree(CallbackInfo ci){if(Boolean.getBoolean("astral_repository.testClient"))ci.cancel();}
}

