package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** AE2 owns calculation, resource reservation, CPUs, and production. Tickets observe real crafting links. */
final class Ae2CraftingProvider implements CraftingProvider {
    private static final String SERVICE="appeng.api.networking.crafting.ICraftingService";
    private static final String REQUESTER="appeng.api.networking.crafting.ICraftingRequester";
    private static final String SIMULATION="appeng.api.networking.crafting.ICraftingSimulationRequester";
    private static final String LINK="appeng.api.networking.crafting.ICraftingLink";
    private static final String PLAN="appeng.api.networking.crafting.ICraftingPlan";
    private final ServerLevel level;
    private final String id;
    private final Object grid;
    private final Object service;
    private final Object storage;
    private final BooleanSupplier valid;
    Ae2CraftingProvider(ServerLevel level,String id,Object grid,BooleanSupplier valid) {
        this.level=level; this.id=id; this.grid=grid; this.valid=valid;
        service=OptionalApi.call(grid,"appeng.api.networking.IGrid","getCraftingService");
        Object storageService=OptionalApi.call(grid,"appeng.api.networking.IGrid","getStorageService");
        storage=OptionalApi.call(storageService,"appeng.api.networking.storage.IStorageService","getInventory");
    }
    public String id() { return id; }
    public Object identity() { return grid; }
    public boolean valid() { return valid.getAsBoolean(); }
    public Map<ItemKey,Long> craftableOutputs() {
        if (!valid()) return Map.of();
        Object filter=OptionalApi.call(null,"appeng.api.storage.AEKeyFilter","none");
        Iterable<?> keys=(Iterable<?>)OptionalApi.call(service,SERVICE,"getCraftables",
                new Class<?>[]{OptionalApi.type("appeng.api.storage.AEKeyFilter")},filter);
        Map<ItemKey,Long> result=new LinkedHashMap<>();
        for(Object key:keys) if(OptionalApi.type(Ae2StorageProvider.ITEM).isInstance(key))
            result.put(new ItemKey((ItemStack)OptionalApi.call(key,Ae2StorageProvider.ITEM,"toStack")),1L);
        return Map.copyOf(result);
    }
    public Ticket request(ItemKey output,long amount,CraftingContext context) {
        context.enter("ae2:"+System.identityHashCode(grid));
        if (!valid() || amount<=0) throw new IllegalStateException("AE2 crafting endpoint unavailable");
        return new AeTicket(output,amount,context.requestId());
    }
    private final class AeTicket implements Ticket {
        private final UUID id;
        private final Object source=OptionalApi.call(null,Ae2StorageProvider.SOURCE,"empty");
        private final Object requester;
        private final Future<?> calculation;
        private Object link;
        private State state=State.RUNNING;
        private String message="Calculating AE2 craft";
        AeTicket(ItemKey output,long amount,UUID id) {
            this.id=id;
            Class<?> api=OptionalApi.type(REQUESTER);
            requester=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(proxy,method,args) -> {
                return switch(method.getName()) {
                    case "getRequestedJobs" -> link==null?ImmutableSet.of():ImmutableSet.of(link);
                    case "getActionableNode" -> OptionalApi.call(grid,"appeng.api.networking.IGrid","getPivot");
                    case "jobStateChange" -> null;
                    case "insertCraftedItems" -> valid() && state==State.RUNNING
                            ? OptionalApi.call(storage,Ae2StorageProvider.STORAGE,"insert",
                                new Class<?>[]{OptionalApi.type(Ae2StorageProvider.KEY),long.class,OptionalApi.type(Ae2StorageProvider.ACTION),OptionalApi.type(Ae2StorageProvider.SOURCE)},
                                args[1],args[2],args[3],source) : 0L;
                    case "toString" -> "Astral Repository craft "+id;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy==args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            Class<?> sim=OptionalApi.type(SIMULATION);
            Object simulation=Proxy.newProxyInstance(sim.getClassLoader(),new Class<?>[]{sim},(proxy,method,args) -> switch(method.getName()) {
                case "getActionSource" -> source;
                case "getGridNode" -> OptionalApi.call(grid,"appeng.api.networking.IGrid","getPivot");
                case "toString" -> "Astral Repository calculation "+id;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
            Object key=OptionalApi.call(null,Ae2StorageProvider.ITEM,"of",new Class<?>[]{ItemStack.class},output.sample());
            calculation=(Future<?>)OptionalApi.call(service,SERVICE,"beginCraftingCalculation",
                    new Class<?>[]{Level.class,sim,OptionalApi.type(Ae2StorageProvider.KEY),long.class,OptionalApi.type("appeng.api.networking.crafting.CalculationStrategy")},
                    level,simulation,key,amount,OptionalApi.value("appeng.api.networking.crafting.CalculationStrategy","REPORT_MISSING_ITEMS"));
        }
        public UUID id() { return id; }
        public State state() {
            if(state!=State.RUNNING) return state;
            if(!valid()) { cancel(); message="AE2 endpoint unloaded or disconnected"; return state; }
            if(link==null && calculation.isDone()) {
                try {
                    Object plan=calculation.get();
                    if((Boolean)OptionalApi.call(plan,PLAN,"simulation")) { state=State.FAILED; message="AE2 reports missing ingredients"; return state; }
                    Object submitted=OptionalApi.call(service,SERVICE,"submitJob",
                            new Class<?>[]{OptionalApi.type(PLAN),OptionalApi.type(REQUESTER),OptionalApi.type("appeng.api.networking.crafting.ICraftingCPU"),boolean.class,OptionalApi.type(Ae2StorageProvider.SOURCE)},
                            plan,requester,null,false,source);
                    String contract="appeng.api.networking.crafting.ICraftingSubmitResult";
                    if(!(Boolean)OptionalApi.call(submitted,contract,"successful")) {
                        state=State.FAILED; message="AE2: "+OptionalApi.call(submitted,contract,"errorCode"); return state;
                    }
                    link=OptionalApi.call(submitted,contract,"link");
                    if(link==null) { state=State.FAILED; message="AE2 did not provide a crafting link"; return state; }
                    message="AE2 crafting";
                } catch(Exception failure) { state=State.FAILED; message=failure.getMessage(); return state; }
            }
            if(link!=null) {
                if((Boolean)OptionalApi.call(link,LINK,"isCanceled")) { state=State.CANCELLED; message="AE2 craft cancelled"; }
                else if((Boolean)OptionalApi.call(link,LINK,"isDone")) { state=State.COMPLETE; message="AE2 craft complete"; }
            }
            return state;
        }
        public String message() { return message; }
        public void cancel() {
            if(state!=State.RUNNING) return;
            state=State.CANCELLED;
            calculation.cancel(false);
            if(link!=null) OptionalApi.call(link,LINK,"cancel");
            message="AE2 craft cancelled";
        }
    }
}