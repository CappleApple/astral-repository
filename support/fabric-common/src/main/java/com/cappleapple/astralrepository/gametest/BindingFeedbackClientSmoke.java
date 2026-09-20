package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

/** Actual world damage rendering, server preview packets, and selected-hotbar gating. */
public final class BindingFeedbackClientSmoke {
    private static final BlockPos MINERAL=new BlockPos(26,-59,14),SOURCE=new BlockPos(24,-59,14),TARGET=new BlockPos(28,-59,14),AIMED=new BlockPos(26,-59,12),CANCEL=new BlockPos(27,-59,17);
    private static int phase,ticks,block;
    private static volatile boolean ready;
    private static volatile String failure;
    private static NativeImage baseline;
    private static double opacity;
    private static long particles;
    private static final List<Integer> differences=new ArrayList<>();
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError(failure);if(++ticks>600)throw new AssertionError("Feedback client timeout "+phase);
        if(phase==0){phase=1;opacity=com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.get();com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(0.0);server(()->{
            var p=player();p.closeContainer();p.serverLevel().getChunkAt(MINERAL);p.serverLevel().setBlockAndUpdate(MINERAL,AstralContent.ASTRAL_GEODE.get().defaultBlockState());p.connection.teleport(26.5,-59,18,180,18);ready=true;
        });}
        else if(phase==1&&ready&&ticks>35){check(AstralPlaneRenderType.ASTRAL_PLANE.affectsCrumbling(),"Mineral material rejects mining cracks");baseline=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget());baseline.writeToFile(out("mineral_"+block+"_intact.png"));server(()->player().serverLevel().destroyBlockProgress(9876,MINERAL,8));next(2);}
        else if(phase==2&&ticks>15){
            try(var broken=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                int changed=0,w=broken.getWidth(),h=broken.getHeight();for(int y=h/2-90;y<h/2+90;y++)for(int x=w/2-90;x<w/2+90;x++){int a=baseline.getPixelRGBA(x,y),b=broken.getPixelRGBA(x,y);if((a&255)-(b&255)>25&&((a>>8)&255)-((b>>8)&255)>25)changed++;}
                broken.writeToFile(out("mineral_"+block+"_breaking.png"));check(changed>100,"No visible damage cracks for mineral "+block+": "+changed);differences.add(changed);
            }baseline.close();baseline=null;server(()->player().serverLevel().destroyBlockProgress(9876,MINERAL,-1));block++;
            if(block<3){server(()->player().serverLevel().setBlockAndUpdate(MINERAL,(block==1?AstralContent.BUDDING_ASTRAL.get():AstralContent.ASTRAL_CLUSTER.get()).defaultBlockState()));next(1);}
            else{com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(opacity);next(3);server(()->{
                var p=player();var level=p.serverLevel();level.removeBlock(MINERAL,false);for(var pos:List.of(SOURCE,TARGET,AIMED))level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());level.setBlockAndUpdate(CANCEL,Blocks.STONE.defaultBlockState());
                var face=RuneSurfaces.getOrCreate(level,SOURCE,Direction.SOUTH);var rune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);int[] pixels=new int[32*32];Arrays.fill(pixels,0xfff044cc);
                rune.preset(new RunePreset(UUID.randomUUID(),"Pink",RuneLayer.Mode.PUSH,new RuneDesign(32,pixels,List.of()),new net.minecraft.nbt.CompoundTag(),0,true),level.registryAccess());
                face.toggleTarget(rune.id(),GlobalPos.of(level.dimension(),TARGET),Direction.SOUTH);
                var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,wand);RuneProgramming.link(wand,level,new RuneProgramming.Selection(face.address(),rune.id()),p);
                p.connection.teleport(26.5,-59,18,180,10);p.inventoryMenu.broadcastChanges();
            });}
        }
        else if(phase==3&&ticks>35){
            mc.player.getInventory().selected=0;var state=BindingPreviewRenderer.active();check(state.rune()!=null&&state.paths().size()==2&&state.color()==0xf044cc,"Assigned and aimed routes not delivered with artwork color: "+state);
            check(BindingPreviewRenderer.renderedSegments>0&&BindingPreviewRenderer.emittedParticles==0&&state.particleOrigin()==null,"Bound rune must render beams without particles");shot("binding_beams.png");
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                int colored=0;for(int y=350;y<530;y++)for(int x=350;x<720;x++){int pixel=image.getPixelRGBA(x,y);int red=pixel&255,green=pixel>>8&255,blue=pixel>>16&255;if(red>100&&red>green*1.4&&blue>green*1.4)colored++;}
                check(colored>100,"Beam geometry did not produce visible rune-colored pixels: "+colored);
            }
            BindingBeamRenderType.BEAM.setupRenderState();
            try{check(!org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK)&&org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST),"Beam must read world depth without writing depth between its glow and core");}finally{BindingBeamRenderType.BEAM.clearRenderState();}
            server(()->{var face=RuneSurfaces.get(player().serverLevel(),SOURCE,Direction.SOUTH);face.clearTarget(face.layers().getFirst().id());});next(7);
        }
        else if(phase==7&&ticks>20){
            var state=BindingPreviewRenderer.active();check(state.particleOrigin()!=null&&state.particleOrigin().distanceToSqr(SOURCE.getCenter())<1&&state.particleFace()==Direction.SOUTH&&BindingPreviewRenderer.emittedParticles>0,"Unbound particles do not originate at the selected rune");
            shot("unbound_rune_particles.png");particles=BindingPreviewRenderer.emittedParticles;mc.player.getInventory().selected=1;mc.gameMode.tick();next(4);
        }
        else if(phase==4&&ticks>10){check(BindingPreviewRenderer.active().rune()==null&&BindingPreviewRenderer.renderedSegments==0&&BindingPreviewRenderer.emittedParticles==particles,"Inactive hotbar wand kept emitting feedback");mc.player.getInventory().selected=0;mc.gameMode.tick();next(5);}
        else if(phase==5&&ticks>15){check(BindingPreviewRenderer.active().rune()!=null&&BindingPreviewRenderer.emittedParticles>particles,"Reselecting the unbound rune did not restore particles");
            server(()->{var p=player();var face=RuneSurfaces.get(p.serverLevel(),SOURCE,Direction.SOUTH);face.toggleTarget(face.layers().getFirst().id(),GlobalPos.of(p.level().dimension(),TARGET),Direction.SOUTH);p.connection.teleport(25,-59,17,-135,12);});next(8);
        }
        else if(phase==8&&ticks>20){check(BindingPreviewRenderer.active().particleOrigin()==null,"Assigning first target did not stop particles");particles=BindingPreviewRenderer.emittedParticles;next(9);}
        else if(phase==9&&ticks>10){check(BindingPreviewRenderer.emittedParticles==particles,"Bound rune continued emitting particles");shot("binding_beams_oblique.png");
            mc.player.input.shiftKeyDown=true;mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(CANCEL.getCenter().add(0,.5,0),Direction.UP,CANCEL,false));next(6);
        }
        else if(phase==6&&ticks>15){check(RuneProgramming.selection(mc.player.getMainHandItem())==null&&BindingPreviewRenderer.active().rune()==null&&!mc.player.getMainHandItem().hasFoil(),"Shift-click solid block did not stop binding feedback");server(()->{var p=player();var face=RuneSurfaces.get(p.serverLevel(),SOURCE,Direction.SOUTH);RuneProgramming.link(p.getMainHandItem(),p.serverLevel(),new RuneProgramming.Selection(face.address(),face.layers().getFirst().id()),p);p.connection.teleport(25,-59,17,-55,-90);p.inventoryMenu.broadcastChanges();});next(10);}
        else if(phase==10&&ticks>15){check(RuneProgramming.selection(mc.player.getMainHandItem())!=null,"Air-cancel fixture did not reselect rune");mc.player.input.shiftKeyDown=true;mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);next(11);}
        else if(phase==11&&ticks>15){check(RuneProgramming.selection(mc.player.getMainHandItem())==null&&BindingPreviewRenderer.active().rune()==null&&mc.screen==null,"Shift-use in air failed to cancel binding or opened the library");check(!mc.mouseHandler.isMouseGrabbed(),"Mouse captured");Files.writeString(out("binding-feedback.txt"),"Crack pixel changes (gem, budding, cluster): "+differences+"; beams render without depth writes; particles originate at unbound rune, stop on assignment, resume after last target removal, and obey hotbar selection.\n");Files.writeString(out("result.txt"),"PASS: actual mining-crack rendering for gem/budding/cluster, custom-color beams with depth-write suppression, unbound-rune particles, assignment transitions and hotbar gating, and non-container/air cancellation.\n");return true;}
        return false;
    }
    private static net.minecraft.server.level.ServerPlayer player(){return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    private static void server(Runnable action){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable t){failure=t.toString();}});}
    private static Path out(String name){return Path.of("../build/client-smoke/"+name);}
    private static void shot(String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out(name));}}
    private static void next(int n){phase=n;ticks=0;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
