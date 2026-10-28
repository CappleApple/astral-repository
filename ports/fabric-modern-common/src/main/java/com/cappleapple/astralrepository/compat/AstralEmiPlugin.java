package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import dev.emi.emi.api.*;
import dev.emi.emi.api.stack.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.material.Fluid;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;

@EmiEntrypoint
public final class AstralEmiPlugin implements EmiPlugin {
    public static boolean registered;
    @Override public void register(EmiRegistry registry){
        registry.removeEmiStacks(stack->!PowerNodeVisibility.visible(stack.getItemStack()));
        OptionalApi.call(registry,"dev.emi.emi.api.EmiRegistry","removeRecipes",new Class<?>[]{java.util.function.Predicate.class},(java.util.function.Predicate<Object>)recipe -> {
            var outputs=(java.util.List<?>)OptionalApi.call(recipe,"dev.emi.emi.api.recipe.EmiRecipe","getOutputs");
            return outputs.stream().anyMatch(stack->!PowerNodeVisibility.visible((net.minecraft.world.item.ItemStack)OptionalApi.call(stack,"dev.emi.emi.api.stack.EmiStack","getItemStack")));
        });
        registered=true;registry.addDragDropHandler(RuneSettingsScreen.class,handler());
        registry.addDragDropHandler(RecipeTomeScreen.class,handler());
        registry.addDragDropHandler(WandScreen.class,handler());
        OptionalApi.call(registry,"dev.emi.emi.api.EmiRegistry","addRecipeHandler",new Class<?>[]{net.minecraft.world.inventory.MenuType.class,OptionalApi.type("dev.emi.emi.api.recipe.handler.EmiRecipeHandler")},com.cappleapple.astralrepository.AstralRepository.NEXUS_MENU.get(),recipeHandler());
    }

    private static Object backingRecipe(Object recipe){return OptionalApi.call(recipe,"dev.emi.emi.api.recipe.EmiRecipe","getBackingRecipe");}
    private static boolean supportsTransfer(Object recipe){
        Object backing=backingRecipe(recipe);
        return backing instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder&&holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting&&RecipeTransfers.supports(crafting);
    }
    private static Object recipeHandler(){
        Class<?> handler=OptionalApi.type("dev.emi.emi.api.recipe.handler.EmiRecipeHandler");
        return java.lang.reflect.Proxy.newProxyInstance(handler.getClassLoader(),new Class<?>[]{handler},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "toString"->"Astral recipe transfer";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->null;};
            if(method.getName().equals("supportsRecipe"))return supportsTransfer(args[0]);
            if(method.getName().equals("getInventory")){
                var screen=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)args[0];
                var stacks=RecipeTransfers.inventory((com.cappleapple.astralrepository.menu.NexusMenu)screen.getMenu()).entrySet().stream().map(entry->EmiStack.of(entry.getKey().sample()).setAmount(entry.getValue())).toList();
                return OptionalApi.type("dev.emi.emi.api.recipe.EmiPlayerInventory").getConstructor(java.util.List.class).newInstance(stacks);
            }
            if(method.getName().equals("canCraft")||method.getName().equals("craft")){
                if(!supportsTransfer(args[0]))return false;
                var menu=(com.cappleapple.astralrepository.menu.NexusMenu)OptionalApi.call(args[1],"dev.emi.emi.api.recipe.handler.EmiCraftContext","getScreenHandler");
                var backing=(net.minecraft.world.item.crafting.RecipeHolder<?>)backingRecipe(args[0]);
                boolean possible=RecipeTransfers.canFill(menu,(net.minecraft.world.item.crafting.CraftingRecipe)backing.value());
                if(possible&&method.getName().equals("craft"))RecipeTransfers.fill(menu,backing.id().identifier(),(int)OptionalApi.call(args[1],"dev.emi.emi.api.recipe.handler.EmiCraftContext","getAmount"));
                return possible;
            }
            if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
            return null;
        });
    }
    public static void refreshPowerVisibility(){
        if(!registered)return;
        // EMI 1.1.24 exposes filtering during registration but no public runtime reload API.
        try{OptionalApi.call(null,"dev.emi.emi.runtime.EmiReloadManager","reload");}
        catch(RuntimeException failure){com.cappleapple.astralrepository.AstralRepository.LOGGER.warn("Could not refresh EMI after the server power setting changed",failure);}
    }
    @SuppressWarnings("unchecked")
    private static <T extends Screen & GhostIngredientScreen> EmiDragDropHandler<T> handler(){
        return (EmiDragDropHandler<T>)java.lang.reflect.Proxy.newProxyInstance(EmiDragDropHandler.class.getClassLoader(),new Class<?>[]{EmiDragDropHandler.class},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "toString"->"Astral ghost ingredients";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->null;};
            var screen=(GhostIngredientScreen)args[0];var ingredient=(EmiIngredient)args[1];
            if(method.getName().equals("dropStack")){
                int x=(int)args[2],y=(int)args[3];
                if(!screen.ingredientArea().contains(x,y)||ingredient.getEmiStacks().size()!=1)return false;
                var stack=ingredient.getEmiStacks().getFirst();
                if(!stack.getItemStack().isEmpty())return screen.acceptItem(stack.getItemStack());
                Fluid fluid=stack.getKeyOfType(Fluid.class);return fluid!=null&&screen.acceptFluid(new FluidStack(fluid,1000));
            }
            if(args.length>2&&args[2] instanceof net.minecraft.client.gui.GuiGraphicsExtractor graphics){
                var area=screen.ingredientArea();graphics.fill(area.getX(),area.getY(),area.getX()+area.getWidth(),area.getY()+area.getHeight(),0x337e9eff);
            }
            return null;
        });
    }
}
