package com.cappleapple.astralrepository.gametest;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class FilterPolicyGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void passiveFiltersGateRealProviderInsertionAndLeaveExtractionAlone(GameTestHelper h){
        var pos=new BlockPos(3,2,3);h.setBlock(pos,Blocks.CHEST);var at=h.absolutePos(pos);
        var surface=RuneSurfaces.getOrCreate(h.getLevel(),at,Direction.SOUTH);
        var iron=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.FILTER),RuneLayer.Mode.FILTER);iron.setPriority(12);iron.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);
        var copper=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.FILTER),RuneLayer.Mode.FILTER);copper.setPriority(27);copper.filter().add(FilterRules.Kind.ITEM,"minecraft:copper_ingot",false,ItemStack.EMPTY);
        var provider=CompatibilityRegistry.discoverStorage(h.getLevel(),at,null).getFirst();
        h.assertTrue(provider.insert(new ItemStack(Items.GOLD_INGOT,4),true).getCount()==4&&provider.insert(new ItemStack(Items.GOLD_INGOT,4),false).getCount()==4,"Simulation and execution reject nonmatches");
        h.assertTrue(provider.insert(new ItemStack(Items.IRON_INGOT,4),false).isEmpty()&&provider.insert(new ItemStack(Items.COPPER_INGOT,3),false).isEmpty(),"Whitelist runes combine with any-match");
        h.assertTrue(ContainerRuneRules.priority(h.getLevel(),at,0)==27,"Highest enabled priority applies");
        copper.filter().toggleBlacklist();h.assertTrue(provider.insert(new ItemStack(Items.COPPER_INGOT),false).getCount()==1,"Blacklist vetoes whitelist matches");
        h.assertTrue(new ItemKey(new ItemStack(Items.COPPER_INGOT)).equals(provider.candidate(stack->stack.is(Items.COPPER_INGOT))),"Live identity hint reaches existing contents through guarded insertion-policy wrappers");
        h.assertTrue(provider.extract(new ItemKey(new ItemStack(Items.COPPER_INGOT)),3,false).getCount()==3,"Insertion filters never trap existing contents");
        iron.setEnabled(false);copper.setEnabled(false);h.assertTrue(provider.insert(new ItemStack(Items.GOLD_INGOT),false).isEmpty(),"Existing provider observes live rule edits");
        h.assertTrue(iron.mode().title().equals("Filter")&&iron.mode().cycle(1)==RuneLayer.Mode.PUSH&&RuneLayer.Mode.PUSH.cycle(-1)==RuneLayer.Mode.FILTER,"Both cycle directions use the Filter name");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void existingProviderObservesAddedRemovedAndReplacedSurfaces(GameTestHelper h){
        var pos=new BlockPos(3,2,3);h.setBlock(pos,Blocks.BARREL);var at=h.absolutePos(pos);var level=h.getLevel();
        var provider=CompatibilityRegistry.discoverStorage(level,at,null).getFirst();
        h.assertTrue(provider.insert(new ItemStack(Items.GOLD_INGOT),true).isEmpty(),"Provider starts without insertion restrictions");
        for(int iteration=0;iteration<2;iteration++){
            var surface=RuneSurfaces.getOrCreate(level,at,Direction.SOUTH);
            var filter=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.FILTER),RuneLayer.Mode.FILTER);
            filter.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);
            h.assertTrue(provider.insert(new ItemStack(Items.GOLD_INGOT),true).getCount()==1,"Already-discovered provider observes a newly added or replaced surface immediately");
            h.assertTrue(provider.insert(new ItemStack(Items.IRON_INGOT),false).isEmpty(),"Live whitelist still accepts matching commits");
            RuneSurfaces.remove(level,at,Direction.SOUTH);
            h.assertTrue(provider.insert(new ItemStack(Items.GOLD_INGOT),true).isEmpty(),"Removing the surface removes its policy without rediscovering providers");
        }h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void indexedPoliciesObserveBothChestHalvesAndSameTickEdits(GameTestHelper h){
        var level=h.getLevel();var first=new BlockPos(3,2,3);
        var state=Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,Direction.NORTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        var second=first.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
        h.setBlock(first,state);h.setBlock(second,state.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        var left=RuneSurfaces.getOrCreate(level,h.absolutePos(first),Direction.SOUTH);
        var right=RuneSurfaces.getOrCreate(level,h.absolutePos(second),Direction.SOUTH);
        var view=left.layers();var iron=left.addLayer(RuneGlyph.id(RuneLayer.Mode.FILTER),RuneLayer.Mode.FILTER);
        iron.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);iron.setPriority(-12);
        var gold=right.addLayer(RuneGlyph.id(RuneLayer.Mode.FILTER),RuneLayer.Mode.FILTER);
        gold.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);gold.setPriority(-4);
        h.assertTrue(view.size()==1&&view==left.layers(),"Read-only layer view stays current without per-transfer snapshots");
        h.assertTrue(ContainerRuneRules.allows(level,h.absolutePos(first),new ItemStack(Items.GOLD_INGOT)),"Whitelist union includes the other chest half");
        h.assertTrue(ContainerRuneRules.priority(level,h.absolutePos(first),99)==-4,"Negative priorities on either half override fallback");
        gold.filter().toggleBlacklist();
        h.assertTrue(!ContainerRuneRules.allows(level,h.absolutePos(first),new ItemStack(Items.GOLD_INGOT)),"Same-tick blacklist edit on other half applies immediately");
        iron.setMode(RuneLayer.Mode.PUSH);gold.setEnabled(false);
        h.assertTrue(ContainerRuneRules.allows(level,h.absolutePos(first),new ItemStack(Items.COPPER_INGOT)),"Mode and enabled edits do not leave stale cached policy");
        left.removeLayer(iron.id());RuneSurfaces.remove(level,h.absolutePos(second),Direction.SOUTH);
        h.assertTrue(view.isEmpty()&&RuneSurfaces.at(level,h.absolutePos(second)).isEmpty(),"Layer and surface removal update indexed views immediately");
        h.assertTrue(ContainerRuneRules.priority(level,h.absolutePos(first),99)==99,"Removing policies restores fallback");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void unrestrictedIdentityFiltersPreserveBlacklistAndNumericLimits(GameTestHelper h){
        var rules=new FilterRules();var iron=new ItemStack(Items.IRON_INGOT);
        var water=new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1);
        for(boolean blacklist:java.util.List.of(false,true)){
            if(rules.blacklist()!=blacklist)rules.toggleBlacklist();rules.setMinimum(17);rules.setTarget(29);
            h.assertTrue(rules.unrestricted()&&rules.matches(iron)&&rules.matches(water),"Empty identity rules accept nonempty resources regardless of blacklist state");
            h.assertTrue(!rules.matches(ItemStack.EMPTY)&&!rules.matches(net.neoforged.neoforge.fluids.FluidStack.EMPTY),"Empty resource samples remain invalid");
            h.assertTrue(rules.minimum()==17&&rules.target()==29,"Unrestricted identities do not erase reserve or stock limits");
        }
        rules.clearPredicates();rules.add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);
        h.assertTrue(!rules.unrestricted()&&!rules.matches(iron)&&rules.matches(new ItemStack(Items.GOLD_INGOT)),"Adding a whitelist predicate immediately disables the fast path");
        rules.toggleBlacklist();h.assertTrue(!rules.unrestricted()&&rules.matches(iron)&&!rules.matches(new ItemStack(Items.GOLD_INGOT)),"Blacklist predicates retain inverse identity matching");
        rules.clearPredicates();h.assertTrue(rules.unrestricted()&&rules.minimum()==17&&rules.target()==29,"Clearing predicates restores unrestricted matching and preserves limits");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void crystalsAttachAndShapeToAllSixFaces(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(3,2,3));
        for(var block:java.util.List.of(AstralContent.STORAGE_NEXUS.get(),AstralContent.RELAY_CRYSTAL.get(),AstralContent.SEED_STORAGE_CRYSTAL.get(),AstralContent.POWER_NODE.get()))for(var face:Direction.values()){
            var context=new DirectionalPlaceContext(h.getLevel(),pos,face,new ItemStack(block),face);
            var state=block.getStateForPlacement(context);h.assertTrue(state.getValue(CrystalNodeBlock.FACING)==face.getOpposite(),"Placement keeps its support face");
            var box=state.getShape(h.getLevel(),pos).bounds();var axis=face.getAxis();
            double touching=face.getAxisDirection()==Direction.AxisDirection.POSITIVE?box.min(axis):box.max(axis);
            h.assertTrue(Math.abs(touching-(face.getAxisDirection()==Direction.AxisDirection.POSITIVE?0:1))<1e-8,"Collision shape touches the attached face");
        }h.succeed();
    }
}
