package com.cappleapple.astralrepository.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Recessed themed field with the same inset for text rendering and cursor placement. */
public final class AstralEditBox extends EditBox {
    private static final int INSET=5;
    public AstralEditBox(Font font,int x,int y,int width,int height,Component label){
        super(font,x,y,width,height,label);setBordered(false);setTextColor(0xd9defb);
    }
    @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
        com.cappleapple.astralrepository.platform.ClientBackport.blitSprite(g,new ResourceLocation("astral_repository",isFocused()?"rune_field_focused":"rune_field"),getX(),getY(),getWidth(),getHeight());
        int x=getX(),y=getY(),width=getWidth();
        setX(x+INSET);setY(y+(getHeight()-8)/2);setWidth(width-INSET*2);
        try{super.renderWidget(g,mx,my,partial);}finally{setX(x);setY(y);setWidth(width);}
    }
    @Override public void onClick(double x,double y){super.onClick(x-INSET,y);}
}
