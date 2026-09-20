package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.client.GhostIngredientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The Nexus has already drawn its translucent tint behind the native inventory items. */
@Pseudo
@Mixin(targets = "com.cappleapple.bundlednotsiloed.client.ContainerInventoryOverlay", remap = false)
public abstract class BundledInventoryOverlayMixin {
    @Redirect(method = "renderEntries(Lnet/minecraft/client/gui/GuiGraphics;II)V", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;III)V", remap = false))
    private static void astral$keepSingleNexusHighlight(GuiGraphics graphics, int x, int y, int blitOffset) {
        if (!(Minecraft.getInstance().screen instanceof NexusScreen || Minecraft.getInstance().screen instanceof GhostIngredientScreen)) {
            AbstractContainerScreen.renderSlotHighlight(graphics, x, y, blitOffset);
        }
    }
    @Redirect(method="mouseButton(II)Z",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;hasShiftDown()Z"),remap=false)
    private static boolean astral$ghostInventoryDoesNotTransfer(){
        return !(Minecraft.getInstance().screen instanceof GhostIngredientScreen)&&net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }
}
