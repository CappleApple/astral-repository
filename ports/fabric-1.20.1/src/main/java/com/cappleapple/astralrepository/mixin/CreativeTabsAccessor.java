package com.cappleapple.astralrepository.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeTabs.class)
public interface CreativeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void astral$invalidate(CreativeModeTab.ItemDisplayParameters parameters) { throw new AssertionError(); }
}
