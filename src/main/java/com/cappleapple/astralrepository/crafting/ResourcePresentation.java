package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Display-only icons. These samples are never inserted into storage or used as recipe identities. */
public final class ResourcePresentation {
    private ResourcePresentation() {}
    public static ItemStack icon(ResourceKey key) {
        if (key instanceof ItemKey item) return item.sample();
        ItemStack icon = new ItemStack(key instanceof FluidKey ? Items.BUCKET : Items.GLASS_BOTTLE);
        icon.setHoverName(name(key));
        return icon;
    }
    private static Component name(ResourceKey key) {
        if (key instanceof ChemicalKey chemical) {
            try {
                Object sample = chemical.sample();
                Object text = sample.getClass().getMethod("getTextComponent").invoke(sample);
                if (text instanceof Component component) return component.copy().append(" (" + key.type().getPath() + ")");
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        if (key.type().equals(ResourceKinds.ENERGY)) return Component.literal("Energy (FE)");
        String label = key.id().getPath().replace('_', ' ').replace('/', ' ');
        if (!label.isEmpty()) label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
        return Component.literal(label + " (" + (key instanceof FluidKey ? "mB" : key.type().getPath()) + ")");
    }
    static List<MissingIngredients.Missing<ItemKey>> missing(List<MissingIngredients.Missing<ResourceKey>> missing) {
        return missing.stream().map(entry -> new MissingIngredients.Missing<>(entry.alternatives().stream()
                .map(key -> key instanceof ItemKey item ? item : new ItemKey(icon(key))).toList(), entry.tag(), entry.count())).toList();
    }
}
