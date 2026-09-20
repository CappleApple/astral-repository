package com.cappleapple.astralrepository.port.storage;

/** Repository-local simulation facade over transactional storage. */
import net.neoforged.neoforge.fluids.FluidStack;
public class FluidTank implements IFluidHandler {
 protected FluidStack fluid=FluidStack.EMPTY;protected final int capacity;
 public FluidTank(int capacity){this.capacity=capacity;}protected void onContentsChanged(){}
 public FluidStack getFluid(){return fluid;}public int getFluidAmount(){return fluid.getAmount();}public void setFluid(FluidStack fluid){this.fluid=fluid.copy();onContentsChanged();}
 public boolean isFluidValid(FluidStack fluid){return true;}
 public int getTanks(){return 1;}public FluidStack getFluidInTank(int tank){java.util.Objects.checkIndex(tank,1);return fluid;}public int getTankCapacity(int tank){java.util.Objects.checkIndex(tank,1);return capacity;}public boolean isFluidValid(int tank,FluidStack stack){java.util.Objects.checkIndex(tank,1);return isFluidValid(stack);}
 public int fill(FluidStack stack,FluidAction action){if(stack.isEmpty()||!isFluidValid(stack)||!fluid.isEmpty()&&!FluidStack.isSameFluidSameComponents(fluid,stack))return 0;int amount=Math.min(stack.getAmount(),capacity-fluid.getAmount());if(amount>0&&action.execute()){fluid=stack.copyWithAmount(fluid.getAmount()+amount);onContentsChanged();}return amount;}
 public FluidStack drain(FluidStack stack,FluidAction action){return !stack.isEmpty()&&FluidStack.isSameFluidSameComponents(fluid,stack)?drain(stack.getAmount(),action):FluidStack.EMPTY;}
 public FluidStack drain(int amount,FluidAction action){if(amount<=0||fluid.isEmpty())return FluidStack.EMPTY;var result=fluid.copyWithAmount(Math.min(amount,fluid.getAmount()));if(action.execute()){fluid=fluid.copyWithAmount(fluid.getAmount()-result.getAmount());onContentsChanged();}return result;}
}
