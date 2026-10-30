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
    // Cache values contain detached items and recipes, never their weakly keyed menu.
    private static final Map<NexusMenu,Cache> caches=new WeakHashMap<>();
    private static long recipeEpoch;
    private RecipeTransfers() {}

    /** Called on the client thread after its item tags are replaced. */
    public static void recipesReloaded(){recipeEpoch++;caches.clear();}
    public static long recipeEpoch(){return recipeEpoch;}


    /** Stable network/player/grid stock. EMI adds the cursor in its inventory constructor. */
    public static Map<ItemKey,Long> inventory(NexusMenu menu){return cache(menu).inventory;}

    /** Reuse a viewer inventory until a committed network, local-slot or cursor change. */
    @SuppressWarnings("unchecked")
    public static <T> T cachedInventory(NexusMenu menu,java.util.function.Function<Map<ItemKey,Long>,T> factory){
        Cache cache=cache(menu);
        if(cache.viewerInventory==null)cache.viewerInventory=factory.apply(cache.inventory);
        return (T)cache.viewerInventory;
    }
    public static boolean supports(CraftingRecipe recipe){return !recipe.isSpecial()&&recipe.canCraftInDimensions(3,3)&&!recipe.getIngredients().isEmpty();}
    public static boolean canFill(NexusMenu menu,CraftingRecipe recipe){
        if(!supports(recipe))return false;
        Cache cache=cache(menu);
        var ingredients=recipe.getIngredients();
        boolean stable=recipe.getClass()==net.minecraft.world.item.crafting.ShapedRecipe.class||recipe.getClass()==net.minecraft.world.item.crafting.ShapelessRecipe.class;
        if(stable){for(var ingredient:ingredients)if(!ingredient.isEmpty()&&!memoizable(ingredient)){stable=false;break;};}
        if(stable)return cache.availability.available(recipe,()->matches(cache,recipe));
        // Modded predicates may depend on state outside the inventory; keep their direct checks.
        return matches(cache,recipe);
    }
    private static boolean itemOnly(net.minecraft.world.item.crafting.Ingredient ingredient){
        // A complete current item domain does not promise that it stays fixed forever.
        return ingredient.isSimple()||com.cappleapple.astralrepository.compat.MoonlightRecipeCompatibility.isItemOnly(ingredient);
    }
    private static boolean memoizable(net.minecraft.world.item.crafting.Ingredient ingredient){
        return (ingredient.getClass()==net.minecraft.world.item.crafting.Ingredient.class&&!ingredient.isCustom())||com.cappleapple.astralrepository.compat.MoonlightRecipeCompatibility.isStableItemOnly(ingredient);
    }
    private static boolean matches(Cache cache,CraftingRecipe recipe){
        List<List<ItemKey>> result=new ArrayList<>(Collections.nCopies(9,List.of()));
        var ingredients=recipe.getIngredients();
        int width=recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped?shaped.getWidth():3;
        for(int i=0;i<ingredients.size();i++){
            var ingredient=ingredients.get(i);if(ingredient.isEmpty())continue;
            int target=recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe?i%width+(i/width)*3:i;
            if(target>=9)return false;
            List<ItemKey> candidates;
            if(itemOnly(ingredient))candidates=cache.availability.candidates(Arrays.stream(ingredient.getItems()).map(net.minecraft.world.item.ItemStack::getItem).toList(),key->true);
            // Ingredient predicates must not mutate their input; never expose ItemKey internals.
            else candidates=cache.availability.candidates(null,key->ingredient.test(cache.samples.computeIfAbsent(key,ItemKey::sample)));
            if(candidates.isEmpty())return false;
            result.set(target,candidates);
        }
        return RecipeTransferPlan.assign(result,cache.availability.stock(),1,key->cache.samples.computeIfAbsent(key,ItemKey::sample).getMaxStackSize())!=null;
    }
    private static Cache cache(NexusMenu menu){
        if(subscribed.add(menu))PacketDistributor.sendToServer(new NetworkPackets.RecipeInventoryRequest(menu.containerId));
        Cache cached=caches.get(menu);
        if(cached==null||!cached.matches(menu)){cached=new Cache(menu);caches.put(menu,cached);}
        return cached;
    }
    private static final class Cache {
        private final Map<ItemKey,Long> network,inventory;
        private final ItemKey[] local;
        private final int[] counts;
        private final RecipeAvailabilityCache<ItemKey,net.minecraft.world.item.Item,CraftingRecipe> availability;
        private final Map<ItemKey,net.minecraft.world.item.ItemStack> samples=new HashMap<>();
        private Object viewerInventory;
        private Cache(NexusMenu menu){
            network=menu.committedRecipeInventory();
            local=new ItemKey[menu.slots.size()];counts=new int[local.length];
            Map<ItemKey,Long> stock=new LinkedHashMap<>(network);
            for(int i=0;i<local.length;i++){
                var stack=i==0?menu.getCarried():menu.slots.get(i).getItem();
                if(!stack.isEmpty()){
                    local[i]=new ItemKey(stack);counts[i]=stack.getCount();
                    if(i!=0)stock.merge(local[i],(long)counts[i],Long::sum);
                }
            }
            inventory=Collections.unmodifiableMap(stock);
            Map<ItemKey,Long> withCursor=stock;
            if(local[0]!=null){withCursor=new LinkedHashMap<>(stock);withCursor.merge(local[0],(long)counts[0],Long::sum);}
            availability=new RecipeAvailabilityCache<>(withCursor,ItemKey::item);
        }
        private boolean matches(NexusMenu menu){
            if(network!=menu.committedRecipeInventory()||local.length!=menu.slots.size())return false;
            for(int i=0;i<local.length;i++){
                var stack=i==0?menu.getCarried():menu.slots.get(i).getItem();
                if(local[i]==null){if(!stack.isEmpty())return false;}
                else if(stack.getCount()!=counts[i]||!local[i].matches(stack))return false;
            }
            return true;
        }
    }
    public static void fill(NexusMenu menu,ResourceLocation recipe,int amount){
        PacketDistributor.sendToServer(new NetworkPackets.FillRecipe(menu.containerId,recipe,Math.clamp(amount,1,64)));
    }
}
