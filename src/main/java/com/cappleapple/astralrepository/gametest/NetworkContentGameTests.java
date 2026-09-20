package com.cappleapple.astralrepository.gametest;

import net.minecraft.core.Direction;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NetworkContentGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=300)
    public static void zeroConfigurationChestDiscoveryAndImmediateExtraction(GameTestHelper h) {
        BlockPos nexus = new BlockPos(4,2,4), chest = nexus.east(2);
        h.setBlock(nexus, AstralContent.STORAGE_NEXUS.get()); h.setBlock(chest, Blocks.CHEST);
        var inventory = (ChestBlockEntity) h.getBlockEntity(chest);
        inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        var origin = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(nexus));
        ItemKey iron = new ItemKey(new ItemStack(Items.IRON_INGOT));
        h.succeedWhen(() -> {
            var network = NetworkManager.get(h.getLevel().getServer()).networkAt(origin);
            h.assertTrue(network != null, "Nexus registered automatically");
            h.assertTrue(network.snapshot().getOrDefault(iron, 0L) == 64, "Nearby chest indexed without a binding tool");
            ItemStack taken = network.extract(iron, 32);
            h.assertTrue(taken.getCount() == 32, "Player-sized withdrawal completes synchronously");
            h.assertTrue(inventory.getItem(0).getCount() == 32, "Actual chest debited immediately");
            h.assertTrue(network.snapshot().getOrDefault(iron, 0L) == 32, "Cached total updated with transaction");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=300)
    public static void dyedNexusesKeepAutomaticCoverageSeparate(GameTestHelper h) {
        BlockPos a = new BlockPos(4,2,4), b = new BlockPos(10,2,4);
        h.setBlock(a, AstralContent.STORAGE_NEXUS.get()); h.setBlock(b, AstralContent.STORAGE_NEXUS.get());
        ((CrystalNodeBlockEntity)h.getBlockEntity(a)).setChannel(1);
        ((CrystalNodeBlockEntity)h.getBlockEntity(b)).setChannel(14);
        BlockPos ac = a.west(), bc = b.east();
        h.setBlock(ac, Blocks.CHEST); h.setBlock(bc, Blocks.CHEST);
        ((ChestBlockEntity)h.getBlockEntity(ac)).setItem(0,new ItemStack(Items.IRON_INGOT,17));
        ((ChestBlockEntity)h.getBlockEntity(bc)).setItem(0,new ItemStack(Items.GOLD_INGOT,23));
        var ap = GlobalPos.of(h.getLevel().dimension(),h.absolutePos(a));
        var bp = GlobalPos.of(h.getLevel().dimension(),h.absolutePos(b));
        ItemKey iron = new ItemKey(new ItemStack(Items.IRON_INGOT)), gold = new ItemKey(new ItemStack(Items.GOLD_INGOT));
        h.succeedWhen(() -> {
            var manager = NetworkManager.get(h.getLevel().getServer());
            var an = manager.networkAt(ap); var bn = manager.networkAt(bp);
            h.assertTrue(an != null && bn != null && an != bn, "Different dye channels create separate networks");
            h.assertTrue(an.snapshot().getOrDefault(iron,0L)==17 && bn.snapshot().getOrDefault(gold,0L)==23, "Both nearest-owner storages discovered");
            h.assertTrue(!an.snapshot().containsKey(gold) && !bn.snapshot().containsKey(iron), "Opposite-channel contents remain invisible");
        });
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=300)
    public static void finitePhysicalStockRulePullsOnlyMatchingItems(GameTestHelper h) {
        BlockPos nexus = new BlockPos(4,2,4), sourcePos = nexus.west(), targetPos = new BlockPos(10,2,4), runePos = targetPos.above();
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get()); h.setBlock(sourcePos,Blocks.CHEST);
        h.setBlock(targetPos,Blocks.CHEST); 
        ((CrystalNodeBlockEntity)h.getBlockEntity(nexus)).setChannel(10);
        var surface=com.cappleapple.astralrepository.content.RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(targetPos),Direction.UP);
        var rune=surface.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PULL),com.cappleapple.astralrepository.content.RuneLayer.Mode.PULL);
        rune.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);rune.filter().setTarget(16);
        h.assertTrue(surface.toggleTarget(rune.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(sourcePos)),Direction.UP).success(),"Pull binds the actual source");

        var source=(ChestBlockEntity)h.getBlockEntity(sourcePos);var destination=(ChestBlockEntity)h.getBlockEntity(targetPos);
        source.setItem(0,new ItemStack(Items.IRON_INGOT,64));source.setItem(1,new ItemStack(Items.GOLD_INGOT,8));
        h.succeedWhen(()->{
            int iron=0,gold=0;
            for(int slot=0;slot<destination.getContainerSize();slot++){
                ItemStack value=destination.getItem(slot);if(value.is(Items.IRON_INGOT))iron+=value.getCount();if(value.is(Items.GOLD_INGOT))gold+=value.getCount();
            }
            h.assertTrue(iron==16,"Finite stock target pulls 16 iron without a Collection Crystal");
            h.assertTrue(gold==0,"Insertion filter rejects gold");
            h.assertTrue(source.getItem(0).getCount()==48&&source.getItem(1).getCount()==8,"Source inventory is debited exactly and unrelated resources remain");
        });
    }
}
