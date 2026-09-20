package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.network.NetworkPackets;
import com.cappleapple.astralrepository.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.DyeColor;
import com.cappleapple.astralrepository.platform.client.event.*;

public final class AstralClient {
    public static void tooltips(RegisterClientTooltipComponentFactoriesEvent event){event.register(RecipeTomeItem.OutputTooltip.class,TomeOutputTooltip::new);}
    public static void screens(RegisterMenuScreensEvent event){
        RunePickupClient.setup();
        com.cappleapple.astralrepository.network.BindingPreviewPackets.receiver=BindingPreviewRenderer::update;
        com.cappleapple.astralrepository.network.RunePackets.artReceiver=RuneDesignRenderer::receive;
        com.cappleapple.astralrepository.network.WandPackets.receiver=WandScreen::receive;
        net.minecraft.client.gui.screens.MenuScreens.register(AstralRepository.NEXUS_MENU.get(),NexusScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(AstralRepository.RECIPE_TOME_MENU.get(),RecipeTomeScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(AstralRepository.RUNE_SETTINGS_MENU.get(),RuneSettingsScreen::new);
        com.cappleapple.astralrepository.network.RecipeTomePackets.pageReceiver=p->{if(Minecraft.getInstance().screen instanceof RecipeTomeScreen screen)screen.update(p);};
        com.cappleapple.astralrepository.network.RunePackets.receiver=RuneRenderer::update;
        com.cappleapple.astralrepository.network.RuneSettingsPackets.receiver=p->{if(Minecraft.getInstance().screen instanceof RuneSettingsScreen screen)screen.update(p);};
        RuneProgramming.clientSelection=hit->{var selected=RuneRenderer.hover(hit);return selected==null?-1:selected.index();};
        NetworkPackets.pageReceiver=p->{if(Minecraft.getInstance().screen instanceof NexusScreen screen)screen.update(p);};
        NetworkPackets.visualReceiver=WorldVisuals::add;NetworkPackets.visualResetReceiver=WorldVisuals::clearStationDisplays;NetworkPackets.diagnosticsReceiver=WorldVisuals::diagnostics;
    }
    public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->RuneDesignRenderer.clearTextures());}
    public static void blockColors(RegisterColorHandlersEvent.Block event){
        for(var block:AstralContent.BLOCKS.getEntries())event.register((state,level,pos,tint)->{
            if(level!=null&&pos!=null&&level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity node)return node.channel()<0?0x65D6CF:com.cappleapple.astralrepository.platform.ClientBackport.dyeColor(DyeColor.byId(node.channel()));return 0x65D6CF;
        },block.get());
    }
    public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(AstralContent.NODE_ENTITY.get(),CrystalRenderer::new);}
    public static void itemColors(RegisterColorHandlersEvent.Item event){
        for(var item:AstralContent.ITEMS.getEntries())event.register(AstralClient::itemColor,item.get());
    }
    private static int itemColor(net.minecraft.world.item.ItemStack stack,int tint){
        int color=0xFFFFFF;
        if(RuneGlyph.isRune(stack))color=RuneGlyph.color(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        else if(stack.is(AstralContent.RECIPE_TOME.get()))color=0xF4EDFF;
        else if(stack.getItem() instanceof net.minecraft.world.item.BlockItem block&&block.getBlock() instanceof CrystalNodeBlock)color=0x65D6CF;
        return 0xFF000000|color;
    }
    private AstralClient(){}
}





