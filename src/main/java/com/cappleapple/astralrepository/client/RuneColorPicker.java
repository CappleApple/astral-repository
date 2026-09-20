package com.cappleapple.astralrepository.client;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;

/** HSV wheel and brightness slider share an exact six-digit RGB field. */
public final class RuneColorPicker extends Screen {
    private final IntConsumer changed;private final Runnable close;
    private float hue,saturation,value;private int color,left,top,drag;
    private EditBox hex;private boolean updating;private ResourceLocation wheel;
    public RuneColorPicker(int color,IntConsumer changed,Runnable close){super(Component.literal("Color"));this.changed=changed;this.close=close;setRgb(color);}
    private void setRgb(int rgb){color=0xff000000|rgb&0xffffff;float[] hsv=java.awt.Color.RGBtoHSB(rgb>>16&255,rgb>>8&255,rgb&255,null);hue=hsv[0];saturation=hsv[1];value=hsv[2];}
    @Override protected void init(){
        left=(width-210)/2;top=(height-224)/2;
        if(wheel==null){var image=new NativeImage(128,128,true);for(int y=0;y<128;y++)for(int x=0;x<128;x++){
            double dx=(x-63.5)/63.5,dy=(y-63.5)/63.5,r=Math.hypot(dx,dy);int c=r>1?0:java.awt.Color.HSBtoRGB((float)((Math.atan2(dy,dx)/(Math.PI*2)+1)%1),(float)r,1);
            image.setPixelRGBA(x,y,(c&0xff00ff00)|((c&255)<<16)|(c>>16&255));}
            wheel=minecraft.getTextureManager().register("astral_color_wheel",new DynamicTexture(image));
        }
        hex=addRenderableWidget(new AstralEditBox(font,left+15,top+182,123,20,Component.literal("Hex color")));
        hex.setBordered(false);hex.setMaxLength(7);hex.setValue(String.format("#%06X",color&0xffffff));hex.setResponder(text->{if(!updating&&text.matches("#?[0-9a-fA-F]{6}")){setRgb(Integer.parseInt(text.replace("#",""),16));changed.accept(color);}});
        addRenderableWidget(new Button(left+145,top+182,50,20,Component.literal("Done"),b->onClose(),supplier->supplier.get()){
            @Override protected void renderWidget(GuiGraphics g,int x,int y,float partial){com.cappleapple.astralrepository.platform.ClientBackport.blitSprite(g,new ResourceLocation("astral_repository",isHovered()?"rune_button_hovered":"rune_button"),getX(),getY(),getWidth(),getHeight());g.drawCenteredString(font,getMessage(),getX()+25,getY()+6,0xD9DEFB);}
        });
    }
    public EditBox hexField(){return hex;}
    public int color(){return color;}
    private void choose(double x,double y){
        if(drag==1){double dx=x-left-82,dy=y-top-100;hue=(float)((Math.atan2(dy,dx)/(Math.PI*2)+1)%1);saturation=(float)com.cappleapple.astralrepository.platform.Backport.clamp(Math.hypot(dx,dy)/64,0,1);}
        else value=(float)com.cappleapple.astralrepository.platform.Backport.clamp(1-(y-top-36)/128,0,1);
        color=java.awt.Color.HSBtoRGB(hue,saturation,value);updating=true;hex.setValue(String.format("#%06X",color&0xffffff));updating=false;changed.accept(color);
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0){
        if(Math.hypot(x-left-82,y-top-100)<=64){drag=1;choose(x,y);return true;}
        if(x>=left+168&&x<left+188&&y>=top+36&&y<top+164){drag=2;choose(x,y);return true;}
    }return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(drag!=0){choose(x,y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(drag!=0){drag=0;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(wheel!=null){minecraft.getTextureManager().release(wheel);wheel=null;}close.run();}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        AstralInterfaceRenderer.blit(g,new ResourceLocation("astral_repository","textures/gui/rune_settings.png"),left,top,210,224);
        g.drawString(font,title,left+12,top+12,0xD9DEFB,false);g.fill(left+153,top+10,left+194,top+25,color);
        g.setColor(value,value,value,1);g.blit(wheel,left+18,top+36,0,0,128,128,128,128);g.setColor(1,1,1,1);
        for(int y=0;y<128;y++)g.fill(left+168,top+36+y,left+188,top+37+y,java.awt.Color.HSBtoRGB(hue,saturation,1-y/127f));
        int x=left+82+(int)(Math.cos(hue*Math.PI*2)*saturation*63),y=top+100+(int)(Math.sin(hue*Math.PI*2)*saturation*63);
        g.renderOutline(x-3,y-3,7,7,0xff000000);g.renderOutline(x-2,y-2,5,5,0xffffffff);g.renderOutline(left+166,top+35+(int)((1-value)*127),24,3,0xffffffff);
        for(var child:children())if(child instanceof AbstractWidget widget)widget.render(g,mx,my,partial);
    }
}
