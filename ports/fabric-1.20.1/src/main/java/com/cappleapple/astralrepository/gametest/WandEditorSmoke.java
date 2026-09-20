package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

public final class WandEditorSmoke {
    private static int phase,ticks;
    private static RunePreset saved;
    private static UUID fullColorId;
    private static volatile String failure;
    private static volatile boolean serverDone;
    private static final Path OUT=Path.of("../build/client-smoke/wand");
    public static boolean tick()throws Exception {
        var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();if(failure!=null)throw new AssertionError(failure);if(++ticks>1600)throw new AssertionError("Wand smoke timeout "+phase);
        int left=(mc.getWindow().getGuiScaledWidth()-420)/2,top=(mc.getWindow().getGuiScaledHeight()-290)/2;
        if(phase==0){phase=1;mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AstralContent.ATTUNEMENT_WAND.get()));WandPackets.open(p);}catch(Throwable t){failure=t.toString();}});}
        else if(phase==1&&mc.screen instanceof WandScreen screen&&screen.snapshot()!=null){check(!screen.editing(),"Initial wand view must be the preset grid");shot(mc,"library.png");
            screen.mouseClicked(left+42,top+62,1);check(screen.editing(),"Right click must edit the preset");screen.onClose();check(!screen.editing(),"Editor returns to the library");
            int plus=screen.presetCount();screen.mouseClicked(left+42+(plus%8)*48,top+62+(plus/8)*48,0);check(screen.editing(),"Plus tile creates a new preset");var name=(EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getX()==left+120).findFirst().orElseThrow();name.setValue("Smoke painted rune");screen.mouseClicked(left+128,top+90,0);screen.mouseDragged(left+240,top+203,0,112,113);screen.mouseReleased(left+240,top+203,0);screen.flush();saved=screen.snapshot();check(saved.design().pixel(2,2)!=0||Arrays.stream(new int[]{saved.design().pixel(1,2),saved.design().pixel(2,3)}).anyMatch(v->v!=0),"Brush failed to paint");click(screen,left+312,top+17);check(screen.colorPicker()!=null,"Color button did not open the wheel");screen.colorPicker().hexField().setValue("#12ABCD");check(screen.colorPicker().color()==0xff12abcd,"Hex field rounded the chosen RGB");phase=10;ticks=0;}
        else if(phase==10&&ticks>8&&mc.screen instanceof WandScreen screen){shot(mc,"color_wheel.png");screen.colorPicker().onClose();screen.mouseClicked(left+131,top+91,0);screen.mouseReleased(left+131,top+91,0);screen.flush();check(screen.snapshot().design().argb(2,2)==0xff12abcd,"Painting did not preserve the exact hex color");saved=screen.snapshot();phase=2;ticks=0;}
        else if(phase==2&&ticks>10&&mc.screen instanceof WandScreen screen){var search=(EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getX()==left+278&&e.getY()==top+55).findFirst().orElseThrow();search.setValue("diamond");click(screen,left+285,top+85);check(screen.snapshot().design().icons().size()==1,"Item search did not add a separate overlay");screen.mouseClicked(left+185,top+148,0);screen.mouseDragged(left+192,top+151,0,7,3);screen.mouseReleased(left+192,top+151,0);screen.mouseScrolled(left+192,top+151,0,2);screen.flush();saved=screen.snapshot();check(saved.design().icons().get(0).rotation()==1,"Overlay rotation was not saved");phase=3;ticks=0;}
        else if(phase==3&&ticks>10&&mc.screen instanceof WandScreen screen){var tex=(net.minecraft.client.renderer.texture.DynamicTexture)mc.getTextureManager().getTexture(RuneDesignRenderer.texture(saved.design(),32));check((tex.getPixels().getPixelRGBA(31,0)>>>24)==0,"Composite lost transparent background");shot(mc,"artwork.png");click(screen,left+375,top+40);screen.acceptItem(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));screen.flush();saved=screen.snapshot();phase=4;ticks=0;}
        else if(phase==4&&ticks>10&&mc.screen instanceof WandScreen screen){shot(mc,"rules.png");var copy=RunePresetFiles.decode(RunePresetFiles.encode(saved),mc.level.registryAccess());check(Arrays.equals(copy.design().pixels(),saved.design().pixels())&&copy.design().icons().equals(saved.design().icons()),"Portable file lost pixels or transforms");check(copy.filter().equals(saved.filter()),"Portable file lost behavior");check(Arrays.equals(copy.design().argbPixels(),saved.design().argbPixels()),"Portable file lost exact RGB");
            int[] noise=new int[128*128];for(int i=0;i<noise.length;i++)noise[i]=0xff000000|(i*7919)&0xffffff;fullColorId=UUID.randomUUID();var large=new RunePreset(fullColorId,"128 RGB transport",RuneLayer.Mode.FILTER,new RuneDesign(128,noise,List.of()),saved.filter(),0,true);var field=WandScreen.class.getDeclaredField("session");field.setAccessible(true);com.cappleapple.astralrepository.platform.PacketDistributor.sendToServer(new WandPackets.Action((UUID)field.get(screen),WandPackets.Op.IMPORT,fullColorId,large.save()));com.cappleapple.astralrepository.AstralServerConfig.runeResolution.set(16);phase=5;ticks=0;}
        else if(phase==5&&ticks>8&&mc.screen instanceof WandScreen screen){shot(mc,"cropped.png");check(Arrays.equals(saved.design().pixels(),screen.snapshot().design().pixels()),"Cropping destroyed hidden pixels");com.cappleapple.astralrepository.AstralServerConfig.runeResolution.set(32);click(screen,left+180,top+270);phase=6;ticks=0;}
        else if(phase==6&&ticks>10){phase=7;mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var large=RuneLibraryData.get(p.getServer()).library(p.getUUID()).get(fullColorId);check(large!=null&&large.design().argb(127,127)==(0xff000000|(16383*7919)&0xffffff),"Largest full-RGB preset did not survive actual serverbound transport");var preset=WandPackets.preset(p,p.getMainHandItem());check(preset!=null&&preset.id().equals(saved.id()),"Preset selection failed on server");check(preset.design().icons().equals(saved.design().icons())&&preset.filter().equals(saved.filter()),"Server preset lost artwork or rules");BlockPos host=new BlockPos(3,-59,0);RuneSurfaces.remove(p.serverLevel(),host,Direction.SOUTH);var hit=new BlockHitResult(host.getCenter().add(.13,-.17,.4375),Direction.SOUTH,host,false);RuneProgramming.use(p.getMainHandItem(),p.level(),host,p,InteractionHand.MAIN_HAND,hit);RuneProgramming.use(p.getMainHandItem(),p.level(),host,p,InteractionHand.MAIN_HAND,new BlockHitResult(hit.getLocation().add(-.35,0,0),Direction.SOUTH,host,false));var surface=RuneSurfaces.get(p.serverLevel(),host,Direction.SOUTH);check(surface!=null&&surface.layers().size()==2,"Wand did not place both independent copies");var cells=RuneLayout.placed(surface);check(cells.get(0).center().distanceTo(cells.get(1).center())>=cells.get(0).size(),"Closest placement overlapped");var frozen=surface.layers().get(0);RuneLibraryData.get(p.getServer()).library(p.getUUID()).remove(saved.id());check(frozen.design().key().equals(saved.design().key())&&frozen.filter().save(p.registryAccess()).equals(saved.filter()),"Deleting the preset changed placed behavior");var aim=cells.get(0).center();p.teleportTo(aim.x,aim.y-p.getEyeHeight(),aim.z+2.5);p.setYRot(180);p.setYHeadRot(180);p.setXRot(0);serverDone=true;}catch(Throwable t){failure=t.toString();}});}
        else if(phase==7&&serverDone&&ticks>30){check(RuneRenderer.renderedGlyphs()>=2,"Custom placed designs did not render");shot(mc,"placed.png");phase=8;ticks=0;mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);var surface=RuneSurfaces.get(p.serverLevel(),new BlockPos(3,-59,0),Direction.SOUTH);p.setShiftKeyDown(false);var cell=RuneLayout.placed(surface).get(0);RuneProgramming.use(p.getMainHandItem(),p.level(),surface.getBlockPos(),p,InteractionHand.MAIN_HAND,new BlockHitResult(cell.center(),surface.facing(),surface.getBlockPos(),false));}catch(Throwable t){failure=t.toString();}});}
        else if(phase==8&&ticks>10&&mc.screen instanceof RuneSettingsScreen screen){
            shot(mc,"placed_settings.png");var priority=(EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getMessage().getString().equals("Priority")).findFirst().orElseThrow();
            screen.mouseScrolled(priority.getX()+2,priority.getY()+2,0,4);check(priority.getValue().equals("1"),"Rune numerical wheel uses one step per event");priority.setValue("13");phase=81;ticks=0;
        }
        else if(phase==81&&ticks>15&&mc.screen instanceof RuneSettingsScreen screen&&!screen.pendingChanges()){
            ((net.minecraft.client.gui.components.Button)screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("Save Preset")).findFirst().orElseThrow()).onPress();phase=9;ticks=0;
        }
        else if(phase==9&&ticks>15&&mc.screen instanceof WandScreen screen&&screen.editing()){
            var imported=screen.snapshot();check(!imported.id().equals(saved.id())&&imported.priority()==13&&imported.filter().equals(saved.filter()),"Save Preset must preserve placed behavior under a new identity");
            check(imported.design().key().equals(saved.design().key()),"Saved placed preset must retain its custom painted artwork and item overlays");
            var file=mc.gameDirectory.toPath().resolve("astral_repository/runes/"+mc.getUser().getProfileId()).resolve(imported.id()+".json");
            var portable=RunePresetFiles.decode(Files.readString(file),mc.level.registryAccess());
            check(portable.design().key().equals(saved.design().key())&&portable.filter().equals(imported.filter()),"Actual personal preset JSON must contain the original custom artwork and behavior");
            screen.mouseClicked(left+150,top+62,1);check(screen.snapshot().mode()==RuneLayer.Mode.FILTER,"Right-click cycles backward to Filter");
            screen.mouseScrolled(left+150,top+62,0,1);check(screen.snapshot().mode()==RuneLayer.Mode.PUSH,"Wheel cycles forward to Push");
            shot(mc,"saved_preset.png");screen.flush();
            Files.writeString(OUT.resolve("result.txt"),"PASS: actual full-RGB color wheel and hex input, 128px serverbound artwork, emissive rune renderer, preset grid, right-click editing, plus tile, paint and item layers, one-step rotation, any-match rules, portable JSON, crop preservation, independent placement, numeric scrolling, save placed preset without held wand, preserved custom artwork and overlays in the actual personal JSON, both mode-cycle directions.\n");return true;
        }
        return false;
    }
    private static void click(WandScreen screen,int x,int y){screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);}
    private static void shot(Minecraft mc,String name)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
