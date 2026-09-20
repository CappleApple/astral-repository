package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.*;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real user requests, native machines and routed delivery; excluded from the production JAR. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID,value=Dist.DEDICATED_SERVER)
public final class DedicatedDeepCraftStress {
    private static final boolean ACTIVE=Boolean.getBoolean("astral_repository.deepCraftStress");
    private static final Path OUT=Path.of(System.getProperty("astral_repository.deepCraftStressOutput","../build/deep-craft-stress"));
    private static final int ORDERS=Integer.getInteger("astral_repository.deepCraftStressOrders",2),COUNT=Integer.getInteger("astral_repository.deepCraftStressCount",128),TIMEOUT=Integer.getInteger("astral_repository.deepCraftStressTimeout",18000);
    private static final String[] NAMES={"angler","archer","arms_up","blade","brewer","burn","danger","explorer","friend","heart","heartbreak","howl","miner"};
    private static final String[] PROCESSES={"crafting","smelting","blasting","smoking","stonecutting"};
    private static final Block[] MACHINES={Blocks.CRAFTING_TABLE,Blocks.FURNACE,Blocks.BLAST_FURNACE,Blocks.SMOKER,Blocks.STONECUTTER};
    private static final List<Item> ITEMS=new ArrayList<>();
    private static final Map<BlockPos,Integer> STATIONS=new LinkedHashMap<>();
    private static final List<CrystalNodeBlockEntity> NODES=new ArrayList<>(),STORES=new ArrayList<>();
    private static final Map<UUID,Integer> PLANNED=new LinkedHashMap<>();
    private static final Set<UUID> COMPLETED=new HashSet<>(),INSPECTED=new HashSet<>();
    private static final Map<BlockPos,Arrival> ARRIVALS=new HashMap<>();
    private static final Map<BlockPos,Item> FURNACE_INPUTS=new HashMap<>();
    private static final long[] STARTED=new long[13];
    private static final List<Long> TIMES=new ArrayList<>(),POST_TIMES=new ArrayList<>();
    private static final StringBuilder CSV=new StringBuilder("tick,server_work_ms,post_tick_work_ms,active_jobs,active_processors,completed_operations\n");
    private static final Field JOBS=field(CraftingService.class,"jobs"),PLAN=field(CraftScheduler.class,"plan"),DONE=field(CraftScheduler.class,"completed"),RUNNING=field(CraftScheduler.class,"running");
    private static MinecraftServer server;private static ServerLevel level;private static FakePlayer observer;private static AstralNetwork network;
    private static Throwable observerFailure;
    private static boolean ready,requested,done;private static int ticks,requestedTick,settleTicks,maxProcessors,dependencyDepth;private static long pre,post,startedNanos,packetBytes,packets,flights,relayedFlights,directMachineFlights,fuelFlights;private static String result;
    private record Arrival(int stage,int due) {}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!ACTIVE)return;server=event.getServer();level=server.overworld();
        try{
            check(ORDERS>=2&&ORDERS<=16&&COUNT>=64&&COUNT<=512,"Use 2..16 orders of 64..512 items");
            check(!AstralConfig.instantAutomaticLogistics.get(),"This fixture must exercise actual transit and processor waiting");
            check(AstralConfig.instantPlayerInteractions.get(),"This fixture tests the default immediate Nexus request path");
            Files.createDirectories(OUT);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(String name:NAMES)ITEMS.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:"+name+"_pottery_sherd")));
            for(int i=1;i<=12;i++)check(level.getRecipeManager().byKey(ResourceLocation.parse(String.format(Locale.ROOT,"astral_stress:stage_%02d",i))).isPresent(),"Missing isolated stage recipe "+i);
            fixture();observer();ready=true;NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,DedicatedDeepCraftStress::finishTick);
            AstralRepository.LOGGER.info("Deep crafting stress: {} concurrent orders x {} items x 12 dependent stages; 40 machines across five types",ORDERS,COUNT);
        }catch(Throwable error){fail(error);}
    }
    private static void load(BlockPos pos){level.getChunkAt(pos);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}
    private static void place(BlockPos pos,Block block){
        load(pos);if(level.getBlockEntity(pos) instanceof Container c)c.clearContent();
        if(level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity c)c.inventory().load(new CompoundTag(),level.registryAccess());
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos,block.defaultBlockState());
    }
    private static CrystalNodeBlockEntity node(BlockPos pos,Block block){place(pos,block);var node=(CrystalNodeBlockEntity)level.getBlockEntity(pos);node.setChannel(0);NODES.add(node);return node;}
    private static void fixture(){
        for(int type=0;type<5;type++){
            BlockPos center=new BlockPos(16+type*12,67,16);node(center,type==0?AstralContent.STORAGE_NEXUS.get():AstralContent.RELAY_CRYSTAL.get());
            for(int i=0;i<8;i++){BlockPos pos=center.offset((i%4)*2-3,-3,i<4?-2:2);place(pos,MACHINES[type]);STATIONS.put(pos,type);}
        }
        var inputs=node(new BlockPos(12,67,16),AstralContent.SEED_STORAGE_CRYSTAL.get());STORES.add(inputs);
        check(inputs.inventory().insertItem(0,new ItemStack(ITEMS.getFirst(),ORDERS*COUNT),false).isEmpty(),"Raw fixture stock rejected");
        var fuel=node(new BlockPos(16,67,12),AstralContent.SEED_STORAGE_CRYSTAL.get());STORES.add(fuel);
        check(fuel.inventory().insertItem(0,new ItemStack(Items.COAL,4096),false).isEmpty(),"Fuel fixture stock rejected");
        BlockPos shelf=new BlockPos(16,66,19);place(shelf,Blocks.CHISELED_BOOKSHELF);
        var tome=new ItemStack(AstralContent.RECIPE_TOME.get());var data=new CompoundTag();data.put("Output",new ItemStack(ITEMS.getLast()).save(level.registryAccess()));data.putString("OutputId",BuiltInRegistries.ITEM.getKey(ITEMS.getLast()).toString());
        tome.set(DataComponents.CUSTOM_DATA,CustomData.of(data));((Container)level.getBlockEntity(shelf)).setItem(0,tome);
    }
    private static void observer(){
        observer=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"deep-craft-stress"));observer.setPos(17.5,68,16.5);
        observer.connection=new ServerGamePacketListenerImpl(server,observer.connection.getConnection(),observer,CommonListenerCookie.createInitial(observer.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){
                if(!(packet instanceof ClientboundCustomPayloadPacket custom)||!(custom.payload() instanceof TransferVisualBatch batch))return;
                var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
                try{TransferVisualBatch.CODEC.encode(buffer,batch);packetBytes+=buffer.readableBytes();var decoded=TransferVisualBatch.CODEC.decode(buffer);packets++;try{for(var visual:decoded.visuals())observe(visual);}catch(Throwable error){observerFailure=error;}}
                finally{buffer.release();}
            }
            @Override public void send(Packet<?> packet,PacketSendListener listener){send(packet);}
        };level.players().add(observer);
    }
    private static void observe(NetworkPackets.Visual visual){
        if(!requested||visual.stack().isEmpty())return;int stage=ITEMS.indexOf(visual.stack().getItem());
        if(visual.slot()==-1&&STATIONS.containsKey(visual.to())){
            if(visual.stack().is(Items.COAL)){fuelFlights++;return;}
            check(stage>=0&&stage<12,"Unexpected material "+visual.stack()+" delivered to station "+visual.to()+" from "+visual.from());int next=stage+1;
            check(STATIONS.get(visual.to())==(next-1)%5,"Material delivered to wrong machine type, stage "+next);
            if(stage>0){check(STATIONS.containsKey(visual.from())&&STATIONS.get(visual.from())==(stage-1)%5,"Intermediate did not leave its actual preceding processor");if(visual.path().size()==2)directMachineFlights++;}
            check(ARRIVALS.put(visual.to(),new Arrival(next,server.getTickCount()+visual.duration()))==null,"Machine received overlapping owned deliveries");
            flights++;if(visual.path().size()>2)relayedFlights++;
        }else if(visual.slot()==9&&stage>0){checkArrival(visual.to(),stage);}
    }
    private static void checkArrival(BlockPos pos,int stage){
        Arrival arrival=ARRIVALS.remove(pos);check(arrival!=null&&arrival.stage()==stage,"Processing without its ingredient delivery at "+pos+" stage "+stage);
        check(server.getTickCount()>=arrival.due(),"Processor started before its input arrived at "+pos+" by "+(arrival.due()-server.getTickCount())+" ticks");STARTED[stage]++;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beginTick(ServerTickEvent.Pre event){if(ACTIVE&&ready&&!done)pre=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void postTick(ServerTickEvent.Post event){if(ACTIVE&&ready&&!done)post=System.nanoTime();}
    public static void finishTick(ServerTickEvent.Post event){
        if(!ACTIVE||!ready||done)return;long now=System.nanoTime();
        try{
            if(observerFailure!=null)throw new AssertionError("Visual ordering check failed",observerFailure);
            ticks++;check(ticks<TIMEOUT,"Deep craft timed out: "+(network==null?"network discovery":network.crafting().statuses()));
            if(!requested){prepareRequests();return;}
            var service=network.crafting();long totalCompleted=0;
            for(var status:service.statuses()){
                check(!Set.of("FAILED","CANCELLED","MISSING").contains(status.state()),"Order "+status.id()+" became "+status.state()+": "+status.message());
                if(status.total()>0&&!PLANNED.containsKey(status.id())){PLANNED.put(status.id(),server.getTickCount()-requestedTick);AstralRepository.LOGGER.info("Deep craft plan {} ready in {} ticks: {} operations",status.id(),PLANNED.get(status.id()),status.total());}
                if(status.state().equals("COMPLETE")){check(status.completed()==COUNT*12&&status.total()==COUNT*12,"Completed order skipped processing stages");COMPLETED.add(status.id());}
                totalCompleted+=status.completed();
            }
            inspectJobs(service);inspectFurnaces();check(conserved(service)==(long)ORDERS*COUNT,"Craft escrow + physical inventory conservation failed");
            int active=service.activeProcessors();maxProcessors=Math.max(maxProcessors,active);TIMES.add(now-pre);POST_TIMES.add(now-post);
            CSV.append(String.format(Locale.ROOT,"%d,%.6f,%.6f,%d,%d,%d%n",ticks,(now-pre)/1e6,(now-post)/1e6,service.activeJobs(),active,totalCompleted));
            if(ticks%200==0)AstralRepository.LOGGER.info("Deep craft progress: {} / {} operations, {} completed orders, {} active machines; {}",totalCompleted,ORDERS*COUNT*12,COMPLETED.size(),active,service.statuses().stream().map(s->s.id().toString().substring(0,8)+":"+s.state()+":"+s.completed()+"/"+s.total()).toList());
            if(COMPLETED.size()==ORDERS&&service.activeJobs()==0){if(++settleTicks>=20)finish();}else settleTicks=0;
        }catch(Throwable error){fail(error);}
    }
    private static void prepareRequests(){
        var manager=NetworkManager.get(server);var candidate=manager.networkAt(NODES.getFirst().address());if(candidate==null)return;
        if(NODES.stream().anyMatch(node->manager.networkAt(node.address())!=candidate)||!candidate.workstations().containsAll(STATIONS.keySet().stream().map(DedicatedDeepCraftStress::at).toList())||candidate.exposedProducts().isEmpty()||candidate.snapshot().getOrDefault(new ItemKey(new ItemStack(ITEMS.getFirst())),0L)!=(long)ORDERS*COUNT)return;
        network=candidate;check(network.travelTicks(at(STATIONS.keySet().iterator().next()),at(new BlockPos(61,64,14)))>20,"Fixture must exercise actual relay hops");
        requestedTick=server.getTickCount();startedNanos=System.nanoTime();
        var menu=new com.cappleapple.astralrepository.menu.NexusMenu(1,observer.getInventory(),NODES.getFirst().address().position(),false);observer.containerMenu=menu;
        check(menu.stillValid(observer),"Player cannot reach the Nexus request menu");
        for(int i=0;i<ORDERS;i++){
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
            try{NetworkPackets.Action.CODEC.encode(buffer,new NetworkPackets.Action(menu.containerId,NetworkPackets.CRAFT,new ItemStack(ITEMS.getLast()),COUNT,0,""));menu.action(NetworkPackets.Action.CODEC.decode(buffer));}
            finally{buffer.release();}
            check(network.crafting().activeJobs()==i+1,"Nexus CRAFT action was rejected: "+menu.error);
        }
        check(network.crafting().statuses().stream().filter(s->s.state().equals("CALCULATING")).count()==ORDERS,"Orders must immediately appear as calculating");requested=true;
    }
    private static void inspectFurnaces(){
        for(var entry:STATIONS.entrySet())if(level.getBlockEntity(entry.getKey()) instanceof AbstractFurnaceBlockEntity furnace){
            ItemStack input=furnace.getItem(0);Item previous=FURNACE_INPUTS.get(entry.getKey());
            if(!input.isEmpty()&&previous==null){int stage=ITEMS.indexOf(input.getItem())+1;check(stage>0,"Unexpected furnace input");checkArrival(entry.getKey(),stage);}
            if(input.isEmpty())FURNACE_INPUTS.remove(entry.getKey());else FURNACE_INPUTS.put(entry.getKey(),input.getItem());
        }
    }
    @SuppressWarnings("unchecked") private static void inspectJobs(CraftingService service)throws Exception{
        for(var entry:((Map<UUID,?>)JOBS.get(service)).entrySet()){
            var scheduler=(CraftScheduler<ItemKey,GlobalPos>)access(entry.getValue(),"scheduler");var plan=(CraftPlan<ItemKey>)PLAN.get(scheduler);var completed=(Set<Integer>)DONE.get(scheduler);
            if(INSPECTED.add(entry.getKey())){
                check(plan.nodes().size()==COUNT*12,"Plan must contain every requested operation");int[] depths=new int[plan.nodes().size()];int[] stages=new int[13];
                for(var node:plan.nodes()){int stage=ITEMS.indexOf(node.recipe().output().sample().getItem());check(stage>0&&node.recipe().id().startsWith("astral_stress:"),"Planner selected an unexpected recipe");stages[stage]++;check(node.recipe().process().equals(PROCESSES[(stage-1)%5]),"Wrong process in planned dependency");int depth=1;for(int dependency:node.dependencies()){check(dependency<node.id(),"Plan dependency is not ordered");depth=Math.max(depth,depths[dependency]+1);}depths[node.id()]=depth;dependencyDepth=Math.max(dependencyDepth,depth);}
                for(int stage=1;stage<=12;stage++)check(stages[stage]==COUNT,"Stage operation count changed");check(dependencyDepth==12,"Recipe graph did not reach 12 dependency layers");
            }
            for(int id:completed)check(completed.containsAll(plan.nodes().get(id).dependencies()),"Completed operation preceded an ingredient dependency");
            for(var running:((Map<Integer,?>)RUNNING.get(scheduler)).values()){var node=(CraftPlan.Node<ItemKey>)access(running,"node");check(completed.containsAll(node.dependencies()),"Running operation preceded an ingredient dependency");}
        }
    }
    @SuppressWarnings("unchecked") private static long conserved(CraftingService service)throws Exception{
        long count=0;for(var store:STORES)for(int slot=0;slot<store.inventory().getSlots();slot++)count+=units(store.inventory().getStackInSlot(slot));
        for(var pos:STATIONS.keySet())if(level.getBlockEntity(pos) instanceof Container c)for(int slot=0;slot<c.getContainerSize();slot++)count+=units(c.getItem(slot));
        for(var job:((Map<UUID,?>)JOBS.get(service)).values()){var scheduler=(CraftScheduler<ItemKey,GlobalPos>)access(job,"scheduler");for(var entry:scheduler.recoverySnapshot().entrySet())if(ITEMS.contains(entry.getKey().sample().getItem()))count+=entry.getValue();}
        return count;
    }
    private static long units(ItemStack stack){return !stack.isEmpty()&&ITEMS.contains(stack.getItem())?stack.getCount():0;}
    private static void finish()throws Exception{
        check(dependencyDepth==12&&INSPECTED.size()==ORDERS,"Every deep order must be inspected");
        long outputs=0;for(var store:STORES)for(int slot=0;slot<store.inventory().getSlots();slot++){var stack=store.inventory().getStackInSlot(slot);if(stack.is(ITEMS.getLast()))outputs+=stack.getCount();else check(units(stack)==0,"Unconsumed raw/intermediate material remains");}
        check(outputs==(long)ORDERS*COUNT,"Final storage did not receive every requested output");
        for(int stage=1;stage<=12;stage++)check(STARTED[stage]==(long)ORDERS*COUNT,"Stage "+stage+" did not visibly/physically start exactly once per output: "+STARTED[stage]);
        check(ARRIVALS.isEmpty()&&relayedFlights>0&&directMachineFlights>0&&fuelFlights>0,"Missing real relay/direct/fuel delivery coverage");
        for(var pos:STATIONS.keySet())check(!CraftingService.isProcessorReserved(at(pos)),"Completed order retained a machine lock");
        double elapsed=(System.nanoTime()-startedNanos)/1e9;writeMetrics(elapsed);long[] sorted=TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
        check(pct(sorted,.95)<50_000_000,"Deep crafting p95 exceeded the 50 ms tick budget");check(TIMES.size()/elapsed>=19.5,"Deep crafting sustained TPS below 19.5");
        result="PASS: "+ORDERS+" concurrent orders x "+COUNT+" outputs, 12 recipe layers, five actual machine types; "+(ORDERS*COUNT*12)+" operations completed; dependency ordering, actual arrival before processing, fuel deliveries, direct/relay paths and exact material conservation verified.";
        Files.writeString(OUT.resolve("result.txt"),result);done=true;level.players().remove(observer);server.halt(false);
    }
    private static void writeMetrics(double elapsed)throws Exception{
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("ticks.csv"),CSV);
        String report=String.format(Locale.ROOT,"Isolated dedicated server; %d concurrent orders of %d outputs; 12 recipe stages; crafting table, furnace, blast furnace, smoker and stonecutter; 8 machines of each type. Custom recipes cook for 4 ticks; normal table/stonecutter delays and real route flight waits retained. No default budgets or parallelism overridden.\nSamples=%d elapsed=%.3fs achieved_tps=%.3f depth=%d max_active_processors=%d\nserver_work_ms %s\npost_tick_work_ms %s\nplanning_latency_ticks=%s started_per_stage=%s\nflights=%d relayed_flights=%d direct_machine_flights=%d fuel_flights=%d codec_batches=%d codec_bytes=%d\nTiming starts at Pre and ends after all normal Post listeners including network and visual dispatch. Verification is outside work samples but included in elapsed TPS. Observer consumes real codec bytes without client or socket traffic.\n",ORDERS,COUNT,TIMES.size(),elapsed,TIMES.size()/Math.max(.001,elapsed),dependencyDepth,maxProcessors,stats(TIMES),stats(POST_TIMES),PLANNED,Arrays.toString(STARTED),flights,relayedFlights,directMachineFlights,fuelFlights,packets,packetBytes);
        String mods=net.neoforged.fml.ModList.get().getMods().stream().map(mod->mod.getModId()+"@"+mod.getVersion()).sorted().toList().toString();
        report="Loaded mods: "+mods+"\nJava: "+System.getProperty("java.version")+"; processors="+Runtime.getRuntime().availableProcessors()+"; max_heap_bytes="+Runtime.getRuntime().maxMemory()+"\nRequest entry: real NexusMenu.action after NetworkPackets.Action codec round trip; CRAFT128 by default.\n"+report;
        Files.writeString(OUT.resolve("metrics.txt"),report);AstralRepository.LOGGER.info(report);
    }
    private static GlobalPos at(BlockPos pos){return GlobalPos.of(level.dimension(),pos);}
    private static Field field(Class<?> type,String name){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static Object access(Object target,String method)throws Exception{var m=target.getClass().getDeclaredMethod(method);m.setAccessible(true);return m.invoke(target);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static String stats(List<Long> values){if(values.isEmpty())return "no samples";long[] sorted=values.stream().mapToLong(Long::longValue).sorted().toArray();return String.format(Locale.ROOT,"median=%.4f p95=%.4f p99=%.4f max=%.4f over50ms=%d",pct(sorted,.5)/1e6,pct(sorted,.95)/1e6,pct(sorted,.99)/1e6,sorted[sorted.length-1]/1e6,Arrays.stream(sorted).filter(n->n>50_000_000).count());}
    private static long pct(long[] sorted,double p){return sorted[Math.min(sorted.length-1,(int)Math.ceil(p*sorted.length)-1)];}
    private static void fail(Throwable error){done=true;result="FAIL: "+error;AstralRepository.LOGGER.error("Deep crafting stress failed",error);try{writeMetrics(requested?(System.nanoTime()-startedNanos)/1e9:0);Files.writeString(OUT.resolve("result.txt"),result);}catch(Exception ignored){}if(level!=null&&observer!=null)level.players().remove(observer);if(server!=null)server.halt(false);}
}
