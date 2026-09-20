package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.StorageProvider;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class ProviderLifecycleGameTests {
    private static BooleanSupplier physical(ServerLevel level,BlockPos pos)throws Exception{
        var method=CompatibilityRegistry.class.getDeclaredMethod("validity",ServerLevel.class,BlockPos.class);method.setAccessible(true);
        return (BooleanSupplier)method.invoke(null,level,pos);
    }
    private static StorageProvider provider(ServerLevel level,BlockPos pos){return CompatibilityRegistry.discoverStorage(level,pos,Direction.UP).get(0);}
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="provider_lifecycle")
    public static void capturedProviderViewsRejectReplacementStateChangesAndCapabilityInvalidation(GameTestHelper h)throws Exception{
        var level=h.getLevel();var relative=new BlockPos(3,2,3);h.setBlock(relative,Blocks.CHEST);var pos=h.absolutePos(relative);
        var original=(ChestBlockEntity)level.getBlockEntity(pos);original.setItem(0,new ItemStack(Items.IRON_INGOT,12));
        var view=provider(level,pos);var physical=physical(level,pos);var iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
        h.assertTrue(view.valid()&&physical.getAsBoolean(),"New provider sees its live inventory");
        var replacement=new ChestBlockEntity(pos,original.getBlockState());replacement.setItem(0,new ItemStack(Items.IRON_INGOT,7));level.setBlockEntity(replacement);
        h.assertTrue(original.isRemoved()&&!physical.getAsBoolean()&&!view.valid(),"Same-position replacement retires the captured instance immediately");
        h.assertTrue(view.extract(iron,1,false).isEmpty()&&replacement.getItem(0).getCount()==7,"Stale provider cannot mutate replacement inventory");
        var current=provider(level,pos);var currentPhysical=physical(level,pos);
        level.invalidateCapabilities(pos);
        h.assertTrue(!current.valid()&&currentPhysical.getAsBoolean(),"Capability invalidation remains immediate even with unchanged physical state");
        var afterInvalidation=provider(level,pos);h.assertTrue(afterInvalidation.valid(),"Rediscovery gets a fresh capability view");
        level.setBlockAndUpdate(pos,replacement.getBlockState().setValue(ChestBlock.FACING,Direction.EAST));
        h.assertTrue(!currentPhysical.getAsBoolean()&&!afterInvalidation.valid(),"Same-instance block-state changes invalidate the retained physical view");
        h.assertTrue(replacement.getItem(0).getCount()==7,"Lifecycle checks preserve inventory contents");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="provider_lifecycle")
    @SuppressWarnings("unchecked")
    public static void capturedChunkStatusAndUnloadRetireProviderViewsWithoutWorldLookups(GameTestHelper h)throws Exception{
        var level=h.getLevel();var relative=new BlockPos(3,2,3);h.setBlock(relative,Blocks.CHEST);var pos=h.absolutePos(relative);
        var original=(ChestBlockEntity)level.getBlockEntity(pos);original.setItem(0,new ItemStack(Items.IRON_INGOT,12));
        var physical=physical(level,pos);var view=provider(level,pos);var chunk=level.getChunkAt(pos);
        var status=LevelChunk.class.getDeclaredField("fullStatus");status.setAccessible(true);var previous=(Supplier<FullChunkStatus>)status.get(chunk);
        try{
            chunk.setFullStatus(()->FullChunkStatus.INACCESSIBLE);
            h.assertTrue(!original.isRemoved()&&!physical.getAsBoolean()&&!view.valid(),"Ticket demotion rejects live views before block-entity removal");
            var offered=new ItemStack(Items.IRON_INGOT,3);
            h.assertTrue(view.insert(offered,false).getCount()==3&&original.getItem(0).getCount()==12,"An inaccessible view cannot receive inventory changes");
            chunk.setFullStatus(previous);h.assertTrue(physical.getAsBoolean()&&view.valid(),"An unchanged instance becomes valid again when its chunk is accessible");
            // Exercise the same entity/capability retirement steps as unloading, without unloading another fixture's chunk.
            original.onChunkUnloaded();level.invalidateCapabilities(chunk.getPos());level.removeBlockEntity(pos);
            h.assertTrue(original.isRemoved()&&!physical.getAsBoolean()&&!view.valid(),"Unload retirement invalidates old native views permanently");
            var restored=new ChestBlockEntity(pos,level.getBlockState(pos));restored.setItem(0,new ItemStack(Items.IRON_INGOT,12));level.setBlockEntity(restored);
            h.assertTrue(provider(level,pos).valid()&&!view.valid()&&!physical.getAsBoolean(),"Reloaded instances require a new provider rather than reviving a stale one");
            h.succeed();
        }finally{chunk.setFullStatus(previous);}
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="provider_lifecycle")
    public static void tilelessValidityStillDetectsNewBlockEntitiesAtTheSameState(GameTestHelper h)throws Exception{
        var level=h.getLevel();var relative=new BlockPos(3,2,3);h.setBlock(relative,Blocks.STONE);var pos=h.absolutePos(relative);var chunk=level.getChunkAt(pos);
        var physical=physical(level,pos);h.assertTrue(physical.getAsBoolean(),"A tile-less physical capability can be tracked");
        // Deliberately inject a tile without changing the block state to isolate dynamic tile detection.
        var added=new ChestBlockEntity(pos,Blocks.CHEST.defaultBlockState());added.setLevel(level);chunk.getBlockEntities().put(pos,added);
        try{h.assertTrue(!physical.getAsBoolean(),"Adding a tile invalidates a previously tile-less view even with identical block state");h.succeed();}
        finally{chunk.getBlockEntities().remove(pos,added);added.setRemoved();}
    }
    private ProviderLifecycleGameTests(){}
}
