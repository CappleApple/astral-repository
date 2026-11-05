package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.crafting.MekanismResourceSupport;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional runtime gate: the real native tank supplies and receives confirmed network escrow. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class MekanismResourceGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void realChemicalTankReservesAndRefundsThroughTheNetworkWithoutBecomingAProcessor(GameTestHelper h)throws Exception{
        if(!Boolean.getBoolean("astral_repository.machineCompatTest")){h.succeed();return;}
        h.assertTrue(ModList.get().isLoaded("mekanism"),"The machine profile requires actual Mekanism");
        BlockPos nexus=new BlockPos(3,2,3),tankPos=nexus.east(),fluidPos=nexus.north(),energyPos=nexus.south();
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());
        h.setBlock(tankPos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_chemical_tank")));
        h.setBlock(fluidPos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_fluid_tank")));
        h.setBlock(energyPos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_energy_cube")));
        ChemicalKey key=ChemicalKey.resolve(ResourceKinds.CHEMICAL,ResourceLocation.parse("mekanism:hydrogen"));
        Object tank=h.getBlockEntity(tankPos).getClass().getMethod("getChemicalTank").invoke(h.getBlockEntity(tankPos));
        tank.getClass().getMethod("setStack",key.sample().getClass()).invoke(tank,key.sampleWithAmount(1000));
        Object fluidTile=h.getBlockEntity(fluidPos);
        Object nativeFluidTank=((List<?>)fluidTile.getClass().getMethod("getFluidTanks",net.minecraft.core.Direction.class).invoke(fluidTile,(Object)null)).getFirst();
        nativeFluidTank.getClass().getMethod("setStack",FluidStack.class).invoke(nativeFluidTank,new FluidStack(Fluids.WATER,1000));
        var water=new FluidKey(new FluidStack(Fluids.WATER,1));
        var exposed=CompatibilityRegistry.discoverResources(h.getLevel(),h.absolutePos(fluidPos),null).stream().filter(provider->provider.resourceType().equals(ResourceKinds.FLUID)).toList();
        h.assertTrue(exposed.size()==1&&exposed.getFirst().identity()==nativeFluidTank,"Native fluid discovery replaces the generic capability provider and retains the physical tank identity");
        Object energyTile=h.getBlockEntity(energyPos);
        Object nativeEnergy=((List<?>)energyTile.getClass().getMethod("getEnergyContainers",net.minecraft.core.Direction.class).invoke(energyTile,(Object)null)).getFirst();
        Class<?> energyApi=Class.forName("mekanism.api.energy.IEnergyContainer");
        energyApi.getMethod("setEnergy",long.class).invoke(nativeEnergy,MekanismResourceSupport.energyFromNetwork(4000));
        var energyProviders=CompatibilityRegistry.discoverResources(h.getLevel(),h.absolutePos(energyPos),null).stream().filter(provider->provider.resourceType().equals(ResourceKinds.ENERGY)).toList();
        h.assertTrue(energyProviders.size()==1&&energyProviders.getFirst().identity()==nativeEnergy,"Native energy discovery preserves one physical container identity without a generic duplicate");
        if(MekanismResourceSupport.energyToNetwork(MekanismResourceSupport.energyFromNetwork(1))!=1){
            Object before=energyApi.getMethod("getEnergy").invoke(nativeEnergy);
            h.assertTrue(energyProviders.getFirst().insert(ResourceKinds.FE,1,false)==0&&before.equals(energyApi.getMethod("getEnergy").invoke(nativeEnergy)),"An unrepresentable FE fraction is not converted into free native energy");
        }
        h.assertTrue(key.equals(ResourceKeyCodec.load(ResourceKeyCodec.save(key,h.getLevel().registryAccess()),h.getLevel().registryAccess())),"The installed native chemical resolves after serialization");
        GlobalPos origin=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(nexus));
        GlobalPos physical=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(tankPos));
        var manager=NetworkManager.get(h.getLevel().getServer());NetworkManager.changed(h.getLevel(),h.absolutePos(tankPos));
        NetworkManager.changed(h.getLevel(),h.absolutePos(fluidPos));
        NetworkManager.changed(h.getLevel(),h.absolutePos(energyPos));
        h.startSequence().thenWaitUntil(()->{
            var network=manager.networkAt(origin);
            var stock=network==null?Map.<ResourceKey,Long>of():network.resourceSnapshot();
            h.assertTrue(stock.getOrDefault(key,0L)==1000&&stock.getOrDefault(water,0L)==1000&&stock.getOrDefault(ResourceKinds.FE,0L)==4000,"Bounded discovery indexes native chemical, fluid and energy containers");
        }).thenExecute(()->{
            try{
                var network=manager.networkAt(origin);
                h.assertTrue(!network.crafting().isProcessingPosition(physical),"A chemical storage tank is not a recipe processor");
                Map<GlobalPos,Long> receipts=new LinkedHashMap<>();
                long extracted=network.reserveResource(key,400,(from,count)->receipts.merge(from,count,Math::addExact));
                h.assertTrue(extracted==400&&receipts.equals(Map.of(physical,400L)),"Reservation reports exactly the confirmed physical source");
                h.assertTrue(amount(tank)==600&&network.resourceSnapshot().getOrDefault(key,0L)==600,"Reservation immediately removes chemical from both the real tank and indexed stock");
                long pending=network.insertResourceFrom(key,extracted,origin);
                h.assertTrue(pending==0&&amount(tank)==1000,"The confirmed refund returns to the real native tank");
                pending=network.insertResourceFrom(key,pending,origin);
                h.assertTrue(pending==0&&amount(tank)==1000&&network.resourceSnapshot().getOrDefault(key,0L)==1000,"Retrying the returned remainder cannot replay an already stored refund");
                h.assertTrue(network.reserveResource(water,250,(from,count)->{})==250&&network.resourceSnapshot().getOrDefault(water,0L)==750,"The native fluid tank reserves real millibuckets");
                h.assertTrue(network.insertResourceFrom(water,250,origin)==0&&network.resourceSnapshot().getOrDefault(water,0L)==1000,"The native fluid provider accepts the exact refund once");
                h.assertTrue(network.reserveResource(ResourceKinds.FE,100,(from,count)->{})==100&&network.resourceSnapshot().getOrDefault(ResourceKinds.FE,0L)==3900,"Native energy reserves an exact representable FE quantity");
                h.assertTrue(network.insertResourceFrom(ResourceKinds.FE,100,origin)==0&&network.resourceSnapshot().getOrDefault(ResourceKinds.FE,0L)==4000,"The native energy provider stores the exact FE refund");
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native chemical tank API changed",failure);}
        }).thenSucceed();
    }
    private static long amount(Object tank)throws ReflectiveOperationException{
        Object stack=tank.getClass().getMethod("getStack").invoke(tank);
        return ((Number)stack.getClass().getMethod("getAmount").invoke(stack)).longValue();
    }
    private MekanismResourceGameTests(){}
}
