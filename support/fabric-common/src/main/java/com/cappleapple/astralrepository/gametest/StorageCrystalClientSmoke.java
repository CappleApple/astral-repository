package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;

/** Checks the server-opened crystal screen and synchronized local entries beside a connected Nexus. */
public final class StorageCrystalClientSmoke {
    private static final BlockPos CRYSTAL=new BlockPos(26,-59,14),NEXUS=CRYSTAL.east(2),STORE=NEXUS.north();
    private static int phase,ticks;
    private static volatile boolean ready;
    private static volatile String failure;
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError(failure);if(++ticks>600)throw new AssertionError("Crystal menu timeout "+phase);
        if(phase==0){phase=1;mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();p.closeContainer();level.getChunkAt(CRYSTAL);
            level.setBlockAndUpdate(CRYSTAL,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());level.setBlockAndUpdate(STORE,Blocks.CHEST.defaultBlockState());
            ((CrystalNodeBlockEntity)level.getBlockEntity(CRYSTAL)).setChannel(6);((CrystalNodeBlockEntity)level.getBlockEntity(NEXUS)).setChannel(6);
            ((CrystalNodeBlockEntity)level.getBlockEntity(CRYSTAL)).inventory().insertItem(0,new ItemStack(Items.AMETHYST_SHARD,23),false);
            ((Container)level.getBlockEntity(STORE)).setItem(0,new ItemStack(Items.DIAMOND,41));p.connection.teleport(27.5,-59,17,180,20);ready=true;
        }catch(Throwable e){failure=e.toString();}});}
        else if(phase==1&&ready&&ticks>80){open(CRYSTAL);phase=2;ticks=0;}
        else if(phase==2&&ticks>20&&mc.screen instanceof NexusScreen screen){
            check(screen.getMenu().entries.size()==1&&screen.getMenu().entries.getFirst().stack().is(Items.AMETHYST_SHARD)&&screen.getMenu().entries.getFirst().count()==23,"Crystal received foreign network items");
            check(screen.getTitle().getString().equals(AstralContent.SEED_STORAGE_CRYSTAL.get().getName().getString()),"Crystal title is mislabeled");
            check(screen.getMenu().jobs.isEmpty(),"Crystal shows network crafting jobs");shot("storage_crystal_local.png");open(NEXUS);phase=3;ticks=0;
        }
        else if(phase==3&&ticks>20&&mc.screen instanceof NexusScreen screen){
            check(screen.getMenu().entries.stream().anyMatch(e->e.stack().is(Items.AMETHYST_SHARD)&&e.count()==23)&&screen.getMenu().entries.stream().anyMatch(e->e.stack().is(Items.DIAMOND)&&e.count()==41),"Nexus no longer exposes combined crystal and chest storage");
            shot("storage_crystal_network.png");check(!mc.mouseHandler.isMouseGrabbed(),"Mouse was captured");Files.writeString(Path.of("../build/client-smoke/result.txt"),"PASS: actual Storage Crystal screen contains only its 23 amethyst shards and localized title; connected Nexus screen contains both crystal shards and 41 chest diamonds.\n");return true;
        }
        return false;
    }
    private static void open(BlockPos pos){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();NetworkManager.open(p,GlobalPos.of(p.level().dimension(),pos));}catch(Throwable e){failure=e.toString();}});}
    private static void shot(String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Path.of("../build/client-smoke/"+name));}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
