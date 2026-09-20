package com.cappleapple.astralrepository.crafting;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import java.util.*;

/** Datapack descriptions of real, single-item-input machine inventory boundaries. */
public final class ProcessingRules extends SimpleJsonResourceReloadListener {
    public record Rule(String id, ResourceLocation block, ResourceLocation recipeType, ResourceLocation aboveBlock, int aboveOffset,
                       Direction inputSide, Direction outputSide, List<Integer> inputSlots, List<Integer> outputSlots,
                       int timeoutTicks, int priority) {}
    private static volatile List<Rule> rules = defaults();
    private ProcessingRules() { super(new Gson(), "astral_repository/processors"); }
    public static void registerReload(AddReloadListenerEvent event) { event.addListener(new ProcessingRules()); }
    public static List<Rule> rules() { return rules; }
    public static boolean isCandidate(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return rules.stream().anyMatch(rule -> BuiltInRegistries.BLOCK.containsKey(rule.block()) && state.is(BuiltInRegistries.BLOCK.get(rule.block())));
    }
    private static List<Rule> defaults() {
        return List.of(new Rule("astral_repository:create_pressing", new ResourceLocation("create:depot"), new ResourceLocation("create:pressing"), new ResourceLocation("create:mechanical_press"), 2, Direction.UP, Direction.DOWN, List.of(), List.of(), 2400, 0),
                new Rule("astral_repository:create_milling", new ResourceLocation("create:millstone"), new ResourceLocation("create:milling"), null, 1, Direction.UP, Direction.DOWN, List.of(0), List.of(1,2,3,4,5,6,7,8,9), 2400, 0));
    }
    @Override protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Rule> next = new LinkedHashMap<>();
        defaults().forEach(rule -> next.put(rule.id(), rule));
        json.forEach((id, element) -> {
            try {
                JsonObject object = element.getAsJsonObject();
                if (object.has("enabled") && !object.get("enabled").getAsBoolean()) { next.remove(id.toString()); return; }
                next.put(id.toString(), new Rule(id.toString(), new ResourceLocation(object.get("block").getAsString()), new ResourceLocation(object.get("recipe_type").getAsString()),
                        object.has("above_block") ? new ResourceLocation(object.get("above_block").getAsString()) : null, bounded(object, "above_offset", 1, 1, 8),
                        side(object, "input_side", Direction.UP), side(object, "output_side", Direction.DOWN),
                        slots(object, "input_slots"), slots(object, "output_slots"), bounded(object, "timeout_ticks", 2400, 20, 72000), bounded(object, "priority", 0, -10000, 10000)));
            } catch (RuntimeException error) { CraftingService.LOGGER.error("Invalid processing adapter {}", id, error); }
        });
        rules = List.copyOf(next.values());
    }
    private static Direction side(JsonObject object, String key, Direction fallback) {
        if (!object.has(key)) return fallback;
        String name = object.get(key).getAsString();
        if (name.equals("unsided")) return null;
        Direction side = Direction.byName(name);
        if (side == null) throw new IllegalArgumentException("Unknown side " + name);
        return side;
    }
    private static List<Integer> slots(JsonObject object, String key) {
        if (!object.has(key)) return List.of();
        List<Integer> result = new ArrayList<>();
        for (JsonElement value : object.getAsJsonArray(key)) {
            int slot = value.getAsInt();
            if (slot < 0 || slot > 4095 || result.contains(slot)) throw new IllegalArgumentException("Invalid or repeated slot " + slot);
            result.add(slot);
        }
        return List.copyOf(result);
    }
    private static int bounded(JsonObject object, String key, int fallback, int min, int max) {
        int value = object.has(key) ? object.get(key).getAsInt() : fallback;
        if (value < min || value > max) throw new IllegalArgumentException(key + " outside allowed range");
        return value;
    }
}
