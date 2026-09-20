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
        event.register(AstralRepository.NEXUS_MENU.get(),NexusScreen::new);
        event.register(AstralRepository.RECIPE_TOME_MENU.get(),RecipeTomeScreen::new);
        event.register(AstralRepository.RUNE_SETTINGS_MENU.get(),RuneSettingsScreen::new);
        com.cappleapple.astralrepository.network.RecipeTomePackets.pageReceiver=p->{if(Minecraft.getInstance().gui.screen() instanceof RecipeTomeScreen screen)screen.update(p);};
        com.cappleapple.astralrepository.network.RunePackets.receiver=RuneRenderer::update;
        com.cappleapple.astralrepository.network.RuneSettingsPackets.receiver=p->{if(Minecraft.getInstance().gui.screen() instanceof RuneSettingsScreen screen)screen.update(p);};
        RuneProgramming.clientSelection=hit->{var selected=RuneRenderer.hover(hit);return selected==null?-1:selected.index();};
        NetworkPackets.pageReceiver=p->{if(Minecraft.getInstance().gui.screen() instanceof NexusScreen screen)screen.update(p);};
        NetworkPackets.visualReceiver=WorldVisuals::add;NetworkPackets.visualResetReceiver=WorldVisuals::clearStationDisplays;NetworkPackets.diagnosticsReceiver=WorldVisuals::diagnostics;
    }
    public static void reload(AddClientReloadListenersEvent event){event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("astral_repository","rune_textures"),(net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->RuneDesignRenderer.clearTextures());}
    public static void blockColors(RegisterColorHandlersEvent.BlockTintSources event){
        var tint = new net.minecraft.client.color.block.BlockTintSource() {
            @Override public int color(net.minecraft.world.level.block.state.BlockState state) { return 0xFF65D6CF; }
            @Override public int colorInWorld(net.minecraft.world.level.block.state.BlockState state, net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
                if (level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity node && node.channel() >= 0)
                    return 0xFF000000 | DyeColor.byId(node.channel()).getTextureDiffuseColor();
                return color(state);
            }
        };
        for(var block:AstralContent.BLOCKS.getEntries())event.register(java.util.List.of(tint),block.get());
    }
    public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(AstralContent.NODE_ENTITY.get(),CrystalRenderer::new);}
    public static void itemColors(RegisterColorHandlersEvent.ItemTintSources event){
        event.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("astral_repository","item_color"), ItemTint.CODEC);
    }
    public record ItemTint() implements net.minecraft.client.color.item.ItemTintSource {
        public static final com.mojang.serialization.MapCodec<ItemTint> CODEC = com.mojang.serialization.MapCodec.unit(new ItemTint());
        @Override public int calculate(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.LivingEntity owner) { return itemColor(stack, 0); }
        @Override public com.mojang.serialization.MapCodec<ItemTint> type() { return CODEC; }
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





