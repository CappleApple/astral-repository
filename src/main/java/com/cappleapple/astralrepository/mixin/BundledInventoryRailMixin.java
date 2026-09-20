package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.client.AstralInterfaceRenderer;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.client.GhostIngredientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changes only BNS's attached background while the local or remote Nexus is open. */
@Pseudo
@Mixin(targets = "com.cappleapple.bundlednotsiloed.client.InventorySideRail$Rail", remap = false)
public abstract class BundledInventoryRailMixin {
    @Unique private static final Identifier astral$tabTexture = Identifier.fromNamespaceAndPath(
            AstralRepository.MOD_ID, "textures/gui/bundled_tab.png");

    @Shadow(remap = false) public abstract int left();
    @Shadow(remap = false) public abstract int top();
    @Shadow(remap = false) public abstract int right();
    @Shadow(remap = false) public abstract int bottom();

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void astral$renderNexusTab(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        if (!(Minecraft.getInstance().gui.screen() instanceof NexusScreen || Minecraft.getInstance().gui.screen() instanceof GhostIngredientScreen)) return;
        AstralInterfaceRenderer.blit(graphics, astral$tabTexture, left(), top(), right() - left(), bottom() - top());
        callback.cancel();
    }
}


