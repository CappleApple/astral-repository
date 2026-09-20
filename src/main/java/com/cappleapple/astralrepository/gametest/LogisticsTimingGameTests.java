package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Configuration overrides are confined to one synchronous server callback and restored in finally. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class LogisticsTimingGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void managedCraftDelayChangesButPhysicalFurnaceAndEscrowRemainReal(GameTestHelper h) {
        BlockPos chestPos=new BlockPos(3,2,3),tablePos=chestPos.east(),furnacePos=chestPos.north();
        h.setBlock(chestPos,Blocks.CHEST);h.setBlock(tablePos,Blocks.CRAFTING_TABLE);h.setBlock(furnacePos,Blocks.FURNACE);
        Container chest=(Container)h.getBlockEntity(chestPos),furnace=(Container)h.getBlockEntity(furnacePos);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"timing-craft"));
        boolean automatic=AstralConfig.instantAutomaticLogistics.get(),interactions=AstralConfig.instantPlayerInteractions.get();
        CraftingService service=null;
        try {
            AstralConfig.instantAutomaticLogistics.set(false);AstralConfig.instantPlayerInteractions.set(true);
            h.assertTrue(LogisticsTiming.playerDelayTicks()==0&&LogisticsTiming.visualTicks(20)==20,"Default player timing does not leak into automatic effects");
            h.assertTrue(LogisticsTiming.playerInteraction(()->LogisticsTiming.visualTicks(20))==20,"Instant inventory operations retain readable player animations");
            h.assertTrue(LogisticsTiming.playerInteraction(()->LogisticsTiming.automaticOperation(()->LogisticsTiming.visualTicks(20)))==20,"Automatic work submitted inside a player interaction retains its own timing");
            try { LogisticsTiming.playerInteraction((Runnable)()->{throw new IllegalStateException("test scope cleanup");}); } catch(IllegalStateException expected) {}
            h.assertTrue(LogisticsTiming.visualTicks(20)==20,"Exceptional player operations restore the automatic visual scope");
            AstralConfig.instantPlayerInteractions.set(false);
            h.assertTrue(LogisticsTiming.playerDelayTicks()==20&&LogisticsTiming.playerInteraction(()->LogisticsTiming.visualTicks(20))==20,"Noninstant player access retains its exact twenty-tick timing");

            ItemStack output=new ItemStack(Items.IRON_TRAPDOOR);
            var table=new CraftingGameTests.Fixture(h,chestPos,chest,List.of(tablePos),output);
            chest.setItem(0,new ItemStack(Items.IRON_INGOT,4));service=new CraftingService(table);
            h.assertTrue(service.request(player,output,1).accepted(),"A real table recipe can be planned from four chest ingots");
            CraftingGameTests.awaitPlanning(service);
            h.assertTrue(count(chest,Items.IRON_INGOT)==0,"Accepted crafting moves ingredients into scheduler escrow");
            service.tick();service.tick();
            h.assertTrue(service.activeJobs()==1&&count(chest,Items.IRON_TRAPDOOR)==0,"Normal mode does not finish after its first processor poll");
            for(int tick=0;tick<19;tick++)service.tick();
            h.assertTrue(service.activeJobs()==1&&table.tableAnimations==1&&count(chest,Items.IRON_TRAPDOOR)==0,"Ingredients arrive after twenty travel ticks before managed crafting begins");
            for(int tick=0;tick<36;tick++)service.tick();
            h.assertTrue(service.activeJobs()==0&&count(chest,Items.IRON_TRAPDOOR)==1&&count(chest,Items.IRON_INGOT)==0,"Normal table mode completes after all thirty-six managed work ticks without duplicating inputs");

            chest.clearContent();chest.setItem(0,new ItemStack(Items.IRON_INGOT,4));
            AstralConfig.instantAutomaticLogistics.set(true);service=new CraftingService(table);
            h.assertTrue(service.request(player,output,1).accepted(),"Instant mode plans the same authoritative recipe");CraftingGameTests.awaitPlanning(service);
            service.tick();
            h.assertTrue(service.activeJobs()==1&&count(chest,Items.IRON_TRAPDOOR)==0,"Instant work still reserves and starts a real processor");
            service.tick();
            h.assertTrue(service.activeJobs()==0&&count(chest,Items.IRON_TRAPDOOR)==1&&count(chest,Items.IRON_INGOT)==0,"Instant mode returns exactly one result on its first poll");
            h.assertTrue(LogisticsTiming.visualTicks(36)==1,"Instant automatic effects use a one-tick flash");

            chest.clearContent();chest.setItem(0,new ItemStack(Items.RAW_IRON));chest.setItem(1,new ItemStack(Items.COAL));
            var physical=new CraftingGameTests.Fixture(h,chestPos,chest,List.of(furnacePos),new ItemStack(Items.IRON_INGOT));
            service=new CraftingService(physical);
            var request=service.request(player,new ItemStack(Items.IRON_INGOT),1);
            h.assertTrue(request.accepted(),"The same instant setting can submit work to a physical furnace");CraftingGameTests.awaitPlanning(service);
            service.tick();service.tick();
            h.assertTrue(service.activeJobs()==1&&furnace.getItem(0).is(Items.RAW_IRON)&&furnace.getItem(2).isEmpty()&&count(chest,Items.IRON_INGOT)==0,"Instant logistics cannot synthesize a furnace output before vanilla processing");
            h.assertTrue(service.cancel(request.jobId()),"Unprocessed physical work remains cancellable");
            h.assertTrue(count(chest,Items.RAW_IRON)==1&&count(chest,Items.COAL)==1&&furnace.getItem(0).isEmpty(),"Cancellation returns only the still-unconsumed physical ingredients and fuel");
        } finally {
            if(service!=null)service.cancelAll();
            AstralConfig.instantAutomaticLogistics.set(automatic);AstralConfig.instantPlayerInteractions.set(interactions);
        }
        h.succeed();
    }

    private static long count(Container container,net.minecraft.world.item.Item item) {
        long total=0;for(int slot=0;slot<container.getContainerSize();slot++){ItemStack stack=container.getItem(slot);if(stack.is(item))total+=stack.getCount();}return total;
    }
    private LogisticsTimingGameTests() {}
}
