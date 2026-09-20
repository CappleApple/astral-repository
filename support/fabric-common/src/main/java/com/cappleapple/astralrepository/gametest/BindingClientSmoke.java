package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import java.nio.file.*;
import java.util.UUID;

/** Exercises actual client use-item packets and synchronized held stacks, not server helper calls. */
public final class BindingClientSmoke {
    private static final BlockPos SOURCE=new BlockPos(3,-59,7),TARGET=new BlockPos(5,-59,7),RELAY=new BlockPos(6,-59,9);
    private static int phase,ticks;
    private static volatile boolean ready;
    private static volatile String failure;
    private static double opacity;
    private static UUID rune, secondRune;
    private static BlockHitResult sourceHit, secondHit;
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError(failure);if(++ticks>400)throw new AssertionError("Binding timeout phase "+phase);
        if(phase==0){phase=1;mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var world=p.serverLevel();
            world.setBlockAndUpdate(SOURCE,Blocks.BARREL.defaultBlockState());world.setBlockAndUpdate(TARGET,Blocks.BARREL.defaultBlockState());world.setBlockAndUpdate(RELAY,AstralContent.RELAY_CRYSTAL.get().defaultBlockState());
            var face=RuneSurfaces.getOrCreate(world,SOURCE,Direction.SOUTH);var layer=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);rune=layer.id();
            sourceHit=new BlockHitResult(RuneLayout.placed(face).getFirst().center(),Direction.SOUTH,SOURCE,false);
            var preset=RunePreset.initial(RuneLayer.Mode.PUSH);RuneLibraryData.get(p.getServer()).library(p.getUUID()).put(preset.id(),preset);
            var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());WandPackets.selection(wand,preset.id(),true);p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,wand);p.teleportTo(4.5,-59,10);p.inventoryMenu.broadcastChanges();ready=true;
        }catch(Throwable e){failure=e.toString();}});}
        else if(phase==1&&ready&&ticks>25){mc.player.getInventory().selected=0;click(true,sourceHit);next(2);}
        else if(phase==2&&ticks>15){
            check(mc.screen==null,"Selecting source opened a menu");var selected=RuneProgramming.selection(mc.player.getMainHandItem());check(selected!=null&&rune.equals(selected.layer())&&mc.player.getMainHandItem().hasFoil(),"Actual client click did not synchronize rune binding: "+selected);
            click(false,new BlockHitResult(TARGET.getCenter().add(0,0,.5),Direction.SOUTH,TARGET,false));next(3);
        }
        else if(phase==3&&ticks>15){verifyTarget(true);click(false,new BlockHitResult(TARGET.getCenter().add(0,0,.5),Direction.SOUTH,TARGET,false));next(4);}
        else if(phase==4&&ticks>15){verifyTarget(false);click(true,sourceHit);next(5);}
        else if(phase==5&&ticks>15){click(true,new BlockHitResult(TARGET.getCenter().add(0,0,.5),Direction.SOUTH,TARGET,false));next(6);}
        else if(phase==6&&ticks>15){verifyTarget(true);next(7);}
        else if(phase==7&&ticks>15){click(false,new BlockHitResult(RELAY.getCenter(),Direction.UP,RELAY,false));next(9);}
        else if(phase==9&&ticks>15){verifyRelay(true);click(true,new BlockHitResult(RELAY.getCenter(),Direction.UP,RELAY,false));next(10);}
        else if(phase==10&&ticks>15){verifyRelay(false);click(true,new BlockHitResult(RELAY.getCenter(),Direction.UP,RELAY,false));next(11);}
        else if(phase==11&&ticks>15){verifyRelay(true);next(12);}
        else if(phase==12&&ticks>15){
            check(mc.screen==null,"Binding opened a menu");check(!mc.mouseHandler.isMouseGrabbed(),"Mouse was captured");
            var bound=mc.player.getMainHandItem().copy();check(bound.hasFoil(),"Selection no longer synchronized");var idle=bound.copy();RuneProgramming.cancel(idle);
            opacity=com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.get();com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(0.0);
            mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Wand glint comparison")){
                @Override public boolean isPauseScreen(){return false;}
                @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff101020);MaterialGeometrySmoke.renderItem(g,idle,width/4f,height/2f,128,0,0);MaterialGeometrySmoke.renderItem(g,bound,width*3/4f,height/2f,128,0,0);g.drawCenteredString(font,"Idle",width/4,40,0xffffff);g.drawCenteredString(font,"Binding",width*3/4,40,0xffffff);}
            });next(8);
        }
        else if(phase==8&&ticks>10){
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                int changed=0,w=image.getWidth(),h=image.getHeight();for(int y=h/2-100;y<h/2+100;y++)for(int x=w/4-100;x<w/4+100;x++)if(image.getPixelRGBA(x,y)!=image.getPixelRGBA(x+w/2,y))changed++;
                image.writeToFile(Path.of("../build/client-smoke/wand_binding_glint.png"));check(changed>100,"Binding glint did not alter actual rendered pixels: "+changed);
                Files.writeString(Path.of("../build/client-smoke/binding-glint.txt"),"Actual glint changed "+changed+" corresponding wand pixels with astral overlay disabled.\n");
            }finally{com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(opacity);}
            mc.setScreen(null);ready=false;next(13);
            mc.getSingleplayerServer().execute(()->{try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                RuneProgramming.cancel(p.getMainHandItem());RuneSurfaces.remove(p.serverLevel(),SOURCE,Direction.SOUTH);
                var face=RuneSurfaces.getOrCreate(p.serverLevel(),SOURCE,Direction.SOUTH);
                rune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH).id();
                secondRune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PULL),RuneLayer.Mode.PULL).id();
                var cells=RuneLayout.placed(face);
                sourceHit=new BlockHitResult(cells.get(0).center(),Direction.SOUTH,SOURCE,false);
                secondHit=new BlockHitResult(cells.get(1).center(),Direction.SOUTH,SOURCE,false);
                p.inventoryMenu.broadcastChanges();ready=true;
            }catch(Throwable e){failure=e.toString();}});
        }else if(phase==13&&ready&&ticks>15){
            click(true,new BlockHitResult(TARGET.getCenter().add(0,0,.5),Direction.SOUTH,TARGET,false));next(14);
        }else if(phase==14&&ticks>15){
            checkContainerSelection();click(true,sourceHit);next(15);
        }else if(phase==15&&ticks>15){
            checkContainerSelection();verifyReverse(rune,true);click(true,secondHit);next(16);
        }else if(phase==16&&ticks>15){
            checkContainerSelection();verifyReverse(secondRune,true);click(true,sourceHit);next(17);
        }else if(phase==17&&ticks>15){
            checkContainerSelection();verifyReverse(rune,false);
            mc.player.input.shiftKeyDown=true;mc.player.setXRot(-90);
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),-90,true));
            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);next(18);
        }else if(phase==18&&ticks>15){
            check(RuneProgramming.selection(mc.player.getMainHandItem())==null,"Air cancellation did not clear container-first binding");
            click(true,sourceHit);next(19);
        }else if(phase==19&&ticks>15){
            check(rune.equals(RuneProgramming.selection(mc.player.getMainHandItem()).layer()),"Rune-first mode did not start");
            click(true,secondHit);next(20);
        }else if(phase==20&&ticks>15){
            check(secondRune.equals(RuneProgramming.selection(mc.player.getMainHandItem()).layer()),"Shift-click did not switch selected rune");
            verifyReverse(rune,false);verifyReverse(secondRune,true);next(21);
        }else if(phase==21&&ticks>15){
            check(mc.screen==null&&!mc.mouseHandler.isMouseGrabbed(),"Reverse binding opened UI or grabbed mouse");
            Files.writeString(Path.of("../build/client-smoke/result.txt"),"PASS: actual client packets synchronize rune-first and container-first binding, sticky selection while assigning/toggling multiple runes, air cancellation, switching selected runes, relay whole-network targets, and the rendered binding glint.\n");return true;
        }
        return false;
    }
    private static void checkContainerSelection(){
        var mc=Minecraft.getInstance();var selected=RuneProgramming.selection(mc.player.getMainHandItem());
        check(selected!=null&&selected.layer()==null&&selected.address().position().pos().equals(TARGET)
                &&mc.player.getMainHandItem().hasFoil()&&mc.screen==null,"Container-first selection switched or disappeared");
    }
    private static void verifyReverse(UUID id,boolean expected){
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var face=RuneSurfaces.get(p.serverLevel(),SOURCE,Direction.SOUTH);
            check(face.layers().size()==2,"Reverse binding placed an extra rune");
            check(face.get(id).targets().contains(new RuneLayer.Target(GlobalPos.of(p.level().dimension(),TARGET),Direction.SOUTH))==expected,"Reverse binding target did not synchronize");
        }catch(Throwable e){failure=e.toString();}});
    }
    private static void click(boolean shift,BlockHitResult hit){
        var mc=Minecraft.getInstance();mc.player.input.shiftKeyDown=shift;
        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,shift?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        check(mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Client block click passed through");
    }
    private static void verifyTarget(boolean expected){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var face=RuneSurfaces.get(p.serverLevel(),SOURCE,Direction.SOUTH);check(!face.get(rune).targets().isEmpty()==expected,"Target state did not reach server in phase "+phase+"; selection="+RuneProgramming.selection(p.getMainHandItem()));check(face.layers().size()==1,"Binding placed an extra rune");}catch(Throwable e){failure=e.toString();}});}
    private static void verifyRelay(boolean expected){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var layer=RuneSurfaces.get(p.serverLevel(),SOURCE,Direction.SOUTH).get(rune);check(layer.targets().contains(new RuneLayer.Target(GlobalPos.of(p.level().dimension(),RELAY),null))==expected,"Relay whole-network assignment failed; targets="+layer.targets());}catch(Throwable e){failure=e.toString();}});}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void next(int value){phase=value;ticks=0;}
}
