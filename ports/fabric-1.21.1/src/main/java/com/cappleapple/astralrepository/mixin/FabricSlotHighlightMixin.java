package com.cappleapple.astralrepository.mixin;
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class)
public abstract class FabricSlotHighlightMixin {
 @org.spongepowered.asm.mixin.injection.Redirect(method="render",at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;III)V"))
 private void astral$highlight(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,int z){if((Object)this instanceof com.cappleapple.astralrepository.client.NexusScreen||(Object)this instanceof com.cappleapple.astralrepository.client.RecipeTomeScreen||(Object)this instanceof com.cappleapple.astralrepository.client.RuneSettingsScreen)return;net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.renderSlotHighlight(graphics,x,y,z);}
}
