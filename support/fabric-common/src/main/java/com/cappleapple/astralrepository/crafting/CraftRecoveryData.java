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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent escrow journal. Physical machine inputs remain in their own block-entity saves. */
final class CraftRecoveryData extends SavedData {
    record Entry(GlobalPos origin, Map<ItemKey, Long> items, boolean uncertain) {}
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    static CraftRecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(com.cappleapple.astralrepository.port.NbtCodecs.savedType("astral_repository_craft_recovery",CraftRecoveryData::new,server.registryAccess(),CraftRecoveryData::load,(data,registries)->data.save(new CompoundTag(),registries)));
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
        ListTag entries = tag.getListOrEmpty("Jobs");
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            try {
                GlobalPos origin = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.getStringOr("Dimension",""))), BlockPos.of(entry.getLongOr("Position",0L)));
                Map<ItemKey, Long> items = new LinkedHashMap<>();
                ListTag savedItems = entry.getListOrEmpty("Escrow");
                for (int j = 0; j < savedItems.size(); j++) {
                    CompoundTag savedItem = savedItems.getCompoundOrEmpty(j);
                    ItemStack stack = com.cappleapple.astralrepository.port.NbtCodecs.item(registries, savedItem.getCompoundOrEmpty("Stack"));
                    long count = savedItem.getLongOr("Count",0L);
                    if (!stack.isEmpty() && count > 0) items.merge(new ItemKey(stack), count, Math::addExact);
                }
                data.entries.put(entry.read("Id",net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), new Entry(origin, Map.copyOf(items), entry.getBooleanOr("Uncertain",false)));
            } catch (RuntimeException error) {
                CraftingService.LOGGER.error("Invalid saved crafting recovery entry {}", i, error);
            }
        }
        return data;
    }
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag jobs = new ListTag();
        entries.forEach((id, entry) -> {
            CompoundTag job = new CompoundTag();
            job.store("Id",net.minecraft.core.UUIDUtil.CODEC, id);
            job.putBoolean("Uncertain", entry.uncertain());
            job.putString("Dimension", entry.origin().dimension().identifier().toString());
            job.putLong("Position", entry.origin().pos().asLong());
            ListTag items = new ListTag();
            entry.items().forEach((key, count) -> {
                CompoundTag item = new CompoundTag();
                item.put("Stack", com.cappleapple.astralrepository.port.NbtCodecs.save(key.sample(),registries));
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
