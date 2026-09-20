package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class ProviderGameTests {
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void capabilityInvalidationDisablesOldProviderAndAllowsRediscovery(GameTestHelper h) {
        BlockPos local=new BlockPos(1,1,1);
        h.setBlock(local,Blocks.CHEST);
        var chest=(ChestBlockEntity)h.getBlockEntity(local);
        chest.setItem(0,new ItemStack(Items.IRON_INGOT,7));
        var pos=h.absolutePos(local);
        var old=CompatibilityRegistry.discoverStorage(h.getLevel(),pos,null).getFirst();
        h.assertTrue(old.valid(),"Initially discovered capability is valid");
        h.getLevel().invalidateCapabilities(pos);
        h.assertTrue(!old.valid(),"Capability invalidation immediately disables its old provider");
        var replacement=CompatibilityRegistry.discoverStorage(h.getLevel(),pos,null).getFirst();
        h.assertTrue(replacement.valid(),"Rediscovery obtains a fresh valid capability");
        h.assertTrue(replacement.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==7,"Rediscovery retains the actual stored quantity");
        h.succeed();
    }
}