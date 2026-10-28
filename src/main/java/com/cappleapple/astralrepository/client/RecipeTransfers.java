package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.menu.RecipeTransferPlan;
import com.cappleapple.astralrepository.network.NetworkPackets;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Shared client checks; the server always resolves and fills the actual recipe again. */
public final class RecipeTransfers {
    private static final Set<NexusMenu> subscribed=Collections.newSetFromMap(new WeakHashMap<>());
    private RecipeTransfers() {}

    public static Map<ItemKey,Long> inventory(NexusMenu menu){
        if(subscribed.add(menu))ClientPacketDistributor.sendToServer(new NetworkPackets.RecipeInventoryRequest(menu.containerId));
        return menu.recipeInventory();
    }
    public static boolean supports(CraftingRecipe recipe){return !recipe.isSpecial()&&(!(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped)||shaped.getWidth()<=3&&shaped.getHeight()<=3)&&!com.cappleapple.astralrepository.port.RecipeCompat.ingredients(recipe).isEmpty();}
    public static boolean canFill(NexusMenu menu,CraftingRecipe recipe){
        if(!supports(recipe))return false;
        Map<ItemKey,Long> stock=inventory(menu);
        if(!menu.getCarried().isEmpty())stock.merge(new ItemKey(menu.getCarried()),(long)menu.getCarried().getCount(),Long::sum);
        var ingredients=NexusMenu.recipeIngredients(recipe,stock);
        return ingredients!=null&&RecipeTransferPlan.assign(ingredients,stock,1,key->key.sample().getMaxStackSize())!=null;
    }
    public static void fill(NexusMenu menu,Identifier recipe,int amount){
        ClientPacketDistributor.sendToServer(new NetworkPackets.FillRecipe(menu.containerId,recipe,Math.clamp(amount,1,64)));
    }
}
