package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ResourceKey;
import com.cappleapple.astralrepository.api.ResourceKinds;
import com.cappleapple.astralrepository.api.ResourceProvider;
import com.cappleapple.astralrepository.api.FluidKey;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.NetworkManager;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class RuneAggregateGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400)
    public static void networkRuneCommitsRecheckContainerFiltersAndProviderValidity(GameTestHelper h){
        var nexus=new BlockPos(5,2,5);var host=new BlockPos(5,2,2);
        var first=new BlockPos(4,2,7);var second=new BlockPos(6,2,7);
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());for(var pos:List.of(host,first,second))h.setBlock(pos,Blocks.BARREL);
        var anchor=(CrystalNodeBlockEntity)h.getBlockEntity(nexus);
        var filterSurface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(first),Direction.SOUTH);
        var filter=filterSurface.addLayer(RuneGlyph.id(RuneLayer.Mode.FILTER),RuneLayer.Mode.FILTER);filter.setPriority(50);
        String id="aggregate_validity_"+UUID.randomUUID();var water=new FluidKey(new FluidStack(Fluids.WATER,1));
        long[] amounts={100,100};boolean[] valid={true,true};
        CompatibilityRegistry.registerResources(id,(level,pos,side)->{
            int i=pos.equals(h.absolutePos(first))?0:pos.equals(h.absolutePos(second))?1:-1;
            if(level!=h.getLevel()||i<0)return List.of();
            return List.of(new ResourceProvider(){
                public String id(){return id+i;}public Object identity(){return id+i;}public boolean valid(){return valid[i];}
                public net.minecraft.resources.Identifier resourceType(){return ResourceKinds.FLUID;}public long capacity(){return 1000;}
                public Map<ResourceKey,Long> snapshot(){return Map.of(water,amounts[i]);}
                public long insert(ResourceKey key,long amount,boolean simulate){long accepted=key.equals(water)?Math.min(amount,1000-amounts[i]):0;if(!simulate)amounts[i]+=accepted;return accepted;}
                public long extract(ResourceKey key,long amount,boolean simulate){long extracted=key.equals(water)?Math.min(amount,amounts[i]):0;if(!simulate)amounts[i]-=extracted;return extracted;}
            });
        });
        h.startSequence().thenWaitUntil(()->{
            var network=NetworkManager.get(h.getLevel().getServer()).networkAt(anchor.address());h.assertTrue(network!=null&&network.storageCount()>=3,"Wait for container discovery");
            var resource=network.runeResources(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(host)),Set.of(),null,ResourceKinds.FLUID);
            h.assertTrue(resource.snapshot().getOrDefault(water,0L)==200,"Wait for both resource providers");
        }).thenExecute(()->{
            var network=NetworkManager.get(h.getLevel().getServer()).networkAt(anchor.address());var origin=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(host));
            var items=network.runeItems(origin,Set.of(),null);var iron=new ItemStack(Items.IRON_INGOT,16);
            h.assertTrue(items.insert(iron,true).isEmpty(),"Simulation finds an accepting priority container");
            filter.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);filter.changed();
            h.assertTrue(items.insert(iron,false).isEmpty(),"Commit finds the next currently accepting container");
            h.assertTrue(((Container)h.getBlockEntity(first)).isEmpty()&&((Container)h.getBlockEntity(second)).getItem(0).getCount()==16,"A changed filter is rechecked after the insertion order was prepared");
            filter.clearFilter();
            var fluids=network.runeResources(origin,Set.of(),null,ResourceKinds.FLUID);
            h.assertTrue(fluids.insert(water,70,true)==70,"Resource simulation accepts before provider invalidation");
            valid[0]=false;
            h.assertTrue(fluids.insert(water,70,false)==70&&amounts[0]==100&&amounts[1]==170,"Commit skips an invalidated priority provider without losing the alternate destination");
            h.assertTrue(fluids.snapshot().getOrDefault(water,0L)==170,"Completed transaction discards its candidate list and sees current provider validity");
        }).thenSucceed();
    }
}
