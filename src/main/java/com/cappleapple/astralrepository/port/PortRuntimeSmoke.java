package com.cappleapple.astralrepository.port;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Opt-in dedicated-server persistence/capability gate in an isolated test world. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID)
public final class PortRuntimeSmoke {
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!Boolean.getBoolean("astral_repository.portSmoke"))return;
        var server=event.getServer();
        try{
            var level=server.overworld();var pos=new BlockPos(0,100,0);level.getChunkAt(pos);
            var marker=java.nio.file.Path.of("port-persistence.marker");var runePos=pos.east(4);var targetPos=pos.east(8);
            if(java.nio.file.Files.exists(marker)){
                check(level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity,"saved world storage block exists after restart");
                var previous=(CrystalNodeBlockEntity)level.getBlockEntity(pos);
                check(previous.channel()==7&&previous.priority()==13,"world settings survived process restart");
                check(previous.inventory().getAmountAsLong(0)==64&&previous.inventory().getStackInSlot(0).getHoverName().getString().equals("Port persistence fixture"),"world item components survived process restart");
                check(previous.tank().getFluidAmount()==1200&&previous.energy().getEnergyStored()==6400,"world fluid and energy survived process restart");
                var surface=RuneSurfaces.get(level,runePos,Direction.SOUTH);check(surface!=null&&surface.layers().size()==1,"rune layer survived process restart");
                var layer=surface.layers().getFirst();check(layer.priority()==12&&!layer.enabled()&&layer.target()!=null&&layer.target().position().pos().equals(targetPos),"rune target/settings survived process restart");
                check(layer.cadence().equals(RuneCadence.DEFAULT.with(RuneCadence.Kind.ITEMS,new RuneCadence.Rate(3,7))),"rune cadence survived process restart");
                check(RuneSavedData.get(server).linked(AnchorAddress.crystal(level,pos),surface.address()),"explicit crystal/rune link survived process restart");
                AstralRepository.LOGGER.info("ASTRAL_PORT_RESTART_PASS: world item components, fluid, energy, settings, rune layers/targets/cadence, explicit links");
            }
            var state=AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState();
            level.removeBlock(pos,false);level.setBlockAndUpdate(pos,state);
            var node=(CrystalNodeBlockEntity)level.getBlockEntity(pos);check(node!=null,"storage block entity");
            node.setChannel(7);node.setPriority(13);
            var named=new ItemStack(Items.IRON_INGOT);named.set(DataComponents.CUSTOM_NAME,Component.literal("Port persistence fixture"));
            var iron=ItemResource.of(named);
            var items=level.getCapability(Capabilities.Item.BLOCK,pos,Direction.UP);check(items!=null,"item capability");
            try(var transaction=Transaction.openRoot()){check(items.insert(0,iron,64,transaction)==64,"item insertion");}
            check(node.inventory().getAmountAsLong(0)==0,"item rollback");
            try(var transaction=Transaction.openRoot()){check(items.insert(0,iron,64,transaction)==64,"item commit insertion");transaction.commit();}
            var fluids=level.getCapability(Capabilities.Fluid.BLOCK,pos,Direction.UP);check(fluids!=null,"fluid capability");
            try(var transaction=Transaction.openRoot()){check(fluids.insert(0,FluidResource.of(Fluids.WATER),1200,transaction)==1200,"fluid insertion");transaction.commit();}
            var energy=level.getCapability(Capabilities.Energy.BLOCK,pos,Direction.UP);check(energy!=null,"energy capability");
            try(var transaction=Transaction.openRoot()){check(energy.insert(6400,transaction)==6400,"energy insertion");transaction.commit();}
            var saved=node.saveWithFullMetadata(level.registryAccess());
            var restored=(CrystalNodeBlockEntity)BlockEntity.loadStatic(pos,state,saved,level.registryAccess());
            check(restored!=null,"block entity restore");restored.setLevel(level);
            check(restored.channel()==7&&restored.priority()==13,"settings persistence");
            check(restored.inventory().getAmountAsLong(0)==64&&iron.matches(restored.inventory().getStackInSlot(0)),"item component persistence");
            check(restored.tank().getFluidAmount()==1200,"fluid persistence");check(restored.energy().getEnergyStored()==6400,"energy persistence");
  var recipeIds=server.getRecipeManager().getRecipes().stream().map(recipe->recipe.id().identifier()).filter(id->id.getNamespace().equals("astral_repository")).map(net.minecraft.resources.Identifier::getPath).collect(java.util.stream.Collectors.toSet());
  var expectedRecipes=java.util.Set.of("astral_geode","astral_nexus","attunement_wand","dimensional_attunement","moon_attunement","power_node","range_attunement","recipe_tome","relay_crystal","resonance_goggles","seed_storage_crystal","star_attunement","storage_nexus");
  check(recipeIds.equals(expectedRecipes),"all 13 unconditional mod recipes: "+recipeIds);
  var pickaxe=new net.minecraft.world.item.ItemStack(Items.DIAMOND_PICKAXE);var cluster=AstralContent.ASTRAL_CLUSTER.get().defaultBlockState();
  var drops=net.minecraft.world.level.block.Block.getDrops(cluster,level,pos,null,null,pickaxe);check(drops.size()==1&&drops.getFirst().is(AstralContent.ASTRAL_GEM.get())&&drops.getFirst().getCount()==4,"cluster pickaxe drops four gems: "+drops);
  drops=net.minecraft.world.level.block.Block.getDrops(cluster,level,pos,null,null,net.minecraft.world.item.ItemStack.EMPTY);check(drops.size()==1&&drops.getFirst().is(AstralContent.ASTRAL_GEM.get())&&drops.getFirst().getCount()==2,"cluster hand drops two gems: "+drops);
  var silk=pickaxe.copy();silk.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH),1);
  drops=net.minecraft.world.level.block.Block.getDrops(cluster,level,pos,null,null,silk);check(drops.size()==1&&drops.getFirst().is(AstralContent.ASTRAL_CLUSTER.get().asItem())&&drops.getFirst().getCount()==1,"silk touch preserves cluster: "+drops);
  var bud=AstralContent.SMALL_ASTRAL_BUD.get().defaultBlockState();check(net.minecraft.world.level.block.Block.getDrops(bud,level,pos,null,null,pickaxe).isEmpty(),"bud requires silk touch");
  drops=net.minecraft.world.level.block.Block.getDrops(bud,level,pos,null,null,silk);check(drops.size()==1&&drops.getFirst().is(AstralContent.SMALL_ASTRAL_BUD.get().asItem()),"silk touch preserves bud: "+drops);

            level.setBlockAndUpdate(runePos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(targetPos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            RuneSurfaces.remove(level,runePos,Direction.SOUTH);
            var surface=RuneSurfaces.getOrCreate(level,runePos,Direction.SOUTH);surface.setChannel(7);
            var layer=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);layer.setPriority(12);layer.setEnabled(false);
            layer.setCadence(RuneCadence.DEFAULT.with(RuneCadence.Kind.ITEMS,new RuneCadence.Rate(3,7)));
            check(surface.toggleTarget(layer.id(),net.minecraft.core.GlobalPos.of(level.dimension(),targetPos),Direction.SOUTH).assigned(),"persisted rune target setup");
            check(NetworkManager.get(server).toggleLink(node.address(),surface.address()).success(),"persisted explicit link setup");
            com.cappleapple.astralrepository.smoke.RuneTransitSmoke.run(server);
            server.saveEverything(true,true,true);java.nio.file.Files.writeString(marker,"Saved runtime fixture for real process restart.\n");
            AstralRepository.LOGGER.info("ASTRAL_PORT_SMOKE_PASS: registry, all 13 mod recipes, native cluster/bud loot, capabilities, transactional rollback, and block entity persistence");
        }catch(Throwable failure){AstralRepository.LOGGER.error("ASTRAL_PORT_SMOKE_FAIL",failure);}
        finally{server.halt(false);}
    }
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    private PortRuntimeSmoke(){}
}
