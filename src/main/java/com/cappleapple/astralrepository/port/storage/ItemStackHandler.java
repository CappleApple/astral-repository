package com.cappleapple.astralrepository.port.storage;

/** Repository-local simulation facade over transactional storage. */
import net.minecraft.world.item.ItemStack;
public class ItemStackHandler implements IItemHandler {
 private final ItemStack[] stacks;
 public ItemStackHandler(int size){stacks=new ItemStack[size];java.util.Arrays.fill(stacks,ItemStack.EMPTY);}
 public int getSlots(){return stacks.length;} public ItemStack getStackInSlot(int slot){return stacks[slot];}
 public void setStackInSlot(int slot,ItemStack stack){stacks[slot]=stack;onContentsChanged(slot);}
 protected void onContentsChanged(int slot){} public int getSlotLimit(int slot){return 64;} public boolean isItemValid(int slot,ItemStack stack){return true;}
 public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){if(stack.isEmpty()||!isItemValid(slot,stack))return stack;var present=stacks[slot];if(!present.isEmpty()&&!ItemStack.isSameItemSameComponents(present,stack))return stack;int amount=Math.min(stack.getCount(),Math.min(getSlotLimit(slot),stack.getMaxStackSize())-present.getCount());if(amount<=0)return stack;if(!simulate){stacks[slot]=stack.copyWithCount(present.getCount()+amount);onContentsChanged(slot);}return stack.copyWithCount(stack.getCount()-amount);}
 public ItemStack extractItem(int slot,int amount,boolean simulate){var present=stacks[slot];if(present.isEmpty()||amount<=0)return ItemStack.EMPTY;int count=Math.min(amount,present.getCount());var result=present.copyWithCount(count);if(!simulate){stacks[slot]=present.copyWithCount(present.getCount()-count);onContentsChanged(slot);}return result;}
}
