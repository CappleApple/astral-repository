package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;

/** Assertions run on the real integrated server, including inside the remapped release test. */
final class FabricGameplayAssertions {
    private static final BlockPos SOURCE=new BlockPos(-5,-60,0), TARGET=new BlockPos(-5,-60,4);
    private static RuneLayer transfer;
    static void prepare(ServerPlayer player){
        if(Boolean.getBoolean("astral_repository.integrationGameplay"))equipTrinkets(player);
        var level=player.serverLevel();var support=new BlockPos(20,-55,2);
        level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPos(22.5,-53,4.5);
        int count=0;
        for(var entry:AstralContent.BLOCKS.getEntries())for(var side:Direction.values()){
            var pos=support.relative(side);level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            var stack=new ItemStack(entry.get());place(player,stack,support,side);
            check(level.getBlockState(pos).is(entry.get())&&level.getBlockEntity(pos)!=null,"Block placement and entity on "+side);
            check(stack.isEmpty(),"Survival placement consumes one item");
            if(entry.get() instanceof CrystalNodeBlock)check(level.getBlockState(pos).getValue(CrystalNodeBlock.FACING)==side.getOpposite(),"Node attachment face");
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());count++;
        }
        check(count==60,"Expected all ten blocks on six faces");
        var storedPos=support.above();place(player,new ItemStack(AstralContent.SEED_STORAGE_CRYSTAL.get()),support,Direction.UP);
        var stored=(CrystalNodeBlockEntity)level.getBlockEntity(storedPos);stored.upgradeStorage(2);stored.upgradeRange();stored.setChannel(5);
        var iron=new ItemStack(Items.IRON_INGOT,512);iron.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Saved identity"));stored.inventory().insertItem(0,iron,false);
        stored.tank().fill(new com.cappleapple.astralrepository.platform.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1500),com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction.EXECUTE);stored.energy().receiveEnergy(2200,false);
        var drops=stored.getBlockState().getDrops(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(storedPos)).withParameter(LootContextParams.TOOL,ItemStack.EMPTY).withOptionalParameter(LootContextParams.BLOCK_ENTITY,stored));
        check(drops.size()==1,"Storage drops exactly one stateful item");level.setBlockAndUpdate(storedPos,Blocks.AIR.defaultBlockState());place(player,drops.get(0),support,Direction.UP);
        var restored=(CrystalNodeBlockEntity)level.getBlockEntity(storedPos);
        check(restored.inventory().getStackInSlot(0).getCount()==512&&ItemStack.isSameItemSameComponents(iron,restored.inventory().getStackInSlot(0)),"Break/re-place retains large named stack");
        check(restored.storageTier()==2&&restored.longRange()&&restored.channel()==5&&restored.tank().getFluidAmount()==1500&&restored.energy().getEnergyStored()==2200,"Break/re-place retains upgrades/fluid/energy");
        level.setBlockAndUpdate(storedPos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(support,Blocks.AIR.defaultBlockState());
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        level.setBlockAndUpdate(SOURCE.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(TARGET.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(SOURCE,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(TARGET,Blocks.CHEST.defaultBlockState());
        var named=new ItemStack(Items.COPPER_INGOT,32);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Rune identity"));((Container)level.getBlockEntity(SOURCE)).setItem(0,named);
        var surface=RuneSurfaces.getOrCreate(level,SOURCE,Direction.SOUTH);transfer=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);transfer.filter().setTarget(12);transfer.changed();
        check(surface.toggleTarget(transfer.id(),GlobalPos.of(level.dimension(),TARGET),Direction.NORTH).assigned(),"Rune binds real chest");
    }
    static void verifyTransfer(ServerPlayer player){
        var from=((Container)player.serverLevel().getBlockEntity(SOURCE)).getItem(0);var to=((Container)player.serverLevel().getBlockEntity(TARGET)).getItem(0);
        check(from.getCount()==20&&to.getCount()==12&&ItemStack.isSameItemSameComponents(from,to),"Rune conserves identity and respects destination stock limit: "+from+" -> "+to);
        check(transfer.transferredItems()==12,"Rune committed transfer count");transfer.setEnabled(false);
    }
    private static void equipTrinkets(ServerPlayer player){
        try {
            var api=Class.forName("dev.emi.trinkets.api.TrinketsApi");
            var component=((java.util.Optional<?>)api.getMethod("getTrinketComponent",net.minecraft.world.entity.LivingEntity.class).invoke(null,player)).orElseThrow();
            var contract=Class.forName("dev.emi.trinkets.api.TrinketComponent");
            var groups=(java.util.Map<?,?>)contract.getMethod("getInventory").invoke(component);
            var head=(java.util.Map<?,?>)groups.get("head");
            check(head!=null&&head.get("face") instanceof Container,"Astral goggles face slot assigned to player");
            var face=(Container)head.get("face");
            check(face.getContainerSize()>0,"Face slot has capacity");
            face.setItem(0,new ItemStack(AstralContent.RESONANCE_GOGGLES.get()));
            check(com.cappleapple.astralrepository.compat.TrinketsCompatibility.isWearingGoggles(player),"Server detects equipped Trinkets goggles");
        }catch(ReflectiveOperationException e){throw new AssertionError("Trinkets equipment API failed",e);}
    }
    private static void place(ServerPlayer player,ItemStack stack,BlockPos support,Direction side){
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.5)),side,support,false);
        check(player.gameMode.useItemOn(player,player.serverLevel(),stack,InteractionHand.MAIN_HAND,hit).consumesAction(),"Use-on places "+stack+" on "+side);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
