package com.cappleapple.astralrepository.smoke;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.RecipeTomeItem;
import com.cappleapple.astralrepository.crafting.*;
import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import com.cappleapple.astralrepository.network.RecipeTomePackets;
import com.cappleapple.astralrepository.port.NbtCodecs;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in headless gate for potion identity and actual native brewing across the modern rewrite. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID)
public final class BrewingRuntimeSmoke {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("astral_repository.brewingSmoke")) return;
        try {
            run(event.getServer().overworld());
            AstralRepository.LOGGER.info("ASTRAL_BREWING_SMOKE_PASS: exact tome potion identity; five native physical brews; charged fuel reuse; reagent bottle remainder; no outstanding processor contents");
        } catch (Throwable failure) { AstralRepository.LOGGER.error("ASTRAL_BREWING_SMOKE_FAIL", failure); }
        finally { event.getServer().halt(false); }
    }
    private static void run(ServerLevel level) {
        var position = new BlockPos(0, 100, 0); level.getChunkAt(position);
        level.setBlockAndUpdate(position, Blocks.BREWING_STAND.defaultBlockState());
        var stand = (BrewingStandBlockEntity) level.getBlockEntity(position);
        var target = PotionContents.createItemStack(Items.LINGERING_POTION, Potions.LONG_SWIFTNESS);
        var player = FakePlayerFactory.getMinecraft(level);
        var tome = new ItemStack(AstralContent.RECIPE_TOME.get()); player.setItemInHand(InteractionHand.MAIN_HAND, tome);
        var menu = new RecipeTomeMenu(93, player.getInventory());
        menu.action(new RecipeTomePackets.Action(93, 2, "minecraft:lingering_potion", 0, "", target));
        var page = menu.browse("=minecraft:lingering_potion", 0);
        check(page.total() > 0 && ItemStack.isSameItemSameComponents(page.entries().getFirst().output(), target), "native tome catalogue retains potion duration and container");
        check(menu.inscribe(page.entries().getFirst().recipe()), "native brewing rule inscription");
        var restored = NbtCodecs.item(level.registryAccess(), (net.minecraft.nbt.CompoundTag) NbtCodecs.save(tome, level.registryAccess()));
        check(ItemStack.isSameItemSameComponents(RecipeTomeItem.product(restored, level.registryAccess()), target), "serialized tome keeps exact native potion variant");
        var access = new Access(level, GlobalPos.of(level.dimension(), position), target);
        access.insert(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        for (var item : List.of(Items.NETHER_WART, Items.SUGAR, Items.REDSTONE, Items.GUNPOWDER, Items.DRAGON_BREATH, Items.BLAZE_POWDER)) access.insert(new ItemStack(item));
        var adapter = new BrewingProcessingAdapter();
        var recipes = adapter.recipes(access);
        var plan = new CraftPlanner<>(recipes).planAdditional(new ItemKey(target), 1, access.snapshot(), Set.of("brewing"));
        check(plan.nodes().size() == 5, "native five-step intermediate brewing plan");
        for (var node : plan.nodes()) {
            node.inputs().forEach((key, count) -> check(access.extract(key, Math.toIntExact(count)).getCount() == count, "physical recipe input reservation"));
            var operation = adapter.start(access, access.origin(), node);
            check(operation != null, "native stand accepts concrete recipe and charged fuel");
            Map<ItemKey, Long> result = null;
            for (int tick = 0; tick < 900 && result == null; tick++) {
                BrewingStandBlockEntity.serverTick(level, position, stand.getBlockState(), stand);
                result = operation.poll();
            }
            check(result != null && result.getOrDefault(node.recipe().output(), 0L) == node.recipe().outputCount(), "real server-ticked stand returns exact expected potion");
            result.forEach((key, count) -> access.insert(key.sample().copyWithCount(Math.toIntExact(count))));
        }
        check(access.stock.equals(Map.of(new ItemKey(target), 1L, new ItemKey(new ItemStack(Items.GLASS_BOTTLE)), 1L)), "only final potion and dragon breath bottle remain in storage");
        for (int slot = 0; slot < 5; slot++) check(stand.getItem(slot).isEmpty(), "no owned stack remains in stand slot " + slot);
    }
    private static final class Access implements CraftingService.NetworkAccess {
        final ServerLevel level; final GlobalPos position; final ItemStack target; final Map<ItemKey, Long> stock = new HashMap<>();
        Access(ServerLevel level, GlobalPos position, ItemStack target) { this.level = level; this.position = position; this.target = target; }
        public ServerLevel level() { return level; }
        public Map<ItemKey, Long> snapshot() { return Map.copyOf(stock); }
        public ItemStack extract(ItemKey key, int count) {
            int taken = (int) Math.min(count, stock.getOrDefault(key, 0L));
            if (taken == 0) return ItemStack.EMPTY;
            long left = stock.get(key) - taken; if (left == 0) stock.remove(key); else stock.put(key, left);
            return key.sample().copyWithCount(taken);
        }
        public ItemStack insert(ItemStack stack) { if (!stack.isEmpty()) stock.merge(new ItemKey(stack), (long)stack.getCount(), Long::sum); return ItemStack.EMPTY; }
        public List<GlobalPos> workstations() { return List.of(position); }
        public List<ItemStack> exposedProducts() { return List.of(target); }
        public GlobalPos origin() { return position; }
        public int travelTicks(GlobalPos from, GlobalPos to) { return 0; }
        public void visual(GlobalPos from, GlobalPos to, ItemStack stack, int ticks) {}
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
