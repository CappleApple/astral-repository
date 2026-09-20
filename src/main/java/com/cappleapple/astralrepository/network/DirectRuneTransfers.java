package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.CraftingService;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Bounded server-thread transfers between one rune layer's two actual sided containers. */
public final class DirectRuneTransfers {
    private static final int SLOT_BUDGET=512,KEY_BUDGET=64,PROVIDER_LIMIT=8;
    private static final RuneCadence.Kind[] RESOURCE_KINDS={RuneCadence.Kind.FLUID,RuneCadence.Kind.ENERGY,RuneCadence.Kind.SOURCE};
    private record Work(RuneSurface surface,RuneLayer layer,RuneTransferSchedule schedule){}
    private static final class ItemView {
        final StorageProvider provider;
        Map<ItemKey,Long> contents=Map.of();
        List<ItemKey> keys=List.of();
        final Map<UUID,Integer> cursors=new LinkedHashMap<>(16,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<UUID,Integer> entry){return size()>256;}};
        boolean ready;
        long polledAt=Long.MIN_VALUE,polledVersion=Long.MIN_VALUE,attemptedVersion=Long.MIN_VALUE;
        ItemView(StorageProvider provider){this.provider=provider;}
        void invalidate(){if(!ready&&polledAt==Long.MIN_VALUE)return;ready=false;contents=Map.of();keys=List.of();polledAt=Long.MIN_VALUE;polledVersion=Long.MIN_VALUE;attemptedVersion=Long.MIN_VALUE;}
        void poll(long now){
            long version=provider.version();
            if(polledAt==now&&(version<0||version==attemptedVersion)||ready&&version>=0&&version==polledVersion)return;
            polledAt=now;attemptedVersion=version;
            provider.poll(SLOT_BUDGET).ifPresent(values->{contents=values;keys=List.copyOf(values.keySet());ready=true;polledVersion=version;});
        }
        ItemKey next(UUID layer){if(keys.isEmpty())return null;int index=Math.floorMod(cursors.getOrDefault(layer,0),keys.size());cursors.put(layer,(index+1)%keys.size());return keys.get(index);}
    }
    private static final class Endpoint {
        final RuneLayer.Target target;
        final List<ItemView> items;
        final List<ResourceProvider> fluids;
        final Set<Object> identities;
        final long discovered;
        long lastUsed;
        Endpoint(ServerLevel level,RuneLayer.Target target){
            this.target=target;discovered=level.getGameTime();
            items=CompatibilityRegistry.discoverStorage(level,target.position().pos(),target.face()).stream().limit(PROVIDER_LIMIT).map(ItemView::new).toList();
            Set<Object> fluidIds=new HashSet<>();
            fluids=CompatibilityRegistry.discoverResources(level,target.position().pos(),target.face()).stream().filter(p->(p.resourceType().equals(ResourceKinds.FLUID)||p.resourceType().equals(ResourceKinds.ENERGY)||p.resourceType().equals(ResourceKinds.SOURCE))&&fluidIds.add(List.of(p.resourceType(),p.identity()))).limit(PROVIDER_LIMIT).toList();
            Set<Object> backing=new HashSet<>();for(ItemView item:items)backing.add(item.provider.identity());for(ResourceProvider fluid:fluids)backing.add(fluid.identity());
            identities=Collections.unmodifiableSet(backing);
        }
        Endpoint(ServerLevel level,RuneLayer.Target target,Endpoint home,RuneLayer layer){
            this.target=target;discovered=level.getGameTime();var network=NetworkManager.get(level.getServer()).networkAt(target.position());
            if(network==null){items=List.of();fluids=List.of();identities=Set.of();return;}
            Set<Object> excluded=home.identities;identities=Set.of(network);
            var visualEndpoint=TransferVisuals.rune(layer);
            items=List.of(new ItemView(network.runeItems(home.target.position(),excluded,visualEndpoint)));fluids=List.of(ResourceKinds.FLUID,ResourceKinds.ENERGY,ResourceKinds.SOURCE).stream().map(kind->network.runeResources(home.target.position(),excluded,visualEndpoint,kind)).toList();
        }
        boolean valid(){for(int i=0;i<items.size();i++)if(!items.get(i).provider.valid())return false;for(int i=0;i<fluids.size();i++)if(!fluids.get(i).valid())return false;return true;}
    }
    private final MinecraftServer server;
    private long clock;
    private final RuneTransitData transit;
    private final Map<UUID,Work> known=new HashMap<>();
    private final ArrayDeque<UUID> queue=new ArrayDeque<>();
    private final Map<GlobalPos,List<Endpoint>> endpointsByPosition=SpatialHash.positions();
    private final Map<Object,List<ItemView>> itemsByIdentity=new HashMap<>();
    private final Map<RuneLayer.Target,Endpoint> endpoints=new LinkedHashMap<>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<RuneLayer.Target,Endpoint> eldest){
            // Retain the loaded rune working set, including every configured target.
            // A fixed 4096-entry cap repeatedly rediscovered providers in large installations.
            if(size()<=Math.max(4096L,(long)known.size()*(RuneLayer.MAX_TARGETS+1)))return false;
            unindex(eldest.getValue());return true;
        }
    };
    private RuneTransferSchedule.Limits cadenceLimits;
    private int itemBudget,fluidBudget;
    private List<GlobalPos> activeRoute=List.of();
    private final Map<UUID,Map<Object,Integer>> fluidCursors=new HashMap<>();
    public DirectRuneTransfers(MinecraftServer server){this.server=server;transit=RuneTransitData.get(server);clock=server.getTickCount()-1L;}
    public void track(RuneSurface surface){
        for(RuneLayer layer:surface.layers()){
            Work previous=known.get(layer.id());
            known.put(layer.id(),new Work(surface,layer,previous==null?new RuneTransferSchedule():previous.schedule));
            if(previous==null)queue.add(layer.id());
        }
    }
    public static String targetError(RuneSurface surface,RuneLayer.Target target){
        if(surface.isRemoved())return "The rune host is no longer loaded.";
        if(!surface.address().position().dimension().equals(target.position().dimension()))return "Push/Pull targets must be in the same dimension.";
        if(surface.getBlockPos().equals(target.position().pos()))return "Choose a different container.";
        ServerLevel level=surface.getLevel();if(!level.hasChunkAt(target.position().pos()))return "Target chunk is not loaded.";
        if(level.getBlockEntity(target.position().pos())==null)return "Target must be a container or tank.";
        if(target.face()==null)return level.getBlockEntity(target.position().pos()) instanceof CrystalNodeBlockEntity?null:"Network endpoint is no longer a crystal.";
        Endpoint host=new Endpoint(level,new RuneLayer.Target(surface.address().position(),surface.facing())),other=new Endpoint(level,target);
        boolean items=!host.items.isEmpty()&&!other.items.isEmpty(),fluids=host.fluids.stream().anyMatch(a->other.fluids.stream().anyMatch(b->a.resourceType().equals(b.resourceType())));
        if(!items&&!fluids)return "These clicked faces do not expose a compatible resource storage.";
        if(items)for(ItemView a:host.items)for(ItemView b:other.items)if(a.provider.identity().equals(b.provider.identity()))return "Both faces refer to the same inventory.";
        if(fluids)for(ResourceProvider a:host.fluids)for(ResourceProvider b:other.fluids)if(a.resourceType().equals(b.resourceType())&&a.identity().equals(b.identity()))return "Both faces refer to the same resource storage.";
        return null;
    }
    public void tick(){
        clock=Math.max(clock+1,server.getTickCount());
        transit.tick(clock,this::arriveSafely);
        if(LogisticsTiming.automaticDue(server.getTickCount()))retryRecovery();
        // Cadence is per rune, independent of the inventory reconciliation budget. Every loaded
        // due rune gets its turn; round-robin ordering still breaks ties between equal priorities.
        cadenceLimits=RuneTransferSchedule.Limits.capture(cadenceLimits);
        int count=queue.size();List<Work> batch=new ArrayList<>(count);
        Map<RuneSurface,Boolean> available=new IdentityHashMap<>();
        for(int i=0;i<count;i++){
            UUID id=queue.remove();Work work=known.get(id);RuneLayer layer=work==null?null:work.layer;
            if(layer==null||work.surface.get(id)!=layer||!available.computeIfAbsent(work.surface,surface->!surface.isRemoved())){known.remove(id);fluidCursors.remove(id);continue;}
            queue.add(id);if(layer.enabled()&&layer.mode()!=RuneLayer.Mode.FILTER)batch.add(work);
        }
        batch.sort(Comparator.comparingInt((Work w)->-w.layer.priority()));
        for(Work work:batch){RuneLayer layer=work.layer;layer.beginReport();try{transfer(work);}catch(RuntimeException failure){layer.setEnabled(false);layer.report("Provider failed; paused");}finally{layer.endReport();}}
        if(!queue.isEmpty())queue.add(queue.remove());
        pruneEndpoints();
    }
    private boolean loaded(GlobalPos pos){ServerLevel level=server.getLevel(pos.dimension());return level!=null&&level.hasChunkAt(pos.pos());}
    private Endpoint endpoint(RuneLayer.Target target){
        Endpoint result=endpoints.get(target);if(result==null||!result.valid()||result.items.isEmpty()&&result.fluids.isEmpty()&&server.overworld().getGameTime()-result.discovered>=20){
            ServerLevel level=server.getLevel(target.position().dimension());Endpoint replacement=new Endpoint(level,target);
            if(result!=null)unindex(result);index(replacement);endpoints.put(target,replacement);result=replacement;
        }result.lastUsed=clock;return result;
    }
    private void pruneEndpoints(){
        var iterator=endpoints.entrySet().iterator();
        for(int i=0;i<256&&iterator.hasNext();i++){
            Endpoint endpoint=iterator.next().getValue();
            if(clock-endpoint.lastUsed<200)break;
            iterator.remove();unindex(endpoint);
        }
    }
    private void index(Endpoint endpoint){
        addIndexed(endpointsByPosition,endpoint.target.position(),endpoint);
        for(int i=0;i<endpoint.items.size();i++){ItemView item=endpoint.items.get(i);addIndexed(itemsByIdentity,item.provider.identity(),item);}
    }
    private void unindex(Endpoint endpoint){
        removeIndexed(endpointsByPosition,endpoint.target.position(),endpoint);
        for(int i=0;i<endpoint.items.size();i++){ItemView item=endpoint.items.get(i);removeIndexed(itemsByIdentity,item.provider.identity(),item);}
    }
    private static <K,V> void addIndexed(Map<K,List<V>> index,K key,V value){
        List<V> values=index.computeIfAbsent(key,ignored->new ArrayList<>(1));
        // Backing keys use equality; each cached endpoint/view is a distinct instance.
        for(int i=0;i<values.size();i++)if(values.get(i)==value)return;
        values.add(value);
    }
    private static <K,V> void removeIndexed(Map<K,List<V>> index,K key,V value){
        List<V> values=index.get(key);if(values==null)return;
        for(int i=0;i<values.size();i++)if(values.get(i)==value){values.remove(i);if(values.isEmpty())index.remove(key);return;}
    }
    private void transfer(Work work){
        RuneSurface surface=work.surface;RuneLayer layer=work.layer;
        if(!layer.enabled()||layer.mode()==RuneLayer.Mode.FILTER)return;
        if(!surface.enabled()){layer.report("Paused");return;}
        int due=work.schedule.due(clock,layer.cadence(),cadenceLimits);
        if(due==0)return;
        RuneLayer.Target linked=layer.nextTarget();if(linked==null){layer.report("Unlinked");return;}
        activeRoute=NetworkManager.get(server).route(surface.address().position(),linked.position(),surface.channel(),com.cappleapple.astralrepository.AstralServerConfig.wandBindingRange.get());
        if(activeRoute.isEmpty()){layer.report("No loaded relay route");return;}
        RuneLayer.Target home=new RuneLayer.Target(surface.address().position(),surface.facing());
        if(home.position().equals(linked.position())){layer.report("Same container");return;}
        if(CraftingService.isProcessorReserved(home.position())||CraftingService.isProcessorReserved(linked.position())){layer.report("Container reserved for crafting");return;}
        Endpoint a=endpoint(home);
        Endpoint b=linked.face()==null?new Endpoint(server.getLevel(linked.position().dimension()),linked,a,layer):endpoint(linked);
        Endpoint source=layer.mode()==RuneLayer.Mode.PUSH?a:b,destination=layer.mode()==RuneLayer.Mode.PUSH?b:a;
        if((source.items.isEmpty()||destination.items.isEmpty())&&(source.fluids.isEmpty()||destination.fluids.isEmpty())){layer.report("Clicked face has no accessible inventory or tank");return;}
        layer.report("Source empty or no matching contents");
        itemBudget=(due&RuneCadence.Kind.ITEMS.bit())!=0?work.schedule.amount(RuneCadence.Kind.ITEMS):0;
        boolean moved=moveItems(layer,source,destination);
        for(var kind:RESOURCE_KINDS)if(layer.enabled()&&(due&kind.bit())!=0){fluidBudget=work.schedule.amount(kind);moved=moveResources(layer,source,destination,kind.resource)||moved;}
        if(moved&&layer.enabled())layer.report("Transferring");

    }
    private boolean moveItems(RuneLayer layer,Endpoint source,Endpoint destination){
        if(itemBudget<=0||source.items.isEmpty()||destination.items.isEmpty())return false;
        // A live identity is enough when no aggregate stock/reserve count is needed.
        // Unsupported, rejected or incomplete hints fall back to the normal bounded snapshot.
        if(source.items.size()==1&&layer.filter().minimum()==0&&layer.filter().target()==Long.MAX_VALUE){
            ItemView from=source.items.getFirst();ItemKey candidate=from.provider.candidate(layer.filter()::matches);
            if(candidate!=null&&(layer.filter().unrestricted()||layer.filter().matches(candidate.sample())))for(ItemView to:destination.items){
                if(from.provider.identity().equals(to.provider.identity()))continue;
                int result=moveItem(layer,source,destination,from,to,candidate,itemBudget);
                if(result!=0)return result>0;
            }
        }
        for(ItemView view:source.items)view.poll(clock);
        if(layer.filter().target()!=Long.MAX_VALUE){for(ItemView view:destination.items)view.poll(clock);if(destination.items.stream().anyMatch(v->!v.ready)){layer.report("Scanning target inventory");return false;}}
        for(ItemView from:source.items){
            if(!from.ready){layer.report("Scanning inventory");continue;}
            for(ItemView to:destination.items){
                if(from.provider.identity().equals(to.provider.identity())){layer.report("Same inventory");continue;}

                int attempts=Math.min(KEY_BUDGET,from.contents.size());
                for(int i=0;i<attempts;i++){
                    ItemKey key=from.next(layer.id());if(key==null||!layer.filter().unrestricted()&&!layer.filter().matches(key.sample()))continue;
                    long available=Math.max(0,itemTotal(source,key)-layer.filter().minimum());
                    long room=layer.filter().target()==Long.MAX_VALUE?Long.MAX_VALUE:Math.max(0,layer.filter().target()-itemTotal(destination,key)-incoming(destination,key));
                    int wanted=(int)Math.min(itemBudget,Math.min(available,room));if(wanted<=0){layer.report(available<=0?"Source reserve reached":"Stock target reached");continue;}
                    int result=moveItem(layer,source,destination,from,to,key,wanted);
                    if(result!=0)return result>0;
                }
            }
        }
        return false;
    }
    /** 1: committed movement; 0: try another identity; -1: stop after uncertain work/exhausted budget. */
    private int moveItem(RuneLayer layer,Endpoint source,Endpoint destination,ItemView from,ItemView to,ItemKey key,int wanted){
        if(!AstralConfig.instantAutomaticLogistics.get())return dispatchItems(layer,source,destination,from.provider,to.provider,key,wanted);
        ItemStack offered=from.provider.extract(key,wanted,true);if(offered.isEmpty())return 0;
        verifyStack(offered,key,wanted);
        ItemStack simulated=to.provider.insert(offered,true);verifyRemainder(simulated,key,offered.getCount());
        int accepting=offered.getCount()-simulated.getCount();if(accepting<=0){layer.report("Target full or rejects matching items");return 0;}
        ItemStack actual;
        try{actual=from.provider.extract(key,accepting,false);verifyStack(actual,key,accepting);}
        catch(RuntimeException failure){uncertain(layer,source,key,accepting,from.provider.id());return -1;}
        if(actual.isEmpty())return 0;
        ItemStack rest;
        try{rest=to.provider.insert(actual,false);verifyRemainder(rest,key,actual.getCount());}
        catch(RuntimeException failure){uncertain(layer,source,key,actual.getCount(),to.provider.id());changed(source,destination);return -1;}
        int accepted=actual.getCount()-rest.getCount();
        if(!rest.isEmpty())refundItems(layer,source,from.provider,key,rest);
        changed(source,destination);itemBudget-=actual.getCount();
        if(accepted>0){layer.transferred(accepted,0);visual(layer,source,destination,key::sample,-1,null);return 1;}
        return !layer.enabled()||itemBudget<=0?-1:0;
    }
    private static long itemTotal(Endpoint endpoint,ItemKey key){long total=0;for(ItemView view:endpoint.items)if(view.ready)total=NetworkInventoryIndex.saturatingAdd(total,view.contents.getOrDefault(key,0L));return total;}
    private static long fluidTotal(Map<ResourceProvider,Map<ResourceKey,Long>> snapshots,ResourceKey key){long total=0;for(var values:snapshots.values())total=NetworkInventoryIndex.saturatingAdd(total,values.getOrDefault(key,0L));return total;}
    private boolean moveResources(RuneLayer layer,Endpoint source,Endpoint destination,net.minecraft.resources.ResourceLocation kind){
        if(fluidBudget<=0||source.fluids.isEmpty()||destination.fluids.isEmpty())return false;
        boolean supported=false;for(ResourceProvider provider:destination.fluids)if(provider.resourceType().equals(kind)){supported=true;break;}if(!supported)return false;
        Map<ResourceProvider,Map<ResourceKey,Long>> sourceAmounts=new LinkedHashMap<>();
        for(ResourceProvider provider:source.fluids)if(provider.resourceType().equals(kind))sourceAmounts.put(provider,provider.snapshot());
        if(sourceAmounts.isEmpty())return false;
        Map<ResourceProvider,Map<ResourceKey,Long>> destinationAmounts=Map.of();
        if(layer.filter().target()!=Long.MAX_VALUE){destinationAmounts=new LinkedHashMap<>();for(ResourceProvider provider:destination.fluids)if(provider.resourceType().equals(kind))destinationAmounts.put(provider,provider.snapshot());}
        for(ResourceProvider from:source.fluids){
            if(!from.resourceType().equals(kind))continue;
            List<ResourceKey> keys=List.copyOf(sourceAmounts.get(from).keySet());
            Map<Object,Integer> positions=fluidCursors.computeIfAbsent(layer.id(),ignored->new HashMap<>());
            for(int checkedKey=0;checkedKey<Math.min(KEY_BUDGET,keys.size());checkedKey++){
                int index=Math.floorMod(positions.getOrDefault(from.identity(),0),keys.size());positions.put(from.identity(),(index+1)%keys.size());
                ResourceKey key=keys.get(index);if(!key.type().equals(kind)||key instanceof FluidKey fluid&&!layer.filter().matches(fluid.sample()))continue;
                for(ResourceProvider to:destination.fluids){
                    if(!to.resourceType().equals(kind))continue;
                    if(from.identity().equals(to.identity())){layer.report("Same inventory");continue;}
                    long available=Math.max(0,fluidTotal(sourceAmounts,key)-layer.filter().minimum());
                    long room=layer.filter().target()==Long.MAX_VALUE?Long.MAX_VALUE:Math.max(0,layer.filter().target()-fluidTotal(destinationAmounts,key)-incoming(destination,key));
                    long amount=Math.min(fluidBudget,Math.min(available,room));
                    if(amount<=0){layer.report(available<=0?"Source reserve reached":"Stock target reached");continue;}
                    if(!AstralConfig.instantAutomaticLogistics.get()){
                        if(dispatchResources(layer,source,destination,from,to,key,amount))return true;
                        continue;
                    }
                    amount=checked(from.extract(key,amount,true),amount);if(amount<=0){layer.report("Source face rejects fluid extraction");continue;}
                    amount=checked(to.insert(key,amount,true),amount);if(amount<=0){layer.report("Target full or rejects matching fluid");continue;}
                    long extracted;
                    try{extracted=checked(from.extract(key,amount,false),amount);}
                    catch(RuntimeException failure){uncertain(layer,source,key,amount,from.id());return false;}
                    long accepted;
                    try{accepted=checked(to.insert(key,extracted,false),extracted);}
                    catch(RuntimeException failure){uncertain(layer,source,key,extracted,to.id());changed(source,destination);return false;}
                    if(accepted<extracted){long refund=extracted-accepted;try{long restored=checked(from.insert(key,refund,false),refund);recover(layer,source,key,refund-restored,false,from.id());}catch(RuntimeException failure){uncertain(layer,source,key,refund,from.id());}}
                    changed(source,destination);fluidBudget-=(int)extracted;
                    if(accepted>0){layer.transferred(0,key instanceof FluidKey?accepted:0);visual(layer,source,destination,()->ItemStack.EMPTY,key.type().equals(ResourceKinds.FLUID)?-2:key.type().equals(ResourceKinds.ENERGY)?-3:-4,key instanceof FluidKey f?net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.sample().getFluid()):null);return true;}
                    return false;
                }
            }
        }
        return false;
    }
    private List<GlobalPos> deliveryRoute(RuneLayer layer,Endpoint source,Endpoint destination,GlobalPos from,GlobalPos to){
        if(source.target.face()!=null&&destination.target.face()!=null)
            return from.equals(layer.surface().address().position())?activeRoute:activeRoute.reversed();
        if(!from.dimension().equals(to.dimension()))return List.of(from,to);
        return NetworkManager.get(server).route(from,to,layer.surface().channel(),com.cappleapple.astralrepository.AstralServerConfig.wandBindingRange.get());
    }
    private long pendingAt(GlobalPos position,ResourceKey key){
        long amount=transit.pendingAmount(position,key);var level=server.getLevel(position.dimension());
        if(level!=null&&level.hasChunkAt(position.pos())){
            var state=level.getBlockState(position.pos());
            if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock&&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
                var other=position.pos().relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
                amount=NetworkInventoryIndex.saturatingAdd(amount,transit.pendingAmount(GlobalPos.of(position.dimension(),other),key));
            }
        }
        return amount;
    }
    private long incoming(Endpoint destination,ResourceKey key){
        if(destination.target.face()!=null)return pendingAt(destination.target.position(),key);
        long amount=0;Set<Object> seen=new HashSet<>();
        if(key instanceof ItemKey item){
            for(var view:destination.items)if(view.provider instanceof RuneRouting.Items routed)
                for(var target:routed.destinations(item))if(seen.add(target.provider().identity()))amount=NetworkInventoryIndex.saturatingAdd(amount,pendingAt(target.position(),key));
        }else{
            for(var provider:destination.fluids)if(provider instanceof RuneRouting.Resources routed&&provider.resourceType().equals(key.type()))
                for(var target:routed.destinations(key))if(seen.add(target.provider().identity()))amount=NetworkInventoryIndex.saturatingAdd(amount,pendingAt(target.position(),key));
        }
        return amount;
    }
    private int dispatchItems(RuneLayer layer,Endpoint source,Endpoint destination,StorageProvider from,StorageProvider to,ItemKey key,int wanted){
        var sources=from instanceof RuneRouting.Items routed?routed.sources(key):List.of(new RuneRouting.ItemEndpoint(source.target.position(),source.target.face(),from));
        var targets=to instanceof RuneRouting.Items routed?routed.destinations(key):List.of(new RuneRouting.ItemEndpoint(destination.target.position(),destination.target.face(),to));
        int sent=0;
        for(var origin:sources)for(var target:targets){
            if(!origin.provider().valid()||!target.provider().valid()||origin.provider().identity().equals(target.provider().identity()))continue;
            var route=deliveryRoute(layer,source,destination,origin.position(),target.position());if(route.size()<2)continue;
            int offer=Math.min(wanted-sent,itemBudget);if(offer<=0)return sent>0?1:0;
            ItemStack available=origin.provider().extract(key,offer,true);verifyStack(available,key,offer);if(available.isEmpty())continue;
            ItemStack rest=target.provider().insert(available,true);verifyRemainder(rest,key,available.getCount());
            int accepting=available.getCount()-rest.getCount();
            if(target.provider() instanceof RuneRouting.ItemPolicy policy&&policy.insertionLimit(key)!=Long.MAX_VALUE)
                accepting=(int)Math.min(accepting,Math.max(0,policy.insertionLimit(key)-pendingAt(target.position(),key)));
            if(accepting<=0)continue;
            var start=address(origin.position(),origin.side(),origin.provider());var end=address(target.position(),target.side(),target.provider());
            ItemStack actual;
            try{actual=origin.provider().extract(key,accepting,false);verifyStack(actual,key,accepting);}
            catch(RuntimeException failure){uncertainFlight(layer.id(),start,key,accepting);notice(start);return -1;}
            if(actual.isEmpty())continue;
            enqueue(layer,start,end,key,actual.getCount(),route);notice(start);itemBudget-=actual.getCount();sent+=actual.getCount();
            if(sent>=wanted||itemBudget<=0)return 1;
        }
        return sent>0?1:0;
    }
    private boolean dispatchResources(RuneLayer layer,Endpoint source,Endpoint destination,ResourceProvider from,ResourceProvider to,ResourceKey key,long wanted){
        var sources=from instanceof RuneRouting.Resources routed?routed.sources(key):List.of(new RuneRouting.ResourceEndpoint(source.target.position(),source.target.face(),from));
        var targets=to instanceof RuneRouting.Resources routed?routed.destinations(key):List.of(new RuneRouting.ResourceEndpoint(destination.target.position(),destination.target.face(),to));
        long sent=0;
        for(var origin:sources)for(var target:targets){
            if(!origin.provider().valid()||!target.provider().valid()||origin.provider().identity().equals(target.provider().identity()))continue;
            var route=deliveryRoute(layer,source,destination,origin.position(),target.position());if(route.size()<2)continue;
            long offer=Math.min(wanted-sent,fluidBudget);if(offer<=0)return sent>0;
            long available=checked(origin.provider().extract(key,offer,true),offer);if(available<=0)continue;
            long accepting=checked(target.provider().insert(key,available,true),available);
            if(target.provider() instanceof RuneRouting.ResourcePolicy policy&&policy.insertionLimit(key)!=Long.MAX_VALUE)
                accepting=Math.min(accepting,Math.max(0,policy.insertionLimit(key)-pendingAt(target.position(),key)));
            if(accepting<=0)continue;
            var start=address(origin.position(),origin.side(),origin.provider());var end=address(target.position(),target.side(),target.provider());
            long actual;
            try{actual=checked(origin.provider().extract(key,accepting,false),accepting);}
            catch(RuntimeException failure){uncertainFlight(layer.id(),start,key,accepting);notice(start);return false;}
            if(actual<=0)continue;
            enqueue(layer,start,end,key,actual,route);notice(start);fluidBudget-=(int)actual;sent+=actual;
            if(sent>=wanted||fluidBudget<=0)return true;
        }
        return sent>0;
    }
    private static RuneTransitData.Endpoint address(GlobalPos pos,Direction side,Object provider){
        String id=provider instanceof StorageProvider items?items.id():((ResourceProvider)provider).id();
        return new RuneTransitData.Endpoint(pos,side,id,provider);
    }
    private void enqueue(RuneLayer layer,RuneTransitData.Endpoint from,RuneTransitData.Endpoint to,ResourceKey resource,long amount,List<GlobalPos> route){
        int duration=from.position().dimension().equals(to.position().dimension())?TransferVisuals.duration(TransferVisuals.projectedPath(route)):20;
        transit.enqueue(clock+duration,new RuneTransitData.Flight(layer.id(),from,to,resource,amount));
        int style=resource instanceof ItemKey?-1:resource.type().equals(ResourceKinds.FLUID)?-2:resource.type().equals(ResourceKinds.ENERGY)?-3:-4;
        var fluid=resource instanceof FluidKey f?net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.sample().getFluid()):null;
        if(!from.position().dimension().equals(to.position().dimension())){
            for(var endpoint:List.of(from,to)){
                var level=server.getLevel(endpoint.position().dimension());if(level==null||level.players().isEmpty()||AstralConfig.particleDensity.get()<=0)continue;
                var point=endpoint.position().pos();var path=List.of(point,point);
                TransferVisualDispatcher.enqueue(level,path,style,()->new NetworkPackets.Visual(point,point,resource instanceof ItemKey item?item.sample():ItemStack.EMPTY,AstralNetwork.color(layer.surface()),duration,style,path,null,null,fluid));
            }
            return;
        }
        java.util.function.Supplier<TransferVisuals.Endpoint> rune=()->TransferVisuals.rune(layer);
        TransferVisuals.sendWithEndpoints(server,route,()->resource instanceof ItemKey item?item.sample():ItemStack.EMPTY,AstralNetwork.color(layer.surface()),style,
                from.position().equals(layer.surface().address().position())?rune:null,to.position().equals(layer.surface().address().position())?rune:null,fluid);
    }
    private Object resolve(RuneTransitData.Endpoint endpoint,ResourceKey key){
        if(!loaded(endpoint.position()))return null;
        Object live=endpoint.live();
        if(live instanceof StorageProvider items)return items.valid()?items:null;
        if(live instanceof ResourceProvider resource)return resource.valid()?resource:null;
        if(endpoint.policyAnchor()!=null){
            var network=NetworkManager.get(server).networkAt(endpoint.policyAnchor());
            if(network==null)return null;
            return key instanceof ItemKey?network.restoreRuneItems(endpoint.position(),endpoint.side(),endpoint.provider()):network.restoreRuneResource(endpoint.position(),endpoint.side(),endpoint.provider(),key);
        }
        var level=server.getLevel(endpoint.position().dimension());
        if(key instanceof ItemKey){for(var provider:CompatibilityRegistry.discoverStorage(level,endpoint.position().pos(),endpoint.side()))if(provider.id().equals(endpoint.provider())&&provider.valid())return provider;}
        else for(var provider:CompatibilityRegistry.discoverResources(level,endpoint.position().pos(),endpoint.side()))if(provider.id().equals(endpoint.provider())&&provider.resourceType().equals(key.type())&&provider.valid())return provider;
        return null;
    }
    private void arriveSafely(RuneTransitData.Flight flight){
        try{arrive(flight);}catch(RuntimeException failure){
            // A callback may throw after mutating. Never replay an indeterminate commit,
            // and never discard the rest of this due bucket because one provider failed.
            uncertainFlight(flight.rune(),flight.from(),flight.resource(),flight.amount());
        }
    }
    private void arrive(RuneTransitData.Flight flight){
        if(!loaded(flight.to().position())){
            // Drop retained chunk/provider references while dormant; saved addresses retain ownership.
            var to=flight.to();var suspended=new RuneTransitData.Endpoint(to.position(),to.side(),to.provider(),null,to.policyAnchor());
            var from=flight.from();if(!loaded(from.position()))from=new RuneTransitData.Endpoint(from.position(),from.side(),from.provider(),null,from.policyAnchor());
            transit.enqueue(clock+20,new RuneTransitData.Flight(flight.rune(),from,suspended,flight.resource(),flight.amount()));return;
        }
        long remaining=flight.amount();
        try{
            if(!CraftingService.isProcessorReserved(flight.to().position())){
                Object target=resolve(flight.to(),flight.resource());
                if(target instanceof StorageProvider items&&flight.resource() instanceof ItemKey key){
                    var rest=items.insert(key.sample().copyWithCount((int)remaining),false);verifyRemainder(rest,key,(int)remaining);remaining=rest.getCount();
                }else if(target instanceof ResourceProvider resources)remaining-=checked(resources.insert(flight.resource(),remaining,false),remaining);
            }
        }catch(RuntimeException failure){uncertainFlight(flight.rune(),flight.from(),flight.resource(),remaining,flight.to().provider());notice(flight.to());return;}
        long delivered=flight.amount()-remaining;
        if(delivered>0){var work=known.get(flight.rune());if(work!=null)work.layer.transferred(flight.resource() instanceof ItemKey?delivered:0,flight.resource() instanceof FluidKey?delivered:0);notice(flight.to());}
        if(remaining<=0)return;
        try{
            Object source=resolve(flight.from(),flight.resource());
            if(source instanceof StorageProvider items&&flight.resource() instanceof ItemKey key){var rest=items.insert(key.sample().copyWithCount((int)remaining),false);verifyRemainder(rest,key,(int)remaining);remaining=rest.getCount();}
            else if(source instanceof ResourceProvider resources)remaining-=checked(resources.insert(flight.resource(),remaining,false),remaining);
        }catch(RuntimeException failure){uncertainFlight(flight.rune(),flight.from(),flight.resource(),remaining);notice(flight.from());return;}
        if(remaining>0)recoverFlight(flight.rune(),flight.from(),flight.resource(),remaining,false);
        notice(flight.from());
    }
    private void recoverFlight(UUID rune,RuneTransitData.Endpoint source,ResourceKey key,long amount,boolean uncertain){
        TransferRecoveryData.get(server).put(source.position(),key,amount,uncertain,"rune:"+rune+":"+source.provider(),source.side());
    }
    private void uncertainFlight(UUID rune,RuneTransitData.Endpoint source,ResourceKey key,long amount){
        uncertainFlight(rune,source,key,amount,source.provider());
    }
    private void uncertainFlight(UUID rune,RuneTransitData.Endpoint source,ResourceKey key,long amount,String failedProvider){
        TransferRecoveryData.get(server).put(source.position(),key,amount,true,"rune:"+rune+":"+failedProvider,source.side());
        var work=known.get(rune);if(work!=null){work.layer.setEnabled(false);work.layer.report("Transfer outcome uncertain; paused");}
    }
    private void notice(RuneTransitData.Endpoint endpoint){
        Object live=endpoint.live();Object identity=live instanceof StorageProvider items?items.identity():live instanceof ResourceProvider resource?resource.identity():null;
        if(identity!=null){invalidateIdentity(identity);NetworkManager.get(server).providerIdentitiesChanged(Set.of(identity));}
        invalidate(endpoint.position());
    }
    private void refundItems(RuneLayer layer,Endpoint source,StorageProvider provider,ItemKey key,ItemStack refund){
        ItemStack rest;
        try{rest=provider.insert(refund,false);verifyRemainder(rest,key,refund.getCount());}
        catch(RuntimeException failure){uncertain(layer,source,key,refund.getCount(),provider.id());return;}
        if(rest.isEmpty())return;
        ServerLevel level=server.getLevel(source.target.position().dimension());BlockPos pos=source.target.position().pos();
        // A source that rejects a confirmed refund cannot destroy the caller-owned items.
        while(!rest.isEmpty()){int count=Math.min(rest.getCount(),rest.getMaxStackSize());ItemStack drop=rest.copyWithCount(count);if(!level.addFreshEntity(new ItemEntity(level,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,drop))){recover(layer,source,key,rest.getCount(),false,provider.id());break;}rest.shrink(count);}
    }
    private void uncertain(RuneLayer layer,Endpoint source,ResourceKey key,long amount,String provider){recover(layer,source,key,amount,true,provider);layer.setEnabled(false);layer.report("Transfer outcome uncertain; paused");}
    private void recover(RuneLayer layer,Endpoint source,ResourceKey key,long amount,boolean uncertain,String provider){TransferRecoveryData.get(server).put(source.target.position(),key,amount,uncertain,"rune:"+layer.id()+":"+provider,source.target.face());}
    private void changed(Endpoint source,Endpoint destination){
        NetworkManager.get(server).providerIdentitiesChanged(source.identities,destination.identities);invalidate(source.target.position());invalidate(destination.target.position());
    }
    public void invalidateIdentities(Set<Object> identities){for(Object identity:identities)invalidateIdentity(identity);}
    void invalidateIdentity(Object identity){var items=itemsByIdentity.get(identity);if(items!=null)for(int i=0;i<items.size();i++)items.get(i).invalidate();}
    public void invalidate(GlobalPos pos){
        var views=endpointsByPosition.get(pos);
        // These loops only reset local fields. External notifications follow after traversal.
        if(views!=null)for(int i=0;i<views.size();i++){
            var items=views.get(i).items;for(int j=0;j<items.size();j++)items.get(j).invalidate();
        }
        ServerLevel level=server.getLevel(pos.dimension());if(level!=null)NetworkManager.providerChanged(level,pos.pos());
    }
    private static void verifyStack(ItemStack stack,ItemKey key,int maximum){if(stack.getCount()<0||stack.getCount()>maximum||!stack.isEmpty()&&!key.matches(stack))throw new IllegalStateException("Provider violated extraction contract");}
    private static void verifyRemainder(ItemStack stack,ItemKey key,int maximum){if(stack.getCount()<0||stack.getCount()>maximum||!stack.isEmpty()&&!key.matches(stack))throw new IllegalStateException("Provider violated insertion contract");}
    private static long checked(long amount,long maximum){if(amount<0||amount>maximum)throw new IllegalStateException("Provider violated quantity contract");return amount;}
    private void visual(RuneLayer layer,Endpoint source,Endpoint destination,java.util.function.Supplier<ItemStack> sample,int style,net.minecraft.resources.ResourceLocation fluid){
        if(AstralConfig.particleDensity.get()<=0)return;
        var level=server.getLevel(source.target.position().dimension());if(level==null||level.players().isEmpty())return;
        if(source.target.face()==null||destination.target.face()==null)return; // Aggregate providers emit their actual storage path.
        var from=source.target.position();var to=destination.target.position();
        // The transaction already validated this route. Pull traverses the same undirected graph in reverse.
        var route=from.equals(layer.surface().address().position())?activeRoute:activeRoute.reversed();
        java.util.function.Supplier<TransferVisuals.Endpoint> endpoint=()->TransferVisuals.rune(layer);
        TransferVisuals.sendWithEndpoints(server,route,sample,AstralNetwork.color(layer.surface()),style,from.equals(layer.surface().address().position())?endpoint:null,to.equals(layer.surface().address().position())?endpoint:null,fluid);
    }
    private void retryRecovery(){
        int budget=4;
        for(var entry:TransferRecoveryData.get(server).confirmed()){
            if(!entry.provider().startsWith("rune:")||!loaded(entry.source()))continue;if(budget--<=0)break;
            ServerLevel level=server.getLevel(entry.source().dimension());long remaining=entry.amount();
            if(entry.side()==null&&entry.provider().contains(":astral_network")){
                var network=NetworkManager.get(server).networkAt(entry.source());if(network==null)continue;
                long offer=Math.min(remaining,entry.resource() instanceof ItemKey?Math.min(64,AstralConfig.transferRate.get()):AstralConfig.fluidTransferRate.get());
                remaining-=network.recoverRuneResource(entry.source(),entry.resource(),offer);TransferRecoveryData.get(server).remaining(entry,remaining);continue;
            }

            if(entry.resource() instanceof ItemKey key){
                for(StorageProvider provider:CompatibilityRegistry.discoverStorage(level,entry.source().pos(),entry.side())){
                    int offer=(int)Math.min(remaining,Math.min(64,AstralConfig.transferRate.get()));
                    try{ItemStack rest=provider.insert(key.sample().copyWithCount(offer),false);verifyRemainder(rest,key,offer);remaining-=offer-rest.getCount();}
                    catch(RuntimeException failure){TransferRecoveryData.get(server).put(entry.source(),key,offer,true,entry.provider(),entry.side());remaining-=offer;break;}
                    if(remaining<=0)break;
                }
            }else {ResourceKey key=entry.resource();
                for(ResourceProvider provider:CompatibilityRegistry.discoverResources(level,entry.source().pos(),entry.side()))if(provider.resourceType().equals(key.type())){
                    long offer=Math.min(remaining,AstralConfig.fluidTransferRate.get());
                    try{remaining-=checked(provider.insert(key,offer,false),offer);}
                    catch(RuntimeException failure){TransferRecoveryData.get(server).put(entry.source(),key,offer,true,entry.provider(),entry.side());remaining-=offer;break;}
                    if(remaining<=0)break;
                }
            }
            TransferRecoveryData.get(server).remaining(entry,remaining);if(remaining<entry.amount())invalidate(entry.source());
        }
    }
}
