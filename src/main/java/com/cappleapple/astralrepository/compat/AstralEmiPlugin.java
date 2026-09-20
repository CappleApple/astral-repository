package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import dev.emi.emi.api.*;
import dev.emi.emi.api.stack.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

@EmiEntrypoint
public final class AstralEmiPlugin implements EmiPlugin {
    public static boolean registered;
    @Override public void register(EmiRegistry registry){
        registry.removeEmiStacks(stack->!PowerNodeVisibility.visible(stack.getItemStack()));
        registry.removeRecipes(recipe->recipe.getOutputs().stream().anyMatch(stack->!PowerNodeVisibility.visible(stack.getItemStack())));
        registered=true;registry.addDragDropHandler(RuneSettingsScreen.class,new Handler<>());
        registry.addDragDropHandler(RecipeTomeScreen.class,new Handler<>());
        registry.addDragDropHandler(WandScreen.class,new Handler<>());
    }
    public static void refreshPowerVisibility(){
        if(!registered)return;
        // EMI 1.1.24 exposes filtering during registration but no public runtime reload API.
        try{OptionalApi.call(null,"dev.emi.emi.runtime.EmiReloadManager","reload");}
        catch(RuntimeException failure){com.cappleapple.astralrepository.AstralRepository.LOGGER.warn("Could not refresh EMI after the server power setting changed",failure);}
    }
    public static final class Handler<T extends Screen & GhostIngredientScreen> implements EmiDragDropHandler<T>{
        @Override public boolean dropStack(T screen,EmiIngredient ingredient,int x,int y){
            if(!screen.ingredientArea().contains(x,y)||ingredient.getEmiStacks().size()!=1)return false;
            var stack=ingredient.getEmiStacks().getFirst();
            if(!stack.getItemStack().isEmpty())return screen.acceptItem(stack.getItemStack());
            Fluid fluid=stack.getKeyOfType(Fluid.class);return fluid!=null&&screen.acceptFluid(new FluidStack(fluid,1000));
        }
        @Override public void render(T screen,EmiIngredient ingredient,net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){
            var area=screen.ingredientArea();g.fill(area.getX(),area.getY(),area.getX()+area.getWidth(),area.getY()+area.getHeight(),0x337e9eff);
        }
    }
}
