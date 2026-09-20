package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.NetworkPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class ForgePortGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void forgeCapabilityInvalidation(GameTestHelper h){
        var local=new BlockPos(1,1,1);h.setBlock(local,Blocks.CHEST);
        var chest=(ChestBlockEntity)h.getBlockEntity(local);chest.setItem(0,new ItemStack(Items.IRON_INGOT,7));
        var old=CompatibilityRegistry.discoverStorage(h.getLevel(),h.absolutePos(local),null).get(0);
        h.assertTrue(old.valid(),"Discovered chest is valid");chest.invalidateCaps();
        h.assertTrue(!old.valid(),"LazyOptional invalidation immediately retires the provider");chest.reviveCaps();
        var fresh=CompatibilityRegistry.discoverStorage(h.getLevel(),h.absolutePos(local),null).get(0);
        h.assertTrue(fresh.valid()&&fresh.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==7,"Rediscovery preserves real chest contents");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void storageNbtPreservesCountsAndIdentity(GameTestHelper h){
        var local=new BlockPos(1,1,1);h.setBlock(local,AstralContent.SEED_STORAGE_CRYSTAL.get());
        var node=(CrystalNodeBlockEntity)h.getBlockEntity(local);node.setChannel(4);node.upgradeRange();
        var named=new ItemStack(Items.IRON_INGOT,512);named.setHoverName(Component.literal("Preserved iron"));
        h.assertTrue(node.inventory().insertItem(0,named,false).isEmpty(),"Storage accepts large logical quantity");
        node.tank().fill(new FluidStack(Fluids.WATER,2000),IFluidHandler.FluidAction.EXECUTE);node.energy().receiveEnergy(2000,false);
        var saved=node.saveWithFullMetadata();var restored=new CrystalNodeBlockEntity(h.absolutePos(local),node.getBlockState());restored.load(saved);
        h.assertTrue(restored.inventory().getStackInSlot(0).getCount()==512,"NBT retains counts beyond signed byte range");
        h.assertTrue(ItemStack.isSameItemSameTags(named,restored.inventory().getStackInSlot(0)),"NBT retains item custom data");
        h.assertTrue(restored.channel()==4&&restored.longRange()&&restored.tank().getFluidAmount()==2000&&restored.energy().getEnergyStored()==2000,"Settings, tank and energy survive reload");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void nativeCapabilitiesExposeAllResources(GameTestHelper h){
        var local=new BlockPos(1,1,1);h.setBlock(local,AstralContent.SEED_STORAGE_CRYSTAL.get());var node=(CrystalNodeBlockEntity)h.getBlockEntity(local);
        h.assertTrue(node.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null)==node.inventory(),"Forge item capability uses actual inventory");
        h.assertTrue(node.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null)==node.tank(),"Forge fluid capability uses actual tank");
        h.assertTrue(node.getCapability(ForgeCapabilities.ENERGY).orElse(null)==node.energy(),"Forge energy capability uses actual store");
        h.assertTrue(CompatibilityRegistry.discoverStorage(h.getLevel(),h.absolutePos(local),null).size()==1,"Provider discovery sees crystal storage");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void recipeAndWorldgenResourcesLoad(GameTestHelper h){
        var recipes=h.getLevel().getRecipeManager();
        for(String id:java.util.List.of("astral_geode","astral_nexus","storage_nexus","seed_storage_crystal","relay_crystal","power_node","attunement_wand","range_attunement","moon_attunement","star_attunement","recipe_tome","resonance_goggles","dimensional_attunement"))
            h.assertTrue(recipes.byKey(new net.minecraft.resources.ResourceLocation("astral_repository",id)).isPresent(),"Recipe loads: "+id);
        h.assertTrue(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE).containsKey(new net.minecraft.resources.ResourceLocation("astral_repository","astral_geode")),"Geode feature loads");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void packetCodecPreservesItemData(GameTestHelper h){
        var stack=new ItemStack(Items.IRON_INGOT);stack.setHoverName(Component.literal("Packet identity"));
        var original=new NetworkPackets.Action(7,NetworkPackets.CRAFT,stack,64,2,"iron",12);
        var bytes=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{NetworkPackets.Action.CODEC.encode(bytes,original);var decoded=NetworkPackets.Action.CODEC.decode(bytes);
            h.assertTrue(decoded.request()==12&&decoded.menu()==7&&decoded.amount()==64&&ItemStack.isSameItemSameTags(stack,decoded.stack()),"Forge codec preserves bounded requests and item NBT");
        }finally{bytes.release();}h.succeed();
    }
}
