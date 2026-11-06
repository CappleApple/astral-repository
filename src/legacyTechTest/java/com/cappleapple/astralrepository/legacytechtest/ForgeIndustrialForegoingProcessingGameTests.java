package com.cappleapple.astralrepository.legacytechtest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_legacy_tech_test")
@PrefixGameTestTemplate(false)
public final class ForgeIndustrialForegoingProcessingGameTests {
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600)
    public static void finiteNativeLatexTransformIsDiscoveredWithoutMachineRules(GameTestHelper h) {
        if (enabled(h)) intrinsic(h, "industrialforegoing:latex_processing_unit", "industrialforegoing:dryrubber", 1, 2);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600)
    public static void finiteNativeSewageTransformConsumesExactlyOneBucket(GameTestHelper h) {
        if (enabled(h)) intrinsic(h, "industrialforegoing:sewage_composter", "industrialforegoing:fertilizer", 1, 1);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600)
    public static void finiteNativeSporeTransformKeepsConsumedSeedSeparateFromProducedCopies(GameTestHelper h) {
        if (enabled(h)) intrinsic(h, "industrialforegoing:spores_recreator", "minecraft:brown_mushroom", 2, 1);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=4800)
    public static void sporeCoordinatorRequiresOneRealSeedAndReturnsOnlyRequestedNetGrowth(GameTestHelper h){
        if(!enabled(h))return;int additional=5;ItemStack target=new ItemStack(Items.BROWN_MUSHROOM);var f=fixture(h,"industrialforegoing:spores_recreator",target);
        var recipe=f.adapter.recipes(f.access).stream().filter(value->value.output().equals(new ItemKey(target))).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        h.assertTrue(recipe.outputCount()==1&&recipe.grossOutputCount()==2&&recipe.returnedSeedCount()==1,"The native gross output and returned seed are distinct from one net new mushroom");
        var fluidInput=recipe.ingredients().stream().filter(input->input.alternatives().get(0) instanceof FluidKey).findFirst().orElseThrow();FluidKey fluid=(FluidKey)fluidInput.alternatives().get(0);int totalWater=Math.toIntExact(fluidInput.count()*additional);
        var source=((NativeFluidFixture)f.access).addTank(new BlockPos(1,2,2));source.tank().fill(new net.minecraftforge.fluids.FluidStack(fluid.sample(),totalWater),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var service=new CraftingService(f.access);var player = new net.minecraftforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "if-native-coordinator"));
        var submitted=service.request(player,target,additional);h.assertTrue(submitted.accepted(),"Net-growth request is accepted without inventing its initial seed");boolean[] supplied={false};
        h.onEachTick(()->{charge(h,f.position);service.tick();if(!supplied[0]&&service.statuses().stream().anyMatch(status->status.id().equals(submitted.jobId())&&status.state().equals("MISSING"))){h.assertTrue(f.access.snapshot().isEmpty()&&source.tank().getFluidAmount()==totalWater,"An absent real seed cannot bootstrap growth or reserve/consume the water");f.access.insert(target.copy());supplied[0]=true;}});
        h.succeedWhen(()->{
            h.assertTrue(supplied[0]&&service.activeJobs()==0&&service.statuses().stream().anyMatch(status->status.id().equals(submitted.jobId())&&status.state().equals("COMPLETE")&&status.count()==additional),"The original missing job resumes with one real supplied seed and completes its requested net growth: "+service.statuses());
            h.assertTrue(f.access.snapshot().getOrDefault(new ItemKey(target),0L)==additional+1,"The original seed and exactly five additional real mushrooms reach storage");
            h.assertTrue(source.tank().getFluidAmount()==0,"Exactly five physical native growth cycles consumed their real water");
            h.assertTrue(!CraftingService.isProcessorReserved(f.access.at(f.position)),"Self-seeding processor claims release after all native cycles");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600)
    public static void dyeMixerCoordinatorConsumesRealDyesAndPreservesNativeColorPools(GameTestHelper h) {
        if(enabled(h))dyeBatch(h,1);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void dyeMixerCoordinatorQueuesTwoFundedNativeBatchesAndReleasesClaims(GameTestHelper h){if(enabled(h))dyeBatch(h,6);}
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void capturedDyeBatchRetainsOnlyActuallyConsumedJobRedInItsRealNativePool(GameTestHelper h){
        if(!enabled(h))return;var f=fixture(h,"industrialforegoing:dye_mixer",new ItemStack(Items.WHITE_DYE));ItemKey white=new ItemKey(new ItemStack(Items.WHITE_DYE));
        var recipe=f.adapter.recipes(f.access).stream().filter(value->value.output().equals(white)&&value.outputCount()==3&&value.ingredients().stream().noneMatch(input->input.alternatives().contains(white))).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        var captured=f.adapter.capturePlan();Object tile=h.getBlockEntity(f.position),redPool=nativeField(tile,"red");var redInput=(net.minecraftforge.items.IItemHandler)nativeField(tile,"inputRed");
        ItemKey red=recipe.ingredients().stream().flatMap(input->input.alternatives().stream()).filter(ItemKey.class::isInstance).map(ItemKey.class::cast).filter(key->redInput.isItemValid(0,key.sample())).findFirst().orElseThrow();
        int maximum=((Number)NativeBackendReflection.call(redPool,"getMaxProgress")).intValue();h.assertTrue(maximum>0&&maximum%3==0,"Native RED capacity can be funded by whole real dye items");
        BlockPos preloadPos=new BlockPos(1,2,3);h.setBlock(preloadPos,Blocks.BARREL);Container preload=(Container)h.getBlockEntity(preloadPos);int left=maximum/3;
        for(int slot=0;left>0&&slot<preload.getContainerSize();slot++){int count=Math.min(left,red.sample().getMaxStackSize());preload.setItem(slot,red.sample().copyWithCount(count));left-=count;}h.assertTrue(left==0,"Every real precharge item fits in physical fixture storage");
        for(var input:recipe.ingredients())f.access.insert(((ItemKey)input.alternatives().get(0)).sample().copyWithCount(Math.toIntExact(input.count())));
        @SuppressWarnings("unchecked") CraftScheduler.Operation<ResourceKey>[] operation=new CraftScheduler.Operation[]{null};@SuppressWarnings("unchecked") Map<ResourceKey,Long>[] result=new Map[]{null};
        h.onEachTick(()->{
            charge(h,f.position);
            if(operation[0]==null){
                if(((Number)NativeBackendReflection.call(redPool,"getProgress")).intValue()==maximum&&redInput.getStackInSlot(0).isEmpty()){
                    h.assertTrue(preload.isEmpty(),"Full RED charge consumed only its real separately stored precharge items");
                    var selected=recipe.ingredients().stream().map(input->new CraftPlan.Selection<ResourceKey>(input.alternatives().get(0),input.count())).toList();
                    for(var input:selected)h.assertTrue(f.access.reserveResource(input.key(),input.count(),(origin,count)->{})==input.count(),"Captured batch input is reserved from actual job storage");
                    operation[0]=captured.start(f.access,f.access.at(f.position),new CraftPlan.Node<>(0,recipe,selected,Set.of()));h.assertTrue(operation[0]!=null,"The captured RGB0 cost remains valid after only real native pool progress changes");
                }else for(int slot=0;slot<preload.getContainerSize();slot++){
                    ItemStack stored=preload.getItem(slot);if(stored.isEmpty())continue;int accepted=stored.getCount()-redInput.insertItem(0,stored.copy(),true).getCount();if(accepted==0)continue;
                    ItemStack taken=preload.removeItem(slot,accepted);ItemStack rejected=redInput.insertItem(0,taken,false);h.assertTrue(rejected.isEmpty(),"Confirmed physical RED precharge insertion succeeds");
                }
            }
            if(operation[0]!=null&&result[0]==null)result[0]=operation[0].poll();
        });
        h.succeedWhen(()->{
            h.assertTrue(result[0]!=null,"The captured actual native batch finishes after the real precharge");
            h.assertTrue(result[0].equals(Map.of(white,3L)),"Exactly three actual dyes return without refunding the RED physically consumed into native setup: expected="+Map.of(white,3L)+", actual="+result[0]+", RGB="+NativeBackendReflection.call(redPool,"getProgress")+"/"+NativeBackendReflection.call(nativeField(tile,"green"),"getProgress")+"/"+NativeBackendReflection.call(nativeField(tile,"blue"),"getProgress")+", stagedRED="+redInput.getStackInSlot(0));
            h.assertTrue(((Number)NativeBackendReflection.call(redPool,"getProgress")).intValue()==maximum,"Three real output cycles consume three RED units and native canIncrease consumes the one staged real RED item to refill exactly three units");
            h.assertTrue(f.access.snapshot().isEmpty()&&preload.isEmpty()&&operation[0].cancel().isEmpty(),"No consumed job item, retained native pool material, or collected output is duplicated by collection or cancellation");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void fermentationStationConservesExactOreFluidDataThroughOneNativeCycle(GameTestHelper h) {
        if (enabled(h)) finiteFluid(h,"industrialforegoing:fermentation_station",true);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void fluidSieveConsumesTaggedNativeOreFluidAndCollectsOnlyActualDust(GameTestHelper h) {
        if (enabled(h)) finiteFluid(h,"industrialforegoing:fluid_sieving_machine",false);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600)
    public static void stoneworkConfiguredFiniteSmeltingDoesNotInventWorldGenerationInputs(GameTestHelper h) {
        if (!enabled(h)) return;
        var f=fixture(h,"industrialforegoing:material_stonework_factory",new ItemStack(Items.STONE));Object tile=h.getBlockEntity(f.position);
        Object[] actions=(Object[])nativeField(tile,"ACTION_RECIPES");int smelt=-1,none=-1;
        for(int index=0;index<actions.length;index++){String action=String.valueOf(NativeBackendReflection.call(actions[index],"getAction"));if(action.equals("smelt"))smelt=index;if(action.equals("none"))none=index;}
        h.assertTrue(smelt>=0&&none>=0,"Actual registered native action definitions include smelting and idle");
        setNativeInt(tile,"firstRecipeId",smelt);for(String field:List.of("secondRecipeId","thirdRecipeId","fourthRecipeId"))setNativeInt(tile,field,none);
        var recipe=f.adapter.recipes(f.access).stream().filter(value -> value.id().contains("/industrialforegoing:intrinsic/") && value.output().equals(new ItemKey(new ItemStack(Items.STONE))) && value.ingredients().size()==1 && value.ingredients().get(0).alternatives().contains(new ItemKey(new ItemStack(Items.COBBLESTONE)))).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        h.assertTrue(recipe.ingredients().get(0).count()==1,"The native finite action consumes one real cobblestone without invented water/lava");
        var operation=start(h,f,recipe);@SuppressWarnings("unchecked") Map<ResourceKey,Long>[] result=new Map[]{null};
        h.onEachTick(()->{charge(h,f.position);if(result[0]==null)result[0]=operation.poll();});
        h.succeedWhen(()->{h.assertTrue(result[0]!=null&&result[0].getOrDefault(recipe.output(),0L)==1,"One actual configured native smelting action produces stone");h.assertTrue(operation.cancel().isEmpty(),"Consumed stonework input cannot replay");});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void stoneworkFiniteGenerationConsumesActualRegisteredWaterAndLava(GameTestHelper h) {
        if(enabled(h))stoneworkGeneration(h,Items.DIORITE);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void stoneworkObsidianConsumesRealLavaAndPreservesItsNativeWaterSetup(GameTestHelper h) {
        if(enabled(h))stoneworkGeneration(h,Items.OBSIDIAN);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void stoneworkNetherrackSharesItsNativeLavaTankWithProtectedRetainedSetup(GameTestHelper h) {
        if(enabled(h))stoneworkGeneration(h,Items.NETHERRACK);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop")
    public static void stoneworkCancellationReturnsOnlyOwnedLavaAndPreservesItsRealRetainedBaseline(GameTestHelper h) {
        if(enabled(h))stoneworkGeneration(h,Items.NETHERRACK,true);
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400, batch="industrialforegoing_native_enchanting")
    public static void nativeFactoryCatalogDoesNotRollAndCollectsTheActualChanceEnchant(GameTestHelper h) throws Exception {
        if(!enabled(h))return;
        ItemStack target=enchantedTarget(h);var f=fixture(h,"industrialforegoing:enchantment_factory",target);var access=(NativeFluidFixture)f.access;access.taught=List.of();
        f.access.insert(new ItemStack(Items.DIAMOND_SWORD));var essenceSource=access.addTank(new BlockPos(1,2,2));Object essence=NativeBackendReflection.call(Class.forName("com.buuz135.industrial.module.ModuleCore").getField("ESSENCE").get(null),"getSourceFluid");if(!(essence instanceof net.minecraft.world.level.material.Fluid))essence=NativeBackendReflection.call(essence,"get");essenceSource.tank().fill(new net.minecraftforge.fluids.FluidStack((net.minecraft.world.level.material.Fluid)essence,30_000),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var originalStock=f.access.resourceSnapshot();Object before=f.adapter.catalogState(f.access);h.assertTrue(before.equals(f.adapter.catalogState(f.access)),"Native catalogue state is stable while stock, mode and taught outputs are unchanged");
        h.getLevel().random.setSeed(425971L);var expected=net.minecraft.util.RandomSource.create(425971L);
        h.assertTrue(f.adapter.recipes(f.access).stream().noneMatch(value->value.output().equals(new ItemKey(target))),"An untaught exact enchant is absent while only real unenchanted input/essence stock exists");
        access.taught=List.of(target.copy());Object after=f.adapter.catalogState(f.access);h.assertTrue(!before.equals(after),"Teaching the exact native output invalidates its catalogue state without any stock or machine change");
        var recipe=f.adapter.recipes(f.access).stream().filter(value -> value.output().equals(new ItemKey(target))).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        h.assertTrue(h.getLevel().random.nextLong()==expected.nextLong(),"Exact enchanting metadata validation never advances native RNG");
        h.assertTrue(f.access.resourceSnapshot().equals(originalStock),"Both catalogue passes preserve every real stored input and essence quantity");
        h.assertTrue(recipe.ingredients().stream().anyMatch(input -> input.alternatives().get(0) instanceof FluidKey)&&recipe.ingredients().stream().filter(input -> input.alternatives().get(0) instanceof ItemKey).mapToLong(CraftRecipe.Ingredient::count).sum()==1,"One real native item and its exact essence quantity are staged");
        long essenceCost=0;for(var input:recipe.ingredients()){ResourceKey key=input.alternatives().get(0);long reserved=f.access.reserveResource(key,input.count(),(origin,count)->{});h.assertTrue(reserved==input.count(),"Actual native input is reserved from physical item/fluid storage");if(key instanceof FluidKey)essenceCost+=reserved;}final long reservedEssence=essenceCost;
        var operation=start(h,f,recipe);h.assertTrue(operation.mayReturnShortOutput(),"A requested exact enchant is a native chance attempt");
        @SuppressWarnings("unchecked") Map<ResourceKey,Long>[] result=new Map[]{null};h.onEachTick(()->{charge(h,f.position);if(result[0]==null)result[0]=operation.poll();});
        h.succeedWhen(()->{
            h.assertTrue(result[0]!=null,"One real native enchanting cycle completes");
            h.assertTrue(result[0].entrySet().stream().filter(entry -> entry.getKey() instanceof ItemKey).mapToLong(Map.Entry::getValue).sum()==1,"Exactly one physically rolled item is collected");
            var actual=result[0].keySet().stream().filter(ItemKey.class::isInstance).map(ItemKey.class::cast).findFirst().orElseThrow();
            h.assertTrue(actual.sample().is(Items.DIAMOND_SWORD)&&NativeEnchantmentTargets.isTarget(actual.sample()),"The real native enchantments are preserved even when they differ from the requested set");
            for(var input:recipe.ingredients())h.assertTrue(result[0].getOrDefault(input.alternatives().get(0),0L)==0,"The declared native item and exact essence were consumed rather than returned as surplus");
            h.assertTrue(f.access.snapshot().isEmpty()&&essenceSource.tank().getFluidAmount()==30_000-reservedEssence,"Only the actually reserved original sword and exact source essence were consumed");
            h.assertTrue(operation.cancel().isEmpty(),"Consumed native item/essence and collected enchantments cannot replay");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop")
    public static void nativeApplicatorDeclinesNonemptyExternalFeedAndClaimsItsLoadedAlias(GameTestHelper h) {
        if(!enabled(h))return;
        ItemStack target=enchantedTarget(h);var f=fixture(h,"industrialforegoing:enchantment_applicator",target);var machine=f.backend.machine(f.access,f.access.at(f.position));
        h.assertTrue(machine.claimedPositions().contains(f.access.at(f.position.above())),"The loaded native above-feed position is an exclusive processor alias");
        h.setBlock(f.position.above(),com.cappleapple.astralrepository.content.AstralContent.SEED_STORAGE_CRYSTAL.get());
        var source=(com.cappleapple.astralrepository.content.CrystalNodeBlockEntity)h.getBlockEntity(f.position.above());
        source.tank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,250),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var blocked=f.backend.machine(f.access,f.access.at(f.position));h.assertTrue(blocked==null||!blocked.valid(),"The real nonempty native external feed invalidates processor admission");
        h.assertTrue(f.adapter.recipes(f.access).stream().noneMatch(recipe -> recipe.id().contains("/industrialforegoing:intrinsic/")),"A nonempty native external feed prevents targeted admission rather than contributing unowned essence");
        h.assertTrue(source.tank().getFluidAmount()==250,"Read-only admission never drains external native fluid");h.succeed();
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void nativeExtractorReservesAnActualEnchantedInputAndCollectsBothRealProducts(GameTestHelper h) {
        if(!enabled(h))return;
        ItemStack source=enchantedTarget(h),target=enchanted(h,Items.ENCHANTED_BOOK);var f=fixture(h,"industrialforegoing:enchantment_extractor",target);
        f.access.insert(source);f.access.insert(new ItemStack(Items.BOOK));
        var machine=f.backend.machine(f.access,f.access.at(f.position));var nativeRecipe=f.backend.recipes(f.access,List.of(machine),List.of(new ItemKey(target))).stream().filter(recipe->recipe.guaranteed().containsKey(new ItemKey(target))).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        ItemKey stripped=nativeRecipe.guaranteed().keySet().stream().filter(ItemKey.class::isInstance).map(ItemKey.class::cast).filter(key->key.sample().is(Items.DIAMOND_SWORD)&&!NativeEnchantmentTargets.isTarget(key.sample())).findFirst().orElseThrow();
        var service=new CraftingService(f.access);
        var player = new net.minecraftforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "if-native-coordinator"));
        h.assertTrue(service.request(player,target,1).accepted(),"The actual enchanted-input extraction request is accepted");h.onEachTick(()->{charge(h,f.position);service.tick();});
        h.succeedWhen(()->{
            h.assertTrue(service.activeJobs()==0&&service.statuses().stream().anyMatch(status->status.state().equals("COMPLETE")),"Actual native extraction finishes: "+service.statuses());
            var stock=f.access.snapshot();h.assertTrue(stock.getOrDefault(new ItemKey(target),0L)==1&&stock.getOrDefault(stripped,0L)==1,"One actual enchanted book and the actual stripped sword retain their full native data");
            h.assertTrue(stock.getOrDefault(new ItemKey(source),0L)==0&&stock.getOrDefault(new ItemKey(new ItemStack(Items.BOOK)),0L)==0,"Exactly the real enchanted sword and one book were consumed");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=2400)
    public static void nativeApplicatorUsesStoredBookAndPhysicalEssenceWithoutExternalFeed(GameTestHelper h) throws Exception {
        if(!enabled(h))return;
        ItemStack target=enchantedTarget(h),book=enchanted(h,Items.ENCHANTED_BOOK);target.getOrCreateTag().putInt("RepairCost",1);var f=fixture(h,"industrialforegoing:enchantment_applicator",target);
        f.access.insert(new ItemStack(Items.DIAMOND_SWORD));f.access.insert(book);var source=((NativeFluidFixture)f.access).addTank(new BlockPos(1,2,2));
        var state=h.getBlockState(f.position);var probe=((net.minecraft.world.level.block.EntityBlock)state.getBlock()).newBlockEntity(h.absolutePos(f.position),state);
        NativeBackendReflection.call(nativeField(probe,"inputFirst"),"setStackInSlot",0,new ItemStack(Items.DIAMOND_SWORD));NativeBackendReflection.call(nativeField(probe,"inputSecond"),"setStackInSlot",0,book.copy());probe.setLevel(h.getLevel());
        Object nativeResult=NativeBackendReflection.call(probe,"updateRepairOutput");ItemStack nativeOutput=(ItemStack)NativeBackendReflection.call(nativeResult,"getLeft");int nativeLevel=((Number)NativeBackendReflection.call(nativeResult,"getRight")).intValue();
        h.assertTrue(!nativeOutput.isEmpty()&&new ItemKey(nativeOutput).equals(new ItemKey(target)),"The actual native anvil algorithm accepts these exact fixture inputs and produces the requested full item data");
        int nativeCost=((Number)NativeBackendReflection.call(probe,"getEssenceConsumed",nativeLevel)).intValue();h.assertTrue(nativeCost>0&&nativeCost<=20_000,"The actual native anvil metadata requires a feasible real essence quantity");
        Object essence=NativeBackendReflection.call(Class.forName("com.buuz135.industrial.module.ModuleCore").getField("ESSENCE").get(null),"getSourceFluid");if(!(essence instanceof net.minecraft.world.level.material.Fluid))essence=NativeBackendReflection.call(essence,"get");
        source.tank().fill(new net.minecraftforge.fluids.FluidStack((net.minecraft.world.level.material.Fluid)essence,20_000),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var realTile=h.getBlockEntity(f.position);h.assertTrue(((net.minecraftforge.items.IItemHandler)nativeField(realTile,"inputFirst")).isItemValid(0,new ItemStack(Items.DIAMOND_SWORD))&&((net.minecraftforge.items.IItemHandler)nativeField(realTile,"inputSecond")).isItemValid(0,book),"The actual native input predicates accept both fixture items");
        h.assertTrue(Boolean.TRUE.equals(NativeBackendReflection.call(nativeField(realTile,"tank"),"isFluidValid",new net.minecraftforge.fluids.FluidStack((net.minecraft.world.level.material.Fluid)essence,1))),"The actual native tank predicate accepts this physical essence identity");
        var recipe=f.adapter.recipes(f.access).stream().filter(value->value.output().equals(new ItemKey(target))).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        long cost=recipe.ingredients().stream().filter(input->input.alternatives().get(0) instanceof FluidKey).mapToLong(CraftRecipe.Ingredient::count).sum();h.assertTrue(cost==nativeCost,"Read-only metadata matches the independently queried native anvil essence cost");
        var service=new CraftingService(f.access);var player = new net.minecraftforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "if-native-coordinator"));
        h.assertTrue(service.request(player,target,1).accepted(),"Native deterministic enchantment application is accepted");h.onEachTick(()->{charge(h,f.position);service.tick();});
        h.succeedWhen(()->{
            h.assertTrue(service.activeJobs()==0&&service.statuses().stream().anyMatch(status->status.state().equals("COMPLETE")),"Native book application finishes: "+service.statuses());
            h.assertTrue(f.access.snapshot().getOrDefault(new ItemKey(target),0L)==1&&f.access.snapshot().getOrDefault(new ItemKey(book),0L)==0,"The actual stored book is consumed and its exact enchantments reach the original sword");
            h.assertTrue(source.tank().getFluidAmount()==20_000-cost,"Only the exact native anvil-derived essence amount was consumed");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=3200)
    public static void nativeConfiguredBrewerProducesOneExactPotionThroughTheWholePrefix(GameTestHelper h){if(enabled(h))configuredBrewer(h,1);}
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=3200)
    public static void nativeConfiguredBrewerProducesTwoExactPotionsThroughTheWholePrefix(GameTestHelper h){if(enabled(h))configuredBrewer(h,2);}
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=3200)
    public static void nativeConfiguredBrewerProducesThreeExactPotionsThroughTheWholePrefix(GameTestHelper h){if(enabled(h))configuredBrewer(h,3);}
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=4800)
    public static void nativeConfiguredBrewerQueuesTwoPhysicalPrefixesForSixPotions(GameTestHelper h){if(enabled(h))configuredBrewer(h,6);}
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600, batch="industrialforegoing_tag_choice")
    public static void symbolicNativeTagChoicesDoNotRollRngAndCollectOneActualSludgeProduct(GameTestHelper h) {
        if (!enabled(h)) return;
        var f = fixture(h, "industrialforegoing:sludge_refiner");
        h.getLevel().random.setSeed(86421357L); var expectedRandom = net.minecraft.util.RandomSource.create(86421357L);
        var catalog = f.adapter.recipes(f.access).stream().filter(recipe -> recipe.id().contains("/industrialforegoing:intrinsic/")).toList();
        h.assertTrue(h.getLevel().random.nextLong() == expectedRandom.nextLong(), "Cataloguing symbolic random-tag alternatives never calls the world RNG");
        Set<ResourceKey> nativeChoices = new HashSet<>();
        try {
            @SuppressWarnings("unchecked") var tag = (net.minecraft.tags.TagKey<Item>)Class.forName("com.buuz135.industrial.utils.IndustrialTags$Items").getField("SLUDGE_OUTPUT").get(null);
            BuiltInRegistries.ITEM.getTag(tag).orElseThrow().forEach(holder -> nativeChoices.add(new ItemKey(new ItemStack(holder.value()))));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        h.assertTrue(nativeChoices.size() > 1 && catalog.stream().map(CraftRecipe::output).collect(java.util.stream.Collectors.toSet()).equals(nativeChoices), "Every native tag alternative has a chance alias: " + diagnostic(f));
        var recipe = catalog.get(0);
        h.assertTrue(recipe.ingredients().size() == 1 && recipe.ingredients().get(0).count() == 500, "Native successful completion consumes500 mB sludge");
        var operation = start(h, f, recipe); h.assertTrue(operation.mayReturnShortOutput(), "An actual different tag choice is a short attempt rather than a guaranteed requested item");
        @SuppressWarnings("unchecked") Map<ResourceKey, Long>[] result = new Map[]{null};
        h.onEachTick(() -> { charge(h, f.position); if (result[0] == null) result[0] = operation.poll(); });
        h.succeedWhen(() -> {
            h.assertTrue(result[0] != null, "One real native random-tag cycle finishes");
            h.assertTrue(result[0].size() == 1 && result[0].values().stream().mapToLong(Long::longValue).sum() == 1 && nativeChoices.contains(result[0].keySet().iterator().next()), "Only one physically rolled native item is collected, never every possible output");
            h.assertTrue(operation.cancel().isEmpty(), "Finished random cycle cannot refund consumed sludge or duplicate output");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600)
    public static void nativeStoredRecipeCompletionCollectsMachineSpecificEssenceCoproduct(GameTestHelper h) {
        if (!enabled(h)) return;
        var f = fixture(h, "industrialforegoing:resourceful_furnace");
        var catalog = f.adapter.recipes(f.access);
        var recipe = catalog.stream().filter(value -> value.id().contains("/industrialforegoing:stored/")
                && value.id().endsWith("/minecraft:iron_ingot_from_smelting_raw_iron") && value.output() instanceof ItemKey).findFirst()
                .orElseThrow(() -> new IllegalStateException("Native stored completion not catalogued: " + diagnostic(f)));
        var fluidAlias = catalog.stream().filter(value -> value.id().endsWith("/minecraft:iron_ingot_from_smelting_raw_iron") && value.output() instanceof FluidKey).findFirst().orElseThrow();
        Object nativeRecipe = NativeRecipeSource.entries(h.getLevel()).stream().filter(value -> value.id().toString().equals("minecraft:iron_ingot_from_smelting_raw_iron")).findFirst().orElseThrow().value();
        int expected = (int)(((Number)NativeBackendReflection.call(nativeRecipe,"getExperience")).floatValue() * 20);
        h.assertTrue(fluidAlias.outputCount() == expected && expected > 0, "Native completion bytecode discovers the exact stored-recipe experience-derived coproduct");
        var operation = start(h,f,recipe);
        @SuppressWarnings("unchecked") Map<ResourceKey,Long>[] result = new Map[]{null};
        h.onEachTick(() -> { charge(h,f.position); if (result[0] == null) result[0] = operation.poll(); });
        h.succeedWhen(() -> {
            h.assertTrue(result[0] != null, "One physical native stored-recipe cycle finishes");
            h.assertTrue(result[0].getOrDefault(recipe.output(),0L) == 1 && result[0].getOrDefault(fluidAlias.output(),0L) == expected,
                    "Both the real ingot and every real essence millibucket are collected from their native buffers");
            h.assertTrue(operation.cancel().isEmpty(), "Collected coproducts and consumed inputs cannot replay");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600)
    public static void resourcefulFurnaceCoordinatorUsesNativeDiscoveryWithTheOldRuleExcluded(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos position = new BlockPos(4, 2, 4), chest = new BlockPos(1, 2, 1);
        h.setBlock(position, BuiltInRegistries.BLOCK.get(new ResourceLocation("industrialforegoing:resourceful_furnace"))); h.setBlock(chest, Blocks.BARREL);
        Container storage = (Container) h.getBlockEntity(chest); storage.setItem(0, new ItemStack(Items.RAW_IRON));
        var access = new ForgeLegacyTechGameTests.Fixture(h, chest, storage, List.of(position), new ItemStack(Items.IRON_INGOT)) {
            @Override public Set<String> excludedRecipes(ItemStack target) { return Set.of("astral_repository:resourceful_smelting/minecraft:iron_ingot_from_smelting_raw_iron"); }
        };
        var service = new CraftingService(access);
        var player = new net.minecraftforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "if-native-coordinator"));
        h.assertTrue(service.request(player, new ItemStack(Items.IRON_INGOT), 1).accepted(), "Native coordinator request is accepted while the old block-rule recipe is explicitly excluded");
        h.onEachTick(() -> { charge(h, position); service.tick(); });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0, "Native Resourceful Furnace request finishes: " + service.statuses());
            h.assertTrue(access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 1 && !access.snapshot().containsKey(new ItemKey(new ItemStack(Items.RAW_IRON))), "One real reserved raw iron becomes exactly one collected ingot through native discovery");
            h.assertTrue(!CraftingService.isProcessorReserved(access.at(position)), "Native processor reservation is released");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop", timeoutTicks=1600)
    public static void nativeTitaniumDissolutionConsumesItemAndFluidAndCollectsBothOutputs(GameTestHelper h) {
        if (!enabled(h)) return;
        var f = fixture(h, "industrialforegoing:dissolution_chamber");
        var recipe = f.adapter.recipes(f.access).stream().filter(value -> value.id().endsWith("/industrialforegoing:dissolution_chamber/pink_slime_ball") && value.output() instanceof ItemKey).findFirst().orElseThrow();
        var operation = start(h, f, recipe);
        @SuppressWarnings("unchecked") Map<ResourceKey, Long>[] result = new Map[]{null};
        h.onEachTick(() -> { charge(h, f.position); if (result[0] == null) result[0] = operation.poll(); });
        h.succeedWhen(() -> {
            h.assertTrue(result[0] != null, "Native dissolution finishes: " + h.getBlockEntity(f.position).saveWithFullMetadata());
            h.assertTrue(result[0].entrySet().stream().anyMatch(entry -> entry.getKey() instanceof ItemKey item && item.sample().is(BuiltInRegistries.ITEM.get(new ResourceLocation("industrialforegoing:pink_slime"))) && entry.getValue() == 1), "One real pink-slime item is collected");
            h.assertTrue(result[0].entrySet().stream().anyMatch(entry -> entry.getKey() instanceof FluidKey fluid && fluid.id().toString().equals("minecraft:water") && entry.getValue() == 150), "All150 mB actual water co-product is collected");
            h.assertTrue(f.backend.machine(f.access, f.access.at(f.position)).ports(nativeRecipe(f)).stream().allMatch(port -> port.snapshot().isEmpty()), "All delivered real inputs were consumed and outputs extracted");
            h.assertTrue(operation.cancel().isEmpty(), "Completed native attempt cannot refund twice");
        });
    }

    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop")
    public static void nativeTitaniumCancellationReturnsExactInputsAndHonorsPredicates(GameTestHelper h) {
        if (!enabled(h)) return;
        var f = fixture(h, "industrialforegoing:dissolution_chamber");
        var recipe = f.adapter.recipes(f.access).stream().filter(value -> value.id().endsWith("/industrialforegoing:dissolution_chamber/pink_slime_ball") && value.output() instanceof ItemKey).findFirst().orElseThrow();
        var selected = recipe.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().get(0), input.count())).toList();
        Map<ResourceKey, Long> expected = new HashMap<>(); selected.forEach(value -> expected.merge(value.key(), value.count(), Long::sum));
        var operation = f.adapter.start(f.access, f.access.at(f.position), new CraftPlan.Node<>(0, recipe, selected, Set.of()));
        h.assertTrue(operation != null, "Native discovered ports accept selected resources");
        h.assertTrue(operation.poll() == null, "Initial polling stages exact resources without running native finish code");
        h.assertTrue(operation.cancel().equals(expected) && operation.cancel().isEmpty(), "Item and fluid escrow return exactly once before native consumption");
        var nativeRecipe = nativeRecipe(f);
        h.assertTrue(f.backend.machine(f.access, f.access.at(f.position)).ports(nativeRecipe).stream().allMatch(port -> port.snapshot().isEmpty()), "Cancelled physical native inventories and tanks are empty");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_legacy_tech_test", template="empty_workshop")
    public static void allNativeIndustrialRecipesAndMachineSurfacesReceiveExplicitAuditClassification(GameTestHelper h) {
        if (!enabled(h)) return;
        var f = fixture(h, "industrialforegoing:dissolution_chamber");
        var host = f.backend.machine(f.access, f.access.at(f.position));
        var recipes = f.backend.recipes(f.access, List.of(host));
        var diagnostics = f.backend.diagnostics();
        var processors = f.backend.auditProcessors(h.getLevel());
        Set<String> expected = new HashSet<>();
        for (var holder : NativeRecipeSource.entries(h.getLevel())) if (BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).getNamespace().equals("industrialforegoing")) expected.add(holder.id().toString());
        Set<String> actual = new HashSet<>(); diagnostics.stream().filter(value -> expected.contains(value.subject())).forEach(value -> actual.add(value.subject()));
        h.assertTrue(actual.equals(expected), "Every enabled native recipe has a catalog alias or an exact unsupported contract reason");
        h.assertTrue(processors.size() >= 20 && processors.stream().noneMatch(value -> value.status().isBlank() || value.reason().isBlank()), "Every registered Industrial Foregoing block-entity surface is classified");
        h.assertTrue(processors.stream().anyMatch(value -> value.status().equals("WORLD_OR_ENTITY_WORKER")), "World/entity work is explicitly classified rather than silently treated as an inventory recipe");
        h.assertTrue(!recipes.isEmpty(), "Native stored recipes produce physical processing profiles");
        try {
            Path output = Path.of(System.getProperty("astral_repository.genericTechOutput")).resolve("industrialforegoing-native-audit.json"); Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("enabled_native_recipes", expected.size(), "catalog_profiles", recipes.size(), "recipes", diagnostics, "processors", processors)) + "\n");
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        h.succeed();
    }

    private record Fixture(BlockPos position, ForgeLegacyTechGameTests.Fixture access, IndustrialForegoingBackend backend, NativeMachineAdapter adapter) {}
    private static void dyeBatch(GameTestHelper h,int requested){
        var f=fixture(h,"industrialforegoing:dye_mixer",new ItemStack(Items.WHITE_DYE));List<Object> pools=List.of(nativeField(h.getBlockEntity(f.position),"red"),nativeField(h.getBlockEntity(f.position),"green"),nativeField(h.getBlockEntity(f.position),"blue"));
        h.assertTrue(pools.stream().allMatch(pool->((Number)NativeBackendReflection.call(pool,"getProgress")).intValue()==0),"The real RGB pools start empty");
        ItemKey white=new ItemKey(new ItemStack(Items.WHITE_DYE));var recipe=f.adapter.recipes(f.access).stream().filter(value->value.output().equals(white)&&value.outputCount()==3&&value.ingredients().stream().noneMatch(input->input.alternatives().contains(white))).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        h.assertTrue(recipe.outputCount()==3&&recipe.returnedSeedCount()==0,"One actual colored RGB batch owns three native cycles without using white dye as its own seed");h.assertTrue(pools.stream().allMatch(pool->((Number)NativeBackendReflection.call(pool,"getProgress")).intValue()==0),"Preview leaves every actual RGB pool unchanged");
        int batches=(requested+2)/3;for(var input:recipe.ingredients()){h.assertTrue(input.alternatives().get(0) instanceof ItemKey,"RGB preparation uses real dye items");f.access.insert(((ItemKey)input.alternatives().get(0)).sample().copyWithCount(Math.toIntExact(input.count()*batches)));}
        var service=new CraftingService(f.access);var player = new net.minecraftforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "if-native-coordinator"));
        var submitted=service.request(player,new ItemStack(Items.WHITE_DYE),requested);h.assertTrue(submitted.accepted(),"Native dye batch request is accepted");h.onEachTick(()->{charge(h,f.position);service.tick();});
        h.succeedWhen(()->{
            h.assertTrue(service.activeJobs()==0&&service.statuses().stream().anyMatch(status->status.id().equals(submitted.jobId())&&status.state().equals("COMPLETE")&&status.count()==requested),"The original job completes every actual funded dye batch: "+service.statuses());
            h.assertTrue(f.access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.WHITE_DYE)),0L)==3*batches,"Every actual dye, including native batch surplus, reaches physical storage");
            for(var input:recipe.ingredients())h.assertTrue(f.access.snapshot().getOrDefault(input.alternatives().get(0),0L)==0,"Exactly the funded RGB items were consumed");
            h.assertTrue(pools.stream().allMatch(pool->((Number)NativeBackendReflection.call(pool,"getProgress")).intValue()==0),"Owned native RGB processing returns to its original pool baseline");
            h.assertTrue(!CraftingService.isProcessorReserved(f.access.at(f.position)),"All processor claims release after the funded cycles finish");
        });
    }
    private static void configuredBrewer(GameTestHelper h,int bottles){
        int prefixes=(bottles+2)/3;
        ItemStack target=net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION),net.minecraft.world.item.alchemy.Potions.STRENGTH);
        var f=fixture(h,"industrialforegoing:potion_brewer",target);Object tile=h.getBlockEntity(f.position),bundle=nativeField(tile,"brewingItems");
        ItemStack[] filters=(ItemStack[])NativeBackendReflection.call(bundle,"getFilter");h.assertTrue(filters.length>=2,"Actual native configured reagent prefix is exposed");filters[0]=new ItemStack(Items.NETHER_WART);filters[1]=new ItemStack(Items.BLAZE_POWDER);NativeBackendReflection.call(bundle,"setLocked",true);
        Object water=nativeField(tile,"water"),blaze=nativeField(tile,"blaze");
        h.assertTrue(((net.minecraftforge.fluids.capability.IFluidHandler)water).fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==1000,"The native retained water setup contains exactly one real bucket");
        h.assertTrue(((Number)NativeBackendReflection.call(blaze,"getProgress")).intValue()==0,"The native fuel pool starts uncharged");
        f.access.insert(new ItemStack(Items.GLASS_BOTTLE,bottles));f.access.insert(new ItemStack(Items.NETHER_WART,prefixes));f.access.insert(new ItemStack(Items.BLAZE_POWDER,prefixes+1));
        var service=new CraftingService(f.access);var player = new net.minecraftforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "if-native-coordinator"));
        var submitted=service.request(player,target,bottles);h.assertTrue(submitted.accepted(),"Exact configured whole-prefix potion request is accepted");h.onEachTick(()->{charge(h,f.position);service.tick();});
        h.succeedWhen(()->{
            h.assertTrue(service.activeJobs()==0&&service.statuses().stream().anyMatch(status->status.id().equals(submitted.jobId())&&status.state().equals("COMPLETE")&&status.count()==bottles),"The original job completes all native configured prefixes: "+service.statuses()+"; machine="+h.getBlockEntity(f.position).saveWithFullMetadata());
            var stock=f.access.snapshot();h.assertTrue(stock.getOrDefault(new ItemKey(target),0L)==bottles,"Exactly the real requested bottle count retains native final strength-potion data");
            h.assertTrue(stock.entrySet().stream().filter(entry->entry.getKey().sample().is(Items.POTION)).allMatch(entry->entry.getKey().equals(new ItemKey(target))),"Intermediate water and awkward potions are not collected before the native prefix finishes");
            for(Item item:List.of(Items.GLASS_BOTTLE,Items.NETHER_WART,Items.BLAZE_POWDER))h.assertTrue(stock.getOrDefault(new ItemKey(new ItemStack(item)),0L)==0,"Exact real bottle/reagent/fuel inputs were consumed: "+item);
            h.assertTrue(((Number)NativeBackendReflection.call(water,"getFluidAmount")).intValue()==1000,"Native retained water setup is preserved");
            h.assertTrue(((Number)NativeBackendReflection.call(blaze,"getProgress")).intValue()==20-2*prefixes,"One real network blaze powder charged20 native uses and every actual two-stage prefix consumed exactly two");
        });
    }
    private static void stoneworkGeneration(GameTestHelper h,Item target) {stoneworkGeneration(h,target,false);}
    private static void stoneworkGeneration(GameTestHelper h,Item target,boolean cancel) {
        var f=fixture(h,"industrialforegoing:material_stonework_factory",new ItemStack(target));Object tile=h.getBlockEntity(f.position);
        String nativeId="industrialforegoing:stonework_generate/"+BuiltInRegistries.ITEM.getKey(target).getPath();
        var definition=NativeRecipeSource.entries(h.getLevel()).stream().filter(entry->entry.id().toString().equals(nativeId)).findFirst().orElseThrow();
        try{nativeFieldType(tile,"generatorRecipe").set(tile,definition.id().toString());}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        Object[] actions=(Object[])nativeField(tile,"ACTION_RECIPES");int none=-1;
        for(int index=0;index<actions.length;index++)if("none".equals(String.valueOf(NativeBackendReflection.call(actions[index],"getAction"))))none=index;
        h.assertTrue(none>=0,"Actual registered action definitions include idle");for(String field:List.of("firstRecipeId","secondRecipeId","thirdRecipeId","fourthRecipeId"))setNativeInt(tile,field,none);
        ItemStack expected=((ItemStack)nativeField(definition.value(),"output")).copy();h.assertTrue(expected.is(target),"The selected actual registered generation recipe produces the requested item");
        Map<ResourceKey,Long> consumed=new HashMap<>();Map<String,Integer> retained=new HashMap<>();List<com.cappleapple.astralrepository.content.CrystalNodeBlockEntity> sources=new ArrayList<>();
        for(String field:List.of("water","lava")) {
            int need=((Number)nativeField(definition.value(),field+"Need")).intValue(),cost=((Number)nativeField(definition.value(),field+"Consume")).intValue();
            var fluid=field.equals("water")?net.minecraft.world.level.material.Fluids.WATER:net.minecraft.world.level.material.Fluids.LAVA;
            var nativeTank=(net.minecraftforge.fluids.capability.IFluidHandler)nativeField(tile,field);
            h.assertTrue(need>=cost&&cost>=0,"Native generation requirements and consumption are nonnegative");
            int baseline=need-cost;retained.put(field,baseline);
            if(baseline>0)h.assertTrue(nativeTank.fill(new net.minecraftforge.fluids.FluidStack(fluid,baseline),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==baseline,"Only actual native readiness buffer is preloaded as protected setup");
            if(cost>0) {
                var source=((NativeFluidFixture)f.access).addTank(new BlockPos(1,2,2+sources.size()));sources.add(source);
                h.assertTrue(source.tank().fill(new net.minecraftforge.fluids.FluidStack(fluid,cost),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==cost,"The actual consumed fluid is available only in real network storage");
                consumed.put(new FluidKey(new net.minecraftforge.fluids.FluidStack(fluid,1)),(long)cost);
            }
        }
        var recipe=f.adapter.recipes(f.access).stream().filter(value->value.output().equals(new ItemKey(expected))).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        Map<ResourceKey,Long> declared=new HashMap<>();for(var input:recipe.ingredients()){h.assertTrue(input.alternatives().size()==1,"The configured generation recipe has an exact native fluid identity");declared.merge(input.alternatives().get(0),input.count(),Long::sum);}
        h.assertTrue(!consumed.isEmpty()&&declared.equals(consumed),"Only actual finite native fluid consumption is planned: expected="+consumed+", actual="+declared);
        if(cancel) {
            for(var input:consumed.entrySet())h.assertTrue(f.access.reserveResource(input.getKey(),input.getValue(),(origin,count)->{})==input.getValue(),"Cancellation owns only the actual consumed input reserved from physical storage");
            var operation=start(h,f,recipe);h.assertTrue(operation.poll()==null,"One poll stages physical owned fluid without advancing the native machine");
            for(String field:retained.keySet())h.assertTrue(((Number)NativeBackendReflection.call(nativeField(tile,field),"getFluidAmount")).intValue()==((Number)nativeField(definition.value(),field+"Need")).intValue(),"Native readiness holds only the retained baseline plus the real committed owned fluid");
            var refund=operation.cancel();h.assertTrue(refund.equals(consumed),"Cancellation returns exact owned fluid, never credits the preloaded baseline: expected="+consumed+", actual="+refund);
            for(var entry:retained.entrySet())h.assertTrue(((Number)NativeBackendReflection.call(nativeField(tile,entry.getKey()),"getFluidAmount")).intValue()==entry.getValue(),"Cancellation preserves the actual native retained "+entry.getKey()+" baseline");
            for(var entry:refund.entrySet())h.assertTrue(f.access.insertResourceFrom(entry.getKey(),entry.getValue(),f.access.at(f.position))==0,"Only physically recovered owned fluid returns to real network storage");
            h.assertTrue(f.access.resourceSnapshot().equals(consumed)&&operation.cancel().isEmpty()&&operation.recoverable().isEmpty(),"Recovered fluid is physically stored once and cannot replay; no output was manufactured");h.succeed();return;
        }
        var service=new CraftingService(f.access);var player = new net.minecraftforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "if-native-coordinator"));
        var submitted=service.request(player,expected.copyWithCount(1),expected.getCount());h.assertTrue(submitted.accepted(),"The configured native finite generation request is accepted");
        h.onEachTick(()->{charge(h,f.position);service.tick();});
        h.succeedWhen(()->{
            h.assertTrue(service.activeJobs()==0&&service.statuses().stream().anyMatch(status->status.id().equals(submitted.jobId())&&status.state().equals("COMPLETE")),"The original generation job completes one physical native execution: "+service.statuses());
            h.assertTrue(f.access.snapshot().equals(Map.of(new ItemKey(expected),(long)expected.getCount())),"Only the actual registered native output reaches storage");
            h.assertTrue(sources.stream().allMatch(source->source.tank().getFluidAmount()==0),"Every planned native fluid quantity was consumed from physical network storage");
            for(var entry:retained.entrySet())h.assertTrue(((Number)NativeBackendReflection.call(nativeField(tile,entry.getKey()),"getFluidAmount")).intValue()==entry.getValue(),"The native retained "+entry.getKey()+" setup survives the physical execution unchanged");
            h.assertTrue(!CraftingService.isProcessorReserved(f.access.at(f.position)),"Finite generation releases its native processor claim after collection");
        });
    }
    private static void finiteFluid(GameTestHelper h,String machine,boolean fluidOutput) {
        var f=fixture(h,machine);
        if(machine.equals("industrialforegoing:fermentation_station"))setNativeInt(h.getBlockEntity(f.position),"seal",2);
        var recipe=f.adapter.recipes(f.access).stream().filter(value -> value.id().contains("/industrialforegoing:intrinsic/") && (value.output() instanceof FluidKey)==fluidOutput).findFirst().orElseThrow(()->new IllegalStateException(diagnostic(f)));
        h.assertTrue(recipe.ingredients().stream().anyMatch(input -> input.alternatives().get(0) instanceof FluidKey),"The native transform declares its real component-sensitive ore fluid");
        var operation=start(h,f,recipe);@SuppressWarnings("unchecked") Map<ResourceKey,Long>[] result=new Map[]{null};
        h.onEachTick(()->{charge(h,f.position);if(result[0]==null)result[0]=operation.poll();});
        h.succeedWhen(()->{h.assertTrue(result[0]!=null,"A physical native ore-fluid cycle finishes: "+machine+"; "+h.getBlockEntity(f.position).saveWithFullMetadata());h.assertTrue(result[0].getOrDefault(recipe.output(),0L)==recipe.outputCount(),"Every actual native output retains its exact item/fluid identity and quantity");h.assertTrue(operation.cancel().isEmpty(),"Consumed native ore fluids and collected outputs cannot replay");});
    }
    private static ItemStack enchantedTarget(GameTestHelper h) {
        return enchanted(h,Items.DIAMOND_SWORD);
    }
    private static ItemStack enchanted(GameTestHelper h,Item item) {
        ItemStack stack=new ItemStack(item);
        net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(Map.of(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,3),stack);
        return stack;
    }
    private static java.lang.reflect.Field nativeFieldType(Object owner,String name) { for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException absent){}throw new IllegalArgumentException(name); }
    private static Object nativeField(Object owner,String name) { try{return nativeFieldType(owner,name).get(owner);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);} }
    private static void setNativeInt(Object owner,String name,int value) { try{nativeFieldType(owner,name).setInt(owner,value);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);} }
    private static void intrinsic(GameTestHelper h, String block, String target, int count, int fluidInputs) {
        var f = fixture(h, block); var item = BuiltInRegistries.ITEM.get(new ResourceLocation(target));
        var catalog = f.adapter.recipes(f.access);
        var recipe = catalog.stream().filter(value -> value.id().contains("/industrialforegoing:intrinsic/") && value.output() instanceof ItemKey key && key.sample().is(item)).findFirst().orElseThrow(() -> new IllegalStateException("Native transform not catalogued: " + diagnostic(f)));
        h.assertTrue(recipe.grossOutputCount() == count, "Native completion body supplies the real gross output count");
        h.assertTrue(recipe.ingredients().stream().filter(value -> value.alternatives().get(0) instanceof FluidKey).count() == fluidInputs, "Native component drains determine every fluid input");
        if (target.equals("industrialforegoing:fertilizer")) h.assertTrue(recipe.ingredients().get(0).count() == 1000, "Sewage consumption is the actual native1000 mB");
        if (target.equals("minecraft:brown_mushroom")) h.assertTrue(recipe.ingredients().stream().anyMatch(value -> value.alternatives().get(0) instanceof ItemKey && value.count() == 1), "One seed is consumed while two actual copies are produced");
        var operation = start(h, f, recipe);
        @SuppressWarnings("unchecked") Map<ResourceKey, Long>[] result = new Map[]{null};
        h.onEachTick(() -> { charge(h, f.position); if (result[0] == null) result[0] = operation.poll(); });
        h.succeedWhen(() -> {
            h.assertTrue(result[0] != null, "Real native fixed processing finishes: " + block + "; machine=" + h.getBlockEntity(f.position).saveWithFullMetadata());
            h.assertTrue(result[0].getOrDefault(recipe.output(), 0L) == count && operation.cancel().isEmpty(), "Only actual produced resources return once");
        });
    }
    private static Fixture fixture(GameTestHelper h, String machine) {
        return fixture(h,machine,new ItemStack(Items.PAPER));
    }
    private static Fixture fixture(GameTestHelper h, String machine,ItemStack target) {
        BlockPos position = new BlockPos(4, 2, 4), chest = new BlockPos(1, 2, 1);
        h.setBlock(position, BuiltInRegistries.BLOCK.get(new ResourceLocation(machine))); h.setBlock(chest, Blocks.BARREL);
        var access = new NativeFluidFixture(h, chest, (Container) h.getBlockEntity(chest), List.of(position), target);
        var backend = new IndustrialForegoingBackend(); return new Fixture(position, access, backend, new NativeMachineAdapter(backend));
    }
    private static String diagnostic(Fixture f) {
        String type = f.access.helper.getBlockEntity(f.position).getClass().getName();
        String value = f.backend.diagnostics().stream().filter(entry -> entry.nativeClass().equals(type)).map(entry -> entry.status() + ": " + entry.reason()).reduce((a,b) -> a + "; " + b).orElse("no native diagnostic");
        return value.length() > 1200 ? value.substring(value.length() - 1200) : value;
    }
    private static final class NativeFluidFixture extends ForgeLegacyTechGameTests.Fixture {
        final List<com.cappleapple.astralrepository.content.CrystalNodeBlockEntity> tanks=new ArrayList<>();
        List<ItemStack> taught;
        NativeFluidFixture(GameTestHelper h,BlockPos origin,Container chest,List<BlockPos> machines,ItemStack target){super(h,origin,chest,machines,target);}
        @Override public List<ItemStack> exposedProducts(){return taught==null?super.exposedProducts():taught.stream().map(ItemStack::copy).toList();}
        com.cappleapple.astralrepository.content.CrystalNodeBlockEntity addTank(BlockPos position){helper.setBlock(position,com.cappleapple.astralrepository.content.AstralContent.SEED_STORAGE_CRYSTAL.get());var crystal=(com.cappleapple.astralrepository.content.CrystalNodeBlockEntity)helper.getBlockEntity(position);tanks.add(crystal);return crystal;}
        @Override public Map<ResourceKey,Long> resourceSnapshot(){Map<ResourceKey,Long> result=new HashMap<>(snapshot());for(var tank:tanks){var fluid=tank.tank().getFluid();if(!fluid.isEmpty())result.merge(new FluidKey(fluid),(long)fluid.getAmount(),Long::sum);}return result;}
        @Override public long reserveResource(ResourceKey key,long count,java.util.function.BiConsumer<GlobalPos,Long> receipt){
            if(key instanceof ItemKey item){ItemStack taken=extract(item,Math.toIntExact(count));if(!taken.isEmpty())receipt.accept(origin(),(long)taken.getCount());return taken.getCount();}
            if(!(key instanceof FluidKey fluid))return 0;long total=0;for(var tank:tanks){var taken=tank.tank().drain(new net.minecraftforge.fluids.FluidStack(fluid.sample(),(int)Math.min(count-total,Integer.MAX_VALUE)),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);if(!taken.isEmpty()){total+=taken.getAmount();receipt.accept(GlobalPos.of(level().dimension(),tank.getBlockPos()),(long)taken.getAmount());}if(total==count)break;}return total;
        }
        @Override public long insertResourceFrom(ResourceKey key,long count,GlobalPos from){
            if(key instanceof ItemKey item)return insert(item.sample().copyWithCount(Math.toIntExact(count))).getCount();if(!(key instanceof FluidKey fluid))return count;long remaining=count;for(var tank:tanks){remaining-=tank.tank().fill(new net.minecraftforge.fluids.FluidStack(fluid.sample(),(int)Math.min(remaining,Integer.MAX_VALUE)),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);if(remaining==0)break;}return remaining;
        }
    }
    private static NativeMachineBackend.Recipe nativeRecipe(Fixture f) {
        return f.backend.recipes(f.access, List.of(f.backend.machine(f.access, f.access.at(f.position)))).stream().filter(value -> value.id().equals("industrialforegoing:dissolution_chamber/pink_slime_ball")).findFirst().orElseThrow();
    }
    private static CraftScheduler.Operation<ResourceKey> start(GameTestHelper h, Fixture f, CraftRecipe<ResourceKey> recipe) {
        var selected = recipe.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().get(0), input.count())).toList();
        var operation = f.adapter.start(f.access, f.access.at(f.position), new CraftPlan.Node<>(0, recipe, selected, Set.of()));
        h.assertTrue(operation != null, "Native discovered slot roles accept the exact item and fluid quantities"); return operation;
    }
    private static void charge(GameTestHelper h, BlockPos position) {
        for (Direction side : Direction.values()) { var energy = h.getBlockEntity(position).getCapability(ForgeCapabilities.ENERGY,side).orElse(null); if (energy != null) energy.receiveEnergy(100_000, false); }
    }
    private static boolean enabled(GameTestHelper h) {
        if (!Boolean.getBoolean("astral_repository.legacyIndustrialTest")) { h.succeed(); return false; }
        h.assertTrue(ModList.get().isLoaded("industrialforegoing") && ModList.get().isLoaded("titanium"), "Actual IF/Titanium runtime is installed"); return true;
    }
}
