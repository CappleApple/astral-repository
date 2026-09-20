package com.cappleapple.astralrepository.platform.items.wrapper;
import com.cappleapple.astralrepository.platform.items.IItemHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
public final class PlayerMainInvWrapper implements IItemHandler {
 private final Inventory inventory; public PlayerMainInvWrapper(Inventory inventory){this.inventory=inventory;}
 public int getSlots(){return inventory.items.size();} public ItemStack getStackInSlot(int slot){return inventory.items.get(slot);}
 public int getSlotLimit(int slot){return inventory.getMaxStackSize();} public boolean isItemValid(int slot,ItemStack stack){return true;}
 public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){var old=getStackInSlot(slot);if(!old.isEmpty()&&!ItemStack.isSameItemSameComponents(old,stack))return stack;int n=Math.min(stack.getCount(),Math.min(stack.getMaxStackSize(),getSlotLimit(slot))-old.getCount());if(n<=0)return stack;if(!simulate){inventory.setItem(slot,stack.copyWithCount(old.getCount()+n));inventory.setChanged();}return stack.copyWithCount(stack.getCount()-n);}
 public ItemStack extractItem(int slot,int amount,boolean simulate){var old=getStackInSlot(slot);int n=Math.min(Math.max(0,amount),old.getCount());var result=old.copyWithCount(n);if(!simulate){inventory.removeItem(slot,n);inventory.setChanged();}return result;}
}
