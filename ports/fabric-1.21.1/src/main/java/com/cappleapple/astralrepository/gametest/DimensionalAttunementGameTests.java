package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.*;
import java.util.ArrayList;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class DimensionalAttunementGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void bothHandOrdersConsumeExactlyOneOnlyOnSuccessfulOrbUpgrade(GameTestHelper h){
        Player player=h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack upgrades=new ItemStack(AstralContent.DIMENSIONAL_ATTUNEMENT.get(),3),orb=new ItemStack(AstralContent.ASTRAL_NEXUS.get());
        GlobalPos binding=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(new BlockPos(3,2,3)));
        PhysicalProgramming.bind(orb,"BoundNexus",binding);
        player.setItemInHand(InteractionHand.MAIN_HAND,orb);player.setItemInHand(InteractionHand.OFF_HAND,upgrades);
        orb.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(AstralNexusItem.attuned(orb)&&upgrades.getCount()==2&&binding.equals(AstralNexusItem.bound(orb)),"Using the orb consumes one attunement and preserves its bound Nexus");
        orb.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(upgrades.getCount()==2,"Repeated use cannot consume another attunement");
        ItemStack otherOrb=new ItemStack(AstralContent.ASTRAL_NEXUS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,upgrades);player.setItemInHand(InteractionHand.OFF_HAND,otherOrb);
        upgrades.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(AstralNexusItem.attuned(otherOrb)&&upgrades.getCount()==1,"Using the attunement with the orb in the other hand also consumes exactly one");
        upgrades.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(upgrades.getCount()==1,"An already attuned offhand orb cannot consume another upgrade");
        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.STICK));
        upgrades.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(upgrades.getCount()==1,"An invalid offhand item does not consume the upgrade");
        Player creative=h.makeMockPlayer(GameType.CREATIVE);ItemStack free=new ItemStack(AstralContent.DIMENSIONAL_ATTUNEMENT.get()),creativeOrb=new ItemStack(AstralContent.ASTRAL_NEXUS.get());
        creative.setItemInHand(InteractionHand.MAIN_HAND,free);creative.setItemInHand(InteractionHand.OFF_HAND,creativeOrb);free.getItem().use(h.getLevel(),creative,InteractionHand.MAIN_HAND);
        h.assertTrue(AstralNexusItem.attuned(creativeOrb)&&free.getCount()==1,"Creative players retain the attunement");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void onlyLongRangeRelaysConsumeTheUnifiedDimensionalUpgrade(GameTestHelper h){
        var relayPos=new BlockPos(3,2,3);var storagePos=relayPos.east(3);var stonePos=relayPos.west();
        h.setBlock(relayPos,AstralContent.RELAY_CRYSTAL.get());h.setBlock(storagePos,AstralContent.SEED_STORAGE_CRYSTAL.get());h.setBlock(stonePos,Blocks.STONE);
        var relay=(CrystalNodeBlockEntity)h.getBlockEntity(relayPos);var storage=(CrystalNodeBlockEntity)h.getBlockEntity(storagePos);
        Player player=h.makeMockPlayer(GameType.SURVIVAL);ItemStack upgrades=new ItemStack(AstralContent.DIMENSIONAL_ATTUNEMENT.get(),3);
        useOn(h,player,upgrades,stonePos);useOn(h,player,upgrades,storagePos);useOn(h,player,upgrades,relayPos);
        h.assertTrue(upgrades.getCount()==3&&!relay.dimensional()&&!storage.dimensional(),"Invalid blocks and relays without the range upgrade do not consume attunements");
        ItemStack range=new ItemStack(AstralContent.RANGE_ATTUNEMENT.get());useOn(h,player,range,relayPos);
        h.assertTrue(range.isEmpty()&&relay.longRange(),"The existing range upgrade still consumes one item");
        useOn(h,player,upgrades,relayPos);
        h.assertTrue(upgrades.getCount()==2&&relay.dimensional(),"An eligible relay consumes one Dimensional Attunement");
        useOn(h,player,upgrades,relayPos);
        h.assertTrue(upgrades.getCount()==2,"A repeated relay upgrade does not consume another item");
        var restored=new CrystalNodeBlockEntity(relay.getBlockPos(),relay.getBlockState());restored.loadWithComponents(relay.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.longRange()&&restored.dimensional(),"Existing persisted relay upgrade fields are preserved");
        var creativePos=relayPos.south(3);h.setBlock(creativePos,AstralContent.RELAY_CRYSTAL.get());var creativeRelay=(CrystalNodeBlockEntity)h.getBlockEntity(creativePos);creativeRelay.upgradeRange();
        useOn(h,h.makeMockPlayer(GameType.CREATIVE),upgrades,creativePos);
        h.assertTrue(creativeRelay.dimensional()&&upgrades.getCount()==2,"Creative relay upgrades retain the item");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void legacyIdsLoadAsOneItemAndTooltipsContainOnlyState(GameTestHelper h){
        var item=AstralContent.DIMENSIONAL_ATTUNEMENT.get();
        for(String old:java.util.List.of("bridge_attunement","remote_attunement")){
            var id=ResourceLocation.fromNamespaceAndPath("astral_repository",old);
            h.assertTrue(BuiltInRegistries.ITEM.get(id)==item,"Old registry ID resolves to the canonical item: "+old);
            var saved=new CompoundTag();saved.putString("id",id.toString());saved.putInt("count",3);
            var restored=ItemStack.parseOptional(h.getLevel().registryAccess(),saved);
            h.assertTrue(restored.is(item)&&restored.getCount()==3,"Legacy saved stacks retain their item count: "+old);
            h.assertTrue(AstralContent.ITEMS.getEntries().stream().noneMatch(entry->entry.getId().equals(id)),"Legacy aliases do not register duplicate creative or recipe-viewer entries");
            h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isEmpty(),"Obsolete duplicate recipe is removed");
        }
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("astral_repository","dimensional_attunement")).isPresent(),"One canonical dimensional recipe is available");
        for(var entry:AstralContent.ITEMS.getEntries())if(entry.get() instanceof AstralToolItem){
            var lines=new ArrayList<Component>();entry.get().appendHoverText(new ItemStack(entry.get()),Item.TooltipContext.of(h.getLevel()),lines,TooltipFlag.NORMAL);
            h.assertTrue(lines.isEmpty(),"Unprogrammed tool has no instructional tooltip: "+entry.getId());
        }
        var legacyOrb=new ItemStack(AstralContent.ASTRAL_NEXUS.get());var legacyData=new CompoundTag();legacyData.putBoolean("DimensionalRemote",true);legacyOrb.set(DataComponents.CUSTOM_DATA,CustomData.of(legacyData));
        PhysicalProgramming.bind(legacyOrb,"BoundNexus",GlobalPos.of(h.getLevel().dimension(),h.absolutePos(new BlockPos(3,2,3))));
        h.assertTrue(AstralNexusItem.attuned(legacyOrb),"Previously applied orb upgrades remain recognized");
        var lines=new ArrayList<Component>();legacyOrb.getItem().appendHoverText(legacyOrb,Item.TooltipContext.of(h.getLevel()),lines,TooltipFlag.NORMAL);
        h.assertTrue(lines.size()==2,"A programmed orb retains only its binding and dimensional state");
        h.succeed();
    }

    private static void useOn(GameTestHelper h,Player player,ItemStack stack,BlockPos relative){
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);var pos=h.absolutePos(relative);
        stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter().add(0,.5,0),Direction.UP,pos,false)));
    }
}
