package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Two independent production server processes verify on-disk world persistence. */
public final class FabricPersistenceSmoke implements net.fabricmc.api.ModInitializer {
    private static final BlockPos STORAGE=new BlockPos(8,90,8), NEXUS=STORAGE.east(2), HOST=STORAGE.south(2);
    private static String result;
    @Override public void onInitialize(){
        String phase=System.getProperty("astral_repository.persistence");if(phase==null)return;
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{try{Files.writeString(Path.of("persistence-"+phase+".txt"),result==null?"FAIL: no completed assertion":result);}catch(Exception e){throw new RuntimeException(e);}});
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try{
                var level=server.overworld();level.getChunkAt(STORAGE);level.getChunkAt(NEXUS);level.getChunkAt(HOST);
                if(phase.equals("write")){
                    level.setBlockAndUpdate(STORAGE,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(NEXUS,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(HOST,Blocks.AIR.defaultBlockState());
                    RuneSurfaces.remove(level,HOST,Direction.SOUTH);
                    level.setBlockAndUpdate(STORAGE,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());level.setBlockAndUpdate(HOST,Blocks.CHEST.defaultBlockState());
                    var node=(CrystalNodeBlockEntity)level.getBlockEntity(STORAGE);node.upgradeStorage(2);node.upgradeRange();node.setChannel(5);
                    var stack=new ItemStack(Items.DIAMOND,512);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Restart identity"));node.inventory().insertItem(0,stack,false);
                    node.tank().fill(new com.cappleapple.astralrepository.platform.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1500),com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction.EXECUTE);node.energy().receiveEnergy(2200,false);
                    var layer=RuneSurfaces.getOrCreate(level,HOST,Direction.SOUTH).addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);layer.filter().setTarget(17);layer.setPriority(8);layer.setEnabled(false);
                    var a=AnchorAddress.crystal(level,STORAGE);var b=AnchorAddress.crystal(level,NEXUS);var data=RuneSavedData.get(server);if(!data.linked(a,b))data.toggle(a,b);
                    result="PASS: populated persistent crystal, rune and link; server completed clean save/shutdown.";
                }else{
                    var node=(CrystalNodeBlockEntity)level.getBlockEntity(STORAGE);check(node!=null,"Persisted crystal exists");var stack=node.inventory().getStackInSlot(0);
                    check(stack.is(Items.DIAMOND)&&stack.getCount()==512&&stack.getHoverName().getString().equals("Restart identity"),"Restart restores named inventory");
                    check(node.storageTier()==2&&node.longRange()&&node.channel()==5&&node.tank().getFluidAmount()==1500&&node.energy().getEnergyStored()==2200,"Restart restores upgrades, channel, fluid and energy");
                    var surface=RuneSurfaces.get(level,HOST,Direction.SOUTH);check(surface!=null&&surface.layers().size()==1,"Restart restores rune surface");var layer=surface.layers().get(0);check(layer.filter().target()==17&&layer.priority()==8&&!layer.enabled(),"Restart restores rune policy");
                    check(RuneSavedData.get(server).linked(AnchorAddress.crystal(level,STORAGE),AnchorAddress.crystal(level,NEXUS)),"Restart restores explicit link");
                    result="PASS: separate production server restart restored512 named items, storage upgrades, channel, fluid, energy, rune policy and explicit link.";
                }
            }catch(Throwable failure){result="FAIL: "+failure;com.mojang.logging.LogUtils.getLogger().error("Persistence regression failed",failure);}
            finally{server.halt(false);}
        });
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
