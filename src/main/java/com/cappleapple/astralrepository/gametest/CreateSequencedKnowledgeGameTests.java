package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

/** Actual native assembly metadata, component persistence and weighted final results. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class CreateSequencedKnowledgeGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=200)
    public static void nativeAssemblyKnowledgePreservesEveryLoopAndNeverRollsItsWeightedFinalPool(GameTestHelper h) {
        if (!Boolean.getBoolean("astral_repository.genericTechTest")) { h.succeed(); return; }
        h.assertTrue(ModList.get().isLoaded("create"), "The actual Create native runtime is installed");
        BlockPos source = new BlockPos(2, 2, 2); h.setBlock(source, Blocks.BARREL);
        var access = new CraftingGameTests.Fixture(h, source, (Container)h.getBlockEntity(source), List.of(), new ItemStack(Items.BRICK));
        long seed = 0x5a913c6dL; h.getLevel().random.setSeed(seed);
        long expected = net.minecraft.util.RandomSource.create(seed).nextLong();
        var catalog = CreateSequencedRecipes.discover(access);
        h.assertTrue(h.getLevel().random.nextLong() == expected, "Native assembly discovery never rolls the world's random generator");
        h.assertTrue(!catalog.steps().isEmpty(), "Real native assemblies expand into exact stages: " + catalog.diagnostics());
        Map<net.minecraft.resources.ResourceLocation, List<CreateSequencedRecipes.Step>> groups = new HashMap<>();
        catalog.steps().forEach(step -> groups.computeIfAbsent(step.assemblyId(), ignored -> new ArrayList<>()).add(step));
        boolean repeatedLoop = false, weighted = false;
        for (var entry : groups.entrySet()) {
            var raw = NativeRecipeSource.entries(h.getLevel()).stream().filter(recipe -> recipe.id().equals(entry.getKey())).findFirst().orElseThrow().value();
            int loops = ((Number)NativeBackendReflection.call(raw, "getLoops")).intValue();
            int sequence = ((List<?>)NativeBackendReflection.call(raw, "getSequence")).size();
            var stages = entry.getValue(); stages.sort(Comparator.comparingInt(CreateSequencedRecipes.Step::index));
            h.assertTrue(stages.size() == sequence * loops, "Every configured native loop has its complete processing steps");
            for (int index = 0; index < stages.size(); index++) {
                var stage = stages.get(index); h.assertTrue(stage.index() == index && stage.total() == stages.size(), "Native stages remain contiguous");
                if (index > 0) {
                    ResourceKey input = stage.primary().alternatives().getFirst();
                    h.assertTrue(stages.get(index - 1).guaranteed().getOrDefault(input, 0L) == 1, "Each real intermediate is the next stage's exact component-bearing primary input");
                    h.assertTrue(((Number)NativeBackendReflection.call(raw, "getStep", ((ItemKey)input).sample())).intValue() == index, "Native Create itself recognizes the exact progress index");
                    h.assertTrue(ResourceKeyCodec.load(ResourceKeyCodec.save(input, h.getLevel().registryAccess()), h.getLevel().registryAccess()).equals(input), "Native progress components survive durable resource serialization");
                    if (index >= sequence) repeatedLoop = true;
                }
            }
            var last = stages.getLast();
            if (last.chanceOutput() && last.possible().size() > 1) {
                weighted = true; h.assertTrue(last.guaranteed().isEmpty(), "Mutually exclusive native weighted final results cannot each be advertised as guaranteed coproducts");
            }
        }
        h.assertTrue(repeatedLoop && weighted, "The actual native recipe inventory includes repeated assembly loops and a weighted final pool");
        h.succeed();
    }
}
