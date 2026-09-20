package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import java.util.List;
import mezz.jei.api.*;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@JeiPlugin
public final class AstralJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid(){return Identifier.fromNamespaceAndPath("astral_repository","ghost_ingredients");}
    public static boolean registered;
    private static mezz.jei.api.runtime.IJeiRuntime runtime;
    private static Boolean powerVisible;
    private static List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> hiddenPowerRecipes=List.of();
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime available){runtime=available;powerVisible=null;refreshPowerVisibility();}
    @Override public void onRuntimeUnavailable(){runtime=null;powerVisible=null;hiddenPowerRecipes=List.of();}
    public static void refreshPowerVisibility(){
        if(runtime==null)return;
        boolean enabled=PowerNodeVisibility.powerEnabled();
        if(powerVisible!=null&&powerVisible==enabled)return;
        powerVisible=enabled;
        var ingredients=runtime.getIngredientManager();
        var recipes=runtime.getRecipeManager();
        var node=new ItemStack(AstralContent.POWER_NODE.get());
        if(enabled){
            ingredients.addIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,List.of(node));
            recipes.unhideRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING,hiddenPowerRecipes);
            hiddenPowerRecipes=List.of();
        }else{
            var level=net.minecraft.client.Minecraft.getInstance().level;
            if(level!=null)hiddenPowerRecipes=recipes.createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get()
                    .filter(recipe->com.cappleapple.astralrepository.port.RecipeCompat.output(recipe.value(),level.registryAccess()).is(node.getItem())).toList();
            recipes.hideRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING,hiddenPowerRecipes);
            ingredients.removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,List.of(node));
        }
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
        registered=true;registration.addGhostIngredientHandler(RuneSettingsScreen.class,new Handler<>());
        registration.addGhostIngredientHandler(RecipeTomeScreen.class,new Handler<>());
        registration.addGhostIngredientHandler(WandScreen.class,new Handler<>());
    }
    public static final class Handler<T extends Screen & GhostIngredientScreen> implements IGhostIngredientHandler<T>{
        @Override public <I> List<Target<I>> getTargetsTyped(T screen,ITypedIngredient<I> ingredient,boolean starting){
            Object value=ingredient.getIngredient();
            if(!(value instanceof ItemStack)&&(!(value instanceof FluidStack)||screen instanceof RecipeTomeScreen))return List.of();
            return List.of(new Target<>(){
                public Rect2i getArea(){return screen.ingredientArea();}
                public void accept(I sample){if(sample instanceof ItemStack item)screen.acceptItem(item);else if(sample instanceof FluidStack fluid)screen.acceptFluid(fluid);}
            });
        }
        @Override public void onComplete(){}
    }
}
