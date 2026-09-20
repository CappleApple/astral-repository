package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class BindingFeedbackGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void selectedWandPreviewsAssignedAndAimedRoutesAndCancelsOnNonContainers(GameTestHelper h){
        BlockPos source=new BlockPos(2,2,2),target=new BlockPos(6,2,2),aimed=new BlockPos(6,2,5),solid=new BlockPos(3,2,5),sign=new BlockPos(3,2,6);
        for(var pos:List.of(source,target,aimed))h.setBlock(pos,Blocks.BARREL);h.setBlock(solid,Blocks.STONE);h.setBlock(sign,Blocks.OAK_SIGN);
        var level=h.getLevel();var face=RuneSurfaces.getOrCreate(level,h.absolutePos(source),Direction.SOUTH);var rune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        rune.preset(new RunePreset(UUID.randomUUID(),"Test",RuneLayer.Mode.PUSH,new RuneDesign(2,new int[]{0xffff0000,0xff0000ff,0,0},List.of()),new net.minecraft.nbt.CompoundTag(),0,true),level.registryAccess());
        h.assertTrue(rune.design().averageColor(2)==0x7f007f&&rune.design().averageColor(1)==0xff0000,"Average ignores transparent and cropped pixels");
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"binding-feedback"));var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());player.setItemInHand(InteractionHand.MAIN_HAND,wand);
        var selection=new RuneProgramming.Selection(face.address(),rune.id());RuneProgramming.link(wand,level,selection,player);
        face.toggleTarget(rune.id(),GlobalPos.of(level.dimension(),h.absolutePos(target)),Direction.SOUTH);
        var eye=h.absolutePos(aimed).getCenter().add(0,0,3);player.setPos(eye.x,eye.y-player.getEyeHeight(),eye.z);player.setYRot(180);player.setXRot(0);
        var preview=BindingPreviewPackets.snapshot(player);h.assertTrue(preview.paths().size()==2&&preview.color()==0x7f007f,"Shows assigned path plus aimed-container preview");
        h.assertTrue(rune.targets().size()==1,"Aimed preview never assigns a target");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try{BindingPreviewPackets.Preview.CODEC.encode(buffer,preview);var decoded=BindingPreviewPackets.Preview.CODEC.decode(buffer);h.assertTrue(decoded.paths().size()==2&&decoded.rune().equals(rune.id())&&decoded.paths().getFirst().departure()!=null,"Bounded preview codec retains routes and rune departure");}finally{buffer.release();}
        h.assertTrue(preview.particleOrigin()==null,"Assigned rune never emits unbound particles");
        face.clearTarget(rune.id());var unbound=BindingPreviewPackets.snapshot(player);
        h.assertTrue(unbound.paths().size()==1&&unbound.particleOrigin().distanceToSqr(RuneLayout.placed(face).getFirst().center())<1e-10&&unbound.particleFace()==Direction.SOUTH,"Unassigned rune emits at its own glyph even while showing an aimed preview");
        var particleBuffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try{BindingPreviewPackets.Preview.CODEC.encode(particleBuffer,unbound);var decoded=BindingPreviewPackets.Preview.CODEC.decode(particleBuffer);h.assertTrue(decoded.particleOrigin().equals(unbound.particleOrigin())&&decoded.particleFace()==Direction.SOUTH,"Particle source survives packet encoding");}finally{particleBuffer.release();}
        face.toggleTarget(rune.id(),GlobalPos.of(level.dimension(),h.absolutePos(target)),Direction.SOUTH);
        h.setBlock(target,Blocks.AIR);h.assertTrue(BindingPreviewPackets.snapshot(player).particleOrigin()==null,"A missing target is still an assignment, not an unbound rune");
        player.getInventory().selected=1;h.assertTrue(BindingPreviewPackets.snapshot(player).rune()==null,"Inactive hotbar wand has no preview");player.getInventory().selected=0;
        for(var block:List.of(solid,sign)){
            RuneProgramming.cancel(wand);RuneProgramming.link(wand,level,selection,player);player.setShiftKeyDown(true);
            player.gameMode.useItemOn(player,level,wand,InteractionHand.MAIN_HAND,new BlockHitResult(h.absolutePos(block).getCenter(),Direction.UP,h.absolutePos(block),false));
            h.assertTrue(RuneProgramming.selection(wand)==null&&!wand.hasFoil(),"Shift-click cancels on ordinary blocks and non-container block entities");
        }
        RuneProgramming.link(wand,level,selection,player);player.setShiftKeyDown(true);player.setXRot(-90);
        wand.getItem().use(level,player,InteractionHand.MAIN_HAND);
        h.assertTrue(RuneProgramming.selection(wand)==null&&!wand.hasFoil(),"Shift-use in air cancels instead of opening the library");
        h.assertTrue(rune.targets().size()==1,"Canceling preserves assigned targets");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void previewUsesTheRelayRouteAndRuneFaceDeparture(GameTestHelper h){
        var source=new BlockPos(2,2,2);var target=new BlockPos(6,2,2);var relay=new BlockPos(4,2,4);
        h.setBlock(source,Blocks.BARREL);h.setBlock(target,Blocks.BARREL);h.setBlock(relay,AstralContent.RELAY_CRYSTAL.get());
        var level=h.getLevel();var face=RuneSurfaces.getOrCreate(level,h.absolutePos(source),Direction.SOUTH);var rune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PULL),RuneLayer.Mode.PULL);
        face.toggleTarget(rune.id(),GlobalPos.of(level.dimension(),h.absolutePos(target)),Direction.SOUTH);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"binding-relay-preview"));player.setPos(h.absolutePos(source).getCenter().add(0,0,2));player.setYRot(90);
        var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());player.setItemInHand(InteractionHand.MAIN_HAND,wand);RuneProgramming.link(wand,level,new RuneProgramming.Selection(face.address(),rune.id()),player);
        h.startSequence().thenWaitUntil(()->h.assertTrue(NetworkManager.get(level.getServer()).networkAt(GlobalPos.of(level.dimension(),h.absolutePos(relay)))!=null,"Relay is discovered"))
            .thenExecute(()->{
                double prior=com.cappleapple.astralrepository.AstralServerConfig.wandBindingRange.get();
                try{com.cappleapple.astralrepository.AstralServerConfig.wandBindingRange.set(3.0);var preview=BindingPreviewPackets.snapshot(player);
                    h.assertTrue(preview.paths().size()==1&&preview.paths().getFirst().path().equals(List.of(h.absolutePos(source),h.absolutePos(relay),h.absolutePos(target))),"Preview retains the actual shortest relay route");
                    h.assertTrue(preview.paths().getFirst().departure().face()==Direction.SOUTH,"Preview leaves the rune's actual face");
                }finally{com.cappleapple.astralrepository.AstralServerConfig.wandBindingRange.set(prior);}
            }).thenSucceed();
    }

}
