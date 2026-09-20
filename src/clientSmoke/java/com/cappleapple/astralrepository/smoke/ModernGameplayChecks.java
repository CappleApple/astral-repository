package com.cappleapple.astralrepository.smoke;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.*;
import com.cappleapple.astralrepository.network.*;
import com.cappleapple.astralrepository.platform.network.PacketDistributor;
import com.mojang.logging.LogUtils;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

/** Exercises player-visible interactions against a running integrated server and real packet handlers. */
final class ModernGameplayChecks {
    private static final BlockPos NEXUS=new BlockPos(0,-60,0), STORAGE=new BlockPos(2,-60,0), SOURCE=new BlockPos(4,-60,0), TARGET=new BlockPos(4,-60,5), SHELF=new BlockPos(0,-60,-3);
    private static int stage,ticks;
    private static CompletableFuture<Void> pending;
    private static RuneLayer push;
    private static int menuId;
    static void setup(ServerPlayer p){
        var level=p.level();
        node(p,STORAGE).inventory().insertItem(1,namedLog().copyWithCount(24),false);
        var source=(Container)level.getBlockEntity(SOURCE);
        source.setItem(0,new ItemStack(Items.COPPER_INGOT,64));source.setItem(1,new ItemStack(Items.GOLD_INGOT,16));
        level.setBlockAndUpdate(TARGET,Blocks.BARREL.defaultBlockState());
        var surface=RuneSurfaces.getOrCreate(level,SOURCE,Direction.SOUTH);
        push=surface.layers().getFirst();push.filter().add(FilterRules.Kind.ITEM,"minecraft:copper_ingot",false,ItemStack.EMPTY);
        push.filter().setMinimum(8);push.filter().setTarget(16);push.changed();
        check(surface.toggleTarget(push.id(),GlobalPos.of(level.dimension(),TARGET),Direction.NORTH).assigned(),"rune destination assignment");
        level.setBlockAndUpdate(SHELF,Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,Direction.SOUTH));
        level.setBlockAndUpdate(new BlockPos(0,-60,-5),Blocks.CRAFTING_TABLE.defaultBlockState());
        use(p,STORAGE,new ItemStack(AstralContent.MOON_ATTUNEMENT.get()));
        check(node(p,STORAGE).storageTier()==2,"moon upgrade interaction");
        use(p,STORAGE,new ItemStack(AstralContent.STAR_ATTUNEMENT.get()));
        check(node(p,STORAGE).storageTier()==3,"star upgrade interaction");
        use(p,STORAGE,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_dye"))));check(node(p,STORAGE).channel()==14,"dye changes channel");
        use(p,STORAGE,new ItemStack(Items.WATER_BUCKET));check(node(p,STORAGE).channel()==-1,"water resets channel");
        // A survival break produces a real block item with inventory, fluid, energy and upgrade data.
        var savedPos=new BlockPos(-3,-60,0);level.setBlockAndUpdate(savedPos,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
        var stored=node(p,savedPos);stored.upgradeStorage(2);stored.inventory().insertItem(0,new ItemStack(Items.DIAMOND,23),false);
        stored.tank().fill(new com.cappleapple.astralrepository.platform.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        stored.energy().receiveEnergy(500,false);
        var drops=Block.getDrops(stored.getBlockState(),level,savedPos,stored,p,new ItemStack(Items.DIAMOND_PICKAXE));
        check(drops.size()==1,"storage drops exactly one preserved crystal");
        level.removeBlock(savedPos,false);var drop=drops.getFirst();
        p.setItemInHand(InteractionHand.MAIN_HAND,drop);
        var hit=new BlockHitResult(Vec3.atCenterOf(savedPos.below()).add(0,.5,0),Direction.UP,savedPos.below(),false);
        check(p.gameMode.useItemOn(p,level,drop,InteractionHand.MAIN_HAND,hit).consumesAction(),"re-place storage item");
        var restored=node(p,savedPos);check(restored!=null&&restored.storageTier()==2&&restored.inventory().getStackInSlot(0).getCount()==23&&restored.tank().getFluidAmount()==1000&&restored.energy().getEnergyStored()==500,"broken and replaced storage retains all resources and tier");
        // Keep persistence fixture isolated from the Nexus stock assertions.
        restored.setChannel(15);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AstralContent.ATTUNEMENT_WAND.get()));
        LogUtils.getLogger().info("FABRIC_GAMEPLAY_UPGRADES_AND_DROP_PERSISTENCE_OK");
    }
    static boolean tick(Minecraft mc){
        if(++ticks>800)throw new AssertionError("Gameplay timeout stage "+stage);
        if(pending!=null){if(!pending.isDone())return false;pending.join();pending=null;}
        if(stage==0){
            menuId=((NexusMenu)mc.player.containerMenu).containerId;
            action(NetworkPackets.SEARCH,ItemStack.EMPTY,0,"minecraft:iron_ingot",1);next();
        }else if(stage==1&&ticks>20){
            var menu=(NexusMenu)mc.player.containerMenu;
            check(menu.entries.size()==1&&menu.entries.getFirst().stack().is(Items.IRON_INGOT)&&menu.entries.getFirst().count()==64,"live search reply and linked inventory count");
            action(NetworkPackets.PICKUP,new ItemStack(Items.IRON_INGOT),1,"",2);next();
        }else if(stage==2&&ticks>20){
            check(mc.player.containerMenu.getCarried().is(Items.IRON_INGOT)&&mc.player.containerMenu.getCarried().getCount()==32,"right-click withdraw half into real cursor");
            action(NetworkPackets.DEPOSIT,ItemStack.EMPTY,0,"",3);next();
        }else if(stage==3&&ticks>20){
            check(mc.player.containerMenu.getCarried().isEmpty(),"deposit clears real cursor");
            check(((NexusMenu)mc.player.containerMenu).entries.getFirst().count()==64,"deposit restores network count");
            action(NetworkPackets.QUICK_WITHDRAW,new ItemStack(Items.IRON_INGOT),0,"",4);next();
        }else if(stage==4&&ticks>20){
            server(p->{
                check(p.getInventory().countItem(Items.IRON_INGOT)==64,"shift-withdraw reaches player inventory");
                var menu=(NexusMenu)p.containerMenu;
                for(int i=10;i<menu.slots.size();i++)if(menu.getSlot(i).getItem().is(Items.IRON_INGOT)){menu.quickMoveStack(p,i);break;}
            });next();
        }else if(stage==5&&ticks>30){
            server(p->{
                check(p.getInventory().countItem(Items.IRON_INGOT)==0,"shift-deposit removes player stock");
                check(((NexusMenu)p.containerMenu).network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==64,"shift-deposit conserves network stock");
                check(count((Container)p.level().getBlockEntity(SOURCE),Items.COPPER_INGOT)==48&&count((Container)p.level().getBlockEntity(TARGET),Items.COPPER_INGOT)==16&&count((Container)p.level().getBlockEntity(SOURCE),Items.GOLD_INGOT)==16,"rune filter and target preserve source remainder and excluded item");
                check(push.transferredItems()==16,"rune committed transfer counter");
                var copy=RuneSurface.load(p.level().getServer(),push.surface().save(p.registryAccess()),p.registryAccess());
                check(copy.get(push.id()).target().face()==Direction.NORTH&&copy.get(push.id()).filter().target()==16,"rune target and stock settings persist");
                manualCraft(p);
                p.closeContainer();p.getInventory().setSelectedSlot(0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AstralContent.RECIPE_TOME.get()));
                p.gameMode.useItem(p,p.level(),p.getMainHandItem(),InteractionHand.MAIN_HAND);
            });next();
        }else if(stage==6&&mc.gui.screen() instanceof RecipeTomeScreen&&ticks>10){
            PacketDistributor.sendToServer(new RecipeTomePackets.Action(mc.player.containerMenu.containerId,1,"",0,"minecraft:iron_trapdoor"));next();
        }else if(stage==7&&ticks>30){
            server(p->{
                check(p.containerMenu instanceof RecipeTomeMenu,"normal tome use opens server menu");
                var tome=p.getMainHandItem();check(RecipeTomeItem.product(tome,p.registryAccess()).is(Items.IRON_TRAPDOOR),"packet inscription stores actual recipe output");
                var page=((RecipeTomeMenu)p.containerMenu).browse("minecraft:iron_trapdoor",0);
                check(page.entries().size()==1&&page.entries().getFirst().ingredients().stream().filter(s->s.is(Items.IRON_INGOT)).count()==4,"recipe catalogue returns actual shaped ingredients");
                p.closeContainer();
                var hit=new BlockHitResult(Vec3.atCenterOf(SHELF).add(-.3,.25,.5),Direction.SOUTH,SHELF,false);
                check(p.gameMode.useItemOn(p,p.level(),tome,InteractionHand.MAIN_HAND,hit).consumesAction(),"normal bookshelf insertion");
                check(RecipeTomeItem.product(((Container)p.level().getBlockEntity(SHELF)).getItem(0),p.registryAccess()).is(Items.IRON_TRAPDOOR),"bookshelf retains inscribed product");
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            });next();
        }else if(stage==8&&ticks>50){server(p->NetworkManager.open(p,GlobalPos.of(p.level().dimension(),NEXUS)));next();
        }else if(stage==9&&mc.gui.screen() instanceof NexusScreen&&ticks>30){
            menuId=mc.player.containerMenu.containerId;
            check(((NexusMenu)mc.player.containerMenu).entries.stream().anyMatch(e->e.stack().is(Items.IRON_TRAPDOOR)&&e.craftable()),"Nexus discovers real bookshelf recipe");
            action(NetworkPackets.CRAFT,new ItemStack(Items.IRON_TRAPDOOR),2,"",5);next();
        }else if(stage==10&&ticks>200){
            server(p->{
                var network=((NexusMenu)p.containerMenu).network();var snapshot=network.snapshot();
                check(snapshot.getOrDefault(new ItemKey(new ItemStack(Items.IRON_TRAPDOOR)),0L)==2,"autocraft deposits two trapdoors; jobs="+network.crafting().statuses());
                check(snapshot.getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==56,"autocraft consumes exactly eight iron");
                check(network.crafting().activeJobs()==0,"autocraft completes without stuck jobs");
                p.closeContainer();var remote=new ItemStack(AstralContent.ASTRAL_NEXUS.get());use(p,NEXUS,remote);
                check(GlobalPos.of(p.level().dimension(),NEXUS).equals(AstralNexusItem.bound(remote)),"remote Nexus binds through block use");
                p.inventoryMenu.broadcastChanges();
            });next();
        }else if(stage==11&&ticks>20){
            check(mc.player.getMainHandItem().is(AstralContent.ASTRAL_NEXUS.get())&&AstralNexusItem.bound(mc.player.getMainHandItem())!=null,"bound remote item reaches client inventory");
            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);next();
        }else if(stage==12&&ticks>20){
            check(mc.gui.screen() instanceof NexusScreen,"remote menu reaches client: "+mc.gui.screen());
            server(p->{check(p.containerMenu instanceof NexusMenu,"remote packet opens server menu");p.closeContainer();p.teleportTo(p.level(),2.5,-60,3.5,Set.of(),180,0,false);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();});next();
        }else if(stage==13&&ticks>20){
            check(mc.player.getMainHandItem().isEmpty(),"empty hand reaches client");
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(STORAGE).add(0,0,.5),Direction.SOUTH,STORAGE,false));next();
        }else if(stage==14&&ticks>20){
            check(mc.gui.screen() instanceof NexusScreen,"local storage menu reaches client: "+mc.gui.screen());
            server(p->{check(p.containerMenu instanceof NexusMenu,"empty-hand storage packet opens server menu");p.closeContainer();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AstralContent.ATTUNEMENT_WAND.get()));});next();
        }else if(stage==15){
            LogUtils.getLogger().info("FABRIC_GAMEPLAY_INTERACTIONS_OK search pickup deposit shift-withdraw shift-deposit manual-crafting-refill tome-inscription bookshelf autocraft remote local-storage rune-filter-stock-cap conservation persistence");
            return true;
        }
        return false;
    }
    private static ItemStack namedLog(){var stack=new ItemStack(Items.OAK_LOG);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Exact runtime crafting input"));return stack;}
    private static void manualCraft(ServerPlayer p){
        var menu=(NexusMenu)p.containerMenu;var key=new com.cappleapple.astralrepository.api.ItemKey(namedLog());
        var first=menu.network().extractAt(key,1,menu.origin);check(first.getCount()==1,"Exact named ingredient withdrawn");menu.grid.setItem(0,first);
        check(menu.slots.getFirst().getItem().is(Items.OAK_PLANKS),"Real vanilla recipe matches");
        menu.clicked(0,0,net.minecraft.world.inventory.ContainerInput.PICKUP,p);
        check(menu.getCarried().is(Items.OAK_PLANKS)&&menu.getCarried().getCount()==4,"Manual craft produces four planks");
        check(ItemStack.isSameItemSameComponents(menu.grid.getItem(0),namedLog())&&menu.network().snapshot().getOrDefault(key,0L)==22,"Craft refills exact components");
        menu.clicked(10,0,net.minecraft.world.inventory.ContainerInput.PICKUP,p);
        menu.clicked(0,0,net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,p);
        check(count(p.getInventory(),Items.OAK_PLANKS)==68,"Shift craft limits output to one additional stack");
        check(menu.network().snapshot().getOrDefault(key,0L)==6&&menu.grid.getItem(0).getCount()==1,"Shift craft conservation");
        menu.action(new NetworkPackets.Action(menu.containerId,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY,0,0,""));
        check(menu.grid.isEmpty()&&menu.network().snapshot().getOrDefault(key,0L)==7,"Clear grid returns remaining ingredient");
    }
    private static void use(ServerPlayer p,BlockPos pos,ItemStack item){p.setItemInHand(InteractionHand.MAIN_HAND,item);var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(0,0,.5),Direction.SOUTH,pos,false);p.gameMode.useItemOn(p,p.level(),item,InteractionHand.MAIN_HAND,hit);}
    private static CrystalNodeBlockEntity node(ServerPlayer p,BlockPos pos){return (CrystalNodeBlockEntity)p.level().getBlockEntity(pos);}
    private static int count(Container container,Item item){int total=0;for(int i=0;i<container.getContainerSize();i++)if(container.getItem(i).is(item))total+=container.getItem(i).getCount();return total;}
    private static void action(int kind,ItemStack stack,int amount,String query,long request){PacketDistributor.sendToServer(new NetworkPackets.Action(menuId,kind,stack,amount,0,query,request));}
    private static void server(Consumer<ServerPlayer> work){var mc=Minecraft.getInstance();var id=mc.player.getUUID();pending=new CompletableFuture<>();var job=pending;mc.getSingleplayerServer().execute(()->{try{work.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));job.complete(null);}catch(Throwable error){job.completeExceptionally(error);}});}
    private static void next(){stage++;ticks=0;LogUtils.getLogger().info("FABRIC_GAMEPLAY_STAGE {}",stage);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
