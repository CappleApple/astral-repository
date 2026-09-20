package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.client.AstralMineralClient;
import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.client.CrystalModelRenderer;
import com.cappleapple.astralrepository.content.AstralContent;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.ItemStack;

/** Opt-in background GPU smoke gate; omitted from the distributable JAR. */
public final class FabricClientSmoke implements net.fabricmc.api.ClientModInitializer {
    public void onInitializeClient(){net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{try{tick();}catch(Throwable failure){try{java.nio.file.Files.writeString(java.nio.file.Path.of("client-smoke-result.txt"),"FAIL: "+failure);}catch(Exception ignored){}throw new RuntimeException(failure);}});}
    private static boolean finished;
    public static void tick() throws Exception {
        if(finished||!Boolean.getBoolean("astral_repository.portClientSmoke"))return;
        var client=Minecraft.getInstance();
        if(client.getOverlay()!=null||!(client.screen instanceof TitleScreen))return;
        finished=true;
        if(!AstralPlaneRenderType.ready())throw new IllegalStateException("Astral shader did not load");
        var transfer=Class.forName("com.cappleapple.astralrepository.client.ResourceTransferRenderer").getDeclaredField("shader");
        transfer.setAccessible(true);
        if(transfer.get(null)==null)throw new IllegalStateException("Resource transfer shader did not load");
        var manager=client.getModelManager();
        var models=new java.util.ArrayList<>(java.util.List.of(AstralMineralClient.GEM_MODEL,AstralMineralClient.WAND_MODEL,
                AstralMineralClient.REMOTE_MODEL,AstralMineralClient.GOGGLES_MODEL,AstralMineralClient.GOGGLES_ICON_MODEL));
        for(var node:AstralContent.NODES)models.add(CrystalModelRenderer.modelLocation(node.get()));
        for(var id:models)if(manager.getModel(id)==manager.getMissingModel())throw new IllegalStateException("Missing model "+id);
        for(var id:models){int quads=0;var model=manager.getModel(id);var random=net.minecraft.util.RandomSource.create(42);for(int i=0;i<7;i++)quads+=model.getQuads(null,i==6?null:net.minecraft.core.Direction.values()[i],random).size();if(quads==0)throw new IllegalStateException("Empty standalone geometry "+id);}
        var graphics=new GuiGraphics(client,client.renderBuffers().bufferSource());
        int x=8;
        for(var item:java.util.List.of(AstralContent.ASTRAL_GEM.get(),AstralContent.ATTUNEMENT_WAND.get(),AstralContent.RESONANCE_GOGGLES.get(),AstralContent.SEED_STORAGE_CRYSTAL.get().asItem())){
            graphics.renderItem(new ItemStack(item),x,8);x+=20;
        }
        graphics.flush();
        LogUtils.getLogger().info("FABRIC_PORT_CLIENT_SMOKE_OK models={} shaders=2 renderedItems=4",models.size());
        java.nio.file.Files.writeString(java.nio.file.Path.of("client-smoke-result.txt"),"PASS: "+models.size()+" standalone models, both shaders, and four rendered GUI item models.");
        client.stop();
    }
}