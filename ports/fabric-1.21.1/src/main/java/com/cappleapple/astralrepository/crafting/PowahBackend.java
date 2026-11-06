package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Discovers native Powah energizing recipes and native per-slot insertion/extraction roles. */
public final class PowahBackend implements NativeMachineBackend {
    private record Binding(Object recipe, long energy) {}
    private final Map<String, String> diagnostics = new LinkedHashMap<>();
    @Override public String id() { return "powah"; }
    @Override public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
        ServerLevel level = access.level().getServer().getLevel(position.dimension());
        Object entity = at(level, position.pos());
        return entity instanceof BlockEntity block ? new Host(level, block) : null;
    }
    @Override public Object identityAt(ServerLevel level, BlockPos position) { return at(level, position); }
    private static Object at(ServerLevel level, BlockPos position) {
        if (level == null || !level.hasChunkAt(position)) return null;
        BlockEntity entity = level.getBlockEntity(position);
        return entity != null && (entity.getClass().getName().startsWith("owmii.powah.") || contract(entity, "owmii.powah.lib.block.IInventoryHolder"))
                && has(entity, "getInventory") && has(entity, "currRecipe") && has(entity, "getBuffer")
                && has(entity, "containRecipe") && has(entity, "fillEnergy", 1L) ? entity : null;
    }
    @Override public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> loadedMachines) {
        diagnostics.clear(); List<Recipe> result = new ArrayList<>();
        Map<ResourceKey, Long> stock = access.resourceSnapshot();
        ItemStock itemStock = itemStock(stock);
        for (var holder : NativeRecipeSource.entries(access.level())) {
            Object nativeRecipe = holder.value();
            if (!(nativeRecipe.getClass().getName().startsWith("owmii.powah.") || contract(nativeRecipe, "owmii.powah.block.energizing.EnergizingRecipe")) || !has(nativeRecipe, "getScaledEnergy")) continue;
            String nativeId = holder.id().toString();
            try {
                List<CraftRecipe.Ingredient<ResourceKey>> inputs = new ArrayList<>();
                for (Object input : NativeRecipeSource.ingredients(nativeRecipe)) {
                    if (!(input instanceof Ingredient ingredient)) throw new IllegalArgumentException("Unsupported native energizing ingredient");
                    // The native matcher consumes one occupied slot per ingredient, including repeated ingredients.
                    inputs.add(ingredient(ingredient, 1, itemStock));
                }
                ItemStack output = item(has(nativeRecipe, "getResultItem") ? call(nativeRecipe, "getResultItem")
                        : NativeRecipeSource.output(nativeRecipe, access.level().registryAccess()));
                if (output.isEmpty() || inputs.isEmpty()) throw new IllegalArgumentException("Empty native energizing inputs or result");
                long energy = number(call(nativeRecipe, "getScaledEnergy"), 0);
                if (energy <= 0) throw new IllegalArgumentException("Invalid native energizing power requirement");
                Map<ResourceKey, Long> outputs = Map.of(new ItemKey(output), (long)output.getCount());
                result.add(new Recipe("powah/native/" + nativeId, "powah/" + holder.type(), inputs, outputs, outputs,
                        1, 72_000, new Binding(nativeRecipe, energy), false));
                diagnostics.put(nativeId, "One native energizing cycle; " + inputs.size() + " distinct one-item ingredients; " + energy + " FE through native energizing rods");
            } catch (RuntimeException unsupported) { diagnostics.put(nativeId, unsupported.getMessage() == null ? unsupported.getClass().getName() : unsupported.getMessage()); }
        }
        return List.copyOf(result);
    }
    public Map<String, String> diagnostics() { return Map.copyOf(diagnostics); }

    private static final class Host implements Machine {
        final ServerLevel level; final BlockEntity entity;
        Host(ServerLevel level, BlockEntity entity) { this.level = level; this.entity = entity; }
        @Override public Object identity() { return entity; }
        @Override public Object instance() { return entity; }
        @Override public boolean valid() { return !entity.isRemoved() && level.hasChunkAt(entity.getBlockPos()) && at(level, entity.getBlockPos()) == entity; }
        @Override public boolean accepts(Recipe recipe) {
            if (!(recipe.nativeRecipe() instanceof Binding binding)) return false;
            return number(optional(binding.recipe(), "getScaledEnergy"), -1) == binding.energy();
        }
        @Override public boolean ready(Recipe recipe, List<CraftPlan.Selection<ResourceKey>> selected) {
            if (!valid() || !accepts(recipe)) return false;
            Object inventory = call(entity, "getInventory");
            int count = Math.toIntExact(number(call(inventory, "getSlots"), 0));
            if (count <= 0 || count > 4096) return false;
            // The native orb clears its complete inventory at completion. Preserve every pre-existing stack.
            for (int slot = 0; slot < count; slot++) if (!item(call(inventory, "getStackInSlot", slot)).isEmpty()) return false;
            return selected.size() == recipe.inputs().size() && selected.stream().allMatch(selection -> selection.count() == 1);
        }
        @Override public Object catalogState() {
            Object inventory = call(entity, "getInventory");
            return List.of(number(call(inventory, "getSlots"), 0));
        }
        @Override public List<GlobalPos> claimedPositions() { return List.of(GlobalPos.of(level.dimension(), entity.getBlockPos())); }
        @Override public List<Port> ports(Recipe recipe) {
            if (!(recipe.nativeRecipe() instanceof Binding) || !accepts(recipe)) return List.of();
            Object inventory = call(entity, "getInventory");
            int count = Math.toIntExact(number(call(inventory, "getSlots"), 0));
            if (count <= 0 || count > 4096) throw new IllegalStateException("Invalid native orb inventory size");
            List<ItemStack> probes = recipe.inputs().stream().flatMap(ingredient -> ingredient.alternatives().stream()).filter(ItemKey.class::isInstance).map(ItemKey.class::cast).map(ItemKey::sample).toList();
            ItemStack outputProbe = recipe.guaranteed().keySet().stream().filter(ItemKey.class::isInstance).map(ItemKey.class::cast).map(ItemKey::sample).findFirst().orElse(ItemStack.EMPTY);
            List<Port> result = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int slot = index;
                boolean input = probes.stream().anyMatch(probe -> Boolean.TRUE.equals(call(entity, "canInsert", slot, probe)));
                boolean output = Boolean.TRUE.equals(call(entity, "canExtract", slot, outputProbe));
                if (input || output) result.add(slot(entity, inventory, slot, input, output));
            }
            return List.copyOf(result);
        }
    }
    private static Port slot(Object owner, Object inventory, int slot, boolean input, boolean output) {
        return NativeMachinePorts.callbacks(NativeMachinePorts.identity(inventory, slot, "item"), input, output,
                () -> { ItemStack stack = item(call(inventory, "getStackInSlot", slot)); return stack.isEmpty() ? Map.of() : Map.of(new ItemKey(stack), (long)stack.getCount()); },
                (key, amount, simulate) -> {
                    if (!(key instanceof ItemKey item) || !input || amount != 1 || !Boolean.TRUE.equals(call(owner, "canInsert", slot, item.sample()))) return 0;
                    // Never stage a queued request's batch count in one native slot.
                    ItemStack offered = item.sample().copyWithCount(1);
                    ItemStack remainder = item(call(inventory, "insertItem", slot, offered, simulate));
                    if (!remainder.isEmpty() && (!new ItemKey(remainder).equals(key) || remainder.getCount() != 1)) throw new IllegalStateException("Native orb returned an invalid insertion remainder");
                    return 1 - remainder.getCount();
                }, (key, amount, simulate) -> {
                    if (!(key instanceof ItemKey) || amount < 1) return 0;
                    ItemStack before = item(call(inventory, "getStackInSlot", slot));
                    if (before.isEmpty() || !new ItemKey(before).equals(key)) return 0;
                    int wanted = (int)Math.min(before.getCount(), amount);
                    // This is native manual extraction for cancellation; external extraction intentionally rejects inputs.
                    String method = input && has(inventory, "extractItemFromSlot", slot, wanted, simulate) ? "extractItemFromSlot" : "extractItem";
                    ItemStack taken = item(call(inventory, method, slot, wanted, simulate));
                    if (!taken.isEmpty() && (!new ItemKey(taken).equals(key) || taken.getCount() > wanted)) {
                        if (simulate) throw new IllegalStateException("Native orb extraction simulation changed identity");
                        throw new NativeMachinePorts.ExtractedResources("Native orb extraction changed identity", Map.of(new ItemKey(taken), (long)taken.getCount()));
                    }
                    return taken.getCount();
                });
    }
}
