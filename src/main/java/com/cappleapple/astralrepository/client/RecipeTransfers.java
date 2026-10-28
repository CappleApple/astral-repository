package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.menu.RecipeTransferPlan;
import com.cappleapple.astralrepository.network.NetworkPackets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared client checks; the server always resolves and fills the actual recipe again. */
public final class RecipeTransfers {
    private static final Set<NexusMenu> subscribed=Collections.newSetFromMap(new WeakHashMap<>());
    private RecipeTransfers() {}

    public static Map<ItemKey,Long> inventory(NexusMenu menu){
        if(subscribed.add(menu))PacketDistributor.sendToServer(new NetworkPackets.RecipeInventoryRequest(menu.containerId));
        return menu.recipeInventory();
    }
    public static boolean supports(CraftingRecipe recipe){return !recipe.isSpecial()&&recipe.canCraftInDimensions(3,3)&&!recipe.getIngredients().isEmpty();}
    public static boolean canFill(NexusMenu menu,CraftingRecipe recipe){
        if(!supports(recipe))return false;
        Map<ItemKey,Long> stock=inventory(menu);
        if(!menu.getCarried().isEmpty())stock.merge(new ItemKey(menu.getCarried()),(long)menu.getCarried().getCount(),Long::sum);
        var ingredients=NexusMenu.recipeIngredients(recipe,stock);
        return ingredients!=null&&RecipeTransferPlan.assign(ingredients,stock,1,key->key.sample().getMaxStackSize())!=null;
    }
    public static void fill(NexusMenu menu,ResourceLocation recipe,int amount){
        PacketDistributor.sendToServer(new NetworkPackets.FillRecipe(menu.containerId,recipe,Math.clamp(amount,1,64)));
    }
}
