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
            if (rule.block().toString().equals("immersiveengineering:alloy_smelter")) {
                // Dummy blocks share state with the master. Only claim the master position.
                if (!entity.getClass().getName().equals("blusunrize.immersiveengineering.api.multiblocks.blocks.registry.MultiblockBlockEntityMaster")) return null;
                Object helper = method(entity, "getHelper").invoke(entity);
                Object state = method(helper, "getState").invoke(helper);
                if (state == null || !state.getClass().getName().equals("blusunrize.immersiveengineering.common.blocks.multiblocks.logic.AlloySmelterLogic$State")) return null;
                Slots slots = new Slots(method(state, "getInventory").invoke(state));
                return slots.size() == 4 ? new Boundary(slots, slots, 2) : null;
            }
            if (entity.getClass().getName().equals("com.buuz135.industrial.block.resourceproduction.tile.ResourcefulFurnaceTile")) {
                // Titanium combines inventory components differently for each configured side.
                // The verified input/output components keep their own stable three-slot contract.
                Field in = entity.getClass().getDeclaredField("input"), out = entity.getClass().getDeclaredField("output");
                in.setAccessible(true); out.setAccessible(true);
                Slots inputs = new Slots(in.get(entity)), outputs = new Slots(out.get(entity));
                return inputs.size() == 3 && outputs.size() == 3 ? new Boundary(inputs, outputs, -1) : null;
            }
            return input == null || output == null ? null : new Boundary(new Slots(input), new Slots(output), -1);
        } catch (ReflectiveOperationException | RuntimeException unsupported) { return null; }
    }

    private static Method method(Object target, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
