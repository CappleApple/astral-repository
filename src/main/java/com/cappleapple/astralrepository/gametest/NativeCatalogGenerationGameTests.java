package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** A knowledge refresh may replace discovery while a physical queued plan retains its captured contracts. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NativeCatalogGenerationGameTests {
    private static final Map<GlobalPos, Fixture> FIXTURES = new HashMap<>();
    private static boolean registered;
    private static synchronized void register() {
        if (registered) return;
        ProcessingAdapters.registerResource("astral_repository:native_catalog_generation", () -> new NativeMachineAdapter(new Backend()));
        registered = true;
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=600)
    public static void knowledgeRefreshKeepsQueuedCapturedBindingsWithoutOfferingThemToNewRequests(GameTestHelper h) {
        register(); BlockPos source = new BlockPos(2, 2, 2), processor = source.east(3);
        h.setBlock(source, Blocks.BARREL); h.setBlock(processor, Blocks.BARREL);
        CompoundTag marker = new CompoundTag(); marker.putString("nativeVariant", "captured-before-reservation");
        ItemStack original = new ItemStack(Items.PAPER); original.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        Container stock = (Container)h.getBlockEntity(source); stock.setItem(0, original.copy());
        Fixture fixture = new Fixture(h, source, stock, processor, original); FIXTURES.put(fixture.origin(), fixture);
        CraftingService service = new CraftingService(fixture); var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "native-catalog-generation"));
        var first = service.request(player, fixture.target, 1); h.assertTrue(first.accepted(), "The original two-step native request is accepted");
        UUID[] newer = {null}; boolean[] refreshed = {false};
        h.onEachTick(() -> {
            service.tick();
            if (!refreshed[0] && !fixture.machine.getItem(0).isEmpty()) {
                h.assertTrue(fixture.machine.getItem(0).is(original.getItem()) && new ItemKey(fixture.machine.getItem(0)).equals(fixture.original), "The exact component variant reached the real first input slot");
                // This is the public read-only API used by native Tome knowledge. The stock variant is now in escrow.
                h.assertTrue(service.catalogSnapshot().stream().noneMatch(recipe -> recipe.process().startsWith("native/catalog_generation/")), "A fresh catalog omits both old native recipes after their source variant disappears");
                var next = service.request(player, fixture.target, 1); h.assertTrue(next.accepted(), "A new request uses the new catalog generation"); newer[0] = next.jobId();
                refreshed[0] = true;
            }
            fixture.physicalTick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(refreshed[0] && fixture.firstCycles == 1 && fixture.secondCycles == 1, "Both physically accepted old contracts execute once despite discovery changing: " + service.statuses());
            h.assertTrue(service.statuses().stream().anyMatch(status -> status.id().equals(first.jobId()) && status.state().equals("COMPLETE")), "The original captured plan completes its previously queued second step");
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(fixture.target), 0L) == 1 && fixture.machine.isEmpty(), "One input becomes exactly one final output with no held surplus");
            h.assertTrue(newer[0] != null && service.statuses().stream().noneMatch(status -> status.id().equals(newer[0]) && status.state().equals("COMPLETE")), "New requests cannot execute a removed native recipe through an old binding");
            h.assertTrue(service.cancel(newer[0]) && !service.cancel(newer[0]), "The newer unavailable request cancels exactly once");
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(fixture.target), 0L) == 1 && service.activeProcessors() == 0, "Cancellation preserves completed physical output and releases all claims");
            FIXTURES.remove(fixture.origin());
        });
    }

    private static final class Fixture extends CraftingGameTests.Fixture {
        final BlockPos processor; final Container machine; final InvWrapper handler; final ItemKey original;
        final ItemKey intermediate = new ItemKey(new ItemStack(Items.NETHER_BRICK)); int firstCycles, secondCycles;
        Fixture(GameTestHelper h, BlockPos source, Container stock, BlockPos processor, ItemStack original) {
            super(h, source, stock, List.of(processor), new ItemStack(Items.BRICK)); this.processor = processor;
            this.machine = (Container)h.getBlockEntity(processor); this.handler = new InvWrapper(machine); this.original = new ItemKey(original);
        }
        @Override public int travelTicks(GlobalPos from, GlobalPos to) { return 0; }
        void physicalTick() {
            ItemStack input = machine.getItem(0); if (input.isEmpty() || !machine.getItem(1).isEmpty()) return;
            ItemKey key = new ItemKey(input);
            if (key.equals(original)) { machine.removeItem(0, 1); machine.setItem(1, intermediate.sample()); firstCycles++; }
            else if (key.equals(intermediate)) { machine.removeItem(0, 1); machine.setItem(1, target.copy()); secondCycles++; }
        }
    }
    private static final class Backend implements NativeMachineBackend {
        public String id() { return "catalog_generation"; }
        public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
            Fixture fixture = FIXTURES.get(access.origin());
            if (fixture == null || !fixture.at(fixture.processor).equals(position)) return null;
            return new Machine() {
                public Object identity() { return fixture.machine; }
                public Object instance() { return fixture.machine; }
                public boolean valid() { return fixture.level().getBlockEntity(position.pos()) == fixture.machine; }
                public boolean accepts(Recipe recipe) { return recipe.nativeRecipe() == fixture; }
                public List<Port> ports(Recipe recipe) { return List.of(NativeMachinePorts.itemSlot(fixture.handler, 0, true, false), NativeMachinePorts.itemSlot(fixture.handler, 1, false, true)); }
            };
        }
        public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> machines) {
            Fixture fixture = FIXTURES.get(access.origin());
            if (fixture == null || !access.resourceSnapshot().containsKey(fixture.original)) return List.of();
            return List.of(recipe("first", fixture.original, fixture.intermediate, fixture), recipe("second", fixture.intermediate, new ItemKey(fixture.target), fixture));
        }
        private static Recipe recipe(String name, ItemKey input, ItemKey output, Fixture fixture) {
            Map<ResourceKey, Long> outputs = Map.of(output, 1L);
            return new Recipe("catalog_generation/" + name, "catalog_generation/" + name,
                    List.of(CraftRecipe.Ingredient.of(input)), outputs, outputs, 1, 400, fixture, false);
        }
    }
    private NativeCatalogGenerationGameTests() {}
}
