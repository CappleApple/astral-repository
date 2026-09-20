package com.cappleapple.astralrepository.mixin;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

@Mixin(Window.class)
public abstract class TestWindowMixin {
    @org.spongepowered.asm.mixin.Shadow public abstract long getWindow();
    @Inject(method="<init>",at=@At("RETURN"))
    private void astral$backgroundTestWindow(CallbackInfo ci){
        if(Boolean.getBoolean("astral_repository.testClient")){
            GLFW.glfwSetWindowAttrib(getWindow(),GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
            GLFW.glfwHideWindow(getWindow());
        }
    }
}
