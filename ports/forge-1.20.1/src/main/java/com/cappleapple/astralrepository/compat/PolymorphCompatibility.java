package com.cappleapple.astralrepository.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional Polymorph 0.49 public API bridge for Minecraft 1.20.1. */
public final class PolymorphCompatibility {
    private static final Bridge BRIDGE=load();
    private static boolean warned;
    private record Bridge(Method common,Method playerData,Method blockData,Method menu,Method recipe,Method select) {
        Object api() throws ReflectiveOperationException {return common.invoke(null);}
    }
    private static Bridge load(){
        try{
            Class<?> api=Class.forName("com.illusivesoulworks.polymorph.api.PolymorphApi");
            Class<?> common=Class.forName("com.illusivesoulworks.polymorph.api.common.base.IPolymorphCommon");
            Class<?> data=Class.forName("com.illusivesoulworks.polymorph.api.common.capability.IRecipeData");
            Class<?> player=Class.forName("com.illusivesoulworks.polymorph.api.common.capability.IPlayerRecipeData");
            return new Bridge(api.getMethod("common"),common.getMethod("getRecipeData",Player.class),common.getMethod("getRecipeData",BlockEntity.class),
                    player.getMethod("setContainerMenu",AbstractContainerMenu.class),data.getMethod("getRecipe",RecipeType.class,Container.class,Level.class,List.class),data.getMethod("selectRecipe",Recipe.class));
        }catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException|LinkageError incompatible){warn(incompatible);return null;}
    }
    @SuppressWarnings("unchecked")
    public static Optional<CraftingRecipe> playerRecipe(AbstractContainerMenu menu,CraftingContainer input,Player player){
        if(BRIDGE==null)return null;
        try{
            Object data=((Optional<?>)BRIDGE.playerData().invoke(BRIDGE.api(),player)).orElse(null);
            if(data==null)return null;
            BRIDGE.menu().invoke(data,menu);
            return (Optional<CraftingRecipe>)BRIDGE.recipe().invoke(data,RecipeType.CRAFTING,input,player.level(),new ArrayList<>());
        }catch(ReflectiveOperationException|RuntimeException failure){warn(failure);return null;}
    }
    public static boolean selectPlayerRecipe(Player player,Recipe<?> recipe){
        if(BRIDGE==null)return false;
        try{return select(BRIDGE.playerData().invoke(BRIDGE.api(),player),recipe);}
        catch(ReflectiveOperationException|RuntimeException failure){warn(failure);return false;}
    }
    public static boolean selectBlockRecipe(BlockEntity machine,Recipe<?> recipe){
        if(BRIDGE==null)return false;
        try{return select(BRIDGE.blockData().invoke(BRIDGE.api(),machine),recipe);}
        catch(ReflectiveOperationException|RuntimeException failure){warn(failure);return false;}
    }
    private static boolean select(Object optional,Recipe<?> recipe)throws ReflectiveOperationException{
        Object data=((Optional<?>)optional).orElse(null);if(data==null)return false;
        BRIDGE.select().invoke(data,recipe);return true;
    }
    private static void warn(Throwable failure){if(!warned){warned=true;LogUtils.getLogger().warn("Polymorph recipe selection is unavailable; using normal recipe matching",failure);}}
    private PolymorphCompatibility(){}
}
