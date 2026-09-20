package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Validated server mirrors of personal instance libraries. Wands store only a selected UUID; placed runes own immutable snapshots. */
public final class RuneLibraryData extends SavedData {
    public static final int MAX_PRESETS=64;
    private final Map<UUID,LinkedHashMap<UUID,RunePreset>> players=new HashMap<>();
    public static RuneLibraryData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RuneLibraryData::new,RuneLibraryData::load),"astral_repository_rune_libraries");}
    public Map<UUID,RunePreset> library(UUID owner){return players.computeIfAbsent(owner,id->{var map=new LinkedHashMap<UUID,RunePreset>();for(var mode:RuneLayer.Mode.values()){var p=RunePreset.initial(mode);map.put(p.id(),p);}setDirty();return map;});}
    public static RuneLibraryData load(CompoundTag t,HolderLookup.Provider registries){RuneLibraryData data=new RuneLibraryData();for(Tag entry:t.getList("Players",Tag.TAG_COMPOUND)){CompoundTag p=(CompoundTag)entry;if(!p.hasUUID("Owner"))continue;var map=new LinkedHashMap<UUID,RunePreset>();for(Tag value:p.getList("Presets",Tag.TAG_COMPOUND)){if(map.size()>=MAX_PRESETS)break;try{var preset=RunePreset.load((CompoundTag)value,registries);map.put(preset.id(),preset);}catch(RuntimeException ignored){}}data.players.put(p.getUUID("Owner"),map);}return data;}
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider registries){ListTag people=new ListTag();players.forEach((owner,map)->{CompoundTag p=new CompoundTag();p.putUUID("Owner",owner);ListTag presets=new ListTag();map.values().forEach(preset->presets.add(preset.save()));p.put("Presets",presets);people.add(p);});t.put("Players",people);return t;}
}
