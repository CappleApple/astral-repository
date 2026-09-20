package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

/** Native gameplay regressions; this entire package is excluded from release artifacts. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class ForgeGameplayGameTests {
    private static FakePlayer player(GameTestHelper h, BlockPos pos) {
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"forge-gameplay"));
        player.setPos(pos.getX()+.5,pos.getY()+3,pos.getZ()+.5);
        return player;
    }
    private static void place(GameTestHelper h, FakePlayer player, ItemStack stack, BlockPos support, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var result=player.gameMode.useItemOn(player,h.getLevel(),stack,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,support,false));
        h.assertTrue(result.consumesAction(),"Normal use-on accepted "+stack.getItem()+" on "+face);
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void everyBlockItemPlacesOnEveryFace(GameTestHelper h) {
        BlockPos support=h.absolutePos(new BlockPos(5,5,5));var player=player(h,support.offset(4,0,4));
        h.getLevel().setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());int placed=0;
        for(var entry:AstralContent.BLOCKS.getEntries())for(Direction face:Direction.values()){
            BlockPos target=support.relative(face);h.getLevel().setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
            ItemStack stack=new ItemStack(entry.get());
            h.assertTrue(stack.getItem() instanceof BlockItem,"Registered block has a real BlockItem: "+entry.getId());
            place(h,player,stack,support,face);
            h.assertTrue(h.getLevel().getBlockState(target).is(entry.get()),"Placed expected block "+entry.getId()+" on "+face);
            h.assertTrue(h.getLevel().getBlockEntity(target)!=null,"Placed block creates its block entity: "+entry.getId());
            h.assertTrue(stack.isEmpty(),"Survival placement consumes exactly one block item");
            if(entry.get() instanceof CrystalNodeBlock)h.assertTrue(h.getLevel().getBlockState(target).getValue(CrystalNodeBlock.FACING)==face.getOpposite(),"Node attaches to clicked face");
            h.getLevel().setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());placed++;
        }
        h.assertTrue(placed==60,"Ten registered blocks cover all six attachment faces");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void brokenStorageItemRestoresContentsWhenPlaced(GameTestHelper h) {
        BlockPos source=h.absolutePos(new BlockPos(3,3,3)),support=source.east(3).below();
        h.getLevel().setBlockAndUpdate(source,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
        var node=(CrystalNodeBlockEntity)h.getLevel().getBlockEntity(source);node.upgradeStorage(2);node.upgradeRange();node.setChannel(5);
        var iron=new ItemStack(Items.IRON_INGOT,512);iron.setHoverName(net.minecraft.network.chat.Component.literal("Saved identity"));
        node.inventory().insertItem(0,iron,false);node.tank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1500),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);node.energy().receiveEnergy(2200,false);
        var drops=node.getBlockState().getBlock().getDrops(node.getBlockState(),new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(source)).withParameter(LootContextParams.TOOL,ItemStack.EMPTY).withOptionalParameter(LootContextParams.BLOCK_ENTITY,node));
        h.assertTrue(drops.size()==1&&drops.get(0).is(AstralContent.SEED_STORAGE_CRYSTAL.get().asItem()),"Breaking creates one stateful crystal item");
        h.getLevel().setBlockAndUpdate(source,Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
        place(h,player(h,support.offset(4,0,4)),drops.get(0),support,Direction.UP);
        var restored=(CrystalNodeBlockEntity)h.getLevel().getBlockEntity(support.above());
        h.assertTrue(restored.inventory().getStackInSlot(0).getCount()==512&&ItemStack.isSameItemSameTags(iron,restored.inventory().getStackInSlot(0)),"Breaking and normal replacement retain item quantity and NBT identity");
        h.assertTrue(restored.storageTier()==2&&restored.longRange()&&restored.channel()==5&&restored.tank().getFluidAmount()==1500&&restored.energy().getEnergyStored()==2200,"Replacement retains upgrades, channel, fluid and energy");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void nexusWithdrawDepositAndVanillaCrafting(GameTestHelper h) {
        var nexus=new BlockPos(5,2,5);h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(nexus.west(),Blocks.CHEST);
        var chest=(ChestBlockEntity)h.getBlockEntity(nexus.west());chest.setItem(0,new ItemStack(Items.IRON_INGOT,32));chest.setItem(1,new ItemStack(Items.OAK_LOG,2));
        var absolute=h.absolutePos(nexus);var player=player(h,absolute);player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        var menu=new NexusMenu(181,player.getInventory(),GlobalPos.of(h.getLevel().dimension(),absolute),false);player.containerMenu=menu;
        var iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
        h.startSequence().thenWaitUntil(()->h.assertTrue(menu.network()!=null&&menu.network().snapshot().getOrDefault(iron,0L)==32,"Actual adjacent chest stock is indexed"))
        .thenExecute(()->{
            menu.action(new NetworkPackets.Action(181,NetworkPackets.PICKUP,new ItemStack(Items.IRON_INGOT),0,0,""));
            h.assertTrue(menu.getCarried().is(Items.IRON_INGOT)&&menu.getCarried().getCount()==32&&chest.getItem(0).isEmpty(),"Nexus pickup extracts real stock to cursor");
            menu.action(new NetworkPackets.Action(181,NetworkPackets.DEPOSIT,ItemStack.EMPTY,0,0,""));
            h.assertTrue(menu.getCarried().isEmpty()&&chest.getItem(0).getCount()==32,"Nexus deposit commits cursor to real chest");
            menu.grid.setItem(0,new ItemStack(Items.OAK_LOG));menu.clicked(0,0,ClickType.PICKUP,player);
            h.assertTrue(menu.getCarried().is(Items.OAK_PLANKS)&&menu.getCarried().getCount()==4,"Real vanilla recipe crafts four planks");
            h.assertTrue(menu.grid.getItem(0).is(Items.OAK_LOG)&&menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_LOG)),0L)==1,"Crafting refills from real storage");
            menu.setCarried(ItemStack.EMPTY);menu.action(new NetworkPackets.Action(181,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY,0,0,""));
            h.assertTrue(menu.grid.isEmpty()&&menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_LOG)),0L)==2,"Clear grid returns the remaining input exactly once");h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=300)
    public static void realRuneTransfersPreserveItemIdentityAndStockCap(GameTestHelper h) {
        var source=new BlockPos(4,2,4);var target=source.east(4);h.setBlock(source,Blocks.CHEST);h.setBlock(target,Blocks.CHEST);
        var from=(ChestBlockEntity)h.getBlockEntity(source);var to=(ChestBlockEntity)h.getBlockEntity(target);
        var named=new ItemStack(Items.IRON_INGOT,32);named.setHoverName(net.minecraft.network.chat.Component.literal("Rune identity"));from.setItem(0,named);
        var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(source),Direction.SOUTH);
        var layer=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);layer.filter().setTarget(12);layer.changed();
        h.assertTrue(surface.toggleTarget(layer.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(target)),Direction.WEST).assigned(),"Rune binds to a plain sided chest");
        h.succeedWhen(()->{
            h.assertTrue(to.getItem(0).getCount()==12,"Scheduled rune reaches exact stock cap");
            h.assertTrue(from.getItem(0).getCount()==20&&ItemStack.isSameItemSameTags(from.getItem(0),to.getItem(0)),"Rune conserves named stack identity and total quantity");
            h.assertTrue(layer.transferredItems()==12,"Transfer counter reflects committed quantity");layer.setEnabled(false);
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400)
    public static void pullRuneMovesNativeFluidAndEnergyWithoutOvershoot(GameTestHelper h) {
        var source=new BlockPos(4,2,4);var target=source.east(4);h.setBlock(source,AstralContent.SEED_STORAGE_CRYSTAL.get());h.setBlock(target,AstralContent.SEED_STORAGE_CRYSTAL.get());
        var from=(CrystalNodeBlockEntity)h.getBlockEntity(source);var to=(CrystalNodeBlockEntity)h.getBlockEntity(target);
        from.tank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);from.energy().receiveEnergy(1000,false);
        var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(target),Direction.SOUTH);
        var pull=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PULL),RuneLayer.Mode.PULL);pull.filter().setTarget(300);pull.changed();
        h.assertTrue(surface.toggleTarget(pull.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(source)),Direction.WEST).assigned(),"Pull binds to real native resource stores");
        h.onEachTick(()->{
            h.assertTrue(from.tank().getFluidAmount()+to.tank().getFluidAmount()+RuneTransitData.get(h.getLevel().getServer()).pendingAmount(pull.id(),new com.cappleapple.astralrepository.api.FluidKey(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1)))==1000,"Native tanks conserve fluid each tick");
            h.assertTrue(from.energy().getEnergyStored()+to.energy().getEnergyStored()+RuneTransitData.get(h.getLevel().getServer()).pendingAmount(pull.id(),com.cappleapple.astralrepository.api.ResourceKinds.FE)==1000,"Native energy stores conserve power each tick");
            h.assertTrue(to.tank().getFluidAmount()<=300&&to.energy().getEnergyStored()<=300,"Both resource transfers respect stock caps");
        });
        h.succeedWhen(()->{h.assertTrue(to.tank().getFluidAmount()==300&&to.energy().getEnergyStored()==300,"Actual scheduled pull reaches both stock caps");pull.setEnabled(false);});
    }
    private ForgeGameplayGameTests() {}
}
