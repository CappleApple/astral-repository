package com.cappleapple.astralrepository.platform.items;
import net.minecraft.world.item.ItemStack;
public final class ItemHandlerHelper {
 public static ItemStack insertItem(IItemHandler handler,ItemStack stack,boolean simulate){var rest=stack.copy();for(int i=0;i<handler.getSlots()&&!rest.isEmpty();i++)rest=handler.insertItem(i,rest,simulate);return rest;}
 public static boolean canItemStacksStack(ItemStack a,ItemStack b){return ItemStack.isSameItemSameTags(a,b);}
 public static void giveItemToPlayer(net.minecraft.world.entity.player.Player player,ItemStack stack){if(!player.getInventory().add(stack))player.drop(stack,false);}
}
