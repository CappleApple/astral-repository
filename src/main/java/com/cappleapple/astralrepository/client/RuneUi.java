package com.cappleapple.astralrepository.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Shared textured slots; tint is drawn before item/fluid contents. */
public final class RuneUi {
    private static final Identifier SLOT=Identifier.fromNamespaceAndPath("astral_repository","rune_button");
    public static void slot(GuiGraphicsExtractor g,int x,int y,int size,boolean hovered){g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,SLOT,x,y,size,size);if(hovered)g.fill(x+2,y+2,x+size-2,y+size-2,0x447e9eff);}
    public static void dropArea(GuiGraphicsExtractor g,int x,int y,int width,int height){g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("astral_repository","rune_drop_area"),x,y,width,height);}
    public static void icon(GuiGraphicsExtractor g,RuneFilterSearch.Option option,int x,int y){
        String rule=option.rule();
        if(rule.startsWith("fluid:")&&!rule.startsWith("fluid:#")){
            var fluid=BuiltInRegistries.FLUID.getValue(Identifier.parse(rule.substring(6)));
            var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            var sprite=model.stillMaterial().sprite(); var tintSource=model.fluidTintSource(); int tint=tintSource==null?-1:tintSource.color(fluid.defaultFluidState());
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,sprite,x,y,16,16,tint);return;
        }
        g.item(option.icon(),x,y);
    }
    private RuneUi(){}
}
