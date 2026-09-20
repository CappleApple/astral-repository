package com.cappleapple.astralrepository.port.storage;

/** Repository-local simulation facade over transactional storage. */
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
public interface IEnergyStorage {
 int receiveEnergy(int amount,boolean simulate);int extractEnergy(int amount,boolean simulate);int getEnergyStored();int getMaxEnergyStored();boolean canExtract();boolean canReceive();
 static IEnergyStorage of(EnergyHandler handler){if(handler instanceof IEnergyStorage direct)return direct;return new IEnergyStorage(){
 public int getEnergyStored(){return handler.getAmountAsInt();}public int getMaxEnergyStored(){return handler.getCapacityAsInt();}public boolean canExtract(){return true;}public boolean canReceive(){return true;}
 public int receiveEnergy(int amount,boolean simulate){if(amount<=0)return 0;try(var tx=Transaction.openRoot()){int accepted=handler.insert(amount,tx);if(!simulate)tx.commit();return accepted;}}
 public int extractEnergy(int amount,boolean simulate){if(amount<=0)return 0;try(var tx=Transaction.openRoot()){int extracted=handler.extract(amount,tx);if(!simulate)tx.commit();return extracted;}}
 };}
}
