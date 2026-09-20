package com.cappleapple.astralrepository.port.storage;

/** Repository-local simulation facade over transactional storage. */
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
public interface IItemHandler {
 int getSlots(); ItemStack getStackInSlot(int slot); int getSlotLimit(int slot); boolean isItemValid(int slot,ItemStack stack);
 ItemStack insertItem(int slot,ItemStack stack,boolean simulate); ItemStack extractItem(int slot,int amount,boolean simulate);
 static IItemHandler of(ResourceHandler<ItemResource> handler){
  if(handler instanceof IItemHandler direct)return direct;
  return new IItemHandler(){
   public int getSlots(){return handler.size();} public ItemStack getStackInSlot(int slot){return handler.getResource(slot).toStack(handler.getAmountAsInt(slot));}
   public int getSlotLimit(int slot){return handler.getCapacityAsInt(slot,ItemResource.EMPTY);}
   public boolean isItemValid(int slot,ItemStack stack){return !stack.isEmpty()&&handler.isValid(slot,ItemResource.of(stack));}
   public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){if(stack.isEmpty())return ItemStack.EMPTY;try(var tx=Transaction.openRoot()){int count=handler.insert(slot,ItemResource.of(stack),stack.getCount(),tx);if(!simulate)tx.commit();return stack.copyWithCount(stack.getCount()-count);}}
   public ItemStack extractItem(int slot,int amount,boolean simulate){if(amount<=0)return ItemStack.EMPTY;var resource=handler.getResource(slot);if(resource.isEmpty())return ItemStack.EMPTY;try(var tx=Transaction.openRoot()){int count=handler.extract(slot,resource,amount,tx);if(!simulate)tx.commit();return resource.toStack(count);}}
  };
 }
}
