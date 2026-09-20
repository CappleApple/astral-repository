package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import java.nio.file.*;
import java.util.*;

/** Real client rendering and session packets; no mouse capture, audio, or foreground window. */
public final class PresentationClientSmoke {
    private static int phase,ticks;private static volatile String failure;private static volatile boolean ready,complete;
    private static final BlockPos HOST=new BlockPos(24,-59,14),TABLE=new BlockPos(12,-59,14),NEXUS=new BlockPos(14,-59,14),NEAR=new BlockPos(14,-59,12),FAR=new BlockPos(28,-59,14);
    private static double opacity;private static long staged=-1,craft=-1;private static boolean partialShot;private static int populated;
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError(failure);if(++ticks>1500)throw new AssertionError("Presentation timeout "+phase);
        if(phase==0){opacity=com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.get();com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(.18);next(1);server(()->{var p=player();p.closeContainer();p.serverLevel().setBlockAndUpdate(HOST,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());var face=RuneSurfaces.getOrCreate(p.serverLevel(),HOST,Direction.SOUTH);var rune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);rune.setEnabled(false);CompatibilityRegistry.registerResources("client_source_fixture",(level,pos,side)->pos.equals(HOST)?List.of(RuneCadenceGameTests.scalar("client_source",new long[]{100},0)):List.of());p.getInventory().setItem(0,new ItemStack(AstralContent.ASTRAL_GEM.get()));p.getInventory().selected=0;p.connection.teleport(24.5,-59,17,180,0);p.inventoryMenu.broadcastChanges();});}
        else if(phase==1&&ticks>30){mc.player.getInventory().selected=0;shot("held_gem_overlay.png");com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(0.0);next(2);}
        else if(phase==2&&ticks>10){shot("held_gem_base.png");com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(1.0);next(3);}
        else if(phase==3&&ticks>10){shot("held_gem_full_overlay.png");com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(opacity);server(()->{var p=player();var face=RuneSurfaces.get(p.serverLevel(),HOST,Direction.SOUTH);RuneSettingsPackets.open(p,face,face.layers().getFirst());});next(4);}
        else if(phase==4&&ticks>15&&mc.screen instanceof RuneSettingsScreen s&&s.currentPage().resources()==15){check(s.children().stream().noneMatch(w->w instanceof Button b&&b.getMessage().getString().equals("Clear filter")),"Clear filter still shown");shot("rune_priority_layout.png");s.openCadence();next(5);}
        else if(phase==5&&ticks>10&&mc.screen instanceof RuneSettingsScreen s){shot("rune_cadence_all_resources.png");var fields=s.children().stream().filter(w->w instanceof EditBox e&&e.getMessage().getString().equals("Transfer cadence")).map(w->(EditBox)w).toList();check(fields.size()==8,"Popup must fit all four amount/interval pairs");for(int i=0;i<4;i++){fields.get(i*2).setValue(Integer.toString(7+i));fields.get(i*2+1).setValue(Integer.toString(9+i));}next(6);}
        else if(phase==6&&ticks>15&&mc.screen instanceof RuneSettingsScreen s&&!s.pendingChanges()){for(var kind:RuneCadence.Kind.values())check(s.currentPage().cadence().overrides().get(kind).equals(new RuneCadence.Rate(7+kind.ordinal(),9+kind.ordinal())),"Cadence did not autosave "+kind);press(s,"Server defaults");next(7);}
        else if(phase==7&&ticks>15&&mc.screen instanceof RuneSettingsScreen s&&!s.pendingChanges()){check(s.currentPage().cadence().equals(s.currentPage().defaults()),"Defaults button did not restore server values");s.onClose();s.apply();next(8);}
        else if(phase==8&&ticks>15&&mc.screen==null){server(()->{player().getInventory().setItem(0,ItemStack.EMPTY);player().inventoryMenu.broadcastChanges();player().connection.teleport(26.5,-58,20,180,12);});next(9);}
        else if(phase==9&&ticks>20){check(mc.getResourceManager().getResource(net.minecraft.resources.Identifier.withDefaultNamespace("textures/particle/glow.png")).isPresent(),"Missing energy sprite");for(int i=0;i<3;i++){var from=new BlockPos(24,-58+i,14);var to=from.east(5);WorldVisuals.add(new NetworkPackets.Visual(from,to,ItemStack.EMPTY,0x8844ff,100,-2-i,List.of(from,to),null,null,i==0?net.minecraft.resources.Identifier.withDefaultNamespace("water"):null));}next(10);}
        else if(phase==10&&ticks>35){checkResourceTrails(mc);shot("flat_resource_transfers.png");next(11);server(()->{
            var p=player();var level=p.serverLevel();level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());((CrystalNodeBlockEntity)level.getBlockEntity(NEXUS)).setChannel(9);
            var relay=new BlockPos(25,-59,14);level.setBlockAndUpdate(relay,AstralContent.RELAY_CRYSTAL.get().defaultBlockState());((CrystalNodeBlockEntity)level.getBlockEntity(relay)).setChannel(9);
            level.setBlockAndUpdate(TABLE,Blocks.CRAFTING_TABLE.defaultBlockState());for(var pos:List.of(NEAR,FAR)){level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());((Container)level.getBlockEntity(pos)).setItem(0,new ItemStack(Items.IRON_INGOT,2));}
            var shelf=NEXUS.south();level.setBlockAndUpdate(shelf,Blocks.CHISELED_BOOKSHELF.defaultBlockState());var tome=new ItemStack(AstralContent.RECIPE_TOME.get());var data=new net.minecraft.nbt.CompoundTag();data.put("Output",new ItemStack(Items.IRON_TRAPDOOR).save(level.registryAccess()));data.putString("OutputId","minecraft:iron_trapdoor");tome.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(data));((Container)level.getBlockEntity(shelf)).setItem(0,tome);
            p.connection.teleport(12.5,-56,20,180,35);ready=true;
        });}
        else if(phase==11&&ready&&ticks>140){check(resourceTrails().isEmpty(),"Expired resource trails retained");NetworkPackets.visualReceiver=p->{WorldVisuals.add(p);if(p.from().equals(TABLE)){if(p.slot()>=10&&!p.stack().isEmpty()){staged=mc.level.getGameTime();populated++;}if(p.slot()>=0&&p.slot()<9&&!p.stack().isEmpty())craft=mc.level.getGameTime();}};server(()->{var p=player();var n=NetworkManager.get(p.server).networkAt(GlobalPos.of(p.level().dimension(),NEXUS));check(n!=null,"Missing table network");check(n.crafting().request(p,new ItemStack(Items.IRON_TRAPDOOR),1).accepted(),"Table job rejected");});next(12);}
        else if(phase==12){
            if(staged>=0&&!partialShot&&mc.level.getGameTime()-staged>=3){check(craft<0&&populated==2,"Table began work before the farther pair arrived");shot("table_partial_arrival.png");partialShot=true;}
            if(ticks%10==0)server(()->{var p=player();var n=NetworkManager.get(p.server).networkAt(GlobalPos.of(p.level().dimension(),NEXUS));complete=n.crafting().statuses().stream().anyMatch(j->j.state().equals("COMPLETE"));});
            if(complete){check(partialShot&&craft>staged,"No progressive arrival followed by actual assembly");NetworkPackets.visualReceiver=WorldVisuals::add;check(!mc.mouseHandler.isMouseGrabbed(),"Mouse captured");Files.writeString(Path.of("../build/client-smoke/result.txt"),"PASS: held gem material renders at 0/18/100 percent; four-resource popup autosaves and restores server defaults; circular fluid/energy sprites with detached falling trails and expiry; actual two-source table recipe displays two arrived ingredients while waiting before assembly.\n");return true;}
        }
        return false;
    }
    private static List<?> resourceTrails()throws Exception{
        var field=WorldVisuals.class.getDeclaredField("resourceTrails");field.setAccessible(true);return (List<?>)field.get(null);
    }
    private static void checkResourceTrails(Minecraft mc)throws Exception{
        var trails=resourceTrails();check(trails.size()>=8&&trails.size()<=512,"Missing or unbounded resource trails");
        boolean fluid=false,energy=false,falling=false;
        for(var trail:trails){
            var type=trail.getClass();var packet=type.getDeclaredMethod("packet");packet.setAccessible(true);
            int slot=((NetworkPackets.Visual)packet.invoke(trail)).slot();fluid|=slot==-2;energy|=slot==-3;check(slot>=-4&&slot<=-2,"Unexpected resource trail");
            var position=type.getDeclaredMethod("position",double.class);position.setAccessible(true);
            var origin=type.getDeclaredMethod("origin");origin.setAccessible(true);
            var point=(net.minecraft.world.phys.Vec3)position.invoke(trail,(double)mc.level.getGameTime());
            falling|=point.y<((net.minecraft.world.phys.Vec3)origin.invoke(trail)).y-.04;
        }
        check(fluid&&energy&&falling,"Fluid/energy trails did not detach and fall");
    }
    private static net.minecraft.server.level.ServerPlayer player(){return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    private static void server(Runnable work){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{work.run();}catch(Throwable e){failure=e.toString();}});}
    private static void press(RuneSettingsScreen s,String text){var b=s.children().stream().filter(w->w instanceof Button v&&v.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow();s.mouseClicked(b.getX()+3,b.getY()+3,0);s.mouseReleased(b.getX()+3,b.getY()+3,0);}
    private static void shot(String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Path.of("../build/client-smoke/"+name));}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void next(int value){phase=value;ticks=0;}
}
