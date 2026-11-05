package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static com.cappleapple.astralrepository.crafting.MekanismRecipeDiscovery.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class MekanismGenericProcessingGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeCrusherAndFactoryUseAutomaticallyDiscoveredSlots(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos machine = new BlockPos(4, 2, 4); h.setBlock(machine, block("mekanism:basic_crushing_factory"));
        request(h, machine, List.of(new ItemStack(Items.IRON_INGOT, 2)), item("mekanism:dust_iron"), 2);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeCombinerHonorsMainExtraRolesAndCounts(GameTestHelper h) {
        if (!enabled(h)) return;
        direct(h, "mekanism:combiner", "/mekanism:combining/obsidian", false);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeInfuserChainsItemToChemicalConversion(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos machine = new BlockPos(4, 2, 4); h.setBlock(machine, block("mekanism:metallurgic_infuser"));
        request(h, machine, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COAL)), item("mekanism:enriched_iron"), 1);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeElectrolysisCollectsBothChemicalOutputs(GameTestHelper h) {
        if (!enabled(h)) return;
        direct(h, "mekanism:electrolytic_separator", "/mekanism:separator/water", false);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeChemicalCrystallizerConsumesChemicalInput(GameTestHelper h) {
        if (!enabled(h)) return;
        direct(h, "mekanism:chemical_crystallizer", "/mekanism:crystallizing/salt", false);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeReactionCollectsItemAndChemicalCoproducts(GameTestHelper h) {
        if (!enabled(h)) return;
        direct(h, "mekanism:pressurized_reaction_chamber", "/mekanism:reaction/coal_gasification/coals", false);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeRotaryUsesItsSelectedConversionDirection(GameTestHelper h) {
        if (!enabled(h)) return;
        direct(h, "mekanism:rotary_condensentrator", "/mekanism:rotary/oxygen", true);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeSawmillReturnsOnlyActualChanceProducts(GameTestHelper h) {
        if (!enabled(h)) return;
        direct(h, "mekanism:precision_sawmill", "/mekanism:sawing/log/oak", false);
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=7200, batch="mekanism_chance")
    public static void nativeChanceOnlyRequestRetriesUnderTheSameJob(GameTestHelper h) {
        if (!enabled(h)) return;
        try {
            Field random = Class.forName("mekanism.api.recipes.SawmillRecipe").getDeclaredField("RANDOM"); random.setAccessible(true);
            ((net.minecraft.util.RandomSource) random.get(null)).setSeed(12345L);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot seed native chance fixture", failure); }
        BlockPos machine = new BlockPos(4, 2, 4); h.setBlock(machine, block("mekanism:precision_sawmill"));
        var target = item("mekanism:sawdust"); var fixture = fixture(h, machine, target); fixture.chest.setItem(0, new ItemStack(Items.OAK_LOG, 32));
        var service = new CraftingService(fixture);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "mekanism-chance"), ClientInformation.createDefault());
        var request = service.request(player, target, 1); h.assertTrue(request.accepted(), "Chance-only native output can be requested");
        h.onEachTick(() -> { power(h, machine); service.tick(); h.assertTrue(service.statuses().stream().allMatch(status -> status.id().equals(request.jobId())), "Retries preserve the original job identity"); });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0, "Native chance request completes: " + service.statuses());
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(target), 0L) >= 1, "Only actual native sawdust satisfies the request");
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_LOG)), 0L) < 32, "Native attempts consume real logs");
        });
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeChemicalConverterPreservesPreexistingTankContents(GameTestHelper h) { if (enabled(h)) converterBaseline(h, false); }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeEnergyConverterPreservesPreexistingCharge(GameTestHelper h) { if (enabled(h)) converterBaseline(h, true); }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeChemicalInfuserConsumesTwoChemicalInputs(GameTestHelper h) { if (enabled(h)) direct(h, "mekanism:chemical_infuser", "/mekanism:chemical_infusing/hydrogen_chloride", false); }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeWasherConsumesFluidAndChemicalInputs(GameTestHelper h) { if (enabled(h)) direct(h, "mekanism:chemical_washer", "/mekanism:processing/copper/slurry/clean", false); }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeDissolutionStreamsPerTickChemicalConsumption(GameTestHelper h) { if (enabled(h)) direct(h, "mekanism:chemical_dissolution_chamber", "/mekanism:processing/copper/slurry/dirty/from_ore", false); }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1600)
    public static void nativeCentrifugeConsumesChemicalAndProducesChemical(GameTestHelper h) { if (enabled(h)) direct(h, "mekanism:isotopic_centrifuge", "/mekanism:processing/lategame/plutonium", false); }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=2000, batch="mekanism_fixed_cost")
    public static void nativeGasCostVariantsChooseTheFeasibleEfficientMachine(GameTestHelper h) {
        if (!enabled(h)) return;
        try {
            Object usage = Class.forName("mekanism.common.config.MekanismConfig").getField("usage").get(null);
            Object setting = usage.getClass().getField("randomizedConsumption").get(usage); boolean before = (Boolean) invoke(setting, "get");
            h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                private void restore() { invoke(setting, "set", new Class<?>[]{boolean.class}, before); }
                public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
                public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
                public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
                public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old, net.minecraft.gametest.framework.GameTestInfo next, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            });
            invoke(setting, "set", new Class<?>[]{boolean.class}, false);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot select native fixed chemical consumption fixture", failure); }
        BlockPos hungry = new BlockPos(4, 2, 4), efficient = new BlockPos(6, 2, 4), chestPos = new BlockPos(1, 2, 1);
        h.setBlock(hungry, block("mekanism:purification_chamber")); h.setBlock(efficient, block("mekanism:purification_chamber")); h.setBlock(chestPos, Blocks.BARREL);
        Object efficientTile = h.getBlockEntity(efficient), hungryHandler = MekanismMachineAccess.handler(h.getBlockEntity(hungry));
        try {
            Class<?> api = Class.forName("mekanism.api.Upgrade"); Object upgrade;
            try { upgrade = api.getField("CHEMICAL").get(null); }
            catch (NoSuchFieldException legacy) { upgrade = api.getField("GAS").get(null); }
            Object component = invoke(efficientTile, "getComponent");
            invoke(component, "addUpgrades", new Class<?>[]{api, int.class}, upgrade, 8);
            invoke(efficientTile, "recalculateUpgrades", new Class<?>[]{api}, upgrade);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot install native gas upgrades in fixture", failure); }
        Object efficientHandler = MekanismMachineAccess.handler(efficientTile);
        Object rawRecipe = MekanismRecipeDiscovery.recipes(h.getLevel()).stream().filter(entry -> entry.id().equals("mekanism:processing/iron/clump/from_raw_ore")).findFirst().orElseThrow().recipe();
        long cheap = MekanismUsageProfile.profile(rawRecipe, efficientHandler).budget(), expensive = MekanismUsageProfile.profile(rawRecipe, hungryHandler).budget();
        h.assertTrue(cheap < expensive, "Native gas upgrades create a genuinely cheaper physical processing contract");
        ResourceKey oxygen = ChemicalKey.resolve(ResourceKinds.CHEMICAL, ResourceLocation.parse("mekanism:oxygen"));
        Map<ResourceKey, Long> resources = new HashMap<>(); resources.put(oxygen, cheap);
        Container chest = (Container) h.getBlockEntity(chestPos); chest.setItem(0, new ItemStack(Items.RAW_IRON)); ItemStack target = item("mekanism:clump_iron");
        var fixture = new CraftingGameTests.Fixture(h, chestPos, chest, List.of(hungry, efficient), target) {
            @Override public Map<ResourceKey, Long> resourceSnapshot() { Map<ResourceKey, Long> result = new HashMap<>(snapshot()); resources.forEach((key, amount) -> { if (amount > 0) result.merge(key, amount, Long::sum); }); return result; }
            @Override public long reserveResource(ResourceKey key, long count, java.util.function.BiConsumer<GlobalPos, Long> receipt) {
                long taken;
                if (key instanceof ItemKey item) taken = extract(item, Math.toIntExact(count)).getCount();
                else { taken = Math.min(count, resources.getOrDefault(key, 0L)); resources.computeIfPresent(key, (ignored, amount) -> amount - taken); }
                if (taken > 0) receipt.accept(origin(), taken); return taken;
            }
            @Override public long insertResourceFrom(ResourceKey key, long count, GlobalPos source) {
                if (key instanceof ItemKey item) return insert(item.sample().copyWithCount(Math.toIntExact(count))).getCount();
                resources.merge(key, count, Long::sum); return 0;
            }
        };
        var adapter = new MekanismProcessingAdapter(); var catalog = adapter.recipes(fixture);
        h.assertTrue(catalog.stream().filter(recipe -> recipe.id().endsWith("/mekanism:processing/iron/clump/from_raw_ore")).map(CraftRecipe::process).distinct().count() == 2,
                "Different physical gas budgets get separate available process identities");
        var service = new CraftingService(fixture);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "mekanism-cost"), ClientInformation.createDefault());
        h.assertTrue(service.request(player, target, 2).accepted(), "A cheaper machine remains feasible even when the expensive machine lacks gas");
        h.onEachTick(() -> { power(h, hungry); power(h, efficient); service.tick(); });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0 && fixture.snapshot().getOrDefault(new ItemKey(target), 0L) >= 2, "Actual efficient purifier completes the resource-limited request: " + service.statuses());
            h.assertTrue(MekanismMachineAccess.ports(hungryHandler).stream().filter(port -> !port.kind.equals("energy")).allMatch(port -> port.amount() == 0), "Expensive machine never receives the cheaper contract's inputs");
        });
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=2400)
    public static void formedNativeEvaporationAliasesShareOneProcessorAndMakeBrine(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos origin = new BlockPos(3, 2, 3), controller = origin.offset(1, 1, 0), alias = origin.offset(2, 1, 0);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
            if (y == 0 || x == 0 || x == 3 || z == 0 || z == 3) h.setBlock(origin.offset(x, y, z), block("mekanism:thermal_evaporation_block"));
            else h.setBlock(origin.offset(x, y, z), Blocks.AIR);
        }
        h.setBlock(controller, block("mekanism:thermal_evaporation_controller")); h.setBlock(alias, block("mekanism:thermal_evaporation_valve"));
        var fixture = fixture(h, controller, new ItemStack(Items.PAPER)); var adapter = new MekanismProcessingAdapter();
        final CraftScheduler.Operation<ResourceKey>[] operation = new CraftScheduler.Operation[]{null}; final Map<ResourceKey, Long>[] results = new Map[]{null};
        final ResourceKey[] brine = {null}; int[] ticks = {0}; boolean[] aliasGuardVerified = {false};
        h.onEachTick(() -> {
            Object tile = h.getBlockEntity(controller); Object handler = MekanismMachineAccess.handler(tile);
            if (handler == null) {
                if (++ticks[0] % 10 == 0) {
                    Object structure = invoke(tile, "getStructure");
                    invoke(structure, "doImmediateUpdate", new Class<?>[]{net.minecraft.world.level.block.entity.BlockEntity.class, boolean.class}, tile, true);
                }
                return;
            }
            Object other = MekanismMachineAccess.handler(h.getBlockEntity(alias));
            h.assertTrue(other != null && MekanismMachineAccess.identity(handler).equals(MekanismMachineAccess.identity(other)), "All formed native casing/valve aliases share one processor identity");
            if (!aliasGuardVerified[0]) {
                try {
                    Field field = CraftingService.class.getDeclaredField("PROCESSOR_IDENTITIES"); field.setAccessible(true);
                    @SuppressWarnings("unchecked") Map<Object, UUID> claims = (Map<Object, UUID>) field.get(null);
                    Object identity = MekanismMachineAccess.identity(handler); UUID owner = UUID.randomUUID();
                    h.assertTrue(!CraftingService.isProcessorReserved(h.getLevel().getServer(), fixture.at(alias)), "Unclaimed formed valve remains available");
                    h.assertTrue(claims.putIfAbsent(identity, owner) == null, "Fixture exclusively claims the native multiblock identity");
                    try { h.assertTrue(CraftingService.isProcessorReserved(h.getLevel().getServer(), fixture.at(alias)), "Direct rune dispatch and arrival see reservations through every native alias"); }
                    finally { claims.remove(identity, owner); }
                    h.assertTrue(!CraftingService.isProcessorReserved(h.getLevel().getServer(), fixture.at(alias)), "Releasing the native identity restores all aliases");
                    aliasGuardVerified[0] = true;
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native alias reservation fixture failed", failure); }
            }
            try {
                Object heat = handler.getClass().getField("heatCapacitor").get(handler);
                double capacity = ((Number) invoke(heat, "getHeatCapacity")).doubleValue();
                invoke(heat, "setHeat", new Class<?>[]{double.class}, capacity * 1000);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot supply evaporation fixture heat", failure); }
            if (operation[0] == null) {
                var recipe = adapter.recipes(fixture).stream().filter(candidate -> candidate.id().endsWith("/mekanism:evaporating/brine")).findFirst().orElseThrow(); brine[0] = recipe.output();
                var selected = recipe.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().getFirst(), input.count())).toList();
                operation[0] = adapter.start(fixture, fixture.at(controller), new CraftPlan.Node<>(0, recipe, selected, Set.of()));
                h.assertTrue(operation[0] != null, "Actual formed evaporation data accepts water through generic fluid ports");
            }
            if (results[0] == null) results[0] = operation[0].poll();
        });
        h.succeedWhen(() -> { h.assertTrue(results[0] != null && results[0].getOrDefault(brine[0], 0L) > 0, "Native heated formed evaporation creates real brine"); });
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void installedNativeRecipeShapesAllHaveCatalogAliases(GameTestHelper h) {
        if (!enabled(h)) return;
        var base = fixture(h, new BlockPos(4, 2, 4), item("mekanism:dust_iron")); int[] stockReads = {0};
        var fixture = new CraftingGameTests.Fixture(h, base.origin, base.chest, base.processors, base.target) {
            @Override public Map<ResourceKey, Long> resourceSnapshot() { stockReads[0]++; return Map.copyOf(snapshot()); }
        };
        var adapter = new MekanismProcessingAdapter(); var catalog = adapter.recipes(fixture);
        h.assertTrue(stockReads[0] == 1, "A complete native catalog captures network stock once and reuses native prototypes");
        List<Map<String, Object>> rows = new ArrayList<>(); List<String> missing = new ArrayList<>();
        for (var entry : MekanismRecipeDiscovery.recipes(h.getLevel())) {
            boolean incomplete = hasMethod(entry.recipe(), "isIncomplete") && Boolean.TRUE.equals(invoke(entry.recipe(), "isIncomplete"));
            long aliases = catalog.stream().filter(recipe -> recipe.id().endsWith("/" + entry.id())).count();
            long unbound = catalog.stream().filter(recipe -> recipe.id().endsWith("/" + entry.id()) && recipe.process().endsWith("/unbound")).count();
            Map<String, Object> row = new LinkedHashMap<>(); row.put("type", entry.type()); row.put("id", entry.id()); row.put("class", entry.recipe().getClass().getName()); row.put("incomplete", incomplete); row.put("aliases", aliases); row.put("aliasesRequiringMachineProfile", unbound);
            row.put("inputs", inputs(entry.recipe()).stream().map(input -> Map.of("getter", input.name(), "kind", input.resource(), "representations", input.representations().size())).toList()); rows.add(row);
            if (!incomplete && aliases == 0) missing.add(entry.type() + "/" + entry.id());
        }
        try {
            Path output = Path.of("build/autocrafting-fixes/mekanism-native-catalog-audit.json").toAbsolutePath(); Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("recipes", rows.size(), "catalogAliases", catalog.size(), "missing", missing, "entries", rows)));
        } catch (java.io.IOException failure) { throw new IllegalStateException("Could not write native recipe audit", failure); }
        h.assertTrue(missing.isEmpty(), "Every valid native recipe has at least one catalog alias: " + missing);
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void futureLikeContractDoesNotRequireAMachineClassOrBlockId(GameTestHelper h) {
        if (!enabled(h)) return;
        BlockPos machine = new BlockPos(4, 2, 4); h.setBlock(machine, block("mekanism:crusher")); Object real = h.getBlockEntity(machine);
        try {
            Class<?> lookup = Class.forName("mekanism.common.recipe.lookup.IRecipeLookupHandler"), inventory = Class.forName("mekanism.api.inventory.IMekanismInventory");
            Object future = Proxy.newProxyInstance(lookup.getClassLoader(), new Class<?>[]{lookup, inventory}, (proxy, method, args) -> {
                if (method.getName().equals("equals")) return proxy == args[0];
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("toString")) return "Future contract fixture";
                try { return real.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(real, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
            h.assertTrue(MekanismMachineAccess.handler(future) == future, "An unknown proxy class is discovered through the shared interface");
            h.assertTrue(MekanismMachineAccess.process(future).equals("mekanism:crushing"), "Future-like handler advertises its native recipe provider");
            h.assertTrue(!MekanismMachineAccess.ports(future).isEmpty(), "Future-like handler exposes native slots without any block ID or slot list");
            Class<?> providerApi = Class.forName("mekanism.common.recipe.IMekanismRecipeTypeProvider");
            Object provider = invoke(real, "getRecipeType");
            Object futureProvider = Proxy.newProxyInstance(providerApi.getClassLoader(), new Class<?>[]{providerApi}, (proxy, method, args) -> {
                if (method.getName().equals("getRegistryName")) return ResourceLocation.parse("future_addon:dynamic_crushing");
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return provider.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(provider, args);
            });
            Object addon = Proxy.newProxyInstance(lookup.getClassLoader(), new Class<?>[]{lookup}, (proxy, method, args) -> {
                if (method.getName().equals("getRecipeType")) return futureProvider;
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return real.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(real, args);
            });
            h.assertTrue(MekanismRecipeDiscovery.recipes(h.getLevel(), List.of(addon)).stream().anyMatch(entry -> entry.type().equals("future_addon:dynamic_crushing")),
                    "Loaded addon providers are discovered even when their registry type does not implement the provider interface");
            Object combiner = MekanismRecipeDiscovery.recipes(h.getLevel()).stream().filter(entry -> entry.id().equals("mekanism:combining/obsidian")).findFirst().orElseThrow().recipe();
            Object sameIngredient = invoke(combiner, "getMainInput");
            Object sharedInputs = Class.forName("mekanism.api.recipes.basic.BasicCombinerRecipe")
                    .getConstructor(sameIngredient.getClass(), sameIngredient.getClass(), ItemStack.class).newInstance(sameIngredient, sameIngredient, new ItemStack(Items.OBSIDIAN));
            h.assertTrue(MekanismRecipeDiscovery.inputs(sharedInputs).size() == 2, "Independent logical inputs remain distinct even when an addon reuses one immutable ingredient object");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Future contract fixture failed", failure); }
        h.succeed();
    }

    private static void converterBaseline(GameTestHelper h, boolean energy) {
        BlockPos machine = new BlockPos(4, 2, 4); h.setBlock(machine, block("mekanism:metallurgic_infuser"));
        Object handler = MekanismMachineAccess.handler(h.getBlockEntity(machine)); var fixture = fixture(h, machine, new ItemStack(Items.PAPER));
        var adapter = new MekanismProcessingAdapter(); var catalog = adapter.recipes(fixture);
        ResourceKey input = new ItemKey(new ItemStack(energy ? Items.REDSTONE : Items.COAL));
        var recipe = catalog.stream().filter(candidate -> candidate.ingredients().size() == 1 && candidate.ingredients().getFirst().alternatives().contains(input)
                && (energy ? candidate.output().equals(ResourceKinds.FE) : candidate.output() instanceof ChemicalKey && candidate.output().id().toString().equals("mekanism:carbon"))).findFirst().orElseThrow();
        var ports = MekanismMachineAccess.ports(handler);
        var converter = ports.stream().filter(port -> port.converter && port.insert(input, 1, true) > 0).filter(port -> ports.stream().anyMatch(output -> port.linksTo(output) && output.kind.equals(energy ? "energy" : "chemical"))).findFirst().orElseThrow();
        var output = ports.stream().filter(port -> converter.linksTo(port) && port.kind.equals(energy ? "energy" : "chemical")).findFirst().orElseThrow();
        long baseline = energy ? 1000 : 100; h.assertTrue(output.insert(recipe.output(), baseline, false) == baseline, "Preexisting native resource is inserted before the operation owns anything");
        var selected = List.of(new CraftPlan.Selection<>(input, recipe.ingredients().getFirst().count()));
        var operation = adapter.start(fixture, fixture.at(machine), new CraftPlan.Node<>(0, recipe, selected, Set.of()));
        h.assertTrue(operation != null && operation.poll() == null, "Preexisting contents cannot satisfy new production before the real input is consumed");
        final Map<ResourceKey, Long>[] results = new Map[]{null};
        h.onEachTick(() -> { if (results[0] == null) results[0] = operation.poll(); });
        h.succeedWhen(() -> {
            h.assertTrue(results[0] != null, "Native converter completes with preexisting contents");
            h.assertTrue(results[0].getOrDefault(recipe.output(), 0L) == recipe.outputCount(), "Only newly produced resources are extracted");
            h.assertTrue(output.amount() == baseline, "Preexisting tank contents or charge remain physically untouched");
        });
    }

    private static void direct(GameTestHelper h, String block, String recipeSuffix, boolean rotary) {
        BlockPos machine = new BlockPos(4, 2, 4); h.setBlock(machine, block(block));
        Object handler = MekanismMachineAccess.handler(h.getBlockEntity(machine));
        var fixture = fixture(h, machine, new ItemStack(Items.PAPER));
        var adapter = new MekanismProcessingAdapter(); var catalog = adapter.recipes(fixture);
        List<CraftRecipe<ResourceKey>> candidates = catalog.stream().filter(recipe -> recipe.id().endsWith(recipeSuffix)).toList();
        h.assertTrue(!candidates.isEmpty(), "Actual native recipe is catalogued: " + recipeSuffix);
        if (rotary) {
            Map<String, Boolean> initial = new LinkedHashMap<>();
            for (var candidate : candidates) initial.put(candidate.process(), adapter.supports(fixture, fixture.at(machine), new CraftPlan.Node<>(0, candidate, List.of(), Set.of())));
            h.assertTrue(initial.size() == 2 && initial.containsValue(true) && initial.containsValue(false), "Planner sees only the selected native rotary direction as available: " + initial + "; mode=" + invoke(handler, "getMode"));
            invoke(handler, "nextMode");
            for (var candidate : candidates) h.assertTrue(adapter.supports(fixture, fixture.at(machine), new CraftPlan.Node<>(0, candidate, List.of(), Set.of())) != initial.get(candidate.process()), "Native mode changes immediately reverse available recipe directions");
            invoke(handler, "nextMode");
        }
        CraftScheduler.Operation<ResourceKey> operation = null; CraftRecipe<ResourceKey> chosen = null;
        for (int attempt = 0; attempt < (rotary ? 2 : 1) && operation == null; attempt++) {
            for (var candidate : candidates) {
                var selected = candidate.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().getFirst(), input.count())).toList();
                var node = new CraftPlan.Node<>(0, candidate, selected, Set.<Integer>of());
                operation = adapter.start(fixture, fixture.at(machine), node);
                if (operation != null) { chosen = candidate; break; }
            }
            if (operation == null && rotary) invoke(handler, "nextMode");
        }
        h.assertTrue(operation != null, "Native resource ports accept actual selected inputs for " + recipeSuffix);
        final var running = operation; final var recipe = chosen; final Map<ResourceKey, Long>[] results = new Map[]{null};
        h.onEachTick(() -> { power(h, machine); if (results[0] == null) results[0] = running.poll(); });
        h.succeedWhen(() -> {
            h.assertTrue(results[0] != null, "Native operation completes: " + recipeSuffix + "; machine=" + h.getBlockEntity(machine).saveWithFullMetadata(h.getLevel().registryAccess()));
            h.assertTrue(results[0].getOrDefault(recipe.output(), 0L) >= recipe.outputCount(), "Actual requested native resource is collected");
            if (recipeSuffix.contains("separator/water")) h.assertTrue(results[0].keySet().stream().filter(ChemicalKey.class::isInstance).count() == 2, "Both actual electrolysis chemical coproducts are collected");
            if (recipeSuffix.contains("coal_gasification")) h.assertTrue(results[0].keySet().stream().anyMatch(ChemicalKey.class::isInstance) && results[0].keySet().stream().anyMatch(ItemKey.class::isInstance), "Actual PRC item and chemical coproducts are collected");
            h.assertTrue(running.cancel().isEmpty(), "Finished native operation cannot return output twice");
        });
    }
    private static void request(GameTestHelper h, BlockPos machine, List<ItemStack> inputs, ItemStack target, int count) {
        var fixture = fixture(h, machine, target); for (int i = 0; i < inputs.size(); i++) fixture.chest.setItem(i, inputs.get(i).copy());
        var service = new CraftingService(fixture);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "generic-mekanism"), ClientInformation.createDefault());
        h.assertTrue(service.request(player, target, count).accepted(), "Generic Mekanism request accepted");
        h.onEachTick(() -> { power(h, machine); service.tick(); });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0, "Native mixed-resource plan completes: " + service.statuses() + "; machine=" + h.getBlockEntity(machine).saveWithFullMetadata(h.getLevel().registryAccess()));
            h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(target), 0L) >= count, "Actual final item reached storage: " + service.statuses());
        });
    }
    private static CraftingGameTests.Fixture fixture(GameTestHelper h, BlockPos machine, ItemStack target) {
        BlockPos chest = new BlockPos(1, 2, 1); h.setBlock(chest, Blocks.BARREL);
        return new CraftingGameTests.Fixture(h, chest, (Container) h.getBlockEntity(chest), List.of(machine), target);
    }
    private static void power(GameTestHelper h, BlockPos machine) {
        Object tile = h.getBlockEntity(machine);
        if (hasMethod(tile, "getEnergyContainer")) {
            Object energy = invoke(tile, "getEnergyContainer"); if (energy != null) invoke(energy, "setEnergy", new Class<?>[]{long.class}, 1_000_000L);
        }
    }
    private static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    private static net.minecraft.world.level.block.Block block(String id) {
        ResourceLocation key = ResourceLocation.parse(id); if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException("Missing fixture block " + id); return BuiltInRegistries.BLOCK.get(key);
    }
    private static boolean enabled(GameTestHelper h) { if (!Boolean.getBoolean("astral_repository.machineCompatTest")) { h.succeed(); return false; } h.assertTrue(ModList.get().isLoaded("mekanism"), "Native Mekanism runtime is installed"); return true; }
}
