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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@JeiPlugin
public final class AstralJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("astral_repository","ghost_ingredients");}
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
                    .filter(recipe->recipe.value().getResultItem(level.registryAccess()).is(node.getItem())).toList();
            recipes.hideRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING,hiddenPowerRecipes);
            ingredients.removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,List.of(node));
        }
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
        registered=true;registration.addGhostIngredientHandler(RuneSettingsScreen.class,new Handler<>());
        registration.addGhostIngredientHandler(RecipeTomeScreen.class,new Handler<>());
        registration.addGhostIngredientHandler(WandScreen.class,new Handler<>());
    }
    @Override public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration){
        registration.addRecipeTransferHandler(new RecipeHandler(registration.getTransferHelper()),mezz.jei.api.constants.RecipeTypes.CRAFTING);
    }
    public static final class RecipeHandler implements mezz.jei.api.recipe.transfer.IRecipeTransferHandler<com.cappleapple.astralrepository.menu.NexusMenu,net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>>{
        private final mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper helper;
        public RecipeHandler(mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper helper){this.helper=helper;}
        @Override public Class<com.cappleapple.astralrepository.menu.NexusMenu> getContainerClass(){return com.cappleapple.astralrepository.menu.NexusMenu.class;}
        @Override public java.util.Optional<net.minecraft.world.inventory.MenuType<com.cappleapple.astralrepository.menu.NexusMenu>> getMenuType(){return java.util.Optional.of(com.cappleapple.astralrepository.AstralRepository.NEXUS_MENU.get());}
        @Override public mezz.jei.api.recipe.RecipeType<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> getRecipeType(){return mezz.jei.api.constants.RecipeTypes.CRAFTING;}
        @Override public mezz.jei.api.recipe.transfer.IRecipeTransferError transferRecipe(com.cappleapple.astralrepository.menu.NexusMenu menu,net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> recipe,mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,net.minecraft.world.entity.player.Player player,boolean maxTransfer,boolean doTransfer){
            if(!RecipeTransfers.supports(recipe.value()))return helper.createInternalError();
            if(!RecipeTransfers.canFill(menu,recipe.value()))return helper.createUserErrorWithTooltip(net.minecraft.network.chat.Component.literal("Not enough ingredients in your inventory or Repository."));
            if(doTransfer)RecipeTransfers.fill(menu,recipe.id(),maxTransfer?64:1);
            return null;
        }
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
