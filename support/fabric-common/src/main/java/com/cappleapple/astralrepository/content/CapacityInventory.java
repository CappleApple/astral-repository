package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.compat.StacksNotSlotsCapacity.Fraction;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import com.cappleapple.astralrepository.platform.items.IItemHandler;

/** Dense virtual positions. Capacity is an exact rational sum, never rounded per individual item. */
public final class CapacityInventory implements IItemHandler {
    record Snapshot(List<ItemStack> entries, List<Fraction> costs, BigInteger numerator, BigInteger denominator, long revision) {}
    private final List<ItemStack> entries = new ArrayList<>();
    private final List<Fraction> costs = new ArrayList<>();
    private final LongSupplier capacity;
    private final Runnable changed;
    private long revision;
    public long revision() { return revision; }
    private BigInteger usedNumerator = BigInteger.ZERO;
    private BigInteger usedDenominator = BigInteger.ONE;
    public CapacityInventory(LongSupplier capacity, Runnable changed) { this.capacity = capacity; this.changed = changed; }
    public long capacity() { return capacity.getAsLong(); }
    public long used() { return usedNumerator.add(usedDenominator).subtract(BigInteger.ONE).divide(usedDenominator).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(); }
    private Fraction cost(ItemStack stack) { return ContentHooks.capacityCostExact.apply(stack); }
    private void charge(Fraction unit, long count) {
        BigInteger numerator = usedNumerator.multiply(unit.denominator()).add(unit.numerator().multiply(BigInteger.valueOf(count)).multiply(usedDenominator));
        BigInteger denominator = usedDenominator.multiply(unit.denominator());
        BigInteger gcd = numerator.gcd(denominator);
        usedNumerator = numerator.divide(gcd); usedDenominator = denominator.divide(gcd);
    }
    private long room(Fraction cost) {
        if (cost.numerator().signum() == 0) return Integer.MAX_VALUE;
        BigInteger remaining = BigInteger.valueOf(capacity()).multiply(usedDenominator).subtract(usedNumerator);
        if (remaining.signum() <= 0) return 0;
        return remaining.multiply(cost.denominator()).divide(usedDenominator.multiply(cost.numerator())).min(BigInteger.valueOf(Integer.MAX_VALUE)).longValue();
    }
    @Override public int getSlots() { return entries.size() + 1; }
    @Override public ItemStack getStackInSlot(int slot) { return slot < 0 || slot >= entries.size() ? ItemStack.EMPTY : entries.get(slot).copy(); }
    @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return !(stack.getItem() instanceof net.minecraft.world.item.BlockItem block && block.getBlock() instanceof CrystalNodeBlock node
                && (node.kind() == NodeKind.STORAGE || node.kind() == NodeKind.BUFFER || node.kind() == NodeKind.POWER));
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack) || slot < 0 || slot > entries.size()) return stack;
        int existing = -1;
        for (int i = 0; i < entries.size(); i++) if (ItemStack.isSameItemSameComponents(entries.get(i), stack)) { existing = i; break; }
        if (existing >= 0 && existing != slot || slot < entries.size() && existing != slot) return stack;
        long current = existing < 0 ? 0 : entries.get(existing).getCount();
        Fraction unit = existing < 0 ? cost(stack) : costs.get(existing);
        int accepted = (int)Math.min(stack.getCount(), Math.min(Integer.MAX_VALUE - current, room(unit)));
        if (accepted <= 0) return stack;
        if (!simulate) {
            if (existing < 0) { entries.add(stack.copyWithCount(accepted)); costs.add(unit); } else entries.get(existing).grow(accepted);
            charge(unit, accepted); revision++; notifyChanged();
        }
        return stack.copyWithCount(stack.getCount() - accepted);
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= entries.size() || amount <= 0) return ItemStack.EMPTY;
        ItemStack present = entries.get(slot); int count = Math.min(present.getCount(), amount);
        ItemStack extracted = present.copyWithCount(count);
        if (!simulate) {
            present.shrink(count); charge(costs.get(slot), -count);
            if (present.isEmpty()) { entries.remove(slot); costs.remove(slot); }
            revision++; notifyChanged();
        }
        return extracted;
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); ListTag items = new ListTag();
        for (ItemStack stack : entries) {
            CompoundTag entry = new CompoundTag(); entry.put("Item", com.cappleapple.astralrepository.port.NbtCodecs.save(stack.copyWithCount(1),registries)); entry.putInt("Count", stack.getCount()); items.add(entry);
        }
        tag.put("Contents", items); return tag;
    }
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        revision++; entries.clear(); costs.clear(); usedNumerator = BigInteger.ZERO; usedDenominator = BigInteger.ONE;
        ListTag items = tag.getListOrEmpty("Contents");
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompoundOrEmpty(i); ItemStack stack = com.cappleapple.astralrepository.port.NbtCodecs.item(registries, entry.getCompoundOrEmpty("Item"));
            if (!stack.isEmpty() && entry.getIntOr("Count",0) > 0) {
                stack.setCount(entry.getIntOr("Count",0)); entries.add(stack); Fraction unit = cost(stack); costs.add(unit); charge(unit, stack.getCount());
            }
        }
    }

    private void notifyChanged() { changed.run(); }
}
