package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Native incomplete-display regression, using the installed loader's real ingredient API. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NativeStockSamplesGameTests {
    private static final Map<GlobalPos, RefreshFixture> REFRESH = new HashMap<>();
    private static boolean registered;
    private static synchronized void register() {
        if (registered) return;
        ProcessingAdapters.registerResource("astral_repository:custom_predicate_refresh", () -> new NativeMachineAdapter(new RefreshBackend())); registered = true;
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=500)
    public static void newDefaultItemTypesRefreshCustomPredicatesBeforeARealNativeRequest(GameTestHelper h) {
        register(); BlockPos source = new BlockPos(2, 2, 2), machine = source.east(3);
        h.setBlock(source, net.minecraft.world.level.block.Blocks.BARREL); h.setBlock(machine, net.minecraft.world.level.block.Blocks.BARREL);
        Container stock = (Container)h.getBlockEntity(source); stock.setItem(0, new ItemStack(Items.COAL));
        Ingredient predicate = new net.neoforged.neoforge.common.crafting.ICustomIngredient() {
            public boolean test(ItemStack stack) { return stack.is(Items.IRON_INGOT) || stack.is(Items.GOLD_INGOT); }
            public java.util.stream.Stream<ItemStack> getItems() { return java.util.stream.Stream.of(new ItemStack(Items.IRON_INGOT)); }
            public boolean isSimple() { return true; }
            public net.neoforged.neoforge.common.crafting.IngredientType<?> getType() { throw new UnsupportedOperationException("Transient fixture ingredient"); }
        }.toVanilla();
        RefreshFixture fixture = new RefreshFixture(h, source, stock, machine, predicate); REFRESH.put(fixture.origin(), fixture);
        CraftingService service = new CraftingService(fixture);
        ItemKey gold = new ItemKey(new ItemStack(Items.GOLD_INGOT));
        h.assertTrue(!alternatives(service).contains(gold), "The initial custom-predicate catalog has no undisplayed gold stock");
        stock.setItem(1, gold.sample());
        h.assertTrue(alternatives(service).contains(gold), "Adding a default item identity invalidates the old native catalog even without new components");
        var player = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "native-hidden-domain"));
        var first = service.request(player, fixture.target, 1); h.assertTrue(first.accepted(), "A new request may use the newly discovered hidden item type");
        h.onEachTick(() -> { service.tick(); fixture.physicalTick(); });
        UUID[] second = {null};
        h.startSequence().thenWaitUntil(() -> h.assertTrue(complete(service, first.jobId()), "The real default gold input completes one native cycle"))
                .thenExecute(() -> {
                    ItemStack named = new ItemStack(Items.GOLD_INGOT); named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("hidden native component variant"));
                    ItemKey variant = new ItemKey(named); stock.setItem(2, named);
                    h.assertTrue(alternatives(service).contains(variant), "The refreshed custom predicate preserves an undisplayed component variant too");
                    var request = service.request(player, fixture.target, 1); h.assertTrue(request.accepted(), "The second exact variant request is accepted"); second[0] = request.jobId();
                }).thenWaitUntil(() -> h.assertTrue(second[0] != null && complete(service, second[0]), "The component variant completes through the same physical native roles"))
                .thenExecute(() -> {
                    h.assertTrue(fixture.cycles == 2 && fixture.snapshot().getOrDefault(new ItemKey(fixture.target), 0L) == 2, "Two real native cycles produce exactly two requested outputs");
                    h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.COAL)), 0L) == 1
                            && fixture.snapshot().keySet().stream().noneMatch(key -> key.sample().is(Items.GOLD_INGOT)) && fixture.machine.isEmpty(), "Only the two accepted gold inputs are consumed and unrelated stock is preserved");
                    h.assertTrue(service.activeProcessors() == 0, "Both requests release their physical processor claims"); REFRESH.remove(fixture.origin());
                }).thenSucceed();
    }
    private static boolean complete(CraftingService service, UUID request) { return service.statuses().stream().anyMatch(status -> status.id().equals(request) && status.state().equals("COMPLETE")); }
    private static Set<ResourceKey> alternatives(CraftingService service) {
        Set<ResourceKey> result = new HashSet<>();
        service.catalogSnapshot().stream().filter(recipe -> recipe.process().startsWith("native/custom_predicate_refresh/"))
                .flatMap(recipe -> recipe.ingredients().stream()).forEach(ingredient -> result.addAll(ingredient.alternatives()));
        return result;
    }
    private static final class RefreshFixture extends CraftingGameTests.Fixture {
        final BlockPos processor; final Container machine; final InvWrapper handler; final Ingredient predicate; int cycles;
        RefreshFixture(GameTestHelper h, BlockPos source, Container stock, BlockPos processor, Ingredient predicate) {
            super(h, source, stock, List.of(processor), new ItemStack(Items.BRICK)); this.processor = processor; this.predicate = predicate;
            machine = (Container)h.getBlockEntity(processor); handler = new InvWrapper(machine);
        }
        @Override public int travelTicks(GlobalPos from, GlobalPos to) { return 0; }
        void physicalTick() { if (!machine.getItem(0).isEmpty() && predicate.test(machine.getItem(0)) && machine.getItem(1).isEmpty()) { machine.removeItem(0, 1); machine.setItem(1, target.copy()); cycles++; } }
    }
    private static final class RefreshBackend implements NativeMachineBackend {
        public String id() { return "custom_predicate_refresh"; }
        public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
            RefreshFixture fixture = REFRESH.get(access.origin()); if (fixture == null || !fixture.at(fixture.processor).equals(position)) return null;
            return new Machine() {
                public Object identity() { return fixture.machine; } public Object instance() { return fixture.machine; }
                public boolean valid() { return fixture.level().getBlockEntity(position.pos()) == fixture.machine; }
                public boolean accepts(Recipe recipe) { return recipe.nativeRecipe() == fixture; }
                public List<Port> ports(Recipe recipe) { return List.of(NativeMachinePorts.itemSlot(fixture.handler, 0, true, false), NativeMachinePorts.itemSlot(fixture.handler, 1, false, true)); }
            };
        }
        public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> machines) {
            RefreshFixture fixture = REFRESH.get(access.origin()); if (fixture == null) return List.of();
            Object index = NativeBackendReflection.call(NativeBackendReflection.class, "itemStock", access.resourceSnapshot());
            @SuppressWarnings("unchecked") var input = (CraftRecipe.Ingredient<ResourceKey>)NativeBackendReflection.call(NativeBackendReflection.class, "ingredient", fixture.predicate, 1L, index);
            Map<ResourceKey, Long> output = Map.of(new ItemKey(fixture.target), 1L);
            return List.of(new Recipe("custom_predicate_refresh/native", "hidden_gold", List.of(input), output, output, 1, 400, fixture, false));
        }
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void customSimpleIngredientKeepsStockOutsideItsDisplayedDomain(GameTestHelper h) {
        Ingredient incomplete = new net.neoforged.neoforge.common.crafting.ICustomIngredient() {
            @Override public boolean test(ItemStack stack) { return stack.is(Items.IRON_INGOT) || stack.is(Items.GOLD_INGOT); }
            @Override public java.util.stream.Stream<ItemStack> getItems() { return java.util.stream.Stream.of(new ItemStack(Items.IRON_INGOT)); }
            @Override public boolean isSimple() { return true; }
            @Override public net.neoforged.neoforge.common.crafting.IngredientType<?> getType() { throw new UnsupportedOperationException("Transient test ingredient is never serialized"); }
        }.toVanilla();
        var named = new ItemStack(Items.GOLD_INGOT); named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("native component variant"));
        ItemKey iron = new ItemKey(new ItemStack(Items.IRON_INGOT)), gold = new ItemKey(new ItemStack(Items.GOLD_INGOT)), variant = new ItemKey(named);
        Map<ResourceKey,Long> stock = Map.of(iron,1L,gold,1L,variant,1L);
        try {
            Class<?> index = Class.forName("com.cappleapple.astralrepository.crafting.NativeStockSamples");
            var constructor = index.getDeclaredConstructor(Map.class); constructor.setAccessible(true); Object samples = constructor.newInstance(stock);
            var matching = index.getDeclaredMethod("matching", Ingredient.class); matching.setAccessible(true);
            @SuppressWarnings("unchecked") List<ItemKey> actual = (List<ItemKey>)matching.invoke(samples, incomplete);
            h.assertTrue(incomplete.getClass() == Ingredient.class && incomplete.isSimple(), "The real loader wraps a custom simple predicate in the vanilla Ingredient class");
            h.assertTrue(new HashSet<>(actual).equals(Set.of(iron,gold,variant)), "The cached fallback tests every real stock prototype, including hidden item types and component variants");
            @SuppressWarnings("unchecked") List<ItemKey> vanilla = (List<ItemKey>)matching.invoke(samples, Ingredient.of(Items.IRON_INGOT));
            h.assertTrue(vanilla.equals(List.of(iron)), "A proven vanilla item domain remains exact");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        h.succeed();
    }
}
