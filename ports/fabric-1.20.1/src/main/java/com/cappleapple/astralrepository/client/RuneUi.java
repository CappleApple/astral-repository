package com.cappleapple.astralrepository.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import com.cappleapple.astralrepository.platform.client.extensions.common.IClientFluidTypeExtensions;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;

/** Shared textured slots; tint is drawn before item/fluid contents. */
public final class RuneUi {
    private static final ResourceLocation SLOT=new ResourceLocation("astral_repository","rune_button");
    public static void slot(GuiGraphics g,int x,int y,int size,boolean hovered){com.cappleapple.astralrepository.platform.ClientBackport.blitSprite(g,SLOT,x,y,size,size);if(hovered)g.fill(x+2,y+2,x+size-2,y+size-2,0x447e9eff);}
    public static void dropArea(GuiGraphics g,int x,int y,int width,int height){com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();com.cappleapple.astralrepository.platform.ClientBackport.blitSprite(g,new ResourceLocation("astral_repository","rune_drop_area"),x,y,width,height);com.mojang.blaze3d.systems.RenderSystem.disableBlend();}
    public static void icon(GuiGraphics g,RuneFilterSearch.Option option,int x,int y){
        String rule=option.rule();
        if(rule.startsWith("fluid:")&&!rule.startsWith("fluid:#")){
            var fluid=BuiltInRegistries.FLUID.get(new ResourceLocation(rule.substring(6)));var stack=new FluidStack(fluid,1000);var ext=IClientFluidTypeExtensions.of(fluid);var texture=ext.getStillTexture(stack);
            if(texture!=null){var sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);int tint=ext.getTintColor(stack);g.blit(x,y,0,16,16,sprite,(tint>>16&255)/255f,(tint>>8&255)/255f,(tint&255)/255f,(tint>>>24)/255f);return;}
        }
        g.renderItem(option.icon(),x,y);
    }
    private RuneUi(){}
}
