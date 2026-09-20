package com.cappleapple.astralrepository.port.storage;

/** Repository-local simulation facade over transactional storage. */
public class EnergyStorage implements IEnergyStorage {
 protected int energy,capacity,maxReceive,maxExtract;
 public EnergyStorage(int capacity){this(capacity,capacity);}
 public EnergyStorage(int capacity,int transfer){this.capacity=capacity;maxReceive=maxExtract=transfer;}
 public int receiveEnergy(int amount,boolean simulate){int accepted=Math.max(0,Math.min(amount,Math.min(capacity-energy,maxReceive)));if(!simulate)energy+=accepted;return accepted;}
 public int extractEnergy(int amount,boolean simulate){int extracted=Math.max(0,Math.min(amount,Math.min(energy,maxExtract)));if(!simulate)energy-=extracted;return extracted;}
 public int getEnergyStored(){return energy;} public int getMaxEnergyStored(){return capacity;} public boolean canExtract(){return maxExtract>0;}public boolean canReceive(){return maxReceive>0;}
}
