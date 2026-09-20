package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class RuneCadenceGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void independentResourceCadencesPersistAndConserveAllFourTypes(GameTestHelper h)throws Exception{
        var from=new BlockPos(3,2,3);var to=new BlockPos(6,2,3);h.setBlock(from,AstralContent.SEED_STORAGE_CRYSTAL.get());h.setBlock(to,AstralContent.SEED_STORAGE_CRYSTAL.get());
        var a=(CrystalNodeBlockEntity)h.getBlockEntity(from);var b=(CrystalNodeBlockEntity)h.getBlockEntity(to);
        a.inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,64),false);a.tank().fill(new FluidStack(Fluids.WATER,4000),IFluidHandler.FluidAction.EXECUTE);a.energy().receiveEnergy(10000,false);
        long[] source={4000,0};String id="cadence_source_"+UUID.randomUUID();
        CompatibilityRegistry.registerResources(id,(level,pos,side)->{
            int index=pos.equals(h.absolutePos(from))?0:pos.equals(h.absolutePos(to))?1:-1;if(level!=h.getLevel()||index<0)return List.of();
            return List.of(scalar(id+index,source,index));
        });
        var face=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(from),Direction.SOUTH);var rune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        int[] amounts={3,70,90,40},intervals={5,7,11,13};RuneCadence cadence=RuneCadence.DEFAULT;
        for(var kind:RuneCadence.Kind.values())cadence=cadence.with(kind,new RuneCadence.Rate(amounts[kind.ordinal()],intervals[kind.ordinal()]));rune.setCadence(cadence);
        var packet=new NetworkPackets.Visual(h.absolutePos(from),h.absolutePos(to),ItemStack.EMPTY,0xffffff,20,-2,List.of(h.absolutePos(from),h.absolutePos(to)),null,null,new net.minecraft.resources.ResourceLocation("lava"));
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{NetworkPackets.Visual.CODEC.encode(buf,packet);h.assertTrue(NetworkPackets.Visual.CODEC.decode(buf).fluid().equals(packet.fluid()),"Resource packet preserves the actual fluid instead of guessing from color");}finally{buf.release();}
        h.assertTrue(RuneSettingsPackets.supportedResources(face)==15,"Capability discovery retains all four types on the same host");
        h.assertTrue(face.toggleTarget(rune.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(to)),Direction.SOUTH).assigned(),"Combined resource host accepts a direct target");
        var restored=RuneSurface.load(h.getLevel().getServer(),face.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(restored.layers().get(0).cadence().equals(cadence),"Placed cadence survives world save");
        var preset=new RunePreset(UUID.randomUUID(),"Four resources",rune.mode(),rune.design(),rune.filter().save(h.getLevel().registryAccess()),0,true,cadence);
        h.assertTrue(RunePresetFiles.decode(RunePresetFiles.encode(preset),h.getLevel().registryAccess()).cadence().equals(cadence),"Instance and pack JSON preserve every resource rate");
        long[] last={0,0,0,0},time={-1,-1,-1,-1};int[] batches={0,0,0,0};
        h.onEachTick(()->{
            long now=h.getLevel().getServer().getTickCount();long[] values={b.inventory().used(),b.tank().getFluidAmount(),b.energy().getEnergyStored(),source[1]};
            h.assertTrue(a.inventory().used()+values[0]==64&&a.tank().getFluidAmount()+values[1]==4000&&a.energy().getEnergyStored()+values[2]==10000&&source[0]+source[1]==4000,"All real resources are conserved");
            for(int i=0;i<4;i++)if(values[i]!=last[i]){h.assertTrue(values[i]-last[i]==amounts[i],"Batch uses its configured amount for resource "+i);if(time[i]>=0)h.assertTrue(now-time[i]>=intervals[i],"Resource cannot move before its own cadence");last[i]=values[i];time[i]=now;batches[i]++;}
        });
        h.succeedWhen(()->{for(int n:batches)h.assertTrue(n>=3,"Await three independent batches of each resource");rune.setEnabled(false);});
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=100)
    public static void serverLimitsClampSavedRatesAndKeepCooldownsOnReload(GameTestHelper h){
        var maxima=List.of(com.cappleapple.astralrepository.AstralServerConfig.maxItemTransfer,com.cappleapple.astralrepository.AstralServerConfig.maxFluidTransfer,com.cappleapple.astralrepository.AstralServerConfig.maxEnergyTransfer,com.cappleapple.astralrepository.AstralServerConfig.maxSourceTransfer);
        var minima=List.of(com.cappleapple.astralrepository.AstralServerConfig.minItemTransferTicks,com.cappleapple.astralrepository.AstralServerConfig.minFluidTransferTicks,com.cappleapple.astralrepository.AstralServerConfig.minEnergyTransferTicks,com.cappleapple.astralrepository.AstralServerConfig.minSourceTransferTicks);
        int[] oldMax=maxima.stream().mapToInt(v->v.get()).toArray(),oldMin=minima.stream().mapToInt(v->v.get()).toArray();
        boolean instant=com.cappleapple.astralrepository.AstralConfig.instantAutomaticLogistics.get();
        RuneLayer rune=null;
        try{
            com.cappleapple.astralrepository.AstralConfig.instantAutomaticLogistics.set(true);
            var from=new BlockPos(3,2,3);var to=new BlockPos(6,2,3);h.setBlock(from,AstralContent.SEED_STORAGE_CRYSTAL.get());h.setBlock(to,AstralContent.SEED_STORAGE_CRYSTAL.get());
            var a=(CrystalNodeBlockEntity)h.getBlockEntity(from);var b=(CrystalNodeBlockEntity)h.getBlockEntity(to);
            for(int i=0;i<3;i++)a.inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,64),false);
            a.tank().fill(new FluidStack(Fluids.WATER,4000),IFluidHandler.FluidAction.EXECUTE);a.energy().receiveEnergy(10000,false);
            long[] source={4000,0};String id="cadence_limits_"+UUID.randomUUID();
            CompatibilityRegistry.registerResources(id,(level,pos,side)->{
                int index=pos.equals(h.absolutePos(from))?0:pos.equals(h.absolutePos(to))?1:-1;
                return level==h.getLevel()&&index>=0?List.of(scalar(id+index,source,index)):List.of();
            });
            var face=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(from),Direction.SOUTH);rune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
            RuneCadence saved=RuneCadence.DEFAULT;
            for(var kind:RuneCadence.Kind.values())saved=saved.with(kind,new RuneCadence.Rate(Integer.MAX_VALUE,1));
            rune.setCadence(RuneCadence.load(saved.save()));
            h.assertTrue(face.toggleTarget(rune.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(to)),Direction.SOUTH).assigned(),"All-resource target assigned");
            int[] caps={7,61,503,37},ticks={3,5,7,11};
            for(int i=0;i<4;i++){maxima.get(i).set(caps[i]);minima.get(i).set(ticks[i]);}
            var worker=new DirectRuneTransfers(h.getLevel().getServer());worker.track(face);
            long[] last={0,0,0,0};int[] lastTick={-1,-1,-1,-1},batches={0,0,0,0};
            for(int tick=0;tick<44;tick++){
                if(tick==10){caps=new int[]{3,31,257,19};ticks=new int[]{9,13,17,21};for(int i=0;i<4;i++){maxima.get(i).set(caps[i]);minima.get(i).set(ticks[i]);}}
                if(tick==16)rune.setCadence(rune.cadence().with(RuneCadence.Kind.ITEMS,new RuneCadence.Rate(Integer.MAX_VALUE-1,1)));
                worker.tick();
                long[] current={b.inventory().used(),b.tank().getFluidAmount(),b.energy().getEnergyStored(),source[1]};
                for(int i=0;i<4;i++)if(current[i]!=last[i]){
                    h.assertTrue(current[i]-last[i]==caps[i],"Actual transfer obeys reloaded maximum for resource "+i);
                    if(lastTick[i]>=0)h.assertTrue(tick-lastTick[i]>=ticks[i],"Reloading limits and editing a preset cannot bypass the active resource cooldown "+i);
                    last[i]=current[i];lastTick[i]=tick;batches[i]++;
                }
                h.assertTrue(a.inventory().used()+current[0]==192&&a.tank().getFluidAmount()+current[1]==4000&&a.energy().getEnergyStored()+current[2]==10000&&source[0]+source[1]==4000,"Bounded transfers conserve all four resource totals");
            }
            for(int i=0;i<4;i++)h.assertTrue(batches[i]>=3,"Each medium transfers repeatedly with independent server limits");
            h.assertTrue(rune.cadence().overrides().get(RuneCadence.Kind.FLUID).amount()==Integer.MAX_VALUE,"Clamping does not erase a saved preset's preference");
            for(int i=0;i<4;i++){maxima.get(i).set(Integer.MAX_VALUE);minima.get(i).set(Integer.MAX_VALUE);}
            for(var kind:RuneCadence.Kind.values()){
                var maximum=new RuneCadence.Rate(Integer.MAX_VALUE,Integer.MAX_VALUE);
                h.assertTrue(kind.permits(maximum)&&kind.constrain(maximum).equals(maximum),"All server limits permit the full positive int range when configured");
                h.assertTrue(RuneCadence.DEFAULT.rate(kind).ticks()==Integer.MAX_VALUE,"Instant automatic logistics still respects the server interval minimum");
                rune.setCadence(rune.cadence().with(kind,maximum));
            }
            long[] before={b.inventory().used(),b.tank().getFluidAmount(),b.energy().getEnergyStored(),source[1]};
            for(int i=0;i<5;i++)worker.tick();
            h.assertTrue(Arrays.equals(before,new long[]{b.inventory().used(),b.tank().getFluidAmount(),b.energy().getEnergyStored(),source[1]}),"Maximum tick intervals do not overflow into immediate transfers");
        }finally{
            if(rune!=null)rune.setEnabled(false);
            for(int i=0;i<4;i++){maxima.get(i).set(oldMax[i]);minima.get(i).set(oldMin[i]);}
            com.cappleapple.astralrepository.AstralConfig.instantAutomaticLogistics.set(instant);
        }
        h.succeed();
    }
    public static ResourceProvider scalar(String id,long[] amounts,int index){return new ResourceProvider(){
        public String id(){return id;}public Object identity(){return id;}public boolean valid(){return true;}public long capacity(){return 10000;}public net.minecraft.resources.ResourceLocation resourceType(){return ResourceKinds.SOURCE;}
        public Map<ResourceKey,Long> snapshot(){return Map.of(ResourceKinds.ARS_SOURCE,amounts[index]);}
        public long insert(ResourceKey key,long n,boolean simulate){long accepted=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,10000-amounts[index]):0;if(!simulate)amounts[index]+=accepted;return accepted;}
        public long extract(ResourceKey key,long n,boolean simulate){long taken=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,amounts[index]):0;if(!simulate)amounts[index]-=taken;return taken;}
    };}
}
