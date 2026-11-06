package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.crafting.NativeBytecode;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.gametest.*;

/** Final-batch persistence integrity checks include native fixture recovery journals. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NativeSavedDataGameTests {
    private static boolean stoppingObserver;
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", batch="zz_native_saved_data")
    public static void everyLoadedSavedDataSnapshotContainsSerializableNamedTags(GameTestHelper h) {
        if(!stoppingObserver) {
            stoppingObserver=true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event)-> {
                List<Map<String,Object>> entries=audit(event.getServer());
                try {
                    var path=java.nio.file.Path.of("build/autocrafting-fixes/native-saved-data-stop-audit.json"); java.nio.file.Files.createDirectories(path.getParent());
                    java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(entries)+"\n");
                } catch(java.io.IOException failure) { throw new IllegalStateException(failure); }
                for(var entry:entries) if(entry.containsKey("failure")) com.mojang.logging.LogUtils.getLogger().error("Native stop SavedData integrity: {}",entry);
            });
        }
        var entries=audit(h.getLevel().getServer());
        h.assertTrue(!entries.isEmpty(),"The live native SavedData map was actually scanned");
        h.assertTrue(entries.stream().anyMatch(value->value.get("path").toString().endsWith("/astral_repository_craft_recovery")),"The live craft recovery journal was included in serialization checks");
        h.assertTrue(entries.stream().noneMatch(value->value.containsKey("failure")),"All live SavedData snapshots are serializable: " + entries.stream().filter(value->value.containsKey("failure")).toList());
        h.succeed();
    }
    private static List<Map<String,Object>> audit(net.minecraft.server.MinecraftServer server) {
        List<Map<String,Object>> result=new ArrayList<>();
        for (var level : server.getAllLevels()) {
            Object storage = level.getDataStorage();
            var shape = NativeBytecode.inspect(storage.getClass());
            var field = shape.fields().stream().filter(value -> value.descriptor().equals("Ljava/util/Map;")).findFirst().orElseThrow();
            Object cached = new NativeBytecode.Reference(180,shape.name(),field.name(),field.descriptor()).readInstance(storage);
            for (var entry : ((Map<?,?>)cached).entrySet()) {
                Object actual=entry.getValue(); if(actual instanceof Optional<?> optional) actual=optional.orElse(null);
                if(!(actual instanceof SavedData saved)) continue;
                String path = level.dimension().location() + "/data/" + entry.getKey();
                Map<String,Object> record=new LinkedHashMap<>(); record.put("path",path); record.put("class",saved.getClass().getName()); record.put("dirty",saved.isDirty());
                try {
                    CompoundTag tag=saved.save(new CompoundTag(),level.registryAccess()); inspect(tag,path);
                    var bytes=new java.io.ByteArrayOutputStream(); NbtIo.write(tag,new java.io.DataOutputStream(bytes)); record.put("bytes",bytes.size());
                } catch(java.io.IOException | RuntimeException invalid) { record.put("failure",invalid.toString()); }
                result.add(Map.copyOf(record));
            }
        }
        return List.copyOf(result);
    }
    private static void inspect(Tag value,String path) {
        if (value instanceof CompoundTag compound) for (String key : compound.getAllKeys()) {
            if (key == null) throw new IllegalStateException("Null NBT name at " + path + "; native parent values=" + compound.getAllKeys());
            Tag child = compound.get(key); if (child == null) throw new IllegalStateException("Null NBT value at " + path + "/" + key);
            inspect(child,path + "/" + key);
        }
        else if (value instanceof ListTag list) for (int index=0;index<list.size();index++) inspect(list.get(index),path + "[" + index + "]");
    }
}
