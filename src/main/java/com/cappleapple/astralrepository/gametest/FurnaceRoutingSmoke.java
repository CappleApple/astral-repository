package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.WorldVisuals;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Actual network and vanilla furnace; observes packets delivered to the client. */
public final class FurnaceRoutingSmoke {
    private static final BlockPos NEXUS=new BlockPos(26,-59,14),FURNACE=new BlockPos(24,-59,12),STORE=new BlockPos(28,-59,12),SHELF=new BlockPos(26,-59,15);
    private static int phase,ticks;
    private static volatile boolean ready,complete;
    private static volatile String failure;
    private static UUID job;
    private static long rawAt=-1,fuelAt=-1,outputAt=-1;
    private static boolean rawChecked,fuelChecked;
    private static volatile boolean rawWaitVerified,fuelWaitVerified;
    private static boolean fuelShot,outputShot;
    private static final List<NetworkPackets.Visual> flights=new ArrayList<>();
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError(failure);if(++ticks>1200)throw new AssertionError("Furnace route timeout "+phase+" packets="+flights);
        if(rawAt>=0&&!rawChecked&&mc.level.getGameTime()-rawAt>=5){rawChecked=true;mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();check(((Container)level.getBlockEntity(FURNACE)).getItem(0).isEmpty(),"Raw input populated before its flight arrived");rawWaitVerified=true;}catch(Throwable e){failure=e.toString();}});}
        if(fuelAt>=0&&!fuelChecked&&mc.level.getGameTime()-fuelAt>=5){fuelChecked=true;mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();var furnace=(Container)level.getBlockEntity(FURNACE);check(furnace.getItem(0).is(Items.RAW_IRON)&&furnace.getItem(1).isEmpty()&&!level.getBlockState(FURNACE).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),"Furnace burned before fuel arrived");fuelWaitVerified=true;}catch(Throwable e){failure=e.toString();}});}
        if(fuelAt>=0&&!fuelShot&&mc.level.getGameTime()-fuelAt>=7){shot("furnace_inputs.png");fuelShot=true;}
        if(outputAt>=0&&!outputShot&&mc.level.getGameTime()-outputAt>=7){shot("furnace_output.png");outputShot=true;}
        if(phase==0){phase=1;NetworkPackets.visualReceiver=p->{WorldVisuals.add(p);if(p.from().getX()>=24&&(p.stack().is(Items.RAW_IRON)||p.stack().is(Items.COAL)||p.stack().is(Items.IRON_INGOT))){flights.add(p);if(p.stack().is(Items.RAW_IRON))rawAt=mc.level.getGameTime();if(p.stack().is(Items.COAL))fuelAt=mc.level.getGameTime();if(p.stack().is(Items.IRON_INGOT))outputAt=mc.level.getGameTime();}};mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=p.serverLevel();p.closeContainer();level.getChunkAt(NEXUS);
            level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());((CrystalNodeBlockEntity)level.getBlockEntity(NEXUS)).setChannel(6);
            level.setBlockAndUpdate(FURNACE,Blocks.FURNACE.defaultBlockState());level.setBlockAndUpdate(STORE,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(SHELF,Blocks.CHISELED_BOOKSHELF.defaultBlockState());
            var chest=(Container)level.getBlockEntity(STORE);chest.setItem(0,new ItemStack(Items.RAW_IRON));chest.setItem(1,new ItemStack(Items.COAL));
            var tome=new ItemStack(AstralContent.RECIPE_TOME.get());var data=new CompoundTag();data.put("Output",new ItemStack(Items.IRON_INGOT).save(level.registryAccess()));data.putString("OutputId","minecraft:iron_ingot");tome.set(DataComponents.CUSTOM_DATA,CustomData.of(data));((Container)level.getBlockEntity(SHELF)).setItem(0,tome);
            p.connection.teleport(26.5,-55,20,180,35);ready=true;
        }catch(Throwable e){failure=e.toString();}});}
        else if(phase==1&&ready&&ticks>80){phase=2;ticks=0;mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var n=NetworkManager.get(p.server).networkAt(GlobalPos.of(p.level().dimension(),NEXUS));check(n!=null&&n.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)),0L)==1,"Furnace fixture storage not discovered");var request=n.crafting().request(p,new ItemStack(Items.IRON_INGOT),1);check(request.accepted(),"Furnace job rejected");job=request.jobId();
        }catch(Throwable e){failure=e.toString();}});}
        else if(phase==2){
            if(ticks%10==0)mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var n=NetworkManager.get(p.server).networkAt(GlobalPos.of(p.level().dimension(),NEXUS));complete=n.crafting().statuses().stream().anyMatch(s->s.id().equals(job)&&s.state().equals("COMPLETE"));if(complete){var chest=(Container)p.level().getBlockEntity(STORE);int count=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(Items.IRON_INGOT))count+=chest.getItem(i).getCount();if(count!=1)failure="Furnace output did not return to chest; furnace="+((Container)p.level().getBlockEntity(FURNACE)).getItem(0);}}catch(Throwable e){failure=e.toString();}});
            if(complete){phase=3;ticks=0;}
        }
        else if(phase==3&&ticks>3&&outputShot){
            check(rawWaitVerified&&fuelWaitVerified,"Both raw ingredients and fuel must wait for their actual flights");
            check(flights.stream().anyMatch(p->p.stack().is(Items.COAL)&&p.from().equals(STORE)&&p.to().equals(FURNACE)&&p.arrival()!=null),"Fuel did not go from storage to furnace: "+flights);
            check(flights.stream().noneMatch(p->p.to().equals(NEXUS)||p.from().equals(NEXUS)),"A furnace flight incorrectly detoured through the Nexus: "+flights);
            check(flights.stream().anyMatch(p->p.stack().is(Items.IRON_INGOT)&&p.from().equals(FURNACE)&&p.to().equals(STORE)&&p.departure()!=null),"Missing physical furnace output flight: "+flights);
            Files.writeString(Path.of("../build/client-smoke/furnace-routes.txt"),flights.toString());
            NetworkPackets.visualReceiver=WorldVisuals::add;Files.writeString(Path.of("../build/client-smoke/result.txt"),"PASS: real input remains empty during its flight; arrived input waits unlit for fuel in transit; actual vanilla smelt returns one iron ingot to storage; received raw-iron and coal flights to furnace and ingot flight back to chest with no Nexus detour.\n");return true;
        }
        return false;
    }
    private static void shot(String file)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Path.of("../build/client-smoke/"+file));}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
