package com.cappleapple.astralrepository.client;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;

/** HSV wheel and brightness slider share an exact six-digit RGB field. */
public final class RuneColorPicker extends Screen {
    private final IntConsumer changed;private final Runnable close;
    private float hue,saturation,value;private int color,left,top,drag;
    private EditBox hex;private boolean updating;private Identifier wheel;
    public RuneColorPicker(int color,IntConsumer changed,Runnable close){super(Component.literal("Color"));this.changed=changed;this.close=close;setRgb(color);}
    private void setRgb(int rgb){color=0xff000000|rgb&0xffffff;float[] hsv=java.awt.Color.RGBtoHSB(rgb>>16&255,rgb>>8&255,rgb&255,null);hue=hsv[0];saturation=hsv[1];value=hsv[2];}
    @Override protected void init(){
        left=(width-210)/2;top=(height-224)/2;
        if(wheel==null){var image=new NativeImage(128,128,true);for(int y=0;y<128;y++)for(int x=0;x<128;x++){
            double dx=(x-63.5)/63.5,dy=(y-63.5)/63.5,r=Math.hypot(dx,dy);int c=r>1?0:java.awt.Color.HSBtoRGB((float)((Math.atan2(dy,dx)/(Math.PI*2)+1)%1),(float)r,1);
            image.setPixelABGR(x,y,(c&0xff00ff00)|((c&255)<<16)|(c>>16&255));}
            wheel=Identifier.fromNamespaceAndPath("astral_repository","dynamic/color_wheel");minecraft.getTextureManager().register(wheel,new DynamicTexture(()->"Astral color wheel",image));
        }
        hex=addRenderableWidget(new AstralEditBox(font,left+15,top+182,123,20,Component.literal("Hex color")));
        hex.setBordered(false);hex.setMaxLength(7);hex.setValue(String.format("#%06X",color&0xffffff));hex.setResponder(text->{if(!updating&&text.matches("#?[0-9a-fA-F]{6}")){setRgb(Integer.parseInt(text.replace("#",""),16));changed.accept(color);}});
        addRenderableWidget(new Button(left+145,top+182,50,20,Component.literal("Done"),b->onClose(),supplier->supplier.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int x,int y,float partial){g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("astral_repository",isHovered()?"rune_button_hovered":"rune_button"),getX(),getY(),getWidth(),getHeight());GuiText.centeredText(g,font,getMessage(),getX()+25,getY()+6,0xD9DEFB);}
        });
    }
    public EditBox hexField(){return hex;}
    public int color(){return color;}
    private void choose(double x,double y){
        if(drag==1){double dx=x-left-82,dy=y-top-100;hue=(float)((Math.atan2(dy,dx)/(Math.PI*2)+1)%1);saturation=(float)Math.clamp(Math.hypot(dx,dy)/64,0,1);}
        else value=(float)Math.clamp(1-(y-top-36)/128,0,1);
        color=java.awt.Color.HSBtoRGB(hue,saturation,value);updating=true;hex.setValue(String.format("#%06X",color&0xffffff));updating=false;changed.accept(color);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick){double x=event.x(), y=event.y(); int button=event.button();if(button==0){
        if(Math.hypot(x-left-82,y-top-100)<=64){drag=1;choose(x,y);return true;}
        if(x>=left+168&&x<left+188&&y>=top+36&&y<top+164){drag=2;choose(x,y);return true;}
    }return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,AstralInput.modifiers())), false);}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy){double x=event.x(), y=event.y(); int button=event.button();if(drag!=0){choose(x,y);return true;}return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,AstralInput.modifiers())), dx, dy);}
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){double x=event.x(), y=event.y(); int button=event.button();if(drag!=0){drag=0;return true;}return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,AstralInput.modifiers())));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(wheel!=null){minecraft.getTextureManager().release(wheel);wheel=null;}close.run();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        AstralInterfaceRenderer.blit(g,Identifier.fromNamespaceAndPath("astral_repository","textures/gui/rune_settings.png"),left,top,210,224);
        GuiText.text(g,font,title,left+12,top+12,0xD9DEFB,false);g.fill(left+153,top+10,left+194,top+25,color);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,wheel,left+18,top+36,0,0,128,128,128,128,net.minecraft.util.ARGB.color(255,(int)(value*255),(int)(value*255),(int)(value*255)));
        for(int y=0;y<128;y++)g.fill(left+168,top+36+y,left+188,top+37+y,java.awt.Color.HSBtoRGB(hue,saturation,1-y/127f));
        int x=left+82+(int)(Math.cos(hue*Math.PI*2)*saturation*63),y=top+100+(int)(Math.sin(hue*Math.PI*2)*saturation*63);
        g.outline(x-3,y-3,7,7,0xff000000);g.outline(x-2,y-2,5,5,0xffffffff);g.outline(left+166,top+35+(int)((1-value)*127),24,3,0xffffffff);
        for(var child:children())if(child instanceof AbstractWidget widget)widget.extractRenderState(g,mx,my,partial);
    }
}
