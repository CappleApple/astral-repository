package com.cappleapple.astralrepository.port.storage;

/** Repository-local simulation facade over transactional storage. */
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
public interface IFluidHandler {
 enum FluidAction {SIMULATE,EXECUTE;public boolean execute(){return this==EXECUTE;}}
 int getTanks();FluidStack getFluidInTank(int tank);int getTankCapacity(int tank);boolean isFluidValid(int tank,FluidStack stack);
 int fill(FluidStack stack,FluidAction action);FluidStack drain(FluidStack stack,FluidAction action);FluidStack drain(int amount,FluidAction action);
 static IFluidHandler of(ResourceHandler<FluidResource> handler){if(handler instanceof IFluidHandler direct)return direct;return new IFluidHandler(){
 public int getTanks(){return handler.size();}public FluidStack getFluidInTank(int tank){return handler.getResource(tank).toStack(handler.getAmountAsInt(tank));}public int getTankCapacity(int tank){return handler.getCapacityAsInt(tank,FluidResource.EMPTY);}public boolean isFluidValid(int tank,FluidStack stack){return !stack.isEmpty()&&handler.isValid(tank,FluidResource.of(stack));}
 public int fill(FluidStack stack,FluidAction action){if(stack.isEmpty())return 0;try(var tx=Transaction.openRoot()){int inserted=0;for(int i=0;i<handler.size()&&inserted<stack.getAmount();i++)inserted+=handler.insert(i,FluidResource.of(stack),stack.getAmount()-inserted,tx);if(action.execute())tx.commit();return inserted;}}
 public FluidStack drain(FluidStack stack,FluidAction action){if(stack.isEmpty())return FluidStack.EMPTY;try(var tx=Transaction.openRoot()){int extracted=0;for(int i=0;i<handler.size()&&extracted<stack.getAmount();i++)extracted+=handler.extract(i,FluidResource.of(stack),stack.getAmount()-extracted,tx);if(action.execute())tx.commit();return stack.copyWithAmount(extracted);}}
 public FluidStack drain(int amount,FluidAction action){if(amount<=0)return FluidStack.EMPTY;for(int i=0;i<handler.size();i++){var fluid=getFluidInTank(i);if(!fluid.isEmpty())return drain(fluid.copyWithAmount(amount),action);}return FluidStack.EMPTY;}
 };}
}
