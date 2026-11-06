package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.*;
import com.mojang.authlib.GameProfile;
import com.google.gson.GsonBuilder;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.call;

/** Real native lifecycle; only kinetic power is supplied by the fixture. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class CreateGenericProcessingGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1500)
    public static void nativeCreateMixerUsesBasinAndRepeatsExactSingleCycles(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos basin = new BlockPos(4, 2, 4), mixer = basin.above(2);
        h.setBlock(basin, block("create:basin")); h.setBlock(mixer, block("create:mechanical_mixer"));
        ItemStack target = item("create:andesite_alloy"); Fixture f = fixture(h, List.of(basin, mixer), target);
        f.chest.setItem(0, new ItemStack(Items.ANDESITE, 3)); f.chest.setItem(1, new ItemStack(Items.IRON_NUGGET, 3));
        CraftingService service = new CraftingService(f); UUID job = request(h, service, target, 3);
        h.onEachTick(() -> { call(h.getBlockEntity(mixer), "setSpeed", 256F); service.tick(); });
        h.succeedWhen(() -> {
            complete(h, service, job); h.assertTrue(f.count(target) == 3 && f.count(new ItemStack(Items.ANDESITE)) == 0 && f.count(new ItemStack(Items.IRON_NUGGET)) == 0, "Three native mixing cycles conserve both physical inputs");
            h.assertTrue(!CraftingService.isProcessorReserved(f.at(basin)) && !CraftingService.isProcessorReserved(f.at(mixer)), "Basin/operator aliases release together");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1500)
    public static void nativeCreateSpoutStagesRealFluidAndTransportedItem(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos depot = new BlockPos(4, 2, 4), spout = depot.above(2);
        h.setBlock(depot, block("create:depot")); h.setBlock(spout, block("create:spout"));
        ItemStack target = item("create:chocolate_glazed_berries"); Fixture f = fixture(h, List.of(spout, depot), target);
        f.chest.setItem(0, new ItemStack(Items.SWEET_BERRIES, 2));
        CrystalNodeBlockEntity tank = f.tank(new BlockPos(1, 2, 2));
        var chocolate = new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("create:chocolate")), 750); tank.tank().fill(chocolate, FluidAction.EXECUTE);
        CraftingService service = new CraftingService(f); UUID job = request(h, service, target, 2); h.onEachTick(service::tick);
        h.succeedWhen(() -> {
            complete(h, service, job); h.assertTrue(f.count(target) == 2 && f.count(new ItemStack(Items.SWEET_BERRIES)) == 0, "Native filling completes two exact item cycles");
            h.assertTrue(tank.tank().getFluidAmount() == 250 && tank.tank().getFluid().getFluid() == chocolate.getFluid(), "Exactly 500 mB physical chocolate is consumed");
            h.assertTrue(!CraftingService.isProcessorReserved(f.at(spout)) && !CraftingService.isProcessorReserved(f.at(depot)), "Spout and transported inventory release together");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1500)
    public static void nativeCreateCrushingCollectsOnlyItsRealDroppedOutputs(GameTestHelper h) throws Exception {
        if (!enabled(h)) return;
        BlockPos controller = new BlockPos(4, 3, 4), left = controller.west(), right = controller.east();
        var wheel = block("create:crushing_wheel").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        h.setBlock(left, wheel); h.setBlock(right, wheel);
        var state = block("create:crushing_wheel_controller").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN);
        for (var property : state.getProperties()) if (property.getName().equals("valid")) state = state.setValue((net.minecraft.world.level.block.state.properties.BooleanProperty)property, true);
        h.setBlock(controller, state);
        Object machine = h.getBlockEntity(controller); var power = machine.getClass().getField("crushingspeed");
        ItemStack target = item("create:crushed_raw_iron"); Fixture f = fixture(h, List.of(controller), target); f.chest.setItem(0, new ItemStack(Items.RAW_IRON));
        var unrelated = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), h.absolutePos(controller).getX()+0.5, h.absolutePos(controller).getY()-0.2, h.absolutePos(controller).getZ()+0.5, target.copy());
        unrelated.setNoGravity(true); h.getLevel().addFreshEntity(unrelated);
        CraftingService service = new CraftingService(f); UUID job = request(h, service, target, 1);
        h.onEachTick(() -> { call(h.getBlockEntity(left), "setSpeed", 256F); call(h.getBlockEntity(right), "setSpeed", -256F); try { power.setFloat(machine, 5.12F); } catch (IllegalAccessException unavailable) { throw new AssertionError(unavailable); } service.tick(); });
        h.succeedWhen(() -> {
            complete(h, service, job); h.assertTrue(f.count(target) == 1 && f.count(new ItemStack(Items.RAW_IRON)) == 0, "Native crushing consumes real raw iron and emits its actual result");
            h.assertTrue(unrelated.isAlive() && new ItemKey(unrelated.getItem()).equals(new ItemKey(target)) && unrelated.getItem().getCount() == 1, "A preexisting nearby drop is never credited to this native cycle");
            h.assertTrue(f.count(item("create:experience_nugget")) <= 1, "Native chance coproduct is conserved without preview rolling");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1500)
    public static void nativeCreateWashingUsesActualAirflowAndOwnedWorldInput(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos fan = new BlockPos(3, 3, 4);
        h.setBlock(fan, block("create:encased_fan").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        // Confine the actual water catalyst so neighboring fixtures and native flow remain unchanged.
        BlockPos water = fan.east(); h.setBlock(water.below(), Blocks.STONE); h.setBlock(water.north(), Blocks.STONE); h.setBlock(water.south(), Blocks.STONE); h.setBlock(water.east().below(), Blocks.STONE); h.setBlock(water, Blocks.WATER);
        ItemStack target = new ItemStack(Items.IRON_NUGGET); Fixture f = fixture(h, List.of(fan), target); f.chest.setItem(0, item("create:crushed_raw_iron"));
        CraftingService service = new CraftingService(f); UUID job = request(h, service, target, 9);
        h.onEachTick(() -> { call(h.getBlockEntity(fan), "setSpeed", 256F); service.tick(); });
        h.succeedWhen(() -> {
            complete(h, service, job); h.assertTrue(f.count(target) == 9 && f.count(item("create:crushed_raw_iron")) == 0, "Native fan washing consumes actual dropped input and produces nine real nuggets");
            h.assertTrue(f.count(new ItemStack(Items.REDSTONE)) <= 1, "Only native rolled redstone may return");
            h.assertTrue(!CraftingService.isProcessorReserved(f.at(fan)), "Airflow ownership is released");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1500)
    public static void nativeCreateSpoutFillsRealBucketsThroughImplicitContainerContract(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos depot = new BlockPos(4, 2, 4), spout = depot.above(2); h.setBlock(depot, block("create:depot")); h.setBlock(spout, block("create:spout"));
        ItemStack target = new ItemStack(Items.WATER_BUCKET); Fixture f = fixture(h, List.of(spout, depot), target); f.chest.setItem(0, new ItemStack(Items.BUCKET, 2));
        var tank = f.tank(new BlockPos(1, 2, 2)); tank.tank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2500), FluidAction.EXECUTE);
        CraftingService service = new CraftingService(f); UUID job = request(h, service, target, 2); h.onEachTick(service::tick);
        h.succeedWhen(() -> { complete(h, service, job); h.assertTrue(f.count(target) == 2 && f.count(new ItemStack(Items.BUCKET)) == 0 && tank.tank().getFluidAmount() == 500, "Two real native bucket fills consume exactly two buckets and2000mB"); });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1500)
    public static void nativeCreateDrainEmptiesRealBucketAndCollectsFluidAndContainer(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos drain = new BlockPos(4, 2, 4); h.setBlock(drain, block("create:item_drain"));
        ItemStack target = new ItemStack(Items.BUCKET); Fixture f = fixture(h, List.of(drain), target); f.chest.setItem(0, new ItemStack(Items.WATER_BUCKET));
        var tank = f.tank(new BlockPos(1, 2, 2)); CraftingService service = new CraftingService(f); UUID job = request(h, service, target, 1); h.onEachTick(service::tick);
        h.succeedWhen(() -> { complete(h, service, job); h.assertTrue(f.count(target) == 1 && f.count(new ItemStack(Items.WATER_BUCKET)) == 0 && tank.tank().getFluidAmount() == 1000 && tank.tank().getFluid().getFluid() == net.minecraft.world.level.material.Fluids.WATER, "Native draining returns only its physical empty bucket and1000mBwater"); });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=3500)
    public static void nativeCreateAssemblyPreservesExactProgressAcrossEveryRealStage(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos filling = new BlockPos(4, 2, 4), pressing = new BlockPos(6, 2, 4), spout = filling.above(2), press = pressing.above(2);
        h.setBlock(filling, block("create:depot")); h.setBlock(pressing, block("create:depot")); h.setBlock(spout, block("create:spout")); h.setBlock(press, block("create:mechanical_press"));
        ItemStack target = item("create:sturdy_sheet"); Fixture f = fixture(h, List.of(filling, spout, pressing, press), target);
        var assembly = h.getLevel().getRecipeManager().getRecipes().stream().filter(holder -> holder.id().toString().equals("create:sequenced_assembly/sturdy_sheet")).findFirst().orElseThrow();
        ItemStack[] starting = (ItemStack[])call(call(assembly.value(), "getIngredient"), "getItems"); h.assertTrue(starting.length > 0, "Native assembly has a registered starting ingredient"); f.chest.setItem(0, starting[0].copyWithCount(1));
        var tank = f.tank(new BlockPos(1, 2, 2)); tank.tank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 3000), FluidAction.EXECUTE);
        int nativeLoops = ((Number)call(assembly.value(), "getLoops")).intValue();
        CraftingService service = new CraftingService(f); UUID job = request(h, service, target, 1);
        h.onEachTick(() -> { call(h.getBlockEntity(press), "setSpeed", 256F); service.tick(); });
        h.succeedWhen(() -> { complete(h, service, job); h.assertTrue(f.count(target) == 1 && f.count(starting[0]) == 0 && tank.tank().getFluidAmount() == 3000 - nativeLoops * 500, "Every native loop consumes one500mB fill and performs both physical pressing stages; loops=" + nativeLoops + ", actual remaining=" + tank.tank().getFluidAmount()); h.assertTrue(service.activeProcessors() == 0, "Every native stage releases its processor"); });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=2000)
    public static void nativeCreateCrafterChainUsesActualTwoByTwoLayoutAndSharedOwnership(GameTestHelper h) throws Exception {
        if (!enabled(h)) return;
        BlockPos topLeft=new BlockPos(4,4,4), topRight=topLeft.east(), bottomLeft=topLeft.below(), bottomRight=bottomLeft.east();
        for (BlockPos pos:List.of(topLeft,topRight,bottomLeft,bottomRight)) {
            var state=block("create:mechanical_crafter").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
            for (var property:state.getProperties()) if(property.getName().equals("pointing")) state=pointing(state,property,pos.equals(topRight)?Direction.DOWN:Direction.EAST);
            h.setBlock(pos,state);
        }
        BlockPos gear=topLeft.west(); h.setBlock(gear,block("create:cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z));
        BlockPos motor=gear.north(); h.setBlock(motor,block("create:creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING,Direction.SOUTH));
        Object source=h.getBlockEntity(motor); call(source.getClass().getField("generatedSpeed").get(source),"setValue",256);
        ItemStack target=new ItemStack(Items.CRAFTING_TABLE); Fixture f=fixture(h,List.of(topLeft,topRight,bottomLeft,bottomRight),target); f.chest.setItem(0,new ItemStack(Items.OAK_PLANKS,8));
        CraftingService service=new CraftingService(f); UUID job=request(h,service,target,2);
        h.onEachTick(()->{ for(BlockPos pos:List.of(topLeft,topRight,bottomLeft,bottomRight)) {
            var entity=h.getLevel().getBlockEntity(h.absolutePos(pos));
            h.assertTrue(entity!=null,"Native crafter survives at "+pos+" state="+h.getLevel().getBlockState(h.absolutePos(pos)));
        } service.tick(); });
        h.succeedWhen(()->{ h.assertTrue(service.statuses().stream().anyMatch(status->status.id().equals(job)&&status.state().equals("COMPLETE")),"Native crafter request completes: "+service.statuses()+"; "+crafterStates(h,List.of(topLeft,topRight,bottomLeft,bottomRight))); h.assertTrue(f.count(target)==2 && f.count(new ItemStack(Items.OAK_PLANKS))==0,"Two native shaped crafter cycles consume eight actual planks"); for(BlockPos pos:List.of(topLeft,topRight,bottomLeft,bottomRight)) h.assertTrue(!CraftingService.isProcessorReserved(f.at(pos)),"Every crafter alias is released"); });
    }
    private static Object nativeField(Object object,String name) { if(object==null)return "null"; for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try { var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object); }catch(ReflectiveOperationException ignored){} return "unavailable"; }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1500)
    public static void identicalNativeCreateCrafterLayoutsBindTheirOwnPhysicalCells(GameTestHelper h) throws Exception {
        if(!enabled(h)) return;
        List<BlockPos> processors=List.of(new BlockPos(4,3,4),new BlockPos(8,3,4));
        for(var pos:processors) {
            var state=block("create:mechanical_crafter").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
            for(var property:state.getProperties()) if(property.getName().equals("pointing")) state=pointing(state,property,Direction.EAST);
            h.setBlock(pos,state); h.setBlock(pos.west(),block("create:cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z));
            var motor=pos.west().north(); h.setBlock(motor,block("create:creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING,Direction.SOUTH));
            var source=h.getBlockEntity(motor); call(source.getClass().getField("generatedSpeed").get(source),"setValue",256);
        }
        ItemStack target=new ItemStack(Items.OAK_PLANKS); Fixture f=fixture(h,processors,target); f.chest.setItem(0,new ItemStack(Items.OAK_LOG,2));
        CraftingService service=new CraftingService(f); UUID job=request(h,service,target,8); boolean[] used={false,false};
        h.onEachTick(()->{ service.tick(); for(int i=0;i<processors.size();i++) if(!String.valueOf(nativeField(h.getBlockEntity(processors.get(i)),"phase")).equals("IDLE")) used[i]=true; });
        h.succeedWhen(()->{ complete(h,service,job); h.assertTrue(used[0]&&used[1],"Both identical native layouts execute their own physical inputs"); h.assertTrue(f.count(target)==8 && f.count(new ItemStack(Items.OAK_LOG))==0,"Two real native crafters conserve both logs and eight planks"); });
    }
    private static String crafterStates(GameTestHelper h,List<BlockPos> positions) { List<String> result=new ArrayList<>(); for(var pos:positions) { var tile=h.getLevel().getBlockEntity(h.absolutePos(pos)); result.add(pos+" phase="+nativeField(tile,"phase")+" timer="+nativeField(tile,"countDown")+" speed="+call(tile,"getSpeed")+" input="+call(nativeField(tile,"inventory"),"getStackInSlot",0)+" grouped="+nativeField(nativeField(tile,"groupedItems"),"grid")); } result.add("drops="+h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(h.absolutePos(positions.getFirst())).inflate(4)).stream().map(item->item.getItem()+" owned="+NativeWorldOutputs.exclusivelyOwned(item)).toList()); return result.toString(); }
    @SuppressWarnings({"unchecked","rawtypes"}) private static net.minecraft.world.level.block.state.BlockState pointing(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.block.state.properties.Property property,Direction direction) { for(Object option:property.getPossibleValues()) { var candidate=state.setValue(property,(Comparable)option); if(call(state.getBlock().getClass(),"getTargetDirection",candidate)==direction) return candidate; } throw new IllegalArgumentException("Missing native pointing "+direction); }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void nativeCreateCatalogClassifiesEveryRegisteredProcessingRecipe(GameTestHelper h) throws Exception {
        if (!enabled(h)) return;
        var backend = new CreateBackend(); Fixture f = fixture(h, List.of(), new ItemStack(Items.PAPER));
        var recipes = backend.recipes(f, List.of());
        var rows = backend.audit();
        Class<?> processing = Class.forName("com.simibubi.create.content.processing.recipe.ProcessingRecipe");
        long nativeCount = h.getLevel().getRecipeManager().getRecipes().stream().filter(holder -> processing.isInstance(holder.value())).count();
        Path output = Path.of("generic-processing", "create-native-audit.json"); Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("nativeProcessingRecipes", nativeCount, "discoveredRecipes", recipes.size(), "rows", rows)));
        var classified = rows.stream().map(CreateBackend.Classification::recipe).collect(java.util.stream.Collectors.toSet());
        h.assertTrue(nativeCount > 100 && h.getLevel().getRecipeManager().getRecipes().stream().filter(holder -> processing.isInstance(holder.value())).allMatch(holder -> classified.contains(holder.id().toString())) && recipes.size() > 100, "Every registered native processing definition is classified, including world/manual boundaries"); h.succeed();
    }

    private static boolean enabled(GameTestHelper h) { if (ModList.get().isLoaded("create")) return true; h.assertTrue(!Boolean.getBoolean("astral_repository.genericTechTest"), "Create is required by this native gate"); h.succeed(); return false; }
    private static Block block(String id) { var key = ResourceLocation.parse(id); if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException(id); return BuiltInRegistries.BLOCK.get(key); }
    private static ItemStack item(String id) { var key = ResourceLocation.parse(id); if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException(id); return new ItemStack(BuiltInRegistries.ITEM.get(key)); }
    private static UUID request(GameTestHelper h, CraftingService service, ItemStack target, int count) { var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "create-native-test"), ClientInformation.createDefault()); var result = service.request(player, target, count); h.assertTrue(result.accepted(), "Native request accepted: " + result.message()); return result.jobId(); }
    private static void complete(GameTestHelper h, CraftingService service, UUID job) { h.assertTrue(service.statuses().stream().anyMatch(status -> status.id().equals(job) && status.state().equals("COMPLETE")), "Original native request completes: " + service.statuses()); }
    private static Fixture fixture(GameTestHelper h, List<BlockPos> machines, ItemStack target) { BlockPos chest = new BlockPos(1, 2, 1); h.setBlock(chest, Blocks.BARREL); return new Fixture(h, chest, (Container)h.getBlockEntity(chest), machines, target); }
    private static final class Fixture extends CraftingGameTests.Fixture {
        final List<CrystalNodeBlockEntity> tanks = new ArrayList<>();
        Fixture(GameTestHelper h, BlockPos chestPos, Container chest, List<BlockPos> machines, ItemStack target) { super(h, chestPos, chest, machines, target); }
        long count(ItemStack stack) { return snapshot().getOrDefault(new ItemKey(stack), 0L); }
        CrystalNodeBlockEntity tank(BlockPos pos) { helper.setBlock(pos, AstralContent.SEED_STORAGE_CRYSTAL.get()); var tank = (CrystalNodeBlockEntity)helper.getBlockEntity(pos); tanks.add(tank); return tank; }
        @Override public Map<ResourceKey, Long> resourceSnapshot() { Map<ResourceKey, Long> result = new HashMap<>(snapshot()); for (var tank : tanks) { var fluid = tank.tank().getFluid(); if (!fluid.isEmpty()) result.merge(new FluidKey(fluid), (long)fluid.getAmount(), Long::sum); } return result; }
        @Override public long reserveResource(ResourceKey key, long count, BiConsumer<GlobalPos, Long> receipt) {
            if (key instanceof ItemKey item) { var taken = extract(item, Math.toIntExact(count)); if (!taken.isEmpty()) receipt.accept(origin(), (long)taken.getCount()); return taken.getCount(); }
            if (!(key instanceof FluidKey fluid)) return 0;
            long total = 0; for (var tank : tanks) { int wanted = (int)Math.min(Integer.MAX_VALUE, count-total); if (wanted <= 0) break; var taken = tank.tank().drain(fluid.sample().copyWithAmount(wanted), FluidAction.EXECUTE); if (!taken.isEmpty()) { if (!new FluidKey(taken).equals(key)) throw new IllegalStateException("Native test stock identity changed"); total += taken.getAmount(); receipt.accept(GlobalPos.of(level().dimension(), tank.getBlockPos()), (long)taken.getAmount()); } } return total;
        }
        @Override public long insertResourceFrom(ResourceKey key, long count, GlobalPos source) { if (key instanceof ItemKey item) return insert(item.sample().copyWithCount(Math.toIntExact(count))).getCount(); if (!(key instanceof FluidKey fluid)) return count; long left=count; for (var tank : tanks) { left-=tank.tank().fill(fluid.sample().copyWithAmount((int)Math.min(Integer.MAX_VALUE, left)), FluidAction.EXECUTE); if (left==0) break; } return left; }
        @Override public int travelTicks(GlobalPos from, GlobalPos to) { return 0; }
    }
    private CreateGenericProcessingGameTests() {}
}
