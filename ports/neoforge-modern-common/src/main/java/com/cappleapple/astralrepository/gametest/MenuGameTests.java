package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.menu.NexusMenu;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class MenuGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void shiftCraftWaitsUntilTheWholeRecipeOutputFits(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "menu-craft-test"));
        BlockPos position = h.absolutePos(new BlockPos(4, 3, 4));
        player.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 63));
        NexusMenu menu = new NexusMenu(71, player.getInventory());
        menu.grid.setItem(0, new ItemStack(Items.OAK_LOG));
        h.assertTrue(menu.slots.getFirst().getItem().is(Items.OAK_PLANKS)
                && menu.slots.getFirst().getItem().getCount() == 4, "Actual server recipe produces four planks");
        ItemStack crafted = menu.quickMoveStack(player, 0);
        h.assertTrue(crafted.isEmpty(), "A four-item recipe waits when only one item fits");
        h.assertTrue(player.getInventory().getItem(0).getCount() == 63, "The partial output is not inserted");
        long dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).stream()
                .filter(entity -> entity.getItem().is(Items.OAK_PLANKS)).mapToLong(entity -> entity.getItem().getCount()).sum();
        h.assertTrue(dropped == 0, "A rejected batch drops no output");
        h.assertTrue(menu.grid.getItem(0).getCount() == 1, "The recipe input remains untouched");
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 60));
        h.assertTrue(menu.quickMoveStack(player, 0).getCount() == 4, "The same recipe crafts when its full output fits");
        h.assertTrue(player.getInventory().getItem(0).getCount() == 64 && menu.grid.getItem(0).isEmpty(), "One complete batch inserts and consumes once");        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void shiftCraftReservesInventoryRoomForOverflowingBottleRemainders(GameTestHelper h){
        FakePlayer player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"menu-remainder-test"));
        BlockPos position=h.absolutePos(new BlockPos(4,3,4));player.setPos(position.getX()+.5,position.getY(),position.getZ()+.5);
        for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
        player.getInventory().setItem(0,new ItemStack(Items.SUGAR,61));
        NexusMenu menu=new NexusMenu(72,player.getInventory());menu.grid.setItem(0,new ItemStack(Items.HONEY_BOTTLE,2));
        h.assertTrue(menu.slots.getFirst().getItem().is(Items.SUGAR)&&menu.slots.getFirst().getItem().getCount()==3,"Actual vanilla honey recipe produces three sugar");
        h.assertTrue(menu.quickMoveStack(player,0).isEmpty(),"Output space alone is insufficient when a returned bottle also needs inventory space");
        h.assertTrue(menu.grid.getItem(0).getCount()==2&&player.getInventory().getItem(0).getCount()==61,"Rejected batch consumes neither honey nor output capacity");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(position).inflate(4)).stream().noneMatch(e->e.getItem().is(Items.GLASS_BOTTLE)),"Rejected batch drops no glass bottle");
        player.getInventory().setItem(1,ItemStack.EMPTY);
        h.assertTrue(menu.quickMoveStack(player,0).getCount()==3,"Batch proceeds once both its output and remainder fit");
        h.assertTrue(player.getInventory().getItem(0).getCount()==64&&player.getInventory().getItem(1).is(Items.GLASS_BOTTLE)&&player.getInventory().getItem(1).getCount()==1&&menu.grid.getItem(0).getCount()==1,"Vanilla consumes one honey, inserts three sugar, and preserves exactly one returned bottle");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(position).inflate(4)).stream().noneMatch(e->e.getItem().is(Items.GLASS_BOTTLE)),"Successful bounded crafting also drops no bottle");h.succeed();
    }    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=160)
    public static void craftingFailureRemainsVisibleAcrossMenuRefreshes(GameTestHelper h) {
        BlockPos local = new BlockPos(4, 2, 4);
        h.setBlock(local, com.cappleapple.astralrepository.content.AstralContent.STORAGE_NEXUS.get());
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "menu-feedback-test"));
        BlockPos position = h.absolutePos(local);
        player.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        h.runAfterDelay(40, () -> {
            var origin = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), position);
            var network = com.cappleapple.astralrepository.network.NetworkManager.get(h.getLevel().getServer()).networkAt(origin);
            h.assertTrue(network != null, "The actual Nexus network is available");
            NexusMenu menu = new NexusMenu(73, player.getInventory(), origin, false);
            menu.action(new com.cappleapple.astralrepository.network.NetworkPackets.Action(73, 3, new ItemStack(Items.DIAMOND), 1, 0, ""));
            h.assertTrue(menu.error.contains("Recipe Tome"), "Server crafting failure is shown in the open menu");
            String failure = menu.error;
            menu.sendPage();
            menu.action(new com.cappleapple.astralrepository.network.NetworkPackets.Action(73, 0, ItemStack.EMPTY, 0, 0, "diamond"));
            h.assertTrue(menu.error.equals(failure), "Index refresh and search must not overwrite action feedback");
            h.assertTrue(network.crafting().activeJobs() == 0, "The rejected request did not create a crafting job");
            h.succeed();
        });
    }
}
