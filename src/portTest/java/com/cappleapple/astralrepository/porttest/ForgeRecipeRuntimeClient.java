package com.cappleapple.astralrepository.porttest;

import com.mojang.logging.LogUtils;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Production client fixture using an isolated world and the real viewer registration. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="astral_port_test",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ForgeRecipeRuntimeClient {
    private static int phase,ticks;
    private static boolean done;
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||done||!Boolean.getBoolean("astral_repository.recipeCompatClient"))return;
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0D);
        try{
            if(++ticks>2400)throw new AssertionError("Recipe client startup timeout in phase "+phase);
            if(phase==0&&mc.screen!=null&&mc.getOverlay()==null){
                mc.options.guiScale().set(2);mc.options.renderDistance().set(3);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                phase=1;
                mc.createWorldOpenFlows().createFreshLevel("recipe-compat-"+System.currentTimeMillis(),new LevelSettings("Recipe compatibility",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }else if(phase==1&&mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()!=null&&mc.getOverlay()==null){
                mc.setScreen(null);phase=2;ticks=0;
                LogUtils.getLogger().info("FORGE_RECIPE_RUNTIME_SOURCE {}",com.cappleapple.astralrepository.menu.NexusMenu.class.getProtectionDomain().getCodeSource().getLocation());
            }else if(phase==2&&RecipeCompatClientSmoke.tick()){
                done=true;LogUtils.getLogger().info("FORGE_RECIPE_COMPAT_CLIENT_OK");mc.stop();
            }
        }catch(Throwable failure){
            done=true;LogUtils.getLogger().error("Forge recipe compatibility client failed",failure);
            try{Files.createDirectories(Path.of("recipe-captures"));Files.writeString(Path.of("recipe-captures/result.txt"),"FAIL: "+failure+"\n");}catch(Exception ignored){}
            mc.stop();
        }
    }
}
