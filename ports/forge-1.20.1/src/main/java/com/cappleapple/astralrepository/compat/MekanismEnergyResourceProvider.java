package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.MekanismResourceSupport;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native energy containers preserve shared frequency/multiblock identity and whole FE ownership. */
public final class MekanismEnergyResourceProvider implements ResourceProvider {
    private record Quantum(long fe,Object nativeAmount){}
    private static final Map<Class<?>,Boolean> SURFACES=new ConcurrentHashMap<>();
    private final ServerLevel level;private final BlockPos pos;private final BlockEntity tile;private final Direction side;
    private final Object owner,container,execute,simulate,external;private final Method getter,read,capacity,insert,extract;private final String id;
    private MekanismEnergyResourceProvider(ServerLevel level,BlockPos pos,BlockEntity tile,Object owner,Object container,Method getter,Direction side,int index)throws ReflectiveOperationException{
        this.level=level;this.pos=pos.immutable();this.tile=tile;this.owner=owner;this.container=container;this.getter=getter;this.side=side;id="mekanism_energy_"+index;
        Class<?> api=Class.forName("mekanism.api.energy.IEnergyContainer"),action=Class.forName("mekanism.api.Action"),automation=Class.forName("mekanism.api.AutomationType");
        read=api.getMethod("getEnergy");capacity=api.getMethod("getMaxEnergy");
        insert=api.getMethod("insert",read.getReturnType(),action,automation);extract=api.getMethod("extract",read.getReturnType(),action,automation);
        execute=enumValue(action,"EXECUTE");simulate=enumValue(action,"SIMULATE");external=enumValue(automation,"EXTERNAL");
    }
    public static Optional<List<ResourceProvider>> discover(ServerLevel level,BlockPos pos,Direction side){
        BlockEntity tile=level.getBlockEntity(pos);if(tile==null||!SURFACES.computeIfAbsent(tile.getClass(),type->Arrays.stream(type.getMethods()).anyMatch(method->method.getName().equals("getEnergyContainers")||method.getName().equals("getMultiblock"))))return Optional.empty();
        Object owner;
        try{owner=ChemicalResourceProvider.owner(tile);if(owner==null)return Optional.of(List.of());
            if(!Class.forName("mekanism.api.energy.IMekanismStrictEnergyHandler").isInstance(tile))return Optional.empty();
        }catch(ClassNotFoundException|LinkageError absent){return Optional.empty();}catch(ReflectiveOperationException|RuntimeException unavailable){return Optional.of(List.of());}
        try{
            if(!ChemicalResourceProvider.publicAccess(tile)||!unitsEnabled())return Optional.of(List.of());Method getter=tile.getClass().getMethod("getEnergyContainers",Direction.class);
            Object raw=getter.invoke(tile,side);if(!(raw instanceof List<?> values))return Optional.of(List.of());
            List<ResourceProvider> result=new ArrayList<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int i=0;i<values.size();i++)if(seen.add(values.get(i)))result.add(new MekanismEnergyResourceProvider(level,pos,tile,owner,values.get(i),getter,side,i));
            return Optional.of(List.copyOf(result));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return Optional.of(List.of());}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static Object enumValue(Class<?> type,String name){return Enum.valueOf((Class)type,name);}
    public String id(){return id;}public Object identity(){return container;}public ResourceLocation resourceType(){return ResourceKinds.ENERGY;}public String unit(){return "FE";}
    public boolean valid(){try{return level.hasChunkAt(pos)&&level.getBlockEntity(pos)==tile&&unitsEnabled()&&ChemicalResourceProvider.publicAccess(tile)&&ChemicalResourceProvider.owner(tile)==owner
            &&((List<?>)getter.invoke(tile,side)).stream().anyMatch(value->value==container);}catch(ReflectiveOperationException|RuntimeException failure){return false;}}
    public Map<ResourceKey,Long> snapshot(){if(!valid())return Map.of();try{Quantum q=quantum(toFE(read.invoke(container)));return q==null?Map.of():Map.of(ResourceKinds.FE,q.fe());}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Native energy read failed",failure);}}
    public long insert(ResourceKey key,long amount,boolean simulation){
        if(!valid()||!ResourceKinds.FE.equals(key)||amount<=0)return 0;Quantum offered=quantum(amount);if(offered==null)return 0;
        try{
            Object rest=insert.invoke(container,offered.nativeAmount(),simulate,external);
            Quantum accepted=quantum(Math.min(offered.fe(),toFE(difference(offered.nativeAmount(),rest))));if(accepted==null)return 0;
            if(!simulation){Object actual=insert.invoke(container,accepted.nativeAmount(),execute,external);
                if(!difference(accepted.nativeAmount(),actual).equals(accepted.nativeAmount()))throw new IllegalStateException("Native energy insertion changed after simulation");}
            return accepted.fe();
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native energy insertion failed",failure);}
    }
    public long extract(ResourceKey key,long amount,boolean simulation){
        if(!valid()||!ResourceKinds.FE.equals(key)||amount<=0)return 0;
        try{
            Quantum offered=quantum(Math.min(amount,toFE(read.invoke(container))));if(offered==null)return 0;
            Quantum accepted=quantum(Math.min(offered.fe(),toFE(extract.invoke(container,offered.nativeAmount(),simulate,external))));if(accepted==null)return 0;
            if(!simulation&&!extract.invoke(container,accepted.nativeAmount(),execute,external).equals(accepted.nativeAmount()))throw new IllegalStateException("Native energy extraction changed after simulation");
            return accepted.fe();
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native energy extraction failed",failure);}
    }
    public long capacity(){try{Quantum q=quantum(toFE(capacity.invoke(container)));return q==null?0:q.fe();}catch(ReflectiveOperationException failure){return 0;}}
    private static boolean unitsEnabled(){
        try{Object unit=Class.forName("mekanism.common.util.UnitDisplayUtils$EnergyUnit").getField("FORGE_ENERGY").get(null);return Boolean.TRUE.equals(unit.getClass().getMethod("isEnabled").invoke(unit));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return false;}
    }
    private static Quantum quantum(long amount){
        for(int i=0;i<64&&amount>0;i++){
            Object nativeAmount=MekanismResourceSupport.energyFromNetwork(amount);long represented=toFE(nativeAmount);
            if(represented==amount)return new Quantum(amount,nativeAmount);if(represented<=0||represented>amount)return null;amount=represented;
        }
        return null;
    }
    private static long toFE(Object amount){return MekanismResourceSupport.energyToNetwork(amount);}
    private static Object difference(Object offered,Object remainder){return MekanismResourceSupport.nativeEnergyDifference(offered,remainder);}
}
