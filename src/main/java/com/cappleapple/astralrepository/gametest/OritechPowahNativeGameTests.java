package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.*;
import com.mojang.authlib.GameProfile;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.call;

/** Actual native machines, physical stock, and the production coordinator. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class OritechPowahNativeGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1800)
    public static void genericOritechFoundryUsesTwoNativeIngredientRolesForEachQueuedCycle(GameTestHelper h) {
        if (!enabled(h, "oritech")) return;
        BlockPos machine = new BlockPos(4, 2, 4); form(h, machine, "oritech:foundry_block");
        ItemStack target = item("oritech:electrum_ingot");
        Fixture fixture = fixture(h, List.of(machine), target);
        fixture.chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 2)); fixture.chest.setItem(1, new ItemStack(Items.REDSTONE, 2));
        var service = new CraftingService(fixture); request(h, service, target, 2);
        h.onEachTick(() -> { power(h, machine); service.tick(); });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0 && fixture.snapshot().getOrDefault(new ItemKey(target), 0L) == 2, "Generic native foundry completes both physical cycles: " + service.statuses());
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.GOLD_INGOT)), 0L) == 0 && fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.REDSTONE)), 0L) == 0, "Exactly two of each distinct ingredient are consumed");
            h.assertTrue(!CraftingService.isProcessorReserved(fixture.at(machine)), "Native foundry claim is released");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=2000)
    public static void genericOritechRefineryConsumesPhysicalFluidAndReturnsItsFluidCoproduct(GameTestHelper h) {
        if (!enabled(h, "oritech")) return;
        BlockPos machine = new BlockPos(4, 2, 4); form(h, machine, "oritech:refinery_block");
        ItemStack target = item("oritech:iron_clump"); Fixture fixture = fixture(h, List.of(machine), target);
        fixture.chest.setItem(0, new ItemStack(Items.RAW_IRON));
        var source = fixture.addTank(new BlockPos(1, 2, 2)); var coproduct = fixture.addTank(new BlockPos(1, 2, 3));
        FluidStack sheol = new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("oritech:still_sheol_fire")), 500);
        source.tank().fill(sheol, FluidAction.EXECUTE);
        var service = new CraftingService(fixture); request(h, service, target, 2);
        h.onEachTick(() -> { power(h, machine); service.tick(); });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0 && fixture.snapshot().getOrDefault(new ItemKey(target), 0L) == 2, "Native refinery's mixed recipe reaches storage: " + service.statuses());
            h.assertTrue(source.tank().getFluidAmount() == 250 && source.tank().getFluid().getFluid() == sheol.getFluid(), "The physical source loses exactly 250 mB");
            h.assertTrue(coproduct.tank().getFluidAmount() == 100 && coproduct.tank().getFluid().getFluid() == Fluids.LAVA, "The native 100 mB lava coproduct is collected and stored physically");
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)), 0L) == 0, "One real raw iron is consumed");
            h.assertTrue(!CraftingService.isProcessorReserved(fixture.at(machine)), "Refinery and port ownership are released");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1800)
    public static void nativeOritechExecutableProviderOverridesMisleadingUnusedGetter(GameTestHelper h) {
        if (!enabled(h, "oritech")) return;
        BlockPos machine = new BlockPos(4, 2, 4); form(h, machine, "oritech:powered_furnace_block");
        ItemStack target = new ItemStack(Items.IRON_INGOT); Fixture fixture = fixture(h, List.of(machine), target);
        fixture.chest.setItem(0, new ItemStack(Items.RAW_IRON));
        OritechBackend backend = new OritechBackend(); var nativeMachine = backend.machine(fixture, fixture.at(machine));
        var recipes = backend.recipes(fixture, List.of(nativeMachine));
        // The Oritech getter returns ASSEMBLER, but its actual serverTick uses vanilla smelting.
        h.assertTrue(recipes.stream().noneMatch(recipe -> recipe.process().contains("assembler") && nativeMachine.accepts(recipe)), "Unused native getter does not advertise an executable assembler process");
        var service = new CraftingService(fixture); request(h, service, target, 1);
        h.onEachTick(() -> { power(h, machine); service.tick(); });
        h.succeedWhen(() -> h.assertTrue(service.activeJobs() == 0 && fixture.snapshot().getOrDefault(new ItemKey(target), 0L) == 1, "Actual powered furnace executes its native smelting provider: " + service.statuses()));
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=2400)
    public static void queuedPowahRequestsNeverOverfillOneItemNativeIngredientSlots(GameTestHelper h) {
        if (!enabled(h, "powah")) return;
        BlockPos orb = new BlockPos(4, 2, 4), rod = new BlockPos(4, 2, 7);
        h.setBlock(orb, block("powah:energizing_orb")); h.setBlock(rod, block("powah:energizing_rod_nitro"));
        Object nativeOrb = h.getBlockEntity(orb), nativeRod = h.getBlockEntity(rod);
        call(nativeRod, "setOrbPos", h.absolutePos(orb));
        ItemStack target = item("powah:steel_energized"); Fixture fixture = fixture(h, List.of(orb), target);
        fixture.chest.setItem(0, new ItemStack(Items.IRON_INGOT, 4)); fixture.chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 4));
        var service = new CraftingService(fixture); request(h, service, target, 4); request(h, service, target, 4);
        int[] observations = {0}, nativeCycles = {0};
        h.onEachTick(() -> {
            Object inventory = call(nativeOrb, "getInventory"); int slots = ((Number)call(inventory, "getSlots")).intValue();
            for (int index = 0; index < slots; index++) {
                ItemStack content = (ItemStack)call(inventory, "getStackInSlot", index);
                if (!content.isEmpty() && Boolean.TRUE.equals(call(nativeOrb, "canExtract", index, content))) {
                    h.assertTrue(content.is(target.getItem()) && content.getCount() == 2, "Every actual orb completion produces its native two-item output");
                    nativeCycles[0]++;
                }
            }
            power(h, rod); service.tick();
            for (int index = 0; index < slots; index++) {
                ItemStack content = (ItemStack)call(inventory, "getStackInSlot", index);
                if (content.isEmpty() || Boolean.TRUE.equals(call(nativeOrb, "canExtract", index, content))) continue;
                h.assertTrue(content.getCount() == 1 && ((Number)call(nativeOrb, "getSlotLimit", index)).intValue() == 1, "Every native input contains exactly one ingredient even while two larger requests are queued");
                observations[0]++;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0 && fixture.snapshot().getOrDefault(new ItemKey(target), 0L) == 8, "Both queued requests finish through real rods and the native orb: " + service.statuses());
            h.assertTrue(observations[0] >= 4 && nativeCycles[0] == 4, "Four real one-recipe cycles each return their native two-item result; input observations=" + observations[0] + ", physical completions=" + nativeCycles[0]);
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 0 && fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.GOLD_INGOT)), 0L) == 0, "Exactly four of each physical ingredient are consumed");
            h.assertTrue(!CraftingService.isProcessorReserved(fixture.at(orb)), "One physical orb claim is released");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=700)
    public static void cancellingUnpoweredPowahCycleRefundsArrivedAndQueuedInputsOnce(GameTestHelper h) {
        if (!enabled(h, "powah")) return;
        BlockPos orb = new BlockPos(4, 2, 4); h.setBlock(orb, block("powah:energizing_orb"));
        ItemStack target = item("powah:steel_energized"); Fixture fixture = fixture(h, List.of(orb), target);
        fixture.chest.setItem(0, new ItemStack(Items.IRON_INGOT, 4)); fixture.chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 4));
        var service = new CraftingService(fixture); var request = service.request(player(h), target, 8);
        h.assertTrue(request.accepted(), "The native orb request is accepted");
        boolean[] cancelled = {false}; int[] after = {0};
        h.onEachTick(() -> {
            service.tick();
            if (cancelled[0]) { after[0]++; return; }
            Object inventory = call(h.getBlockEntity(orb), "getInventory"); int count = 0;
            for (int slot = 0; slot < ((Number)call(inventory, "getSlots")).intValue(); slot++) count += ((ItemStack)call(inventory, "getStackInSlot", slot)).getCount();
            if (count == 2) { h.assertTrue(service.cancel(request.jobId()) && !service.cancel(request.jobId()), "Only one cancellation owns the arrived native inputs"); cancelled[0] = true; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(cancelled[0] && after[0] >= 40, "The native cycle was cancelled after physical arrival");
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 4 && fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.GOLD_INGOT)), 0L) == 4, "Arrived and unstarted ingredients are refunded exactly once");
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(target), 0L) == 0 && service.activeProcessors() == 0, "Cancellation invents no output and releases the orb");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void completeNativeOritechAndPowahCatalogHasExplicitBoundaryDiagnostics(GameTestHelper h) throws Exception {
        if (!enabled(h, "oritech", "powah")) return;
        Fixture fixture = fixture(h, List.of(), new ItemStack(Items.PAPER));
        OritechBackend oritech = new OritechBackend(); PowahBackend powah = new PowahBackend();
        var oritechRecipes = oritech.recipes(fixture, List.of()); var powahRecipes = powah.recipes(fixture, List.of());
        Map<String, Long> types = new TreeMap<>(); List<Map<String, Object>> rows = new ArrayList<>();
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            var type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            if (type == null || !(type.getNamespace().equals("oritech") || type.getNamespace().equals("powah"))) continue;
            String id = holder.id().toString(); Map<String, String> reasons = type.getNamespace().equals("oritech") ? oritech.diagnostics() : powah.diagnostics();
            h.assertTrue(reasons.containsKey(id), "Every native recipe, including player research and generator data, has a diagnosis: " + id);
            types.merge(type.toString(), 1L, Long::sum);
            rows.add(Map.of("id", id, "type", type.toString(), "class", holder.value().getClass().getName(), "diagnostic", reasons.get(id)));
        }
        h.assertTrue(!oritechRecipes.isEmpty() && !powahRecipes.isEmpty(), "Both actual native catalogs project recipes");
        h.assertTrue(oritech.diagnostics().values().stream().anyMatch(reason -> reason.contains("research")) && oritech.diagnostics().values().stream().anyMatch(reason -> reason.contains("burn/world")), "Non-inventory native records are reported explicitly");
        Path output = Path.of(System.getProperty("astral_repository.genericTechOutput", "../build/generic-processing/runtime")); Files.createDirectories(output);
        Files.writeString(output.resolve("oritech-powah-native-audit.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status", "PASS", "native_types", types, "native_recipes", rows.size(), "oritech_projected", oritechRecipes.size(), "powah_projected", powahRecipes.size(), "recipes", rows)) + "\n");
        h.succeed();
    }

    private static boolean enabled(GameTestHelper h, String... mods) {
        if (!Boolean.getBoolean("astral_repository.genericTechTest")) { h.succeed(); return false; }
        for (String mod : mods) h.assertTrue(ModList.get().isLoaded(mod), "The generic native gate requires " + mod);
        return true;
    }
    private static Block block(String id) { ResourceLocation key = ResourceLocation.parse(id); if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException("Missing native fixture " + id); return BuiltInRegistries.BLOCK.get(key); }
    private static ItemStack item(String id) { ResourceLocation key = ResourceLocation.parse(id); if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Missing native item " + id); return new ItemStack(BuiltInRegistries.ITEM.get(key)); }
    private static ServerPlayer player(GameTestHelper h) { return new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "native-tech-fixture"), ClientInformation.createDefault()); }
    private static void request(GameTestHelper h, CraftingService service, ItemStack target, int count) { var request = service.request(player(h), target, count); h.assertTrue(request.accepted(), "Native request accepted: " + request.message()); }
    private static void power(GameTestHelper h, BlockPos relative) {
        for (Direction side : Direction.values()) { var energy = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(relative), side); if (energy != null) energy.receiveEnergy(Integer.MAX_VALUE, false); }
    }
    private static void form(GameTestHelper h, BlockPos relative, String id) {
        h.setBlock(relative, block(id)); Object machine = h.getBlockEntity(relative);
        for (int count = 0; count < 64; count++) { Object next = call(machine, "getNextMissingCore"); if (!(next instanceof BlockPos core)) break; h.getLevel().setBlockAndUpdate(core, block("oritech:machine_core_1").defaultBlockState()); }
        h.assertTrue(Boolean.TRUE.equals(call(machine, "initMultiblock", h.getBlockState(relative))), "Actual Oritech cores form the native machine");
    }
    private static Fixture fixture(GameTestHelper h, List<BlockPos> processors, ItemStack target) { BlockPos chest = new BlockPos(1, 2, 1); h.setBlock(chest, Blocks.BARREL); return new Fixture(h, chest, (Container)h.getBlockEntity(chest), processors, target); }
    private static final class Fixture extends CraftingGameTests.Fixture {
        final List<CrystalNodeBlockEntity> tanks = new ArrayList<>();
        Fixture(GameTestHelper helper, BlockPos origin, Container chest, List<BlockPos> processors, ItemStack target) { super(helper, origin, chest, processors, target); }
        CrystalNodeBlockEntity addTank(BlockPos relative) { helper.setBlock(relative, AstralContent.SEED_STORAGE_CRYSTAL.get()); var crystal = (CrystalNodeBlockEntity)helper.getBlockEntity(relative); tanks.add(crystal); return crystal; }
        @Override public Map<ResourceKey, Long> resourceSnapshot() {
            Map<ResourceKey, Long> result = new HashMap<>(snapshot());
            for (var crystal : tanks) { FluidStack fluid = crystal.tank().getFluid(); if (!fluid.isEmpty()) result.merge(new FluidKey(fluid), (long)fluid.getAmount(), Long::sum); }
            return result;
        }
        @Override public long reserveResource(ResourceKey key, long count, BiConsumer<GlobalPos, Long> receipt) {
            if (key instanceof ItemKey item) { ItemStack taken = extract(item, Math.toIntExact(count)); if (!taken.isEmpty()) receipt.accept(origin(), (long)taken.getCount()); return taken.getCount(); }
            if (!(key instanceof FluidKey fluid)) return 0;
            long result = 0;
            for (var crystal : tanks) { int wanted = (int)Math.min(Integer.MAX_VALUE, count - result); if (wanted == 0) break; FluidStack taken = crystal.tank().drain(fluid.sample().copyWithAmount(wanted), FluidAction.EXECUTE); if (!taken.isEmpty()) { hKey(key, taken); result += taken.getAmount(); receipt.accept(GlobalPos.of(level().dimension(), crystal.getBlockPos()), (long)taken.getAmount()); } }
            return result;
        }
        private static void hKey(ResourceKey key, FluidStack stack) { if (!new FluidKey(stack).equals(key)) throw new IllegalStateException("Physical test tank changed identity"); }
        @Override public long insertResourceFrom(ResourceKey key, long count, GlobalPos source) {
            if (key instanceof ItemKey item) return insert(item.sample().copyWithCount(Math.toIntExact(count))).getCount();
            if (!(key instanceof FluidKey fluid)) return count;
            long remaining = count;
            for (var crystal : tanks) { int offered = (int)Math.min(Integer.MAX_VALUE, remaining); remaining -= crystal.tank().fill(fluid.sample().copyWithAmount(offered), FluidAction.EXECUTE); if (remaining == 0) break; }
            return remaining;
        }
        @Override public int travelTicks(GlobalPos from, GlobalPos to) { return 0; }
    }
    private OritechPowahNativeGameTests() {}
}
