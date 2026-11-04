package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class RecipeTomeItem extends Item {
    public RecipeTomeItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new RecipeTomeMenu(id, inventory, hand),
                    Component.literal("Recipe Tome")));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    public static ItemStack product(ItemStack tome, HolderLookup.Provider registries) {
        return ItemStack.parseOptional(registries, tome.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("Output"));
    }
    /** Recipe IDs excluded from every path to this tome's taught output. */
    public static java.util.Set<String> excludedRecipes(ItemStack tome) {
        var rules = tome.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("ExcludedRecipes");
        return rules.getAllKeys().stream().filter(id -> ResourceLocation.tryParse(id) != null).limit(256)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public static void setRecipeExcluded(ItemStack tome, String recipe, boolean excluded) {
        var tag = tome.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var rules = tag.getCompound("ExcludedRecipes");
        if (excluded) { if (rules.size() >= 256 && !rules.contains(recipe)) return; rules.putBoolean(recipe, true); }
        else rules.remove(recipe);
        if (rules.isEmpty()) tag.remove("ExcludedRecipes"); else tag.put("ExcludedRecipes", rules);
        tome.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public record OutputTooltip(ItemStack tome) implements net.minecraft.world.inventory.tooltip.TooltipComponent {}
    @Override public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack){
        return isFoil(stack)?java.util.Optional.of(new OutputTooltip(stack.copy())):java.util.Optional.empty();
    }
    @Override public boolean isFoil(ItemStack stack) { return !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("OutputId").isEmpty(); }
}
