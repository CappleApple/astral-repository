package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.network.RuneSavedData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.fluids.FluidStack;

/** Insertion policy for Astral's provider views. Vanilla/manual access stays with the container. */
public final class ContainerRuneRules {
    private static final int WHITELIST=1,MATCH=2,DENIED=4;
    private static BlockPos otherHalf(ServerLevel level,BlockPos pos){
        var state=level.getBlockState(pos);
        if(state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
            var other=pos.relative(ChestBlock.getConnectedDirection(state));
            if(level.hasChunkAt(other))return other;
        }
        return null;
    }
    private static <T> int evaluate(RuneSavedData data,GlobalPos pos,T resource,java.util.function.BiPredicate<FilterRules,T> test){
        int result=0;var surfaces=data.surfaces(pos);
        for(int i=0;i<surfaces.size();i++){
            var surface=surfaces.get(i);
            if(!surface.enabled())continue;
            boolean checkedHost=false;
            var layers=surface.layers();
            for(int j=0;j<layers.size();j++){
                var layer=layers.get(j);
                if(!layer.enabled()||layer.mode()!=RuneLayer.Mode.FILTER)continue;
                if(!checkedHost){if(surface.isRemoved())break;checkedHost=true;}
                if(layer.filter().blacklist()){
                    if(!test.test(layer.filter(),resource))return DENIED;
                }else{
                    result|=WHITELIST;
                    if((result&MATCH)==0&&test.test(layer.filter(),resource))result|=MATCH;
                }
            }
        }
        return result;
    }
    private static <T> boolean allowed(ServerLevel level,RuneSavedData data,GlobalPos pos,T resource,java.util.function.BiPredicate<FilterRules,T> test){
        int result=evaluate(data,pos,resource,test);
        if((result&DENIED)!=0)return false;
        var other=otherHalf(level,pos.pos());
        if(other!=null)result|=evaluate(data,GlobalPos.of(pos.dimension(),other),resource,test);
        return (result&DENIED)==0&&((result&WHITELIST)==0||(result&MATCH)!=0);
    }
    public static boolean allows(ServerLevel level,BlockPos pos,ItemStack item){return allowed(level,RuneSavedData.get(level.getServer()),GlobalPos.of(level.dimension(),pos),item,FilterRules::matches);}
    public static boolean allows(ServerLevel level,BlockPos pos,FluidStack fluid){return allowed(level,RuneSavedData.get(level.getServer()),GlobalPos.of(level.dimension(),pos),fluid,FilterRules::matches);}
    private static int priorityAt(ServerLevel level,BlockPos pos){
        int result=Integer.MIN_VALUE;
        for(var surface:RuneSurfaces.at(level,pos))if(surface.enabled()&&!surface.isRemoved())
            for(var layer:surface.layers())if(layer.enabled()&&layer.mode()==RuneLayer.Mode.FILTER)result=Math.max(result,layer.priority());
        return result;
    }
    public static int priority(ServerLevel level,BlockPos pos,int fallback){
        int result=priorityAt(level,pos);var other=otherHalf(level,pos);
        if(other!=null)result=Math.max(result,priorityAt(level,other));
        return result==Integer.MIN_VALUE?fallback:result;
    }
    public static StorageProvider wrap(ServerLevel level,BlockPos pos,StorageProvider delegate){
        var data=RuneSavedData.get(level.getServer());var address=GlobalPos.of(level.dimension(),pos.immutable());
        // Retain only the world-owned index and address; rule lists are resolved live on every insertion.
        return new StorageProvider(){
        public String id(){return delegate.id();}public Object identity(){return delegate.identity();}public boolean valid(){return delegate.valid();}public long capacity(){return delegate.capacity();}public long version(){return delegate.version();}
        public Map<ItemKey,Long> snapshot(){return delegate.snapshot();}public Optional<Map<ItemKey,Long>> poll(int budget){return delegate.poll(budget);}
        public ItemKey candidate(java.util.function.Predicate<ItemStack> matches){return delegate.candidate(matches);}
        public ItemStack extract(ItemKey key,int amount,boolean simulate){return delegate.extract(key,amount,simulate);}
        public ItemStack insert(ItemStack stack,boolean simulate){return allowed(level,data,address,stack,FilterRules::matches)?delegate.insert(stack,simulate):stack.copy();}
    };}
    public static ResourceProvider wrap(ServerLevel level,BlockPos pos,ResourceProvider delegate){
        var data=RuneSavedData.get(level.getServer());var address=GlobalPos.of(level.dimension(),pos.immutable());
        return new ResourceProvider(){
        public String id(){return delegate.id();}public Object identity(){return delegate.identity();}public net.minecraft.resources.ResourceLocation resourceType(){return delegate.resourceType();}public boolean valid(){return delegate.valid();}public long capacity(){return delegate.capacity();}public long version(){return delegate.version();}public String unit(){return delegate.unit();}public net.minecraft.resources.ResourceLocation visualization(){return delegate.visualization();}
        public Map<ResourceKey,Long> snapshot(){return delegate.snapshot();}public long extract(ResourceKey key,long amount,boolean simulate){return delegate.extract(key,amount,simulate);}
        public long insert(ResourceKey key,long amount,boolean simulate){return key instanceof FluidKey fluid&&!allowed(level,data,address,fluid.sample(),FilterRules::matches)?0:delegate.insert(key,amount,simulate);}
    };}
    private ContainerRuneRules(){}
}
