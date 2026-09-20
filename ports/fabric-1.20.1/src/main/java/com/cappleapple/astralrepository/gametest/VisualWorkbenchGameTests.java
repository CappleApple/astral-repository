package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

/** Uses the installed mod's replacement block, persistent container, menu and NBT serializer. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class VisualWorkbenchGameTests {
    private static final ResourceLocation TABLE=new ResourceLocation("visualworkbench:minecraft/crafting_table");
    private static final ItemKey IRON=new ItemKey(new ItemStack(Items.IRON_INGOT));
    private static final ItemKey OUTPUT=new ItemKey(new ItemStack(Items.IRON_TRAPDOOR));
    private record Fixture(GlobalPos origin,GlobalPos table,Container chest,BlockEntity persistentTable,
                           CompoundTag savedGrid,FakePlayer player,ItemKey manualIron) {
        AstralNetwork network(GameTestHelper h){return NetworkManager.get(h.getLevel().getServer()).networkAt(origin);}
    }
    private static boolean available(GameTestHelper h) {
        if(ModList.get().isLoaded("visualworkbench"))return true;
        h.assertTrue(!Boolean.getBoolean("astral_repository.compatTest"),"Compatibility gate requires Visual Workbench and Puzzles Lib");
        h.succeed();return false;
    }
    private static Fixture fixture(GameTestHelper h,boolean fullChest) {
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(TABLE),"Installed Visual Workbench replacement is registered");
        BlockPos nexus=new BlockPos(5,2,5),table=nexus.east(),chest=nexus.west(),shelf=nexus.north();
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(table,BuiltInRegistries.BLOCK.get(TABLE));
        h.setBlock(chest,Blocks.CHEST);h.setBlock(shelf,Blocks.CHISELED_BOOKSHELF);
        BlockPos absolute=h.absolutePos(table);BlockEntity original=h.getBlockEntity(table);
        h.assertTrue(VisualWorkbenchCompatibility.isPersistentTable(h.getLevel(),absolute)&&original instanceof Container,"Real persistent table identified without loading optional classes in production");
        Container grid=(Container)original;
        ItemStack pattern=new ItemStack(Items.IRON_INGOT,7);pattern.set(DataComponents.CUSTOM_NAME,Component.literal("Player's saved workbench pattern"));
        for(int slot:new int[]{0,1,3,4})grid.setItem(slot,pattern.copy());
        FakePlayer player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"workbench-test"));
        player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+1.5);
        var manual=((MenuProvider)original).createMenu(87,player.getInventory(),player);
        h.assertTrue(manual!=null&&manual.slots.get(0).getItem().is(Items.IRON_TRAPDOOR),"Visual Workbench's actual menu computes its persistent preview result");
        manual.removed(player);
        CompoundTag full=original.saveWithFullMetadata(h.getLevel().registryAccess());
        BlockEntity restored=BlockEntity.loadStatic(absolute,original.getBlockState(),full,h.getLevel().registryAccess());
        h.assertTrue(restored!=null&&restored.getType()==original.getType(),"Actual registered block-entity loader restores the persistent table");
        h.getLevel().removeBlockEntity(absolute);h.getLevel().setBlockEntity(restored);
        CompoundTag savedGrid=restored.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(savedGrid.equals(original.saveWithoutMetadata(h.getLevel().registryAccess())),"Saved player inputs, components and preview survive the NBT reload");
        Container storage=(Container)h.getBlockEntity(chest);
        if(fullChest)for(int i=0;i<storage.getContainerSize();i++)storage.setItem(i,new ItemStack(Items.STONE,64));
        else storage.setItem(0,new ItemStack(Items.IRON_INGOT,4));
        ItemStack tome=new ItemStack(AstralContent.RECIPE_TOME.get());CompoundTag taught=new CompoundTag();
        taught.put("Output",OUTPUT.sample().save(h.getLevel().registryAccess()));taught.putString("OutputId","minecraft:iron_trapdoor");tome.set(DataComponents.CUSTOM_DATA,CustomData.of(taught));
        ((ChiseledBookShelfBlockEntity)h.getBlockEntity(shelf)).setItem(0,tome);
        return new Fixture(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(nexus)),GlobalPos.of(h.getLevel().dimension(),absolute),storage,restored,savedGrid,player,new ItemKey(pattern));
    }
    private static GameTestSequence awaitWorkshop(GameTestHelper h,Fixture f) {
        return h.startSequence().thenWaitUntil(()->{
            var network=f.network(h);
            h.assertTrue(network!=null&&network.workstations().contains(f.table())&&network.exposedProducts().stream().anyMatch(s->new ItemKey(s).equals(OUTPUT)),"Real Nexus has discovered the persistent table and taught output");
            h.assertTrue(!network.snapshot().containsKey(f.manualIron())&&!network.snapshot().containsKey(OUTPUT),"Player's saved grid and preview are not network inventory");
        });
    }
    private static void assertPreserved(GameTestHelper h,Fixture f) {
        h.assertTrue(h.getLevel().getBlockEntity(f.table().pos())==f.persistentTable(),"Autocrafting does not replace the persistent table");
        h.assertTrue(f.savedGrid().equals(f.persistentTable().saveWithoutMetadata(h.getLevel().registryAccess())),"Every saved input slot, component and preview remains untouched");
    }
    private static long count(Container container,ItemKey key) {
        long result=0;for(int i=0;i<container.getContainerSize();i++){ItemStack item=container.getItem(i);if(!item.isEmpty()&&new ItemKey(item).equals(key))result+=item.getCount();}return result;
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=800,batch="astral_visual_workbench")
    public static void persistentTableCraftsFromNetworkEscrowAndCancellationPreservesPlayerGrid(GameTestHelper h) {
        if(!available(h))return;Fixture f=fixture(h,false);UUID[] cancelled={null},completed={null};CraftingService[] submitted={null};
        h.onEachTick(()->assertPreserved(h,f));
        awaitWorkshop(h,f).thenWaitUntil(()->h.assertTrue(f.network(h).snapshot().getOrDefault(IRON,0L)==4,"Crafting ingredients are indexed from the actual chest"))
                .thenExecute(()->{
                    var request=f.network(h).crafting().request(f.player(),OUTPUT.sample(),1);
                    h.assertTrue(request.accepted(),"Converted table accepts actual Nexus craft: "+request.message());cancelled[0]=request.jobId();
                }).thenWaitUntil(()->h.assertTrue(CraftingService.isProcessorReserved(f.table()),"Visual Workbench is actually running the first operation"))
                .thenExecute(()->{
                    h.assertTrue(f.network(h).crafting().cancel(cancelled[0]),"Active craft can be cancelled");
                    h.assertTrue(!f.network(h).crafting().cancel(cancelled[0]),"Repeated cancellation cannot duplicate ingredients");
                    h.assertTrue(count(f.chest(),IRON)==4&&count(f.chest(),OUTPUT)==0,"Only scheduler-owned ingredients return to the chest");
                    assertPreserved(h,f);
                    var request=f.network(h).crafting().request(f.player(),OUTPUT.sample(),1);
                    h.assertTrue(request.accepted(),"A new actual craft starts after cancellation: "+request.message());completed[0]=request.jobId();submitted[0]=f.network(h).crafting();
                }).thenWaitUntil(()->{
                    var network=f.network(h);var service=submitted[0];
                    var status=service.statuses().stream().filter(s->s.id().equals(completed[0])).findFirst().orElse(null);
                    if(network==null||network.crafting()!=service){h.fail("Fixture network rebuilt during the second craft; submitted="+status+", chestIron="+count(f.chest(),IRON)+", chestOutput="+count(f.chest(),OUTPUT));return;}
                    if(status!=null&&(status.state().equals("FAILED")||status.state().equals("CANCELLED"))){h.fail("Second workbench craft terminated: "+status+", chestIron="+count(f.chest(),IRON)+", chestOutput="+count(f.chest(),OUTPUT));return;}
                    h.assertTrue(status!=null&&status.state().equals("COMPLETE")&&service.activeJobs()==0,"Second workbench craft completion: "+status+", tableReserved="+CraftingService.isProcessorReserved(f.table()));
                    h.assertTrue(count(f.chest(),OUTPUT)==1,"Completed workbench craft returns one trapdoor: chest="+count(f.chest(),OUTPUT)+", indexed="+network.snapshot().getOrDefault(OUTPUT,0L)+", iron="+count(f.chest(),IRON)+", network="+network.status());
                    h.assertTrue(count(f.chest(),IRON)==0,"Exactly four chest ingots were consumed");
                    h.assertTrue(!CraftingService.isProcessorReserved(f.table()),"Persistent table reservation is released");
                    assertPreserved(h,f);
                }).thenExecute(()->AstralRepository.LOGGER.info("ASTRAL_COMPAT: Visual Workbench {} persisted grid, cancellation and actual Nexus craft passed",ModList.get().getModContainerById("visualworkbench").orElseThrow().getModInfo().getVersion()))
                .thenSucceed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600,batch="astral_visual_workbench")
    public static void savedWorkbenchInputsCannotBeWithdrawnOrFilledByNetworkStorage(GameTestHelper h) {
        if(!available(h))return;Fixture f=fixture(h,true);
        awaitWorkshop(h,f).thenWaitUntil(()->h.assertTrue(f.network(h).snapshot().getOrDefault(new ItemKey(new ItemStack(Items.STONE)),0L)==(long)f.chest().getContainerSize()*64,"The full physical chest is indexed before testing rejected deposits"))
                .thenExecute(()->{
            for(Direction side:Direction.values())h.assertTrue(CompatibilityRegistry.discoverStorage(h.getLevel(),f.table().pos(),side).isEmpty(),"Persistent grid is excluded from sided storage discovery");
            h.assertTrue(CompatibilityRegistry.discoverStorage(h.getLevel(),f.table().pos(),null).isEmpty(),"Persistent grid is excluded from unsided storage discovery");
            var network=f.network(h);
            h.assertTrue(network.extract(f.manualIron(),28).isEmpty(),"Nexus cannot take ingredients out of a player's unfinished recipe");
            ItemStack remainder=network.insert(new ItemStack(Items.DIAMOND,12));
            h.assertTrue(remainder.getCount()==12&&remainder.is(Items.DIAMOND),"Full network storage does not spill deposits into the persistent crafting grid");
            var request=network.crafting().request(f.player(),OUTPUT.sample(),1);
            h.assertTrue(request.accepted(),"Request enters calculation queue");CraftingGameTests.awaitPlanning(network.crafting());
            h.assertTrue(network.crafting().statuses().stream().anyMatch(j->j.id().equals(request.jobId())&&j.state().equals("MISSING")),"A saved manual pattern is not a source of autocrafting ingredients");network.crafting().cancelAll();
            assertPreserved(h,f);
        }).thenSucceed();
    }
}
