package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/** Test-only speed injection supplies kinetic input; Create itself must perform every recipe. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class CreateProcessingGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=800)
    public static void actualCreatePressProducesAnIronSheet(GameTestHelper h) {
        if (!requireCreate(h)) return;
        BlockPos chest = new BlockPos(1, 2, 1), depot = new BlockPos(3, 2, 1), press = depot.above(2);
        h.setBlock(chest, Blocks.CHEST);
        h.setBlock(depot, block("create:depot")); h.setBlock(press, block("create:mechanical_press"));
        ItemStack target = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse("create:iron_sheet")));
        run(h, chest, depot, press, new ItemStack(Items.IRON_INGOT), target);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=800)
    public static void actualCreateMillstoneProducesGravel(GameTestHelper h) {
        if (!requireCreate(h)) return;
        BlockPos chest = new BlockPos(1, 2, 1), millstone = new BlockPos(3, 2, 1);
        h.setBlock(chest, Blocks.CHEST); h.setBlock(millstone, block("create:millstone"));
        run(h, chest, millstone, millstone, new ItemStack(Items.COBBLESTONE), new ItemStack(Items.GRAVEL));
    }
    private static boolean requireCreate(GameTestHelper h) {
        if (ModList.get().isLoaded("create")) return true;
        h.assertTrue(!Boolean.getBoolean("astral_repository.compatTest"), "Create must be installed during the optional integration gate");
        h.succeed(); return false;
    }
    private static net.minecraft.world.level.block.Block block(String id) {
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException("Missing Create fixture block " + key);
        return BuiltInRegistries.BLOCK.get(key);
    }
    private static void run(GameTestHelper h, BlockPos chestPos, BlockPos processor, BlockPos kinetic,
                            ItemStack input, ItemStack output) {
        h.assertTrue(!output.isEmpty(), "Expected output exists in the installed registry");
        Container chest = (Container)h.getBlockEntity(chestPos); chest.setItem(0, input.copy());
        var fixture = new CraftingGameTests.Fixture(h, chestPos, chest, List.of(processor), output);
        CraftingService service = new CraftingService(fixture);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "create-craft-test"), ClientInformation.createDefault());
        Object machine = h.getBlockEntity(kinetic);
        Method setSpeed;
        try { setSpeed = machine.getClass().getMethod("setSpeed", float.class); setSpeed.invoke(machine, 256F); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Create kinetic test API", failure); }
        var request = service.request(player, output, 1);
        h.assertTrue(request.accepted(), "Create production plan accepted: " + request.message());
        h.onEachTick(() -> {
            try { setSpeed.invoke(machine, 256F); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot supply Create test kinetic input", failure); }
            service.tick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0, "Create operation is complete");
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(output), 0L) >= 1L,
                    "Actual Create output returned to chest: " + service.statuses());
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(input), 0L) == 0L, "Actual ingredient was consumed");
            h.assertTrue(!CraftingService.isProcessorReserved(fixture.at(processor)), "Create machine reservation released");
        });
    }
}
