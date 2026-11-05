package com.cappleapple.astralrepository.legacymektest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import static com.cappleapple.astralrepository.crafting.MekanismRecipeDiscovery.*;

/** Opt-in tests against the actual 10.4 native APIs, kept outside the distributable mod. */
@net.minecraftforge.fml.common.Mod("astral_legacy_mek_test")
@GameTestHolder("astral_legacy_mek_test")
@PrefixGameTestTemplate(false)
public final class ForgeLegacyMekanismGameTests {
    @GameTest(templateNamespace="astral_legacy_mek_test", template="empty_workshop", timeoutTicks=1600)
    public static void legacyEnergizedSmelterRunsRealProcessing(GameTestHelper h) {
        if (!enabled(h)) return;
        request(h, "mekanism:energized_smelter", List.of(new ItemStack(Items.RAW_IRON)), new ItemStack(Items.IRON_INGOT));
    }
    @GameTest(templateNamespace="astral_legacy_mek_test", template="empty_workshop", timeoutTicks=1600)
    public static void legacyEnrichmentChamberRunsRealProcessing(GameTestHelper h) {
        if (!enabled(h)) return;
        request(h, "mekanism:enrichment_chamber", List.of(item("mekanism:dirty_dust_iron")), item("mekanism:dust_iron"));
    }
    @GameTest(templateNamespace="astral_legacy_mek_test", template="empty_workshop", timeoutTicks=1600)
    public static void legacyMetallurgicInfuserConvertsAndConsumesNativeInfusion(GameTestHelper h) {
        if (!enabled(h)) return;
        ChemicalKey carbon = ChemicalKey.resolve(ResourceKinds.INFUSION, id("mekanism:carbon"));
        h.assertTrue(carbon.kind().equals(ResourceKinds.INFUSION) && carbon.sample().getClass().getName().endsWith("InfusionStack"), "Legacy infusion identity retains its distinct native chemical family");
        h.assertTrue(carbon.equals(ResourceKeyCodec.load(ResourceKeyCodec.save(carbon, h.getLevel().registryAccess()), h.getLevel().registryAccess())), "Legacy infusion identity survives journal serialization");
        request(h, "mekanism:metallurgic_infuser", List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COAL)), item("mekanism:enriched_iron"));
    }
    @GameTest(templateNamespace="astral_legacy_mek_test", template="empty_workshop", timeoutTicks=1600)
    public static void legacyElectrolysisProducesBothNativeGasFamilies(GameTestHelper h) {
        if (!enabled(h)) return;
        var at = new BlockPos(4, 2, 4); h.setBlock(at, block("mekanism:electrolytic_separator"));
        var fixture = fixture(h, at, new ItemStack(Items.PAPER)); var adapter = new MekanismProcessingAdapter();
        var recipe = adapter.recipes(fixture).stream().filter(value -> value.id().endsWith("/mekanism:separator/water")).findFirst().orElseThrow();
        var selected = recipe.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().get(0), input.count())).toList();
        var operation = adapter.start(fixture, fixture.at(fixture.machine), new CraftPlan.Node<>(0, recipe, selected, Set.of()));
        h.assertTrue(operation != null, "The actual separator accepts the selected native fluid input");
        final Map<ResourceKey, Long>[] result = new Map[]{null};
        h.onEachTick(() -> { power(h.getBlockEntity(at)); if (result[0] == null) result[0] = operation.poll(); });
        h.succeedWhen(() -> {
            h.assertTrue(result[0] != null, "The real separator finishes processing");
            var hydrogen = ChemicalKey.resolve(ResourceKinds.GAS, id("mekanism:hydrogen")); var oxygen = ChemicalKey.resolve(ResourceKinds.GAS, id("mekanism:oxygen"));
            h.assertTrue(result[0].getOrDefault(hydrogen, 0L) == 2 && result[0].getOrDefault(oxygen, 0L) == 1, "Both actual gas coproducts retain their distinct identities and exact native units");
            h.assertTrue(hydrogen.equals(ResourceKeyCodec.load(ResourceKeyCodec.save(hydrogen, h.getLevel().registryAccess()), h.getLevel().registryAccess())), "Legacy gas identity survives journal serialization");
            h.assertTrue(operation.cancel().isEmpty(), "The completed native operation cannot yield a duplicate refund");
        });
    }
    @GameTest(templateNamespace="astral_legacy_mek_test", template="empty_workshop")
    public static void legacyFloatingLongPowerUsesConfiguredNativeConversionWithoutTruncatingJoules(GameTestHelper h) {
        if (!enabled(h)) return;
        var at = new BlockPos(4, 2, 4); h.setBlock(at, block("mekanism:energized_smelter"));
        Object tile = h.getBlockEntity(at), energy = invoke(tile, "getEnergyContainer");
        Object nativeOne = MekanismResourceSupport.energyFromNetwork(1);
        h.assertTrue(nativeOne.getClass().getName().equals("mekanism.api.math.FloatingLong") && MekanismResourceSupport.energyToNetwork(nativeOne) == 1, "A single network FE converts through the native fractional FloatingLong API and back exactly");
        Object empty = MekanismResourceSupport.energyFromNetwork(0); callCompatible(energy, "setEnergy", empty);
        var port = MekanismMachineAccess.ports(tile).stream().filter(value -> value.kind.equals("energy")).findFirst().orElseThrow();
        h.assertTrue(port.insert(ResourceKinds.FE, 1, true) == 1 && port.amount() == 0, "Simulated native energy insertion is reversible");
        h.assertTrue(port.insert(ResourceKinds.FE, 1, false) == 1 && port.amount() == 1, "One FE physically inserts without truncating its native joule fraction");
        h.assertTrue(port.extract(1, false) == 1 && port.amount() == 0, "Native extraction returns exactly the same single FE");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_legacy_mek_test", template="empty_workshop")
    public static void legacyNativeRecipeFamiliesHaveExplicitCatalogCoverage(GameTestHelper h) {
        if (!enabled(h)) return;
        var machine = new BlockPos(4, 2, 4); h.setBlock(machine, block("mekanism:energized_smelter"));
        var fixture = fixture(h, machine, new ItemStack(Items.PAPER));
        var catalog = new MekanismProcessingAdapter().recipes(fixture);
        List<Map<String, Object>> rows = new ArrayList<>(); List<String> missing = new ArrayList<>();
        for (var entry : MekanismRecipeDiscovery.recipes(h.getLevel())) {
            boolean incomplete = hasMethod(entry.recipe(), "isIncomplete") && Boolean.TRUE.equals(invoke(entry.recipe(), "isIncomplete"));
            long aliases = catalog.stream().filter(recipe -> recipe.id().endsWith("/" + entry.id())).count();
            var row = new LinkedHashMap<String, Object>(); row.put("type", entry.type()); row.put("id", entry.id()); row.put("class", entry.recipe().getClass().getName()); row.put("incomplete", incomplete); row.put("aliases", aliases);
            row.put("inputs", inputs(entry.recipe()).stream().map(input -> Map.of("kind", input.resource(), "getter", input.name(), "representations", input.representations().size())).toList()); rows.add(row);
            if (!incomplete && aliases == 0) missing.add(entry.type() + "/" + entry.id());
        }
        try { java.nio.file.Files.writeString(java.nio.file.Path.of("legacy-mekanism-catalog-audit.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("recipes", rows.size(), "aliases", catalog.size(), "missing", missing, "entries", rows))); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Could not write legacy native recipe audit", failure); }
        h.assertTrue(!rows.isEmpty() && missing.isEmpty(), "Every valid legacy native recipe has an explicit catalog alias: " + missing);
        h.succeed();
    }
    @GameTest(templateNamespace="astral_legacy_mek_test", template="empty_workshop", timeoutTicks=1600)
    public static void legacyPurifierUsesItsNativeConstantGasBudget(GameTestHelper h) {
        if (!enabled(h)) return;
        perTick(h, "mekanism:purification_chamber", "/mekanism:processing/iron/clump/from_raw_ore", false);
    }
    @GameTest(templateNamespace="astral_legacy_mek_test", template="empty_workshop", timeoutTicks=1600)
    public static void legacyDissolverTopsUpRealGasAfterItsLiveUsageIncreases(GameTestHelper h) {
        if (!enabled(h)) return;
        perTick(h, "mekanism:chemical_dissolution_chamber", "/mekanism:processing/iron/slurry/dirty/from_raw_ore", true);
    }
    private static void perTick(GameTestHelper h, String machineId, String suffix, boolean raiseUsage) {
        var at = new BlockPos(4, 2, 4); h.setBlock(at, block(machineId));
        var f = fixture(h, at, new ItemStack(Items.PAPER)); var adapter = new MekanismProcessingAdapter();
        var recipe = adapter.recipes(f).stream().filter(value -> value.id().endsWith(suffix)).findFirst().orElseThrow();
        var selected = recipe.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().get(0), input.count())).toList();
        var gas = selected.stream().filter(input -> input.key() instanceof ChemicalKey).findFirst().orElseThrow();
        var nativeRecipe = MekanismRecipeDiscovery.recipes(h.getLevel()).stream().filter(entry -> entry.id().equals(suffix.substring(1))).findFirst().orElseThrow().recipe();
        var profile = MekanismUsageProfile.profile(nativeRecipe, h.getBlockEntity(at));
        Object nativeGas = ((ChemicalKey)gas.key()).sample();
        long ingredientUnits = inputs(nativeRecipe).stream().filter(input -> input.resource().equals("chemical") && input.accepts(nativeGas)).findFirst().orElseThrow().amount(nativeGas);
        h.assertTrue(profile.budget() > 1 && gas.count() == Math.multiplyExact(ingredientUnits, profile.budget()), "The ingredient amount multiplies the native per-machine usage budget exactly once");
        final long supplied = 100_000;
        var source = new BlockPos(1, 2, 4); h.setBlock(source, block("mekanism:ultimate_chemical_tank"));
        Object tank = invoke(h.getBlockEntity(source), "getGasTank");
        callCompatible(tank, "setStack", ((ChemicalKey)gas.key()).sampleWithAmount(supplied));
        f.resourceProviders.addAll(com.cappleapple.astralrepository.compat.CompatibilityRegistry.discoverResources(h.getLevel(), h.absolutePos(source), null));
        h.assertTrue(f.resourceSnapshot().getOrDefault(gas.key(), 0L) == supplied, "The native gas source is discovered through the actual typed storage provider");
        for (var input : selected) if (input.key() instanceof ItemKey item) f.insert(item.sample().copyWithCount((int)input.count()));
        for (var input : selected) h.assertTrue(f.reserveResource(input.key(), input.count(), (from, amount) -> {}) == input.count(), "The initial per-tick recipe budget is physically reserved");
        var operation = adapter.start(f, f.at(f.machine), new CraftPlan.Node<>(0, recipe, selected, Set.of()));
        h.assertTrue(operation != null, "The native per-tick machine accepts its item and gas inputs");
        final Map<ResourceKey, Long>[] result = new Map[]{null}; int[] ticks = {0}; boolean[] refundAttempted = {false}; long[] refundLeft = {0};
        h.onEachTick(() -> {
            power(h.getBlockEntity(at));
            if (raiseUsage && ++ticks[0] == 20) {
                try { java.lang.invoke.MethodHandles.publicLookup().findVarHandle(h.getBlockEntity(at).getClass(), "injectUsage", double.class).set(h.getBlockEntity(at), 100D); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native usage fixture API changed", failure); }
            }
            if (result[0] == null) result[0] = operation.poll();
        });
        h.succeedWhen(() -> {
            h.assertTrue(result[0] != null && result[0].getOrDefault(recipe.output(), 0L) >= recipe.outputCount(), "Actual native per-tick processing completes with its physical output");
            if (raiseUsage) h.assertTrue(f.resourceReserved.getOrDefault(gas.key(), 0L) > gas.count(), "A live usage increase extracts supplemental gas from the real native source");
            long returned = result[0].getOrDefault(gas.key(), 0L);
            if (returned > 0 && !refundAttempted[0]) { refundAttempted[0] = true; refundLeft[0] = f.insertResourceFrom(gas.key(), returned, f.origin()); }
            h.assertTrue(refundLeft[0] == 0, "Unused actual gas returns to its real source tank");
            long consumed = f.resourceReserved.get(gas.key()) - returned;
            h.assertTrue(consumed > 1 && f.resourceSnapshot().getOrDefault(gas.key(), 0L) == supplied - consumed, "Initial reservation, supplemental extraction, native consumption and refund conserve exact gas units");
            h.assertTrue(operation.cancel().isEmpty(), "Completed per-tick processing cannot refund consumed gas");
        });
    }
    private static void request(GameTestHelper h, String machineId, List<ItemStack> inputs, ItemStack target) {
        var machine = new BlockPos(4, 2, 4); h.setBlock(machine, block(machineId));
        var fixture = fixture(h, machine, target);
        for (int slot = 0; slot < inputs.size(); slot++) fixture.inventory.setItem(slot, inputs.get(slot).copy());
        var service = new CraftingService(fixture); var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "legacy-mekanism"));
        var request = service.request(player, target, 1); h.assertTrue(request.accepted(), "The real native item request is accepted");
        h.onEachTick(() -> { power(h.getBlockEntity(machine)); service.tick(); });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0, "Actual legacy machine processing completes: " + service.statuses());
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(target), 0L) == 1, "The exact actual item reaches physical storage");
            for (var input : inputs) h.assertTrue(!fixture.snapshot().containsKey(new ItemKey(input)), "Native processing consumes each real initial input");
            h.assertTrue(!CraftingService.isProcessorReserved(fixture.at(fixture.machine)), "Native processor ownership is released");
        });
    }
    private static Fixture fixture(GameTestHelper h, BlockPos machine, ItemStack target) {
        var chest = new BlockPos(1, 2, 1); h.setBlock(chest, Blocks.BARREL);
        return new Fixture(h.getLevel(), h.absolutePos(chest), h.absolutePos(machine), (Container)h.getBlockEntity(chest), target);
    }
    private static void power(Object tile) { Object energy = invoke(tile, "getEnergyContainer"); if (energy != null) callCompatible(energy, "setEnergy", invoke(energy, "getMaxEnergy")); }
    private static boolean enabled(GameTestHelper h) { if (!Boolean.getBoolean("astral_repository.legacyMekTest")) { h.succeed(); return false; } h.assertTrue(net.minecraftforge.fml.ModList.get().isLoaded("mekanism"), "The legacy profile includes actual Mekanism10.4"); return true; }
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static ItemStack item(String value) { var key = id(value); if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Missing fixture item " + value); return new ItemStack(BuiltInRegistries.ITEM.get(key)); }
    private static net.minecraft.world.level.block.Block block(String value) { var key = id(value); if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException("Missing fixture block " + value); return BuiltInRegistries.BLOCK.get(key); }
    private static final class Fixture implements CraftingService.NetworkAccess {
        final ServerLevel level; final BlockPos origin, machine; final Container inventory; final ItemStack target;
        final List<ResourceProvider> resourceProviders = new ArrayList<>(); final Map<ResourceKey, Long> resourceReserved = new HashMap<>();
        Fixture(ServerLevel level, BlockPos origin, BlockPos machine, Container inventory, ItemStack target) { this.level=level; this.origin=origin; this.machine=machine; this.inventory=inventory; this.target=target; }
        GlobalPos at(BlockPos position) { return GlobalPos.of(level.dimension(), position); }
        public ServerLevel level() { return level; }
        public Map<ItemKey, Long> snapshot() { var result = new HashMap<ItemKey, Long>(); for (int slot=0;slot<inventory.getContainerSize();slot++) { var stack=inventory.getItem(slot); if(!stack.isEmpty()) result.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum); } return Map.copyOf(result); }
        public Map<ResourceKey, Long> resourceSnapshot() { Map<ResourceKey, Long> result = new HashMap<>(snapshot()); for (var provider : resourceProviders) provider.snapshot().forEach((key, amount) -> result.merge(key, amount, Long::sum)); return Map.copyOf(result); }
        public long reserveResource(ResourceKey key, long amount, java.util.function.BiConsumer<GlobalPos, Long> receipt) {
            if (key instanceof ItemKey) return CraftingService.NetworkAccess.super.reserveResource(key, amount, receipt);
            long taken = 0; for (var provider : resourceProviders) if (provider.resourceType().equals(key.type()) && taken < amount) taken += provider.extract(key, amount - taken, false);
            if (taken > 0) { resourceReserved.merge(key, taken, Math::addExact); receipt.accept(origin(), taken); } return taken;
        }
        public long insertResourceFrom(ResourceKey key, long amount, GlobalPos source) {
            if (key instanceof ItemKey) return CraftingService.NetworkAccess.super.insertResourceFrom(key, amount, source);
            long left = amount; for (var provider : resourceProviders) if (provider.resourceType().equals(key.type()) && left > 0) left -= provider.insert(key, left, false); return left;
        }
        public ItemStack extract(ItemKey key,int amount) { for(int slot=0;slot<inventory.getContainerSize();slot++) if(!inventory.getItem(slot).isEmpty()&&new ItemKey(inventory.getItem(slot)).equals(key)) return inventory.removeItem(slot,amount); return ItemStack.EMPTY; }
        public ItemStack insert(ItemStack stack) { return net.minecraftforge.items.ItemHandlerHelper.insertItem(new net.minecraftforge.items.wrapper.InvWrapper(inventory),stack,false); }
        public List<GlobalPos> workstations() { return List.of(at(machine)); }
        public List<ItemStack> exposedProducts() { return List.of(target.copy()); }
        public GlobalPos origin() { return at(origin); }
        public int travelTicks(GlobalPos from, GlobalPos to) { return 0; }
        public void visual(GlobalPos from, GlobalPos to, ItemStack stack, int ticks) {}
    }
}
