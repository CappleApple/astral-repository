package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Modifier state for wheel interactions, which do not carry key modifiers. */
public final class AstralInput {
    private AstralInput() {}
    private static boolean down(int key) { return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key); }
    public static boolean hasShiftDown() { return down(InputConstants.KEY_LSHIFT) || down(InputConstants.KEY_RSHIFT); }
    public static boolean hasControlDown() { return down(InputConstants.KEY_LCONTROL) || down(InputConstants.KEY_RCONTROL); }
    public static boolean hasAltDown() { return down(InputConstants.KEY_LALT) || down(InputConstants.KEY_RALT); }
    public static int modifiers() { return (hasShiftDown() ? InputConstants.MOD_SHIFT : 0) | (hasControlDown() ? InputConstants.MOD_CONTROL : 0) | (hasAltDown() ? InputConstants.MOD_ALT : 0); }
}
