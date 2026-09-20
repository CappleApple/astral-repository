package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;

/** Real, opt-in background gameplay regression. Never included in the release mod. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="astral_repository",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ForgeGameplaySmoke {
    private static final Path OUT=Path.of("gameplay-captures");
    private static final BlockPos NEXUS=new BlockPos(0,-60,0), STORAGE=new BlockPos(2,-60,0), CHEST=new BlockPos(4,-60,2);
    private static int phase,ticks;
    private static long craftingLogs;
    private static boolean done;
    private static CompletableFuture<Void> pending;
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void event(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase==net.minecraftforge.event.TickEvent.Phase.END)tick(Minecraft.getInstance());}
    private static void tick(Minecraft mc){
        if(!Boolean.getBoolean("astral_repository.portGameplay")||done)return;
        mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0D);
        try {
            if(++ticks>1800)throw new AssertionError("Gameplay timeout phase "+phase);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(phase==0&&mc.screen!=null&&mc.getOverlay()==null){
                mc.options.guiScale().set(2);mc.options.renderDistance().set(3);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                next(1);mc.createWorldOpenFlows().createFreshLevel("astral-gameplay-"+System.currentTimeMillis(),new LevelSettings("Astral gameplay",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }else if(phase==1&&mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()!=null&&mc.getOverlay()==null){mc.setScreen(null);server(ForgeGameplaySmoke::workshop);next(2);
            }else if(phase==2&&ticks>100){
                for(var entry:AstralContent.ITEMS.getEntries()){
                    var stack=new ItemStack(entry.get());String key=stack.getDescriptionId();
                    check(net.minecraft.client.resources.language.I18n.exists(key)&&!stack.getHoverName().getString().equals(key),"Untranslated item "+entry.getId()+": "+key);
                    if(stack.getItem() instanceof BlockItem item)check(key.equals(item.getBlock().getDescriptionId()),"Block item translation mismatch: "+entry.getId());
                    LogUtils.getLogger().info("FORGE_GAMEPLAY_NAME {} = {}",entry.getId(),stack.getHoverName().getString());
                    var lines=stack.getTooltipLines(mc.player,TooltipFlag.Default.NORMAL);check(!lines.isEmpty(),"Missing tooltip "+entry.getId());
                }
                check(mc.level.getBlockEntity(NEXUS) instanceof CrystalNodeBlockEntity,"Nexus placement not synchronized");capture("placed-blocks.png");
                server(p->{p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);var hit=new BlockHitResult(Vec3.atCenterOf(NEXUS),Direction.SOUTH,NEXUS,false);check(p.gameMode.useItemOn(p,p.serverLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit).consumesAction(),"Nexus use");});next(3);
            }else if(phase==3&&mc.screen instanceof NexusScreen screen&&ticks>30){
                check(screen.getMenu().entries.stream().anyMatch(e->e.stack().is(Items.IRON_INGOT)&&e.count()==64),"Nexus has no linked crystal stock");capture("nexus.png");
                action(NetworkPackets.PICKUP,new ItemStack(Items.IRON_INGOT));next(4);
            }else if(phase==4&&mc.player.containerMenu.getCarried().is(Items.IRON_INGOT)){
                check(mc.player.containerMenu.getCarried().getCount()==64,"Client withdrawal quantity");
                server(p->check(((CrystalNodeBlockEntity)p.serverLevel().getBlockEntity(STORAGE)).inventory().getStackInSlot(0).isEmpty(),"Server withdrawal did not commit"));
                action(NetworkPackets.DEPOSIT,ItemStack.EMPTY);next(5);
            }else if(phase==5&&mc.player.containerMenu.getCarried().isEmpty()&&ticks>10){
                server(p->{check(((NexusMenu)p.containerMenu).network().snapshot().getOrDefault(new com.cappleapple.astralrepository.api.ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==64,"Server deposit did not commit");p.getInventory().setItem(9,new ItemStack(Items.OAK_LOG));p.containerMenu.broadcastChanges();});next(6);
            }else if(phase==6&&mc.player.getInventory().getItem(9).is(Items.OAK_LOG)){
                click(10);next(7);
            }else if(phase==7&&mc.player.containerMenu.getCarried().is(Items.OAK_LOG)){
                click(1);next(8);
            }else if(phase==8&&mc.player.containerMenu.slots.get(0).getItem().is(Items.OAK_PLANKS)){
                click(0);next(9);
            }else if(phase==9&&mc.player.containerMenu.getCarried().is(Items.OAK_PLANKS)){
                check(mc.player.containerMenu.getCarried().getCount()==4,"Client recipe produced wrong quantity");capture("nexus-crafted.png");
                server(p->check(p.containerMenu.getCarried().is(Items.OAK_PLANKS)&&p.containerMenu.getCarried().getCount()==4,"Server craft differs from client"));next(10);
            }else if(phase==10){
                server(p->{p.closeContainer();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AstralContent.RECIPE_TOME.get()));AstralContent.RECIPE_TOME.get().use(p.level(),p,InteractionHand.MAIN_HAND);});next(11);
            }else if(phase==11&&mc.screen instanceof RecipeTomeScreen screen){screen.acceptItem(new ItemStack(Items.OAK_PLANKS));next(12);
            }else if(phase==12&&mc.screen instanceof RecipeTomeScreen screen){
                var id=new net.minecraft.resources.ResourceLocation("minecraft:oak_planks");
                if(!screen.selectRecipe(id))return;capture("recipe-catalogue.png");screen.inscribeSelection();next(13);
            }else if(phase==13&&RecipeTomeItem.product(mc.player.getMainHandItem(),mc.level.registryAccess()).is(Items.OAK_PLANKS)){
                server(p->{
                    var level=p.serverLevel();var tome=p.getMainHandItem().copy();check(RecipeTomeItem.product(tome,level.registryAccess()).is(Items.OAK_PLANKS),"Server inscription output");p.closeContainer();
                    var table=new BlockPos(0,-60,-2);var shelf=new BlockPos(1,-60,-2);level.setBlockAndUpdate(table,Blocks.CRAFTING_TABLE.defaultBlockState());level.setBlockAndUpdate(shelf,Blocks.CHISELED_BOOKSHELF.defaultBlockState());((Container)level.getBlockEntity(shelf)).setItem(0,tome);
                    check(net.minecraftforge.items.ItemHandlerHelper.insertItem(new net.minecraftforge.items.wrapper.InvWrapper((Container)level.getBlockEntity(CHEST)),new ItemStack(Items.OAK_LOG,4),false).isEmpty(),"Extra crafting logs fit without replacing existing stock");NetworkManager.changed(level,table);NetworkManager.changed(level,shelf);NetworkManager.changed(level,CHEST);
                    p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                });next(14);
            }else if(phase==14&&ticks>60){server(p->NetworkManager.open(p,GlobalPos.of(p.level().dimension(),NEXUS)));next(15);
            }else if(phase==15&&mc.screen instanceof NexusScreen screen&&screen.getMenu().entries.stream().anyMatch(e->e.stack().is(Items.OAK_PLANKS)&&e.craftable())){
                server(p->{craftingLogs=((NexusMenu)p.containerMenu).network().snapshot().getOrDefault(new com.cappleapple.astralrepository.api.ItemKey(new ItemStack(Items.OAK_LOG)),0L);check(craftingLogs>=2,"Autocraft has real ingredients");});next(18);
            }else if(phase==18){
                com.cappleapple.astralrepository.platform.PacketDistributor.sendToServer(new NetworkPackets.Action(mc.player.containerMenu.containerId,NetworkPackets.CRAFT,new ItemStack(Items.OAK_PLANKS),8,0,""));next(16);
            }else if(phase==16&&mc.screen instanceof NexusScreen screen&&screen.getMenu().entries.stream().anyMatch(e->e.stack().is(Items.OAK_PLANKS)&&e.count()==8)){
                server(p->{var menu=(NexusMenu)p.containerMenu;check(menu.network().snapshot().getOrDefault(new com.cappleapple.astralrepository.api.ItemKey(new ItemStack(Items.OAK_PLANKS)),0L)==8,"Autocraft output inserted in real storage");check(menu.network().snapshot().getOrDefault(new com.cappleapple.astralrepository.api.ItemKey(new ItemStack(Items.OAK_LOG)),0L)==craftingLogs-2,"Autocraft consumed exactly two logs");check(menu.network().snapshot().getOrDefault(new com.cappleapple.astralrepository.api.ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==64,"Autocraft preserves unrelated deposited iron");check(menu.network().crafting().statuses().stream().anyMatch(j->j.state().equals("COMPLETE")),"Autocraft finished a real job");});capture("nexus-autocraft.png");next(17);
            }else if(phase==17){
                check(!mc.mouseHandler.isMouseGrabbed(),"Client captured mouse");Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"PASS: actual block-item placement for every registered block; placed world render; all item localization/tooltips; synchronized linked Nexus menu; client-to-server withdrawal/deposit packets and vanilla menu-click crafting; actual Recipe Tome catalogue and inscription; bookshelf discovery; completed eight-plank autocraft consuming exactly two logs.\n");LogUtils.getLogger().info("FORGE_GAMEPLAY_OK");done=true;mc.stop();

            }
        }catch(Throwable failure){done=true;LogUtils.getLogger().error("Forge gameplay regression failed",failure);try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"FAIL: "+failure+"\n");}catch(Exception ignored){}mc.stop();}
    }
    private static void workshop(ServerPlayer p){
        var level=p.serverLevel();p.teleportTo(level,2.5,-58,5.5,180,25);p.getAbilities().flying=true;p.onUpdateAbilities();
        for(int x=-2;x<24;x++)for(int z=-2;z<8;z++)level.setBlockAndUpdate(new BlockPos(x,-61,z),Blocks.SMOOTH_STONE.defaultBlockState());
        place(p,AstralContent.STORAGE_NEXUS.get(),NEXUS);place(p,AstralContent.SEED_STORAGE_CRYSTAL.get(),STORAGE);
        int x=6;for(var entry:AstralContent.BLOCKS.getEntries()){var block=entry.get();if(block!=AstralContent.STORAGE_NEXUS.get()&&block!=AstralContent.SEED_STORAGE_CRYSTAL.get())place(p,block,new BlockPos(x++,-60,0));}
        ((CrystalNodeBlockEntity)level.getBlockEntity(STORAGE)).inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,64),false);
        check(NetworkManager.get(level.getServer()).toggleLink(AnchorAddress.crystal(level,NEXUS),AnchorAddress.crystal(level,STORAGE)).success(),"Storage link failed");
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());((Container)level.getBlockEntity(CHEST)).setItem(0,new ItemStack(Items.OAK_LOG,32));NetworkManager.changed(level,CHEST);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AstralContent.ATTUNEMENT_WAND.get()));
    }
    private static void place(ServerPlayer p,Block block,BlockPos pos){
        var item=new ItemStack(block);p.setItemInHand(InteractionHand.MAIN_HAND,item);
        var hit=new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false);
        check(item.useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Block item placement rejected "+BuiltInRegistries.BLOCK.getKey(block));
        check(p.level().getBlockState(pos).is(block),"Placed wrong block "+BuiltInRegistries.BLOCK.getKey(block));
        LogUtils.getLogger().info("FORGE_GAMEPLAY_PLACED {}",BuiltInRegistries.BLOCK.getKey(block));
    }
    private static void server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();pending=mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());action.accept(p);});}
    private static void action(int kind,ItemStack stack){var mc=Minecraft.getInstance();com.cappleapple.astralrepository.platform.PacketDistributor.sendToServer(new NetworkPackets.Action(mc.player.containerMenu.containerId,kind,stack,0,0,""));}
    private static void click(int slot){var mc=Minecraft.getInstance();mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);}
    private static void capture(String file)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(OUT.resolve(file));}}
    private static void next(int value){phase=value;ticks=0;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
