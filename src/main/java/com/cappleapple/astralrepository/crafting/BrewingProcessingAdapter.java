package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import java.util.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** Runs one bottle and one reagent through a real, exclusively claimed brewing stand. */
public final class BrewingProcessingAdapter implements ProcessingAdapter {
    private final Map<String, BrewingRecipes.Entry> bindings = new HashMap<>();
    public BrewingProcessingAdapter() {}
    private BrewingProcessingAdapter(BrewingProcessingAdapter source) { bindings.putAll(source.bindings); }
    @Override public ProcessingAdapter capturePlan() { return new BrewingProcessingAdapter(this); }

    @Override public List<CraftRecipe<ItemKey>> recipes(CraftingService.NetworkAccess access) {
        bindings.clear();
        List<CraftRecipe<ItemKey>> result = new ArrayList<>();
        for (var entry : BrewingRecipes.entries(access.level(), access.snapshot().keySet().stream().map(ItemKey::sample).toList())) {
            bindings.put(entry.id().toString(), entry);
            result.add(new CraftRecipe<>(entry.id().toString(), new ItemKey(entry.output()), entry.output().getCount(), "brewing",
                    List.of(new CraftRecipe.Ingredient<>(List.of(new ItemKey(entry.input())), 1),
                            new CraftRecipe.Ingredient<>(List.of(new ItemKey(entry.reagent())), 1)), 0, 400));
        }
        return result;
    }
    @Override public boolean supports(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node) {
        var level = VanillaProcessingAdapter.world(access, position);
        return bindings.containsKey(node.recipe().id()) && level != null && level.hasChunkAt(position.pos())
                && level.getBlockEntity(position.pos()) instanceof BrewingStandBlockEntity;
    }
    @Override public CraftScheduler.Operation<ItemKey> start(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node) {
        if (!supports(access, position, node) || node.selected().size() != 2) return null;
        ServerLevel level = VanillaProcessingAdapter.world(access, position);
        var stand = (BrewingStandBlockEntity) level.getBlockEntity(position.pos());
        for (int slot = 0; slot < 4; slot++) if (!stand.getItem(slot).isEmpty()) return null;
        var data = stand.saveWithoutMetadata();
        if (data.getShort("BrewTime") > 0) return null;
        var entry = bindings.get(node.recipe().id());
        ItemStack input = node.selected().get(0).key().sample(), reagent = node.selected().get(1).key().sample();
        ItemStack reagentRemainder = reagent.getItem().hasCraftingRemainingItem() ? new ItemStack(reagent.getItem().getCraftingRemainingItem()) : ItemStack.EMPTY;
        ItemStack expected = BrewingRecipes.mix(level, reagent, input.copy());
        if (!BrewingRecipes.hasMix(level, input, reagent) || !ItemStack.isSameItemSameTags(expected, entry.output())
                || expected.getCount() != entry.output().getCount()) throw new IllegalStateException("Brewing recipe changed");
        if (!stand.canPlaceItem(0, input) || !stand.canPlaceItem(3, reagent)) return null;
        ItemStack preloadedFuel = stand.getItem(4);
        if (!preloadedFuel.isEmpty() && !access.mayUseFuel(new ItemKey(preloadedFuel))) return null;
        ItemStack fuel = ItemStack.EMPTY;
        int[] delay = {0};
        if (data.getByte("Fuel") <= 0 && preloadedFuel.isEmpty()) {
            var powder = new ItemKey(new ItemStack(Items.BLAZE_POWDER));
            if (!access.mayUseFuel(powder)) return null;
            fuel = access.extractForDelivery(powder, 1, position, (from, stack) -> {
                delay[0] = Math.max(delay[0], access.travelTicks(from, position));
                CraftingService.animate(access, from, position, stack, Math.max(1, delay[0]));
            });
            if (fuel.isEmpty()) return null;
        }
        stand.setItem(0, input.copy());
        stand.setItem(3, reagent.copy());
        stand.setChanged();
        ItemStack suppliedFuel = fuel;
        return new CraftScheduler.Operation<>() {
            ItemStack pendingFuel = suppliedFuel.copy(), ownedFuel = ItemStack.EMPTY;
            int elapsed, fuelTicks = delay[0];
            boolean settled;
            private boolean present() {
                var current = VanillaProcessingAdapter.world(access, position);
                return current != null && current.hasChunkAt(position.pos()) && current.getBlockEntity(position.pos()) == stand;
            }
            private void deliverFuel() {
                if (fuelTicks > 0) fuelTicks--;
                if (fuelTicks == 0 && !pendingFuel.isEmpty() && stand.getItem(4).isEmpty()) {
                    stand.setItem(4, pendingFuel); ownedFuel = pendingFuel.copy(); pendingFuel = ItemStack.EMPTY; stand.setChanged();
                }
            }
            @Override public Map<ItemKey, Long> poll() {
                var current = VanillaProcessingAdapter.world(access, position);
                if (current == null || !current.hasChunkAt(position.pos())) return null;
                if (!present()) throw new IllegalStateException("Brewing stand removed; contents remain in-world");
                deliverFuel();
                if (ItemStack.isSameItemSameTags(stand.getItem(0), expected) && stand.getItem(0).getCount() >= expected.getCount()) {
                    Map<ItemKey, Long> result = new LinkedHashMap<>();
                    VanillaProcessingAdapter.add(result, stand.removeItem(0, expected.getCount()));
                    // The reagent's real container remainder belongs to this operation too.
                    reclaimReagentRemainder(result);
                    reclaimFuel(result); stand.setChanged(); settled = true; return Map.copyOf(result);
                }
                if (++elapsed > 3200) throw new IllegalStateException("Brewing stand stalled; check fuel or external extraction");
                return null;
            }
            private void reclaimFuel(Map<ItemKey, Long> result) {
                VanillaProcessingAdapter.add(result, pendingFuel); pendingFuel = ItemStack.EMPTY;
                if (!ownedFuel.isEmpty() && ItemStack.isSameItemSameTags(stand.getItem(4), ownedFuel))
                    VanillaProcessingAdapter.add(result, stand.removeItem(4, ownedFuel.getCount()));
                ownedFuel = ItemStack.EMPTY;
            }
            private void reclaimReagentRemainder(Map<ItemKey, Long> result) {
                if (!reagentRemainder.isEmpty() && ItemStack.isSameItemSameTags(stand.getItem(3), reagentRemainder))
                    VanillaProcessingAdapter.add(result, stand.removeItem(3, reagentRemainder.getCount()));
            }
            @Override public Map<ItemKey, Long> cancel() {
                if (settled) return Map.of(); settled = true;
                Map<ItemKey, Long> result = new LinkedHashMap<>();
                if (present()) {
                    boolean brewed = ItemStack.isSameItemSameTags(stand.getItem(0), expected);
                    if (ItemStack.isSameItemSameTags(stand.getItem(0), input) || ItemStack.isSameItemSameTags(stand.getItem(0), expected))
                        VanillaProcessingAdapter.add(result, stand.removeItem(0, Math.max(input.getCount(), expected.getCount())));
                    if (ItemStack.isSameItemSameTags(stand.getItem(3), reagent)) VanillaProcessingAdapter.add(result, stand.removeItem(3, 1));
                    else if (brewed) reclaimReagentRemainder(result);
                    reclaimFuel(result); stand.setChanged();
                } else { VanillaProcessingAdapter.add(result, pendingFuel); pendingFuel = ItemStack.EMPTY; }
                return Map.copyOf(result);
            }
            @Override public Map<ItemKey, Long> recoverable() {
                return settled || pendingFuel.isEmpty() ? Map.of() : VanillaProcessingAdapter.counts(List.of(pendingFuel));
            }
        };
    }
}
