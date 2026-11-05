package com.cappleapple.astralrepository.client;

import java.util.Locale;

/** Names the recipe's process without claiming that a matching machine is connected. */
public final class RecipeProcessLabels {
    private RecipeProcessLabels() {}

    public static String describe(String type) {
        return switch (type) {
            case "minecraft:crafting" -> "Crafting table";
            case "minecraft:smelting" -> "Smelting / Furnace";
            case "minecraft:blasting" -> "Blasting / Blast furnace";
            case "minecraft:smoking" -> "Smoking / Smoker";
            case "minecraft:campfire_cooking" -> "Campfire cooking";
            case "minecraft:stonecutting" -> "Stonecutter";
            case "minecraft:smithing" -> "Smithing table";
            case "minecraft:brewing" -> "Brewing stand";
            case "create:crushing" -> "Create / Crushing wheels";
            case "create:milling" -> "Create / Millstone";
            case "create:pressing" -> "Create / Mechanical press";
            case "immersiveengineering:alloy" -> "Immersive Engineering / Alloy Kiln";
            case "oritech:foundry" -> "Oritech / Foundry";
            default -> {
                int colon = type.indexOf(':');
                yield colon < 0 ? title(type) : title(type.substring(0, colon)) + " / " + title(type.substring(colon + 1));
            }
        };
    }

    private static String title(String id) {
        String[] words = id.replace('_', ' ').replace('/', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }
}
