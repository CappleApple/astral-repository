package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.RunePackets;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.cappleapple.astralrepository.platform.ModList;
import com.cappleapple.astralrepository.platform.client.event.RenderGuiEvent;


/** The same visible cells select independent runes for editing, hover details, and link previews. */
public final class RuneRenderer {
    public record Hover(RunePackets.Face face,RunePackets.Layer layer,int index,RuneLayout.Cell cell) {}
    private static final Identifier WHITE=Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static List<RunePackets.Face> faces=List.of();
    private static Object level;
    private static long received;
    private static int rendered;
    public static long builtInHoverFrames;
    public static void update(RunePackets.Faces packet){var mc=Minecraft.getInstance();level=mc.level;faces=packet.faces();received=mc.level==null?0:mc.level.getGameTime();}
    private static boolean current(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.level==level&&mc.player!=null&&mc.level.getGameTime()-received<=40;}
    private static boolean visible(RunePackets.Face face){
        var mc=Minecraft.getInstance();return GogglesEquipment.isWearing(mc.player)||RunePackets.editingTool(mc.player.getMainHandItem())||RunePackets.editingTool(mc.player.getOffhandItem())||(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(face.pos())&&hit.getDirection()==face.face());
    }
    public static int visibleFaces(){return current()?(int)faces.stream().filter(RuneRenderer::visible).count():0;}
    public static int renderedGlyphs(){return rendered;}
    public static Hover hover(){return Minecraft.getInstance().hitResult instanceof BlockHitResult hit?hover(hit):null;}
    public static Hover hover(BlockHitResult hit){
        if(!current())return null;var mc=Minecraft.getInstance();
        for(var face:faces)if(face.pos().equals(hit.getBlockPos())&&face.face()==hit.getDirection()){
            int index=RuneLayout.selectedIndex(cells(face),hit.getLocation());
            if(index>=0)return new Hover(face,face.layers().get(index),index,cells(face).get(index));
        }return null;
    }
    private static List<RuneLayout.Cell> cells(RunePackets.Face face){return RuneLayout.placed(Minecraft.getInstance().level,face.pos(),face.face(),face.layers().stream().map(l->new RuneLayout.Position(l.u(),l.v())).toList());}
    public enum Symbol { NONE, ARROW, ALL, MATCH_ALL, PAUSE, PLAY, RESERVE, LIMIT, PRIORITY }
    public record Icon(ItemStack item,Symbol symbol,String amount,boolean excluded,boolean exact,net.minecraft.resources.Identifier texture) {public Icon(ItemStack item,Symbol symbol,String amount,boolean excluded,boolean exact){this(item,symbol,amount,excluded,exact,null);}}
    private static Icon itemIcon(net.minecraft.world.item.Item item){return new Icon(new ItemStack(item),Symbol.NONE,"",false,false);}
    private static Icon symbol(Symbol symbol,long amount){return new Icon(ItemStack.EMPTY,symbol,amount<0?"":Long.toString(amount),false,false);}
    /** Inspection is opt-in while sneaking. Rows contain only pictograms and numeric quantities. */
    public static List<List<Icon>> hoverIcons(BlockHitResult hit){
        var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isShiftKeyDown())return List.of();
        var hover=hover(hit);if(hover==null)return List.of();var layer=hover.layer();
        var design=RuneDesignRenderer.find(layer.design());Icon rune=new Icon(ItemStack.EMPTY,Symbol.NONE,"",false,false,RuneDesignRenderer.texture(design==null?RuneDesign.initial(layer.mode()):design,com.cappleapple.astralrepository.AstralServerConfig.runeResolution.get()));
        Icon target=layer.target()==null?new Icon(new ItemStack(Items.IRON_CHAIN),Symbol.NONE,"",true,false):itemIcon(BuiltInRegistries.ITEM.getValue(layer.targetItem()));
        List<Icon> targets=new ArrayList<>();for(var id:layer.targetIcons())targets.add(itemIcon(BuiltInRegistries.ITEM.getValue(id)));if(targets.isEmpty())targets.add(target);
        if(layer.targetCount()>targets.size())targets.add(new Icon(new ItemStack(Items.IRON_CHAIN),Symbol.NONE,Integer.toString(layer.targetCount()-targets.size()),false,false));
        List<Icon> route=new ArrayList<>();if(layer.mode()==RuneLayer.Mode.FILTER){route.add(rune);}else if(layer.mode()==RuneLayer.Mode.PUSH){route.add(rune);route.add(symbol(Symbol.ARROW,-1));route.addAll(targets);}else{route.addAll(targets);route.add(symbol(Symbol.ARROW,-1));route.add(rune);}
        if(!layer.enabled())route.add(symbol(Symbol.PAUSE,-1));
        else if(layer.status().equals("Transferring"))route.add(symbol(Symbol.PLAY,-1));
        else if(layer.status().contains("reserve"))route.add(symbol(Symbol.RESERVE,-1));
        else if(layer.status().contains("Stock target"))route.add(symbol(Symbol.LIMIT,-1));
        else if(layer.status().contains("full")||layer.status().contains("reject")||layer.status().contains("unloaded")||layer.status().contains("out of range")||layer.status().contains("No loaded relay")||layer.status().contains("no accessible")||layer.status().contains("Same ")||layer.status().contains("failed"))route.add(itemIcon(Items.BARRIER));
        else route.add(itemIcon(Items.CLOCK));
        List<List<Icon>> rows=new ArrayList<>();rows.add(List.copyOf(route));
        List<Icon> filters=new ArrayList<>();if(layer.icons().isEmpty())filters.add(symbol(Symbol.ALL,-1));
        else {if(layer.matchAll())filters.add(symbol(Symbol.MATCH_ALL,-1));for(var icon:layer.icons())filters.add(new Icon(new ItemStack(BuiltInRegistries.ITEM.getValue(icon.item())),Symbol.NONE,"",icon.excluded(),icon.exact()));if(layer.filterCount()>layer.icons().size())filters.add(new Icon(new ItemStack(Items.PAPER),Symbol.NONE,"+"+(layer.filterCount()-layer.icons().size()),false,false));}
        rows.add(List.copyOf(filters));List<Icon> counts=new ArrayList<>();if(layer.mode()!=RuneLayer.Mode.FILTER&&layer.minimum()>0)counts.add(symbol(Symbol.RESERVE,layer.minimum()));if(layer.mode()!=RuneLayer.Mode.FILTER&&layer.targetAmount()!=Long.MAX_VALUE)counts.add(symbol(Symbol.LIMIT,layer.targetAmount()));if(layer.priority()!=0)counts.add(new Icon(ItemStack.EMPTY,Symbol.PRIORITY,Integer.toString(layer.priority()),false,false));if(!counts.isEmpty())rows.add(List.copyOf(counts));return List.copyOf(rows);
    }
    private static int cellWidth(Icon icon){return Math.max(20,Minecraft.getInstance().font.width(icon.amount())+4);}
    public static int iconWidth(List<List<Icon>> rows){return rows.stream().mapToInt(row->row.stream().mapToInt(RuneRenderer::cellWidth).sum()).max().orElse(0);}
    public static int iconHeight(List<List<Icon>> rows){return rows.size()*21;}
    public static void renderIcons(GuiGraphicsExtractor g,List<List<Icon>> rows,int x,int y){
        var font=Minecraft.getInstance().font;
        for(var row:rows){int at=x;for(var icon:row){int cell=cellWidth(icon),ix=at+(cell-16)/2;
            if(icon.texture()!=null)g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,icon.texture(),ix,y,0,0,16,16,16,16);else if(!icon.item().isEmpty())g.item(icon.item(),ix,y);else drawSymbol(g,icon.symbol(),ix,y);
            if(icon.excluded())for(int i=0;i<15;i++)g.fill(ix+i,y+14-i,ix+i+2,y+16-i,0xFFFF6666);
            if(icon.exact()){g.fill(ix+11,y,ix+16,y+2,0xFF8FEAFF);g.fill(ix+11,y+4,ix+16,y+6,0xFF8FEAFF);}
            if(!icon.amount().isEmpty()){g.nextStratum();g.text(font,icon.amount(),at+cell-2-font.width(icon.amount()),y+10,0xFFFFFFFF,true);}
            at+=cell;
        }y+=21;}
    }
    private static void drawSymbol(GuiGraphicsExtractor g,Symbol symbol,int x,int y){
        int light=0xFFE1E8F5;
        switch(symbol){
            case ARROW->{g.fill(x+2,y+7,x+12,y+9,light);for(int i=0;i<5;i++)g.fill(x+9+i,y+3+i,x+11+i,y+13-i,light);}
            case PAUSE->{g.fill(x+3,y+2,x+7,y+14,0xFFFFC46E);g.fill(x+10,y+2,x+14,y+14,0xFFFFC46E);}
            case PLAY->{for(int i=0;i<10;i++)g.fill(x+3+i,y+2+i/2,x+4+i,y+14-i/2,0xFF90E2A5);}
            case ALL->{g.fill(x+7,y+2,x+9,y+14,light);g.fill(x+2,y+7,x+14,y+9,light);for(int i=0;i<10;i++){g.fill(x+3+i,y+3+i,x+4+i,y+4+i,light);g.fill(x+3+i,y+12-i,x+4+i,y+13-i,light);}}
            case MATCH_ALL->{g.outline(x+1,y+1,6,6,0xFF9DE7FF);g.outline(x+9,y+9,6,6,0xFF9DE7FF);g.fill(x+6,y+6,x+10,y+10,0xFF9DE7FF);}
            case RESERVE->{g.outline(x+2,y+5,12,9,0xFF8FEAFF);g.fill(x+4,y+10,x+12,y+13,0xFF8FEAFF);g.fill(x+4,y+2,x+12,y+4,light);}
            case LIMIT->{g.outline(x+2,y+5,12,9,0xFFFFD590);g.fill(x+4,y+6,x+12,y+9,0xFFFFD590);g.fill(x+1,y+2,x+15,y+4,light);}
            case PRIORITY->{g.fill(x+7,y+4,x+9,y+14,0xFFD6B0FF);for(int i=0;i<5;i++)g.fill(x+3+i,y+6-i,x+13-i,y+8-i,0xFFD6B0FF);}
            default->{}
        }
    }
    public static void disconnect(com.cappleapple.astralrepository.platform.client.event.ClientPlayerNetworkEvent.LoggingOut event){faces=List.of();RuneDesignRenderer.disconnect();}
    public static void render(AstralWorldFrame event){
        
        rendered=0;if(!current())faces=List.of();var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;var pose=event.getPoseStack();var buffers=event.buffers();
        var vertex=buffers.getBuffer(RenderTypes.entityTranslucent(WHITE));Vec3 camera=event.getCamera().position();var hovered=hover();var selected=RuneProgramming.selection(mc.player.getMainHandItem());
        for(var face:faces){if(!visible(face)||Vec3.atCenterOf(face.pos()).distanceToSqr(camera)>4096)continue;var cells=cells(face);
            for(int i=0;i<cells.size();i++){var cell=cells.get(i);var layer=face.layers().get(i);var origin=cell.center().subtract(camera);double glyph=cell.size()*0.78;
                int color=face.channel()<0?RuneGlyph.color(layer.item().getPath()):DyeColor.byId(face.channel()).getTextureDiffuseColor();if(!layer.enabled())color=0x777777;
                var design=RuneDesignRenderer.find(layer.design());
                if(design!=null){buffers.endBatch(RenderTypes.entityTranslucent(WHITE));var texture=RuneDesignRenderer.texture(design,com.cappleapple.astralrepository.AstralServerConfig.runeResolution.get());var material=RenderTypes.entityTranslucentEmissive(texture);var art=buffers.getBuffer(material);rectangle(art,pose,cell,origin,-cell.size()/2,cell.size()/2,cell.size(),cell.size(),layer.enabled()?0xffffff:0x777777);buffers.endBatch(material);vertex=buffers.getBuffer(RenderTypes.entityTranslucent(WHITE));}
                if(hovered!=null&&hovered.layer().id().equals(layer.id())||selected!=null&&layer.id().equals(selected.layer())){double half=cell.size()*0.46,stroke=Math.max(0.004,cell.size()*0.028);Vec3 front=origin.add(cell.normal().scale(0.001));rectangle(vertex,pose,cell,front,-half,half,half*2,stroke,0xFFFFFF);rectangle(vertex,pose,cell,front,-half,-half+stroke,half*2,stroke,0xFFFFFF);rectangle(vertex,pose,cell,front,-half,half,stroke,half*2,0xFFFFFF);rectangle(vertex,pose,cell,front,half-stroke,half,stroke,half*2,0xFFFFFF);}
                rendered++;
            }
        }
        buffers.endBatch(RenderTypes.entityTranslucent(WHITE));
    }
    private static void rectangle(VertexConsumer vertex,PoseStack pose,RuneLayout.Cell cell,Vec3 origin,double x,double y,double width,double height,int color){
        Vec3 a=origin.add(cell.right().scale(x)).add(cell.up().scale(y)),b=a.add(cell.right().scale(width)),c=b.add(cell.up().scale(-height)),d=a.add(cell.up().scale(-height));
        Vec3[] points={d,c,b,a};float[][] uv={{0,1},{1,1},{1,0},{0,0}};
        for(int i=0;i<4;i++)vertex.addVertex(pose.last().pose(),(float)points[i].x,(float)points[i].y,(float)points[i].z).setColor(color>>16&255,color>>8&255,color&255,240).setUv(uv[i][0],uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose.last(),(float)cell.normal().x,(float)cell.normal().y,(float)cell.normal().z);
    }
    private static void line(VertexConsumer vertex,PoseStack pose,Vec3 a,Vec3 b,int color){if(a.distanceToSqr(b)<0.000001)return;Vec3 n=b.subtract(a).normalize();for(Vec3 p:List.of(a,b))vertex.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).setColor(color>>16&255,color>>8&255,color&255,220).setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);}
    private static void arrow(VertexConsumer vertex,PoseStack pose,Vec3 a,Vec3 b,int color,long time){line(vertex,pose,a,b,color);Vec3 d=b.subtract(a).normalize(),side=d.cross(new Vec3(0,1,0));if(side.lengthSqr()<0.01)side=d.cross(new Vec3(1,0,0));side=side.normalize().scale(0.10);Vec3 tip=a.lerp(b,0.2+(time%50)/50.0*0.65),back=tip.subtract(d.scale(0.18));line(vertex,pose,back.add(side),tip,color);line(vertex,pose,back.subtract(side),tip,color);}
    public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.gui.screen()!=null||!current())return;
        var selection=RuneProgramming.selection(mc.player.getMainHandItem());
        if(ModList.get().isLoaded("jade")||!(mc.hitResult instanceof BlockHitResult hit))return;
        var rows=hoverIcons(hit);if(rows.isEmpty())return;builtInHoverFrames++;
        int width=iconWidth(rows),height=iconHeight(rows),x=Math.min(mc.getWindow().getGuiScaledWidth()-width-10,mc.getWindow().getGuiScaledWidth()/2+18),y=Math.max(8,mc.getWindow().getGuiScaledHeight()/2-height/2);
        event.getGuiGraphics().fill(x-5,y-5,x+width+5,y+height+3,0xEB182030);renderIcons(event.getGuiGraphics(),rows,x,y);
    }    private RuneRenderer(){}
}





