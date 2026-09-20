package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;

/** RS1 calculates and executes the task; a delegating task observes its completion and cancellation. */
final class RefinedCraftingProvider implements CraftingProvider {
    private static final String MANAGER="com.refinedmods.refinedstorage.api.autocrafting.ICraftingManager";
    private static final String RESULT="com.refinedmods.refinedstorage.api.autocrafting.task.ICalculationResult";
    private static final String TASK="com.refinedmods.refinedstorage.api.autocrafting.task.ICraftingTask";
    private final String id;
    private final Object network;
    private final Object crafting;
    private final BooleanSupplier valid;
    RefinedCraftingProvider(String id,Object network,BooleanSupplier valid) {
        this.id=id;this.network=network;this.valid=valid;
        crafting=OptionalApi.call(network,RefinedStorageProvider.NETWORK,"getCraftingManager");
    }
    public String id(){return id;}
    public Object identity(){return network;}
    public boolean valid(){return crafting!=null&&valid.getAsBoolean();}
    public Map<ItemKey,Long> craftableOutputs() {
        if(!valid())return Map.of();
        Object cache=OptionalApi.call(network,RefinedStorageProvider.NETWORK,"getItemStorageCache");
        return RefinedStorageProvider.snapshotList(OptionalApi.call(cache,RefinedStorageProvider.CACHE,"getCraftablesList"),true);
    }
    public Ticket request(ItemKey output,long amount,CraftingContext context) {
        context.enter("refinedstorage:"+System.identityHashCode(network));
        if(!valid()||amount<=0)throw new IllegalStateException("Refined Storage crafting endpoint unavailable");
        if(amount>Integer.MAX_VALUE)throw new IllegalArgumentException("Refined Storage 1.x accepts at most 2147483647 items per task");
        return new RsTicket(output,(int)amount,context.requestId());
    }
    private final class RsTicket implements Ticket {
        private final UUID id;
        private UUID taskId;
        private boolean released;
        private State state=State.RUNNING;
        private String message="Refined Storage crafting";
        RsTicket(ItemKey output,int amount,UUID id) {
            this.id=id;
            Object result=OptionalApi.call(crafting,MANAGER,"create",new Class<?>[]{ItemStack.class,int.class},output.sample(),amount);
            if(!(Boolean)OptionalApi.call(result,RESULT,"isOk")) {
                state=State.FAILED;message="Refined Storage: "+OptionalApi.call(result,RESULT,"getType");return;
            }
            Object task=OptionalApi.call(result,RESULT,"getTask");
            if(task==null){state=State.FAILED;message="Refined Storage did not provide a crafting task";return;}
            taskId=(UUID)OptionalApi.call(task,TASK,"getId");
            Class<?> api=OptionalApi.type(TASK);
            Object observed=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {
                    case "toString"->"Astral Repository observer "+id;
                    case "hashCode"->System.identityHashCode(proxy);
                    case "equals"->proxy==args[0];
                    default->throw new UnsupportedOperationException(method.getName());
                };
                try {
                    if(method.getName().equals("onCancelled")) {
                        if(!released){released=true;method.invoke(task,args);}
                        if(state==State.RUNNING){state=State.CANCELLED;message="Refined Storage craft cancelled";}
                        return null;
                    }
                    // RS1 applies queued cancellations before queued additions. A cancellation
                    // before the first network tick must still return reserved ingredients.
                    if(method.getName().equals("update")&&state==State.CANCELLED) {
                        if(!released){released=true;OptionalApi.call(task,TASK,"onCancelled");}
                        return true;
                    }
                    Object value=method.invoke(task,args);
                    if(method.getName().equals("update")&&Boolean.TRUE.equals(value)&&state==State.RUNNING) {
                        state=State.COMPLETE;message="Refined Storage craft complete";
                    }
                    return value;
                } catch(InvocationTargetException failure){throw failure.getCause();}
            });
            OptionalApi.call(crafting,MANAGER,"start",new Class<?>[]{api},observed);
        }
        public UUID id(){return id;}
        public State state() {
            if(state==State.RUNNING&&!valid()){cancel();message="Refined Storage endpoint unloaded or disconnected";}
            return state;
        }
        public String message(){return message;}
        public void cancel() {
            if(state!=State.RUNNING)return;
            state=State.CANCELLED;
            if(taskId!=null)OptionalApi.call(crafting,MANAGER,"cancel",new Class<?>[]{UUID.class},taskId);
            message="Refined Storage craft cancelled";
        }
    }
}
