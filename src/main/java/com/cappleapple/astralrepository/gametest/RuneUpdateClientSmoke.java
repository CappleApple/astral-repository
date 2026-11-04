package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.AnchorAddress;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** Focused development-client evidence for native glint dispatch and default Blocking Rune artwork. */
public final class RuneUpdateClientSmoke {
    private static final Path OUT=Path.of("../build/client-smoke/rune-update");
    private static Preview preview;
    private static int ticks;
    private static boolean verified;
    private static ItemStack linked(int color){
        var mc=Minecraft.getInstance();var stack=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());
        CompoundTag data=new CompoundTag();var endpoint=new AnchorAddress(GlobalPos.of(mc.level.dimension(),new BlockPos(3,-59,0)),Direction.SOUTH).save();
        endpoint.putUUID("Layer",UUID.randomUUID());data.put("RuneLinkStart",endpoint);data.putInt("RuneLinkColor",color);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return stack;
    }
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();
        if(verified)return TomeUpdateClientSmoke.tick();
        if(preview==null){preview=new Preview();mc.setScreen(preview);ticks=0;return false;}
        if(++ticks<20||preview.frames<3)return false;
        Files.createDirectories(OUT);
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve("blocking-and-linking-glint.png"));}
        check(preview.redRequests>0&&preview.blueRequests>0,"Actual ItemRenderer failed to request both tinted native glint buffers");
        int redPixels=checkTexture(0xff0000),bluePixels=checkTexture(0x0000ff);
        Files.writeString(OUT.resolve("result.txt"),"PASS: actual registered ItemRenderer rendered inactive, red-linking and blue-linking wands; custom native GLINT buffers requested red="+preview.redRequests+", blue="+preview.blueRequests+"; native texture pixels preserve exact pure RGB channels red="+redPixels+", blue="+bluePixels+"; default Blocking Rune red X rendered through RuneDesignRenderer. Screenshot is visual evidence; no whole-modpack rendering claim.\n");
        verified=true;return false;
    }
    private static int checkTexture(int rgb)throws Exception{
        var field=RuneLinkGlint.class.getDeclaredField("materials");field.setAccessible(true);Object material=((Map<?,?>)field.get(null)).get(rgb);check(material!=null,"Missing generated colored glint material");
        var method=material.getClass().getDeclaredMethod("texture");method.setAccessible(true);var texture=(DynamicTexture)Minecraft.getInstance().getTextureManager().getTexture((ResourceLocation)method.invoke(material));
        var pixels=texture.getPixels();check(pixels!=null,"Native colored glint texture was disposed");int painted=0;
        for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){
            int pixel=pixels.getPixelRGBA(x,y);int red=pixel&255,green=pixel>>8&255,blue=pixel>>16&255;
            check(green==0&&(rgb==0xff0000?blue==0:red==0),"Generated glint texture retained vanilla purple channels");if((red|blue)>0)painted++;
        }
        check(painted>0,"Generated glint texture has no painted intensity");return painted;
    }
    private static final class Preview extends Screen {
        private final ItemStack red=linked(0xff0000),blue=linked(0x0000ff);
        int frames,redRequests,blueRequests;
        Preview(){super(Component.literal("Blocking Rune and linking glint"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            frames++;g.fill(0,0,width,height,0xff10141e);g.drawCenteredString(font,"Blocking Rune and linking wand colors",width/2,16,0xe7ebf6);
            String[] labels={"Wand", "Red linking rune", "Blue linking rune", "Blocking Rune"};
            float size=Math.min(width/4F-24,height*.6F);int centerY=height/2;
            for(int i=0;i<4;i++)g.drawCenteredString(font,labels[i],Math.round(width*(i+.5F)/4),height-36,0xcad4e7);
            g.flush();draw(g,new ItemStack(AstralContent.ATTUNEMENT_WAND.get()),width/8F,centerY,size,0);draw(g,red,width*3/8F,centerY,size,0xff0000);draw(g,blue,width*5/8F,centerY,size,0x0000ff);
            var texture=RuneDesignRenderer.texture(RunePreset.blocking().design(),32);int side=Math.round(size*.72F),cx=Math.round(width*7/8F);
            g.blit(texture,cx-side/2,centerY-side/2,side,side,0,0,32,32,32,32);g.flush();
        }
        private void draw(GuiGraphics g,ItemStack stack,float x,float y,float size,int color){
            var mc=Minecraft.getInstance();var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);var pose=g.pose();pose.pushPose();
            MultiBufferSource buffers=type->{if(color!=0&&type==RuneLinkGlint.type(color)){if(color==0xff0000)redRequests++;else blueRequests++;}return g.bufferSource().getBuffer(type);};
            try{pose.translate(x,y,180);pose.scale(size,-size,size);Lighting.setupForFlatItems();RenderSystem.enableDepthTest();RenderSystem.enableCull();mc.getItemRenderer().render(stack,ItemDisplayContext.GUI,false,pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,model);g.bufferSource().endBatch();}
            finally{pose.popPose();Lighting.setupFor3DItems();}
        }
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private RuneUpdateClientSmoke(){}
}
