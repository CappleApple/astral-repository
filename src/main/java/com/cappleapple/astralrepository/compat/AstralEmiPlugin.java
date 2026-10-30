package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import dev.emi.emi.api.*;
import dev.emi.emi.api.stack.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

@EmiEntrypoint
public final class AstralEmiPlugin implements EmiPlugin {
    public static boolean registered;
    @Override public void register(EmiRegistry registry){
        registry.removeEmiStacks(stack->!PowerNodeVisibility.visible(stack.getItemStack()));
        registry.removeRecipes(recipe->recipe.getOutputs().stream().anyMatch(stack->!PowerNodeVisibility.visible(stack.getItemStack())));
        registered=true;registry.addDragDropHandler(RuneSettingsScreen.class,new Handler<>());
        registry.addDragDropHandler(RecipeTomeScreen.class,new Handler<>());
        registry.addDragDropHandler(WandScreen.class,new Handler<>());
        registry.addRecipeHandler(com.cappleapple.astralrepository.AstralRepository.NEXUS_MENU.get(),new RecipeHandler());
    }
    private static final class CachedInventory extends dev.emi.emi.api.recipe.EmiPlayerInventory {
        private final long recipeEpoch=RecipeTransfers.recipeEpoch();
        private CachedInventory(java.util.Map<com.cappleapple.astralrepository.api.ItemKey,Long> stock){
            super(stock.entrySet().stream().map(entry->EmiStack.of(entry.getKey().sample()).setAmount(entry.getValue())).toList());
        }
        @Override public boolean isEqual(dev.emi.emi.api.recipe.EmiPlayerInventory other){
            if(this==other)return true;
            if(other instanceof CachedInventory cached&&recipeEpoch!=cached.recipeEpoch)return false;
            return super.isEqual(other);
        }
    }
    public static void refreshPowerVisibility(){
        if(!registered)return;
        // EMI 1.1.24 exposes filtering during registration but no public runtime reload API.
        try{OptionalApi.call(null,"dev.emi.emi.runtime.EmiReloadManager","reload");}
        catch(RuntimeException failure){com.cappleapple.astralrepository.AstralRepository.LOGGER.warn("Could not refresh EMI after the server power setting changed",failure);}
    }
    public static final class RecipeHandler implements dev.emi.emi.api.recipe.handler.EmiRecipeHandler<com.cappleapple.astralrepository.menu.NexusMenu>{
        @Override public dev.emi.emi.api.recipe.EmiPlayerInventory getInventory(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<com.cappleapple.astralrepository.menu.NexusMenu> screen){
            return RecipeTransfers.cachedInventory(screen.getMenu(),CachedInventory::new);
        }
        @Override public boolean supportsRecipe(dev.emi.emi.api.recipe.EmiRecipe recipe){
            var backing=recipe.getBackingRecipe();return backing!=null&&backing instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting&&RecipeTransfers.supports(crafting);
        }
        @Override public boolean canCraft(dev.emi.emi.api.recipe.EmiRecipe recipe,dev.emi.emi.api.recipe.handler.EmiCraftContext<com.cappleapple.astralrepository.menu.NexusMenu> context){
            var backing=recipe.getBackingRecipe();return supportsRecipe(recipe)&&RecipeTransfers.canFill(context.getScreenHandler(),(net.minecraft.world.item.crafting.CraftingRecipe)backing);
        }
        @Override public boolean craft(dev.emi.emi.api.recipe.EmiRecipe recipe,dev.emi.emi.api.recipe.handler.EmiCraftContext<com.cappleapple.astralrepository.menu.NexusMenu> context){
            if(!canCraft(recipe,context))return false;
            RecipeTransfers.fill(context.getScreenHandler(),recipe.getBackingRecipe().getId(),context.getAmount());return true;
        }
    }
    public static final class Handler<T extends Screen & GhostIngredientScreen> implements EmiDragDropHandler<T>{
        @Override public boolean dropStack(T screen,EmiIngredient ingredient,int x,int y){
            if(!screen.ingredientArea().contains(x,y)||ingredient.getEmiStacks().size()!=1)return false;
            var stack=ingredient.getEmiStacks().get(0);
            if(!stack.getItemStack().isEmpty())return screen.acceptItem(stack.getItemStack());
            Fluid fluid=stack.getKeyOfType(Fluid.class);return fluid!=null&&screen.acceptFluid(new FluidStack(fluid,1000));
        }
        @Override public void render(T screen,EmiIngredient ingredient,net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){
            var area=screen.ingredientArea();g.fill(area.getX(),area.getY(),area.getX()+area.getWidth(),area.getY()+area.getHeight(),0x337e9eff);
        }
    }
}
