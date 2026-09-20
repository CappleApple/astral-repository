package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.client.RuneRenderer;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

/** Loaded by Jade only; shares the Shift-gated icon presentation with the built-in overlay. */
@WailaPlugin("astral_repository")
public final class AstralJadePlugin implements IWailaPlugin {
    public static boolean registered;
    public static long shown;
    @Override public void registerClient(IWailaClientRegistration registration){registration.registerBlockComponent(RuneInfo.INSTANCE,Block.class);registered=true;}
    public enum RuneInfo implements IBlockComponentProvider {
        INSTANCE;
        @Override public Identifier getUid(){return Identifier.fromNamespaceAndPath("astral_repository","rune_assignment");}
        @Override public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,IPluginConfig config){
            var rows=RuneRenderer.hoverIcons(accessor.getHitResult());
            if(rows.isEmpty())return;
            tooltip.add(new RuneIcons(rows));shown++;
        }
    }
    private static final class RuneIcons extends Element {
        private final List<List<RuneRenderer.Icon>> rows;
        RuneIcons(List<List<RuneRenderer.Icon>> rows){this.rows=rows.stream().map(List::copyOf).toList();width=RuneRenderer.iconWidth(rows);height=RuneRenderer.iconHeight(rows);}
        @Override public net.minecraft.network.chat.Component getNarration(){return net.minecraft.network.chat.Component.literal("Assigned runes");}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick){
            RuneRenderer.renderIcons(graphics,rows,getX(),getY());
        }
    }
}
