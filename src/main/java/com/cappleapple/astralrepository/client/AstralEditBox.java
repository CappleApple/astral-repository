package com.cappleapple.astralrepository.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Recessed themed field with the same inset for text rendering and cursor placement. */
public final class AstralEditBox extends EditBox {
    private static final int INSET=5;
    public AstralEditBox(Font font,int x,int y,int width,int height,Component label){
        super(font,x,y,width,height,label);setBordered(false);setTextColor(GuiText.opaque(0xd9defb));
    }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("astral_repository",isFocused()?"rune_field_focused":"rune_field"),getX(),getY(),getWidth(),getHeight());
        int x=getX(),y=getY(),width=getWidth();
        setX(x+INSET);setY(y+(getHeight()-8)/2);setWidth(width-INSET*2);
        try{super.extractWidgetRenderState(g,mx,my,partial);}finally{setX(x);setY(y);setWidth(width);}
    }
    @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){super.onClick(new net.minecraft.client.input.MouseButtonEvent(event.x()-INSET,event.y(),event.buttonInfo()),doubleClick);}
}
