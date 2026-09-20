package com.cappleapple.astralrepository.mixin;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.sdl.SDLVideo;

@Mixin(Window.class)
public abstract class TestWindowMixin {
    @Shadow public abstract long handle();
    @Inject(method="<init>",at=@At("RETURN"))
    private void astral$backgroundTestWindow(CallbackInfo ci) {
        if(Boolean.getBoolean("astral_repository.testClient")) {
            SDLVideo.SDL_SetWindowFocusable(handle(),false);
            SDLVideo.SDL_HideWindow(handle());
        }
    }
}
