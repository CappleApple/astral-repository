package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class ResourceReservationGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void wrongItemExtractionNeverBecomesTheRequestedResource(GameTestHelper h) {
        var position = new BlockPos(3,2,3);
        h.setBlock(position, Blocks.BARREL);
        var barrel = (Container) h.getBlockEntity(position);
        barrel.setItem(0, new ItemStack(Items.DIAMOND));
        var access = new CraftingGameTests.Fixture(h,position,barrel,List.of(),new ItemStack(Items.CLAY_BALL)) {
            @Override public ItemStack extract(ItemKey key,int amount) { return barrel.removeItem(0,amount); }
        };
        int[] receipts = {0};
        boolean rejected = false;
        try { access.reserveResource(new ItemKey(new ItemStack(Items.CLAY_BALL)),1,(from,amount)->receipts[0]++); }
        catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected && receipts[0] == 0,"A wrong physical stack never enters requested-resource escrow");
        h.assertTrue(barrel.getItem(0).is(Items.DIAMOND) && barrel.getItem(0).getCount()==1,
                "The actual wrong item is returned once instead of being lost or converted into clay");
        h.assertTrue(!access.snapshot().containsKey(new ItemKey(new ItemStack(Items.CLAY_BALL))),"No requested item was manufactured");
        h.succeed();
    }
    private ResourceReservationGameTests() {}
}
