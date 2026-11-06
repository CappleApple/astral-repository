package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class IndustrialForegoingGeneratorGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void everyNativeMycelialProviderDefinitionReceivesAnExactCatalogOrUnsupportedClassification(GameTestHelper h) {
        if (!Boolean.getBoolean("astral_repository.genericTechTest")) { h.succeed(); return; }
        h.assertTrue(ModList.get().isLoaded("industrialforegoing"), "Native IF generator runtime is installed");
        var audit = new IndustrialForegoingGeneratorBackend().audit(h.getLevel());
        try {
            var path = java.nio.file.Path.of("build/autocrafting-fixes/industrialforegoing-generators-native-audit.json"); java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(audit) + "\n");
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        long classified = audit.diagnostics().stream().filter(value -> value.status().equals("CATALOGUED") || value.status().equals("UNSUPPORTED")).map(IndustrialForegoingGeneratorBackend.Diagnostic::subject).distinct().count();
        h.assertTrue(audit.nativeTypes() > 0 && audit.nativeTypes() == audit.physicalHostTypes(), "Every registered native provider has a real discoverable generator boundary");
        h.assertTrue(classified == audit.declaredDefinitions() && audit.catalogProfiles() > 0, "Every declared native fuel definition has a profile or an exact unsupported contract reason");
        h.assertTrue(audit.diagnostics().stream().noneMatch(value -> value.status().equals("PROVIDER_UNAVAILABLE") || value.status().equals("HOST_UNAVAILABLE") || value.status().equals("REGISTRY_UNAVAILABLE")), "Native provider data can be read on a dedicated server");
        h.assertTrue(audit.nativeTypes()==16&&audit.diagnostics().stream().noneMatch(value->value.status().equals("UNSUPPORTED")),"All16 actual native generator providers expose their declared fuel definitions without an unsupported finite contract");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1200)
    public static void actualNativePotionFuelStreamsPastCapacityAndPreservesPreloadedEnergyAndBottle(GameTestHelper h) {
        if (!Boolean.getBoolean("astral_repository.genericTechTest")) { h.succeed(); return; }
        h.assertTrue(ModList.get().isLoaded("industrialforegoing"), "Native IF generator runtime is installed");
        BlockPos machine = new BlockPos(4,2,4), chest = new BlockPos(1,2,1);
        h.setBlock(machine, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("industrialforegoing:mycelial_potion"))); h.setBlock(chest, Blocks.BARREL);
        var tile = h.getBlockEntity(machine);
        // Native player placement assigns the generator owner; direct GameTest placement does not.
        NativeBackendReflection.call(tile,"setOwner","astral_repository_native_generator_fixture");
        Object energy = NativeBackendReflection.call(tile, "getEnergyStorage");
        try {
            var capacity = EnergyStorage.class.getDeclaredField("capacity"); capacity.setAccessible(true); capacity.setInt(energy, 1000);
            NativeBackendReflection.call(energy, "setEnergyStored", 137);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot configure small native storage fixture", failure); }
        h.assertTrue(((Number)NativeBackendReflection.call(energy, "getMaxEnergyStored")).intValue() == 1000, "Native physical capacity is deliberately smaller than one fuel cycle");
        var access = new CraftingGameTests.Fixture(h, chest, (Container)h.getBlockEntity(chest), List.of(machine), new ItemStack(Items.PAPER));
        var backend = new IndustrialForegoingGeneratorBackend(); var adapter = new NativeMachineAdapter(backend);
        var recipe = adapter.recipes(access).stream().filter(candidate -> candidate.output().equals(ResourceKinds.FE) && candidate.durationTicks() <= 100 && candidate.outputCount() > 1000).findFirst().orElseThrow(() -> new IllegalStateException("Native short potion fuel not discovered: " + tail(backend)));
        var selected = recipe.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().getFirst(), input.count())).toList();
        h.assertTrue(selected.size() == 1 && selected.getFirst().key() instanceof ItemKey && selected.getFirst().count() == 1, "Exact native potion identity is one physically consumed fuel");
        var operation = adapter.start(access, access.at(machine), new CraftPlan.Node<>(0, recipe, selected, Set.of()));
        h.assertTrue(operation != null, "The idle real generator accepts its declared native fuel");
        @SuppressWarnings("unchecked") Map<ResourceKey,Long>[] result = new Map[]{null};
        int[] observedStored = {0};
        h.onEachTick(() -> {
            observedStored[0] = Math.max(observedStored[0], ((Number)NativeBackendReflection.call(energy, "getEnergyStored")).intValue());
            if (result[0] == null) result[0] = operation.poll();
        });
        h.succeedWhen(() -> {
            h.assertTrue(result[0] != null, "The real native generator finishes its fuel-progress cycle");
            h.assertTrue(result[0].getOrDefault(ResourceKinds.FE, 0L) == recipe.outputCount() + 137, "Actual generated FE and the137 FE preloaded baseline are both conserved, with no premature production credit");
            h.assertTrue(result[0].getOrDefault(new ItemKey(new ItemStack(Items.GLASS_BOTTLE)), 0L) == 1, "The native fuel-container remainder is collected once");
            h.assertTrue(observedStored[0] <= 1000 && ((Number)NativeBackendReflection.call(energy, "getEnergyStored")).intValue() == 0, "Streaming stays within actual native capacity and drains the final real energy");
            h.assertTrue(!result[0].containsKey(selected.getFirst().key()) && operation.cancel().isEmpty(), "Consumed potion fuel is never refunded or replayed");
        });
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=3600)
    public static void actualNativeVanillaCoalUsesLoaderBurnTimeAndStreamsAllGeneratedEnergy(GameTestHelper h) throws Exception {
        if(!Boolean.getBoolean("astral_repository.genericTechTest")){h.succeed();return;}
        h.assertTrue(ModList.get().isLoaded("industrialforegoing"),"Native IF generator runtime is installed");
        BlockPos machine=new BlockPos(4,2,4),chest=new BlockPos(1,2,1);h.setBlock(machine,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("industrialforegoing:mycelial_furnace")));h.setBlock(chest,Blocks.BARREL);
        var tile=h.getBlockEntity(machine);NativeBackendReflection.call(tile,"setOwner","astral_repository_native_generator_fixture");Object energy=NativeBackendReflection.call(tile,"getEnergyStorage");
        var capacity=EnergyStorage.class.getDeclaredField("capacity");capacity.setAccessible(true);capacity.setInt(energy,1000);
        var storage=(Container)h.getBlockEntity(chest);storage.setItem(0,new ItemStack(Items.COAL));var access=new CraftingGameTests.Fixture(h,chest,storage,List.of(machine),new ItemStack(Items.PAPER));
        var backend=new IndustrialForegoingGeneratorBackend();var adapter=new NativeMachineAdapter(backend);ItemKey coal=new ItemKey(new ItemStack(Items.COAL));
        var recipe=adapter.recipes(access).stream().filter(candidate->candidate.output().equals(ResourceKinds.FE)&&candidate.ingredients().size()==1&&candidate.ingredients().getFirst().alternatives().contains(coal)).findFirst().orElseThrow(()->new IllegalStateException("Native vanilla fuel was wrongly omitted: "+tail(backend)));
        int burnTime=coal.sample().getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);
        h.assertTrue(burnTime>0&&recipe.durationTicks()==burnTime,"Metadata uses the real loader fuel map, including vanilla fuel fallback");
        ItemStack reserved=access.extract(coal,1);h.assertTrue(reserved.getCount()==1&&new ItemKey(reserved).equals(coal),"One real coal is reserved from physical storage before staging");
        var selected=List.of(new CraftPlan.Selection<ResourceKey>(coal,1));var operation=adapter.start(access,access.at(machine),new CraftPlan.Node<>(0,recipe,selected,Set.of()));h.assertTrue(operation!=null,"Native furnace generator accepts the reserved real coal");
        @SuppressWarnings("unchecked") Map<ResourceKey,Long>[] result=new Map[]{null};int[] observed={0};
        h.onEachTick(()->{observed[0]=Math.max(observed[0],((Number)NativeBackendReflection.call(energy,"getEnergyStored")).intValue());if(result[0]==null)result[0]=operation.poll();});
        h.succeedWhen(()->{
            h.assertTrue(result[0]!=null,"The native coal burn completes its actual finite progress cycle");
            h.assertTrue(result[0].equals(Map.of(ResourceKinds.FE,recipe.outputCount())),"Every actual generated FE is streamed from native storage exactly once");
            h.assertTrue(observed[0]<=1000&&((Number)NativeBackendReflection.call(energy,"getEnergyStored")).intValue()==0,"Generation respects physical capacity and drains the final real energy");
            h.assertTrue(access.snapshot().isEmpty()&&operation.cancel().isEmpty(),"The original real coal is consumed and cannot be refunded or replayed");
        });
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=3600)
    public static void actualNativeRocketFuelPreservesItsExactDataAndStreamsOneWholePhysicalCycle(GameTestHelper h) throws Exception {
        if(!Boolean.getBoolean("astral_repository.genericTechTest")){h.succeed();return;}
        h.assertTrue(ModList.get().isLoaded("industrialforegoing"),"Native IF generator runtime is installed");
        BlockPos machine=new BlockPos(4,2,4),chest=new BlockPos(1,2,1);h.setBlock(machine,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("industrialforegoing:mycelial_rocket")));h.setBlock(chest,Blocks.BARREL);
        var tile=h.getBlockEntity(machine);NativeBackendReflection.call(tile,"setOwner","astral_repository_native_generator_fixture");Object energy=NativeBackendReflection.call(tile,"getEnergyStorage");
        var storage=(Container)h.getBlockEntity(chest);var access=new CraftingGameTests.Fixture(h,chest,storage,List.of(machine),new ItemStack(Items.PAPER));
        ItemStack realRocket=rocketFuel();ItemKey requestedFuel=new ItemKey(realRocket);storage.setItem(0,realRocket.copy());
        var backend=new IndustrialForegoingGeneratorBackend();var adapter=new NativeMachineAdapter(backend);
        var definition=adapter.recipes(access).stream().filter(candidate->candidate.output().equals(ResourceKinds.FE)&&candidate.durationTicks()>1&&candidate.ingredients().size()==1&&candidate.ingredients().getFirst().alternatives().equals(List.of(requestedFuel))).findFirst().orElseThrow(()->new IllegalStateException("The native provider must accept the real rocket's exact flight/shape/colors/fade/trail/twinkle data: "+tail(backend)));
        h.assertTrue(access.snapshot().equals(Map.of(requestedFuel,1L)),"Preview preserves the real rocket's exact data and physical quantity");
        h.assertTrue(definition.outputCount()%definition.durationTicks()==0,"Native rocket metadata exposes its exact integral energy per actual progress tick");int nativePerTick=Math.toIntExact(definition.outputCount()/definition.durationTicks());
        var capacity=EnergyStorage.class.getDeclaredField("capacity");capacity.setAccessible(true);capacity.setInt(energy,nativePerTick);
        h.assertTrue(nativePerTick>0&&nativePerTick<definition.outputCount()&&((Number)NativeBackendReflection.call(energy,"getMaxEnergyStored")).intValue()==nativePerTick,"Native capacity holds only one real generated tick, less than the complete fuel cycle");
        ItemKey rocket=(ItemKey)definition.ingredients().getFirst().alternatives().getFirst();long fuelCount=definition.ingredients().getFirst().count();h.assertTrue(fuelCount==1,"One real component-bearing rocket funds exactly one native fuel cycle");
        var recipe=adapter.recipes(access).stream().filter(candidate->candidate.output().equals(ResourceKinds.FE)&&candidate.durationTicks()==definition.durationTicks()&&candidate.outputCount()==definition.outputCount()&&candidate.ingredients().equals(definition.ingredients())).findFirst().orElseThrow(()->new IllegalStateException("The exact native rocket profile remains valid at one-tick physical capacity: "+tail(backend)));
        ItemStack reserved=access.extract(rocket,Math.toIntExact(fuelCount));h.assertTrue(reserved.getCount()==fuelCount&&rocket.matches(reserved)&&storage.isEmpty(),"The exact native component/NBT-bearing rocket is reserved from actual physical storage");
        var selected=List.of(new CraftPlan.Selection<ResourceKey>(rocket,fuelCount));var operation=adapter.start(access,access.at(machine),new CraftPlan.Node<>(0,recipe,selected,Set.of()));h.assertTrue(operation!=null,"The real idle generator accepts the native declared rocket identity");
        @SuppressWarnings("unchecked") Map<ResourceKey,Long>[] result=new Map[]{null};int[] observed={0};
        h.onEachTick(()->{observed[0]=Math.max(observed[0],((Number)NativeBackendReflection.call(energy,"getEnergyStored")).intValue());if(result[0]==null)result[0]=operation.poll();});
        h.succeedWhen(()->{
            h.assertTrue(result[0]!=null,"The actual native rocket reaches the end of its declared finite progress cycle");
            h.assertTrue(result[0].equals(Map.of(ResourceKinds.FE,recipe.outputCount())),"Only every actual generated FE is collected, with no synthetic fuel, tick, or completion credit: "+result[0]);
            h.assertTrue(observed[0]<=nativePerTick&&((Number)NativeBackendReflection.call(energy,"getEnergyStored")).intValue()==0,"Streaming respects the one-tick capacity and collects the final physical energy");
            h.assertTrue(access.snapshot().isEmpty()&&!result[0].containsKey(rocket)&&operation.cancel().isEmpty()&&operation.recoverable().isEmpty(),"The original real rocket was consumed once and neither fuel nor energy can replay");
        });
    }
    private static ItemStack rocketFuel() {
        ItemStack rocket=new ItemStack(Items.FIREWORK_ROCKET);
        var explosion=new net.minecraft.world.item.component.FireworkExplosion(net.minecraft.world.item.component.FireworkExplosion.Shape.STAR,it.unimi.dsi.fastutil.ints.IntList.of(0xAA0000,0x0000AA),it.unimi.dsi.fastutil.ints.IntList.of(0xFFFFFF),true,true);
        rocket.set(net.minecraft.core.component.DataComponents.FIREWORKS,new net.minecraft.world.item.component.Fireworks(2,List.of(explosion)));
        return rocket;
    }
    private static String tail(IndustrialForegoingGeneratorBackend backend) {
        String text = backend.diagnostics().stream().filter(value -> value.status().equals("UNSUPPORTED")).map(IndustrialForegoingGeneratorBackend.Diagnostic::reason).distinct().limit(3).reduce((a,b) -> a + "; " + b).orElse("no native profile");
        return text.length() > 1200 ? text.substring(0,1200) : text;
    }
}
