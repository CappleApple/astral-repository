package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.StorageProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;

/** Refined Storage 2.x root storage access with public resource factory and isolated item conversion. */
final class RefinedStorageProvider implements StorageProvider {
    static final String API = "com.refinedmods.refinedstorage.common.api.RefinedStorageApi";
    static final String ITEM = "com.refinedmods.refinedstorage.common.support.resource.ItemResource";
    static final String RESOURCE = "com.refinedmods.refinedstorage.api.resource.ResourceKey";
    static final String AMOUNT = "com.refinedmods.refinedstorage.api.resource.ResourceAmount";
    static final String ACTION = "com.refinedmods.refinedstorage.api.core.Action";
    static final String ACTOR = "com.refinedmods.refinedstorage.api.storage.Actor";
    private final String id;
    private final Object network;
    private final Object storage;
    private final BooleanSupplier valid;
    RefinedStorageProvider(String id, Object network, Object storage, BooleanSupplier valid) {
        this.id=id; this.network=network; this.storage=storage; this.valid=valid;
    }
    public String id() { return id; }
    public Object identity() { return network; }
    public boolean valid() { return valid.getAsBoolean(); }
    public long capacity() { return -1; }
    public Map<ItemKey,Long> snapshot() {
        if (!valid()) return Map.of();
        Map<ItemKey,Long> result=new LinkedHashMap<>();
        for (Object amount : (Iterable<?>)OptionalApi.call(storage,
                "com.refinedmods.refinedstorage.api.storage.StorageView", "getAll")) {
            Object resource=OptionalApi.call(amount, AMOUNT, "resource");
            if (!OptionalApi.type(ITEM).isInstance(resource)) continue;
            long quantity=((Number)OptionalApi.call(amount,AMOUNT,"amount")).longValue();
            if (quantity > 0) result.put(new ItemKey((ItemStack)OptionalApi.call(resource,ITEM,"toItemStack")),quantity);
        }
        return Map.copyOf(result);
    }
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (!valid() || stack.isEmpty()) return stack.copy();
        return stack.copyWithCount(stack.getCount() - (int)transfer("insert",stack,stack.getCount(),simulate));
    }
    public ItemStack extract(ItemKey key,int amount,boolean simulate) {
        if (!valid() || amount <= 0) return ItemStack.EMPTY;
        ItemStack sample=key.sample();
        return sample.copyWithCount((int)transfer("extract",sample,amount,simulate));
    }
    private long transfer(String method, ItemStack stack,long amount,boolean simulate) {
        Object api=OptionalApi.field(API,"INSTANCE");
        Object factory=OptionalApi.call(api,API,"getItemResourceFactory");
        Optional<?> created=(Optional<?>)OptionalApi.call(factory,
                "com.refinedmods.refinedstorage.common.api.support.resource.ResourceFactory", "create",new Class<?>[]{ItemStack.class},stack);
        if (created.isEmpty()) return 0;
        Object key=OptionalApi.call(created.get(),AMOUNT,"resource");
        Object action=OptionalApi.value(ACTION,simulate?"SIMULATE":"EXECUTE");
        String contract="com.refinedmods.refinedstorage.api.storage."+(method.equals("insert")?"InsertableStorage":"ExtractableStorage");
        long moved=((Number)OptionalApi.call(storage,contract,method,
                new Class<?>[]{OptionalApi.type(RESOURCE),long.class,OptionalApi.type(ACTION),OptionalApi.type(ACTOR)},
                key,amount,action,OptionalApi.field(ACTOR,"EMPTY"))).longValue();
        if (moved < 0 || moved > amount) throw new IllegalStateException("Refined Storage violated amount contract");
        return moved;
    }
}