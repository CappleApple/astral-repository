package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent escrow journal. Physical machine inputs remain in their own block-entity saves. */
final class CraftRecoveryData extends SavedData {
    record Entry(GlobalPos origin, Map<ItemKey, Long> items, boolean uncertain) {}
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    static CraftRecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent((tag)->CraftRecoveryData.load(tag,null),CraftRecoveryData::new, "astral_repository_craft_recovery");
    }
    void put(UUID id, GlobalPos origin, Map<ItemKey, Long> items) {
        Entry next = new Entry(origin, Map.copyOf(items), false);
        if (!next.equals(entries.put(id, next))) setDirty();
    }
    void uncertain(GlobalPos origin, Map<ItemKey, Long> items) { entries.put(UUID.randomUUID(), new Entry(origin, Map.copyOf(items), true)); setDirty(); }
    void remove(UUID id) { if (entries.remove(id) != null) setDirty(); }
    Map<UUID, Entry> pending(GlobalPos origin, Set<UUID> active) {
        Map<UUID, Entry> result = new LinkedHashMap<>();
        entries.forEach((id, entry) -> { if (!entry.uncertain() && entry.origin().equals(origin) && !active.contains(id)) result.put(id, entry); });
        return result;
    }
    private static CraftRecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        CraftRecoveryData data = new CraftRecoveryData();
        ListTag entries = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            try {
                GlobalPos origin = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(entry.getString("Dimension"))), BlockPos.of(entry.getLong("Position")));
                Map<ItemKey, Long> items = new LinkedHashMap<>();
                ListTag savedItems = entry.getList("Escrow", Tag.TAG_COMPOUND);
                for (int j = 0; j < savedItems.size(); j++) {
                    CompoundTag savedItem = savedItems.getCompound(j);
                    ItemStack stack = ItemStack.of(savedItem.getCompound("Stack"));
                    long count = savedItem.getLong("Count");
                    if (!stack.isEmpty() && count > 0) items.merge(new ItemKey(stack), count, Math::addExact);
                }
                data.entries.put(entry.getUUID("Id"), new Entry(origin, Map.copyOf(items), entry.getBoolean("Uncertain")));
            } catch (RuntimeException error) {
                CraftingService.LOGGER.error("Invalid saved crafting recovery entry {}", i, error);
            }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {HolderLookup.Provider registries=null;
        ListTag jobs = new ListTag();
        entries.forEach((id, entry) -> {
            CompoundTag job = new CompoundTag();
            job.putUUID("Id", id);
            job.putBoolean("Uncertain", entry.uncertain());
            job.putString("Dimension", entry.origin().dimension().location().toString());
            job.putLong("Position", entry.origin().pos().asLong());
            ListTag items = new ListTag();
            entry.items().forEach((key, count) -> {
                CompoundTag item = new CompoundTag();
                item.put("Stack", key.sample().save(new net.minecraft.nbt.CompoundTag()));
                item.putLong("Count", count);
                items.add(item);
            });
            job.put("Escrow", items);
            jobs.add(job);
        });
        tag.put("Jobs", jobs);
        return tag;
    }
}
