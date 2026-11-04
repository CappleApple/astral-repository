package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NexusWorkspaceGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void closedGridKeepsActualItemsAndRecipeWithoutDuplicatingResults(GameTestHelper h) {
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"saved-nexus-grid"));
        var menu=new NexusMenu(301,player.getInventory());player.containerMenu=menu;
        ItemStack logs=new ItemStack(Items.OAK_LOG,3);
        logs.set(DataComponents.CUSTOM_NAME,Component.literal("Saved crafting ingredients"));
        menu.grid.setItem(4,logs);
        h.assertTrue(menu.selectCraftingRecipe(ResourceLocation.parse("minecraft:oak_planks")),"Select the actual vanilla recipe");
        menu.removed(player);
        for(int slot=0;slot<36;slot++)h.assertTrue(player.getInventory().getItem(slot).isEmpty(),"Closing leaves ingredients in the workspace");
        var reopened=new NexusMenu(302,player.getInventory());player.containerMenu=reopened;
        h.assertTrue(reopened.grid.getItem(4)==menu.grid.getItem(4)&&ItemStack.isSameItemSameComponents(reopened.grid.getItem(4),logs)&&reopened.grid.getItem(4).getCount()==3,"Reopen borrows the same owned ingredients with components and layout");
        h.assertTrue(reopened.slots.getFirst().getItem().is(Items.OAK_PLANKS),"The selected recipe result is recomputed on reopen");
        reopened.clicked(0,0,ClickType.PICKUP,player);
        h.assertTrue(reopened.getCarried().is(Items.OAK_PLANKS)&&reopened.getCarried().getCount()==4&&reopened.grid.getItem(4).getCount()==2,"One result consumes exactly one saved ingredient");
        var data=NexusCraftingData.get(h.getLevel().getServer());
        var restored=NexusCraftingData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        var state=restored.workspace(player.getUUID());
        h.assertTrue(state.recipe.equals("minecraft:oak_planks")&&state.items.get(4).getCount()==2&&ItemStack.isSameItemSameComponents(state.items.get(4),logs),"World serialization retains exact remaining ingredients and recipe");
        var other=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"other-nexus-grid"));
        h.assertTrue(new NexusMenu(303,other.getInventory()).grid.isEmpty(),"Each player owns a separate workspace");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void ordinaryDepositsSkipIdleFurnacesEvenWhenStorageIsFull(GameTestHelper h) {
        BlockPos nexus=new BlockPos(5,2,5),chest=nexus.west(),furnace=nexus.east();
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(chest,Blocks.CHEST);h.setBlock(furnace,Blocks.FURNACE);
        var storage=(Container)h.getBlockEntity(chest);var machine=(Container)h.getBlockEntity(furnace);
        for(int slot=0;slot<storage.getContainerSize();slot++)storage.setItem(slot,new ItemStack(Items.STONE,64));
        var origin=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(nexus));
        var position=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(furnace));
        var manager=NetworkManager.get(h.getLevel().getServer());
        h.startSequence().thenWaitUntil(()->{
            var network=manager.networkAt(origin);
            h.assertTrue(network!=null&&network.workstations().contains(position)&&network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.STONE)),0L)==1728,"Storage and idle furnace are discovered");
        }).thenExecute(()->{
            var network=manager.networkAt(origin);
            ItemStack offered=new ItemStack(Items.RAW_IRON,12);
            var rest=network.insertAt(offered,origin);
            h.assertTrue(rest.getCount()==12&&machine.isEmpty(),"Full storage rejects the deposit without putting raw iron into the idle furnace");
            storage.setItem(0,ItemStack.EMPTY);network.providerChanged(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(chest)));
            rest=network.insertAt(offered,origin);
            h.assertTrue(rest.isEmpty()&&storage.getItem(0).is(Items.RAW_IRON)&&storage.getItem(0).getCount()==12&&machine.isEmpty(),"Available storage accepts the entire deposit and leaves the machine idle");
            h.succeed();
        });
    }

    private NexusWorkspaceGameTests() {}
}
