package com.cappleapple.astralrepository.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/** The verified three-slot cooking contract; other inventories require a processing adapter. */
final record FurnaceAccess(BlockEntity entity, Container inventory, RecipeType<?> recipeType) {
    private record IronContract(Field recipeType, Method isFurnace) {}
    private static final ClassValue<Optional<IronContract>> IRON = new ClassValue<>() {
        @Override protected Optional<IronContract> computeValue(Class<?> type) {
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                if (!parent.getName().equals("ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase")) continue;
                try { return Optional.of(new IronContract(parent.getField("recipeType"), parent.getMethod("isFurnace"))); }
                catch (ReflectiveOperationException failure) {
                    CraftingService.LOGGER.warn("Unsupported Iron Furnaces cooking API: {}", type.getName());
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }
    };

    static boolean isCandidate(BlockEntity entity) {
        return entity instanceof AbstractFurnaceBlockEntity || entity != null && IRON.get(entity.getClass()).isPresent();
    }

    static FurnaceAccess find(ServerLevel level, BlockPos position, String process) {
        BlockEntity entity = level.getBlockEntity(position);
        if (!(entity instanceof Container inventory) || inventory.getContainerSize() < 3) return null;
        RecipeType<?> actual = null;
        var state = level.getBlockState(position);
        if (entity instanceof AbstractFurnaceBlockEntity) {
            if (state.getBlock() instanceof FurnaceBlock) actual = RecipeType.SMELTING;
            else if (state.getBlock() instanceof BlastFurnaceBlock) actual = RecipeType.BLASTING;
            else if (state.getBlock() instanceof SmokerBlock) actual = RecipeType.SMOKING;
        } else {
            var contract = IRON.get(entity.getClass());
            if (contract.isEmpty() || inventory.getContainerSize() < 6 || !inventory.getItem(5).isEmpty()) return null;
            try {
                if (!Boolean.TRUE.equals(contract.get().isFurnace().invoke(entity))) return null;
                Object value = contract.get().recipeType().get(entity);
                if (value instanceof RecipeType<?> type) actual = type;
                // Iron Furnaces updates its cached mode on a tick. Wait for a just-changed
                // augment rather than offering ingredients to the previous cooking mode.
                String augment = inventory.getItem(3).getItem().getClass().getName();
                RecipeType<?> selected = switch (augment) {
                    case "ironfurnaces.items.augments.ItemAugmentBlasting" -> RecipeType.BLASTING;
                    case "ironfurnaces.items.augments.ItemAugmentSmoking" -> RecipeType.SMOKING;
                    default -> RecipeType.SMELTING;
                };
                if (actual != selected) return null;
            } catch (ReflectiveOperationException | RuntimeException failure) { return null; }
        }
        RecipeType<?> requested = switch (process) {
            case "smelting" -> RecipeType.SMELTING;
            case "blasting" -> RecipeType.BLASTING;
            case "smoking" -> RecipeType.SMOKING;
            default -> null;
        };
        return actual != null && actual == requested && state.hasProperty(BlockStateProperties.LIT)
                ? new FurnaceAccess(entity, inventory, actual) : null;
    }

    boolean lit(ServerLevel level) { return level.getBlockState(entity.getBlockPos()).getValue(BlockStateProperties.LIT); }
    ItemStack getItem(int slot) { return inventory.getItem(slot); }
    void setItem(int slot, ItemStack stack) { inventory.setItem(slot, stack); }
    ItemStack removeItem(int slot, int count) { return inventory.removeItem(slot, count); }
    boolean canPlaceItem(int slot, ItemStack stack) { return inventory.canPlaceItem(slot, stack); }
    void setChanged() { inventory.setChanged(); }
}
