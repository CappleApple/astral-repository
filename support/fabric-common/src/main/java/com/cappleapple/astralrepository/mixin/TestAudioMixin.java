package com.cappleapple.astralrepository.mixin;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Opt-in development clients are silent from the first sound request. */
@Mixin(SoundEngine.class)
public abstract class TestAudioMixin {
    @Inject(method="play",at=@At("HEAD"),cancellable=true)
    private void astral$muteTest(SoundInstance sound,CallbackInfoReturnable<SoundEngine.PlayResult> ci){if(Boolean.getBoolean("astral_repository.testClient"))ci.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);}
}


