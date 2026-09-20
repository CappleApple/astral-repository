package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import com.cappleapple.astralrepository.platform.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class ContentGameTests {
    private static final String EMPTY = "bastion/mobs/empty";

    @GameTest(templateNamespace="minecraft", template=EMPTY)
    public static void multipleFiltersAndExclusions(GameTestHelper h) {
        FilterRules rules = new FilterRules();
        rules.add(FilterRules.Kind.ITEM, "minecraft:iron_ingot", false, ItemStack.EMPTY);
        rules.add(FilterRules.Kind.ITEM, "minecraft:copper_ingot", false, ItemStack.EMPTY);
        h.assertTrue(rules.matches(new ItemStack(Items.IRON_INGOT)), "First exact item matches");
        h.assertTrue(rules.matches(new ItemStack(Items.COPPER_INGOT)), "Second exact item matches");
        h.assertTrue(!rules.matches(new ItemStack(Items.GOLD_INGOT)), "Unknown item rejected");
        rules.add(FilterRules.Kind.ITEM, "minecraft:iron_ingot", true, ItemStack.EMPTY);
        h.assertTrue(!rules.matches(new ItemStack(Items.IRON_INGOT)), "Exclusion vetoes positive match");
        h.assertTrue(!rules.matches(new FluidStack(Fluids.WATER, 1000)), "Item whitelist cannot silently admit fluids");
        h.succeed();
    }

    @GameTest(templateNamespace="minecraft", template=EMPTY)
    public static void tagsComponentsAndPersistence(GameTestHelper h) {
        FilterRules rules = new FilterRules();
        rules.add(FilterRules.Kind.ITEM_TAG, "minecraft:planks", false, ItemStack.EMPTY);
        rules.add(FilterRules.Kind.NAMESPACE, "minecraft", false, ItemStack.EMPTY);
        rules.toggleAll();
        h.assertTrue(rules.matches(new ItemStack(Items.OAK_PLANKS)), "Tag plus namespace AND matches");
        h.assertTrue(rules.matches(new ItemStack(Items.IRON_INGOT)), "Legacy all-match data still uses any matching rule");
        rules.clear();
        ItemStack named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Sample"));
        rules.add(FilterRules.Kind.COMPONENTS, "minecraft:iron_ingot", false, named);
        rules.setMinimum(32); rules.setTarget(64);
        FilterRules copy = new FilterRules(); copy.load(rules.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(copy.matches(named) && !copy.matches(new ItemStack(Items.IRON_INGOT)), "Component equality survives persistence");
        h.assertTrue(copy.minimum() == 32 && copy.target() == 64, "Stock limits survive persistence");
        h.succeed();
    }

    @GameTest(templateNamespace="minecraft", template=EMPTY)
    public static void capacityCostsAndSimulation(GameTestHelper h) {
        CapacityInventory inventory = new CapacityInventory(() -> 128, () -> {});
        h.assertTrue(inventory.insertItem(0, new ItemStack(Items.IRON_SWORD), true).isEmpty(), "Simulated insertion fits");
        h.assertTrue(inventory.used() == 0, "Simulation does not consume capacity");
        inventory.insertItem(0, new ItemStack(Items.IRON_SWORD), false);
        h.assertTrue(inventory.used() == 64, "An unstackable item costs 64 units");
        inventory.insertItem(inventory.getSlots()-1, new ItemStack(Items.IRON_INGOT, 64), false);
        h.assertTrue(inventory.used() == 128, "Stackable items cost one unit each");
        h.assertTrue(!inventory.insertItem(inventory.getSlots()-1, new ItemStack(Items.COPPER_INGOT), false).isEmpty(), "Over-capacity insertion rejected");
        inventory.extractItem(0, 1, true);
        h.assertTrue(inventory.used() == 128, "Simulated extraction leaves contents intact");
        inventory.extractItem(0, 1, false);
        h.assertTrue(inventory.used() == 64, "Extraction releases charged capacity");
        h.succeed();
    }

    @GameTest(templateNamespace="minecraft", template=EMPTY)
    public static void fractionalStackCapacityNeverRoundsPerItem(GameTestHelper h) {
        CapacityInventory inventory = new CapacityInventory(() -> 64, () -> {});
        ItemStack sample = new ItemStack(Items.IRON_INGOT, 3);
        sample.set(DataComponents.MAX_STACK_SIZE, 3);
        h.assertTrue(inventory.insertItem(0, sample, false).isEmpty(), "Three items at 64/3 units fit exactly in 64 units");
        h.assertTrue(inventory.used() == 64, "Exact fraction sums to 64 rather than 66");
        h.assertTrue(!inventory.insertItem(0, sample.copyWithCount(1), true).isEmpty(), "Exact capacity is full");
        inventory.extractItem(0, 1, false);
        h.assertTrue(inventory.insertItem(0, sample.copyWithCount(1), true).isEmpty(), "Fractional extraction restores exact room");
        h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=EMPTY)
    public static void storageDropsPreserveContentsAndChannels(GameTestHelper h) {
        BlockPos pos = new BlockPos(1,2,1);
        h.setBlock(pos, AstralContent.SEED_STORAGE_CRYSTAL.get());
        var node = (CrystalNodeBlockEntity) h.getBlockEntity(pos);
        node.setChannel(11); node.setPriority(7);
        var capability = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), Direction.UP);
        h.assertTrue(capability != null, "Storage exposes an item capability");
        capability.insertItem(0, new ItemStack(Items.DIAMOND, 47), false);
        var drops = Block.getDrops(node.getBlockState(), h.getLevel(), node.getBlockPos(), node);
        h.assertTrue(drops.size() == 1 && drops.get(0).has(DataComponents.BLOCK_ENTITY_DATA), "Drop contains block entity component");
        h.setBlock(pos, Blocks.AIR); h.setBlock(pos.below(), Blocks.STONE);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var below = h.absolutePos(pos.below());
        var hit = new net.minecraft.world.phys.BlockHitResult(below.getCenter().add(0,.5,0),Direction.UP,below,false);
        var context = new net.minecraft.world.item.context.BlockPlaceContext(h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,drops.get(0).copy(),hit);
        var placed = ((net.minecraft.world.item.BlockItem)drops.get(0).getItem()).place(context);
        h.assertTrue(placed.consumesAction(), "Dropped block item can actually be placed");
        var restored = (CrystalNodeBlockEntity) h.getBlockEntity(pos);
        h.assertTrue(restored.inventory().getStackInSlot(0).getCount() == 47, "Contents survive block item round trip");
        h.assertTrue(restored.channel() == 11 && restored.priority() == 7, "Physical programming survives move");
        h.assertTrue(!restored.inventory().isItemValid(0, drops.get(0)), "Recursive storage nesting is rejected");
        h.succeed();
    }

    @GameTest(templateNamespace="minecraft", template=EMPTY)
    public static void fluidTagFilteringAndTomeBookshelfTag(GameTestHelper h) {
        FilterRules rules = new FilterRules();
        rules.add(FilterRules.Kind.FLUID_TAG, "minecraft:water", false, ItemStack.EMPTY);
        h.assertTrue(rules.matches(new FluidStack(Fluids.WATER, 1000)), "Water tag matches water");
        h.assertTrue(!rules.matches(new FluidStack(Fluids.LAVA, 1000)), "Water tag rejects lava");
        h.assertTrue(new ItemStack(AstralContent.RECIPE_TOME.get()).is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS), "Tomes insert into vanilla chiseled bookshelves");
        h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=EMPTY)
    public static void everyPackagedRecipeLoadsIntoTheServerRecipeManager(GameTestHelper h) {
        var resources = h.getLevel().getServer().getResourceManager().listResources("recipe", id -> id.getNamespace().equals("astral_repository") && id.getPath().endsWith(".json"));
        h.assertTrue(!resources.isEmpty(), "Packaged crafting recipes are discoverable");
        for (var resource : resources.keySet()) {
            String path = resource.getPath();
            var id = new net.minecraft.resources.ResourceLocation(resource.getNamespace(),path.substring("recipe/".length(),path.length()-".json".length()));
            var loaded = h.getLevel().getRecipeManager().byKey(id);
            if (id.equals(com.cappleapple.astralrepository.compat.PatchouliIntegration.BOOK) && !com.cappleapple.astralrepository.compat.PatchouliIntegration.isLoaded()) {
                h.assertTrue(loaded.isEmpty(), "The optional guide recipe is absent without Patchouli");
                continue;
            }
            h.assertTrue(loaded.isPresent(), "Recipe must parse successfully: " + id);
            ItemStack result = loaded.get().value().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(!result.isEmpty() && result.getCount() <= result.getMaxStackSize(), "Recipe result respects registered maximum: " + id);
        }
        h.succeed();
    }
}
