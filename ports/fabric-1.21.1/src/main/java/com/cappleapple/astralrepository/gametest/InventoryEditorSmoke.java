package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;
import java.nio.file.*;

@net.neoforged.fml.common.EventBusSubscriber(modid="astral_repository",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class InventoryEditorSmoke {
    private static ItemStack tooltip=ItemStack.EMPTY;
    @net.neoforged.bus.api.SubscribeEvent public static void tooltip(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){if(Boolean.getBoolean("astral_repository.editorOnly")&&phase==14)event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font,tooltip,180,100);}
    private static int phase,ticks;
    private static volatile boolean ready;
    private static volatile String failure;
    private static final BlockPos HOST=new BlockPos(3,-59,0);
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError(failure);
        if(++ticks>1200)throw new AssertionError("Inventory editor timeout "+phase);
        if(phase==0){phase=1;mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            p.getInventory().setItem(9,new ItemStack(Items.IRON_INGOT,32));p.getInventory().setItem(10,new ItemStack(Items.WATER_BUCKET));
            var surface=RuneSurfaces.get(p.serverLevel(),HOST,Direction.SOUTH);var rune=surface.layers().getFirst();rune.clearFilter();rune.filter().setTarget(Long.MAX_VALUE);
            var aim=RuneLayout.placed(surface).getFirst().center();p.teleportTo(aim.x,aim.y-p.getEyeHeight(),aim.z+2);
            p.getInventory().selected=0;var wand=p.getMainHandItem();check(!wand.hasFoil(),"Idle wand glinted");p.setShiftKeyDown(true);
            var hit=new BlockHitResult(aim,Direction.SOUTH,HOST,false);RuneProgramming.use(wand,p.level(),HOST,p,InteractionHand.MAIN_HAND,hit);check(wand.hasFoil(),"Binding wand lacked glint");
            p.setShiftKeyDown(false);RuneProgramming.use(wand,p.level(),HOST,p,InteractionHand.MAIN_HAND,hit);check(!wand.hasFoil(),"Finished binding retained glint");
            ready=true;
        }catch(Throwable t){failure=t.toString();}});}
        else if(phase==1&&ready&&mc.screen instanceof RuneSettingsScreen s&&ticks>15){
            check(s.getMenu().slots.size()==36,"Missing player slots");check(s.children().stream().noneMatch(w->w instanceof EditBox b&&b.getMessage().getString().contains("Search")),"Removed search is still present");
            var limit=field(s,"Stop at destination");check(limit.getValue().equals("-1"),"Unlimited is not -1");
            s.mouseScrolled(limit.getX()+4,limit.getY()+4,0,-1);check(limit.getValue().equals("-1"),"Unlimited scrolled below -1");
            s.mouseScrolled(limit.getX()+4,limit.getY()+4,0,1);check(limit.getValue().equals("0"),"Unlimited did not step to zero");
            s.mouseScrolled(limit.getX()+4,limit.getY()+4,0,1);check(limit.getValue().equals("1"),"Zero did not step to one");
            limit.setValue("-1");clickItem(s,Items.IRON_INGOT);next(11);
        }
        else if(phase==11&&mc.screen instanceof RuneSettingsScreen s&&ticks>15){
            check(s.getMenu().getCarried().is(Items.IRON_INGOT)&&s.currentPage().rules().isEmpty(),"Normal pickup added a filter or failed to pick up");
            s.mouseClicked(s.getGuiLeft()+18,s.getGuiTop()+43,0);next(12);
        }
        else if(phase==12&&mc.screen instanceof RuneSettingsScreen s&&ticks>15&&!s.pendingChanges()){
            check(s.currentPage().rules().contains("minecraft:iron_ingot")&&s.getMenu().getCarried().getCount()==32,"Cursor drop did not copy safely");
            var slot=s.getMenu().slots.stream().filter(x->x.getContainerSlot()==9).findFirst().orElseThrow();
            s.mouseClicked(s.getGuiLeft()+slot.x+8,s.getGuiTop()+slot.y+8,0);s.mouseReleased(s.getGuiLeft()+slot.x+8,s.getGuiTop()+slot.y+8,0);next(13);
        }
        else if(phase==13&&mc.screen instanceof RuneSettingsScreen s&&ticks>15){
            check(s.getMenu().getCarried().isEmpty(),"Normal click failed to return cursor");shiftItem(s,Items.IRON_INGOT);next(2);
        }
        else if(phase==2&&mc.screen instanceof RuneSettingsScreen s&&ticks>15&&!s.pendingChanges()){
            check(s.currentPage().rules().contains("minecraft:iron_ingot"),"Inventory did not add iron");check(s.getMenu().getCarried().isEmpty(),"Inventory selection moved items onto cursor");
            shiftItem(s,Items.WATER_BUCKET);next(3);
        }
        else if(phase==3&&mc.screen instanceof RuneSettingsScreen s&&ticks>15&&!s.pendingChanges()){
            check(s.currentPage().rules().contains("fluid:minecraft:water"),"Bucket did not select fluid");
            shot("rune_inventory.png");s.mouseClicked(s.getGuiLeft()+18,s.getGuiTop()+43,1);next(4);
        }
        else if(phase==4&&mc.screen instanceof RuneSettingsScreen s&&ticks>15&&!s.pendingChanges()){
            check(!s.currentPage().rules().contains("minecraft:iron_ingot"),"Right-click failed to remove filter");
            RecipeViewerDropSmoke.rune(s);next(5);
        }
        else if(phase==5&&mc.screen instanceof RuneSettingsScreen s&&ticks>15&&!s.pendingChanges()){
            RecipeViewerDropSmoke.verifyRune(s);s.apply();next(6);
        }
        else if(phase==6&&mc.screen==null&&ticks>15){
            phase=7;mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                check(p.getInventory().items.stream().filter(x->x.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum()>=32,"Filter copied inventory destructively");
                p.getInventory().selected=7;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AstralContent.RECIPE_TOME.get()));
                p.getMainHandItem().getItem().use(p.level(),p,InteractionHand.MAIN_HAND);
            }catch(Throwable t){failure=t.toString();}});
        }
        else if(phase==7&&mc.screen instanceof RecipeTomeScreen s&&ticks>15){
            shiftItem(s,Items.IRON_INGOT);next(8);
        }
        else if(phase==8&&mc.screen instanceof RecipeTomeScreen s&&ticks>15){
            check(!s.visibleRecipes().isEmpty()&&s.visibleRecipes().getFirst().output().is(Items.IRON_INGOT),"Tome inventory sample did not resolve server recipes");
            shot("tome_pagination.png");RecipeViewerDropSmoke.tome(s);next(9);
        }
        else if(phase==9&&mc.screen instanceof RecipeTomeScreen s&&ticks>15){
            check(!s.visibleRecipes().isEmpty(),"Tome ghost drop has no recipe");shot("tome_inventory.png");s.inscribeSelection();next(10);
        }
        else if(phase==10&&ticks>15){
            var tome=mc.player.containerMenu.slots.stream().map(slot->slot.getItem()).filter(stack->stack.is(AstralContent.RECIPE_TOME.get())&&stack.hasFoil()).findFirst().orElseThrow(()->new AssertionError("Tome was not inscribed"));
            check(tome.getTooltipLines(Item.TooltipContext.of(mc.level),mc.player,net.minecraft.world.item.TooltipFlag.NORMAL).stream().noneMatch(c->c.getString().contains("minecraft:")),"Tome leaked raw output ID");
            var tip=net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(tome.getTooltipImage().orElseThrow());check(tip instanceof TomeOutputTooltip&&tip.getHeight()==20,"Native output icon tooltip was not registered");
            tooltip=tome.copy();next(14);
        }
        else if(phase==14&&ticks>8){shot("tome_output_tooltip.png");
            check(!mc.mouseHandler.isMouseGrabbed(),"Client captured mouse");check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"Client audio enabled");return true;}
        return false;
    }
    private static EditBox field(RuneSettingsScreen s,String name){return (EditBox)s.children().stream().filter(w->w instanceof EditBox b&&b.getMessage().getString().equals(name)).findFirst().orElseThrow();}
    private static void clickItem(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s,Item item){
        var slot=s.getMenu().slots.stream().filter(x->x.getItem().is(item)).findFirst().orElseThrow();
        s.mouseClicked(s.getGuiLeft()+slot.x+8,s.getGuiTop()+slot.y+8,0);s.mouseReleased(s.getGuiLeft()+slot.x+8,s.getGuiTop()+slot.y+8,0);
    }
    private static void shiftItem(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s,Item item)throws Exception{
        var slot=s.getMenu().slots.stream().filter(x->x.getItem().is(item)).findFirst().orElseThrow();
        var method=s.getClass().getDeclaredMethod("slotClicked",net.minecraft.world.inventory.Slot.class,int.class,int.class,net.minecraft.world.inventory.ClickType.class);method.setAccessible(true);
        method.invoke(s,slot,slot.index,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE);
    }
    private static void next(int n){phase=n;ticks=0;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void shot(String file)throws Exception{try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Path.of("../build/client-smoke/"+file));}}
}
