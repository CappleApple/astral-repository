package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.*;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.tick.ServerTickEvent;

/** Opt-in actual inventory workload. No worker calls, resource resets, or artificial logistics budgets. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID,value=Dist.DEDICATED_SERVER)
public final class TenThousandRuneStress {
    private static final boolean ACTIVE=Boolean.getBoolean("astral_repository.tenThousandRuneStress");
    private static final Path OUT=Path.of(System.getProperty("astral_repository.tenThousandRuneStressOutput","../build/ten-thousand-rune-stress"));
    private static final int NETWORKS=50,HOSTS_PER_NETWORK=200,COUNT=NETWORKS*HOSTS_PER_NETWORK,STACK=64;
    private static final int WARMUP=Integer.getInteger("astral_repository.tenThousandRuneWarmup",600);
    private static final int SAMPLES=Integer.getInteger("astral_repository.tenThousandRuneSamples",600);
    private static final int OBSERVERS=Integer.getInteger("astral_repository.tenThousandRuneObservers",0);
    private static final String MODE=System.getProperty("astral_repository.tenThousandRuneMode","both");
    private static final Item[] ITEMS={Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT,Items.REDSTONE,Items.LAPIS_LAZULI,Items.QUARTZ,Items.COAL,Items.DIAMOND};
    private static final Host[] HOSTS=new Host[COUNT];
    private static final List<Node> NODES=new ArrayList<>();
    private static final CrystalNodeBlockEntity[] ROOTS=new CrystalNodeBlockEntity[NETWORKS];
    private static final Map<ItemKey,Long> EXPECTED=new HashMap<>();
    private static final List<Long> TIMES=new ArrayList<>(),POST_TIMES=new ArrayList<>(),VERIFY_TIMES=new ArrayList<>();
    private static final List<String> SUMMARIES=new ArrayList<>();
    private static final StringBuilder CSV=new StringBuilder("phase,sample,server_work_ms,post_tick_work_ms,verification_ms\n");
    private static List<ChunkPos> chunks;private static List<Boolean> phases;private static RuneStressObservers observers;
    private static State state=State.CHUNKS;private static int cursor,phase,ticks,setupTicks;private static long pre,post,started,sampleStarted;
    private static boolean initialized,done;private static String result;
    private static RouteMetrics previousRoutes=new RouteMetrics(0,0,0,0,0,0,0,0,0);
    private enum State { CHUNKS, CONTAINERS, NODES, RUNES, BINDINGS, ACTIVATE, WARMUP, SAMPLE }
    private static final class Host {
        final int id,network,bank,local;final BlockPos pos;final ItemStack item;
        Container inventory;RuneLayer rune;long previous;
        Host(int id,int network,int bank,int local,BlockPos pos,ItemStack item){this.id=id;this.network=network;this.bank=bank;this.local=local;this.pos=pos;this.item=item;}
    }
    private static final class Node {
        final int network;final BlockPos pos;final boolean nexus;CrystalNodeBlockEntity entity;
        Node(int network,BlockPos pos,boolean nexus){this.network=network;this.pos=pos;this.nexus=nexus;}
    }

    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!ACTIVE)return;MinecraftServer server=event.getServer();
        try{
            Files.createDirectories(OUT);check(server.isDedicatedServer(),"Requires dedicated server");check(WARMUP>=40&&SAMPLES>=40,"Need at least 40 warmup/sample ticks");
            phases=switch(MODE){case "direct"->List.of(false);case "relayed"->List.of(true);case "both"->List.of(false,true);default->throw new IllegalArgumentException("tenThousandRuneMode must be direct, relayed or both");};
            ServerLevel level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);level.setWeatherParameters(100000,0,false,false);
            var needed=new TreeSet<ChunkPos>(Comparator.comparingInt((ChunkPos p)->p.x).thenComparingInt(p->p.z));
            for(int group=0;group<NETWORKS;group++){
                BlockPos origin=new BlockPos(16+(group%10)*96,64,16+(group/10)*64);
                for(int bank=0;bank<2;bank++)for(int local=0;local<100;local++){
                    int id=group*200+bank*100+local;BlockPos pos=origin.offset(bank*40+(local%10)*2,0,(local/10)*2);
                    ItemStack item=new ItemStack(ITEMS[(group+local/2)%ITEMS.length]);
                    if((local/2)%8==0)item.set(DataComponents.CUSTOM_NAME,Component.literal("Network "+group+" sample "+local/2));
                    HOSTS[id]=new Host(id,group,bank,local,pos,item);needed.add(new ChunkPos(pos));
                }
                for(int bank=0;bank<2;bank++)for(int x:new int[]{3,9,15})for(int z:new int[]{3,9,15})NODES.add(new Node(group,origin.offset(bank*40+x,6,z),false));
                NODES.add(new Node(group,origin.offset(27,6,9),true));NODES.add(new Node(group,origin.offset(39,6,9),false));
            }
            for(Node node:NODES)needed.add(new ChunkPos(node.pos));chunks=List.copyOf(needed);
            initialized=true;started=System.nanoTime();MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,TenThousandRuneStress::finishTick);
            progress("Creating "+COUNT+" distinct container endpoints and "+NODES.size()+" crystals over "+NETWORKS+" physically separate networks; "+chunks.size()+" fixture chunks");
        }catch(Throwable failure){fail(server,failure);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beginTick(ServerTickEvent.Pre event){if(ACTIVE&&initialized&&!done)pre=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void postTick(ServerTickEvent.Post event){if(ACTIVE&&initialized&&!done)post=System.nanoTime();}
    public static void finishTick(ServerTickEvent.Post event){
        if(!ACTIVE||!initialized||done)return;long now=System.nanoTime();MinecraftServer server=event.getServer();
        try{
            if(state!=State.WARMUP&&state!=State.SAMPLE){setup(server.overworld());return;}
            ticks++;boolean sampled=state==State.SAMPLE;
            for(Host host:HOSTS){
                long count=host.rune.transferredItems();
                if(sampled){
                    if(!host.rune.enabled())throw new AssertionError("Rune unexpectedly disabled at "+host.pos+": "+host.rune.status());
                    if(count-host.previous!=STACK)throw new AssertionError("Expected 64 items at 1-tick cadence for rune "+host.id+" "+host.rune.mode()+" at "+host.pos+"; actual="+(count-host.previous)+" status="+host.rune.status());
                }
                host.previous=count;
            }
            if(ticks%20==0)verifyConservation();
            long verification=System.nanoTime()-now;
            if(sampled){
                TIMES.add(now-pre);POST_TIMES.add(now-post);VERIFY_TIMES.add(verification);
                CSV.append(String.format(Locale.ROOT,"%s,%d,%.6f,%.6f,%.6f%n",phaseName(),ticks,(now-pre)/1e6,(now-post)/1e6,verification/1e6));
                if(ticks==SAMPLES)completePhase(server,(System.nanoTime()-sampleStarted)/1e9);
            }else if(ticks==WARMUP){
                verifyTopology(server);verifyConservation();state=State.SAMPLE;ticks=0;sampleStarted=System.nanoTime();observers.reset();
                progress(phaseName()+": sampling "+SAMPLES+" actual server ticks; all "+COUNT+" rune destinations are distinct");
            }
        }catch(Throwable failure){fail(server,failure);}
    }
    private static void setup(ServerLevel level)throws Exception{
        // No sleeps or fabricated worker calls: at most 64 fixture edits and 5 ms of setup between real ticks.
        long until=System.nanoTime()+5_000_000;int operations=0;setupTicks++;
        while(operations++<64&&System.nanoTime()<until){
            switch(state){
                case CHUNKS->{
                    if(cursor==chunks.size()){move(State.CONTAINERS);continue;}
                    var chunk=chunks.get(cursor++);level.getChunk(chunk.x,chunk.z);level.setChunkForced(chunk.x,chunk.z,true);
                    if(operations>=2)return;
                }
                case CONTAINERS->{
                    if(cursor==COUNT){move(State.NODES);continue;}
                    Host host=HOSTS[cursor++];
                    for(var surface:RuneSurfaces.at(level,host.pos))RuneSurfaces.remove(level,host.pos,surface.facing());
                    if(level.getBlockEntity(host.pos) instanceof Container previous)previous.clearContent();
                    level.setBlockAndUpdate(host.pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(host.pos,containerState(host));
                    check(level.getBlockEntity(host.pos) instanceof Container,"Missing fixture container at "+host.pos);
                    host.inventory=(Container)level.getBlockEntity(host.pos);
                    host.inventory.setItem(0,host.item.copyWithCount(STACK));host.inventory.setItem(1,host.item.copyWithCount(STACK));
                    host.inventory.setItem(2,new ItemStack(Items.COBBLESTONE,STACK));host.inventory.setItem(3,new ItemStack(Items.DIRT,STACK));
                    EXPECTED.merge(new ItemKey(host.item),128L,Long::sum);EXPECTED.merge(new ItemKey(new ItemStack(Items.COBBLESTONE)),64L,Long::sum);EXPECTED.merge(new ItemKey(new ItemStack(Items.DIRT)),64L,Long::sum);
                }
                case NODES->{
                    if(cursor==NODES.size()){move(State.RUNES);continue;}
                    Node node=NODES.get(cursor++);
                    if(level.getBlockEntity(node.pos) instanceof CrystalNodeBlockEntity old)old.inventory().load(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
                    level.setBlockAndUpdate(node.pos,Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(node.pos,(node.nexus?AstralContent.STORAGE_NEXUS.get():AstralContent.RELAY_CRYSTAL.get()).defaultBlockState());
                    node.entity=(CrystalNodeBlockEntity)level.getBlockEntity(node.pos);node.entity.setChannel(node.network%16);if(node.nexus)ROOTS[node.network]=node.entity;
                }
                case RUNES->{
                    if(cursor==COUNT){move(State.BINDINGS);continue;}
                    Host host=HOSTS[cursor++];RuneSurface surface=RuneSurfaces.getOrCreate(level,host.pos,Direction.UP);surface.setChannel(host.network%16);
                    RuneLayer.Mode mode=(host.network+host.local/2)%2==0?RuneLayer.Mode.PUSH:RuneLayer.Mode.PULL;
                    host.rune=surface.addLayer(RuneGlyph.id(mode),mode);check(host.rune!=null,"Could not place fixture rune");host.rune.setEnabled(false);host.rune.setPriority(host.network%7-3);
                    RuneCadence cadence=RuneCadence.DEFAULT;
                    for(var kind:RuneCadence.Kind.values())cadence=cadence.with(kind,new RuneCadence.Rate(kind==RuneCadence.Kind.ITEMS?STACK:0,1));host.rune.setCadence(cadence);
                    host.rune.filter().add(host.item.has(DataComponents.CUSTOM_NAME)?FilterRules.Kind.COMPONENTS:FilterRules.Kind.ITEM,BuiltInRegistries.ITEM.getKey(host.item.getItem()).toString(),false,host.item);host.rune.changed();
                }
                case BINDINGS->{
                    if(cursor==COUNT){
                        Set<BlockPos> destinations=new HashSet<>();
                        for(Host host:HOSTS){Host target=target(host);destinations.add(host.rune.mode()==RuneLayer.Mode.PUSH?target.pos:host.pos);}
                        check(destinations.size()==COUNT,"Actual transfer destinations must be unique, got "+destinations.size());move(State.ACTIVATE);continue;
                    }
                    Host host=HOSTS[cursor++],target=target(host);RuneSurface surface=host.rune.surface();surface.clearTarget(host.rune.id());
                    var assignment=surface.toggleTarget(host.rune.id(),GlobalPos.of(level.dimension(),target.pos),Direction.UP);
                    check(assignment.assigned(),"Could not assign unique fixture target: "+assignment.message());
                }
                case ACTIVATE->{
                    if(cursor==COUNT){
                        if(observers==null)observers=new RuneStressObservers(level,OBSERVERS,new BlockPos(44,70,25));
                        state=State.WARMUP;cursor=0;ticks=0;TIMES.clear();POST_TIMES.clear();VERIFY_TIMES.clear();
                        verifyConservation();progress(phaseName()+": warming "+WARMUP+" ticks with all 10,000 runes active; setupElapsedSeconds="+(System.nanoTime()-started)/1e9);return;
                    }
                    HOSTS[cursor++].rune.setEnabled(true);
                }
                default->{return;}
            }
        }
        if(setupTicks%100==0)progress("Setup "+state+" "+cursor+"; tick="+setupTicks);
    }
    private static BlockState containerState(Host host){return switch((host.network+host.local/2)%6){
        case 0->Blocks.CHEST.defaultBlockState();case 1->Blocks.BARREL.defaultBlockState();case 2->Blocks.DROPPER.defaultBlockState();
        case 3->Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.ENABLED,false);case 4->Blocks.SHULKER_BOX.defaultBlockState();default->Blocks.TRAPPED_CHEST.defaultBlockState();
    };}
    private static Host target(Host host){return HOSTS[host.network*200+(phases.get(phase)?(1-host.bank)*100+host.local:host.bank*100+(host.local^1))];}
    private static String phaseName(){return phases.get(phase)?"10000_relayed":"10000_direct";}
    private static void move(State next){state=next;cursor=0;}
    private static void verifyTopology(MinecraftServer server){
        var manager=NetworkManager.get(server);Set<AstralNetwork> unique=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var root:ROOTS){var network=manager.networkAt(root.address());check(network!=null,"Relay network was not constructed");unique.add(network);}
        check(unique.size()==NETWORKS,"Expected 50 independent relay networks, got "+unique.size());
        for(Node node:NODES)check(manager.networkAt(node.entity.address())==manager.networkAt(ROOTS[node.network].address()),"A relay failed to join its intended network");
        if(phases.get(phase))for(int group=0;group<NETWORKS;group++){
            Host host=HOSTS[group*200],target=target(host);var route=manager.route(host.rune.surface().address().position(),target.rune.surface().address().position(),group%16,AstralServerConfig.wandBindingRange.get());
            check(route.size()>=4,"Distant containers did not use intermediate relays in network "+group+": "+route.size()+" nodes");
        }
    }
    private static void verifyConservation(){
        Map<ItemKey,Long> actual=new HashMap<>();
        for(Host host:HOSTS)for(int slot=0;slot<host.inventory.getContainerSize();slot++){
            ItemStack stack=host.inventory.getItem(slot);if(!stack.isEmpty())actual.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);
        }
        check(EXPECTED.equals(actual),"Real item quantities/components were not conserved");
    }
    private static void completePhase(MinecraftServer server,double elapsed)throws Exception{
        verifyConservation();
        String summary=String.format(Locale.ROOT,"%s: runes=%d unique_destinations=%d networks=%d samples=%d elapsed=%.3fs achieved_tps=%.3f items_transferred=%d; server_work_ms %s; post_tick_work_ms %s; verification_ms %s; %s",phaseName(),COUNT,COUNT,NETWORKS,SAMPLES,elapsed,SAMPLES/elapsed,(long)COUNT*STACK*SAMPLES,stats(TIMES),stats(POST_TIMES),stats(VERIFY_TIMES),observers.summary());
        RouteMetrics routes=routeMetrics(server);
        if(routes.completed>0)check(routes.workerThread!=routes.serverThread,"Prepared graph trees must be calculated off the server thread");
        summary+="; async_routes "+routes.describe(previousRoutes);previousRoutes=routes;
        SUMMARIES.add(summary);AstralRepository.LOGGER.info(summary);write();
        for(Host host:HOSTS)host.rune.setEnabled(false);
        if(++phase==phases.size()){
            done=true;result="PASS: 10,000 independent rune layers transferred 64 actual items each every measured server tick to 10,000 distinct destinations; 50 independent relay networks; quantities and item components conserved; modes="+MODE;
            write();observers.close();server.halt(false);
        }else{move(State.BINDINGS);progress("Rebinding 10,000 unique destinations for "+phaseName());}
    }
    private record RouteMetrics(long prepared,long synchronous,long submitted,long completed,long cancelled,long workerThread,long serverThread,int pending,int retained){
        String describe(RouteMetrics before){return "prepared_origins="+prepared+" synchronous_origins="+synchronous+" submitted="+submitted+" completed="+completed+" cancelled="+cancelled+" pending="+pending+" retained_states="+retained+" worker_thread="+workerThread+" server_thread="+serverThread
            +" phase_prepared_origins="+(prepared-before.prepared)+" phase_synchronous_origins="+(synchronous-before.synchronous)+" phase_submitted="+(submitted-before.submitted)+" phase_completed="+(completed-before.completed);}
    }
    private static RouteMetrics routeMetrics(MinecraftServer server)throws ReflectiveOperationException{
        var manager=NetworkManager.get(server);var treesField=NetworkManager.class.getDeclaredField("preparedRouteTrees");treesField.setAccessible(true);Object trees=treesField.get(manager);
        return new RouteMetrics(longField(manager,"preparedRouteOrigins"),longField(manager,"synchronousRouteOrigins"),longField(trees,"submitted"),longField(trees,"completed"),longField(trees,"cancelled"),longField(trees,"lastWorkerThreadId"),Thread.currentThread().threadId(),intMethod(trees,"pendingCount"),intMethod(trees,"retainedStates"));
    }
    private static long longField(Object instance,String name)throws ReflectiveOperationException{var field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.getLong(instance);}
    private static int intMethod(Object instance,String name)throws ReflectiveOperationException{var method=instance.getClass().getDeclaredMethod(name);method.setAccessible(true);return (int)method.invoke(instance);}
    private static String stats(List<Long> values){long[] sorted=values.stream().mapToLong(Long::longValue).sorted().toArray();return String.format(Locale.ROOT,"median=%.4f p95=%.4f p99=%.4f max=%.4f over50ms=%d",pct(sorted,.5)/1e6,pct(sorted,.95)/1e6,pct(sorted,.99)/1e6,sorted[sorted.length-1]/1e6,Arrays.stream(sorted).filter(v->v>50_000_000).count());}
    private static long pct(long[] values,double p){return values[Math.min(values.length-1,(int)Math.ceil(values.length*p)-1)];}
    private static void progress(String message)throws Exception{Files.writeString(OUT.resolve("progress.txt"),message);AstralRepository.LOGGER.info("10k rune stress: {}",message);}
    private static void write()throws Exception{
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("ticks.csv"),CSV);
        String mods=ModList.get().getMods().stream().map(info->info.getModId()+"="+info.getVersion()).sorted().reduce((a,b)->a+", "+b).orElse("");
        Files.writeString(OUT.resolve("metrics.txt"),"Real dedicated server; Java "+System.getProperty("java.version")+"; "+Runtime.getRuntime().availableProcessors()+" logical processors; mods: "+mods+"\n"
            +"10,000 distinct vanilla inventories and 10,000 rune layers. Six container types; eight item types and 350 named variants; filtered ballast retained. 50 isolated relay networks, 20 crystals each. Direct stage uses separate neighboring targets; relayed stage crosses 40 blocks through the corresponding network. All destinations are distinct in each stage.\n"
            +"Observed configuration: bindingRange="+AstralServerConfig.wandBindingRange.get()+", relayRange="+AstralConfig.relayRange.get()+", lineOfSight="+AstralConfig.requireLineOfSight.get()+", discoveryBudget="+AstralConfig.discoveryBudget.get()+", reconciliationBudget="+AstralConfig.reconciliationBudget.get()+", maxItemTransfer="+AstralServerConfig.maxItemTransfer.get()+", minItemTransferTicks="+AstralServerConfig.minItemTransferTicks.get()+".\n"
            +"Normal production ticking only. No runtime transfer/range/discovery/reconciliation budget overrides. Bounded fixture construction precedes warmup. Native transfers circulate existing stacks; no per-tick replenishment. This is an item-transfer benchmark, not a guarantee for arbitrary provider mods or hardware.\n"
            +"Pre(HIGHEST)-to-final-Post(LOWEST) includes real network and visual dispatch work, unlike vanilla's pre-Post timing. Verification is outside recorded work; achieved TPS includes it. Optional fake observers are near the first network only and encode/decode real visual batches without socket traffic.\n"
            +String.join("\n",SUMMARIES)+"\n");
        if(result!=null)Files.writeString(OUT.resolve("result.txt"),result);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void fail(MinecraftServer server,Throwable failure){done=true;result="FAIL: "+failure;AstralRepository.LOGGER.error("10k rune stress failed",failure);try{write();}catch(Exception ignored){}if(observers!=null)observers.close();server.halt(false);}
}
