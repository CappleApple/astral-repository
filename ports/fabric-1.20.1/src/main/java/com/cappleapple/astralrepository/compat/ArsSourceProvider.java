package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Ars Nouveau 1.21.x ISourceTile adapter; Source is not converted into FE. */
final class ArsSourceProvider implements ResourceProvider {
    private static final String API = "com.hollingsworth.arsnouveau.api.source.ISourceTile";
    private final ServerLevel level;
    private final BlockEntity tile;
    ArsSourceProvider(ServerLevel level, BlockEntity tile) { this.level = level; this.tile = tile; }
    static boolean supports(BlockEntity tile) { return tile != null && OptionalApi.type(API).isInstance(tile); }
    public String id() { return "ars_nouveau:" + level.dimension().location() + ":" + tile.getBlockPos().asLong(); }
    public Object identity() { return tile; }
    public ResourceLocation resourceType() { return ResourceKinds.SOURCE; }
    public String unit() { return "Source"; }
    public boolean valid() {
        return !tile.isRemoved() && level.hasChunkAt(tile.getBlockPos()) && level.getBlockEntity(tile.getBlockPos()) == tile;
    }
    private int number(String name) { return ((Number)OptionalApi.call(tile, API, name)).intValue(); }
    public Map<ResourceKey,Long> snapshot() {
        return valid() ? Map.of(ResourceKinds.ARS_SOURCE, (long)number("getSource")) : Map.of();
    }
    public long capacity() { return number("getMaxSource"); }
    public long insert(ResourceKey key, long amount, boolean simulate) {
        if (!valid() || !ResourceKinds.ARS_SOURCE.equals(key) || amount <= 0
                || !((Boolean)OptionalApi.call(tile, API, "canAcceptSource"))) return 0;
        int before = number("getSource");
        int accepted = (int)Math.min(Math.min(amount, number("getTransferRate")), Math.max(0, capacity() - before));
        if (simulate || accepted <= 0) return Math.max(0, accepted);
        OptionalApi.call(tile, API, "addSource", new Class<?>[]{int.class}, accepted);
        return Math.max(0, number("getSource") - before);
    }
    public long extract(ResourceKey key, long amount, boolean simulate) {
        if (!valid() || !ResourceKinds.ARS_SOURCE.equals(key) || amount <= 0) return 0;
        int before = number("getSource");
        int extracted = (int)Math.min(Math.min(amount, number("getTransferRate")), before);
        if (simulate || extracted <= 0) return Math.max(0, extracted);
        OptionalApi.call(tile, API, "removeSource", new Class<?>[]{int.class}, extracted);
        return Math.max(0, before - number("getSource"));
    }
}