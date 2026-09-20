package com.cappleapple.astralrepository.port.storage;

/** Repository-local simulation facade over transactional storage. */
import net.minecraft.world.item.ItemStack;
public final class ItemHandlerHelper {
 public static ItemStack insertItem(IItemHandler handler,ItemStack stack,boolean simulate){var remainder=stack;for(int slot=0;slot<handler.getSlots()&&!remainder.isEmpty();slot++)remainder=handler.insertItem(slot,remainder,simulate);return remainder;}
 private ItemHandlerHelper(){}
}
