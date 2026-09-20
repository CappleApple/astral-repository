package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class ExplicitLinkGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400,batch="astral_link_lifecycle")
    public static void runeUnloadPreservesLinksAndRebuildCleansReplacedHosts(GameTestHelper h){
        BlockPos nexus=new BlockPos(4,2,4),hostPos=new BlockPos(10,2,4);
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(hostPos,Blocks.CHEST);
        var node=(CrystalNodeBlockEntity)h.getBlockEntity(nexus);node.setChannel(5);
        var rune=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH);rune.setChannel(5);rune.addGlyph(Identifier.parse("astral_repository:filter_sigil"),NodeKind.ROUTING);
        var manager=NetworkManager.get(h.getLevel().getServer());manager.toggleLink(node.address(),rune.address());
        h.succeedWhen(()->{
            h.assertTrue(manager.networkAt(rune.address())!=null,"Rune is registered before lifecycle transitions");
            try{
                var chunk=h.getLevel().getChunkAt(h.absolutePos(hostPos));
                NetworkManager.chunkUnload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));
                h.setBlock(hostPos,Blocks.BARREL);
                NetworkManager.chunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk,false));
                var tick=NetworkManager.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(manager);
                h.assertTrue(RuneSurfaces.get(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH)==rune&&!rune.isRemoved(),"Unload clears only the cached host so reloaded block entities retain the surface");
                h.assertTrue(manager.links(rune.address()).contains(node.address()),"Unload and reload preserve persisted links");
                h.setBlock(hostPos,Blocks.CHEST);
                // An unrelated topology rebuild can run before this rune's bounded validation turn.
                var rebuild=NetworkManager.class.getDeclaredMethod("rebuild");rebuild.setAccessible(true);rebuild.invoke(manager);
                h.assertTrue(RuneSurfaces.get(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH)==null,"Rebuild removes a rune whose loaded host was replaced");
                h.assertTrue(manager.links(rune.address()).isEmpty()&&!manager.links(node.address()).contains(rune.address()),"Host replacement removes reciprocal links");
                RuneSavedData copy=RuneSavedData.load(h.getLevel().getServer(),RuneSavedData.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
                h.assertTrue(copy.surface(rune.address())==null,"Saved data cannot resurrect a removed rune on the replacement host");
            }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400)
    public static void linkedNexusesShareOneInventoryAndCraftingCoordinator(GameTestHelper h){
        BlockPos a=new BlockPos(4,2,4),b=new BlockPos(10,2,4),left=a.west(),right=b.east();
        h.setBlock(a,AstralContent.STORAGE_NEXUS.get());h.setBlock(b,AstralContent.STORAGE_NEXUS.get());h.setBlock(left,Blocks.CHEST);h.setBlock(right,Blocks.CHEST);
        var first=(CrystalNodeBlockEntity)h.getBlockEntity(a);var second=(CrystalNodeBlockEntity)h.getBlockEntity(b);first.setChannel(12);second.setChannel(12);
        ((ChestBlockEntity)h.getBlockEntity(left)).setItem(0,new ItemStack(Items.IRON_INGOT,17));((ChestBlockEntity)h.getBlockEntity(right)).setItem(0,new ItemStack(Items.IRON_INGOT,23));
        var manager=NetworkManager.get(h.getLevel().getServer());boolean[] linked={false};
        h.runAfterDelay(40,()->{
            h.assertTrue(manager.networkAt(first.address())!=null&&manager.networkAt(second.address())!=null,"Both standalone Nexuses registered");
            h.assertTrue(manager.networkAt(first.address())==manager.networkAt(second.address()),"Nearby same-channel Nexuses join automatically");
            linked[0]=true;
        });
        ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
        h.succeedWhen(()->{
            h.assertTrue(linked[0],"Automatic network discovered");var an=manager.networkAt(first.address());var bn=manager.networkAt(second.address());
            h.assertTrue(an!=null&&an==bn,"Both Nexuses address the same component");h.assertTrue(an.crafting()==bn.crafting(),"One crafting coordinator belongs to the component");
            h.assertTrue(an.snapshot().getOrDefault(iron,0L)==40,"Both physical inventories contribute once");
            h.assertTrue(an.extractAt(iron,10,second.address().position()).getCount()==10,"Either Nexus performs an immediate withdrawal");
            h.assertTrue(bn.snapshot().getOrDefault(iron,0L)==30,"The other Nexus sees the same updated index");
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400)
    public static void separateLinkedFacesKeepIndependentNetworkAccessWithoutDuplicatingTransfers(GameTestHelper h){
        BlockPos a=new BlockPos(4,2,4),b=new BlockPos(10,2,4),hostPos=new BlockPos(7,2,4);
        h.setBlock(a,AstralContent.STORAGE_NEXUS.get());h.setBlock(b,AstralContent.STORAGE_NEXUS.get());h.setBlock(hostPos,Blocks.CHEST);
        var first=(CrystalNodeBlockEntity)h.getBlockEntity(a);var second=(CrystalNodeBlockEntity)h.getBlockEntity(b);first.setChannel(7);second.setChannel(8);
        var chest=(ChestBlockEntity)h.getBlockEntity(hostPos);chest.setItem(0,new ItemStack(Items.IRON_INGOT,64));
        var north=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH);var south=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.SOUTH);
        for(var rune:List.of(north,south)){rune.setChannel(7);rune.addGlyph(Identifier.parse("astral_repository:filter_sigil"),NodeKind.ROUTING);}
        south.setChannel(8);
        var manager=NetworkManager.get(h.getLevel().getServer());manager.toggleLink(first.address(),north.address());manager.toggleLink(second.address(),south.address());
        ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT));boolean[] withdrawn={false};
        h.succeedWhen(()->{
            var an=manager.networkAt(first.address());var bn=manager.networkAt(second.address());
            h.assertTrue(an!=null&&bn!=null&&an!=bn,"Two independently linked rune faces keep their separate networks");
            if(!withdrawn[0]){
                h.assertTrue(an.snapshot().getOrDefault(iron,0L)==64&&bn.snapshot().getOrDefault(iron,0L)==64,"Each linked face exposes its actual shared backend");
                h.assertTrue(an.extract(iron,16).getCount()==16,"First network extracts actual items");withdrawn[0]=true;
            }
            h.assertTrue(chest.getItem(0).getCount()==48,"Shared backend was debited exactly once");
            h.assertTrue(an.snapshot().getOrDefault(iron,0L)==48&&bn.snapshot().getOrDefault(iron,0L)==48,"Both network indexes reconcile the same physical remainder");
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400)
    public static void runeFacesStackRulesPersistLinksAndUseTheActualSide(GameTestHelper h){
        BlockPos nexus=new BlockPos(4,2,4),sourcePos=nexus.west(),targetPos=new BlockPos(10,2,4);
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(sourcePos,Blocks.CHEST);h.setBlock(targetPos,Blocks.ENCHANTING_TABLE);
        var node=(CrystalNodeBlockEntity)h.getBlockEntity(nexus);node.setChannel(9);
        ((ChestBlockEntity)h.getBlockEntity(sourcePos)).setItem(0,new ItemStack(Items.IRON_INGOT,64));
        ItemStackHandler target=new ItemStackHandler(2);BlockPos absolute=h.absolutePos(targetPos);String fixture="sided_rune_"+UUID.randomUUID();
        CompatibilityRegistry.registerStorage(fixture,(level,pos,side)->level==h.getLevel()&&pos.equals(absolute)&&side==Direction.NORTH
                ?List.of(new ItemHandlerStorageProvider(fixture,target,target,()->level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE))):List.of());
        RuneSurface destination=RuneSurfaces.getOrCreate(h.getLevel(),absolute,Direction.NORTH);
        destination.setChannel(9);destination.addGlyph(Identifier.parse("astral_repository:routing_rune"),NodeKind.ROUTING);
        destination.addGlyph(Identifier.parse("astral_repository:filter_sigil"),NodeKind.ROUTING);destination.addGlyph(Identifier.parse("astral_repository:filter_sigil"),NodeKind.ROUTING);
        var pull=destination.layers().getFirst();pull.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);pull.filter().setTarget(16);pull.changed();
        h.assertTrue(destination.toggleTarget(pull.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(sourcePos)),Direction.UP).assigned(),"Explicit Pull target assigned to the bare source chest");
        RuneSurface source=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(sourcePos),Direction.UP);source.setChannel(9);source.addGlyph(Identifier.parse("astral_repository:collection_rune"),NodeKind.COLLECTION);
        var manager=NetworkManager.get(h.getLevel().getServer());
        h.assertTrue(manager.toggleLink(node.address(),destination.address()).linked(),"Crystal-to-rune explicit link accepted");
        h.assertTrue(manager.toggleLink(destination.address(),source.address()).linked(),"Rune-to-rune explicit link accepted");
        h.succeedWhen(()->{
            h.assertTrue(target.getStackInSlot(0).getCount()==16,"Rune stock rule transfers through the required NORTH capability");
            h.assertTrue(((ChestBlockEntity)h.getBlockEntity(sourcePos)).getItem(0).getCount()==48,"Real source debited once");
            h.assertTrue(manager.networkAt(node.address())==manager.networkAt(destination.address())&&manager.networkAt(node.address())==manager.networkAt(source.address()),"Face endpoints join the explicit component");
            RuneSavedData copy=RuneSavedData.load(h.getLevel().getServer(),RuneSavedData.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            RuneSurface restored=copy.surface(destination.address());h.assertTrue(restored!=null&&restored.facing()==Direction.NORTH,"Face address survives serialization");
            h.assertTrue(restored.glyphs().size()==3&&restored.glyphs().get(1).equals(restored.glyphs().get(2)),"Duplicate glyph layers survive serialization");
            h.assertTrue(restored.get(pull.id()).filter().target()==16&&restored.get(pull.id()).filter().matches(new ItemStack(Items.IRON_INGOT))&&!restored.get(pull.id()).filter().matches(new ItemStack(Items.GOLD_INGOT)),"Stacked stock and identity rules survive serialization");
            h.assertTrue(copy.links(destination.address()).containsAll(Set.of(node.address(),source.address())),"Multiple reciprocal links survive serialization");
            h.assertTrue(RuneSurfaces.remove(h.getLevel(),source.getBlockPos(),source.facing()),"Rune surface can be removed");
            h.assertTrue(manager.links(source.address()).isEmpty()&&!manager.links(destination.address()).contains(source.address()),"Removing a rune removes its reciprocal links");
        });
    }
}