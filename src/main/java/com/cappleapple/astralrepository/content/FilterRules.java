package com.cappleapple.astralrepository.content;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;

/** Positive predicates combine by ANY; exclusions always veto. Empty filters accept all. */
public final class FilterRules {
    public enum Kind { ITEM, ITEM_TAG, FLUID, FLUID_TAG, NAMESPACE, COMPONENTS }
    public record Entry(Kind kind, String id, boolean exclude, ItemStack sample) {}
    private final List<Entry> entries = new ArrayList<>();
    private boolean blacklist;
    private long minimum;
    private long target = Long.MAX_VALUE;

    public List<Entry> entries() { return List.copyOf(entries); }
    /** No identity predicates; numeric reserve and stock limits are still applied by callers. */
    public boolean unrestricted() { return entries.isEmpty(); }
    public void remove(int index) { entries.remove(index); }
    public boolean toggleItemData(int index) {
        Entry entry = entries.get(index);
        if ((entry.kind != Kind.ITEM && entry.kind != Kind.COMPONENTS) || entry.sample.isEmpty()) return false;
        entries.set(index, new Entry(entry.kind == Kind.ITEM ? Kind.COMPONENTS : Kind.ITEM, entry.id, entry.exclude, entry.sample));
        return true;
    }
    public boolean all() { return false; }
    public boolean blacklist() { return blacklist; }
    public long minimum() { return minimum; }
    public long target() { return target; }
    public void setMinimum(long value) { minimum = Math.max(0, value); }
    public void setTarget(long value) { target = value < 0 ? Long.MAX_VALUE : value; }
    /** Retained for old callers; matching is always ANY. */
    @Deprecated public void toggleAll() {}
    public void toggleBlacklist() { blacklist = !blacklist; }
    public void clearPredicates() { entries.clear(); blacklist = false; }
    public void clear() { clearPredicates(); minimum = 0; target = Long.MAX_VALUE; }
    public void add(Kind kind, String id, boolean exclude, ItemStack sample) {
        ItemStack copy = sample.isEmpty() ? ItemStack.EMPTY : sample.copyWithCount(1);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.kind == kind && entry.id.equals(id) && entry.exclude == exclude
                    && (kind != Kind.COMPONENTS || ItemStack.isSameItemSameComponents(entry.sample, copy))) {
                if (kind == Kind.ITEM && !copy.isEmpty()) entries.set(i, new Entry(kind, id, exclude, copy));
                return;
            }
        }
        if (entries.size() < 64) entries.add(new Entry(kind, id, exclude, copy));
    }
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (unrestricted()) return true;
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return evaluate(e -> switch (e.kind) {
            case ITEM -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(e.id);
            case ITEM_TAG -> stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(e.id)));
            case NAMESPACE -> namespace.equals(e.id);
            case COMPONENTS -> ItemStack.isSameItemSameComponents(stack, e.sample);
            default -> false;
        }, false);
    }

    public boolean matches(FluidStack stack) {
        if (stack.isEmpty()) return false;
        if (unrestricted()) return true;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return evaluate(e -> switch (e.kind) {
            case FLUID -> id.toString().equals(e.id);
            case FLUID_TAG -> stack.is(TagKey.create(Registries.FLUID, ResourceLocation.parse(e.id)));
            case NAMESPACE -> id.getNamespace().equals(e.id);
            default -> false;
        }, true);
    }

    private boolean evaluate(java.util.function.Predicate<Entry> match, boolean fluid) {
        boolean positive = false;
        int positiveCount = 0;
        for (Entry entry : entries) {
            boolean applicable = entry.kind == Kind.NAMESPACE || (fluid
                    ? entry.kind == Kind.FLUID || entry.kind == Kind.FLUID_TAG
                    : entry.kind != Kind.FLUID && entry.kind != Kind.FLUID_TAG);
            if (!applicable) continue;
            boolean result = match.test(entry);
            if (entry.exclude && result) return false;
            if (!entry.exclude) { positiveCount++; positive |= result; }
        }
        if (positiveCount == 0) return entries.stream().noneMatch(e -> !e.exclude) || blacklist;
        return blacklist != positive;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Blacklist", blacklist);
        tag.putLong("Minimum", minimum); tag.putLong("Target", target);
        ListTag values = new ListTag();
        for (Entry entry : entries) {
            CompoundTag value = new CompoundTag();
            value.putString("Kind", entry.kind.name()); value.putString("Id", entry.id);
            value.putBoolean("Exclude", entry.exclude);
            if (!entry.sample.isEmpty()) value.put("Sample", entry.sample.save(registries));
            values.add(value);
        }
        tag.put("Entries", values); return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        clear(); blacklist = tag.getBoolean("Blacklist");
        minimum = Math.max(0, tag.getLong("Minimum"));
        target = tag.contains("Target") ? Math.max(0, tag.getLong("Target")) : Long.MAX_VALUE;
        ListTag values = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(64, values.size()); i++) {
            CompoundTag value = values.getCompound(i);
            try {
                Kind kind = Kind.valueOf(value.getString("Kind"));
                String id = value.getString("Id");
                if (kind != Kind.NAMESPACE) ResourceLocation.parse(id);
                add(kind, id, value.getBoolean("Exclude"), ItemStack.parseOptional(registries, value.getCompound("Sample")));
            } catch (IllegalArgumentException ignored) { /* Ignore invalid edited filter entries. */ }
        }
    }

    public String summary() {
        return (blacklist ? "BLACKLIST " : "") + "ANY " + entries.size() + " entries; keep " + minimum
                + (target == Long.MAX_VALUE ? "" : "; target " + target);
    }
}




