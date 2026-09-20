package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.NetworkPackets;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Live shortage grid; tag labels describe choices instead of demanding one arbitrary tag member. */
public final class CraftMissingScreen extends Screen {
    private final NexusScreen parent;private final NexusMenu menu;private final UUID id;private int row;
    public CraftMissingScreen(NexusScreen parent,NexusMenu menu,UUID id){super(Component.literal("Missing ingredients"));this.parent=parent;this.menu=menu;this.id=id;}
    private List<NetworkPackets.Missing> entries(){return menu.jobs.stream().filter(j->j.id().equals(id)).findFirst().map(NetworkPackets.Job::missing).orElse(List.of());}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){parent.closeMissingDetails();}
    @Override public boolean mouseScrolled(double x,double y,double dy){if(dy==0)return false;row=com.cappleapple.astralrepository.platform.Backport.clamp(row+(dy<0?1:-1),0,Math.max(0,(entries().size()+7)/8-5));return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        int left=(width-280)/2,top=(height-212)/2;var entries=entries();row=Math.min(row,Math.max(0,(entries.size()+7)/8-5));
        AstralInterfaceRenderer.blit(g,new ResourceLocation("astral_repository","textures/gui/rune_settings.png"),left,top,280,212);
        g.drawString(font,title,left+12,top+10,0xD9DEFB,false);
        if(entries.isEmpty()){
            boolean calculating=menu.jobs.stream().anyMatch(j->j.id().equals(id)&&j.state()==NetworkPackets.JobState.CALCULATING);
            if(!calculating){parent.closeMissingDetails();return;}g.drawString(font,"Calculating",left+12,top+36,0xB9B7ED,false);
        }
        for(int i=0;i<40&&row*8+i<entries.size();i++){
            var missing=entries.get(row*8+i);int x=left+12+i%8*32,y=top+30+i/8*32;boolean hover=mx>=x&&mx<x+28&&my>=y&&my<y+28;
            RuneUi.slot(g,x,y,28,hover);g.renderItem(missing.icon(),x+6,y+4);g.renderItemDecorations(font,missing.icon(),x+6,y+4,Long.toString(missing.count()));
            if(!missing.tag().isEmpty())g.drawString(font,"#",x+2,y+2,0xBDB6F2,false);
            if(hover)g.renderComponentTooltip(font,List.of(Component.literal(missing.tag().isEmpty()?missing.icon().getHoverName().getString():"#"+missing.tag()),Component.literal(Long.toString(missing.count()))),mx,my);
        }
        g.drawString(font,"Esc",left+245,top+194,0xBDB6F2,false);
    }
}
