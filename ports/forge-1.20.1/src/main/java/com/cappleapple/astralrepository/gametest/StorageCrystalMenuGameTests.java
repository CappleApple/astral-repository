package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class StorageCrystalMenuGameTests {
    private static void action(NexusMenu menu,int kind,ItemStack stack){menu.action(new NetworkPackets.Action(menu.containerId,kind,stack,0,0,""));}
    private static long count(CapacityInventory inventory,Item item){long count=0;for(int i=0;i<inventory.getSlots();i++)if(inventory.getStackInSlot(i).is(item))count+=inventory.getStackInSlot(i).getCount();return count;}
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void crystalMenuOwnsItsViewTransfersAndCraftingGrid(GameTestHelper h){
        BlockPos crystal=new BlockPos(4,2,4),nexus=crystal.east(2),chest=nexus.north();
        h.setBlock(crystal,AstralContent.SEED_STORAGE_CRYSTAL.get());h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(chest,Blocks.CHEST);
        var local=((CrystalNodeBlockEntity)h.getBlockEntity(crystal)).inventory();local.insertItem(0,new ItemStack(Items.OAK_LOG,3),false);
        var external=(Container)h.getBlockEntity(chest);external.setItem(0,new ItemStack(Items.DIAMOND,7));external.setItem(1,new ItemStack(Items.OAK_LOG,20));
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"crystal-local"));var pos=h.absolutePos(crystal);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        var menu=new NexusMenu(151,player.getInventory(),GlobalPos.of(h.getLevel().dimension(),pos),false);player.containerMenu=menu;
        h.startSequence().thenWaitUntil(()->h.assertTrue(menu.network()!=null&&menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.DIAMOND)),0L)==7,"Connected external chest is indexed"))
        .thenExecute(()->{
            menu.collectUpdate();h.assertTrue(menu.entries.size()==1&&menu.entries.get(0).stack().is(Items.OAK_LOG)&&menu.entries.get(0).count()==3&&!menu.entries.get(0).craftable()&&menu.jobs.isEmpty(),"Local view excludes all other network stock and crafting jobs");
            action(menu,NetworkPackets.QUICK_WITHDRAW,new ItemStack(Items.DIAMOND));h.assertTrue(player.getInventory().isEmpty()&&external.getItem(0).getCount()==7,"Forged withdrawal cannot reach remote stock");
            action(menu,NetworkPackets.PICKUP,new ItemStack(Items.OAK_LOG));h.assertTrue(menu.getCarried().getCount()==3&&count(local,Items.OAK_LOG)==0&&external.getItem(1).getCount()==20,"Pickup takes only local logs");
            action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY);h.assertTrue(menu.getCarried().isEmpty()&&count(local,Items.OAK_LOG)==3,"Cursor deposits return to this crystal");
            player.getInventory().setItem(9,new ItemStack(Items.GOLD_INGOT,5));menu.clicked(10,0,ClickType.QUICK_MOVE,player);h.assertTrue(count(local,Items.GOLD_INGOT)==5,"Shift deposit targets this crystal");
            menu.grid.setItem(0,new ItemStack(Items.OAK_LOG));menu.clicked(0,0,ClickType.PICKUP,player);h.assertTrue(menu.getCarried().is(Items.OAK_PLANKS)&&count(local,Items.OAK_LOG)==2&&external.getItem(1).getCount()==20,"Crafting refill uses local storage only");
            menu.setCarried(ItemStack.EMPTY);menu.clicked(0,0,ClickType.QUICK_MOVE,player);h.assertTrue(menu.grid.isEmpty()&&count(local,Items.OAK_LOG)==0&&external.getItem(1).getCount()==20,"Refill stops when this crystal runs out despite remote stock");
            menu.grid.setItem(4,new ItemStack(Items.IRON_INGOT,2));action(menu,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY);h.assertTrue(menu.grid.isEmpty()&&count(local,Items.IRON_INGOT)==2,"Clear returns items to this crystal");
            var nexusMenu=new NexusMenu(152,player.getInventory(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(nexus)),false);nexusMenu.collectUpdate();h.assertTrue(nexusMenu.entries.stream().anyMatch(e->e.stack().is(Items.DIAMOND)&&e.count()==7),"Nexus still shows connected chest contents");
            local.insertItem(local.getSlots()-1,new ItemStack(Items.STONE,Integer.MAX_VALUE),false);
            menu.setCarried(new ItemStack(Items.EMERALD,6));action(menu,NetworkPackets.DEPOSIT,ItemStack.EMPTY);h.assertTrue(menu.getCarried().getCount()==6&&count(local,Items.EMERALD)==0,"Full crystal never overflows deposits into another container");
            menu.grid.setItem(0,new ItemStack(Items.EMERALD));action(menu,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY);h.assertTrue(menu.grid.getItem(0).is(Items.EMERALD),"Full local storage retains rejected grid ingredients");
            h.succeed();
        });
    }
}
