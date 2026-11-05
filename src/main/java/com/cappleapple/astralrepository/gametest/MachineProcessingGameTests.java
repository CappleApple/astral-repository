package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.cappleapple.astralrepository.crafting.MachineRecipeSupport;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Method;
import java.util.*;

/** Optional integration fixtures supply power; the installed machines must consume real inputs. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class MachineProcessingGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void energizedSmelterExecutesActualSmelting(GameTestHelper h) {
        if (!enabled(h, "mekanism")) return;
        BlockPos machine = new BlockPos(4, 2, 4);
        h.setBlock(machine, block("mekanism:energized_smelter"));
        run(h, machine, List.of(new ItemStack(Items.RAW_IRON)), new ItemStack(Items.IRON_INGOT), 1);
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void resourcefulFurnaceExecutesActualSmelting(GameTestHelper h) {
        if (!enabled(h, "industrialforegoing")) return;
        BlockPos machine = new BlockPos(4, 2, 4);
        h.setBlock(machine, block("industrialforegoing:resourceful_furnace"));
        run(h, machine, List.of(new ItemStack(Items.RAW_IRON)), new ItemStack(Items.IRON_INGOT), 1);
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void formedAlloyKilnConsumesCountedInputsAndFuel(GameTestHelper h) {
        if (!enabled(h, "immersiveengineering")) return;
        try {
            Object multiblock = Class.forName("blusunrize.immersiveengineering.common.blocks.multiblocks.IEMultiblocks").getField("ALLOY_SMELTER").get(null);
            BlockPos origin = new BlockPos(4, 2, 4);
            @SuppressWarnings("unchecked") List<StructureBlockInfo> structure = (List<StructureBlockInfo>) invoke(multiblock, "getStructure", new Class<?>[]{Level.class}, h.getLevel());
            for (var info : structure) h.setBlock(origin.offset(info.pos()), info.state());
            BlockPos trigger = (BlockPos) invoke(multiblock, "getTriggerOffset", new Class<?>[]{});
            ServerPlayer player = player(h);
            boolean formed = (Boolean) invoke(multiblock, "createStructure", new Class<?>[]{Level.class, BlockPos.class, Direction.class, net.minecraft.world.entity.player.Player.class}, h.getLevel(), h.absolutePos(origin.offset(trigger)), Direction.SOUTH, player);
            h.assertTrue(formed, "IE template forms through its real multiblock API");
            BlockPos master = null;
            for (var info : structure) {
                BlockPos candidate = origin.offset(info.pos());
                BlockEntity entity = h.getBlockEntity(candidate);
                if (entity != null && entity.getClass().getSimpleName().equals("MultiblockBlockEntityMaster")) master = candidate;
            }
            h.assertTrue(master != null, "Formed kiln has a real master");
            var recipe = h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("immersiveengineering:alloysmelter/bronze")).orElseThrow().value();
            ItemStack target = MachineRecipeSupport.output(recipe, h.getLevel().registryAccess());
            List<ItemStack> inputs = new ArrayList<>();
            for (var input : MachineRecipeSupport.inputs(recipe)) inputs.add(input.ingredient().getItems()[0].copyWithCount(input.count()));
            inputs.add(new ItemStack(Items.COAL));
            h.assertTrue(inputs.stream().anyMatch(stack -> stack.getCount() > 1), "Fixture checks counted alloy ingredients");
            run(h, master, inputs, target, target.getCount());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported IE integration test API", failure); }
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void formedFoundryConsumesBothIngredients(GameTestHelper h) {
        if (!enabled(h, "oritech")) return;
        BlockPos machine = new BlockPos(4, 2, 4);
        h.setBlock(machine, block("oritech:foundry_block"));
        Object entity = h.getBlockEntity(machine);
        try {
            for (int i = 0; i < 3; i++) {
                BlockPos next = (BlockPos) invoke(entity, "getNextMissingCore", new Class<?>[]{});
                h.assertTrue(next != null, "Foundry requests its next real core");
                h.getLevel().setBlockAndUpdate(next, block("oritech:machine_core_1").defaultBlockState());
            }
            boolean formed = (Boolean) invoke(entity, "initMultiblock", new Class<?>[]{net.minecraft.world.level.block.state.BlockState.class}, h.getBlockState(machine));
            h.assertTrue(formed, "Foundry validates and forms its actual multiblock");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Oritech integration test API", failure); }
        run(h, machine, List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.REDSTONE)),
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("oritech:electrum_ingot"))), 1);
    }

    private static boolean enabled(GameTestHelper h, String mod) {
        if (Boolean.getBoolean("astral_repository.machineCompatTest")) {
            h.assertTrue(ModList.get().isLoaded(mod), "Machine runtime gate requires " + mod);
            return true;
        }
        h.succeed(); return false;
    }
    private static net.minecraft.world.level.block.Block block(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException("Missing machine fixture " + key);
        return BuiltInRegistries.BLOCK.get(key);
    }
    private static ServerPlayer player(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "machine-craft-test"), ClientInformation.createDefault());
    }
    private static Object invoke(Object target, String method, Class<?>[] types, Object... arguments) throws ReflectiveOperationException {
        Method call = target.getClass().getMethod(method, types); call.setAccessible(true); return call.invoke(target, arguments);
    }
    private static void power(GameTestHelper h, BlockPos pos) {
        BlockEntity entity = h.getBlockEntity(pos);
        if (entity.getClass().getName().equals("mekanism.common.tile.machine.TileEntityEnergizedSmelter")) {
            try {
                Object energy = invoke(entity, "getEnergyContainer", new Class<?>[]{});
                invoke(energy, "setEnergy", new Class<?>[]{long.class}, 100_000L);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot supply test Mekanism power", failure); }
            return;
        }
        for (Direction side : Direction.values()) {
            var energy = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(pos), side);
            if (energy != null) energy.receiveEnergy(100_000, false);
        }
    }
    private static void run(GameTestHelper h, BlockPos machine, List<ItemStack> inputs, ItemStack target, int count) {
        BlockPos chestPos = new BlockPos(1, 2, 1); h.setBlock(chestPos, Blocks.CHEST);
        Container chest = (Container) h.getBlockEntity(chestPos);
        for (int i = 0; i < inputs.size(); i++) chest.setItem(i, inputs.get(i).copy());
        var fixture = new CraftingGameTests.Fixture(h, chestPos, chest, List.of(machine), target);
        CraftingService service = new CraftingService(fixture);
        var request = service.request(player(h), target, count);
        h.assertTrue(request.accepted(), "Machine request accepted: " + request.message());
        h.onEachTick(() -> { power(h, machine); service.tick(); });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0, "Machine request finished: " + service.statuses() + "; machine=" + h.getBlockEntity(machine).saveWithFullMetadata(h.getLevel().registryAccess()));
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(target), 0L) >= count, "Actual machine output reached storage: " + service.statuses());
            for (ItemStack input : inputs) if (!input.is(Items.COAL)) h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(input), 0L) == 0, "Machine consumed all counted inputs");
            h.assertTrue(!CraftingService.isProcessorReserved(fixture.at(machine)), "Machine reservation released");
        });
    }
}
