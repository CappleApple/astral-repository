package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.tick.ServerTickEvent;

/** Separate actual aggregate-provider workload; never loaded in the production JAR. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID,value=Dist.DEDICATED_SERVER)
public final class DedicatedNetworkRuneStress {
    private static final boolean ACTIVE=Boolean.getBoolean("astral_repository.networkRuneStress");
    private static final Path OUT=Path.of(System.getProperty("astral_repository.networkRuneStressOutput","../build/network-rune-stress"));
    private static final boolean DENSE=Boolean.getBoolean("astral_repository.networkRuneStressDense");
    private static final int WARMUP=Integer.getInteger("astral_repository.runeStressWarmup",DENSE?800:240),SAMPLES=Integer.getInteger("astral_repository.runeStressSamples",400);
    private static final List<Work> WORK=new ArrayList<>();
    private static final List<Container> HOSTS=new ArrayList<>();
    private static final List<CrystalNodeBlockEntity> STORES=new ArrayList<>(),RELAYS=new ArrayList<>(),NEXUSES=new ArrayList<>();
    private static final List<Long> TIMES=new ArrayList<>(),POST_TIMES=new ArrayList<>();
    private static final List<String> SUMMARIES=new ArrayList<>();
    private static final StringBuilder CSV=new StringBuilder("phase,sample,server_work_ms,post_tick_work_ms\n");
    private static RuneStressObservers observers;
    private static boolean ready,done;private static int phase,ticks;private static long pre,post,start,expectedTotal;private static String result;
    private static final class Work {final RuneLayer rune;long previous;boolean active;Work(RuneLayer rune){this.rune=rune;}}

    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!ACTIVE)return;MinecraftServer server=event.getServer();
        try{
            Files.createDirectories(OUT);check(WARMUP>=40&&SAMPLES>=40,"Need at least 40 warmup/sample ticks");
            ServerLevel level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int group=0;group<8;group++)fixture(level,group);
            denseFixture(level);
            expectedTotal=total();observers=new RuneStressObservers(level,Integer.getInteger("astral_repository.runeStressObservers",0),new BlockPos(72,70,32));
            ready=true;MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,DedicatedNetworkRuneStress::finishTick);selectPhase();
            AstralRepository.LOGGER.info("Network rune stress: 128 host barrels, 256 independent Push/Pull layers, {} relay/Nexus networks and {} native storage crystals",DENSE?1:8,STORES.size());
        }catch(Throwable error){fail(server,error);}
    }
    private static void load(ServerLevel level,BlockPos pos){level.getChunkAt(pos);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}
    private static CrystalNodeBlockEntity node(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.Block block,int channel){
        load(level,pos);if(level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity previous)previous.inventory().load(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos,block.defaultBlockState());
        var node=(CrystalNodeBlockEntity)level.getBlockEntity(pos);node.setChannel(channel);return node;
    }
    private static void fixture(ServerLevel level,int group){
        BlockPos origin=new BlockPos(16+(group%4)*48,64,16+(group/4)*48);
        var relay=node(level,origin.offset(3,6,3),AstralContent.RELAY_CRYSTAL.get(),group);RELAYS.add(relay);
        var nexus=node(level,origin.offset(3,6,9),AstralContent.STORAGE_NEXUS.get(),group);NEXUSES.add(nexus);
        for(BlockPos offset:List.of(new BlockPos(-3,6,3),new BlockPos(9,6,3),new BlockPos(3,6,-3),new BlockPos(3,6,15))){
            var store=node(level,origin.offset(offset),AstralContent.SEED_STORAGE_CRYSTAL.get(),group);
            check(store.inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,8192),false).isEmpty(),"Storage fixture did not accept iron");STORES.add(store);
        }
        for(int i=0;i<16;i++){
            BlockPos pos=origin.offset((i%4)*2,0,(i/4)*2);load(level,pos);
            for(var surface:RuneSurfaces.at(level,pos))RuneSurfaces.remove(level,pos,surface.facing());
            if(level.getBlockEntity(pos) instanceof Container previous)previous.clearContent();
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());
            Container host=(Container)level.getBlockEntity(pos);host.setItem(0,new ItemStack(Items.IRON_INGOT,64));host.setItem(1,new ItemStack(Items.IRON_INGOT,64));HOSTS.add(host);
            RuneSurface surface=RuneSurfaces.getOrCreate(level,pos,Direction.UP);surface.setChannel(group);
            for(var mode:List.of(RuneLayer.Mode.PUSH,RuneLayer.Mode.PULL)){
                RuneLayer rune=surface.addLayer(RuneGlyph.id(mode),mode);rune.setEnabled(false);
                RuneCadence cadence=RuneCadence.DEFAULT;
                for(var kind:RuneCadence.Kind.values())cadence=cadence.with(kind,new RuneCadence.Rate(kind==RuneCadence.Kind.ITEMS?64:0,1));rune.setCadence(cadence);
                var target=mode==RuneLayer.Mode.PUSH?relay:nexus;
                check(surface.toggleTarget(rune.id(),target.address().position(),null).assigned(),"Aggregate target did not bind");WORK.add(new Work(rune));
            }
        }
        // All hosts are six blocks below the network: reachable by runes (8), outside discovery (5).
        // They therefore cannot consume one another's buffers through the aggregate view.
    }
    private static void denseFixture(ServerLevel level){
        if(DENSE){
            for(var store:STORES)store.setChannel(0);
            for(var relay:RELAYS)relay.setChannel(0);
            for(var nexus:NEXUSES)nexus.setChannel(0);
            for(var work:WORK)work.rune.surface().setChannel(0);
        }
        for(int i=0;i<96;i++){
            BlockPos pos=new BlockPos(16+(i%16)*10,76,16+(i/16)*12);load(level,pos);
            if(DENSE){
                var store=node(level,pos,AstralContent.SEED_STORAGE_CRYSTAL.get(),0);
                check(store.inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,8192),false).isEmpty(),"Dense store rejected fixture stock");STORES.add(store);
            }else if(level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity previous){
                previous.inventory().load(new net.minecraft.nbt.CompoundTag(),level.registryAccess());level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beginTick(ServerTickEvent.Pre event){if(ACTIVE&&ready&&!done)pre=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void postTick(ServerTickEvent.Post event){if(ACTIVE&&ready&&!done)post=System.nanoTime();}
    public static void finishTick(ServerTickEvent.Post event){
        if(!ACTIVE||!ready||done)return;long now=System.nanoTime();MinecraftServer server=event.getServer();
        try{
            ticks++;boolean sampled=ticks>WARMUP;
            for(var work:WORK){
                long current=work.rune.transferredItems();
                if(sampled){check(work.rune.enabled()==work.active,"Aggregate rune disabled unexpectedly");check(current-work.previous==(work.active?64:0),"Aggregate rune did not transfer a full stack at 1-tick cadence at "+work.rune.surface().getBlockPos()+" "+work.rune.mode()+" delta="+(current-work.previous)+" status="+work.rune.status());}
                work.previous=current;
            }
            if(ticks==WARMUP){
                var manager=NetworkManager.get(server);
                for(int i=0;i<8;i++){
                    var network=manager.networkAt(RELAYS.get(i).address());check(network!=null&&network==manager.networkAt(NEXUSES.get(i).address()),"Relay and Nexus did not auto-connect");
                    check(network.storageCount()==(DENSE?128:4),"Expected "+(DENSE?128:4)+" native stores, no discovered host barrels; got "+network.storageCount());
                    if(DENSE)check(network==manager.networkAt(RELAYS.get(0).address()),"Dense network did not join every relay/Nexus");
                }
                var route=manager.route(WORK.get(0).rune.surface().address().position(),NEXUSES.get(0).address().position(),0,8);
                check(route.size()>=3,"The distant Nexus binding did not traverse a relay");start=System.nanoTime();
            }
            if(ticks%20==0)check(total()==expectedTotal,"Network inventory conservation failed");
            if(sampled){TIMES.add(now-pre);POST_TIMES.add(now-post);CSV.append(String.format(Locale.ROOT,"%d,%d,%.6f,%.6f%n",activeCount(),ticks-WARMUP,(now-pre)/1e6,(now-post)/1e6));}
            if(ticks==WARMUP+SAMPLES){
                double elapsed=(System.nanoTime()-start)/1e9;
                String summary=String.format(Locale.ROOT,"aggregate_%d: samples=%d elapsed=%.3fs achieved_tps=%.3f items_transferred=%d; server_work_ms %s; post_tick_work_ms %s; %s",activeCount(),SAMPLES,elapsed,SAMPLES/elapsed,(long)activeCount()*64*SAMPLES,stats(TIMES),stats(POST_TIMES),observers.summary());
                SUMMARIES.add(summary);AstralRepository.LOGGER.info(summary);write();
                long[] ordered=TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
                check(pct(ordered,.95)<50_000_000,"Aggregate server work p95 exceeds the 50 ms tick budget");
                check(SAMPLES/elapsed>=19.5,"Sustained aggregate TPS fell below 19.5");
                if(++phase==(DENSE?1:2)){done=true;result="PASS: real aggregate network stress; "+(DENSE?"256 busy runes in one network with 128 native storage providers":"128 then 256 busy runes across eight four-provider networks")+"; each moved 64 real items every sampled tick through Relay/Nexus targets; automatic relay paths and item conservation verified.";write();observers.close();server.halt(false);}else selectPhase();
            }
        }catch(Throwable error){fail(server,error);}
    }
    private static int activeCount(){return DENSE?256:phase==0?128:256;}
    private static void selectPhase(){for(int i=0;i<WORK.size();i++){var work=WORK.get(i);work.active=i<activeCount();work.rune.setEnabled(work.active);}ticks=0;TIMES.clear();POST_TIMES.clear();observers.reset();AstralRepository.LOGGER.info("Aggregate rune stress: {} busy rune layers",activeCount());}
    private static long total(){long total=0;for(Container host:HOSTS)for(int i=0;i<host.getContainerSize();i++)total+=host.getItem(i).getCount();for(var store:STORES)total+=store.inventory().used();return total;}
    private static String stats(List<Long> values){long[] sorted=values.stream().mapToLong(Long::longValue).sorted().toArray();return String.format(Locale.ROOT,"median=%.4f p95=%.4f p99=%.4f max=%.4f over50ms=%d",pct(sorted,.5)/1e6,pct(sorted,.95)/1e6,pct(sorted,.99)/1e6,sorted[sorted.length-1]/1e6,Arrays.stream(sorted).filter(n->n>50_000_000).count());}
    private static long pct(long[] sorted,double p){return sorted[Math.min(sorted.length-1,(int)Math.ceil(p*sorted.length)-1)];}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void write()throws Exception{Files.createDirectories(OUT);Files.writeString(OUT.resolve("ticks.csv"),CSV);Files.writeString(OUT.resolve("metrics.txt"),"Real dedicated server; 128 barrel hosts, 256 independently bound rune layers; "+(DENSE?"one network with 128 native storage crystals, 8 Relays and 8 Nexuses":"8 disconnected dye channels, each with one Relay, one Nexus, and four native storage crystals")+". Every active Push/Pull transfers a full stack every tick. Item-only aggregate profile; no claims for arbitrary large provider networks.\nPre-to-final-Post includes actual network/visual flush work. Verification is outside recorded work; elapsed TPS includes it. Observer packet encoding/decoding is real but no client/socket traffic.\n"+String.join("\n",SUMMARIES)+"\n");if(result!=null)Files.writeString(OUT.resolve("result.txt"),result);}
    private static void fail(MinecraftServer server,Throwable error){done=true;result="FAIL: "+error;AstralRepository.LOGGER.error("Aggregate rune stress failed",error);try{write();}catch(Exception ignored){}if(observers!=null)observers.close();server.halt(false);}
}
