package com.cappleapple.astralrepository.platform.items;
import net.minecraft.world.item.ItemStack;
/** Fixed-slot fixture used by the provider contract tests. */
public class ItemStackHandler implements IItemHandler {
 private final ItemStack[] slots;public ItemStackHandler(int size){slots=new ItemStack[size];java.util.Arrays.fill(slots,ItemStack.EMPTY);}public int getSlots(){return slots.length;}public ItemStack getStackInSlot(int slot){return slots[slot];}public void setStackInSlot(int slot,ItemStack stack){slots[slot]=stack;}public int getSlotLimit(int slot){return 64;}public boolean isItemValid(int slot,ItemStack stack){return true;}
 public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){var old=slots[slot];if(!old.isEmpty()&&!ItemStack.isSameItemSameComponents(old,stack))return stack;int n=Math.min(stack.getCount(),Math.min(stack.getMaxStackSize(),getSlotLimit(slot))-old.getCount());if(n<=0)return stack;if(!simulate)slots[slot]=stack.copyWithCount(old.getCount()+n);return stack.copyWithCount(stack.getCount()-n);}
 public ItemStack extractItem(int slot,int amount,boolean simulate){var old=slots[slot];int n=Math.min(Math.max(0,amount),old.getCount());var result=old.copyWithCount(n);if(!simulate)slots[slot]=old.copyWithCount(old.getCount()-n);return result;}
}
