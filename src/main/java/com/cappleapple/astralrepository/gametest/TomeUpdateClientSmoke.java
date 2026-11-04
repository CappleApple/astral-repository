package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.RecipeTomeScreen;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.RecipeTomeItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Focused real-menu packet and rendering check, run after the rune client fixture. */
public final class TomeUpdateClientSmoke {
    private static final Path OUT=Path.of("../build/client-smoke/tome-update");
    private static final ResourceLocation BLOCK=ResourceLocation.withDefaultNamespace("iron_block");
    private static int phase,ticks,pageWait,pages;
    private static volatile boolean serverDone;
    private static volatile String failure;
    private static volatile ResourceLocation unpack;

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
            check(!mc.mouseHandler.isMouseGrabbed()&&mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"The client stays muted and mouse-free");
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.txt"),"PASS: actual Inscribe, Block recipe and Allow recipe button clicks and serverbound packets; exact iron-block unpack recipe discovered from the server; independent intermediate recipe exclusion; final target preservation; selective allowance; close/reopen persistence; blocked/intermediate/allowed screenshots; muted mouse-free client.\n");
            return true;
        }
        return false;
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
