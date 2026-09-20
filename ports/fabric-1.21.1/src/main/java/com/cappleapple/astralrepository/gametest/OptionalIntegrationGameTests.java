package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.NetworkPowerProvider;
import com.cappleapple.astralrepository.compat.CapacityCosts;
import com.cappleapple.astralrepository.compat.CreateStressProvider;
import com.cappleapple.astralrepository.content.CapacityInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.math.BigInteger;

/** These tests exercise installed optional APIs; the opt-in gate fails if the expected jar is absent. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class OptionalIntegrationGameTests {
    private static boolean available(GameTestHelper h, String mod) {
        if (ModList.get().isLoaded(mod)) return true;
        h.assertTrue(!Boolean.getBoolean("astral_repository.compatTest"), "Compatibility gate requires installed mod " + mod);
        h.succeed(); return false;
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void installedStacksNotSlotsExactCapacityApi(GameTestHelper h) {
        if (!available(h,"stacksnotslots")) return;
        ItemStack sample = new ItemStack(Items.IRON_INGOT);
        sample.set(DataComponents.MAX_STACK_SIZE, 3);
        try {
            Class<?> api = Class.forName("com.cappleapple.stacksnotslots.api.StacksNotSlotsApi");
            Object exact = api.getMethod("exactCapacityCost",ItemStack.class).invoke(null,sample);
            BigInteger numerator = (BigInteger)exact.getClass().getMethod("numerator").invoke(exact);
            BigInteger denominator = (BigInteger)exact.getClass().getMethod("denominator").invoke(exact);
            var bridged = CapacityCosts.exactCost(sample);
            h.assertTrue(bridged.numerator().multiply(denominator).equals(numerator.multiply(bridged.denominator())), "Bridge returns installed SNS public exact capacity fraction");
            h.assertTrue(numerator.multiply(BigInteger.valueOf(3)).equals(denominator.multiply(BigInteger.valueOf(64))), "Custom max-stack size is respected exactly");
            CapacityInventory inventory = new CapacityInventory(()->64,()->{});
            h.assertTrue(inventory.insertItem(0,sample.copyWithCount(3),false).isEmpty(), "Astral storage accepts three fractional-cost items through installed SNS bridge");
            h.assertTrue(inventory.used()==64, "Exact total is 64 capacity units");
            h.assertTrue(!inventory.insertItem(0,sample,true).isEmpty(), "A fourth item is rejected without mutating storage");
            AstralRepository.LOGGER.info("ASTRAL_COMPAT: Stacks Not Slots {} exact API and Astral storage passed", ModList.get().getModContainerById("stacksnotslots").orElseThrow().getModInfo().getVersion());
            h.succeed();
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Installed Stacks Not Slots public capacity API failed",failure); }
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=150)
    public static void realCreateMotorStressIsSharedCapacityLease(GameTestHelper h) {
        if (!available(h,"create")) return;
        BlockPos motor = new BlockPos(5,3,5);
        var id = ResourceLocation.parse("create:creative_motor");
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Installed Create motor is registered");
        h.setBlock(motor,BuiltInRegistries.BLOCK.get(id));
        h.succeedWhen(()->{
            var tile = h.getBlockEntity(motor);
            h.assertTrue(CreateStressProvider.supports(tile), "Real motor has an initialized kinetic network");
            CreateStressProvider first = new CreateStressProvider(h.getLevel(),tile);
            CreateStressProvider second = new CreateStressProvider(h.getLevel(),tile);
            h.assertTrue(first.valid(), "Real running motor provides stress capacity");
            h.assertTrue(first.mode()==NetworkPowerProvider.Mode.CAPACITY, "Stress is represented as capacity, not consumable FE");
            double before = first.available();
            h.assertTrue(before>=200, "Real motor supplies spare stress capacity");
            h.assertTrue(first.acquire(100,true)==100 && first.available()==before, "Simulated lease leaves real stress accounting unchanged");
            try {
                h.assertTrue(first.acquire(100,false)==100, "Operating capacity acquired");
                h.assertTrue(second.available()==before-100, "Providers on the same kinetic network share the lease");
                first.release(40);
                h.assertTrue(second.available()==before-60, "Partial release returns shared available capacity");
                first.release(60);
                h.assertTrue(second.available()==before, "Release restores real network capacity");
            } finally { first.release(Double.MAX_VALUE); second.release(Double.MAX_VALUE); }
            AstralRepository.LOGGER.info("ASTRAL_COMPAT: Create {} real motor stress capacity lease passed ({} spare SU)", ModList.get().getModContainerById("create").orElseThrow().getModInfo().getVersion(),before);
        });
    }
}
