package com.cappleapple.astralrepository.content;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** An immutable saved snapshot; placed layers receive their own filter and design values. */
public record RunePreset(UUID id,String name,RuneLayer.Mode mode,RuneDesign design,CompoundTag filter,int priority,boolean enabled,RuneCadence cadence) {
    public RunePreset(UUID id,String name,RuneLayer.Mode mode,RuneDesign design,CompoundTag filter,int priority,boolean enabled){this(id,name,mode,design,filter,priority,enabled,RuneCadence.DEFAULT);}
    public RunePreset { Objects.requireNonNull(cadence);Objects.requireNonNull(id);Objects.requireNonNull(mode);Objects.requireNonNull(design);name=name.strip();if(name.isEmpty())name="Rune";if(name.length()>32)name=name.substring(0,32);filter=filter.copy();try{var bytes=new java.io.ByteArrayOutputStream();var bounded=new CompoundTag();bounded.put("Filter",filter);bounded.put("Design",design.save());net.minecraft.nbt.NbtIo.write(bounded,new java.io.DataOutputStream(bytes));if(bytes.size()>95000)throw new IllegalArgumentException("Preset exceeds storage budget");}catch(java.io.IOException impossible){throw new IllegalArgumentException(impossible);}priority=Math.clamp(priority,-999,999); }
    @Override public CompoundTag filter(){return filter.copy();}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.put("Cadence",cadence.save());t.putUUID("Id",id);t.putString("Name",name);t.putString("Mode",mode.name());t.put("Design",design.save());t.put("Filter",filter.copy());t.putInt("Priority",priority);t.putBoolean("Enabled",enabled);return t;}
    public static RunePreset load(CompoundTag t,HolderLookup.Provider registries){FilterRules rules=new FilterRules();rules.load(t.getCompound("Filter"),registries);return new RunePreset(t.getUUID("Id"),t.getString("Name"),RuneLayer.Mode.valueOf(t.getString("Mode")),RuneDesign.load(t.getCompound("Design")),rules.save(registries),t.getInt("Priority"),!t.contains("Enabled")||t.getBoolean("Enabled"),RuneCadence.load(t.getCompound("Cadence")));}
    public static RunePreset initial(RuneLayer.Mode mode){return new RunePreset(UUID.randomUUID(),mode.title(),mode,RuneDesign.initial(mode),new CompoundTag(),0,true);}
}
