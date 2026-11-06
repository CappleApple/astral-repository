package com.cappleapple.astralrepository.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.lang.reflect.*;
import java.util.List;

/** Optional bridges to real mod inventories; no recipe execution occurs here. */
final class MachineInventory {
    record Boundary(Slots input, Slots output, int fuelSlot) {}
    static final class Slots {
        private final Object inventory;
        private final Method size, get, insert, extract;
        Slots(Object inventory) throws ReflectiveOperationException {
            this.inventory = inventory;
            size = method(inventory, "getSlots");
            get = method(inventory, "getStackInSlot", int.class);
            insert = method(inventory, "insertItem", int.class, ItemStack.class, boolean.class);
            extract = method(inventory, "extractItem", int.class, int.class, boolean.class);
        }
        int size() { return ((Number) call(size)).intValue(); }
        ItemStack get(int slot) { return (ItemStack) call(get, slot); }
        ItemStack insert(int slot, ItemStack stack, boolean simulate) { return (ItemStack) call(insert, slot, stack, simulate); }
        ItemStack extract(int slot, int count) { return (ItemStack) call(extract, slot, count, false); }
        private Object call(Method method, Object... args) {
            try { return method.invoke(inventory, args); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Machine inventory operation failed", failure); }
        }
    }

    static Boundary find(BlockEntity entity, ProcessingRules.Rule rule, Object input, Object output) {
        if (entity == null) return null;
        try {
            return input == null || output == null ? null : new Boundary(new Slots(input), new Slots(output), -1);
        } catch (ReflectiveOperationException | RuntimeException unsupported) { return null; }
    }

    private static Method method(Object target, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
