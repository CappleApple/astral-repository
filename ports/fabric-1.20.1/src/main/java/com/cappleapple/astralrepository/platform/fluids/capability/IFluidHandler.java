package com.cappleapple.astralrepository.platform.fluids.capability;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;
public interface IFluidHandler {enum FluidAction {EXECUTE,SIMULATE;public boolean execute(){return this==EXECUTE;}public boolean simulate(){return this==SIMULATE;}}int getTanks();FluidStack getFluidInTank(int tank);int getTankCapacity(int tank);boolean isFluidValid(int tank,FluidStack stack);int fill(FluidStack stack,FluidAction action);FluidStack drain(FluidStack stack,FluidAction action);FluidStack drain(int maxDrain,FluidAction action);}
