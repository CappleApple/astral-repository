package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;

/** Exercises the real coordinator with physical test inventories and typed non-item quantities. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class MixedResourceCraftingGameTests {
    private static final FluidKey WATER = new FluidKey(new FluidStack(Fluids.WATER, 1));
    private static final ResourceKey SOURCE = new ResourceKinds.ScalarKey(ResourceKinds.SOURCE, ResourceLocation.parse("astral_repository:test_processed_source"));
    private static final ItemKey CLAY = new ItemKey(new ItemStack(Items.CLAY_BALL)), BRICK = new ItemKey(new ItemStack(Items.BRICK));
    private static final Map<GlobalPos, Fixture> FIXTURES = new HashMap<>();
    private static boolean registered;

    private static synchronized void register() {
        if (registered) return;
        ProcessingAdapters.registerResource("astral_repository:mixed_resource_regression", Driver::new);
        registered = true;
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void fluidAndScalarIntermediatesUseOneCoordinatorAndExactPhysicalReservations(GameTestHelper h) {
        try (var f = new Fixture(h, 750, 2)) {
            var service = f.service();
            h.assertTrue(service.request(f.player, BRICK.sample(), 2).accepted(), "A taught item target can depend on fluid and scalar intermediates");
            CraftingGameTests.awaitPlanning(service);
            for (int tick = 0; tick < 120 && service.activeJobs() > 0; tick++) service.tick();
            h.assertTrue(service.activeJobs() == 0 && service.statuses().stream().anyMatch(job -> job.state().equals("COMPLETE")), "The mixed-resource dependency graph completes");
            h.assertTrue(f.reserved.equals(Map.of(WATER, 500L, CLAY, 2L)), "Only the exact initial water and clay requirements are physically reserved");
            h.assertTrue(f.storageWater.getFluidAmount() == 250 && f.snapshot().getOrDefault(BRICK, 0L) == 2 && !f.snapshot().containsKey(CLAY), "Two real clay items and 500 mB produce exactly two physical bricks");
            h.assertTrue(f.fluidSteps == 2 && f.itemSteps == 2 && f.sourceStock == 0 && f.scalarInMachine() == 0 && f.machineWater.isEmpty(), "Both typed intermediate batches are consumed without residue or duplication");
            h.assertTrue(f.maxActive == 1 && service.activeProcessors() == 0, "The physical processor is released after the pipeline");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void cancellingMixedResourceFlightsRefundsEachPhysicalReservationOnce(GameTestHelper h) {
        try (var f = new Fixture(h, 500, 1)) {
            f.travel = 12;
            var service = f.service(); var request = service.request(f.player, BRICK.sample(), 1);
            h.assertTrue(request.accepted(), "The mixed-resource request is accepted"); CraftingGameTests.awaitPlanning(service); service.tick();
            h.assertTrue(service.activeProcessors() == 1 && f.starts == 0 && f.machineWater.isEmpty(), "The processor is claimed while fluid remains in delivery escrow");
            h.assertTrue(service.cancel(request.jobId()) && !service.cancel(request.jobId()), "Cancellation consumes the job once");
            for (int tick = 0; tick < 210; tick++) service.tick();
            h.assertTrue(f.storageWater.getFluidAmount() == 500 && f.snapshot().getOrDefault(CLAY, 0L) == 1 && !f.snapshot().containsKey(BRICK), "All in-flight fluid and queued clay return exactly once");
            h.assertTrue(service.activeProcessors() == 0 && f.active == 0, "Cancelled transport releases the machine claim without starting it");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void rejectedPhysicalFluidRefundIsRecoveredByTheNextServiceExactlyOnce(GameTestHelper h) {
        try (var f = new Fixture(h, 500, 1)) {
            f.hold = true;
            var service = f.service(); var request = service.request(f.player, BRICK.sample(), 1);
            h.assertTrue(request.accepted(), "The mixed-resource request is accepted"); CraftingGameTests.awaitPlanning(service);
            for (int tick = 0; tick < 4; tick++) service.tick();
            h.assertTrue(f.starts == 1 && f.machineWater.getFluidAmount() == 250, "The real processor tank owns the arrived 250 mB");
            f.rejectFluidRefund = true;
            h.assertTrue(service.cancel(request.jobId()), "The running physical operation can be cancelled");
            h.assertTrue(f.machineWater.isEmpty() && f.storageWater.getFluidAmount() == 250 && f.fluidRefundCalls == 1, "Rejected fluid is removed from the processor and retained by the recovery journal");
            f.rejectFluidRefund = false;
            var replacement = f.service();
            for (int tick = 0; tick < 310; tick++) replacement.tick();
            h.assertTrue(f.storageWater.getFluidAmount() == 500 && f.fluidRefundCalls == 2, "The replacement service restores the pending fluid once and removes its journal entry");
            h.assertTrue(f.snapshot().getOrDefault(CLAY, 0L) == 1 && !f.snapshot().containsKey(BRICK) && f.active == 0, "Recovery preserves queued item ownership and releases the processor");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void uncertainFluidInsertionIsRecordedWithoutAutomaticReplay(GameTestHelper h) {
        try (var f = new Fixture(h, 500, 1)) {
            f.hold = true;
            var service = f.service(); var request = service.request(f.player, BRICK.sample(), 1);
            h.assertTrue(request.accepted(), "The mixed-resource request is accepted"); CraftingGameTests.awaitPlanning(service);
            for (int tick = 0; tick < 4; tick++) service.tick();
            f.throwAfterFluidRefund = true;
            h.assertTrue(service.cancel(request.jobId()), "Cancellation records a confirmed physical extraction even when reinsertion becomes uncertain");
            var replacement = f.service();
            for (int tick = 0; tick < 310; tick++) replacement.tick();
            h.assertTrue(f.storageWater.getFluidAmount() == 500 && f.fluidRefundCalls == 1, "An insertion that threw after committing is never replayed as another fluid refund");
            h.assertTrue(f.machineWater.isEmpty() && f.snapshot().getOrDefault(CLAY, 0L) == 1 && !f.snapshot().containsKey(BRICK), "Uncertain insertion handling does not duplicate any resource");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void aliasesOfOnePhysicalProcessorCannotRunTwoResourceJobsConcurrently(GameTestHelper h) {
        try (var f = new Fixture(h, 500, 2)) {
            f.hold = true;
            var first = f.service(); var second = f.service();
            h.assertTrue(first.request(f.player, BRICK.sample(), 1).accepted(), "The first network queues a resource job");
            CraftingGameTests.awaitPlanning(first);
            h.assertTrue(second.request(f.player, BRICK.sample(), 1).accepted(), "The second network queues against the remaining physical stock");
            CraftingGameTests.awaitPlanning(second);
            for (int tick = 0; tick < 8; tick++) { first.tick(); second.tick(); }
            h.assertTrue(f.starts == 1 && f.maxActive == 1 && first.activeProcessors() + second.activeProcessors() == 1, "Two positions exposing one block entity share one global processor identity");
            h.assertTrue(first.statuses().stream().noneMatch(job -> job.state().equals("FAILED")) && second.statuses().stream().noneMatch(job -> job.state().equals("FAILED")), "The second alias waits before any conflicting physical start");
            first.cancelAll();
            for (int tick = 0; tick < 8; tick++) second.tick();
            h.assertTrue(f.starts == 2 && f.active == 1 && f.maxActive == 1, "Releasing the first alias allows the other job to claim the same machine");
            second.cancelAll();
            h.assertTrue(f.storageWater.getFluidAmount() == 500 && f.snapshot().getOrDefault(CLAY, 0L) == 2 && f.active == 0, "Both cancellations return their separate real reservations and leave no alias claim");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=600)
    public static void chanceProcessingRetriesRealAttemptsUntilTheOriginalItemRequestCompletes(GameTestHelper h) {
        var f = new Fixture(h, 750, 3); f.chanceFailures = 2;
        var service = f.service(); var request = service.request(f.player, BRICK.sample(), 1);
        h.assertTrue(request.accepted(), "A probabilistic physical recipe can supply a taught target");
        h.onEachTick(() -> {
            service.tick();
            h.assertTrue(service.statuses().stream().allMatch(job -> job.id().equals(request.jobId()) && job.count() == 1), "Retries retain the original request identity and requested output amount");
        });
        h.startSequence().thenWaitUntil(() -> h.assertTrue(service.activeJobs() == 0, "The third real attempt finishes the original request"))
                .thenExecute(() -> {
                    h.assertTrue(service.statuses().stream().anyMatch(job -> job.id().equals(request.jobId()) && job.state().equals("COMPLETE")), "Only actual target production completes the request");
                    h.assertTrue(f.chanceAttempts == 3 && f.starts == 3 && f.reserved.equals(Map.of(WATER, 750L, CLAY, 3L)), "Two failed yields and one successful yield each consume separately reserved real inputs");
                    h.assertTrue(f.storageWater.isEmpty() && !f.snapshot().containsKey(CLAY) && f.snapshot().getOrDefault(BRICK, 0L) == 1, "Three consumed fluid/clay batches produce one physical brick without synthetic replacement");
                    h.assertTrue(f.active == 0 && f.machineWater.isEmpty(), "Every attempt releases its physical machine and resource ownership");
                    f.close();
                }).thenSucceed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=600)
    public static void chanceRetryWaitsForRealReplacementStockAndResumesTheSameRequest(GameTestHelper h) {
        var f = new Fixture(h, 250, 1); f.chanceFailures = 1;
        var service = f.service(); var request = service.request(f.player, BRICK.sample(), 1);
        h.assertTrue(request.accepted(), "The original chance-based request is accepted");
        h.onEachTick(service::tick);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(service.statuses().stream().anyMatch(job -> job.id().equals(request.jobId()) && job.state().equals("MISSING")), "A failed attempt waits for additional physical ingredients"))
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(f.chanceAttempts == 1 && f.storageWater.isEmpty() && !f.snapshot().containsKey(CLAY) && !f.snapshot().containsKey(BRICK), "Missing stock does not create retries or refund consumed chance inputs");
                    h.assertTrue(service.activeJobs() == 1 && service.statuses().stream().allMatch(job -> job.id().equals(request.jobId()) && job.count() == 1), "The same request remains queued with its unchanged target amount");
                    f.storageWater.fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE); f.insert(new ItemStack(Items.CLAY_BALL));
                }).thenWaitUntil(() -> h.assertTrue(service.activeJobs() == 0, "Supplying real extra stock resumes the same waiting request"))
                .thenExecute(() -> {
                    h.assertTrue(f.chanceAttempts == 2 && f.reserved.equals(Map.of(WATER, 500L, CLAY, 2L)), "The resumed attempt reserves and consumes the newly supplied resources");
                    h.assertTrue(f.snapshot().getOrDefault(BRICK, 0L) == 1 && f.storageWater.isEmpty() && !f.snapshot().containsKey(CLAY), "Only the successful second physical attempt produces output");
                    h.assertTrue(service.statuses().stream().anyMatch(job -> job.id().equals(request.jobId()) && job.state().equals("COMPLETE") && job.count() == 1), "Completion retains the original request identity");
                    f.close();
                }).thenSucceed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void nativePerCallLimitsReserveTheFullRecipeInConfirmedChunks(GameTestHelper h) {
        try (var f = new Fixture(h,750,1)) {
            f.maxReserveChunk = 64;
            var service = f.service();
            h.assertTrue(service.request(f.player,BRICK.sample(),1).accepted(),"A native transfer rate does not change recipe quantities");
            CraftingGameTests.awaitPlanning(service);
            for(int tick=0;tick<120&&service.activeJobs()>0;tick++)service.tick();
            h.assertTrue(service.activeJobs()==0&&service.statuses().stream().anyMatch(job->job.state().equals("COMPLETE")),"Several confirmed native withdrawals reserve one complete recipe");
            h.assertTrue(f.reserved.getOrDefault(WATER,0L)==250&&f.storageWater.getFluidAmount()==500&&f.snapshot().getOrDefault(BRICK,0L)==1,
                    "Native 64-unit call limits consume exactly 250 mB and produce one real brick");
        }
        h.succeed();
    }

    private static final class Fixture implements CraftingService.NetworkAccess, AutoCloseable {
        final ServerLevel level; final Container storage, machine; final BlockEntity machineEntity; final GlobalPos origin, processor, alias;
        final FluidTank storageWater = new FluidTank(4000), machineWater = new FluidTank(1000);
        final FakePlayer player; final List<CraftingService> services = new ArrayList<>(); final Map<ResourceKey, Long> reserved = new HashMap<>();
        int travel, starts, active, maxActive, fluidSteps, itemSteps, fluidRefundCalls, chanceFailures = -1, chanceAttempts; long sourceStock; long maxReserveChunk = Long.MAX_VALUE;
        boolean hold, rejectFluidRefund, throwAfterFluidRefund;
        Fixture(GameTestHelper h, int water, int clay) {
            register(); level = h.getLevel();
            var storagePos = new BlockPos(3, 2, 3); var machinePos = storagePos.east(2);
            h.setBlock(storagePos, Blocks.BARREL); h.setBlock(machinePos, Blocks.BARREL);
            origin = GlobalPos.of(level.dimension(), h.absolutePos(storagePos)); processor = GlobalPos.of(level.dimension(), h.absolutePos(machinePos)); alias = GlobalPos.of(level.dimension(), processor.pos().north());
            storage = (Container) h.getBlockEntity(storagePos); machineEntity = h.getBlockEntity(machinePos); machine = (Container) machineEntity;
            storage.setItem(0, new ItemStack(Items.CLAY_BALL, clay)); storageWater.fill(new FluidStack(Fluids.WATER, water), FluidAction.EXECUTE);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mixed-resource-fixture")); FIXTURES.put(origin, this);
        }
        CraftingService service() { var service = new CraftingService(this); services.add(service); return service; }
        long scalarInMachine() { return machineEntity.getPersistentData().getLong("MixedResourceFixtureSource"); }
        void scalarInMachine(long count) { machineEntity.getPersistentData().putLong("MixedResourceFixtureSource", count); machineEntity.setChanged(); }
        public ServerLevel level() { return level; }
        public Map<ItemKey, Long> snapshot() {
            Map<ItemKey, Long> result = new HashMap<>();
            for (int slot = 0; slot < storage.getContainerSize(); slot++) if (!storage.getItem(slot).isEmpty()) result.merge(new ItemKey(storage.getItem(slot)), (long)storage.getItem(slot).getCount(), Long::sum);
            return Map.copyOf(result);
        }
        public Map<ResourceKey, Long> resourceSnapshot() {
            Map<ResourceKey, Long> result = new HashMap<>(snapshot());
            if (!storageWater.isEmpty()) result.put(WATER, (long)storageWater.getFluidAmount());
            if (sourceStock > 0) result.put(SOURCE, sourceStock);
            return Map.copyOf(result);
        }
        public long reserveResource(ResourceKey key, long count, java.util.function.BiConsumer<GlobalPos, Long> receipt) {
            long taken = key.equals(WATER) ? storageWater.drain((int)Math.min(count,maxReserveChunk), FluidAction.EXECUTE).getAmount()
                    : key.equals(SOURCE) ? Math.min(sourceStock, count) : key instanceof ItemKey item ? extract(item, (int)count).getCount() : 0;
            if (key.equals(SOURCE)) sourceStock -= taken;
            if (taken > 0) { reserved.merge(key, taken, Long::sum); receipt.accept(origin, taken); }
            return taken;
        }
        public long insertResourceFrom(ResourceKey key, long count, GlobalPos from) {
            if (key.equals(WATER)) {
                fluidRefundCalls++;
                if (rejectFluidRefund) return count;
                long inserted = storageWater.fill(new FluidStack(Fluids.WATER, (int)count), FluidAction.EXECUTE);
                if (throwAfterFluidRefund) throw new IllegalStateException("Expected fixture failure after physical fluid reinsertion");
                return count - inserted;
            }
            if (key.equals(SOURCE)) { sourceStock += count; return 0; }
            return key instanceof ItemKey item ? insert(item.sample().copyWithCount((int)count)).getCount() : count;
        }
        public ItemStack extract(ItemKey key, int count) {
            for (int slot = 0; slot < storage.getContainerSize(); slot++) if (!storage.getItem(slot).isEmpty() && new ItemKey(storage.getItem(slot)).equals(key)) return storage.removeItem(slot, count);
            return ItemStack.EMPTY;
        }
        public ItemStack insert(ItemStack stack) {
            var left = stack.copy();
            for (int slot = 0; slot < storage.getContainerSize() && !left.isEmpty(); slot++) {
                var current = storage.getItem(slot);
                if (current.isEmpty()) { storage.setItem(slot, left); return ItemStack.EMPTY; }
                if (ItemStack.isSameItemSameComponents(current, left)) { int moved = Math.min(left.getCount(), current.getMaxStackSize() - current.getCount()); current.grow(moved); left.shrink(moved); }
            }
            return left;
        }
        public List<GlobalPos> workstations() { return List.of(processor, alias); }
        public List<ItemStack> exposedProducts() { return List.of(BRICK.sample()); }
        public GlobalPos origin() { return origin; }
        public int travelTicks(GlobalPos from, GlobalPos to) { return travel; }
        public void visual(GlobalPos from, GlobalPos to, ItemStack stack, int duration) {}
        @Override public void close() { for (var service : services) service.cancelAll(); FIXTURES.remove(origin); }
    }

    private static final class Driver implements ResourceProcessingAdapter {
        private static Fixture fixture(CraftingService.NetworkAccess access) { return FIXTURES.get(access.origin()); }
        public List<CraftRecipe<ResourceKey>> recipes(CraftingService.NetworkAccess access) {
            var f = fixture(access); if (f == null) return List.of();
            if (f.chanceFailures >= 0) return List.of(new CraftRecipe<>("astral_repository:regression/chance_brick", BRICK, 1, "fixture_chance",
                    List.of(new CraftRecipe.Ingredient<>(List.of(WATER), 250), CraftRecipe.Ingredient.of(CLAY)), 0, 4));
            return List.of(new CraftRecipe<>("astral_repository:regression/fluid_to_source", SOURCE, 50, "fixture_fluid", List.of(new CraftRecipe.Ingredient<>(List.of(WATER), 250)), 0, 4),
                    new CraftRecipe<>("astral_repository:regression/source_to_brick", BRICK, 1, "fixture_item", List.of(new CraftRecipe.Ingredient<>(List.of(SOURCE), 50), CraftRecipe.Ingredient.of(CLAY)), 0, 4));
        }
        public boolean supports(CraftingService.NetworkAccess access, GlobalPos at, CraftPlan.Node<ResourceKey> node) {
            var f = fixture(access); return f != null && (at.equals(f.processor) || at.equals(f.alias)) && (node.recipe().process().equals("fixture_fluid") || node.recipe().process().equals("fixture_item") || node.recipe().process().equals("fixture_chance"));
        }
        public boolean isProcessingPosition(CraftingService.NetworkAccess access, GlobalPos at) {
            var f = fixture(access); return f != null && (at.equals(f.processor) || at.equals(f.alias));
        }
        public Object processorIdentity(CraftingService.NetworkAccess access, GlobalPos at) { var f = fixture(access); return f == null ? at : f.machineEntity; }
        public CraftScheduler.Operation<ResourceKey> start(CraftingService.NetworkAccess access, GlobalPos at, CraftPlan.Node<ResourceKey> node) {
            var f = fixture(access); if (!supports(access, at, node)) return null;
            if (f.active > 0) throw new IllegalStateException("Two aliases attempted to start one physical fixture machine");
            boolean fluid = node.recipe().output().equals(SOURCE);
            boolean chance = node.recipe().process().equals("fixture_chance");
            if (fluid || chance) {
                if (!f.machineWater.isEmpty()) throw new IllegalStateException("Fixture input tank is occupied");
                f.machineWater.fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE);
                if (chance) {
                    if (!f.machine.getItem(0).isEmpty()) throw new IllegalStateException("Fixture chance input is occupied");
                    f.machine.setItem(0, new ItemStack(Items.CLAY_BALL));
                }
            } else {
                if (!f.machine.getItem(0).isEmpty() || f.scalarInMachine() != 0) throw new IllegalStateException("Fixture item/source input is occupied");
                f.machine.setItem(0, new ItemStack(Items.CLAY_BALL)); f.scalarInMachine(50);
            }
            f.starts++; f.active++; f.maxActive = Math.max(f.maxActive, f.active);
            return new CraftScheduler.Operation<>() {
                int ticks; boolean settled;
                public Map<ResourceKey, Long> poll() {
                    if (settled || f.hold || ++ticks < 4) return null;
                    Map<ResourceKey, Long> result;
                    if (chance) {
                        if (f.machineWater.drain(250, FluidAction.EXECUTE).getAmount() != 250 || !f.machine.getItem(0).is(Items.CLAY_BALL)) throw new IllegalStateException("Physical chance recipe inputs were lost");
                        f.machine.removeItem(0, 1); f.chanceAttempts++;
                        if (f.chanceAttempts <= f.chanceFailures) result = Map.of();
                        else { f.machine.setItem(1, new ItemStack(Items.BRICK)); result = Map.of(BRICK, (long)f.machine.removeItem(1, 1).getCount()); }
                    } else if (fluid) {
                        if (f.machineWater.drain(250, FluidAction.EXECUTE).getAmount() != 250) throw new IllegalStateException("Physical fixture water was lost");
                        f.scalarInMachine(50); result = Map.of(SOURCE, f.scalarInMachine()); f.scalarInMachine(0); f.fluidSteps++;
                    } else {
                        if (f.scalarInMachine() != 50 || !f.machine.getItem(0).is(Items.CLAY_BALL)) throw new IllegalStateException("Physical fixture item/source was lost");
                        f.scalarInMachine(0); f.machine.removeItem(0, 1); f.machine.setItem(1, new ItemStack(Items.BRICK));
                        result = Map.of(BRICK, (long)f.machine.removeItem(1, 1).getCount()); f.itemSteps++;
                    }
                    settled = true; f.active--; return result;
                }
                public Map<ResourceKey, Long> cancel() {
                    if (settled) return Map.of(); settled = true; f.active--;
                    if (fluid) return Map.of(WATER, (long)f.machineWater.drain(250, FluidAction.EXECUTE).getAmount());
                    Map<ResourceKey, Long> result = new HashMap<>();
                    if (chance && !f.machineWater.isEmpty()) result.put(WATER, (long)f.machineWater.drain(250, FluidAction.EXECUTE).getAmount());
                    if (f.scalarInMachine() > 0) result.put(SOURCE, f.scalarInMachine()); f.scalarInMachine(0);
                    if (f.machine.getItem(0).is(Items.CLAY_BALL)) result.put(CLAY, (long)f.machine.removeItem(0, 1).getCount());
                    return result;
                }
                public Map<ResourceKey, Long> recoverable() { return Map.of(); }
                public boolean mayReturnShortOutput() { return chance; }
            };
        }
    }
    private MixedResourceCraftingGameTests() {}
}
