package com.cappleapple.astralrepository.client;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

/** A per-editor catalogue of the registries and tags actually loaded by this client. */
public final class RuneFilterSearch {
    public enum Category { ALL, ITEMS, TAGS, MODS, FLUIDS }
    public record Option(String rule, String name, String detail, ItemStack icon, Category category) {}
    private final List<Option> options;
    private final Map<String,Option> byRule=new HashMap<>();
    public Option option(String rule){return byRule.get(rule.startsWith("!")?rule.substring(1):rule);}

    public RuneFilterSearch() {
        var values = new ArrayList<Option>();
        var namespaces = new TreeMap<String, ItemStack>();
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item == Items.AIR) return;
            var id = BuiltInRegistries.ITEM.getKey(item); var icon = new ItemStack(item);
            values.add(new Option(id.toString(), icon.getHoverName().getString(), id.toString(), icon, Category.ITEMS));
            namespaces.putIfAbsent(id.getNamespace(), icon);
        });
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            var id = pair.key().location();
            var icon = pair.stream().findFirst().map(holder -> new ItemStack(holder.value())).orElse(new ItemStack(Items.PAPER));
            values.add(new Option("#" + id, readable(id.getPath()), "Item tag #" + id, icon, Category.TAGS));
        });
        BuiltInRegistries.FLUID.forEach(fluid -> {
            if (fluid == Fluids.EMPTY) return;
            var id = BuiltInRegistries.FLUID.getKey(fluid); var icon = fluidIcon(fluid);
            String name = new FluidStack(fluid, 1000).getHoverName().getString();
            values.add(new Option("fluid:" + id, name, "Fluid " + id, icon, Category.FLUIDS));
            namespaces.putIfAbsent(id.getNamespace(), icon);
        });
        BuiltInRegistries.FLUID.getTags().forEach(pair -> {
            var id = pair.key().location();
            var icon = pair.stream().findFirst().map(holder -> fluidIcon(holder.value())).orElse(new ItemStack(Items.BUCKET));
            values.add(new Option("fluid:#" + id, readable(id.getPath()), "Fluid tag #" + id, icon, Category.TAGS));
        });
        namespaces.forEach((namespace, icon) -> {
            String name = namespace.equals("minecraft") ? "Minecraft" : ModList.get().getModContainerById(namespace).map(container -> container.getModInfo().getDisplayName()).orElse(namespace);
            values.add(new Option("@" + namespace, name, "Mod @" + namespace, icon, Category.MODS));
        });
        values.sort(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Option::rule));
        options = List.copyOf(values);options.forEach(o->byRule.put(o.rule(),o));
    }
    public List<Option> search(String query, Category category) {
        String[] words = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
        return options.stream().filter(option -> category == Category.ALL || option.category() == category).filter(option -> {
            String haystack = (option.name() + " " + option.detail() + " " + option.rule()).toLowerCase(Locale.ROOT);
            return Arrays.stream(words).allMatch(haystack::contains);
        }).toList();
    }
    private static ItemStack fluidIcon(Fluid fluid) { return new ItemStack(fluid.getBucket() == Items.AIR ? Items.BUCKET : fluid.getBucket()); }
    private static String readable(String path) {
        String text = path.replace('_', ' ').replace('/', ' ');
        return text.isEmpty() ? path : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
