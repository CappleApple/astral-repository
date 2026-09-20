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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in background GPU smoke gate; omitted from the distributable JAR. */
@Mod.EventBusSubscriber(modid="astral_repository",value=Dist.CLIENT)
public final class ForgeClientSmoke {
    private static boolean finished;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if(event.phase!=TickEvent.Phase.END||finished||!Boolean.getBoolean("astral_repository.portClientSmoke"))return;
        var client=Minecraft.getInstance();
        if(client.getOverlay()!=null||client.screen==null||!AstralPlaneRenderType.ready())return;
        if(client.screen instanceof Gallery){
            if(++ticks<20)return;
            var out=java.nio.file.Path.of("captures");java.nio.file.Files.createDirectories(out);
            try(var capture=net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())){capture.writeToFile(out.resolve("items.png"));}
            LogUtils.getLogger().info("FORGE_PORT_CLIENT_CAPTURE_OK captures/items.png");finished=true;client.stop();return;
        }
        if(!AstralPlaneRenderType.ready())throw new IllegalStateException("Astral shader did not load");
        var transfer=Class.forName("com.cappleapple.astralrepository.client.ResourceTransferRenderer").getDeclaredField("shader");
        transfer.setAccessible(true);
        if(transfer.get(null)==null)throw new IllegalStateException("Resource transfer shader did not load");
        var manager=client.getModelManager();
        var models=new java.util.ArrayList<>(java.util.List.of(AstralMineralClient.GEM_MODEL,AstralMineralClient.WAND_MODEL,
                AstralMineralClient.REMOTE_MODEL,AstralMineralClient.GOGGLES_MODEL,AstralMineralClient.GOGGLES_ICON_MODEL));
        for(var node:AstralContent.NODES)models.add(CrystalModelRenderer.modelLocation(node.get()));
        for(var id:models)if(manager.getModel(id)==manager.getMissingModel())throw new IllegalStateException("Missing model "+id);
        client.setScreen(new Gallery());
        LogUtils.getLogger().info("FORGE_PORT_CLIENT_SMOKE_OK models={} shaders=2 renderedItems=4",models.size());

    }
    private static final class Gallery extends net.minecraft.client.gui.screens.Screen {
        Gallery(){super(net.minecraft.network.chat.Component.literal("Astral Repository - Forge 1.20.1"));}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
            graphics.fill(0,0,width,height,0xff182132);graphics.drawCenteredString(font,title,width/2,20,0xffffffff);
            var items=java.util.List.of(AstralContent.ASTRAL_GEM.get(),AstralContent.ATTUNEMENT_WAND.get(),AstralContent.RESONANCE_GOGGLES.get(),AstralContent.SEED_STORAGE_CRYSTAL.get().asItem());
            int x=width/2-items.size()*16;for(var item:items){graphics.renderItem(new ItemStack(item),x,height/2);x+=32;}
        }
    }
}

