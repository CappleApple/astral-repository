package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.network.RuneTransitData;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.CrystalNodeBlockEntity;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class ResourcePersistenceGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void onePhysicalStorageIdentityRetainsBothItsFluidAndEnergyKinds(GameTestHelper h){
        var nexus=new net.minecraft.core.BlockPos(3,2,3);var storage=nexus.east(2);
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(storage,AstralContent.SEED_STORAGE_CRYSTAL.get());
        var crystal=(CrystalNodeBlockEntity)h.getBlockEntity(storage);
        crystal.tank().fill(new FluidStack(Fluids.WATER,1000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        crystal.energy().receiveEnergy(7000,false);
        var water=new FluidKey(new FluidStack(Fluids.WATER,1));
        var origin=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(nexus));
        var manager=NetworkManager.get(h.getLevel().getServer());
        h.startSequence().thenWaitUntil(()->{
            var network=manager.networkAt(origin);var stock=network==null?Map.<ResourceKey,Long>of():network.resourceSnapshot();
            h.assertTrue(stock.getOrDefault(water,0L)==1000&&stock.getOrDefault(ResourceKinds.FE,0L)==7000,"Fluid and energy on one physical block are separate resource domains");
        }).thenExecute(()->{
            var network=manager.networkAt(origin);
            h.assertTrue(network.reserveResource(water,250,(from,count)->{})==250&&network.reserveResource(ResourceKinds.FE,1200,(from,count)->{})==1200,"Each resource kind reserves its own confirmed quantity");
            h.assertTrue(crystal.tank().getFluidAmount()==750&&crystal.energy().getEnergyStored()==5800,"Both physical resources change independently");
            h.assertTrue(network.insertResourceFrom(water,250,origin)==0&&network.insertResourceFrom(ResourceKinds.FE,1200,origin)==0,"Both resource kinds return through the same network");
            h.assertTrue(crystal.tank().getFluidAmount()==1000&&crystal.energy().getEnergyStored()==7000,"Refunding one resource never hides or duplicates the other");
        }).thenSucceed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void resourceCodecRetainsComponentsAndUnavailableOptionalIdentities(GameTestHelper h) {
        var registries=h.getLevel().registryAccess();
        var potion=PotionContents.createItemStack(Items.SPLASH_POTION,Potions.LONG_SWIFTNESS);
        potion.set(DataComponents.CUSTOM_NAME,Component.literal("Reserved potion"));
        var fluid=new FluidStack(Fluids.WATER,1);fluid.set(DataComponents.CUSTOM_NAME,Component.literal("Reserved water"));
        for(ResourceKey key:List.of(new ItemKey(potion),new FluidKey(fluid),ResourceKinds.ARS_SOURCE,
                new ResourceKinds.ScalarKey(ResourceLocation.parse("example:heat"),ResourceLocation.parse("example:stored_heat"))))
            h.assertTrue(key.equals(ResourceKeyCodec.load(ResourceKeyCodec.save(key,registries),registries)),"Resource identity survives its durable codec");
        var legacy=new CompoundTag();legacy.put("Stack",potion.save(registries));
        h.assertTrue(new ItemKey(potion).equals(ResourceKeyCodec.load(legacy,registries)),"The old crafting Stack field remains readable");
        CompoundTag unavailable=missingChemical();
        ResourceKey deferred=ResourceKeyCodec.load(unavailable,registries);
        h.assertTrue(deferred instanceof ResourceKeyCodec.DeferredKey,"An absent chemical cannot turn into a transferable substitute");
        h.assertTrue(ResourceKeyCodec.save(deferred,registries).equals(unavailable),"Unknown optional data is retained verbatim for a future restored registry");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void craftingJournalPreservesMixedLongCountsAndSeparatesUncertainEntries(GameTestHelper h) throws Exception {
        var registries=h.getLevel().registryAccess();
        GlobalPos origin=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(net.minecraft.core.BlockPos.ZERO));
        var potion=PotionContents.createItemStack(Items.POTION,Potions.AWKWARD);
        var item=new ItemKey(potion);var fluid=new FluidKey(new FluidStack(Fluids.WATER,1));
        ResourceKey chemical=ResourceKeyCodec.load(missingChemical(),registries);
        var resources=new LinkedHashMap<ResourceKey,Long>();resources.put(item,2L);resources.put(fluid,1234L);
        resources.put(ResourceKinds.ARS_SOURCE,5_000_000_000L);resources.put(chemical,999L);
        UUID id=UUID.randomUUID();Class<?> type=Class.forName("com.cappleapple.astralrepository.crafting.CraftRecoveryData");
        var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object journal=constructor.newInstance();
        call(type,journal,"put",new Class<?>[]{UUID.class,GlobalPos.class,Map.class},id,origin,resources);
        call(type,journal,"uncertain",new Class<?>[]{GlobalPos.class,Map.class},origin,Map.of(fluid,75L));
        CompoundTag saved=(CompoundTag)call(type,journal,"save",new Class<?>[]{CompoundTag.class,HolderLookup.Provider.class},new CompoundTag(),registries);
        Object restored=call(type,null,"load",new Class<?>[]{CompoundTag.class,HolderLookup.Provider.class},saved,registries);
        Map<?,?> pending=(Map<?,?>)call(type,restored,"pending",new Class<?>[]{GlobalPos.class,Set.class},origin,Set.of());
        h.assertTrue(pending.size()==1&&pending.containsKey(id),"Uncertain insertion records are retained but never offered for automatic replay");
        Object entry=pending.get(id);Map<?,?> actual=(Map<?,?>)call(entry.getClass(),entry,"items",new Class<?>[0]);
        h.assertTrue(actual.equals(resources),"Mixed item, fluid, scalar and unavailable chemical escrow retains exact long amounts");
        CompoundTag savedAgain=(CompoundTag)call(type,restored,"save",new Class<?>[]{CompoundTag.class,HolderLookup.Provider.class},new CompoundTag(),registries);
        h.assertTrue(savedAgain.getList("Jobs",10).size()==2,"Both confirmed and uncertain records survive resaving");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void unavailableChemicalFlightsRetainTheirDeadlineAndArriveOnce(GameTestHelper h) {
        var registries=h.getLevel().registryAccess();ResourceKey key=ResourceKeyCodec.load(missingChemical(),registries);
        var origin=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(net.minecraft.core.BlockPos.ZERO));
        var endpoint=new RuneTransitData.Endpoint(origin,null,"missing_optional_provider",null);
        var transit=new RuneTransitData();UUID rune=UUID.randomUUID();
        transit.enqueue(10,new RuneTransitData.Flight(rune,endpoint,endpoint,key,5_000_000_000L));
        var restored=RuneTransitData.load(transit.save(new CompoundTag(),registries),registries);
        var arrivals=new ArrayList<RuneTransitData.Flight>();restored.tick(100,arrivals::add);restored.tick(109,arrivals::add);
        h.assertTrue(arrivals.isEmpty()&&restored.pendingAmount(rune,key)==5_000_000_000L,"An unknown chemical remains owned in transit until its restored deadline");
        restored.tick(110,arrivals::add);restored.tick(111,arrivals::add);
        h.assertTrue(arrivals.size()==1&&arrivals.getFirst().amount()==5_000_000_000L,"A saved flight arrives once with its original long amount");
        h.assertTrue(ResourceKeyCodec.save(arrivals.getFirst().resource(),registries).equals(missingChemical()),"An unavailable registry never erases the chemical's saved identity");
        h.succeed();
    }

    private static CompoundTag missingChemical(){
        var tag=new CompoundTag();tag.putString("Type",ResourceKinds.CHEMICAL.toString());tag.putString("Resource","astral_missing_optional:chemical");
        tag.putInt("PreservedUnknownField",42);return tag;
    }
    private static Object call(Class<?> type,Object target,String name,Class<?>[] arguments,Object... values)throws Exception{
        Method method=type.getDeclaredMethod(name,arguments);method.setAccessible(true);return method.invoke(target,values);
    }
    private ResourcePersistenceGameTests(){}
}
