package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.ItemHandlerStorageProvider;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.*;
import net.minecraftforge.event.tick.ServerTickEvent;

/** Opt-in, excluded from the distributable JAR. Two independent JVMs exercise real world persistence. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID,value=Dist.DEDICATED_SERVER)
public final class DedicatedPersistenceSmoke {
    private static final String MODE=System.getProperty("astral_repository.persistenceSmoke","");
    private static final Path OUT=Path.of(System.getProperty("astral_repository.persistenceOutput","../build/persistence-smoke-v3"));
    private static final String MANIFEST="astral-persistence-fixture-v3.txt";
    private static final String VERSION="astral_repository:independent-rune-persistence-v3";
    private static final BlockPos FIRST=new BlockPos(4,64,4),SECOND=new BlockPos(12,64,4),STORE=new BlockPos(8,64,8),CHEST=new BlockPos(10,64,8),PUSH_TARGET=new BlockPos(4,64,14),PULL_TARGET=new BlockPos(12,64,14);
    private static final List<ResourceLocation> GLYPHS=List.of(new ResourceLocation("astral_repository:push_rune"),new ResourceLocation("astral_repository:pull_rune"),new ResourceLocation("astral_repository:push_rune"),new ResourceLocation("astral_repository:pull_rune"));
    private static List<String> expectedLayerIds=List.of();
    private static boolean initialized,done;
    private static int ticks;
    private static Path world;
    private static String success;
    private static boolean active(){return MODE.equals("create")||MODE.equals("verify");}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!active())return;
        MinecraftServer server=event.getServer();
        try{
            check(server.isDedicatedServer(),"Persistence fixture requires a dedicated server");world=server.getWorldPath(LevelResource.ROOT);
            Files.createDirectories(OUT);
            if(MODE.equals("create"))check(!Files.exists(world.resolve(MANIFEST)),"Refusing to recreate an existing persistence fixture");
            else {List<String> lines=Files.readAllLines(world.resolve(MANIFEST));check(lines.size()==5&&lines.get(0).equals(VERSION),"Existing world lacks the completed first-run fixture marker");expectedLayerIds=List.copyOf(lines.subList(1,5));}
            ServerLevel level=server.overworld();
            // The dev fixture deliberately loads its one known chunk; production network discovery never does so.
            level.getChunkAt(FIRST);
            if(MODE.equals("create"))create(level);
            verifySavedState(level);initialized=true;
        }catch(Throwable failure){fail(server,failure);}
    }
    private static void create(ServerLevel level){
        level.setBlockAndUpdate(FIRST,AstralContent.STORAGE_NEXUS.get().defaultBlockState());
        level.setBlockAndUpdate(SECOND,AstralContent.STORAGE_NEXUS.get().defaultBlockState());
        level.setBlockAndUpdate(STORE,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());((CrystalNodeBlockEntity)level.getBlockEntity(STORE)).upgradeStorage(2);
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(PUSH_TARGET,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(PULL_TARGET,Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity)level.getBlockEntity(PUSH_TARGET)).setItem(0,new ItemStack(Items.DIAMOND,3));
        ((ChestBlockEntity)level.getBlockEntity(PULL_TARGET)).setItem(0,namedIron().copyWithCount(13));
        for(BlockPos pos:List.of(FIRST,SECOND,STORE))node(level,pos).setChannel(3);
        node(level,FIRST).setPriority(2);node(level,SECOND).setPriority(9);
        var inventory=node(level,STORE).inventory();
        check(inventory.insertItem(0,new ItemStack(Items.IRON_INGOT,12345),false).isEmpty(),"Fixture capacity rejected iron");
        ChestBlockEntity chest=chest(level);chest.setItem(0,new ItemStack(Items.GOLD_INGOT,37));chest.setItem(1,namedIron().copyWithCount(11));
        RuneSurface rune=RuneSurfaces.getOrCreate(level,CHEST,Direction.NORTH);rune.setChannel(3);rune.setPriority(7);rune.setDimensional(true);
        for(int i=0;i<GLYPHS.size();i++)check(rune.addLayer(GLYPHS.get(i),i%2==0?RuneLayer.Mode.PUSH:RuneLayer.Mode.PULL)!=null,"Fixture glyph layer rejected");
        RuneLayer push=rune.layers().get(0),pull=rune.layers().get(1);
        push.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);push.filter().setMinimum(7);push.filter().setTarget(19);push.setPriority(11);push.setEnabled(false);
        pull.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);pull.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",true,ItemStack.EMPTY);pull.filter().add(FilterRules.Kind.COMPONENTS,"minecraft:iron_ingot",false,namedIron());
        pull.filter().toggleAll();pull.filter().setMinimum(2);pull.filter().setTarget(11);pull.setPriority(-4);pull.setEnabled(false);
        check(rune.toggleTarget(push.id(),GlobalPos.of(level.dimension(),PUSH_TARGET),Direction.EAST).assigned(),"Push bare target assignment failed");
        check(rune.toggleTarget(pull.id(),GlobalPos.of(level.dimension(),PULL_TARGET),Direction.WEST).assigned(),"Pull bare target assignment failed");
        check(rune.toggleTarget(push.id(),GlobalPos.of(level.dimension(),FIRST),null).assigned(),"Push network target failed");
        check(rune.toggleTarget(pull.id(),GlobalPos.of(level.dimension(),SECOND),null).assigned(),"Pull network target failed");
        expectedLayerIds=rune.layers().stream().map(layer->layer.id().toString()).toList();rune.topologyChanged();
        var manager=NetworkManager.get(level.getServer());var a=AnchorAddress.crystal(level,FIRST);var b=AnchorAddress.crystal(level,SECOND);var storage=AnchorAddress.crystal(level,STORE);
        check(manager.toggleLink(a,b).linked(),"Nexus-to-Nexus link failed");check(manager.toggleLink(a,storage).linked(),"Storage crystal link failed");
        check(manager.toggleLink(a,rune.address()).linked(),"First Nexus-to-rune link failed");check(manager.toggleLink(b,rune.address()).linked(),"Second Nexus-to-rune link failed");
    }
    private static void verifySavedState(ServerLevel level){
        check(level.getBlockState(FIRST).is(AstralContent.STORAGE_NEXUS.get())&&level.getBlockState(SECOND).is(AstralContent.STORAGE_NEXUS.get()),"Both Nexus blocks must exist in the saved world");
        check(level.getBlockState(STORE).is(AstralContent.SEED_STORAGE_CRYSTAL.get()),"Saved storage crystal block is missing");
        for(BlockPos pos:List.of(FIRST,SECOND,STORE))check(node(level,pos).channel()==3,"Crystal dye channel changed at "+pos);
        check(node(level,FIRST).priority()==2&&node(level,SECOND).priority()==9,"Crystal priorities changed");
        check(node(level,STORE).storageTier()==2,"Moon upgrade stage changed");
        var inventory=node(level,STORE).inventory();
        var actual=new ItemHandlerStorageProvider("persistence-fixture",inventory,inventory,()->true).snapshot();
        check(actual.equals(Map.of(new ItemKey(new ItemStack(Items.IRON_INGOT)),12345L)),"Storage crystal iron count differs: "+actual);
        ChestBlockEntity chest=chest(level);Map<ItemKey,Long> chestItems=new HashMap<>();
        for(int slot=0;slot<chest.getContainerSize();slot++){ItemStack stack=chest.getItem(slot);if(!stack.isEmpty())chestItems.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);}
        check(chestItems.equals(Map.of(new ItemKey(new ItemStack(Items.GOLD_INGOT)),37L,new ItemKey(namedIron()),11L)),"Saved chest component-aware contents differ: "+chestItems);
        RuneSurface rune=RuneSurfaces.get(level,CHEST,Direction.NORTH);check(rune!=null&&!rune.isRemoved(),"Saved mounted NORTH rune is missing");
        check(rune.glyphs().equals(GLYPHS),"Stacked duplicate glyph layers changed");
        check(rune.channel()==3&&rune.priority()==7&&rune.dimensional()&&rune.enabled(),"Rune endpoint settings changed");
        check(rune.layers().stream().map(layer->layer.id().toString()).toList().equals(expectedLayerIds),"Per-layer stable UUIDs changed across real restart");
        RuneLayer push=rune.layers().get(0),pull=rune.layers().get(1);
        check(push.mode()==RuneLayer.Mode.PUSH&&pull.mode()==RuneLayer.Mode.PULL&&!push.enabled()&&!pull.enabled(),"Independent direction/enabled settings changed");
        check(push.target().equals(new RuneLayer.Target(GlobalPos.of(level.dimension(),PUSH_TARGET),Direction.EAST))&&pull.target().equals(new RuneLayer.Target(GlobalPos.of(level.dimension(),PULL_TARGET),Direction.WEST)),"Independent bare-container positions or clicked faces changed");
        check(push.targets().size()==2&&pull.targets().size()==2&&push.targets().get(1).face()==null&&pull.targets().get(1).position().pos().equals(SECOND),"Multiple container and network targets changed");
        check(push.priority()==11&&pull.priority()==-4&&push.filter().minimum()==7&&push.filter().target()==19&&pull.filter().minimum()==2&&pull.filter().target()==11,"Independent priority/reserve/stock settings changed");
        check(push.filter().matches(new ItemStack(Items.GOLD_INGOT))&&!push.filter().matches(namedIron()),"Push item filter changed");
        check(!pull.filter().all()&&pull.filter().entries().size()==3&&pull.filter().matches(namedIron())&&pull.filter().matches(new ItemStack(Items.IRON_INGOT))&&!pull.filter().matches(new ItemStack(Items.GOLD_INGOT)),"Pull component/include/exclude filter changed");
        for(int i=2;i<4;i++)check(rune.layers().get(i).target()==null&&rune.layers().get(i).filter().entries().isEmpty()&&rune.layers().get(i).enabled(),"Unassigned sibling inherited another layer's settings");
        check(((ChestBlockEntity)level.getBlockEntity(PUSH_TARGET)).getItem(0).is(Items.DIAMOND)&&((ChestBlockEntity)level.getBlockEntity(PUSH_TARGET)).getItem(0).getCount()==3,"Bare Push target contents changed while layer paused");
        ItemStack pullContents=((ChestBlockEntity)level.getBlockEntity(PULL_TARGET)).getItem(0);check(ItemStack.isSameItemSameTags(pullContents,namedIron())&&pullContents.getCount()==13,"Bare Pull target contents changed while layer paused");
        var data=RuneSavedData.get(level.getServer());var a=AnchorAddress.crystal(level,FIRST);var b=AnchorAddress.crystal(level,SECOND);var storage=AnchorAddress.crystal(level,STORE);
        check(data.links(a).equals(Set.of(b,storage,rune.address())),"First Nexus explicit links changed");
        check(data.links(b).equals(Set.of(a,rune.address()))&&data.links(rune.address()).equals(Set.of(a,b))&&data.links(storage).equals(Set.of(a)),"Reciprocal multi-links changed");
        check(data.links().size()==4,"Saved fixture must contain exactly four explicit links");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!active()||!initialized||done)return;MinecraftServer server=event.getServer();
        try{
            check(++ticks<=1200,"Network reconstruction timed out");ServerLevel level=server.overworld();var manager=NetworkManager.get(server);
            var first=manager.networkAt(AnchorAddress.crystal(level,FIRST));var second=manager.networkAt(AnchorAddress.crystal(level,SECOND));
            if(first==null||second==null||!first.snapshot().equals(expected()))return;
            check(first==second&&first.crafting()==second.crafting(),"Restarted Nexuses do not share one network and crafting coordinator");
            check(first.nodes().size()==4&&first.storageCount()==2,"Automatic coverage and rune views did not yield exactly two physical stores");
            check(manager.networkAt(AnchorAddress.rune(level,CHEST,Direction.NORTH))==first,"Saved rune did not rejoin the explicit component");
            verifySavedState(level);
            success="PASS: "+MODE+"; real dedicated world; 12345 iron + 37 gold + 11 named iron; four independent Push/Pull layers with stable UUIDs; multiple container/network targets, Moon upgrade, filters, stock settings and four reciprocal links; two Nexuses share one network with one storage crystal and one rune-mounted chest.";
            done=true;server.saveEverything(false,true,true);server.halt(false);
        }catch(Throwable failure){fail(server,failure);}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        if(!active()||success==null)return;
        try{
            if(MODE.equals("create"))Files.writeString(world.resolve(MANIFEST),VERSION+"\n"+String.join("\n",expectedLayerIds));
            Files.createDirectories(OUT);Files.writeString(OUT.resolve(MODE+"-result.txt"),success);AstralRepository.LOGGER.info(success);
        }catch(Throwable failure){success=null;writeFailure(failure);}
    }
    private static ChestBlockEntity chest(ServerLevel level){check(level.getBlockState(CHEST).is(Blocks.CHEST)&&level.getBlockEntity(CHEST) instanceof ChestBlockEntity,"Missing saved rune-mounted chest");return (ChestBlockEntity)level.getBlockEntity(CHEST);}
    private static CrystalNodeBlockEntity node(ServerLevel level,BlockPos pos){check(level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity,"Missing saved crystal block entity at "+pos);return (CrystalNodeBlockEntity)level.getBlockEntity(pos);}
    private static ItemStack namedIron(){ItemStack stack=new ItemStack(Items.IRON_INGOT);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Persisted component iron"));return stack;}
    private static Map<ItemKey,Long> expected(){return Map.of(new ItemKey(new ItemStack(Items.IRON_INGOT)),12345L,new ItemKey(new ItemStack(Items.GOLD_INGOT)),37L,new ItemKey(namedIron()),11L);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void fail(MinecraftServer server,Throwable failure){done=true;success=null;writeFailure(failure);server.halt(false);}
    private static void writeFailure(Throwable failure){AstralRepository.LOGGER.error("Dedicated persistence smoke failed",failure);try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(MODE+"-result.txt"),"FAIL: "+failure);}catch(Exception ignored){}}
}