package com.cappleapple.astralrepository.platform.capabilities;
import java.util.*;
import java.lang.ref.WeakReference;
import java.util.function.Function;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;
import com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction;
/** Fabric transactions snapshot the actual persisted node state, so aborted external transfers restore it. */
public final class NodeStorages {
 private static final Map<CrystalNodeBlockEntity,WeakReference<ItemStore>> ITEMS=new WeakHashMap<>();
 private static final Map<CrystalNodeBlockEntity,WeakReference<FluidStore>> FLUIDS=new WeakHashMap<>();
 private static final Map<CrystalNodeBlockEntity,WeakReference<EnergyStore>> ENERGY=new WeakHashMap<>();
 public static void register(){
  ItemStorage.SIDED.registerForBlockEntity((node,side)->node.hasInventory()?cached(ITEMS,node,ItemStore::new):null,AstralContent.NODE_ENTITY.get());
  FluidStorage.SIDED.registerForBlockEntity((node,side)->node.hasInventory()?cached(FLUIDS,node,FluidStore::new):null,AstralContent.NODE_ENTITY.get());
  team.reborn.energy.api.EnergyStorage.SIDED.registerForBlockEntity((node,side)->node.hasInventory()?cached(ENERGY,node,EnergyStore::new):null,AstralContent.NODE_ENTITY.get());
 }
 private static <T> T cached(Map<CrystalNodeBlockEntity,WeakReference<T>> cache, CrystalNodeBlockEntity node, Function<CrystalNodeBlockEntity,T> factory){
  var reference=cache.get(node);var storage=reference==null?null:reference.get();
  if(storage==null){storage=factory.apply(node);cache.put(node,new WeakReference<>(storage));}
  return storage;
 }
 public static void clear(){ITEMS.clear();FLUIDS.clear();ENERGY.clear();}
 private static final class ItemStore extends SnapshotParticipant<CompoundTag> implements Storage<ItemVariant>{
  private final CrystalNodeBlockEntity node;ItemStore(CrystalNodeBlockEntity node){this.node=node;}
  protected CompoundTag createSnapshot(){return node.inventory().save(node.getLevel().registryAccess());}protected void readSnapshot(CompoundTag tag){node.inventory().load(tag,node.getLevel().registryAccess());}protected void onFinalCommit(){node.changed();}
  public long insert(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);if(amount==0)return 0;var inventory=node.inventory();long inserted=0;for(int slot=0;slot<inventory.getSlots()&&inserted<amount;slot++){var stack=resource.toStack((int)Math.min(Integer.MAX_VALUE,amount-inserted));var simulated=inventory.insertItem(slot,stack,true);int accepted=stack.getCount()-simulated.getCount();if(accepted>0){updateSnapshots(tx);var remainder=inventory.insertItem(slot,stack,false);inserted+=stack.getCount()-remainder.getCount();}}return inserted;}
  public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);if(amount==0)return 0;var inventory=node.inventory();for(int i=0;i<inventory.getSlots();i++){var stack=inventory.getStackInSlot(i);if(resource.matches(stack)){updateSnapshots(tx);return inventory.extractItem(i,(int)Math.min(Integer.MAX_VALUE,amount),false).getCount();}}return 0;}
  public Iterator<StorageView<ItemVariant>> iterator(){var views=new ArrayList<StorageView<ItemVariant>>();var inventory=node.inventory();for(int i=0;i<inventory.getSlots();i++){var stack=inventory.getStackInSlot(i);if(stack.isEmpty())continue;var resource=ItemVariant.of(stack);views.add(new StorageView<>(){public boolean isResourceBlank(){return false;}public ItemVariant getResource(){return resource;}public long getAmount(){for(int slot=0;slot<inventory.getSlots();slot++){var current=inventory.getStackInSlot(slot);if(resource.matches(current))return current.getCount();}return 0;}public long getCapacity(){return Integer.MAX_VALUE;}public long extract(ItemVariant requested,long max,TransactionContext tx){StoragePreconditions.notBlankNotNegative(requested,max);return resource.equals(requested)?ItemStore.this.extract(requested,max,tx):0;}});}return views.iterator();}
 }
 private static final class FluidStore extends SnapshotParticipant<FluidStack> implements Storage<FluidVariant>,StorageView<FluidVariant>{
  private final CrystalNodeBlockEntity node;FluidStore(CrystalNodeBlockEntity node){this.node=node;}protected FluidStack createSnapshot(){return node.tank().getFluid().copy();}protected void readSnapshot(FluidStack fluid){node.tank().setFluid(fluid);}protected void onFinalCommit(){node.changed();}
  public boolean isResourceBlank(){return node.tank().getFluid().isEmpty();}public FluidVariant getResource(){return node.tank().getFluid().variant();}public long getAmount(){return (long)node.tank().getFluidAmount()*81;}public long getCapacity(){return (long)node.tank().getCapacity()*81;}
  public long insert(FluidVariant fluid,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(fluid,amount);if(amount<81)return 0;var request=new FluidStack(fluid,(int)Math.min(Integer.MAX_VALUE,amount/81));int n=node.tank().fill(request,FluidAction.SIMULATE);if(n>0){updateSnapshots(tx);return (long)node.tank().fill(request,FluidAction.EXECUTE)*81;}return 0;}
  public long extract(FluidVariant fluid,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(fluid,amount);if(amount<81)return 0;var request=new FluidStack(fluid,(int)Math.min(Integer.MAX_VALUE,amount/81));int n=node.tank().drain(request,FluidAction.SIMULATE).getAmount();if(n>0){updateSnapshots(tx);return (long)node.tank().drain(request,FluidAction.EXECUTE).getAmount()*81;}return 0;}
  public Iterator<StorageView<FluidVariant>> iterator(){return List.<StorageView<FluidVariant>>of(this).iterator();}
 }
 private static final class EnergyStore extends SnapshotParticipant<Tag> implements team.reborn.energy.api.EnergyStorage{
  private final CrystalNodeBlockEntity node;EnergyStore(CrystalNodeBlockEntity node){this.node=node;}protected Tag createSnapshot(){return node.energy().serializeNBT(node.getLevel().registryAccess());}protected void readSnapshot(Tag value){node.energy().deserializeNBT(node.getLevel().registryAccess(),value);}protected void onFinalCommit(){node.changed();}
  public long getAmount(){return node.energy().getEnergyStored();}public long getCapacity(){return node.energy().getMaxEnergyStored();}public boolean supportsInsertion(){return node.energy().canReceive();}public boolean supportsExtraction(){return node.energy().canExtract();}
  public long insert(long amount,TransactionContext tx){StoragePreconditions.notNegative(amount);int request=(int)Math.min(Integer.MAX_VALUE,amount);if(node.energy().receiveEnergy(request,true)<=0)return 0;updateSnapshots(tx);return node.energy().receiveEnergy(request,false);}
  public long extract(long amount,TransactionContext tx){StoragePreconditions.notNegative(amount);int request=(int)Math.min(Integer.MAX_VALUE,amount);if(node.energy().extractEnergy(request,true)<=0)return 0;updateSnapshots(tx);return node.energy().extractEnergy(request,false);}
 }
 private NodeStorages(){}
}
