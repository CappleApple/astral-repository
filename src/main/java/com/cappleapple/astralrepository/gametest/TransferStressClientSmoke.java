package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.client.WorldVisuals;
import com.cappleapple.astralrepository.network.NetworkPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import java.nio.file.*;
import java.util.*;

/** Excluded development profile: actual world rendering under a 10,000-flight offered load. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID,value=Dist.CLIENT)
public final class TransferStressClientSmoke {
    private static int stage,ticks,query;
    private static boolean pendingQuery,recording;
    private static final List<Double> cpu=new ArrayList<>(),gpu=new ArrayList<>();
    private static final boolean UNIQUE=Boolean.getBoolean("astral_repository.transferStressUnique");
    private static final boolean ITEMS=Boolean.getBoolean("astral_repository.transferStressItems");
    private static final Path OUT=Path.of("../build/client-smoke/"+(UNIQUE?"transfer-stress-unique":ITEMS?"transfer-stress-items":"transfer-stress"));
    private static int maxItems,maxIcons,maxResources,maxTrails;
    private static long admissionNanos,frozenAt;
    private static Object first;
    private static final int[] saved=new int[4];
    private static boolean oldGui;
    @SubscribeEvent(priority=EventPriority.HIGH) public static void before(RenderLevelStageEvent e){
        if(!Boolean.getBoolean("astral_repository.transferStress")||!recording||!com.cappleapple.astralrepository.client.TransferRenderPass.matches(e))return;
        if(pendingQuery&&GL15.glGetQueryObjecti(query,GL15.GL_QUERY_RESULT_AVAILABLE)!=0){gpu.add(GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT)/1e6);pendingQuery=false;}
        if(!pendingQuery){if(query==0)query=GL15.glGenQueries();GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);}
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void after(RenderLevelStageEvent e){
        if(!Boolean.getBoolean("astral_repository.transferStress")||!recording||!com.cappleapple.astralrepository.client.TransferRenderPass.matches(e))return;
        if(!pendingQuery){GL15.glEndQuery(GL33.GL_TIME_ELAPSED);pendingQuery=true;}
        var stats=WorldVisuals.performance();check(stats.iconModelResolutions()<=16,"Unbounded item model preparation");cpu.add(stats.renderNanos()/1e6);
        maxItems=Math.max(maxItems,stats.itemsDrawn());maxIcons=Math.max(maxIcons,stats.iconsDrawn());maxResources=Math.max(maxResources,stats.resourcesDrawn());maxTrails=Math.max(maxTrails,stats.trailsDrawn());
    }
    public static boolean tick() throws Exception {
        var mc=Minecraft.getInstance();ticks++;
        if(stage==0){
            Files.createDirectories(OUT);oldGui=mc.options.hideGui;mc.options.hideGui=true;
            saved[0]=AstralClientConfig.maxActiveTransfers.get();saved[1]=AstralClientConfig.maxItemTransferModels.get();saved[2]=AstralClientConfig.maxResourceTrails.get();saved[3]=AstralClientConfig.transferRenderDistance.get();
            AstralClientConfig.maxActiveTransfers.set(4096);AstralClientConfig.maxItemTransferModels.set(128);AstralClientConfig.maxResourceTrails.set(512);AstralClientConfig.transferRenderDistance.set(96);
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(0,-55,30,180,0);});stage=1;ticks=0;
        }else if(stage==1&&ticks>40){
            list("flights").clear();list("resourceTrails").clear();stage=2;ticks=0;
        }else if(stage==2&&ticks>2){
            var random=new Random(321);
            long begin=System.nanoTime();
            for(int i=0;i<10000;i++){
                var from=new BlockPos(-15+random.nextInt(16),-57+random.nextInt(9),random.nextInt(18));
                var to=from.offset(10+random.nextInt(5),random.nextInt(5)-2,random.nextInt(7)-3);
                int slot=ITEMS||i<128?-1:-2-i%3;
                var item=slot==-1?new ItemStack(List.of(Items.DIAMOND,Items.CHEST,Items.IRON_BLOCK,Items.DIAMOND_PICKAXE,Items.OAK_STAIRS,Items.APPLE,Items.ENDER_PEARL,Items.IRON_INGOT).get(i%8)):ItemStack.EMPTY;
                if(UNIQUE&&!item.isEmpty())item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Transfer variant "+i));
                WorldVisuals.add(new NetworkPackets.Visual(from,to,item,0x8270ee,600,slot,List.of(from,from.offset(4,2,0),to),null,null,slot==-2?ResourceLocation.withDefaultNamespace(i%2==0?"water":"lava"):null));
            }
            admissionNanos=System.nanoTime()-begin;
            check(WorldVisuals.performance().active()==4096,"Admission did not retain its configured bounded population");
            check(WorldVisuals.performance().sampled()>=5904,"Excess presentation was not sampled out");
            first=list("flights").getFirst();recording=UNIQUE;stage=3;ticks=0;
        }else if(stage==3){
            if(ticks==25){recording=true;}
            if(ticks==45){Screenshot.takeScreenshot(mc.getMainRenderTarget()).writeToFile(OUT.resolve("ten_thousand_offered.png"));}
            if(ticks==70){
                long local=longField("visualTicks");
                // Simulate a server clock correction. Client cosmetic time must neither jump nor reset.
                mc.level.setGameTime(1L<<40);
                check(longField("visualTicks")==local,"Server game time correction jumped the cosmetic clock");
                for(int i=0;i<10000;i++)WorldVisuals.add(new NetworkPackets.Visual(BlockPos.ZERO,new BlockPos(12,0,0),ItemStack.EMPTY,0,600,-2));
                check(list("flights").contains(first),"Load evicted a visible flight before it finished");
            }
            if(ticks==90){mc.level.tickRateManager().setFrozen(true);}
            if(ticks==91){check(!mc.level.tickRateManager().runsNormally(),"Tick freeze did not take effect");frozenAt=longField("visualTicks");}
            if(ticks==100){check(longField("visualTicks")==frozenAt,"Tick freeze advanced cosmetic animations");mc.level.tickRateManager().setFrozen(false);}
            if(ticks>=140){
                recording=false;
                check(cpu.size()>60&&gpu.size()>10,"Not enough real rendered frames");
                check((ITEMS?maxIcons:maxResources)>=3000,"Stress population was not visible: "+(ITEMS?maxIcons:maxResources));
                check(maxItems==128,"Moving item model budget was not exercised: "+maxItems);
                check(maxTrails<=512,"Trail budget exceeded");
                check(list("flights").contains(first),"Time sync correction prematurely expired active flights");
                String report="10,000 initial offers; 10,000 more during flight; actual hidden Minecraft world rendering.\n"
                    +"gpu="+org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)+"\n"
                    +"admission_ms="+admissionNanos/1e6+"\nactive="+WorldVisuals.performance().active()+"\nsampled="+WorldVisuals.performance().sampled()+"\n"
                    +"max_drawn_items="+maxItems+"\nmax_drawn_icons="+maxIcons+"\nmax_drawn_resources="+maxResources+"\nmax_drawn_trails="+maxTrails+"\n"
                    +"first_recorded_frame_cpu_ms="+cpu.getFirst()+"\nmax_recorded_frame_cpu_ms="+Collections.max(cpu)+"\n"
                    +"frames="+cpu.size()+"\nrender_cpu_ms_median="+percentile(cpu,.5)+"\nrender_cpu_ms_p95="+percentile(cpu,.95)+"\n"
                    +"gpu_samples="+gpu.size()+"\nrender_gpu_ms_median="+percentile(gpu,.5)+"\nrender_gpu_ms_p95="+percentile(gpu,.95)+"\n"
                    +"Client-only offered-load benchmark; no claim of identical FPS on other hardware. GPU scope includes other handlers at the selected transfer render stage between fixture hooks.\n";
                Files.writeString(OUT.resolve("metrics.txt"),report);AstralRepository.LOGGER.info("Transfer stress measurements:\n{}",report);
                check(!mc.mouseHandler.isMouseGrabbed()&&mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"Client was not silent and mouse-free");
                list("flights").clear();list("resourceTrails").clear();
                AstralClientConfig.maxActiveTransfers.set(saved[0]);AstralClientConfig.maxItemTransferModels.set(saved[1]);AstralClientConfig.maxResourceTrails.set(saved[2]);AstralClientConfig.transferRenderDistance.set(saved[3]);mc.options.hideGui=oldGui;
                if(query!=0)GL15.glDeleteQueries(query);
                Files.writeString(OUT.resolve("result.txt"),"PASS: bounded 20,000 offers, no active-flight eviction, local clock correction isolation and tick-freeze stability, real GPU and CPU measurements.\n");
                Files.writeString(Path.of("../build/client-smoke/result.txt"),"PASS: transfer stress; see transfer-stress/metrics.txt.\n");return true;
            }
        }
        return false;
    }
    private static double percentile(List<Double> values,double fraction){var copy=new ArrayList<>(values);Collections.sort(copy);return copy.get(Math.min(copy.size()-1,(int)(copy.size()*fraction)));}
    private static List<?> list(String name)throws Exception{var f=WorldVisuals.class.getDeclaredField(name);f.setAccessible(true);return (List<?>)f.get(null);}
    private static long longField(String name)throws Exception{var f=WorldVisuals.class.getDeclaredField(name);f.setAccessible(true);return f.getLong(null);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
