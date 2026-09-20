package com.cappleapple.astralrepository.content;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Default preset glyphs and identifiers retained for saved rune artwork. */
public final class RuneGlyph {
    private static final Map<String,String[]> PIXELS=Map.ofEntries(
        Map.entry("push_rune",new String[]{"...#...","..###..",".#.#.#.","...#...","...#...",".#####.","......."}),
        Map.entry("pull_rune",new String[]{".......",".#####.","...#...","...#...",".#.#.#.","..###..","...#..."}),
        Map.entry("filter_sigil",new String[]{"...#...","..#.#..",".#...#.","#..#..#",".#...#.","..#.#..","...#..."}),
        Map.entry("routing_rune",new String[]{"...#...","..###..",".#.#.#.","...#...",".#.#.#.","..###..","...#..."}),
        Map.entry("collection_rune",new String[]{"#..#..#",".#.#.#.","..###..","...#...","...#...","..###..","..#.#.."}),
        Map.entry("distribution_rune",new String[]{"...#...","...#...","..###..",".#.#.#.","#..#..#","...#...","...#..."}),
        Map.entry("and_rune",new String[]{".#...#.",".#...#.","..#.#..","...#...","..#.#..",".#...#.",".#...#."}),
        Map.entry("not_rune",new String[]{"..###..",".#...#.","#....##","#..#..#","##....#",".#...#.","..###.."}),
        Map.entry("direction_rune",new String[]{"...#...","..##...",".#.#...","#..####",".#.#...","..##...","...#..."}),
        Map.entry("stock_rune",new String[]{".#####.",".#...#.","..###..","...#...","..###..",".#...#.",".#####."}),
        Map.entry("priority_rune",new String[]{"...#...","..#.#..",".#...#.",".......","...#...","..#.#..",".#...#."}));
    public static net.minecraft.resources.ResourceLocation id(RuneLayer.Mode mode){return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("astral_repository",switch(mode){case PUSH->"push_rune";case PULL->"pull_rune";case FILTER->"filter_sigil";});}
    public static boolean isRune(ItemStack stack) { var id=BuiltInRegistries.ITEM.getKey(stack.getItem()); return id.getNamespace().equals("astral_repository")&&(id.getPath().equals("push_rune")||id.getPath().equals("pull_rune")); }
    public static boolean isLegacyRune(ItemStack stack) { var id=BuiltInRegistries.ITEM.getKey(stack.getItem()); return id.getNamespace().equals("astral_repository")&&PIXELS.containsKey(id.getPath())&&!isRune(stack); }
    public static String[] pixels(String path) { return PIXELS.getOrDefault(path,PIXELS.get("filter_sigil")); }
    public static int color(String path) { return switch(path) {
        case "push_rune" -> 0xFFD998;
        case "pull_rune" -> 0xA2DDF5;
        case "collection_rune" -> 0xA2DDF5;
        case "distribution_rune" -> 0xFFD998;
        case "routing_rune" -> 0xC6A4FF;
        case "filter_sigil" -> 0xDFB5FF;
        case "and_rune" -> 0xCBA8FF;
        case "not_rune" -> 0xFC91BB;
        case "direction_rune" -> 0x97E0D4;
        case "stock_rune" -> 0xE4D894;
        case "priority_rune" -> 0xFFD594;
        default -> 0xCEAEFF;
    }; }
    private RuneGlyph() {}
}
