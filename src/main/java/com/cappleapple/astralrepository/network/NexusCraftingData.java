package com.cappleapple.astralrepository.network;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Personal Nexus ingredients; menus borrow this inventory, never copy its ownership. */
public final class NexusCraftingData extends SavedData {
    public static final class Workspace {
        public final NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        public String recipe = "";
    }
    private final Map<UUID, Workspace> players = new HashMap<>();
    public static NexusCraftingData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(com.cappleapple.astralrepository.port.NbtCodecs.savedType("astral_repository_nexus_crafting",NexusCraftingData::new,server.registryAccess(),NexusCraftingData::load,(data,registries) -> data.save(new CompoundTag(),registries))); }
    public Workspace workspace(UUID player) { return players.computeIfAbsent(player, id -> new Workspace()); }
    public static NexusCraftingData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new NexusCraftingData();
        for (Tag value : tag.getListOrEmpty("Players")) {
            if (!(value instanceof CompoundTag entry)) continue;
            try {
                UUID owner = UUID.fromString(entry.getStringOr("Owner",""));
                var state = data.workspace(owner);
                state.recipe = entry.getStringOr("Recipe","");
                ListTag items = entry.getListOrEmpty("Items");
                for (int slot = 0; slot < Math.min(9, items.size()); slot++) {
                    CompoundTag item = items.getCompoundOrEmpty(slot);
                    if (!item.isEmpty()) state.items.set(slot, com.cappleapple.astralrepository.port.NbtCodecs.item(registries,item));
                }
            } catch (IllegalArgumentException invalid) {
                com.mojang.logging.LogUtils.getLogger().warn("Invalid saved Nexus crafting workspace", invalid);
            }
        }
        return data;
    }
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        players.forEach((owner, state) -> {
            if (state.recipe.isEmpty() && state.items.stream().allMatch(ItemStack::isEmpty)) return;
            CompoundTag entry = new CompoundTag();
            entry.putString("Owner", owner.toString());
            entry.putString("Recipe", state.recipe);
            ListTag items = new ListTag();
            for (ItemStack stack : state.items) items.add(stack.isEmpty() ? new CompoundTag() : com.cappleapple.astralrepository.port.NbtCodecs.save(stack,registries));
            entry.put("Items", items);
            entries.add(entry);
        });
        tag.put("Players", entries);
        return tag;
    }
}
