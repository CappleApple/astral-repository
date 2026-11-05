package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.RecipeTomeScreen;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.RecipeTomeItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import com.cappleapple.astralrepository.network.NetworkManager;

/** Focused real-menu packet and rendering check, run after the rune client fixture. */
public final class TomeUpdateClientSmoke {
    private static final Path OUT=Path.of("../build/client-smoke/tome-update");
    private static final ResourceLocation BLOCK=ResourceLocation.withDefaultNamespace("iron_block");
    private static int phase,ticks,pageWait,pages;
    private static volatile boolean serverDone;
    private static volatile String failure;
    private static volatile ResourceLocation unpack;
    private static volatile ResourceLocation smelt;
    private static final BlockPos NEXUS=new BlockPos(1,-59,0),SHELF=new BlockPos(1,-59,-3);

    public static boolean tick() throws Exception {
        var mc=Minecraft.getInstance();
        mc.mouseHandler.releaseMouse();
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        if(failure!=null)throw new AssertionError(failure);
        if(++ticks>1200)throw new AssertionError("Tome update client timed out in phase "+phase);
        if(phase==0){
            next(1);
            server(player->{
                unpack=player.server.getRecipeManager().getRecipes().stream()
                        .filter(holder->holder.value().getResultItem(player.registryAccess()).is(Items.IRON_INGOT))
                        .filter(holder->holder.value().getIngredients().stream().anyMatch(ingredient->ingredient.test(new ItemStack(Items.IRON_BLOCK))))
                        .map(holder->holder.id()).findFirst().orElseThrow();
                smelt=player.server.getRecipeManager().getRecipes().stream()
                        .filter(holder->holder.value().getType()==RecipeType.SMELTING)
                        .filter(holder->holder.value().getResultItem(player.registryAccess()).is(Items.IRON_INGOT))
                        .map(holder->holder.id()).findFirst().orElseThrow();
                player.closeContainer();player.getInventory().selected=0;
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AstralContent.RECIPE_TOME.get()));
                AstralContent.RECIPE_TOME.get().use(player.level(),player,InteractionHand.MAIN_HAND);
            });
        }else if(phase==1&&serverDone&&mc.screen instanceof RecipeTomeScreen screen&&ticks>8){
            check(screen.acceptItem(new ItemStack(Items.IRON_INGOT)),"The tome must accept the desired final output");next(2);
        }else if(phase==2&&mc.screen instanceof RecipeTomeScreen screen&&ticks>8&&!screen.visibleRecipes().isEmpty()){
            click(screen,"Inscribe");next(3);
        }else if(phase==3&&ticks>15){
            next(4);server(player->check(RecipeTomeItem.product(player.getMainHandItem(),player.registryAccess()).is(Items.IRON_INGOT),"The actual Inscribe button must teach iron ingots on the server"));
        }else if(phase==4&&serverDone&&mc.screen instanceof RecipeTomeScreen screen&&ticks>8){
            if(!show(screen,unpack))return false;
            click(screen,"Block recipe");next(5);
        }else if(phase==5&&mc.screen instanceof RecipeTomeScreen screen&&ticks>10&&selected(screen,unpack,true)){
            shot(mc,"blocked.png");next(6);
            server(player->{
                check(RecipeTomeItem.excludedRecipes(player.getMainHandItem()).contains(unpack.toString()),"The Block button must persist the exact unpack recipe server-side");
                check(RecipeTomeItem.product(player.getMainHandItem(),player.registryAccess()).is(Items.IRON_INGOT),"Blocking an alternative must preserve the final output");
            });
        }else if(phase==6&&serverDone&&mc.screen instanceof RecipeTomeScreen screen&&ticks>8){
            check(screen.acceptItem(new ItemStack(Items.IRON_BLOCK)),"The tome can browse an intermediate output");next(7);
        }else if(phase==7&&mc.screen instanceof RecipeTomeScreen screen&&ticks>10&&screen.selectRecipe(BLOCK)){
            click(screen,"Block recipe");next(8);
        }else if(phase==8&&mc.screen instanceof RecipeTomeScreen screen&&ticks>10&&selected(screen,BLOCK,true)){
            shot(mc,"intermediate.png");next(9);
            server(player->{
                var excluded=RecipeTomeItem.excludedRecipes(player.getMainHandItem());
                check(excluded.contains(unpack.toString())&&excluded.contains(BLOCK.toString()),"An intermediate route is blocked independently");
                check(RecipeTomeItem.product(player.getMainHandItem(),player.registryAccess()).is(Items.IRON_INGOT),"Browsing an intermediate output must not reteach the tome");
            });
        }else if(phase==9&&serverDone&&mc.screen instanceof RecipeTomeScreen screen&&ticks>8){
            screen.acceptItem(new ItemStack(Items.IRON_INGOT));next(10);
        }else if(phase==10&&mc.screen instanceof RecipeTomeScreen screen&&ticks>8){
            if(!show(screen,unpack))return false;
            check(selected(screen,unpack,true),"Returning to the final output retains its excluded alternative");
            click(screen,"Allow recipe");next(11);
        }else if(phase==11&&mc.screen instanceof RecipeTomeScreen screen&&ticks>10&&selected(screen,unpack,false)){
            shot(mc,"allowed.png");next(12);
            server(player->{
                var excluded=RecipeTomeItem.excludedRecipes(player.getMainHandItem());
                check(!excluded.contains(unpack.toString())&&excluded.contains(BLOCK.toString()),"The Allow button restores only its selected route");
                player.closeContainer();AstralContent.RECIPE_TOME.get().use(player.level(),player,InteractionHand.MAIN_HAND);
            });
        }else if(phase==12&&serverDone&&mc.screen instanceof RecipeTomeScreen screen&&ticks>15){
            if(!show(screen,unpack))return false;
            check(selected(screen,unpack,false),"The allowed route survives closing and reopening the real tome");
            check(screen.processName().equals("Crafting table"),"Crafting alternatives must identify the required physical crafting table");
            next(13);
        }else if(phase==13&&mc.screen instanceof RecipeTomeScreen screen&&ticks>8){
            if(!show(screen,smelt))return false;
            check(screen.processName().equals("Smelting / Furnace"),"The selected smelting recipe must identify its process and workstation");
            var details=screen.processTooltip().stream().map(component->component.getString()).toList();
            check(details.contains("Recipe type: minecraft:smelting")&&details.contains("Recipe: "+smelt),"The process tooltip must retain the exact server recipe and type IDs");
            shot(mc,"smelting-process.png");screen.acceptItem(new ItemStack(Items.CHEST));next(14);
        }else if(phase==14&&mc.screen instanceof RecipeTomeScreen screen&&ticks>10&&!screen.visibleRecipes().isEmpty()&&screen.visibleRecipes().getFirst().output().is(Items.CHEST)){
            click(screen,"Inscribe");next(15);
        }else if(phase==15&&mc.screen instanceof RecipeTomeScreen&&ticks>15){
            next(16);server(player->{
                check(RecipeTomeItem.product(player.getMainHandItem(),player.registryAccess()).is(Items.CHEST),"The quantity fixture must teach real chest output");
                var level=player.serverLevel();level.setBlockAndUpdate(SHELF,Blocks.CHISELED_BOOKSHELF.defaultBlockState());
                ((ChiseledBookShelfBlockEntity)level.getBlockEntity(SHELF)).setItem(0,player.getMainHandItem().copy());
                NetworkManager.changed(level,SHELF);player.closeContainer();
                NetworkManager.open(player,GlobalPos.of(level.dimension(),NEXUS));
            });
        }else if(phase==16&&serverDone&&mc.screen instanceof NexusScreen screen&&ticks>25){
            var search=screen.children().stream().filter(widget->widget instanceof EditBox box&&box.getMessage().getString().equals("Search storage"))
                    .map(widget->(EditBox)widget).findFirst().orElseThrow();
            search.setValue("chest");next(17);
        }else if(phase==17&&mc.screen instanceof NexusScreen screen&&ticks>15){
            if(screen.getMenu().entries.size()!=1)return false;
            var entry=screen.getMenu().entries.getFirst();
            check(entry.stack().is(Items.CHEST)&&entry.craftable()&&entry.count()==0,"The starred test output must be taught and absent from storage");
            clickEntry(screen,0);next(18);
        }else if(phase==18&&mc.screen instanceof NexusScreen screen&&ticks>12){
            check(quantity(screen).isFocused(),"Left-clicking an unstocked star must focus the amount field");
            check(screen.getMenu().jobs.isEmpty(),"Choosing a quantity must not queue a preliminary single craft");
            next(19);server(player->check(crafting(player).activeJobs()==0,"Left-click selection must not create a server-side job"));
        }else if(phase==19&&serverDone&&mc.screen instanceof NexusScreen screen&&ticks>8){
            clickEntry(screen,1);next(20);
        }else if(phase==20&&mc.screen instanceof NexusScreen screen&&ticks>12){
            check(quantity(screen).isFocused()&&screen.getMenu().jobs.isEmpty(),"Right-click selection must also wait for an explicit request");
            next(21);server(player->check(crafting(player).activeJobs()==0,"Right-click selection must not create a server-side job"));
        }else if(phase==21&&serverDone&&mc.screen instanceof NexusScreen screen&&ticks>8){
            quantity(screen).setValue("64");screen.keyPressed(257,0,0);next(22);
        }else if(phase==22&&mc.screen instanceof NexusScreen screen&&ticks>15){
            if(screen.getMenu().jobs.isEmpty())return false;
            check(screen.getMenu().jobs.size()==1&&screen.getMenu().jobs.getFirst().target().is(Items.CHEST)&&screen.getMenu().jobs.getFirst().count()==64,
                    "Submitting the amount must create exactly one 64-item request");
            shot(mc,"star-quantity-request.png");next(23);server(player->{
                var service=crafting(player);var jobs=service.statuses();
                check(jobs.size()==1&&jobs.getFirst().target().is(Items.CHEST)&&jobs.getFirst().count()==64,"The server must receive only the chosen amount");
                service.cancelAll();
            });
        }else if(phase==23&&serverDone&&ticks>8){
            check(!mc.mouseHandler.isMouseGrabbed()&&mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"The client stays muted and mouse-free");
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.txt"),"PASS: actual Inscribe, Block recipe and Allow recipe button clicks and serverbound packets; exact iron-block unpack recipe discovered from the server; independent intermediate recipe exclusion; final target preservation; selective allowance; close/reopen persistence; actual crafting and smelting process labels and exact recipe/type tooltip IDs; left/right unstocked-star clicks focus quantity with zero server jobs; Enter submits exactly one chosen 64-item request; blocked/intermediate/allowed/process/quantity screenshots; muted mouse-free client.\n");
            return true;
        }
        return false;
    }

    private static com.cappleapple.astralrepository.crafting.CraftingService crafting(ServerPlayer player){
        return NetworkManager.get(player.server).networkAt(GlobalPos.of(player.level().dimension(),NEXUS)).crafting();
    }
    private static EditBox quantity(NexusScreen screen){return screen.children().stream()
            .filter(widget->widget instanceof EditBox box&&box.getMessage().getString().equals("Craft quantity"))
            .map(widget->(EditBox)widget).findFirst().orElseThrow();}
    private static void clickEntry(NexusScreen screen,int button){
        double x=(screen.width-318)/2+18,y=(screen.height-266)/2+57;
        check(screen.mouseClicked(x,y,button),"The starred storage cell must consume its click");screen.mouseReleased(x,y,button);
    }

    private static boolean selected(RecipeTomeScreen screen,ResourceLocation recipe,boolean excluded){
        return screen.visibleRecipes().stream().anyMatch(entry->entry.recipe().equals(recipe)&&entry.excluded()==excluded);
    }
    private static boolean show(RecipeTomeScreen screen,ResourceLocation recipe){
        if(screen.selectRecipe(recipe))return true;
        if(++pageWait<10)return false;pageWait=0;
        check(++pages<=32,"The requested recipe was absent from the server catalogue");
        int left=(Minecraft.getInstance().getWindow().getGuiScaledWidth()-192)/2;
        var next=screen.children().stream().filter(widget->widget instanceof PageButton button&&button.visible&&button.getX()==left+126)
                .map(widget->(PageButton)widget).findFirst();
        if(next.isPresent())click(screen,next.get());
        return false;
    }
    private static void click(RecipeTomeScreen screen,String label){
        var button=screen.children().stream().filter(widget->widget instanceof Button b&&b.visible&&b.active&&b.getMessage().getString().equals(label))
                .map(widget->(Button)widget).findFirst().orElseThrow(()->new AssertionError("Missing active tome button: "+label));
        click(screen,button);
    }
    private static void click(RecipeTomeScreen screen,Button button){
        double x=button.getX()+button.getWidth()/2.0,y=button.getY()+button.getHeight()/2.0;
        check(screen.mouseClicked(x,y,0),"The actual tome button must handle its click");screen.mouseReleased(x,y,0);
    }
    private static void server(Consumer<ServerPlayer> action){
        serverDone=false;
        Minecraft.getInstance().getSingleplayerServer().execute(()->{
            try{action.accept(Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst());}
            catch(Throwable error){failure=error.toString();}
            finally{serverDone=true;}
        });
    }
    private static void next(int value){phase=value;ticks=0;pageWait=0;pages=0;}
    private static void shot(Minecraft mc,String name)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private TomeUpdateClientSmoke(){}
}
