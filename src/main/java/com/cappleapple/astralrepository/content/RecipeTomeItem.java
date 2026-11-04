package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import java.util.List;
import net.minecraft.core.HolderLookup;
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
        return ItemStack.of(com.cappleapple.astralrepository.platform.Backport.customData(tome).getCompound("Output"));
    }
    /** Recipe IDs excluded from every path to this tome's taught output. */
    public static java.util.Set<String> excludedRecipes(ItemStack tome) {
        var rules = com.cappleapple.astralrepository.platform.Backport.customData(tome).getCompound("ExcludedRecipes");
        return rules.getAllKeys().stream().filter(id -> ResourceLocation.tryParse(id) != null).limit(256)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public static void setRecipeExcluded(ItemStack tome, String recipe, boolean excluded) {
        var tag = com.cappleapple.astralrepository.platform.Backport.customData(tome);
        var rules = tag.getCompound("ExcludedRecipes");
        if (excluded) { if (rules.size() >= 256 && !rules.contains(recipe)) return; rules.putBoolean(recipe, true); }
        else rules.remove(recipe);
        if (rules.isEmpty()) tag.remove("ExcludedRecipes"); else tag.put("ExcludedRecipes", rules);
        tome.setTag(tag);
    }
    public record OutputTooltip(ItemStack tome) implements net.minecraft.world.inventory.tooltip.TooltipComponent {}
    @Override public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack){
        return isFoil(stack)?java.util.Optional.of(new OutputTooltip(stack.copy())):java.util.Optional.empty();
    }
    @Override public boolean isFoil(ItemStack stack) { return !com.cappleapple.astralrepository.platform.Backport.customData(stack).getString("OutputId").isEmpty(); }
}
