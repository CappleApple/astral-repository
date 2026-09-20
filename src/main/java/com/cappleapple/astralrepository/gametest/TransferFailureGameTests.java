package com.cappleapple.astralrepository.gametest;
import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class TransferFailureGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400,batch="astral_failure")
    public static void destinationCommitFailureNeverRefundsAnAlreadyInsertedOffer(GameTestHelper h){
        BlockPos nexus=new BlockPos(4,2,4),sourcePos=nexus.west(),targetPos=new BlockPos(10,2,4),runePos=targetPos.above();
        BlockPos absolute=h.absolutePos(targetPos);int[] accepted={0};
        ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
        String id="failure_fixture_"+UUID.randomUUID();
        StorageProvider broken=new StorageProvider(){
            public String id(){return id;}public Object identity(){return this;}
            public boolean valid(){return h.getLevel().getBlockState(absolute).is(Blocks.ENCHANTING_TABLE);}
            public long capacity(){return 64;}public Map<ItemKey,Long> snapshot(){return accepted[0]==0?Map.of():Map.of(iron,(long)accepted[0]);}
            public ItemStack extract(ItemKey key,int amount,boolean simulate){return ItemStack.EMPTY;}
            public ItemStack insert(ItemStack stack,boolean simulate){if(simulate)return ItemStack.EMPTY;accepted[0]+=stack.getCount();throw new IllegalStateException("Expected fixture failure after successful insertion");}
        };
        CompatibilityRegistry.registerStorage(id,(level,pos,side)->level==h.getLevel()&&pos.equals(absolute)?List.of(broken):List.of());
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(sourcePos,Blocks.CHEST);h.setBlock(targetPos,Blocks.ENCHANTING_TABLE);
        ((CrystalNodeBlockEntity)h.getBlockEntity(nexus)).setChannel(8);
        var surface=com.cappleapple.astralrepository.content.RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(targetPos),Direction.UP);
        var rune=surface.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PULL),com.cappleapple.astralrepository.content.RuneLayer.Mode.PULL);
        rune.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);rune.filter().setTarget(16);
        h.assertTrue(surface.toggleTarget(rune.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(sourcePos)),Direction.UP).success(),"Pull binds the actual source");

        var source=(ChestBlockEntity)h.getBlockEntity(sourcePos);source.setItem(0,new ItemStack(Items.IRON_INGOT,64));
        h.succeedWhen(()->{
            h.assertTrue(accepted[0]==16,"Destination accepted the one offered batch");
            h.assertTrue(source.getItem(0).getCount()==48,"Uncertain committed items were not recreated in source");
            var saved=TransferRecoveryData.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getList("Transfers",Tag.TAG_COMPOUND);
            boolean recorded=false;
            for(int i=0;i<saved.size();i++)if(saved.getCompound(i).getString("Provider").contains(id)&&saved.getCompound(i).getBoolean("Uncertain")&&saved.getCompound(i).getLong("Amount")==16)recorded=true;
            h.assertTrue(recorded,"Uncertain offer preserved in the non-replaying audit journal");
        });
    }
}

