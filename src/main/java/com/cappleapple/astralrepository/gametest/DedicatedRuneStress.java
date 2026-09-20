package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.content.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Real server tick workload, opt-in and excluded from the distributable JAR. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID,value=Dist.DEDICATED_SERVER)
public final class DedicatedRuneStress {
    private static final boolean ACTIVE=Boolean.getBoolean("astral_repository.runeStress");
    private static final Path OUT=Path.of(System.getProperty("astral_repository.runeStressOutput","../build/rune-stress"));
    private static final int WARMUP=Integer.getInteger("astral_repository.runeStressWarmup",200);
    private static final int SAMPLES=Integer.getInteger("astral_repository.runeStressSamples",600);
    private static final int OBSERVERS=Integer.getInteger("astral_repository.runeStressObservers",0);
    private static RuneStressObservers observers;
    private static final int STACK=64,FLUID=1000,ENERGY=10000,SOURCE=1000;
    private static final Map<BlockPos,Media> MEDIA=new HashMap<>();
    private static final List<Container> CONTAINERS=new ArrayList<>();
    private static final List<Work> PRIMARY=new ArrayList<>(),STACKED=new ArrayList<>(),BLOCKED=new ArrayList<>();
    private static final List<Work> ALL=new ArrayList<>();
    private static final List<String> SUMMARIES=new ArrayList<>();
    private static final StringBuilder CSV=new StringBuilder("phase,sample,server_work_ms,post_tick_work_ms,verification_ms\n");
    private static final Item[] ITEMS={Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT,Items.REDSTONE,Items.LAPIS_LAZULI,Items.QUARTZ,Items.COAL,Items.DIAMOND};
    private static final Phase[] PHASES={new Phase("idle",0,false,false),new Phase("256_busy",256,false,false),new Phase("1024_busy",1024,false,false),new Phase("1216_busy_256_blocked",1024,true,true)};
    private static final List<Long> SERVER_TIMES=new ArrayList<>(),POST_TIMES=new ArrayList<>(),VERIFY_TIMES=new ArrayList<>();
    private static Map<ItemKey,Long> expectedItems;
    private static boolean ready,done;
    private static int phase=-1,ticks;
    private static long preStarted,postStarted,sampleStarted;
    private static String result;
    private record Phase(String name,int primary,boolean stacked,boolean blocked){}
    private static final class Work {
        final RuneLayer rune; final boolean busy; final Media media; boolean scheduled; long items,fluid;
        Work(RuneLayer rune,boolean busy,Media media){this.rune=rune;this.busy=busy;this.media=media;}
    }

    @EventBusSubscriber(modid=AstralRepository.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.DEDICATED_SERVER)
    public static final class CapabilitiesForFixture {
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent event){
            if(!ACTIVE)return;
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,BlockEntityType.BARREL,(be,side)->{Media m=MEDIA.get(be.getBlockPos());return m==null?null:m.tank;});
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,BlockEntityType.BARREL,(be,side)->{Media m=MEDIA.get(be.getBlockPos());return m==null?null:m.energy;});
        }
    }

    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!ACTIVE)return;
        MinecraftServer server=event.getServer();
        try{
            Files.createDirectories(OUT);check(server.isDedicatedServer(),"Requires a real dedicated server");
            check(WARMUP>=40&&SAMPLES>=40,"Warmup and sample windows must each contain at least 40 ticks");
            ServerLevel level=server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
            level.setWeatherParameters(100000,0,false,false);
            // Only this opt-in fixture creates/forces chunks. Production discovery remains loaded-world only.
            for(int x=0;x<8;x++)for(int z=0;z<5;z++){level.getChunk(x,z);level.setChunkForced(x,z,true);}
            CompatibilityRegistry.registerResources("rune_stress_source",(world,pos,side)->{
                Media m=world==level?MEDIA.get(pos):null;return m==null?List.of():List.of(m.source);
            });
            for(int pair=0;pair<640;pair++)createPair(level,pair);
            ALL.addAll(PRIMARY);ALL.addAll(STACKED);ALL.addAll(BLOCKED);
            check(PRIMARY.size()==1024&&STACKED.size()==192&&BLOCKED.size()==256,"Incorrect fixture population");
            expectedItems=itemTotals();observers=new RuneStressObservers(level,OBSERVERS,new BlockPos(64,66,40));ready=true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,DedicatedRuneStress::post);
            advance(server);
            AstralRepository.LOGGER.info("Rune stress ready: {} containers, {} rune layers, {} compound fluid/energy/Source hosts; codec-consuming observer count "+OBSERVERS+"; visuals retain configured defaults",CONTAINERS.size(),ALL.size(),MEDIA.size());
        }catch(Throwable failure){fail(server,failure);}
    }

    private static void createPair(ServerLevel level,int pair){
        BlockPos a=new BlockPos((pair%32)*4,64,(pair/32)*4),b=a.east(2);
        boolean compound=pair<32,blocked=pair>=512;
        int blockedKind=(pair-512)%3;
        BlockState state=compound?Blocks.BARREL.defaultBlockState():switch(pair%6){
            case 0->Blocks.CHEST.defaultBlockState();case 1->Blocks.BARREL.defaultBlockState();
            case 2->Blocks.DROPPER.defaultBlockState();case 3->Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.ENABLED,false);
            case 4->Blocks.SHULKER_BOX.defaultBlockState();default->Blocks.TRAPPED_CHEST.defaultBlockState();
        };
        for(BlockPos pos:List.of(a,b)){
            for(var surface:RuneSurfaces.at(level,pos))RuneSurfaces.remove(level,pos,surface.facing());
            if(level.getBlockEntity(pos) instanceof Container previous)previous.clearContent();
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos,state);
            check(level.getBlockEntity(pos) instanceof Container,"Missing vanilla container at "+pos);
            Container inventory=(Container)level.getBlockEntity(pos);CONTAINERS.add(inventory);
            if(compound)MEDIA.put(pos,new Media(pos));
            if(!blocked||blockedKind!=0){
                int variants=compound?4:1;
                for(int layer=0;layer<variants;layer++){
                    ItemStack sample=sample(pair,layer);
                    inventory.setItem(layer*2,sample.copyWithCount(STACK));inventory.setItem(layer*2+1,sample.copyWithCount(STACK));
                }
                if(blocked&&blockedKind==1)for(int slot=0;slot<inventory.getContainerSize();slot++)inventory.setItem(slot,sample(pair,0).copyWithCount(STACK));
            }
        }
        for(int side=0;side<2;side++){
            BlockPos from=side==0?a:b,to=side==0?b:a;
            RuneSurface surface=RuneSurfaces.getOrCreate(level,from,Direction.SOUTH);
            for(int layer=0;layer<(compound?4:1);layer++){
                var mode=(pair&1)==0?RuneLayer.Mode.PUSH:RuneLayer.Mode.PULL;
                RuneLayer rune=surface.addLayer(RuneGlyph.id(mode),mode);check(rune!=null,"Could not place fixture rune");
                rune.setEnabled(false);rune.setPriority(pair%11-5);
                var cadence=RuneCadence.DEFAULT.with(RuneCadence.Kind.ITEMS,new RuneCadence.Rate(STACK,1))
                    .with(RuneCadence.Kind.FLUID,new RuneCadence.Rate(FLUID,1)).with(RuneCadence.Kind.ENERGY,new RuneCadence.Rate(ENERGY,1))
                    .with(RuneCadence.Kind.SOURCE,new RuneCadence.Rate(SOURCE,1));
                rune.setCadence(cadence);
                ItemStack item=blocked&&blockedKind==2?new ItemStack(Items.STICK):sample(pair,layer);
                rune.filter().add(FilterRules.Kind.ITEM,BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),false,item);
                if(compound)rune.filter().add(FilterRules.Kind.FLUID,"minecraft:water",false,ItemStack.EMPTY);
                rune.changed();
                var assignment=surface.toggleTarget(rune.id(),GlobalPos.of(level.dimension(),to),Direction.SOUTH);
                check(assignment.assigned(),"Fixture binding failed: "+assignment.message());
                Work work=new Work(rune,!blocked,MEDIA.get(from));
                if(blocked)BLOCKED.add(work);else if(layer==0)PRIMARY.add(work);else STACKED.add(work);
            }
        }
    }
    private static ItemStack sample(int pair,int layer){return new ItemStack(ITEMS[(pair+layer)%ITEMS.length]);}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre event){if(ACTIVE&&ready&&!done)preStarted=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void postStart(ServerTickEvent.Post event){if(ACTIVE&&ready&&!done)postStarted=System.nanoTime();}
    public static void post(ServerTickEvent.Post event){
        if(!ACTIVE||!ready||done)return;
        long now=System.nanoTime(),serverNanos=now-preStarted,postNanos=now-postStarted;
        MinecraftServer server=event.getServer();
        try{
            ticks++;
            boolean sample=ticks>WARMUP;
            for(Work work:ALL){
                long items=work.rune.transferredItems(),fluid=work.rune.transferredFluid();
                if(sample){
                    check(work.rune.enabled()==work.scheduled,"Rune unexpectedly disabled: "+work.rune.id()+"; "+work.rune.status());
                    long expected=work.busy&&work.scheduled?STACK:0;
                    check(items-work.items==expected,"Cadence dropped/duplicated on "+work.rune.id()+" at "+work.rune.surface().getBlockPos()+": expected "+expected+" items, received "+(items-work.items)+"; "+work.rune.status());
                    long expectedFluid=expected>0&&work.media!=null?FLUID:0;
                    check(fluid-work.fluid==expectedFluid,"Fluid cadence dropped/duplicated on "+work.rune.id());
                }
                work.items=items;work.fluid=fluid;
            }
            for(Media media:MEDIA.values())media.verify(sample);
            if(ticks%20==0||ticks==WARMUP+SAMPLES)check(itemTotals().equals(expectedItems),"Actual inventory contents were not conserved");
            long verification=System.nanoTime()-now;
            if(ticks==WARMUP)sampleStarted=System.nanoTime();
            if(sample){
                SERVER_TIMES.add(serverNanos);POST_TIMES.add(postNanos);VERIFY_TIMES.add(verification);
                CSV.append(String.format(Locale.ROOT,"%s,%d,%.6f,%.6f,%.6f%n",PHASES[phase].name,ticks-WARMUP,serverNanos/1e6,postNanos/1e6,verification/1e6));
            }
            if(ticks>=WARMUP+SAMPLES){
                summarize((System.nanoTime()-sampleStarted)/1e9);advance(server);
            }
        }catch(Throwable failure){fail(server,failure);}
    }

    private static void advance(MinecraftServer server)throws Exception{
        if(++phase==PHASES.length){
            done=true;result="PASS: dedicated server rune stress; every busy rune transferred 64 real items every sampled tick; all four media conserved; "+SAMPLES+" measured ticks per stage after "+WARMUP+" warmup ticks; vanilla inventories and real NeoForge fluid/energy capabilities, Source adapter fixtures.";
            write();observers.close();server.halt(false);return;
        }
        Phase current=PHASES[phase];
        for(Work work:ALL)work.rune.setEnabled(false);
        for(int i=0;i<current.primary;i++)PRIMARY.get(i).rune.setEnabled(true);
        if(current.stacked)for(Work work:STACKED)work.rune.setEnabled(true);
        if(current.blocked)for(Work work:BLOCKED)work.rune.setEnabled(true);
        for(Work work:ALL)work.scheduled=work.rune.enabled();
        for(Media media:MEDIA.values())media.active=0;
        for(Work work:ALL)if(work.rune.enabled()&&work.media!=null)work.media.active++;
        ticks=0;SERVER_TIMES.clear();POST_TIMES.clear();VERIFY_TIMES.clear();observers.reset();
        AstralRepository.LOGGER.info("Rune stress stage {}: {} busy runes, {} blocked runes; 1 tick cadence",current.name,current.primary+(current.stacked?STACKED.size():0),current.blocked?BLOCKED.size():0);
    }
    private static void summarize(double elapsed)throws Exception{
        Phase current=PHASES[phase];int busy=current.primary+(current.stacked?STACKED.size():0);
        String summary=String.format(Locale.ROOT,"%s: busy=%d blocked=%d samples=%d elapsed=%.3fs achieved_tps=%.3f items_transferred=%d; server_work_ms %s; post_tick_work_ms %s; verification_ms %s",current.name,busy,current.blocked?BLOCKED.size():0,SAMPLES,elapsed,SAMPLES/elapsed,(long)busy*STACK*SAMPLES,stats(SERVER_TIMES),stats(POST_TIMES),stats(VERIFY_TIMES));
        if(OBSERVERS>0&&busy>0)check(observers.batches()>0,"Active rune observers received no animation packets");
        summary+="; "+observers.summary();
        SUMMARIES.add(summary);AstralRepository.LOGGER.info(summary);write();
        long[] ordered=SERVER_TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
        check(percentile(ordered,.95)<50_000_000,"Server work p95 exceeds the 50 ms tick budget");
        check(SAMPLES/elapsed>=19.5,"Sustained TPS fell below 19.5");
    }
    private static String stats(List<Long> values){
        long[] sorted=values.stream().mapToLong(Long::longValue).sorted().toArray();
        return String.format(Locale.ROOT,"median=%.4f p95=%.4f p99=%.4f max=%.4f over50ms=%d",percentile(sorted,.5)/1e6,percentile(sorted,.95)/1e6,percentile(sorted,.99)/1e6,sorted[sorted.length-1]/1e6,Arrays.stream(sorted).filter(v->v>50_000_000).count());
    }
    private static long percentile(long[] sorted,double percentile){return sorted[Math.min(sorted.length-1,(int)Math.ceil(sorted.length*percentile)-1)];}
    private static Map<ItemKey,Long> itemTotals(){
        Map<ItemKey,Long> result=new HashMap<>();
        for(Container container:CONTAINERS)for(int slot=0;slot<container.getContainerSize();slot++){
            ItemStack stack=container.getItem(slot);if(!stack.isEmpty())result.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);
        }
        return result;
    }
    private static void write()throws Exception{
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("ticks.csv"),CSV);
        Files.writeString(OUT.resolve("metrics.txt"),"Real dedicated server; "+System.getProperty("java.version")+"; "+Runtime.getRuntime().availableProcessors()+" logical processors\n"
            +"1280 vanilla containers; 1024 primary + 192 stacked busy + 256 blocked rune layers; 64 compound resource hosts.\n"
            +"Pre(HIGHEST) to Post(LOWEST) timing includes actual network work omitted by vanilla's pre-Post tick timing. Verification is recorded separately; elapsed TPS includes verification. "+OBSERVERS+" fake observers encode/decode/discard actual visual batches; no connected clients or socket traffic. No artificial transfer throttling or fake transfer offers.\n"
            +"Source uses the public resource adapter API; fluid and energy use real NeoForge capability handlers attached only to isolated fixture barrels. This is not an Ars Nouveau or Sophisticated Storage integration benchmark.\n"
            +String.join("\n",SUMMARIES)+"\n");
        if(result!=null)Files.writeString(OUT.resolve("result.txt"),result);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void fail(MinecraftServer server,Throwable failure){
        done=true;result="FAIL: "+failure;AstralRepository.LOGGER.error("Rune stress failed",failure);
        try{write();}catch(Exception ignored){}if(observers!=null)observers.close();server.halt(false);
    }

    private static final class Media {
        final TrackingTank tank=new TrackingTank();final TrackingEnergy energy=new TrackingEnergy();final SourceStorage source;
        final BlockPos pos;int active;long lastFill,lastDrain,lastReceive,lastExtract,lastSourceIn,lastSourceOut;
        Media(BlockPos pos){this.pos=pos;source=new SourceStorage(pos);tank.fill(new FluidStack(Fluids.WATER,8000),FluidTank.FluidAction.EXECUTE);energy.receiveEnergy(500000,false);}
        void verify(boolean sampled){
            if(sampled){
                check(tank.filled-lastFill==(long)active*FLUID&&tank.drained-lastDrain==(long)active*FLUID,"Native fluid handler cadence mismatch at "+pos);
                check(energy.received-lastReceive==(long)active*ENERGY&&energy.extracted-lastExtract==(long)active*ENERGY,"Native energy handler cadence mismatch at "+pos);
                check(source.inserted-lastSourceIn==(long)active*SOURCE&&source.extracted-lastSourceOut==(long)active*SOURCE,"Source handler cadence mismatch at "+pos);
                check(tank.getFluidAmount()==8000&&energy.getEnergyStored()==500000&&source.amount==64000,"Actual fluid/energy/Source quantities were not conserved at "+pos);
            }
            lastFill=tank.filled;lastDrain=tank.drained;lastReceive=energy.received;lastExtract=energy.extracted;lastSourceIn=source.inserted;lastSourceOut=source.extracted;
        }
    }
    private static final class TrackingTank extends FluidTank {
        long filled,drained;TrackingTank(){super(16000);}
        @Override public int fill(FluidStack stack,FluidAction action){int n=super.fill(stack,action);if(action.execute())filled+=n;return n;}
        @Override public FluidStack drain(int n,FluidAction action){FluidStack result=super.drain(n,action);if(action.execute())drained+=result.getAmount();return result;}
    }
    private static final class TrackingEnergy extends EnergyStorage {
        long received,extracted;TrackingEnergy(){super(1000000);}
        @Override public int receiveEnergy(int n,boolean simulate){int result=super.receiveEnergy(n,simulate);if(!simulate)received+=result;return result;}
        @Override public int extractEnergy(int n,boolean simulate){int result=super.extractEnergy(n,simulate);if(!simulate)extracted+=result;return result;}
    }
    private static final class SourceStorage implements ResourceProvider {
        final String id;long amount=64000,inserted,extracted;SourceStorage(BlockPos pos){id="rune_stress_source:"+pos.asLong();}
        public String id(){return id;}public Object identity(){return this;}public ResourceLocation resourceType(){return ResourceKinds.SOURCE;}
        public Map<ResourceKey,Long> snapshot(){return Map.of(ResourceKinds.ARS_SOURCE,amount);}
        public long insert(ResourceKey key,long n,boolean simulate){long accepted=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,capacity()-amount):0;if(!simulate){amount+=accepted;inserted+=accepted;}return accepted;}
        public long extract(ResourceKey key,long n,boolean simulate){long taken=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,amount):0;if(!simulate){amount-=taken;extracted+=taken;}return taken;}
        public long capacity(){return 128000;}public boolean valid(){return true;}
    }
}
