package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.AstralRepository;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** Confirmed uninserted resources retry safely; indeterminate third-party commits are retained for inspection only. */
public final class TransferRecoveryData extends SavedData {
    public record Entry(UUID id,GlobalPos source,ResourceKey resource,long amount,boolean uncertain,String provider,Direction side) {}
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    public static TransferRecoveryData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(com.cappleapple.astralrepository.port.NbtCodecs.savedType("astral_repository_transfer_recovery",TransferRecoveryData::new,server.registryAccess(),TransferRecoveryData::load,(data,registries)->data.save(new CompoundTag(),registries)));}
    public void put(GlobalPos source,ResourceKey key,long amount,boolean uncertain,String provider){put(source,key,amount,uncertain,provider,null);}
    public void put(GlobalPos source,ResourceKey key,long amount,boolean uncertain,String provider,Direction side){if(amount<=0)return;UUID id=UUID.randomUUID();entries.put(id,new Entry(id,source,key,amount,uncertain,provider,side));setDirty();if(uncertain)AstralRepository.LOGGER.error("Uncertain transfer {}: {} x {} at {} via {}. Recorded in astral_repository_transfer_recovery.dat; not replayed automatically.",id,amount,key,source,provider);}
    public List<Entry> confirmed(){return entries.values().stream().filter(e->!e.uncertain).toList();}
    public void remaining(Entry entry,long amount){if(amount<=0)entries.remove(entry.id);else entries.put(entry.id,new Entry(entry.id,entry.source,entry.resource,amount,false,entry.provider,entry.side));setDirty();}
    private static TransferRecoveryData load(CompoundTag tag,HolderLookup.Provider registries){
        TransferRecoveryData data=new TransferRecoveryData();ListTag list=tag.getListOrEmpty("Transfers");
        for(int i=0;i<list.size();i++){CompoundTag value=list.getCompoundOrEmpty(i);try{
            GlobalPos source=GlobalPos.of(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,Identifier.parse(value.getStringOr("Dimension",""))),BlockPos.of(value.getLongOr("Position",0L)));
            ResourceKey key;
            if(value.contains("Item"))key=new ItemKey(com.cappleapple.astralrepository.port.NbtCodecs.item(registries,value.getCompoundOrEmpty("Item")));
            else if(value.contains("Fluid"))key=new FluidKey(com.cappleapple.astralrepository.port.NbtCodecs.fluid(registries,value.getCompoundOrEmpty("Fluid")));
            else key=new ResourceKinds.ScalarKey(Identifier.parse(value.getStringOr("Type","")),Identifier.parse(value.getStringOr("Resource","")));
            UUID id=value.read("Id",net.minecraft.core.UUIDUtil.CODEC).orElseThrow();data.entries.put(id,new Entry(id,source,key,value.getLongOr("Amount",0L),value.getBooleanOr("Uncertain",false),value.getStringOr("Provider",""),value.contains("Side")?Direction.byName(value.getStringOr("Side","")):null));
        }catch(RuntimeException error){AstralRepository.LOGGER.error("Invalid transfer recovery entry {}",i,error);}}
        return data;
    }
    public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        ListTag list=new ListTag();for(Entry e:entries.values()){CompoundTag value=new CompoundTag();value.store("Id",net.minecraft.core.UUIDUtil.CODEC,e.id);value.putString("Dimension",e.source.dimension().identifier().toString());value.putLong("Position",e.source.pos().asLong());value.putLong("Amount",e.amount);value.putBoolean("Uncertain",e.uncertain);value.putString("Provider",e.provider);if(e.side!=null)value.putString("Side",e.side.getName());
            if(e.resource instanceof ItemKey item)value.put("Item",com.cappleapple.astralrepository.port.NbtCodecs.save(item.sample(),registries));
            else if(e.resource instanceof FluidKey fluid)value.put("Fluid",com.cappleapple.astralrepository.port.NbtCodecs.save(fluid.sample(),registries));
            else{value.putString("Type",e.resource.type().toString());value.putString("Resource",e.resource.id().toString());}
            list.add(value);
        }tag.put("Transfers",list);return tag;
    }
}

