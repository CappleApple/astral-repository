package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.content.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import static com.cappleapple.astralrepository.api.PowerPolicy.Component.*;

/** Known fuel becomes node credit before payment. A failed multi-resource preflight never spends that credit. */
public final class NetworkPower implements AutoCloseable {
    private record Lease(NetworkPowerProvider provider,double amount) { }
    private record Supply(String id,Object identity,String kind,NetworkAnchor node,DoubleUnaryOperator simulate,DoubleUnaryOperator consume) { }
    private record Credit(NetworkAnchor node,String kind) { }
    private final AstralNetwork network;
    private final List<Lease> leases=new ArrayList<>();
    private final Set<String> reportedFailures=new HashSet<>();
    private final Map<String,StorageProvider> storageViews=new HashMap<>();
    NetworkPower(AstralNetwork network) { this.network=network; }
    public boolean upkeep() {
        close();
        if(!AstralConfig.powerEnabled.get()) return true;
        var quantities=Map.of(BASE,1D,NODES,(double)network.nodes().size(),STORAGE_PROVIDERS,(double)network.storageCount(),
                INDEXED_CAPACITY,AstralConfig.capacityCost.get()==0?0D:(double)network.capacity(),
                CRAFTING_JOBS,(double)network.crafting().activeJobs(),PROCESSORS,(double)network.crafting().activeProcessors(),
                DIMENSIONAL_LINKS,(double)Math.max(0,network.nodes().stream().map(n -> n.getLevel().dimension()).distinct().count()-1));
        var rates=Map.of(BASE,AstralConfig.baseCost.get(),NODES,AstralConfig.nodeCost.get(),STORAGE_PROVIDERS,AstralConfig.storageCost.get(),
                INDEXED_CAPACITY,AstralConfig.capacityCost.get(),CRAFTING_JOBS,AstralConfig.jobCost.get(),PROCESSORS,AstralConfig.processorCost.get(),
                DIMENSIONAL_LINKS,AstralConfig.dimensionalCost.get());
        return pay(PowerPolicy.cost(quantities,rates),true);
    }
    public boolean operation(double cost) { return !AstralConfig.powerEnabled.get() || pay(cost,false); }
    private boolean pay(double amount,boolean retainCapacity) {
        if(!Double.isFinite(amount)||amount<0) return false;
        double needed=Math.ceil(amount);
        if(needed==0) return true;
        List<NetworkAnchor> nodes=network.nodes().stream().filter(n -> n.kind()==NodeKind.POWER&&!n.isRemoved())
                .sorted(Comparator.comparing(n -> NetworkManager.id(NetworkManager.location(n)))).toList();
        List<Supply> supplies=new ArrayList<>();
        List<NetworkPowerProvider> capacities=new ArrayList<>();
        Set<List<Object>> seen=new HashSet<>();
        for(var node:nodes) {
            if(!(node.getLevel() instanceof ServerLevel level)) continue;
            BlockPos pos=node.providerPosition();
            if(!level.hasChunkAt(pos)) continue;
            Direction side=node.providerSide();
            for(ResourceProvider provider:CompatibilityRegistry.discoverResources(level,pos,side)) {
                if(reportedFailures.contains(provider.id())||!provider.valid()||!seen.add(List.of(provider.identity(),provider.resourceType()))) continue;
                ResourceKey key;
                double unit=1;
                if(provider.resourceType().equals(ResourceKinds.ENERGY)) key=ResourceKinds.FE;
                else if(provider.resourceType().equals(ResourceKinds.SOURCE)) key=ResourceKinds.ARS_SOURCE;
                else if(provider.resourceType().equals(ResourceKinds.FLUID)) {
                    Identifier id=Identifier.tryParse(AstralConfig.fluidFuel.get());
                    if(id==null) continue;
                    FluidStack fuel=new FluidStack(BuiltInRegistries.FLUID.getValue(id),1);
                    if(fuel.isEmpty()) continue;
                    key=new FluidKey(fuel); unit=AstralConfig.fluidFuelValue.get();
                } else continue;
                double value=unit;
                supplies.add(new Supply(provider.id(),provider.identity(),provider.resourceType().getPath(),node,
                        n -> provider.extract(key,units(n,value),true)*value,
                        n -> provider.extract(key,units(n,value),false)*value));
            }
            for(StorageProvider discovered:CompatibilityRegistry.discoverStorage(level,pos,side)) {
                StorageProvider provider=storageViews.compute(discovered.id(),(id,previous) -> previous!=null&&previous.valid()?previous:discovered);
                if(reportedFailures.contains(provider.id())||!provider.valid()||!seen.add(List.of(provider.identity(),ResourceKinds.ITEM))) continue;
                Identifier id=Identifier.tryParse(AstralConfig.itemFuel.get());
                if(id==null) continue;
                ItemStack fuel=new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                if(fuel.isEmpty()) continue;
                ItemKey key=new ItemKey(fuel); double value=AstralConfig.itemFuelValue.get();
                supplies.add(new Supply(provider.id(),provider.identity(),"item",node,
                        n -> provider.extract(key,(int)Math.min(Integer.MAX_VALUE,units(n,value)),true).getCount()*value,
                        n -> provider.extract(key,(int)Math.min(Integer.MAX_VALUE,units(n,value)),false).getCount()*value));
            }
            for(NetworkPowerProvider provider:CompatibilityRegistry.discoverPower(level,pos,side)) {
                if(reportedFailures.contains(provider.id())||!provider.valid()||!seen.add(List.of(provider.identity(),provider.resourceType()))) continue;
                if(provider.mode()==NetworkPowerProvider.Mode.CAPACITY) capacities.add(provider);
                else supplies.add(new Supply(provider.id(),provider.identity(),provider.resourceType().getPath(),node,
                        n -> provider.acquire(n,true),n -> provider.acquire(n,false)));
            }
        }
        List<String> allowed=AstralConfig.powerProviders.get().stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().toList();
        List<PowerPolicy.Source> available=new ArrayList<>();
        for(String kind:allowed) for(var node:nodes) available.add(new PowerPolicy.Source(new Credit(node,kind),kind,credit(node,kind)));
        for(Supply supply:supplies) {
            double simulated=simulate(supply.id,() -> supply.simulate.applyAsDouble(needed));
            available.add(new PowerPolicy.Source(supply.identity,supply.kind,simulated));
        }
        for(NetworkPowerProvider provider:capacities) available.add(new PowerPolicy.Source(provider.identity(),provider.resourceType().getPath(),
                simulate(provider.id(),() -> provider.acquire(needed,true))));
        var selected=PowerPolicy.choose(allowed,AstralConfig.powerMode.get().equalsIgnoreCase("all"),needed,PowerPolicy.available(available));
        if(selected.isEmpty()) return false;
        Map<Credit,Double> debits=new LinkedHashMap<>();
        List<Lease> pendingLeases=new ArrayList<>();
        boolean successful=false;
        try {
            for(String kind:selected.get()) {
                double remaining=needed;
                for(var node:nodes) {
                    double use=Math.min(credit(node,kind),remaining);
                    if(use>0) { debits.merge(new Credit(node,kind),use,Double::sum); remaining-=use; }
                }
                for(Supply supply:supplies) if(supply.kind.equals(kind)&&remaining>0) {
                    double obtained;
                    try { obtained=supply.consume.applyAsDouble(remaining); }
                    catch(RuntimeException | LinkageError failure) { report(supply.id,failure); return false; }
                    if(!Double.isFinite(obtained)||obtained<0) { report(supply.id,new IllegalStateException("Invalid committed power quantity")); return false; }
                    // Keep confirmed fuel in persistent credit before attempting the next required resource.
                    setCredit(supply.node,kind,saturatingAdd(credit(supply.node,kind),obtained));
                    double used=Math.min(obtained,remaining);
                    debits.merge(new Credit(supply.node,kind),used,Double::sum);
                    remaining-=used;
                }
                for(NetworkPowerProvider provider:capacities) if(provider.resourceType().getPath().equals(kind)&&remaining>0) {
                    double acquired;
                    try { acquired=provider.acquire(remaining,false); }
                    catch(RuntimeException | LinkageError failure) { report(provider.id(),failure); return false; }
                    if(!Double.isFinite(acquired)||acquired<0||acquired>remaining) {
                        if(Double.isFinite(acquired)&&acquired>0) release(new Lease(provider,acquired));
                        report(provider.id(),new IllegalStateException("Invalid capacity lease amount")); return false;
                    }
                    if(acquired>0) pendingLeases.add(new Lease(provider,acquired));
                    remaining-=acquired;
                }
                if(remaining>1e-9) return false;
            }
            for(var debit:debits.entrySet()) setCredit(debit.getKey().node,debit.getKey().kind,
                    Math.max(0,credit(debit.getKey().node,debit.getKey().kind)-debit.getValue()));
            successful=true;
            if(retainCapacity) leases.addAll(pendingLeases);
            return true;
        } finally {
            if(!successful||!retainCapacity) for(Lease lease:pendingLeases) release(lease);
        }
    }
    private double simulate(String id,java.util.function.DoubleSupplier operation) {
        try { double amount=operation.getAsDouble(); return Double.isFinite(amount)&&amount>0?amount:0; }
        catch(RuntimeException | LinkageError failure) { report(id,failure); return 0; }
    }
    private void report(String id,Throwable failure) {
        if(reportedFailures.add(id)) LogUtils.getLogger().error("Astral power provider {} failed; this payment is unavailable",id,failure);
    }
    private void release(Lease lease) {
        try { lease.provider.release(lease.amount); }
        catch(RuntimeException | LinkageError failure) { report(lease.provider.id(),failure); }
    }
    private static long units(double amount,double unitValue) { return (long)Math.ceil(amount/unitValue); }
    private static double credit(NetworkAnchor node,String kind) {
        double value=node.getPersistentData().getDoubleOr("AstralPowerCredit_"+kind,0D);
        return Double.isFinite(value)&&value>0?value:0;
    }
    private static double saturatingAdd(double a,double b) { return b>Double.MAX_VALUE-a?Double.MAX_VALUE:a+b; }
    private static void setCredit(NetworkAnchor node,String kind,double value) {
        node.getPersistentData().putDouble("AstralPowerCredit_"+kind,value); node.setChanged();
    }
    @Override public void close() { for(Lease lease:leases) release(lease); leases.clear(); }
}