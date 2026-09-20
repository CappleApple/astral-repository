package com.cappleapple.astralrepository.platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
public final class ClientBackport {
 public static ResourceLocation standalone(ResourceLocation id){return id;}
 public static void blitSprite(GuiGraphics graphics,ResourceLocation sprite,int x,int y,int width,int height){
  // UI textures use repeating nine-slice borders on 1.20.1, before the GUI sprite atlas.
  ResourceLocation texture=new ResourceLocation(sprite.getNamespace(),"textures/gui/sprites/"+sprite.getPath()+".png");
  int left=Math.min(2,width/2),right=Math.min(2,width/2),top=Math.min(2,height/2),bottom=Math.min(2,height/2);
  int[] xs={0,left,width-right},ys={0,top,height-bottom},ws={left,width-left-right,right},hs={top,height-top-bottom,bottom};
  int[] us={0,2,16-right},vs={0,2,16-bottom},uw={left,12,right},vh={top,12,bottom};
  for(int row=0;row<3;row++)for(int col=0;col<3;col++)if(uw[col]>0&&vh[row]>0)
   for(int dy=0;dy<hs[row];dy+=vh[row])for(int dx=0;dx<ws[col];dx+=uw[col])
    graphics.blit(texture,x+xs[col]+dx,y+ys[row]+dy,(float)us[col],(float)vs[row],Math.min(uw[col],ws[col]-dx),Math.min(vh[row],hs[row]-dy),16,16);
 }
 public static int dyeColor(net.minecraft.world.item.DyeColor dye){float[] color=dye.getTextureDiffuseColors();return ((int)(color[0]*255)<<16)|((int)(color[1]*255)<<8)|(int)(color[2]*255);}
}
