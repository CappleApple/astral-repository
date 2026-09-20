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
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NexusGameTests {
    private record Fixture(NexusMenu menu, FakePlayer player, ChestBlockEntity chest) {}
    private static Fixture fixture(GameTestHelper h,int id) {
        BlockPos nexus=new BlockPos(4,2,4),chest=nexus.west();
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(chest,Blocks.CHEST);
        FakePlayer player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"nexus-cursor-test"));
        BlockPos absolute=h.absolutePos(nexus);player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        var menu=new NexusMenu(id,player.getInventory(),GlobalPos.of(h.getLevel().dimension(),absolute),false);
        player.containerMenu=menu;
        return new Fixture(menu,player,(ChestBlockEntity)h.getBlockEntity(chest));
    }
    private static void action(NexusMenu menu,int kind,ItemStack stack,int amount) {
        menu.action(new NetworkPackets.Action(menu.containerId,kind,stack,amount,0,""));
    }
    private static long stored(NexusMenu menu,ItemStack stack) {return menu.network().snapshot().getOrDefault(new ItemKey(stack),0L);}
    private static GameTestSequence awaitStored(GameTestHelper h,NexusMenu menu,ItemStack stack,long expected) {
        // Discovery and polling are shared across the concurrent test batch, and topology changes
        // rebuild scan cursors. Wait for the real index instead of assuming a fixed tick deadline.
        return h.startSequence().thenWaitUntil(()->{
            var network=menu.network();
            long actual=network==null?-1:network.snapshot().getOrDefault(new ItemKey(stack),0L);
            h.assertTrue(actual==expected,"Storage index waiting for "+expected+", got "+actual+"; "+(network==null?"network not yet discovered":network.status()));
        });
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void cursorPickupSupportsHalvesComponentsAndVanillaCraftingDrag(GameTestHelper h) {
        var f=fixture(h,81);var menu=f.menu();var player=f.player();
        ItemStack iron=new ItemStack(Items.IRON_INGOT),named=iron.copy();named.set(DataComponents.CUSTOM_NAME,Component.literal("Cursor identity"));
        f.chest().setItem(0,iron.copyWithCount(64));f.chest().setItem(1,iron.copyWithCount(31));f.chest().setItem(2,named.copyWithCount(9));
        awaitStored(h,menu,iron,95).thenExecute(()->{
            action(menu,NetworkPackets.PICKUP,iron.copyWithCount(99),0);
            h.assertTrue(menu.getCarried().getCount()==64&&stored(menu,iron)==31,"Left click takes one legal stack onto the cursor, ignoring client stack count");
            action(menu,NetworkPackets.PICKUP,named,0);
            h.assertTrue(menu.getCarried().getCount()==64&&stored(menu,named)==9,"A queued pickup cannot overwrite a nonempty cursor");
            menu.clicked(1,1,ClickType.PICKUP,player);
            menu.clicked(10,0,ClickType.PICKUP,player);
            h.assertTrue(menu.grid.getItem(0).getCount()==1&&player.getInventory().getItem(9).getCount()==63&&menu.getCarried().isEmpty(),"Normal right-click places one in the crafting grid and left-click places the rest in inventory");
            action(menu,NetworkPackets.PICKUP,iron,1);
            h.assertTrue(menu.getCarried().getCount()==16&&stored(menu,iron)==15,"Right click rounds the available 31-item stack upward to 16");
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY,1);
            h.assertTrue(menu.getCarried().getCount()==15&&stored(menu,iron)==16,"Right click deposits exactly one from the authoritative cursor");
            action(menu,NetworkPackets.DEPOSIT,new ItemStack(Items.DIAMOND,64),0);
            h.assertTrue(menu.getCarried().isEmpty()&&stored(menu,iron)==31,"Left deposit uses actual cursor identity, not a supplied item payload");
            menu.clicked(10,1,ClickType.PICKUP,player);
            menu.clicked(1,0,ClickType.PICKUP,player);
            h.assertTrue(menu.grid.getItem(0).getCount()==33&&player.getInventory().getItem(9).getCount()==31,"Vanilla half pickup and crafting-grid merging remain intact");
            menu.clicked(10,0,ClickType.QUICK_MOVE,player);
            h.assertTrue(player.getInventory().getItem(9).isEmpty()&&stored(menu,iron)==62,"Shift click deposits the ordinary inventory stack");
            menu.clicked(1,0,ClickType.PICKUP,player);
            menu.clicked(-999,0,ClickType.QUICK_CRAFT,player);
            menu.clicked(2,1,ClickType.QUICK_CRAFT,player);
            menu.clicked(3,1,ClickType.QUICK_CRAFT,player);
            menu.clicked(-999,2,ClickType.QUICK_CRAFT,player);
            h.assertTrue(menu.grid.getItem(1).getCount()==16&&menu.grid.getItem(2).getCount()==16&&menu.getCarried().getCount()==1,"Vanilla left drag divides the carried items between crafting slots without loss");
            menu.clicked(1,0,ClickType.PICKUP,player);
            h.assertTrue(stored(menu,iron)+menu.grid.getItem(0).getCount()+menu.grid.getItem(1).getCount()+menu.grid.getItem(2).getCount()==95,"Storage and crafting grid conserve every ordinary iron ingot");
            action(menu,NetworkPackets.PICKUP,named,1);
            h.assertTrue(menu.getCarried().getCount()==5&&ItemStack.isSameItemSameComponents(menu.getCarried(),named)&&stored(menu,named)==4,"Half pickup preserves exact component identity");
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY,1);
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY,0);
            ItemStack absent=named.copy();absent.set(DataComponents.CUSTOM_NAME,Component.literal("Not stored"));
            action(menu,NetworkPackets.PICKUP,absent,0);
            action(menu,NetworkPackets.PICKUP,named,64);
            menu.action(new NetworkPackets.Action(menu.containerId+1,NetworkPackets.PICKUP,named,0,0,""));
            h.assertTrue(menu.getCarried().isEmpty()&&stored(menu,named)==9,"Unknown components, invalid pickup mode and stale container IDs cannot create items");
            h.succeed();
        });
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void cursorPartialDepositsAndQuickWithdrawalRespectCapacity(GameTestHelper h) {
        var f=fixture(h,82);var menu=f.menu();var player=f.player();ItemStack iron=new ItemStack(Items.IRON_INGOT);
        for(int i=0;i<f.chest().getContainerSize();i++)f.chest().setItem(i,new ItemStack(Items.STONE,64));
        f.chest().setItem(0,iron.copyWithCount(57));
        awaitStored(h,menu,iron,57).thenExecute(()->{
            menu.setCarried(iron.copyWithCount(10));
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY,1);
            h.assertTrue(menu.getCarried().getCount()==9&&stored(menu,iron)==58,"Right deposit commits only one item");
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY,0);
            h.assertTrue(menu.getCarried().getCount()==3&&stored(menu,iron)==64,"Partial insertion leaves all three rejected items on cursor");
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY,1);
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY,0);
            h.assertTrue(menu.getCarried().getCount()==3&&stored(menu,iron)==64,"Rejected repeated deposits neither duplicate nor void the cursor");
            menu.clicked(1,0,ClickType.PICKUP,player);
            for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            player.getInventory().setItem(0,iron.copyWithCount(63));
            action(menu,NetworkPackets.QUICK_WITHDRAW,iron,Integer.MAX_VALUE);
            h.assertTrue(player.getInventory().getItem(0).getCount()==64&&stored(menu,iron)==63&&menu.getCarried().isEmpty(),"Shift withdrawal takes only the one item that fits, ignoring client quantity");
            action(menu,NetworkPackets.QUICK_WITHDRAW,iron,0);
            h.assertTrue(stored(menu,iron)==63&&menu.getCarried().isEmpty(),"Full inventory leaves the remaining storage untouched");
            action(menu,NetworkPackets.PICKUP,iron,1);
            h.assertTrue(menu.getCarried().getCount()==32&&stored(menu,iron)==31,"A full inventory does not prevent normal cursor pickup");
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY,0);
            h.assertTrue(menu.getCarried().isEmpty()&&stored(menu,iron)==63&&menu.grid.getItem(0).getCount()==3,"Round trip preserves network items and the earlier rejected stack");
            h.succeed();
        });
    }
}
