package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.crafting.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.*;
import java.util.*;

/** Fault injection checks real ItemStack ownership across hostile inventory callbacks. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class InventoryProcessingFailureGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void partialSecondInsertionReturnsOnlyKnownInputsExactlyOnce(GameTestHelper h) {
        var handler = new FaultHandler(false, false);
        var operation = start(h, handler);
        h.assertTrue(operation != null, "Partially committed inputs stay operation-owned");
        boolean failed = false;
        try { operation.poll(); } catch (RuntimeException expected) { failed = true; }
        h.assertTrue(failed, "Unexpected partial insertion fails the operation");
        var refund = operation.cancel();
        h.assertTrue(count(refund, Items.IRON_INGOT) == 3 && count(refund, Items.GOLD_INGOT) == 2 && count(refund, Items.DIAMOND) == 1,
                "Committed inputs, rejected remainder and unoffered ingredients are returned exactly once");
        h.assertTrue(handler.stacks.stream().allMatch(ItemStack::isEmpty), "Known physical inputs were extracted");
        h.assertTrue(operation.cancel().isEmpty() && operation.recoverable().isEmpty(), "Repeated cleanup cannot return input twice");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void throwingSecondInsertionNeverRefundsItsUnknownCommit(GameTestHelper h) {
        var handler = new FaultHandler(true, false);
        var operation = start(h, handler);
        h.assertTrue(operation != null, "A callback exception keeps prior commitments owned");
        var refund = operation.cancel();
        h.assertTrue(count(refund, Items.IRON_INGOT) == 3 && count(refund, Items.DIAMOND) == 1 && count(refund, Items.GOLD_INGOT) == 0,
                "Only known earlier and unoffered inputs are refundable");
        h.assertTrue(handler.stacks.get(1).is(Items.GOLD_INGOT) && handler.stacks.get(1).getCount() == 2,
                "Unknown insertion remains physically in the machine without a synthetic refund");
        h.assertTrue(handler.stacks.get(0).isEmpty() && handler.stacks.get(2).isEmpty() && operation.cancel().isEmpty(), "Known cleanup is exact and idempotent");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void failedSimulationLeavesEveryInputUntouched(GameTestHelper h) {
        var handler = new FaultHandler(false, true);
        h.assertTrue(start(h, handler) == null, "Rejected simulation declines ownership");
        h.assertTrue(handler.stacks.stream().allMatch(ItemStack::isEmpty) && handler.mutations == 0, "No ingredient is inserted before every slot has passed simulation");
        h.succeed();
    }

    private static long count(Map<ItemKey, Long> items, net.minecraft.world.item.Item item) { return items.getOrDefault(new ItemKey(new ItemStack(item)), 0L); }
    @SuppressWarnings("unchecked")
    private static CraftScheduler.Operation<ItemKey> start(GameTestHelper h, FaultHandler handler) {
        var position = new BlockPos(3, 2, 3); h.setBlock(position, Blocks.BARREL);
        var fixture = new CraftingGameTests.Fixture(h, position, (Container) h.getBlockEntity(position), List.of(position), new ItemStack(Items.NETHERITE_INGOT));
        List<CraftPlan.Selection<ItemKey>> selected = List.of(new CraftPlan.Selection<>(new ItemKey(new ItemStack(Items.IRON_INGOT)), 3),
                new CraftPlan.Selection<>(new ItemKey(new ItemStack(Items.GOLD_INGOT)), 2), new CraftPlan.Selection<>(new ItemKey(new ItemStack(Items.DIAMOND)), 1));
        var recipe = new CraftRecipe<>("fault-injection", new ItemKey(new ItemStack(Items.NETHERITE_INGOT)), 1, "test",
                selected.stream().map(input -> new CraftRecipe.Ingredient<>(List.of(input.key()), input.count())).toList(), 0, 100);
        var node = new CraftPlan.Node<>(0, recipe, selected, Set.<Integer>of());
        var rule = new ProcessingRules.Rule("test", ResourceLocation.parse("minecraft:barrel"), ResourceLocation.parse("minecraft:smelting"), null, 1,
                null, null, List.of(0, 1, 2), List.of(3), 1200, 0);
        try {
            Class<?> slotsClass = Class.forName("com.cappleapple.astralrepository.crafting.MachineInventory$Slots");
            Constructor<?> slotsConstructor = slotsClass.getDeclaredConstructor(Object.class); slotsConstructor.setAccessible(true);
            Object slots = slotsConstructor.newInstance(handler);
            Class<?> boundaryClass = Class.forName("com.cappleapple.astralrepository.crafting.MachineInventory$Boundary");
            Constructor<?> boundaryConstructor = boundaryClass.getDeclaredConstructor(slotsClass, slotsClass, int.class); boundaryConstructor.setAccessible(true);
            Object boundary = boundaryConstructor.newInstance(slots, slots, -1);
            Method start = InventoryProcessingAdapter.class.getDeclaredMethod("startBoundary", CraftingService.NetworkAccess.class, GlobalPos.class,
                    CraftPlan.Node.class, ProcessingRules.Rule.class, Map.class, boundaryClass); start.setAccessible(true);
            return (CraftScheduler.Operation<ItemKey>) start.invoke(new InventoryProcessingAdapter(), fixture, fixture.at(position), node, rule, Map.of(recipe.output(), 1L), boundary);
        } catch (InvocationTargetException failure) { throw new IllegalStateException("Inventory failure fixture could not start", failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Inventory failure fixture contract changed", failure); }
    }

    public static final class FaultHandler {
        final List<ItemStack> stacks = new ArrayList<>(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        final boolean throwSecond, rejectSimulation;
        int mutations;
        FaultHandler(boolean throwSecond, boolean rejectSimulation) { this.throwSecond = throwSecond; this.rejectSimulation = rejectSimulation; }
        public int getSlots() { return 4; }
        public ItemStack getStackInSlot(int slot) { return stacks.get(slot); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            boolean matches = slot == 0 && stack.is(Items.IRON_INGOT) || slot == 1 && stack.is(Items.GOLD_INGOT) || slot == 2 && stack.is(Items.DIAMOND);
            if (!matches || simulate && rejectSimulation && slot == 1) return stack.copy();
            if (simulate) return ItemStack.EMPTY;
            mutations++;
            if (slot == 1) {
                stacks.set(slot, stack.copyWithCount(throwSecond ? stack.getCount() : 1));
                if (throwSecond) throw new IllegalStateException("Intentional callback failure after actual insertion");
                return stack.copyWithCount(stack.getCount() - 1);
            }
            stacks.set(slot, stack.copy()); return ItemStack.EMPTY;
        }
        public ItemStack extractItem(int slot, int count, boolean simulate) {
            var present = stacks.get(slot); var result = present.copyWithCount(Math.min(count, present.getCount()));
            if (!simulate) { present.shrink(result.getCount()); if (present.isEmpty()) stacks.set(slot, ItemStack.EMPTY); }
            return result;
        }
    }
}
