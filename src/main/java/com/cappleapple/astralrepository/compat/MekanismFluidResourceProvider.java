package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;

/** Native Mekanism fluid tanks keep the same identity when exposed through different multiblock ports. */
public final class MekanismFluidResourceProvider implements ResourceProvider {
    private static final Map<Class<?>,Boolean> SURFACES=new ConcurrentHashMap<>();
    private final ServerLevel level;private final BlockPos pos;private final BlockEntity tile;
    private final Object owner,tank,execute,simulate,external;private final Direction side;
    private final Method getter,read,insert,extract,capacity;private final String id;
    private MekanismFluidResourceProvider(ServerLevel level,BlockPos pos,BlockEntity tile,Object owner,Object tank,Method getter,Direction side,int index)throws ReflectiveOperationException{
        this.level=level;this.pos=pos.immutable();this.tile=tile;this.owner=owner;this.tank=tank;this.getter=getter;this.side=side;id="mekanism_fluid_"+index;
        Class<?> api=Class.forName("mekanism.api.fluid.IExtendedFluidTank"),action=Class.forName("mekanism.api.Action"),automation=Class.forName("mekanism.api.AutomationType");
        read=api.getMethod("getFluid");capacity=api.getMethod("getCapacity");
        insert=api.getMethod("insert",FluidStack.class,action,automation);extract=api.getMethod("extract",int.class,action,automation);
        execute=enumValue(action,"EXECUTE");simulate=enumValue(action,"SIMULATE");external=enumValue(automation,"EXTERNAL");
    }
    /** Empty Optional means no native contract; an empty list means a known but unavailable native surface. */
    public static Optional<List<ResourceProvider>> discover(ServerLevel level,BlockPos pos,Direction side){
        BlockEntity tile=level.getBlockEntity(pos);if(tile==null||!SURFACES.computeIfAbsent(tile.getClass(),type->Arrays.stream(type.getMethods()).anyMatch(method->method.getName().equals("getFluidTanks")||method.getName().equals("getMultiblock"))))return Optional.empty();
        Object owner;
        try{owner=ChemicalResourceProvider.owner(tile);if(owner==null)return Optional.of(List.of());
            if(!Class.forName("mekanism.api.fluid.IMekanismFluidHandler").isInstance(owner))return Optional.empty();
        }catch(ClassNotFoundException|LinkageError absent){return Optional.empty();}
        catch(ReflectiveOperationException|RuntimeException unavailable){return Optional.of(List.of());}
        try{
            if(!ChemicalResourceProvider.publicAccess(tile))return Optional.of(List.of());
            Method getter=tile.getClass().getMethod("getFluidTanks",Direction.class);
            Object raw=getter.invoke(tile,side);if(!(raw instanceof List<?> tanks))return Optional.of(List.of());
            List<ResourceProvider> result=new ArrayList<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int i=0;i<tanks.size();i++)if(seen.add(tanks.get(i)))result.add(new MekanismFluidResourceProvider(level,pos,tile,owner,tanks.get(i),getter,side,i));
            return Optional.of(List.copyOf(result));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return Optional.of(List.of());}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static Object enumValue(Class<?> type,String name){return Enum.valueOf((Class)type,name);}
    public String id(){return id;}public Object identity(){return tank;}public Identifier resourceType(){return ResourceKinds.FLUID;}public String unit(){return "mB";}
    public boolean valid(){try{return level.hasChunkAt(pos)&&level.getBlockEntity(pos)==tile&&ChemicalResourceProvider.publicAccess(tile)&&ChemicalResourceProvider.owner(tile)==owner
            &&((List<?>)getter.invoke(tile,side)).stream().anyMatch(value->value==tank);}catch(ReflectiveOperationException|RuntimeException failure){return false;}}
    public Map<ResourceKey,Long> snapshot(){if(!valid())return Map.of();try{FluidStack stack=(FluidStack)read.invoke(tank);return stack.isEmpty()?Map.of():Map.of(new FluidKey(stack),(long)stack.getAmount());}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Native fluid tank read failed",failure);}}
    public long insert(ResourceKey key,long amount,boolean simulation){
        if(!valid()||!(key instanceof FluidKey fluid)||amount<=0)return 0;
        int offer=(int)Math.min(amount,Integer.MAX_VALUE);
        try{return offer-((FluidStack)insert.invoke(tank,copy(fluid.sample(),offer),simulation?simulate:execute,external)).getAmount();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Native fluid tank insertion failed",failure);}
    }
    public long extract(ResourceKey key,long amount,boolean simulation){
        if(!valid()||!(key instanceof FluidKey)||amount<=0)return 0;
        try{FluidStack before=(FluidStack)read.invoke(tank);if(before.isEmpty()||!new FluidKey(before).equals(key))return 0;
            FluidStack result=(FluidStack)extract.invoke(tank,(int)Math.min(amount,Integer.MAX_VALUE),simulation?simulate:execute,external);
            if(result.isEmpty())return 0;if(!new FluidKey(result).equals(key))throw new IllegalStateException("Native tank returned another fluid");return result.getAmount();
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native fluid tank extraction failed",failure);}
    }
    public long capacity(){try{return ((Number)capacity.invoke(tank)).longValue();}catch(ReflectiveOperationException failure){return 0;}}
    private static FluidStack copy(FluidStack stack,int amount){return stack.copyWithAmount(amount);}
}
