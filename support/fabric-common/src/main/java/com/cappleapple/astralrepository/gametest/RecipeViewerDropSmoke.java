package com.cappleapple.astralrepository.gametest;
import com.cappleapple.astralrepository.client.*;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;
public final class RecipeViewerDropSmoke {
    public static void rune(RuneSettingsScreen s){
        if(ModList.get().isLoaded("emi"))Emi.rune(s);
        if(ModList.get().isLoaded("jei"))Jei.rune(s);
    }
    public static void verifyRune(RuneSettingsScreen s){
        if(ModList.get().isLoaded("emi")&&!s.currentPage().rules().contains("minecraft:diamond"))throw new AssertionError("EMI item drop failed");
        if(ModList.get().isLoaded("jei")&&!s.currentPage().rules().contains("fluid:minecraft:lava"))throw new AssertionError("JEI fluid drop failed");
    }
    public static void tome(RecipeTomeScreen s){if(ModList.get().isLoaded("emi"))Emi.tome(s);else if(ModList.get().isLoaded("jei"))Jei.tome(s);else s.acceptItem(new ItemStack(Items.CHEST));}
    private static class Emi {
        static void rune(RuneSettingsScreen s){if(!com.cappleapple.astralrepository.compat.AstralEmiPlugin.registered)throw new AssertionError("EMI plugin was not registered");var area=s.ingredientArea();if(!new com.cappleapple.astralrepository.compat.AstralEmiPlugin.Handler<RuneSettingsScreen>().dropStack(s,dev.emi.emi.api.stack.EmiStack.of(new ItemStack(Items.DIAMOND)),area.getX()+8,area.getY()+8))throw new AssertionError("EMI rejected rune target");}
        static void tome(RecipeTomeScreen s){var area=s.ingredientArea();new com.cappleapple.astralrepository.compat.AstralEmiPlugin.Handler<RecipeTomeScreen>().dropStack(s,dev.emi.emi.api.stack.EmiStack.of(new ItemStack(Items.CHEST)),area.getX()+8,area.getY()+8);}
    }
    private static class Jei {
        static void rune(RuneSettingsScreen s){
            if(!com.cappleapple.astralrepository.compat.AstralJeiPlugin.registered)throw new AssertionError("JEI plugin was not registered");
            var fluid=new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA,1000);
            var typed=new mezz.jei.api.ingredients.ITypedIngredient<net.neoforged.neoforge.fluids.FluidStack>(){
                public mezz.jei.api.ingredients.IIngredientType<net.neoforged.neoforge.fluids.FluidStack> getType(){return mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK;}
                public net.neoforged.neoforge.fluids.FluidStack getIngredient(){return fluid;}
            };
            var targets=new com.cappleapple.astralrepository.compat.AstralJeiPlugin.Handler<RuneSettingsScreen>().getTargetsTyped(s,typed,true);
            if(targets.isEmpty())throw new AssertionError("JEI has no fluid ghost target");targets.getFirst().accept(fluid);
        }
        static void tome(RecipeTomeScreen s){
            var item=new ItemStack(Items.CHEST);
            var typed=new mezz.jei.api.ingredients.ITypedIngredient<ItemStack>(){
                public mezz.jei.api.ingredients.IIngredientType<ItemStack> getType(){return mezz.jei.api.constants.VanillaTypes.ITEM_STACK;}
                public ItemStack getIngredient(){return item;}
            };
            new com.cappleapple.astralrepository.compat.AstralJeiPlugin.Handler<RecipeTomeScreen>().getTargetsTyped(s,typed,true).getFirst().accept(item);
        }
    }
}
