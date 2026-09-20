package com.cappleapple.astralrepository.menu;

import com.cappleapple.astralrepository.AstralRepository;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Ordinary player slots; Shift-click is intercepted by the screen to copy a filter. */
public final class RuneSettingsMenu extends AbstractContainerMenu {
    public RuneSettingsMenu(int id, Inventory inventory) {
        super(AstralRepository.RUNE_SETTINGS_MENU.get(), id);
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,9+y*9+x,34+x*18,184+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,34+x*18,242));
    }
    @Override public boolean stillValid(Player player){return true;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
