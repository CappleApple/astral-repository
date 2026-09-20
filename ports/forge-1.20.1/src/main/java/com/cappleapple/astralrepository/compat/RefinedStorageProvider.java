package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.StorageProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;

/** Uses Refined Storage 1.x's network API and its authoritative item storage cache. */
final class RefinedStorageProvider implements StorageProvider {
    static final String NETWORK="com.refinedmods.refinedstorage.api.network.INetwork";
    static final String CACHE="com.refinedmods.refinedstorage.api.storage.cache.IStorageCache";
    static final String LIST="com.refinedmods.refinedstorage.api.util.IStackList";
    static final String ENTRY="com.refinedmods.refinedstorage.api.util.StackListEntry";
    static final String ACTION="com.refinedmods.refinedstorage.api.util.Action";
    private final String id;
    private final Object network;
    private final Object storage;
    private final BooleanSupplier valid;
    RefinedStorageProvider(String id,Object network,Object storage,BooleanSupplier valid) {
        this.id=id;this.network=network;this.storage=storage;this.valid=valid;
    }
    public String id(){return id;}
    public Object identity(){return network;}
    public boolean valid(){return valid.getAsBoolean();}
    public long capacity(){return -1;}
    public Map<ItemKey,Long> snapshot() {
        if(!valid())return Map.of();
        return snapshotList(OptionalApi.call(storage,CACHE,"getList"),false);
    }
    static Map<ItemKey,Long> snapshotList(Object list,boolean craftable) {
        Map<ItemKey,Long> result=new LinkedHashMap<>();
        for(Object entry:(Iterable<?>)OptionalApi.call(list,LIST,"getStacks")) {
            ItemStack stack=(ItemStack)OptionalApi.call(entry,ENTRY,"getStack");
            if(!stack.isEmpty())result.put(new ItemKey(stack),craftable?1L:(long)stack.getCount());
        }
        return Map.copyOf(result);
    }
    public ItemStack insert(ItemStack stack,boolean simulate) {
        if(!valid()||stack.isEmpty())return stack.copy();
        ItemStack remainder=(ItemStack)OptionalApi.call(network,NETWORK,"insertItem",
                new Class<?>[]{ItemStack.class,int.class,OptionalApi.type(ACTION)},
                stack.copyWithCount(1),stack.getCount(),action(simulate));
        // RS1 uses null to represent an entirely accepted insertion.
        if(remainder==null||remainder.isEmpty())return ItemStack.EMPTY;
        if(!ItemStack.isSameItemSameTags(stack,remainder)||remainder.getCount()>stack.getCount())
            throw new IllegalStateException("Refined Storage violated insertion contract");
        return remainder.copy();
    }
    public ItemStack extract(ItemKey key,int amount,boolean simulate) {
        if(!valid()||amount<=0)return ItemStack.EMPTY;
        ItemStack extracted=(ItemStack)OptionalApi.call(network,NETWORK,"extractItem",
                new Class<?>[]{ItemStack.class,int.class,OptionalApi.type(ACTION)},key.sample(),amount,action(simulate));
        if(extracted==null||extracted.isEmpty())return ItemStack.EMPTY;
        if(!key.equals(new ItemKey(extracted))||extracted.getCount()>amount)
            throw new IllegalStateException("Refined Storage violated extraction contract");
        return extracted.copy();
    }
    private static Object action(boolean simulate){return OptionalApi.value(ACTION,simulate?"SIMULATE":"PERFORM");}
}
