package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.StorageProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;

/** Uses AE2's explicitly exposed ME_STORAGE integration capability. */
final class Ae2StorageProvider implements StorageProvider {
    static final String STORAGE = "appeng.api.storage.MEStorage";
    static final String ITEM = "appeng.api.stacks.AEItemKey";
    static final String KEY = "appeng.api.stacks.AEKey";
    static final String ACTION = "appeng.api.config.Actionable";
    static final String SOURCE = "appeng.api.networking.security.IActionSource";
    private final String id;
    private final Object storage;
    private final Object identity;
    private final Object energy;
    private final BooleanSupplier valid;
    Ae2StorageProvider(String id, Object storage, Object identity, Object energy, BooleanSupplier valid) {
        this.id=id; this.storage=storage; this.identity=identity; this.energy=energy; this.valid=valid;
    }
    public String id() { return id; }
    public Object identity() { return identity; }
    public boolean valid() { return valid.getAsBoolean(); }
    public long capacity() { return -1; }
    public Map<ItemKey,Long> snapshot() {
        if (!valid()) return Map.of();
        Map<ItemKey,Long> result = new LinkedHashMap<>();
        Iterable<?> counter = (Iterable<?>)OptionalApi.call(storage, STORAGE, "getAvailableStacks");
        for (Object value : counter) {
            Map.Entry<?,?> entry = (Map.Entry<?,?>)value;
            if (!OptionalApi.type(ITEM).isInstance(entry.getKey())) continue;
            long amount = ((Number)entry.getValue()).longValue();
            if (amount > 0) result.put(new ItemKey((ItemStack)OptionalApi.call(entry.getKey(), ITEM, "toStack")), amount);
        }
        return Map.copyOf(result);
    }
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (!valid() || stack.isEmpty()) return stack.copy();
        long accepted = transfer("insert", stack, stack.getCount(), simulate);
        return stack.copyWithCount(stack.getCount() - (int)accepted);
    }
    public ItemStack extract(ItemKey key, int amount, boolean simulate) {
        if (!valid() || amount <= 0) return ItemStack.EMPTY;
        ItemStack sample = key.sample();
        int request = amount;
        return sample.copyWithCount((int)transfer("extract", sample, request, simulate));
    }
    private long transfer(String method, ItemStack stack, long amount, boolean simulate) {
        Object key = OptionalApi.call(null, ITEM, "of", new Class<?>[]{ItemStack.class}, stack);
        Object action = OptionalApi.value(ACTION, simulate ? "SIMULATE" : "MODULATE");
        Object source = OptionalApi.call(null, SOURCE, "empty");
        long moved = ((Number)OptionalApi.call(null, "appeng.api.storage.StorageHelper", method.equals("insert")?"poweredInsert":"poweredExtraction",
                new Class<?>[]{OptionalApi.type("appeng.api.networking.energy.IEnergySource"),OptionalApi.type(STORAGE),OptionalApi.type(KEY),long.class,OptionalApi.type(SOURCE),OptionalApi.type(ACTION)},
                energy, storage, key, amount, source, action)).longValue();
        if (moved < 0 || moved > amount) throw new IllegalStateException("AE2 storage violated amount contract");
        return moved;
    }
}