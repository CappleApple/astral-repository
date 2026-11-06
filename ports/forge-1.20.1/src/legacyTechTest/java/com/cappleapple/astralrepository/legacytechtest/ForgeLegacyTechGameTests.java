package com.cappleapple.astralrepository.legacytechtest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.*;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.IItemHandler;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.call;

/** Real registered IE and Ender IO machines exercise the shared production coordinator. */
@net.minecraftforge.fml.common.Mod("astral_legacy_tech_test")
@GameTestHolder("astral_legacy_tech_test")
@PrefixGameTestTemplate(false)
public final class ForgeLegacyTechGameTests {
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1800)
    public static void genericIeAlloyConsumesCountedInputsAndNetworkFuel(GameTestHelper h)throws Exception{
        if(!enabled(h,"immersiveengineering"))return;
        BlockPos master=form(h,"ALLOY_SMELTER");Fixture fixture=fixture(h,List.of(master),new ItemStack(Items.PAPER));
        var backend=new ImmersiveEngineeringBackend();var machine=backend.machine(fixture,fixture.at(master));
        var recipe=backend.recipes(fixture,List.of(machine)).stream().filter(value -> value.id().endsWith("/immersiveengineering:alloysmelter/bronze")).findFirst().orElseThrow();
        ItemStack target=target(recipe);fixture.targetOverride=target;fund(fixture,recipe);fixture.insert(new ItemStack(Items.COAL));
        h.assertTrue(recipe.inputs().stream().anyMatch(input -> input.count()>1),"Native alloy recipe exposes counted ingredients");
        var service=request(h,fixture,target,(int)recipe.guaranteed().get(new ItemKey(target)).longValue());
        tick(h,fixture,service);
        h.succeedWhen(() -> {
            finished(h,fixture,service,target,4);
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.COAL)),0L)==0,"One real network coal was delivered and burned in the protected native fuel slot");
            assertConsumed(h,fixture,recipe);h.assertTrue(!CraftingService.isProcessorReserved(fixture.at(master)),"Formed kiln and aliases released their claims");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1800)
    public static void genericIeCrusherOwnsNativeOutputDestinationAndMultiblockAliases(GameTestHelper h)throws Exception{
        if(!enabled(h,"immersiveengineering"))return;
        BlockPos master=form(h,"CRUSHER");Fixture fixture=fixture(h,List.of(master),new ItemStack(Items.GRAVEL));
        var backend=new ImmersiveEngineeringBackend();var machine=backend.machine(fixture,fixture.at(master));receiveDrops(h,machine);
        h.assertTrue(machine.claimedPositions().size()>1,"Every formed part and the native output destination is claimed");
        fixture.insert(new ItemStack(Items.COBBLESTONE));var service=request(h,fixture,new ItemStack(Items.GRAVEL),1);tick(h,fixture,service);
        h.succeedWhen(() -> {
            finished(h,fixture,service,new ItemStack(Items.GRAVEL),1);
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.COBBLESTONE)),0L)==0,"Real crusher consumed the original cobblestone");
            for(GlobalPos alias:machine.claimedPositions())h.assertTrue(!CraftingService.isProcessorReserved(alias),"All output and casing claims release together");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1800)
    public static void genericIeBottlingConsumesExactlyOnePhysicalFluidRecipe(GameTestHelper h)throws Exception{
        if(!enabled(h,"immersiveengineering"))return;
        BlockPos master=form(h,"BOTTLING_MACHINE");Fixture fixture=fixture(h,List.of(master),new ItemStack(Items.PAPER));
        var backend=new ImmersiveEngineeringBackend();var machine=backend.machine(fixture,fixture.at(master));receiveDrops(h,machine);
        var nativeRecipe=backend.recipes(fixture,List.of(machine)).stream().filter(value -> value.id().endsWith("/immersiveengineering:bottling/white_concrete_powder")||value.id().endsWith("/immersiveengineering:bottling/mud")).sorted(Comparator.comparing(value -> value.id().endsWith("/immersiveengineering:bottling/white_concrete_powder")?0:1)).findFirst().orElseThrow();
        ItemStack target=target(nativeRecipe);fixture.targetOverride=target;fund(fixture,nativeRecipe);long water=nativeRecipe.inputs().stream().filter(input -> input.alternatives().contains(new FluidKey(new FluidStack(Fluids.WATER,1)))).findFirst().orElseThrow().count();
        var source=fixture.addTank(new BlockPos(1,2,2));source.tank().fill(new FluidStack(Fluids.WATER,500),FluidAction.EXECUTE);
        h.assertTrue(machine.accepts(nativeRecipe)&&machine.ready(nativeRecipe,List.of()),"Native bottling mode and output destination are ready");
        for(var input:nativeRecipe.inputs())h.assertTrue(input.alternatives().stream().anyMatch(key -> machine.ports(nativeRecipe).stream().filter(NativeMachineBackend.Port::input).anyMatch(port -> port.insert(key,input.count(),true)==input.count())),"Native bottling has a physical staged input port for "+input+"; ports="+machine.ports(nativeRecipe).stream().map(port -> List.of(port.input(),port.output(),port.snapshot())).toList());
        var service=request(h,fixture,target,1);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,target,1);h.assertTrue(source.tank().getFluidAmount()==500-water,"Bottling consumed its exact native water input: "+water+"mB");for(var input:nativeRecipe.inputs())if(input.alternatives().get(0) instanceof ItemKey key)h.assertTrue(fixture.snapshot().getOrDefault(key,0L)==0,"One real bottling item recipe was consumed");});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=2000)
    public static void genericIeBlastFurnaceCollectsBothSteelAndSlag(GameTestHelper h)throws Exception{
        if(!enabled(h,"immersiveengineering"))return;
        BlockPos master=form(h,"BLAST_FURNACE");Fixture fixture=fixture(h,List.of(master),new ItemStack(Items.PAPER));
        var backend=new ImmersiveEngineeringBackend();var machine=backend.machine(fixture,fixture.at(master));
        var recipe=backend.recipes(fixture,List.of(machine)).stream().filter(value -> value.id().endsWith("/immersiveengineering:blastfurnace/steel")).findFirst().orElseThrow();
        var aliases=new NativeMachineAdapter(backend).recipes(fixture).stream().filter(value -> value.id().endsWith("/immersiveengineering:blastfurnace/steel")).toList();
        h.assertTrue(recipe.guaranteed().size()==2&&recipe.guaranteed().values().stream().allMatch(count -> count==1),"Native blast data declares exactly one steel and one slag, excluding its display-only output");
        for(var product:recipe.guaranteed().entrySet())h.assertTrue(aliases.stream().anyMatch(alias -> alias.output().equals(product.getKey())&&alias.outputCount()==product.getValue()),"Both native steel and slag have directly craftable catalog aliases");
        ItemStack steel=recipe.guaranteed().keySet().stream().filter(ItemKey.class::isInstance).map(ItemKey.class::cast).map(ItemKey::sample).filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("steel")).findFirst().orElseThrow();
        fixture.targetOverride=steel;fund(fixture,recipe);fixture.insert(item("immersiveengineering:coal_coke"));
        var service=request(h,fixture,steel,1);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,steel,1);for(var output:recipe.guaranteed().entrySet())h.assertTrue(fixture.resourceSnapshot().getOrDefault(output.getKey(),0L)>=output.getValue(),"Every real native coproduct reached physical storage: "+output.getKey());});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=3800)
    public static void genericIeCokeOvenUsesOneRealInputAndCollectsNativeCreosote(GameTestHelper h)throws Exception{
        if(!enabled(h,"immersiveengineering"))return;
        BlockPos master=form(h,"COKE_OVEN");Fixture fixture=fixture(h,List.of(master),new ItemStack(Items.CHARCOAL));var fluidOutput=fixture.addTank(new BlockPos(1,2,2));
        var backend=new ImmersiveEngineeringBackend();var machine=backend.machine(fixture,fixture.at(master));var recipe=backend.recipes(fixture,List.of(machine)).stream().filter(value -> value.id().endsWith("/immersiveengineering:cokeoven/charcoal")).findFirst().orElseThrow();
        h.assertTrue(recipe.inputs().size()==1&&recipe.inputs().get(0).count()==1,"Native coke oven admits a one-item attempt even though its recipe also declares a larger maximum batch");
        var coproduct=recipe.guaranteed().entrySet().stream().filter(entry -> entry.getKey() instanceof FluidKey).findFirst().orElseThrow();h.assertTrue(coproduct.getValue()==250,"Native fluid registry holder resolves to the actual250mB creosote identity");fund(fixture,recipe);
        var service=request(h,fixture,new ItemStack(Items.CHARCOAL),1);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,new ItemStack(Items.CHARCOAL),1);h.assertTrue(fluidOutput.tank().getFluidAmount()==250&&new FluidKey(fluidOutput.tank().getFluid()).equals(coproduct.getKey()),"The real native oven produces and returns exactly250mB creosote");assertConsumed(h,fixture,recipe);});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1800)
    public static void genericIeAssemblerUsesNativeConfiguredPatternQueries(GameTestHelper h)throws Exception{
        if(!enabled(h,"immersiveengineering"))return;
        BlockPos master=form(h,"ASSEMBLER");Object helper=call(h.getBlockEntity(master),"getHelper"),state=call(helper,"getState");Object pattern=((Object[])state.getClass().getField("patterns").get(state))[0];
        @SuppressWarnings("unchecked") List<ItemStack> ghost=(List<ItemStack>)pattern.getClass().getField("inv").get(pattern);ghost.set(0,new ItemStack(Items.OAK_PLANKS));ghost.set(3,new ItemStack(Items.OAK_PLANKS));call(pattern,"recalculateOutput",h.getLevel());
        Fixture fixture=fixture(h,List.of(master),new ItemStack(Items.STICK));fixture.insert(new ItemStack(Items.OAK_PLANKS,2));var service=request(h,fixture,new ItemStack(Items.STICK),4);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,new ItemStack(Items.STICK),4);h.assertTrue(ghost.get(0).is(Items.OAK_PLANKS)&&ghost.get(3).is(Items.OAK_PLANKS),"Native assembler pattern remains configured");h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_PLANKS)),0L)==0,"Two real planks satisfy the native RecipeQuery inputs");});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1800)
    public static void genericEnderIoAlloyQueuesSingleNativeCyclesWithoutTouchingCapacitor(GameTestHelper h){
        if(!enabled(h,"enderio"))return;
        BlockPos machine=new BlockPos(4,2,4);h.setBlock(machine,block("enderio:alloy_smelter"));capacitor(h,machine);
        ItemStack target=item("enderio:conductive_alloy_ingot");Fixture fixture=fixture(h,List.of(machine),target);var backend=new EnderIoBackend();var nativeMachine=backend.machine(fixture,fixture.at(machine));
        var recipe=backend.recipes(fixture,List.of(nativeMachine)).stream().filter(value -> value.id().endsWith("/enderio:alloy_smelting/conductive_alloy_ingot")).findFirst().orElseThrow();
        fund(fixture,recipe);fund(fixture,recipe);int count=Math.toIntExact(2*recipe.guaranteed().get(new ItemKey(target)));
        var service=request(h,fixture,target,count);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,target,count);h.assertTrue(capacitorStack(h,machine).is(item("enderio:octadic_capacitor").getItem()),"Native capacitor remains installed after both cycles");assertConsumed(h,fixture,recipe);});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1800)
    public static void genericEnderIoSagMillUsesNativeTaskHostAndProtectedGrindingSetup(GameTestHelper h){
        if(!enabled(h,"enderio"))return;
        BlockPos machine=new BlockPos(4,2,4);h.setBlock(machine,block("enderio:sag_mill"));capacitor(h,machine);
        Fixture fixture=fixture(h,List.of(machine),new ItemStack(Items.COBBLESTONE));fixture.insert(new ItemStack(Items.STONE));var service=request(h,fixture,new ItemStack(Items.COBBLESTONE),1);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,new ItemStack(Items.COBBLESTONE),1);h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.STONE)),0L)==0,"Real SAG task consumed exactly one stone");});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1800)
    public static void genericEnderIoSlicerPreservesAndDamagesNativeTools(GameTestHelper h)throws Exception{
        if(!enabled(h,"enderio"))return;
        BlockPos machine=new BlockPos(4,2,4);h.setBlock(machine,block("enderio:slice_and_splice"));capacitor(h,machine);
        Object entity=h.getBlockEntity(machine);IItemHandler inventory=(IItemHandler)call(entity,"getInventory");
        int axe=slot(entity,"AXE"),shears=slot(entity,"SHEARS");inventory.insertItem(axe,new ItemStack(Items.IRON_AXE),false);inventory.insertItem(shears,new ItemStack(Items.SHEARS),false);
        ItemStack target=item("enderio:z_logic_controller");Fixture fixture=fixture(h,List.of(machine),target);var backend=new EnderIoBackend();var nativeMachine=backend.machine(fixture,fixture.at(machine));
        var recipe=backend.recipes(fixture,List.of(nativeMachine)).stream().filter(value -> value.id().endsWith("/enderio:slicing/z_logic_controller")).findFirst().orElseThrow();fund(fixture,recipe);var service=request(h,fixture,target,1);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,target,1);h.assertTrue(inventory.getStackInSlot(axe).is(Items.IRON_AXE)&&inventory.getStackInSlot(shears).is(Items.SHEARS),"Native tools remain in their protected setup slots");h.assertTrue(inventory.getStackInSlot(axe).getDamageValue()==1&&inventory.getStackInSlot(shears).getDamageValue()==1,"Actual slicer execution damages each native tool once");assertConsumed(h,fixture,recipe);});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1800)
    public static void genericEnderIoPaintingPreservesExactPaintComponentsAndTemplate(GameTestHelper h)throws Exception{
        if(!enabled(h,"enderio"))return;
        BlockPos machine=new BlockPos(4,2,4);h.setBlock(machine,block("enderio:painting_machine"));capacitor(h,machine);
        Object entity=h.getBlockEntity(machine);IItemHandler inventory=(IItemHandler)call(entity,"getInventory");int paint=slot(entity,"PAINT");inventory.insertItem(paint,new ItemStack(Items.STONE),false);
        Fixture fixture=fixture(h,List.of(machine),item("enderio:painted_fence"));var backend=new EnderIoBackend();var nativeMachine=backend.machine(fixture,fixture.at(machine));
        var recipe=backend.recipes(fixture,List.of(nativeMachine)).stream().filter(value -> value.id().endsWith("/enderio:painting/painted_fence")).findFirst().orElseThrow();ItemStack target=target(recipe);fixture.targetOverride=target;
        h.assertTrue(target.hasTag(),"Catalog retains the actual native paint component");fund(fixture,recipe);var service=request(h,fixture,target,1);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,target,1);h.assertTrue(inventory.getStackInSlot(paint).is(Items.STONE)&&inventory.getStackInSlot(paint).getCount()==1,"Unconsumed paint template stays in the machine");});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=400)
    public static void genericEnderIoConfiguredCrafterExecutesGhostRecipeWithoutConsumingTemplate(GameTestHelper h)throws Exception{
        if(!enabled(h,"enderio"))return;
        BlockPos machine=new BlockPos(4,2,4);h.setBlock(machine,block("enderio:crafter"));capacitor(h,machine);
        Object entity=h.getBlockEntity(machine),ghost=entity.getClass().getField("GHOST").get(null);IItemHandler inventory=(IItemHandler)call(entity,"getInventory");
        for(int index:new int[]{0,1,3,4})call(inventory,"setStackInSlot",((Number)call(call(ghost,"get",index),"getIndex")).intValue(),new ItemStack(Items.OAK_PLANKS));
        Fixture fixture=fixture(h,List.of(machine),new ItemStack(Items.CRAFTING_TABLE));fixture.insert(new ItemStack(Items.OAK_PLANKS,4));var service=request(h,fixture,new ItemStack(Items.CRAFTING_TABLE),1);tick(h,fixture,service);
        h.succeedWhen(() -> {
            finished(h,fixture,service,new ItemStack(Items.CRAFTING_TABLE),1);
            for(int index:new int[]{0,1,3,4})h.assertTrue(((ItemStack)call(call(ghost,"get",index),"getItemStack",inventory)).is(Items.OAK_PLANKS),"The real ghost template remains configured");
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_PLANKS)),0L)==0,"Native crafter consumed four actual planks");
        });
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=400)
    public static void genericEnderIoTankDrainsNativeRecipeIntoItemAndPhysicalFluid(GameTestHelper h){
        if(!enabled(h,"enderio"))return;
        BlockPos machine=new BlockPos(4,2,4);h.setBlock(machine,block("enderio:fluid_tank"));Fixture fixture=fixture(h,List.of(machine),new ItemStack(Items.SPONGE));
        fixture.insert(new ItemStack(Items.WET_SPONGE));var output=fixture.addTank(new BlockPos(1,2,2));var service=request(h,fixture,new ItemStack(Items.SPONGE),1);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,new ItemStack(Items.SPONGE),1);h.assertTrue(output.tank().getFluid().getFluid()==Fluids.WATER&&output.tank().getFluidAmount()==1000,"Actual cached native tank recipe produced its full1000mB water coproduct");h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.WET_SPONGE)),0L)==0,"Exactly one real wet sponge was consumed");});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=400)
    public static void genericEnderIoTankFillsNativeExperienceBottleFromPhysicalFluid(GameTestHelper h)throws Exception{
        if(!enabled(h,"enderio"))return;
        BlockPos machine=new BlockPos(4,2,4);h.setBlock(machine,block("enderio:fluid_tank"));Fixture fixture=fixture(h,List.of(machine),new ItemStack(Items.EXPERIENCE_BOTTLE));
        fixture.insert(new ItemStack(Items.GLASS_BOTTLE));var source=fixture.addTank(new BlockPos(1,2,2));Object fluid=call(Class.forName("com.enderio.base.common.init.EIOFluids").getField("XP_JUICE").get(null),"getSource");
        if(!(fluid instanceof net.minecraft.world.level.material.Fluid))fluid=call(fluid,"get");
        source.tank().fill(new FluidStack((net.minecraft.world.level.material.Fluid)fluid,500),FluidAction.EXECUTE);var service=request(h,fixture,new ItemStack(Items.EXPERIENCE_BOTTLE),1);tick(h,fixture,service);
        h.succeedWhen(() -> {finished(h,fixture,service,new ItemStack(Items.EXPERIENCE_BOTTLE),1);h.assertTrue(source.tank().getFluidAmount()==250,"Actual native recipe consumed exactly250mB of physical experience fluid");h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.GLASS_BOTTLE)),0L)==0,"One actual bottle was consumed");});
    }
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=400)
    public static void completeIeEnderIoRecipeAndMachineInventoriesHaveExplicitClassifications(GameTestHelper h)throws Exception{
        if(!enabled(h,"immersiveengineering","enderio"))return;
        Fixture fixture=fixture(h,List.of(),new ItemStack(Items.PAPER));var ie=new ImmersiveEngineeringBackend();var ender=new EnderIoBackend();
        var ieRecipes=ie.recipes(fixture,List.of());var enderRecipes=ender.recipes(fixture,List.of());
        Path output=Path.of(System.getProperty("astral_repository.genericTechOutput","../build/generic-processing/runtime"));Files.createDirectories(output);
        Files.writeString(output.resolve("ie-enderio-native-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status","CLASSIFIED","ie_recipes",ie.audit(),"ie_machines",ie.machineInventory(),"ie_projected",ieRecipes.size(),"enderio_recipes",ender.audit(),"enderio_machines",ender.machineInventory(),"enderio_projected",enderRecipes.size()))+"\n");
        h.assertTrue(!ieRecipes.isEmpty()&&!enderRecipes.isEmpty(),"Both installed native recipe catalogs project processing data");
        h.assertTrue(ie.machineInventory().size()>=20&&ender.machineInventory().size()>=20,"Every registered native machine family is inventoried without a block allowlist: IE="+ie.machineInventory().size()+", EnderIO="+ender.machineInventory().size());
        h.assertTrue(ie.audit().stream().noneMatch(row -> row.classification().equals("unsupported-contract")),"IE native catalog has no unexplained contract failures: "+ie.audit().stream().filter(row -> row.classification().equals("unsupported-contract")).toList());
        h.assertTrue(ender.audit().stream().noneMatch(row -> row.classification().equals("unsupported-contract")),"Ender IO native catalog has no unexplained contract failures: "+ender.audit().stream().filter(row -> row.classification().equals("unsupported-contract")).toList());
        Files.writeString(output.resolve("ie-enderio-native-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status","PASS","ie_recipes",ie.audit(),"ie_machines",ie.machineInventory(),"ie_projected",ieRecipes.size(),"enderio_recipes",ender.audit(),"enderio_machines",ender.machineInventory(),"enderio_projected",enderRecipes.size()))+"\n");h.succeed();
    }
    static boolean enabled(GameTestHelper h,String...mods){if(!Boolean.getBoolean("astral_repository.genericTechTest")){h.succeed();return false;}for(String mod:mods)h.assertTrue(ModList.get().isLoaded(mod),"Native gate requires "+mod);return true;}
    static Block block(String id){return BuiltInRegistries.BLOCK.get(new ResourceLocation(id));}
    static ItemStack item(String id){return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));}
    static ServerPlayer player(GameTestHelper h){return new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"ie-ender-native"));}
    static int slot(Object entity,String field)throws Exception{return ((Number)call(entity.getClass().getField(field).get(null),"getIndex")).intValue();}
    static void capacitor(GameTestHelper h,BlockPos relative){Object entity=h.getBlockEntity(relative);IItemHandler inventory=(IItemHandler)call(entity,"getInventory");int slot=((Number)call(call(inventory,"getLayout"),"getCapacitorSlot")).intValue();inventory.insertItem(slot,item("enderio:octadic_capacitor"),false);}
    static ItemStack capacitorStack(GameTestHelper h,BlockPos relative){Object inventory=call(h.getBlockEntity(relative),"getInventory");return (ItemStack)call(inventory,"getStackInSlot",((Number)call(call(inventory,"getLayout"),"getCapacitorSlot")).intValue());}
    static ItemStack target(NativeMachineBackend.Recipe recipe){return recipe.guaranteed().keySet().stream().filter(ItemKey.class::isInstance).map(ItemKey.class::cast).map(ItemKey::sample).findFirst().orElseThrow();}
    static void fund(Fixture fixture,NativeMachineBackend.Recipe recipe){for(var input:recipe.inputs()){ResourceKey key=input.alternatives().get(0);if(key instanceof ItemKey item)fixture.insert(item.sample().copyWithCount(Math.toIntExact(input.count())));}}
    static void assertConsumed(GameTestHelper h,Fixture fixture,NativeMachineBackend.Recipe recipe){for(var input:recipe.inputs())h.assertTrue(fixture.resourceSnapshot().getOrDefault(input.alternatives().get(0),0L)==0,"Exact native ingredient consumed: "+input.alternatives().get(0));}
    static CraftingService request(GameTestHelper h,Fixture fixture,ItemStack target,int count){var service=new CraftingService(fixture);var request=service.request(player(h),target,count);h.assertTrue(request.accepted(),"Native request accepted: "+request.message());return service;}
    static void finished(GameTestHelper h,Fixture fixture,CraftingService service,ItemStack target,long count){h.assertTrue(service.activeJobs()==0&&fixture.snapshot().getOrDefault(new ItemKey(target),0L)==count&&service.statuses().stream().anyMatch(status -> status.state().equals("COMPLETE")&&status.count()==count),"Native physical output reached storage and the full request completed: "+service.statuses()+"; physical="+physical(fixture));}
    static String physical(Fixture fixture){
        List<Object> states=new ArrayList<>();
        for(GlobalPos position:fixture.workstations()){
            BlockEntity entity=fixture.level().getBlockEntity(position.pos());if(entity==null){states.add("missing "+position);continue;}
            String nbt=entity.saveWithFullMetadata().toString();states.add(nbt.length()>5000?nbt.substring(0,5000):nbt);
        }
        return states.toString();
    }
    static void tick(GameTestHelper h,Fixture fixture,CraftingService service){h.onEachTick(() -> {for(GlobalPos position:fixture.workstations()){BlockEntity entity=h.getLevel().getBlockEntity(position.pos());if(entity!=null){var ie=new ImmersiveEngineeringBackend();var nativeMachine=ie.machine(fixture,position);if(nativeMachine!=null){for(GlobalPos alias:nativeMachine.claimedPositions())power(h,alias.pos());}else power(h,position.pos());}}service.tick();});}
    static void power(GameTestHelper h,BlockPos absolute){for(Direction side:Direction.values()){var energy=(h.getLevel().getBlockEntity(absolute)==null?null:h.getLevel().getBlockEntity(absolute).getCapability(ForgeCapabilities.ENERGY,side).orElse(null));if(energy!=null)energy.receiveEnergy(Integer.MAX_VALUE,false);}}
    static BlockPos form(GameTestHelper h,String name)throws Exception{
        Object multiblock=Class.forName("blusunrize.immersiveengineering.common.blocks.multiblocks.IEMultiblocks").getField(name).get(null);BlockPos origin=new BlockPos(4,2,4);
        @SuppressWarnings("unchecked") List<StructureBlockInfo> structure=(List<StructureBlockInfo>)call(multiblock,"getStructure",h.getLevel());for(var info:structure)h.setBlock(origin.offset(info.pos()),info.state());
        BlockPos trigger=(BlockPos)call(multiblock,"getTriggerOffset");h.assertTrue(Boolean.TRUE.equals(call(multiblock,"createStructure",h.getLevel(),h.absolutePos(origin.offset(trigger)),Direction.SOUTH,player(h))),"Actual IE template forms: "+name);
        for(var info:structure){BlockPos candidate=origin.offset(info.pos());BlockEntity entity=h.getBlockEntity(candidate);if(entity!=null&&entity.getClass().getSimpleName().equals("MultiblockBlockEntityMaster"))return candidate;}throw new IllegalStateException("No native formed master: "+name);
    }
    static void receiveDrops(GameTestHelper h,NativeMachineBackend.Machine machine){for(GlobalPos position:machine.claimedPositions())if(h.getLevel().isEmptyBlock(position.pos()))h.getLevel().setBlockAndUpdate(position.pos(),Blocks.BARREL.defaultBlockState());}
    static Fixture fixture(GameTestHelper h,List<BlockPos> machines,ItemStack target){BlockPos origin=new BlockPos(1,2,1);h.setBlock(origin,Blocks.BARREL);return new Fixture(h,origin,(Container)h.getBlockEntity(origin),machines,target);}
    static class Fixture implements CraftingService.NetworkAccess{
        final GameTestHelper helper;final BlockPos origin;final Container chest;final List<BlockPos> processors;final ItemStack target;
        GlobalPos at(BlockPos position){return GlobalPos.of(level().dimension(),helper.absolutePos(position));}
        public ServerLevel level(){return helper.getLevel();}
        public GlobalPos origin(){return at(origin);}
        public List<GlobalPos> workstations(){return processors.stream().map(this::at).toList();}
        public Map<ItemKey,Long> snapshot(){Map<ItemKey,Long> result=new HashMap<>();for(int slot=0;slot<chest.getContainerSize();slot++){ItemStack stack=chest.getItem(slot);if(!stack.isEmpty())result.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);}return result;}
        public ItemStack extract(ItemKey key,int count){ItemStack result=ItemStack.EMPTY;for(int slot=0;slot<chest.getContainerSize()&&result.getCount()<count;slot++){ItemStack stack=chest.getItem(slot);if(!stack.isEmpty()&&new ItemKey(stack).equals(key)){ItemStack taken=chest.removeItem(slot,count-result.getCount());if(result.isEmpty())result=taken;else result.grow(taken.getCount());}}return result;}
        public ItemStack insert(ItemStack offered){ItemStack remaining=offered.copy();for(int slot=0;slot<chest.getContainerSize()&&!remaining.isEmpty();slot++){ItemStack present=chest.getItem(slot);if(!present.isEmpty()&&!ItemStack.isSameItemSameTags(present,remaining))continue;int count=Math.min(remaining.getCount(),remaining.getMaxStackSize()-present.getCount());if(present.isEmpty())chest.setItem(slot,remaining.copyWithCount(count));else present.grow(count);remaining.shrink(count);}chest.setChanged();return remaining;}
        public void visual(GlobalPos from,GlobalPos to,ItemStack stack,int ticks){}

        ItemStack targetOverride;final List<CrystalNodeBlockEntity> tanks=new ArrayList<>();
        Fixture(GameTestHelper h,BlockPos origin,Container chest,List<BlockPos> machines,ItemStack target){this.helper=h;this.origin=origin;this.chest=chest;this.processors=machines;this.target=target;targetOverride=target;}
        @Override public List<ItemStack> exposedProducts(){return List.of(targetOverride.copy());}
        @Override public int travelTicks(GlobalPos from,GlobalPos to){return 0;}
        @Override public Set<String> excludedRecipes(ItemStack target){return new InventoryProcessingAdapter().recipes(this).stream().map(CraftRecipe::id).collect(java.util.stream.Collectors.toUnmodifiableSet());}
        CrystalNodeBlockEntity addTank(BlockPos relative){helper.setBlock(relative,AstralContent.SEED_STORAGE_CRYSTAL.get());var crystal=(CrystalNodeBlockEntity)helper.getBlockEntity(relative);tanks.add(crystal);return crystal;}
        @Override public Map<ResourceKey,Long> resourceSnapshot(){Map<ResourceKey,Long> result=new HashMap<>(snapshot());for(var tank:tanks){FluidStack fluid=tank.tank().getFluid();if(!fluid.isEmpty())result.merge(new FluidKey(fluid),(long)fluid.getAmount(),Long::sum);}return result;}
        @Override public long reserveResource(ResourceKey key,long count,BiConsumer<GlobalPos,Long> receipt){if(key instanceof ItemKey item){ItemStack taken=extract(item,Math.toIntExact(count));if(!taken.isEmpty())receipt.accept(origin(),(long)taken.getCount());return taken.getCount();}if(!(key instanceof FluidKey fluid))return 0;long total=0;for(var tank:tanks){FluidStack taken=tank.tank().drain(new FluidStack(fluid.sample(),(int)Math.min(count-total,Integer.MAX_VALUE)),FluidAction.EXECUTE);if(!taken.isEmpty()){total+=taken.getAmount();receipt.accept(GlobalPos.of(level().dimension(),tank.getBlockPos()),(long)taken.getAmount());}if(total==count)break;}return total;}
        @Override public long insertResourceFrom(ResourceKey key,long count,GlobalPos from){if(key instanceof ItemKey item)return insert(item.sample().copyWithCount(Math.toIntExact(count))).getCount();if(!(key instanceof FluidKey fluid))return count;long remaining=count;for(var tank:tanks){remaining-=tank.tank().fill(new FluidStack(fluid.sample(),(int)Math.min(remaining,Integer.MAX_VALUE)),FluidAction.EXECUTE);if(remaining==0)break;}return remaining;}
    }
    public ForgeLegacyTechGameTests(){}
}
