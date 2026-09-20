package com.cappleapple.astralrepository.platform.energy;
import net.minecraft.core.HolderLookup; import net.minecraft.nbt.*;
public class EnergyStorage implements IEnergyStorage {
 protected int energy; private final int capacity,rate;public EnergyStorage(int capacity){this(capacity,capacity);}public EnergyStorage(int capacity,int rate){this.capacity=capacity;this.rate=rate;}
 public int receiveEnergy(int amount,boolean simulate){int n=Math.min(Math.min(Math.max(amount,0),rate),capacity-energy);if(!simulate)energy+=n;return n;}
 public int extractEnergy(int amount,boolean simulate){int n=Math.min(Math.min(Math.max(amount,0),rate),energy);if(!simulate)energy-=n;return n;}
 public int getEnergyStored(){return energy;}public int getMaxEnergyStored(){return capacity;}public boolean canReceive(){return rate>0;}public boolean canExtract(){return rate>0;}
 public Tag serializeNBT(){return serializeNBT(null);}public void deserializeNBT(Tag tag){deserializeNBT(null,tag);}
 public Tag serializeNBT(HolderLookup.Provider registries){return IntTag.valueOf(energy);}public void deserializeNBT(HolderLookup.Provider registries,Tag tag){energy=com.cappleapple.astralrepository.platform.Backport.clamp(tag instanceof NumericTag n?n.getAsInt():0,0,capacity);}
}
