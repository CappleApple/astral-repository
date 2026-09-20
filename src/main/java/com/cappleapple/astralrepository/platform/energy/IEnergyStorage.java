package com.cappleapple.astralrepository.platform.energy;
public interface IEnergyStorage {int receiveEnergy(int amount,boolean simulate);int extractEnergy(int amount,boolean simulate);int getEnergyStored();int getMaxEnergyStored();boolean canExtract();boolean canReceive();}
