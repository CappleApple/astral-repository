package com.cappleapple.astralrepository.content;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;

/** Exact RGB pixels and independent item transforms. No item stacks or flattened item artwork are stored. */
public final class RuneDesign {
    public static final int MAX_SIZE=128, MAX_ICONS=8;
    public record Icon(Identifier item,float x,float y,float scale,float rotation) {
        public Icon {
            Objects.requireNonNull(item);
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(scale)||!Float.isFinite(rotation)) throw new IllegalArgumentException("Nonfinite icon transform");
            x=Math.clamp(x,0,MAX_SIZE);y=Math.clamp(y,0,MAX_SIZE);scale=Math.clamp(scale,1,MAX_SIZE);rotation=Math.clamp(rotation,-360,360);
        }
    }
    private String key;
    private int averagedResolution=-1,averagedColor;
    private final int size;
    private final int[] pixels;
    private final List<Icon> icons;
    public RuneDesign(int size,byte[] pixels,List<Icon> icons){this(size,decodeLegacy(pixels),icons);}
    public RuneDesign(int size,int[] pixels,List<Icon> icons){
        if(size<1||size>MAX_SIZE||pixels.length!=size*size||icons.size()>MAX_ICONS)throw new IllegalArgumentException("Invalid rune canvas");
        this.size=size;this.pixels=pixels.clone();for(int i=0;i<this.pixels.length;i++)this.pixels[i]=(this.pixels[i]>>>24)==0?0:0xff000000|this.pixels[i]&0xffffff;this.icons=List.copyOf(icons);
    }
    private static int[] decodeLegacy(byte[] pixels){int[] result=new int[pixels.length];for(int i=0;i<pixels.length;i++)result[i]=color(Byte.toUnsignedInt(pixels[i]));return result;}
    public int[] argbPixels(){return pixels.clone();}
    public int argb(int x,int y){return x<0||y<0||x>=size||y>=size?0:pixels[y*size+x];}
    public String key(){if(key==null)key=UUID.nameUUIDFromBytes(save().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();return key;}
    /** Average visible painted pixels, excluding transparency and artwork cropped by the server limit. */
    public int averageColor(int resolution){
        int n=Math.min(size,Math.max(1,resolution));if(averagedResolution==n)return averagedColor;
        long red=0,green=0,blue=0,count=0;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int pixel=argb(x,y);if((pixel>>>24)==0)continue;red+=pixel>>16&255;green+=pixel>>8&255;blue+=pixel&255;count++;}
        averagedResolution=n;return averagedColor=count==0?0xA1AFFF:(int)(red/count)<<16|(int)(green/count)<<8|(int)(blue/count);
    }
    public int size(){return size;} public byte[] pixels(){byte[] result=new byte[pixels.length];for(int i=0;i<pixels.length;i++)result[i]=(byte)legacyIndex(pixels[i]);return result;} public List<Icon> icons(){return icons;}
    public int pixel(int x,int y){return legacyIndex(argb(x,y));}
    private static int legacyIndex(int argb){return (argb>>>24)==0?0:Math.max(1,(Math.round((argb>>16&255)*7f/255)<<5)|(Math.round((argb>>8&255)*7f/255)<<2)|Math.round((argb&255)*3f/255));}
    // Legacy RGB332 decoding keeps old instance presets and placed artwork readable.
    public static int color(int index){return index==0?0:0xff000000|((index>>5&7)*255/7)<<16|((index>>2&7)*255/7)<<8|(index&3)*255/3;}
    public static int index(int rgb){return Math.max(1,((rgb>>16&255)*7/255)<<5|((rgb>>8&255)*7/255)<<2|(rgb&255)*3/255);}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putInt("Size",size);t.putIntArray("PixelsRGB",pixels);ListTag list=new ListTag();for(Icon i:icons){CompoundTag v=new CompoundTag();v.putString("Item",i.item.toString());v.putFloat("X",i.x);v.putFloat("Y",i.y);v.putFloat("Scale",i.scale);v.putFloat("Rotation",i.rotation);list.add(v);}t.put("Icons",list);return t;}
    public static RuneDesign load(CompoundTag t){int n=t.getIntOr("Size",0);List<Icon> icons=new ArrayList<>();ListTag list=t.getListOrEmpty("Icons");if(list.size()>MAX_ICONS)throw new IllegalArgumentException("Too many icons");for(int k=0;k<list.size();k++){var v=list.getCompoundOrEmpty(k);icons.add(new Icon(Identifier.parse(v.getStringOr("Item","")),v.getFloatOr("X",0F),v.getFloatOr("Y",0F),v.getFloatOr("Scale",0F),v.getFloatOr("Rotation",0F)));}return t.contains("PixelsRGB")?new RuneDesign(n,t.getIntArray("PixelsRGB").orElseGet(()->new int[0]),icons):new RuneDesign(n,t.getByteArray("Pixels").orElseGet(()->new byte[0]),icons);}
    public static RuneDesign initial(RuneLayer.Mode mode){
        int n=32;byte[] pixels=new byte[n*n];String[] glyph=RuneGlyph.pixels(RuneGlyph.id(mode).getPath());byte color=(byte)index(switch(mode){case PUSH->0xffcc88;case PULL->0x88ddff;case FILTER->0xcc88ff;});
        for(int y=0;y<7;y++)for(int x=0;x<7;x++)if(glyph[y].charAt(x)=='#')for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++)pixels[(y*3+dy+5)*n+x*3+dx+5]=color;
        return new RuneDesign(n,pixels,List.of());
    }
}
