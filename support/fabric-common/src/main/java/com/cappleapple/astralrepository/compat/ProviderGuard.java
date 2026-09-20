package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Quarantines a failed provider for its current discovery lifetime. Uncertain commits are propagated. */
public final class ProviderGuard {
    private final String id;
    private boolean failed;
    public ProviderGuard(String id) { this.id = id; }
    public boolean failed() { return failed; }
    public <T> T read(Supplier<T> work, T fallback) {
        if (failed) return fallback;
        try { return work.get(); }
        catch (RuntimeException | LinkageError failure) { fail(failure); return fallback; }
    }
    public <T> T transfer(Supplier<T> work, T fallback, boolean simulate) {
        if (failed) return fallback;
        try { return work.get(); }
        catch (RuntimeException | LinkageError failure) {
            fail(failure);
            if (!simulate) throw new ProviderFailure(id, failure);
            return fallback;
        }
    }
    private <T> T transferLazy(Supplier<T> work,Supplier<T> fallback,boolean simulate) {
        if(failed)return fallback.get();
        try{return work.get();}
        catch(RuntimeException|LinkageError failure){
            fail(failure);
            if(!simulate)throw new ProviderFailure(id,failure);
            return fallback.get();
        }
    }
    private void fail(Throwable failure) {
        if (!failed) LogUtils.getLogger().error("Astral Repository quarantined provider {}", id, failure);
        failed = true;
    }
    public static StorageProvider storage(StorageProvider backend) {
        ProviderGuard guard = new ProviderGuard(backend.id());
        return new StorageProvider() {
            public String id() { return backend.id(); }
            public Object identity() { return backend.identity(); }
            public Map<ItemKey,Long> snapshot() { return guard.read(backend::snapshot, Map.of()); }
            public java.util.Optional<Map<ItemKey,Long>> poll(int budget) { return guard.read(() -> backend.poll(budget), java.util.Optional.of(Map.of())); }
            public ItemKey candidate(java.util.function.Predicate<ItemStack> matches) { return guard.read(() -> backend.candidate(matches), null); }
            public ItemStack insert(ItemStack stack, boolean simulate) {
                return guard.transferLazy(() -> backend.insert(stack, simulate), stack::copy, simulate);
            }
            public ItemStack extract(ItemKey key, int amount, boolean simulate) {
                return guard.transfer(() -> backend.extract(key, amount, simulate), ItemStack.EMPTY, simulate);
            }
            public boolean valid() { return guard.read(backend::valid, false); }
            public long capacity() { return guard.read(backend::capacity, -1L); }
            public long version() { return guard.read(backend::version, -1L); }
        };
    }
    public static CraftingProvider crafting(CraftingProvider backend) {
        ProviderGuard guard=new ProviderGuard(backend.id());
        return new CraftingProvider() {
            public String id() { return backend.id(); }
            public Object identity() { return backend.identity(); }
            public boolean valid() { return guard.read(backend::valid,false); }
            public Map<ItemKey,Long> craftableOutputs() { return guard.read(backend::craftableOutputs,Map.of()); }
            public Ticket request(ItemKey output,long amount,CraftingContext context) {
                Ticket ticket=guard.transfer(() -> backend.request(output,amount,context),null,false);
                if(ticket==null) throw new IllegalStateException("Quarantined crafting provider "+id());
                return new Ticket() {
                    public java.util.UUID id() { return ticket.id(); }
                    public State state() { return guard.read(ticket::state,State.FAILED); }
                    public String message() { return guard.failed()?"Crafting provider failed":ticket.message(); }
                    public void cancel() {
                        try { ticket.cancel(); } catch(RuntimeException | LinkageError failure) { guard.fail(failure); }
                    }
                };
            }
        };
    }
    public static ResourceProvider resource(ResourceProvider backend) {
        ProviderGuard guard = new ProviderGuard(backend.id());
        return new ResourceProvider() {
            public String id() { return backend.id(); }
            public Object identity() { return backend.identity(); }
            public Identifier resourceType() { return backend.resourceType(); }
            public Map<ResourceKey,Long> snapshot() { return guard.read(backend::snapshot, Map.of()); }
            public long insert(ResourceKey key, long amount, boolean simulate) {
                return guard.transfer(() -> backend.insert(key, amount, simulate), 0L, simulate);
            }
            public long extract(ResourceKey key, long amount, boolean simulate) {
                return guard.transfer(() -> backend.extract(key, amount, simulate), 0L, simulate);
            }
            public boolean valid() { return guard.read(backend::valid, false); }
            public long capacity() { return guard.read(backend::capacity, -1L); }
            public long version() { return guard.read(backend::version, -1L); }
            public String unit() { return backend.unit(); }
        };
    }
}