package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralConfig;
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
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NexusManualCraftingGameTests {
    private record Fixture(NexusMenu menu,FakePlayer player,Container chest) {}
    private static Fixture fixture(GameTestHelper h,int id){
        BlockPos nexus=new BlockPos(5,2,5),chest=nexus.west();h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(chest,Blocks.CHEST);
        FakePlayer player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"manual-nexus"));BlockPos absolute=h.absolutePos(nexus);player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        NexusMenu menu=new NexusMenu(id,player.getInventory(),GlobalPos.of(h.getLevel().dimension(),absolute),false);player.containerMenu=menu;
        return new Fixture(menu,player,(Container)h.getBlockEntity(chest));
    }
    private static long stored(Fixture f,ItemStack stack){return f.menu().network().snapshot().getOrDefault(new ItemKey(stack),0L);}
    private static long inventory(Fixture f,Item item){long result=0;for(int i=0;i<36;i++)if(f.player().getInventory().getItem(i).is(item))result+=f.player().getInventory().getItem(i).getCount();return result;}
    private static GameTestSequence await(GameTestHelper h,Fixture f,ItemStack stack,long count){return h.startSequence().thenWaitUntil(()->h.assertTrue(f.menu().network()!=null&&stored(f,stack)==count,"Actual chest resources are indexed"));}
    private static void clear(Fixture f){f.menu().action(new NetworkPackets.Action(f.menu().containerId,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY,0,0,""));}
    private static void delayed(Runnable action){boolean before=AstralConfig.instantPlayerInteractions.get();AstralConfig.instantPlayerInteractions.set(false);try{action.run();}finally{AstralConfig.instantPlayerInteractions.set(before);}}

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void manualCraftRefillsExactIngredientsAndShiftStopsAtOneOutputStack(GameTestHelper h){
        var f=fixture(h,91);ItemStack named=new ItemStack(Items.OAK_LOG);named.set(DataComponents.CUSTOM_NAME,Component.literal("Exact crafting input"));
        f.chest().setItem(0,named.copyWithCount(32));f.chest().setItem(1,new ItemStack(Items.OAK_LOG,10));
        await(h,f,named,32).thenExecute(()->{
            f.menu().grid.setItem(0,named.copy());f.menu().clicked(0,0,ClickType.PICKUP,f.player());
            h.assertTrue(f.menu().getCarried().is(Items.OAK_PLANKS)&&f.menu().getCarried().getCount()==4,"Normal result pickup crafts one actual recipe");
            h.assertTrue(ItemStack.isSameItemSameTags(f.menu().grid.getItem(0),named)&&stored(f,named)==31&&stored(f,new ItemStack(Items.OAK_LOG))==10,"Refill consumes the exact named component variant and leaves ordinary logs untouched");
            f.menu().clicked(10,0,ClickType.PICKUP,f.player());
            f.menu().clicked(0,0,ClickType.QUICK_MOVE,f.player());
            h.assertTrue(inventory(f,Items.OAK_PLANKS)==68,"One Shift click adds exactly64 outputs, despite space and ingredients for more");
            h.assertTrue(stored(f,named)==15&&f.menu().grid.getItem(0).getCount()==1,"Sixteen further real recipes consume sixteen refills and keep the next ingredient in the grid");
            clear(f);h.assertTrue(f.menu().grid.isEmpty()&&stored(f,named)==16&&inventory(f,Items.OAK_PLANKS)==68,"Clear grid returns ingredients only to storage and leaves crafted inventory outputs alone");
            clear(f);h.assertTrue(f.menu().error.isEmpty()&&stored(f,named)==16,"Clearing an already empty grid is silent and changes nothing");h.succeed();
        });
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void manualCakeCraftReturnsBucketsAndRefillsTheSameRecipe(GameTestHelper h){
        var f=fixture(h,92);for(int i=0;i<3;i++)f.chest().setItem(i,new ItemStack(Items.MILK_BUCKET));
        f.chest().setItem(3,new ItemStack(Items.SUGAR,2));f.chest().setItem(4,new ItemStack(Items.WHEAT,3));f.chest().setItem(5,new ItemStack(Items.EGG));
        await(h,f,new ItemStack(Items.MILK_BUCKET),3).thenExecute(()->{
            for(int i=0;i<3;i++)f.menu().grid.setItem(i,new ItemStack(Items.MILK_BUCKET));
            f.menu().grid.setItem(3,new ItemStack(Items.SUGAR));f.menu().grid.setItem(4,new ItemStack(Items.EGG));f.menu().grid.setItem(5,new ItemStack(Items.SUGAR));
            for(int i=6;i<9;i++)f.menu().grid.setItem(i,new ItemStack(Items.WHEAT));
            h.assertTrue(f.menu().slots.get(0).getItem().is(Items.CAKE),"The loaded vanilla cake recipe matches the grid");f.menu().clicked(0,0,ClickType.PICKUP,f.player());
            h.assertTrue(f.menu().getCarried().is(Items.CAKE)&&f.menu().getCarried().getCount()==1,"Exactly one vanilla cake reaches the cursor");
            h.assertTrue(stored(f,new ItemStack(Items.BUCKET))==3&&stored(f,new ItemStack(Items.MILK_BUCKET))==0,"The three real container remainders enter storage and replacement milk buckets leave it");
            for(int i=0;i<3;i++)h.assertTrue(f.menu().grid.getItem(i).is(Items.MILK_BUCKET),"The original milk-bucket slots are refilled");
            h.assertTrue(f.menu().slots.get(0).getItem().is(Items.CAKE),"Refill retains the same actual recipe");h.succeed();
        });
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void clearGridLeavesEveryRejectedIngredientInItsOriginalSlot(GameTestHelper h){
        var f=fixture(h,93);ItemStack named=new ItemStack(Items.IRON_INGOT);named.set(DataComponents.CUSTOM_NAME,Component.literal("Partial grid return"));
        for(int i=0;i<f.chest().getContainerSize();i++)f.chest().setItem(i,new ItemStack(Items.STONE,64));f.chest().setItem(0,named.copyWithCount(63));
        await(h,f,named,63).thenExecute(()->{
            f.menu().grid.setItem(4,named.copyWithCount(10));clear(f);
            h.assertTrue(stored(f,named)==64&&f.menu().grid.getItem(4).getCount()==9&&ItemStack.isSameItemSameTags(f.menu().grid.getItem(4),named),"Only the one accepted item leaves its exact grid slot");
            clear(f);h.assertTrue(stored(f,named)==64&&f.menu().grid.getItem(4).getCount()==9&&!f.menu().error.isEmpty(),"Repeated full-storage clears preserve all nine rejected items and report the capacity failure");
            h.assertTrue(f.menu().getCarried().isEmpty()&&inventory(f,Items.IRON_INGOT)==0,"Clear does not use the cursor or player inventory as an implicit destination");h.succeed();
        });
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void deferredMenuTransferWaitsAndCancelsChangedOrSpectatorState(GameTestHelper h){
        var f=fixture(h,94);ItemStack iron=new ItemStack(Items.IRON_INGOT);f.chest().setItem(0,iron.copyWithCount(16));
        await(h,f,iron,16).thenExecute(()->{
            Runnable pickup=()->f.menu().action(new NetworkPackets.Action(94,NetworkPackets.PICKUP,iron,0,0,""));
            delayed(pickup);h.assertTrue(stored(f,iron)==16&&f.menu().getCarried().isEmpty(),"The delayed request commits no extraction before its timer");
            f.menu().setCarried(new ItemStack(Items.DIAMOND));f.menu().broadcastChanges();f.menu().setCarried(ItemStack.EMPTY);
            h.onEachTick(f.menu()::broadcastChanges);
            h.runAfterDelay(21,()->{
                h.assertTrue(stored(f,iron)==16&&f.menu().getCarried().isEmpty(),"Changing the cursor cancels the queued transfer rather than replaying it");
                delayed(pickup);h.runAfterDelay(19,()->h.assertTrue(stored(f,iron)==16&&f.menu().getCarried().isEmpty(),"The unchanged transaction is still waiting before tick20"));
                h.runAfterDelay(21,()->{
                    h.assertTrue(stored(f,iron)==0&&f.menu().getCarried().getCount()==16,"The unchanged transaction commits once after20 ticks");
                    delayed(()->f.menu().action(new NetworkPackets.Action(94,NetworkPackets.DEPOSIT,ItemStack.EMPTY,0,0,"")));
                    f.player().setGameMode(GameType.SPECTATOR);f.menu().broadcastChanges();
                    h.runAfterDelay(21,()->{
                        h.assertTrue(stored(f,iron)==0&&f.menu().getCarried().getCount()==16,"Becoming a spectator cancels a queued mutation without dropping or depositing its cursor");
                        f.menu().action(new NetworkPackets.Action(94,NetworkPackets.DEPOSIT,ItemStack.EMPTY,0,0,""));
                        h.assertTrue(stored(f,iron)==0,"Spectator custom packets cannot bypass vanilla's mutation restriction");h.succeed();
                    });
                });
            });
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void delayedShiftCraftCommitsOneCompleteOutputStackAfterTheTimer(GameTestHelper h){
        var f=fixture(h,95);ItemStack log=new ItemStack(Items.OAK_LOG);f.chest().setItem(0,log.copyWithCount(15));
        await(h,f,log,15).thenExecute(()->{
            f.menu().grid.setItem(0,log.copy());delayed(()->f.menu().clicked(0,0,ClickType.QUICK_MOVE,f.player()));
            h.assertTrue(inventory(f,Items.OAK_PLANKS)==0&&stored(f,log)==15&&f.menu().grid.getItem(0).getCount()==1,"Delayed Shift craft consumes no ingredient or recipe before commit");
            h.runAfterDelay(19,()->{f.menu().broadcastChanges();h.assertTrue(inventory(f,Items.OAK_PLANKS)==0&&stored(f,log)==15,"The entire batch still waits before tick20");});
            h.runAfterDelay(21,()->{
                delayed(f.menu()::broadcastChanges);
                h.assertTrue(inventory(f,Items.OAK_PLANKS)==64&&stored(f,log)==0&&f.menu().grid.isEmpty(),"Timed commit refills between sixteen real recipes and produces exactly64 outputs");
                f.menu().broadcastChanges();h.assertTrue(inventory(f,Items.OAK_PLANKS)==64,"Completed delayed batch cannot replay");h.succeed();
            });
        });
    }    private NexusManualCraftingGameTests() {}
}