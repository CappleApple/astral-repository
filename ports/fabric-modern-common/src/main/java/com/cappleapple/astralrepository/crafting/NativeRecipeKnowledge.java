package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.content.CrystalNodeBlockEntity;
import com.cappleapple.astralrepository.network.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-owned native output aliases available through a locally accessible, loaded network. */
public final class NativeRecipeKnowledge {
    public record Entry(Identifier recipe, Identifier type, ItemStack output,
                        List<ItemStack> ingredients, String process) {
        public Entry { output = output.copy(); ingredients = ingredients.stream().map(ItemStack::copy).toList(); }
    }
    private record Alias(Identifier recipe, ItemKey output, String process, List<CraftRecipe.Ingredient<ResourceKey>> inputs) {}
    private NativeRecipeKnowledge() {}

    public static List<Entry> entries(ServerPlayer player) {
        return entries(player, ItemStack.EMPTY);
    }
    public static List<Entry> entries(ServerPlayer player, ItemStack desiredOutput) {
        if (!player.level().getServer().isSameThread()) throw new IllegalStateException("Native recipe knowledge is server-thread only");
        var level = player.level(); var manager = NetworkManager.get(player.level().getServer());
        Set<AstralNetwork> networks = Collections.newSetFromMap(new IdentityHashMap<>());
        BlockPos center = player.blockPosition();
        for (int chunkX = (center.getX() - 8) >> 4; chunkX <= (center.getX() + 8) >> 4; chunkX++)
            for (int chunkZ = (center.getZ() - 8) >> 4; chunkZ <= (center.getZ() + 8) >> 4; chunkZ++) {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ); if (chunk == null) continue;
                for (var entity : List.copyOf(chunk.getBlockEntities().values())) {
                    if (!(entity instanceof CrystalNodeBlockEntity node) || node.isRemoved() || !node.enabled()
                            || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(node.getBlockPos())) > 64) continue;
                    GlobalPos origin = GlobalPos.of(level.dimension(), node.getBlockPos());
                    if (!manager.canAccess(player, origin, false)) continue;
                    AstralNetwork network = manager.networkAt(origin); if (network != null) networks.add(network);
                }
            }
        Map<Alias, Entry> entries = new LinkedHashMap<>();
        for (AstralNetwork network : networks) for (var recipe : network.crafting().catalogSnapshot(desiredOutput)) {
            // Ordinary recipes remain globally browsable through RecipeManager; this adds native resource aliases.
            if (!(recipe.output() instanceof ItemKey item) || !(recipe.process().startsWith("native/") || recipe.process().startsWith("mekanism/"))) continue;
            if (recipe.process().startsWith("mekanism/") && recipe.process().contains("/unbound")) continue;
            ItemStack output = item.sample(); if (output.isEmpty() || !output.isItemEnabled(level.enabledFeatures())) continue;
            Set<Identifier> sources = new TreeSet<>(Comparator.comparing(Identifier::toString));
            for (String source : network.crafting().sourceRecipes(recipe.id())) {
                Identifier canonical = canonicalId(source); if (canonical != null) sources.add(canonical);
            }
            if (sources.isEmpty()) { Identifier canonical = canonicalId(recipe.id()); if (canonical != null) sources.add(canonical); }
            for (Identifier canonical : sources) {
                Identifier type = type(player, canonical, recipe.process());
                Alias key = new Alias(canonical, item, recipe.process(), recipe.ingredients());
                String label = network.crafting().workstationLabel(recipe.id());
                if (label.isBlank()) label = title(type.getNamespace()) + " / " + title(type.getPath());
                entries.putIfAbsent(key, new Entry(canonical, type, output.copyWithCount((int)Math.min(Integer.MAX_VALUE, recipe.grossOutputCount())),
                        preview(recipe.ingredients()), label.length() <= 256 ? label : label.substring(0, 256)));
            }
        }
        return List.copyOf(entries.values());
    }

    /** Re-resolve at inscription time; an old page or client sample never supplies a saved result. */
    public static ItemStack resolve(ServerPlayer player, Identifier recipe, ItemStack selector) {
        return entries(player, selector).stream().filter(entry -> entry.recipe().equals(recipe)
                && (selector.isEmpty() || ItemStack.isSameItemSameComponents(entry.output(), selector)))
                .map(entry -> entry.output().copy()).findFirst().orElse(ItemStack.EMPTY);
    }

    /** Native aliases end in their original recipe ID, preserving exclusion matching across profiles. */
    static Identifier canonicalId(String alias) {
        Identifier direct = Identifier.tryParse(alias); if (direct != null) return direct;
        int colon = alias.lastIndexOf(':'); if (colon < 0) return null;
        int start = alias.lastIndexOf('/', colon) + 1;
        Identifier suffix = Identifier.tryParse(alias.substring(start));
        return suffix != null && alias.endsWith("/" + suffix) ? suffix : null;
    }
    private static Identifier type(ServerPlayer player, Identifier canonical, String process) {
        var holder = player.level().getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, canonical));
        if (holder.isPresent()) {
            Identifier type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.get().value().getType());
            if (type != null) return type;
        }
        for (String part : process.split("/")) if (part.contains(":")) { Identifier type = Identifier.tryParse(part); if (type != null) return type; }
        String[] parts = process.split("/"); String namespace = parts.length > 1 && parts[0].equals("native") ? parts[1] : parts[0];
        Identifier fallback = Identifier.tryParse(namespace + ":native_processing");
        return fallback == null ? Identifier.fromNamespaceAndPath("astral_repository", "native_processing") : fallback;
    }
    private static List<ItemStack> preview(List<CraftRecipe.Ingredient<ResourceKey>> ingredients) {
        List<ItemStack> result = new ArrayList<>();
        for (var ingredient : ingredients) {
            if (result.size() == 9) break;
            ResourceKey key = ingredient.alternatives().getFirst(); ItemStack display = ResourcePresentation.icon(key);
            if (key instanceof ItemKey) display.setCount((int)Math.min(Integer.MAX_VALUE, ingredient.count()));
            else {
                String unit = key instanceof FluidKey ? " mB" : key.type().equals(ResourceKinds.ENERGY) ? " FE" : " units";
                display.set(DataComponents.CUSTOM_NAME, Component.literal(display.getHoverName().getString() + ": " + ingredient.count() + unit));
            }
            result.add(display);
        }
        return List.copyOf(result);
    }
    private static String title(String id) {
        StringBuilder label = new StringBuilder();
        for (String word : id.replace('_', ' ').replace('/', ' ').split(" ")) if (!word.isEmpty()) {
            if (!label.isEmpty()) label.append(' ');
            label.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return label.toString();
    }
}
