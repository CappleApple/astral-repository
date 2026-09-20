package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.client.GhostIngredientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The Nexus has already drawn its translucent tint behind the native inventory items. */
@Pseudo
@Mixin(targets = "com.cappleapple.bundlednotsiloed.client.ContainerInventoryOverlay", remap = false)
public abstract class BundledInventoryOverlayMixin {
    @Redirect(method = "renderEntries(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphicsExtractor;III)V", remap = false))
    private static void astral$keepSingleNexusHighlight(GuiGraphicsExtractor graphics, int x, int y, int blitOffset) {
        if (!(Minecraft.getInstance().gui.screen() instanceof NexusScreen || Minecraft.getInstance().gui.screen() instanceof GhostIngredientScreen)) {
            graphics.fill(x,y,x+16,y+16,0x80FFFFFF);
        }
    }
    @Redirect(method="mouseButton(II)Z",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;hasShiftDown()Z"),remap=false)
    private static boolean astral$ghostInventoryDoesNotTransfer(){
        return !(Minecraft.getInstance().gui.screen() instanceof GhostIngredientScreen)&&Minecraft.getInstance().hasShiftDown();
    }
}


