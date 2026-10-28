package com.cappleapple.astralrepository.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional Polymorph public API bridge. No optional classes are linked by the normal mod. */
public final class PolymorphCompatibility {
    private static final Bridge BRIDGE = load();
    private static boolean warned;

    private record Bridge(Method instance, Method manager, Method playerRecipe, Method playerData,
                          Method blockData, Method select) {
        Object api() throws ReflectiveOperationException { return instance.invoke(null); }
    }

    private static Bridge load() {
        try {
            Class<?> api = Class.forName("com.illusivesoulworks.polymorph.api.PolymorphApi");
            Class<?> manager = Class.forName("com.illusivesoulworks.polymorph.api.common.base.IPolymorphRecipeManager");
            Class<?> data = Class.forName("com.illusivesoulworks.polymorph.api.common.capability.IRecipeData");
            return new Bridge(api.getMethod("getInstance"), api.getMethod("getRecipeManager"),
                    manager.getMethod("getPlayerRecipe", AbstractContainerMenu.class, RecipeType.class, RecipeInput.class, Level.class, Player.class),
                    api.getMethod("getPlayerRecipeData", Player.class), api.getMethod("getBlockEntityRecipeData", BlockEntity.class),
                    data.getMethod("selectRecipe", RecipeHolder.class));
        } catch (ClassNotFoundException absent) { return null; }
        catch (ReflectiveOperationException | LinkageError incompatible) { warn(incompatible); return null; }
    }

    /** Null means the optional API is unavailable; an empty Optional is a valid no-recipe answer. */
    @SuppressWarnings("unchecked")
    public static Optional<RecipeHolder<CraftingRecipe>> playerRecipe(AbstractContainerMenu menu, CraftingInput input, Player player) {
        if (BRIDGE == null) return null;
        try {
            Object api = BRIDGE.api();
            return (Optional<RecipeHolder<CraftingRecipe>>) BRIDGE.playerRecipe().invoke(
                    BRIDGE.manager().invoke(api), menu, RecipeType.CRAFTING, input, player.level(), player);
        } catch (ReflectiveOperationException | RuntimeException failure) { warn(failure); return null; }
    }

    public static boolean selectPlayerRecipe(Player player, RecipeHolder<?> recipe) {
        if (BRIDGE == null) return false;
        try { return select(BRIDGE.playerData().invoke(BRIDGE.api(), player), recipe); }
        catch (ReflectiveOperationException | RuntimeException failure) { warn(failure); return false; }
    }

    /** Only machines already supported by Polymorph can accept a persistent selection. */
    public static boolean selectBlockRecipe(BlockEntity machine, RecipeHolder<?> recipe) {
        if (BRIDGE == null) return false;
        try { return select(BRIDGE.blockData().invoke(BRIDGE.api(), machine), recipe); }
        catch (ReflectiveOperationException | RuntimeException failure) { warn(failure); return false; }
    }

    private static boolean select(Object data, RecipeHolder<?> recipe) throws ReflectiveOperationException {
        if (data == null) return false;
        BRIDGE.select().invoke(data, recipe);
        return true;
    }

    private static void warn(Throwable failure) {
        if (!warned) { warned = true; LogUtils.getLogger().warn("Polymorph recipe selection is unavailable; using normal recipe matching", failure); }
    }

    private PolymorphCompatibility() {}
}
