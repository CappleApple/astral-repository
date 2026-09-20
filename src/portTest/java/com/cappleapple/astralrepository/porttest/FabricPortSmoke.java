package com.cappleapple.astralrepository.porttest;
import net.fabricmc.api.ModInitializer;import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;import net.fabricmc.fabric.api.transfer.v1.item.*;import net.fabricmc.fabric.api.transfer.v1.fluid.*;import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;import net.minecraft.core.BlockPos;import net.minecraft.world.item.Items;import com.cappleapple.astralrepository.content.*;
public final class FabricPortSmoke implements ModInitializer {
 public void onInitialize(){if(!Boolean.getBoolean("astral_repository.portSmoke"))return;ServerLifecycleEvents.SERVER_STARTED.register(server->{try{
  FabricFluidTransferTest.run();
  var world=server.overworld();var pos=new BlockPos(0,90,0);world.removeBlock(pos,false);world.setBlockAndUpdate(pos,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());var node=(CrystalNodeBlockEntity)world.getBlockEntity(pos);check(node!=null,"storage block entity");
  var items=ItemStorage.SIDED.find(world,pos,null);check(items!=null,"Fabric item lookup");try(var tx=Transaction.openOuter()){check(items.insert(ItemVariant.of(Items.DIAMOND),23,tx)==23,"item insertion");}check(node.inventory().used()==0,"item transaction rollback");try(var tx=Transaction.openOuter()){check(items.insert(ItemVariant.of(Items.DIAMOND),23,tx)==23,"committed insertion");tx.commit();}check(node.inventory().getStackInSlot(0).getCount()==23,"committed item count");
  var fluids=FluidStorage.SIDED.find(world,pos,null);try(var tx=Transaction.openOuter()){check(fluids.insert(FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),81000,tx)==81000,"fluid insertion");tx.commit();}check(node.tank().getFluidAmount()==1000,"millibucket conversion");
  var energy=team.reborn.energy.api.EnergyStorage.SIDED.find(world,pos,null);try(var tx=Transaction.openOuter()){check(energy.insert(500,tx)==500,"energy insertion");}check(node.energy().getEnergyStored()==0,"energy rollback");
  check(ItemStorage.SIDED.find(world,pos,null)==items,"item lookup identity while retained");check(FluidStorage.SIDED.find(world,pos,null)==fluids,"fluid lookup identity while retained");
  try(var tx=Transaction.openOuter()){
   invalid(()->items.insert(ItemVariant.blank(),1,tx));invalid(()->items.extract(ItemVariant.of(Items.DIAMOND),-1,tx));
   invalid(()->fluids.insert(FluidVariant.blank(),81,tx));invalid(()->fluids.extract(FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),-1,tx));
   invalid(()->energy.insert(-1,tx));check(items.insert(ItemVariant.of(Items.DIAMOND),0,tx)==0,"zero item insert");check(fluids.extract(FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),0,tx)==0,"zero fluid extract");
  }
  var saved=node.saveWithFullMetadata();var restored=new CrystalNodeBlockEntity(pos,node.getBlockState());restored.setLevel(world);restored.load(saved);check(restored.inventory().getStackInSlot(0).getCount()==23,"item persistence");check(restored.tank().getFluidAmount()==1000,"fluid persistence");
  var recipeIds=server.getRecipeManager().getRecipes().stream().map(recipe->recipe.getId()).filter(id->id.getNamespace().equals("astral_repository")).map(net.minecraft.resources.ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet());
  var expectedRecipes=java.util.Set.of("astral_geode","astral_nexus","attunement_wand","dimensional_attunement","moon_attunement","power_node","range_attunement","recipe_tome","relay_crystal","resonance_goggles","seed_storage_crystal","star_attunement","storage_nexus");
  check(recipeIds.equals(expectedRecipes),"all 13 unconditional mod recipes: "+recipeIds);
  var pickaxe=new net.minecraft.world.item.ItemStack(Items.DIAMOND_PICKAXE);var cluster=AstralContent.ASTRAL_CLUSTER.get().defaultBlockState();
  var drops=net.minecraft.world.level.block.Block.getDrops(cluster,world,pos,null,null,pickaxe);check(drops.size()==1&&drops.get(0).is(AstralContent.ASTRAL_GEM.get())&&drops.get(0).getCount()==4,"cluster pickaxe drops four gems: "+drops);
  drops=net.minecraft.world.level.block.Block.getDrops(cluster,world,pos,null,null,net.minecraft.world.item.ItemStack.EMPTY);check(drops.size()==1&&drops.get(0).is(AstralContent.ASTRAL_GEM.get())&&drops.get(0).getCount()==2,"cluster hand drops two gems: "+drops);
  var silk=pickaxe.copy();silk.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH,1);
  drops=net.minecraft.world.level.block.Block.getDrops(cluster,world,pos,null,null,silk);check(drops.size()==1&&drops.get(0).is(AstralContent.ASTRAL_CLUSTER.get().asItem())&&drops.get(0).getCount()==1,"silk touch preserves cluster: "+drops);
  var bud=AstralContent.SMALL_ASTRAL_BUD.get().defaultBlockState();check(net.minecraft.world.level.block.Block.getDrops(bud,world,pos,null,null,pickaxe).isEmpty(),"bud requires silk touch");
  drops=net.minecraft.world.level.block.Block.getDrops(bud,world,pos,null,null,silk);check(drops.size()==1&&drops.get(0).is(AstralContent.SMALL_ASTRAL_BUD.get().asItem()),"silk touch preserves bud: "+drops);
  java.nio.file.Files.writeString(java.nio.file.Path.of("port-smoke-result.txt"),"PASS: Fabric item/fluid/energy lookups, transactional commit and rollback, block entity persistence, all 13 unconditional mod recipes, silk-touch and normal cluster/bud loot.");
 }catch(Throwable failure){try{java.nio.file.Files.writeString(java.nio.file.Path.of("port-smoke-result.txt"),"FAIL: "+failure);}catch(Exception ignored){}throw new RuntimeException(failure);}finally{server.halt(false);}});}
 private static void invalid(Runnable transfer){try{transfer.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid native transfer accepted");}
 private static void check(boolean pass,String what){if(!pass)throw new AssertionError(what);}
}
