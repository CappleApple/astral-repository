package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralServerConfig;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class EditorInteractionGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void eightRunesFitPersistAndSynchronizeWithConfigurableLimit(GameTestHelper h){
        var pos=new BlockPos(3,2,3);h.setBlock(pos,Blocks.CHEST);var world=h.getLevel();
        var player=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"eight-runes"));
        var preset=RunePreset.initial(RuneLayer.Mode.PUSH);RuneLibraryData.get(world.getServer()).library(player.getUUID()).put(preset.id(),preset);
        var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());WandPackets.selection(wand,preset.id(),true);player.setItemInHand(InteractionHand.MAIN_HAND,wand);
        int previous=AstralServerConfig.maxRunesPerFace.get();double previousSize=AstralServerConfig.runeSize.get();
        try{
            AstralServerConfig.maxRunesPerFace.set(8);AstralServerConfig.runeSize.set(.20);
            for(int i=0;i<8;i++){
                var hit=new BlockHitResult(RuneLayout.frame(world,h.absolutePos(pos),Direction.SOUTH).center().center().add((i%4-1.5)*.21,(i/4==0?.11:-.11),0),Direction.SOUTH,h.absolutePos(pos),false);
                RuneProgramming.use(wand,world,hit.getBlockPos(),player,InteractionHand.MAIN_HAND,hit);
                var placed=RuneSurfaces.get(world,h.absolutePos(pos),Direction.SOUTH);h.assertTrue(placed!=null&&placed.layers().size()==i+1,"Placement "+i+" has "+(placed==null?0:placed.layers().size())+" runes; size="+AstralServerConfig.runeSize.get()+" frame="+RuneLayout.frame(world,h.absolutePos(pos),Direction.SOUTH)+" cells="+(placed==null?"none":RuneLayout.placed(placed)));
            }
            var surface=RuneSurfaces.get(world,h.absolutePos(pos),Direction.SOUTH);
            h.assertTrue(surface.layers().size()==8,"Eight independently placed runes fit one chest side");
            h.assertTrue(surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH)==null,"Ninth rune respects server limit");
            var cells=RuneLayout.placed(surface);h.assertTrue(cells.size()==8,"Renderer has eight cells");
            for(int i=0;i<8;i++)h.assertTrue(RuneLayout.selectedIndex(cells,cells.get(i).center())==i,"Every rune has an independent click target");
            var packet=new RunePackets.Faces(List.of(new RunePackets.Face(surface.getBlockPos(),surface.facing(),-1,surface.layers().stream().map(l->RunePackets.snapshot(surface,l)).toList())));
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),world.registryAccess());
            try{RunePackets.Faces.CODEC.encode(buffer,packet);h.assertTrue(RunePackets.Faces.CODEC.decode(buffer).faces().get(0).layers().size()==8,"Eight runes survive actual packet codec");}finally{buffer.release();}
            AstralServerConfig.maxRunesPerFace.set(2);
            var loaded=RuneSurface.load(world.getServer(),surface.save(world.registryAccess()),world.registryAccess());
            h.assertTrue(loaded.layers().size()==8&&loaded.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH)==null,"Lowering limit preserves existing saved runes and rejects additions");
            AstralServerConfig.maxRunesPerFace.set(12);
            h.assertTrue(surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH)!=null,"Raising configured limit allows more layers");
        }finally{AstralServerConfig.maxRunesPerFace.set(previous);AstralServerConfig.runeSize.set(previousSize);}
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void shiftWandSelectsBindableEndpointsBeforePlacementOrLibrary(GameTestHelper h){
        var pos=new BlockPos(3,2,3);var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"binding-input"));
        var preset=RunePreset.initial(RuneLayer.Mode.PUSH);RuneLibraryData.get(h.getLevel().getServer()).library(player.getUUID()).put(preset.id(),preset);
        var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());player.setItemInHand(InteractionHand.MAIN_HAND,wand);player.setShiftKeyDown(true);
        for(var block:List.of(Blocks.CHEST,AstralContent.RELAY_CRYSTAL.get(),AstralContent.STORAGE_NEXUS.get(),AstralContent.SEED_STORAGE_CRYSTAL.get())){
            h.setBlock(pos,block);WandPackets.selection(wand,preset.id(),true);
            var hit=new BlockHitResult(h.absolutePos(pos).getCenter().add(0,0,.4375),Direction.SOUTH,h.absolutePos(pos),false);
            var event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,hit.getBlockPos(),hit);
            RuneProgramming.interact(event);
            h.assertTrue(event.isCanceled()&&RuneProgramming.selection(wand)!=null&&!WandPackets.placing(wand)&&wand.hasFoil(),"Shift enters binding and glints on "+block);
            h.assertTrue(!player.getPersistentData().hasUUID("AstralWandSession"),"Shift did not open the library");
            h.assertTrue(RuneSurfaces.get(h.getLevel(),h.absolutePos(pos),Direction.SOUTH)==null,"Shift did not place a rune");
        }
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void targetClicksBindWithShiftHeldOrReleased(GameTestHelper h){
        var source=new BlockPos(2,2,2);var target=source.east(3);var relay=source.south(3);var nexus=relay.east(3);
        h.setBlock(source,Blocks.BARREL);h.setBlock(target,Blocks.BARREL);h.setBlock(relay,AstralContent.RELAY_CRYSTAL.get());h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());
        var world=h.getLevel();var player=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"binding-completion"));
        var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());player.setItemInHand(InteractionHand.MAIN_HAND,wand);
        var surface=RuneSurfaces.getOrCreate(world,h.absolutePos(source),Direction.SOUTH);var layer=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        var glyph=new BlockHitResult(RuneLayout.placed(surface).get(0).center(),Direction.SOUTH,h.absolutePos(source),false);
        var destination=new BlockHitResult(h.absolutePos(target).getCenter().add(0,0,.5),Direction.SOUTH,h.absolutePos(target),false);
        for(boolean holdShift:new boolean[]{false,true}){
            player.setShiftKeyDown(true);player.gameMode.useItemOn(player,world,wand,InteractionHand.MAIN_HAND,glyph);
            player.setShiftKeyDown(holdShift);player.gameMode.useItemOn(player,world,wand,InteractionHand.MAIN_HAND,destination);
            h.assertTrue(layer.targets().size()==1&&layer.id().equals(RuneProgramming.selection(wand).layer()),"Target click binds without losing the selected rune; Shift="+holdShift);
            player.gameMode.useItemOn(player,world,wand,InteractionHand.MAIN_HAND,destination);
            h.assertTrue(layer.targets().isEmpty()&&surface.layers().size()==1,"Repeated target toggles assignment without placing another rune");
        }
        for(boolean held:new boolean[]{false,true}){
            player.setShiftKeyDown(true);player.gameMode.useItemOn(player,world,wand,InteractionHand.MAIN_HAND,glyph);
            player.setShiftKeyDown(held);
            var crystalHit=new BlockHitResult(h.absolutePos(relay).getCenter(),Direction.UP,h.absolutePos(relay),false);
            player.gameMode.useItemOn(player,world,wand,InteractionHand.MAIN_HAND,crystalHit);
            h.assertTrue(layer.targets().size()==1&&layer.target().face()==null&&layer.target().position().pos().equals(h.absolutePos(relay)),"Relay binding addresses the entire network, not a clicked inventory face");
            player.gameMode.useItemOn(player,world,wand,InteractionHand.MAIN_HAND,crystalHit);
            h.assertTrue(layer.targets().isEmpty(),"Relay target can be toggled off");
        }
        RuneProgramming.cancel(wand);player.setShiftKeyDown(true);
        var a=new BlockHitResult(h.absolutePos(relay).getCenter(),Direction.UP,h.absolutePos(relay),false);
        var b=new BlockHitResult(h.absolutePos(nexus).getCenter(),Direction.UP,h.absolutePos(nexus),false);
        player.gameMode.useItemOn(player,world,wand,InteractionHand.MAIN_HAND,a);player.gameMode.useItemOn(player,world,wand,InteractionHand.MAIN_HAND,b);
        h.assertTrue(RuneSavedData.get(world.getServer()).linked(new AnchorAddress(GlobalPos.of(world.dimension(),h.absolutePos(relay)),null),new AnchorAddress(GlobalPos.of(world.dimension(),h.absolutePos(nexus)),null))&&RuneProgramming.selection(wand)==null,"Held Shift completes and clears a crystal link");
        h.succeed();
    }

}
