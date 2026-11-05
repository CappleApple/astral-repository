package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional native chemical tanks, retaining each physical tank's identity across multiblock ports. */
public final class ChemicalResourceProvider implements ResourceProvider {
    private static final Map<Class<?>,Boolean> SURFACES=new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean potential(Class<?> type){return SURFACES.computeIfAbsent(type,key->Arrays.stream(key.getMethods()).anyMatch(method->
            method.getName().matches("get(Chemical|Gas|Infusion|Pigment|Slurry)Tanks")
                    ||(method.getName().equals("getMultiblock")&&method.getReturnType().getName().startsWith("mekanism."))));}
    private final ServerLevel level;private final BlockPos pos;private final BlockEntity tile;
    private final Object owner,tank;private final Method getter,read,insert,extract,capacity;
    private final Direction side;private final Identifier kind;private final String id;
    private final Object execute,simulate,external;
    private ChemicalResourceProvider(ServerLevel level,BlockPos pos,BlockEntity tile,Object owner,Object tank,Method getter,Direction side,int index)throws ReflectiveOperationException{
        this.level=level;this.pos=pos.immutable();this.tile=tile;this.owner=owner;this.tank=tank;this.getter=getter;this.side=side;
        Class<?> tankApi=Class.forName("mekanism.api.chemical.IChemicalTank");read=tankApi.getMethod("getStack");Object sample=read.invoke(tank);
        kind=kind(sample.getClass().getName());id="mekanism_"+kind.getPath()+"_"+index;
        insert=find(tankApi,"insert",3,sample);extract=find(tankApi,"extract",3,null);capacity=tankApi.getMethod("getCapacity");
        Class<?> action=Class.forName("mekanism.api.Action"),automation=Class.forName("mekanism.api.AutomationType");
        execute=enumValue(action,"EXECUTE");simulate=enumValue(action,"SIMULATE");external=enumValue(automation,"EXTERNAL");
    }
    public static List<ResourceProvider> discover(ServerLevel level,BlockPos pos,Direction side){
        BlockEntity tile=level.getBlockEntity(pos);if(tile==null||!potential(tile.getClass()))return List.of();
        List<ResourceProvider> result=new ArrayList<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        try{
            if(!publicAccess(tile))return List.of();Object owner=owner(tile);if(owner==null)return List.of();
            for(String name:new String[]{"getChemicalTanks","getGasTanks","getInfusionTanks","getPigmentTanks","getSlurryTanks"}){
                Method getter;try{getter=tile.getClass().getMethod(name,Direction.class);}catch(NoSuchMethodException absent){continue;}
                Object raw=getter.invoke(tile,side);if(!(raw instanceof List<?> tanks))continue;
                for(int i=0;i<tanks.size();i++)if(seen.add(tanks.get(i)))result.add(new ChemicalResourceProvider(level,pos,tile,owner,tanks.get(i),getter,side,i));
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return List.of();}
        return List.copyOf(result);
    }
    static Object owner(BlockEntity tile)throws ReflectiveOperationException{
        Object owner=tile;
        try{owner=tile.getClass().getMethod("getMultiblock").invoke(tile);if(owner==null)return null;
            if(!(Boolean)owner.getClass().getMethod("isFormed").invoke(owner))return null;
        }catch(NoSuchMethodException singleBlock){return tile;}
        return owner;
    }
    static boolean publicAccess(BlockEntity tile)throws ReflectiveOperationException{
        try{tile.getClass().getMethod("validateSecurityIsPublic").invoke(tile);}catch(NoSuchMethodException noSecurity){}
        return true;
    }
    private static Identifier kind(String name){return name.contains(".gas.")?ResourceKinds.GAS:name.contains(".infuse.")?ResourceKinds.INFUSION:
            name.contains(".pigment.")?ResourceKinds.PIGMENT:name.contains(".slurry.")?ResourceKinds.SLURRY:ResourceKinds.CHEMICAL;}
    private static Method find(Class<?> tankApi,String name,int count,Object sample)throws NoSuchMethodException{
        for(Method method:tankApi.getMethods())if(method.getName().equals(name)&&method.getParameterCount()==count
                &&(sample==null?method.getParameterTypes()[0]==long.class:method.getParameterTypes()[0].isInstance(sample)))return method;
        throw new NoSuchMethodException(name);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static Object enumValue(Class<?> type,String name){return Enum.valueOf((Class)type,name);}
    public String id(){return id;}public Object identity(){return tank;}public Identifier resourceType(){return kind;}public String unit(){return "mB";}
    public boolean valid(){try{return level.hasChunkAt(pos)&&level.getBlockEntity(pos)==tile&&publicAccess(tile)&&owner(tile)==owner
            &&((List<?>)getter.invoke(tile,side)).stream().anyMatch(value->value==tank);}catch(ReflectiveOperationException|RuntimeException failure){return false;}}
    public Map<ResourceKey,Long> snapshot(){
        if(!valid())return Map.of();try{Object value=read.invoke(tank);long amount=amount(value);return amount<=0?Map.of():Map.of(ChemicalKey.fromNative(value),amount);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Chemical tank read failed",failure);}
    }
    public long insert(ResourceKey key,long amount,boolean simulation){
        if(!valid()||!(key instanceof ChemicalKey chemical)||!kind.equals(chemical.type())||amount<=0)return 0;
        try{return amount-amount(insert.invoke(tank,chemical.sampleWithAmount(amount),simulation?simulate:execute,external));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Chemical tank insertion failed",failure);}
    }
    public long extract(ResourceKey key,long amount,boolean simulation){
        if(!valid()||!(key instanceof ChemicalKey)||amount<=0)return 0;
        try{Object before=read.invoke(tank);if(amount(before)<=0||!ChemicalKey.fromNative(before).equals(key))return 0;
            Object result=extract.invoke(tank,amount,simulation?simulate:execute,external);
            if(amount(result)<=0)return 0;if(!ChemicalKey.fromNative(result).equals(key))throw new IllegalStateException("Chemical tank returned another chemical");return amount(result);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Chemical tank extraction failed",failure);}
    }
    public long capacity(){try{return ((Number)capacity.invoke(tank)).longValue();}catch(ReflectiveOperationException failure){return 0;}}
    private static long amount(Object stack)throws ReflectiveOperationException{return ((Number)stack.getClass().getMethod("getAmount").invoke(stack)).longValue();}
}
