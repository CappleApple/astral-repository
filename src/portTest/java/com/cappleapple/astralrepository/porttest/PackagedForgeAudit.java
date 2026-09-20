package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

/** Separate test-only mod for an installed Forge server and the reobfuscated release JAR. */
@Mod("astral_port_test")
public final class PackagedForgeAudit {
    private static final BlockPos NEXUS=new BlockPos(0,100,0),STORAGE=new BlockPos(2,100,0),CHEST=new BlockPos(-1,100,0),SOURCE=new BlockPos(8,100,0),TARGET=new BlockPos(12,100,0);
    private static final Path STATE=Path.of("packaged-autocraft-state.txt"),RESULT=Path.of("packaged-autocraft-result.txt");
    private MinecraftServer server;
    private FakePlayer player;
    private NexusMenu menu;
    private int ticks,craftPhase;
    private long craftingLogs;
    private boolean finished,restart;
    public PackagedForgeAudit(){MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void start(ServerStartedEvent event){
        if(!Boolean.getBoolean("astral_repository.packagedAudit"))return;
        server=event.getServer();restart=Files.exists(STATE);
        try {
            var level=server.overworld();level.getChunkAt(NEXUS);level.getChunkAt(CHEST);
            player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"packaged-audit"));player.setPos(.5,101,3.5);
            com.mojang.logging.LogUtils.getLogger().info("FORGE_PACKAGED_SOURCE {}",AstralContent.class.getProtectionDomain().getCodeSource().getLocation());
            if(restart)return;
            for(int x=-2;x<24;x++)for(int z=-1;z<10;z++)level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());
            int x=0;for(var entry:AstralContent.BLOCKS.getEntries())place(level,entry.get(),new BlockPos(x++*2,100,8));
            check(x==10,"Ten registered block items placed");
            place(level,AstralContent.STORAGE_NEXUS.get(),NEXUS);place(level,AstralContent.SEED_STORAGE_CRYSTAL.get(),STORAGE);
            var storage=(CrystalNodeBlockEntity)level.getBlockEntity(STORAGE);storage.upgradeStorage(2);storage.upgradeRange();
            var named=new ItemStack(Items.DIAMOND,512);named.setHoverName(net.minecraft.network.chat.Component.literal("Packaged identity"));storage.inventory().insertItem(0,named,false);
            storage.tank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1500),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);storage.energy().receiveEnergy(2200,false);
            NetworkAnchor anchor=storage;check(anchor.getLevel()==level&&anchor.getBlockPos().equals(STORAGE)&&!anchor.isRemoved(),"Reobfuscated NetworkAnchor inherited bridges");anchor.setChanged();
            var link=NetworkManager.get(server).toggleLink(AnchorAddress.crystal(level,NEXUS),AnchorAddress.crystal(level,STORAGE));check(link.success(),"Explicit storage link: "+link);
            level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);chest.setItem(0,new ItemStack(Items.IRON_INGOT,32));chest.setItem(1,new ItemStack(Items.OAK_LOG,2));NetworkManager.changed(level,CHEST);
            level.setBlockAndUpdate(SOURCE,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(TARGET,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)level.getBlockEntity(SOURCE)).setItem(0,new ItemStack(Items.GOLD_INGOT,32));
            var surface=RuneSurfaces.getOrCreate(level,SOURCE,Direction.SOUTH);var rune=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);rune.filter().setTarget(12);rune.changed();
            check(surface.toggleTarget(rune.id(),GlobalPos.of(level.dimension(),TARGET),Direction.WEST).assigned(),"Rune target assignment");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);menu=new NexusMenu(197,player.getInventory(),GlobalPos.of(level.dimension(),NEXUS),false);player.containerMenu=menu;
        }catch(Throwable failure){fail(failure);}
    }
    private void tick(TickEvent.ServerTickEvent event){
        if(server==null||finished||event.phase!=TickEvent.Phase.END)return;
        try {
            if(++ticks>600)throw new AssertionError("Packaged gameplay timed out");
            var level=server.overworld();var storage=(CrystalNodeBlockEntity)level.getBlockEntity(STORAGE);
            if(restart){
                if(ticks<80)return; // Let loaded providers rebuild the network index before querying it.
                check(storage!=null&&storage.inventory().getStackInSlot(0).getCount()==512&&storage.inventory().getStackInSlot(0).getHoverName().getString().equals("Packaged identity"),"Disk restart retains quantity and custom identity");
                check(storage.storageTier()==2&&storage.longRange()&&storage.tank().getFluidAmount()==1500&&storage.energy().getEnergyStored()==2200,"Disk restart retains upgrades, fluid and energy");
                check(((ChestBlockEntity)level.getBlockEntity(CHEST)).getItem(0).getCount()==32,"Disk restart retains Nexus transactions");
                check(((ChestBlockEntity)level.getBlockEntity(TARGET)).getItem(0).getCount()==12&&((ChestBlockEntity)level.getBlockEntity(SOURCE)).getItem(0).getCount()==20,"Disk restart retains conserved rune transfer");
                var surface=RuneSurfaces.get(level,SOURCE,Direction.SOUTH);check(surface!=null&&surface.layers().size()==1&&surface.layers().get(0).transferredItems()==12,"Disk restart retains rune settings and counters");
                var restored=NetworkManager.get(server).networkAt(GlobalPos.of(level.dimension(),NEXUS));if(restored==null)return;
                check(restored.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_PLANKS)),0L)==8,"Disk restart retains autocrafted output");
                check(restored.exposedProducts().stream().anyMatch(item->item.is(Items.OAK_PLANKS)),"Disk restart retains taught bookshelf recipe");
                finish("PASS: installed Forge 47.4.10; actual release JAR; ten real block-item placements; reobfuscated NetworkAnchor dispatch; linked Nexus withdrawal/deposit and vanilla crafting/refill; real scheduled rune transfer; Recipe Tome inscription, bookshelf discovery and completed eight-plank autocraft with exact ingredient conservation; second-process disk persistence of stock, NBT identity, upgrades, fluids, energy and runes.");return;
            }
            if(craftPhase==1){
                menu.broadcastChanges();
                if(menu.network()==null||menu.network().exposedProducts().stream().noneMatch(item->item.is(Items.OAK_PLANKS)))return;
                craftingLogs=menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_LOG)),0L);
                check(craftingLogs>=2,"Packaged autocraft has real log stock");
                menu.action(new NetworkPackets.Action(197,NetworkPackets.CRAFT,new ItemStack(Items.OAK_PLANKS),8,0,""));
                craftPhase=2;return;
            }
            if(craftPhase==2){
                menu.broadcastChanges();
                if(menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_PLANKS)),0L)!=8)return;
                check(menu.network().crafting().statuses().stream().anyMatch(job->job.state().equals("COMPLETE")),"Packaged autocraft completed a real job");
                check(menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_LOG)),0L)==craftingLogs-2,"Packaged autocraft consumes exactly two logs for eight planks");
                Files.writeString(STATE,"First process passed autocrafting; awaiting restart.\n");finish("FIRST_PASS: packaged gameplay and Recipe Tome/bookshelf autocraft complete; restart required for disk persistence.");return;
            }
            if(menu.network()==null||menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)!=32||((ChestBlockEntity)level.getBlockEntity(TARGET)).getItem(0).getCount()!=12)return;
            var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);
            menu.action(new NetworkPackets.Action(197,NetworkPackets.PICKUP,new ItemStack(Items.IRON_INGOT),0,0,""));check(menu.getCarried().getCount()==32&&chest.getItem(0).isEmpty(),"Packaged Nexus withdrawal");
            menu.action(new NetworkPackets.Action(197,NetworkPackets.DEPOSIT,ItemStack.EMPTY,0,0,""));check(menu.getCarried().isEmpty()&&chest.getItem(0).getCount()==32,"Packaged Nexus deposit");
            menu.grid.setItem(0,new ItemStack(Items.OAK_LOG));menu.clicked(0,0,ClickType.PICKUP,player);check(menu.getCarried().is(Items.OAK_PLANKS)&&menu.getCarried().getCount()==4&&menu.grid.getItem(0).is(Items.OAK_LOG),"Packaged vanilla crafting and refill");
            var rune=RuneSurfaces.get(level,SOURCE,Direction.SOUTH).layers().get(0);rune.setEnabled(false);check(rune.transferredItems()==12,"Packaged rune counter");
            menu.setCarried(ItemStack.EMPTY);menu.grid.clearContent();
            var tome=new ItemStack(AstralContent.RECIPE_TOME.get());player.setItemInHand(InteractionHand.MAIN_HAND,tome);
            var tomeMenu=new com.cappleapple.astralrepository.menu.RecipeTomeMenu(198,player.getInventory(),InteractionHand.MAIN_HAND);
            check(tomeMenu.inscribe(new net.minecraft.resources.ResourceLocation("minecraft:oak_planks")),"Packaged Recipe Tome inscription");
            check(RecipeTomeItem.product(tome,level.registryAccess()).is(Items.OAK_PLANKS),"Packaged Recipe Tome output");
            var table=NEXUS.north(2);var shelf=table.east();
            level.setBlockAndUpdate(table,Blocks.CRAFTING_TABLE.defaultBlockState());level.setBlockAndUpdate(shelf,Blocks.CHISELED_BOOKSHELF.defaultBlockState());
            ((net.minecraft.world.Container)level.getBlockEntity(shelf)).setItem(0,tome.copy());chest.setItem(2,new ItemStack(Items.OAK_LOG,4));
            NetworkManager.changed(level,table);NetworkManager.changed(level,shelf);NetworkManager.changed(level,CHEST);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            craftPhase=1;
        }catch(Throwable failure){fail(failure);}
    }
    private void place(ServerLevel level,Block block,BlockPos pos){
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());var stack=new ItemStack(block);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false);
        check(player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,hit).consumesAction()&&level.getBlockState(pos).is(block),"Packaged BlockItem placement "+block);
    }
    private void finish(String message)throws Exception {finished=true;Files.writeString(RESULT,message+"\n");com.mojang.logging.LogUtils.getLogger().info("FORGE_PACKAGED_AUDIT {}",message);server.saveEverything(false,true,true);server.halt(false);}
    private void fail(Throwable failure){finished=true;com.mojang.logging.LogUtils.getLogger().error("FORGE_PACKAGED_AUDIT_FAILED",failure);try{Files.writeString(RESULT,"FAIL: "+failure+"\n");}catch(Exception ignored){}if(server!=null)server.halt(false);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
