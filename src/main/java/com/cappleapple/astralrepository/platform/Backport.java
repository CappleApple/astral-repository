package com.cappleapple.astralrepository.platform;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
public final class Backport {
 public static int clamp(long value,int min,int max){return (int)Math.max(min,Math.min(max,value));}
 public static long clamp(long value,long min,long max){return Math.max(min,Math.min(max,value));}
 public static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
 public static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
 public static CompoundTag customData(ItemStack stack){return stack.hasTag()?stack.getTag().copy():new CompoundTag();}
 public static int itemHash(ItemStack stack){return java.util.Objects.hash(stack.getItem(),stack.getTag());}
 public static final StreamCodec<FriendlyByteBuf,ItemStack> ITEM_CODEC=StreamCodec.of(FriendlyByteBuf::writeItem,FriendlyByteBuf::readItem);
 public static <T> java.util.List<T> reverse(java.util.List<T> input){var result=new java.util.ArrayList<>(input);java.util.Collections.reverse(result);return result;}
 public static net.minecraft.world.inventory.CraftingContainer craftingGrid(java.util.List<ItemStack> items){
  var grid=new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null,-1){public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int i){return ItemStack.EMPTY;}},3,3);
  for(int i=0;i<items.size();i++)grid.setItem(i,items.get(i));return grid;
 }
 public static <K,V> java.util.Map.Entry<K,V> pollFirst(java.util.LinkedHashMap<K,V> map){var iterator=map.entrySet().iterator();var entry=iterator.next();var result=new java.util.AbstractMap.SimpleImmutableEntry<>(entry);iterator.remove();return result;}
 public static int dyeColor(net.minecraft.world.item.DyeColor dye){float[] color=dye.getTextureDiffuseColors();return ((int)(color[0]*255)<<16)|((int)(color[1]*255)<<8)|(int)(color[2]*255);}
}
