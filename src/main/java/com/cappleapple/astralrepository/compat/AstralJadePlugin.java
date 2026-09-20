package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.client.RuneRenderer;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
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
        @Override public ResourceLocation getUid(){return ResourceLocation.fromNamespaceAndPath("astral_repository","rune_assignment");}
        @Override public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,IPluginConfig config){
            var rows=RuneRenderer.hoverIcons(accessor.getHitResult());
            if(rows.isEmpty())return;
            tooltip.add(new RuneIcons(rows));shown++;
        }
    }
    private static final class RuneIcons extends Element {
        private final List<List<RuneRenderer.Icon>> rows;
        RuneIcons(List<List<RuneRenderer.Icon>> rows){this.rows=rows.stream().map(List::copyOf).toList();}
        @Override public Vec2 getSize(){return new Vec2(RuneRenderer.iconWidth(rows),RuneRenderer.iconHeight(rows));}
        @Override public void render(GuiGraphics graphics,float x,float y,float width,float height){
            RuneRenderer.renderIcons(graphics,rows,Math.round(x),Math.round(y));
        }
    }
}
