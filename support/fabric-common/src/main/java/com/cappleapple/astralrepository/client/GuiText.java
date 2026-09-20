package com.cappleapple.astralrepository.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Existing theme colors are RGB; extraction requires an explicit alpha channel. */
final class GuiText {
    private GuiText() {}
    static int opaque(int color) { return (color & 0xff000000) == 0 ? color | 0xff000000 : color; }
    static void text(GuiGraphicsExtractor g,Font f,String s,int x,int y,int c) { g.text(f,s,x,y,opaque(c)); }
    static void text(GuiGraphicsExtractor g,Font f,String s,int x,int y,int c,boolean shadow) { g.text(f,s,x,y,opaque(c),shadow); }
    static void text(GuiGraphicsExtractor g,Font f,Component s,int x,int y,int c) { g.text(f,s,x,y,opaque(c)); }
    static void text(GuiGraphicsExtractor g,Font f,Component s,int x,int y,int c,boolean shadow) { g.text(f,s,x,y,opaque(c),shadow); }
    static void text(GuiGraphicsExtractor g,Font f,FormattedCharSequence s,int x,int y,int c) { g.text(f,s,x,y,opaque(c)); }
    static void text(GuiGraphicsExtractor g,Font f,FormattedCharSequence s,int x,int y,int c,boolean shadow) { g.text(f,s,x,y,opaque(c),shadow); }
    static void centeredText(GuiGraphicsExtractor g,Font f,String s,int x,int y,int c) { g.centeredText(f,s,x,y,opaque(c)); }
    static void centeredText(GuiGraphicsExtractor g,Font f,Component s,int x,int y,int c) { g.centeredText(f,s,x,y,opaque(c)); }
}
