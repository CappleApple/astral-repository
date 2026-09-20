package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;

/** Submits real RS2 tasks and polls their task IDs. Does not manufacture or mirror outputs. */
final class RefinedCraftingProvider implements CraftingProvider {
    private static final String COMPONENT="com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent";
    private static final String TOKEN="com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken";
    private static final String PREVIEW="com.refinedmods.refinedstorage.api.autocrafting.preview.PreviewProvider";
    private static final String STATUS="com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatusProvider";
    private static final String TASK="com.refinedmods.refinedstorage.api.autocrafting.task.TaskId";
    private final String id;
    private final Object network;
    private final Object crafting;
    private final BooleanSupplier valid;
    RefinedCraftingProvider(String id,Object network,BooleanSupplier valid) {
        this.id=id; this.network=network; this.valid=valid;
        crafting=CompatibilityRegistry.rsComponent(network,COMPONENT);
    }
    public String id() { return id; }
    public Object identity() { return network; }
    public boolean valid() { return crafting!=null && valid.getAsBoolean(); }
    public Map<ItemKey,Long> craftableOutputs() {
        if(!valid()) return Map.of();
        Map<ItemKey,Long> result=new LinkedHashMap<>();
        for(Object key:(Iterable<?>)OptionalApi.call(crafting,COMPONENT,"getOutputs"))
            if(OptionalApi.type(RefinedStorageProvider.ITEM).isInstance(key))
                result.put(new ItemKey((ItemStack)OptionalApi.call(key,RefinedStorageProvider.ITEM,"toItemStack")),1L);
        return Map.copyOf(result);
    }
    public Ticket request(ItemKey output,long amount,CraftingContext context) {
        context.enter("refinedstorage:"+System.identityHashCode(network));
        if(!valid() || amount<=0) throw new IllegalStateException("Refined Storage crafting endpoint unavailable");
        Object api=OptionalApi.field(RefinedStorageProvider.API,"INSTANCE");
        Object factory=OptionalApi.call(api,RefinedStorageProvider.API,"getItemResourceFactory");
        Optional<?> created=(Optional<?>)OptionalApi.call(factory,
                "com.refinedmods.refinedstorage.common.api.support.resource.ResourceFactory","create",new Class<?>[]{ItemStack.class},output.sample());
        if(created.isEmpty()) throw new IllegalArgumentException("Refined Storage rejected item");
        Object key=OptionalApi.call(created.get(),RefinedStorageProvider.AMOUNT,"resource");
        return new RsTicket(key,amount,context.requestId());
    }
    private final class RsTicket implements Ticket {
        private final UUID id;
        private final Object token;
        private final Object listener;
        private boolean completed;
        private Object task;
        private State state=State.RUNNING;
        private String message="Refined Storage crafting";
        private boolean observed;
        private boolean cancelled;
        RsTicket(Object key,long amount,UUID id) {
            this.id=id;
            Class<?> tokenType=OptionalApi.type(TOKEN);
            token=Proxy.newProxyInstance(tokenType.getClassLoader(),new Class<?>[]{tokenType},(proxy,method,args) -> switch(method.getName()) {
                case "isCancelled" -> cancelled;
                case "cancel" -> { cancelled=true; yield null; }
                case "toString" -> "Astral Repository calculation "+id;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
            Class<?> listenerType=OptionalApi.type("com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatusListener");
            listener=Proxy.newProxyInstance(listenerType.getClassLoader(),new Class<?>[]{listenerType},(proxy,method,args) -> {
                if(method.getName().equals("taskStatusChanged") && task!=null) {
                    String contract="com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatus";
                    Object info=OptionalApi.call(args[0],contract,"info");
                    Object current=OptionalApi.call(info,contract+"$TaskInfo","id");
                    if(task.equals(current) && OptionalApi.call(args[0],contract,"state").toString().equals("COMPLETED")) completed=true;
                }
                return switch(method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy==args[0];
                    case "toString" -> "Astral Repository observer "+id;
                    default -> null;
                };
            });
            OptionalApi.call(crafting,STATUS,"addListener",new Class<?>[]{listenerType},listener);
            Optional<?> started=(Optional<?>)OptionalApi.call(crafting,PREVIEW,"startTask",
                    new Class<?>[]{OptionalApi.type(RefinedStorageProvider.RESOURCE),long.class,OptionalApi.type(RefinedStorageProvider.ACTOR),boolean.class,tokenType},
                    key,amount,OptionalApi.field(RefinedStorageProvider.ACTOR,"EMPTY"),false,token);
            if(started.isEmpty()) { state=State.FAILED; message="Refined Storage reports missing resources or unavailable processor"; detach(); }
            else { task=started.get(); observed=true; }
        }
        public UUID id() { return id; }
        public State state() {
            if(state!=State.RUNNING) return state;
            if(completed) { state=State.COMPLETE; message="Refined Storage craft complete"; detach(); return state; }
            if(!valid()) { cancel(); message="Refined Storage endpoint unloaded or disconnected"; return state; }
            for(Object status:(Iterable<?>)OptionalApi.call(crafting,STATUS,"getStatuses")) {
                String contract="com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatus";
                Object info=OptionalApi.call(status,contract,"info");
                Object current=OptionalApi.call(info,contract+"$TaskInfo","id");
                if(!task.equals(current)) continue;
                observed=true;
                String stage=OptionalApi.call(status,contract,"state").toString();
                if(stage.equals("COMPLETED")) { state=State.COMPLETE; message="Refined Storage craft complete"; detach(); }
                return state;
            }
            if(observed) { state=State.FAILED; message="Refined Storage task disappeared before a completion event"; detach(); }
            return state;
        }
        private void detach() {
            OptionalApi.call(crafting,STATUS,"removeListener",new Class<?>[]{OptionalApi.type("com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatusListener")},listener);
        }
        public String message() { return message; }
        public void cancel() {
            if(state!=State.RUNNING) return;
            cancelled=true; state=State.CANCELLED;
            if(task!=null) OptionalApi.call(crafting,STATUS,"cancel",new Class<?>[]{OptionalApi.type(TASK)},task);
            message="Refined Storage craft cancelled";
            detach();
        }
    }
}