package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.cappleapple.astralrepository.crafting.CraftingService.NetworkAccess;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import java.util.*;

/** A cached connected component, owning discovery cursors, provider indexes and its crafting coordinator. */
public final class AstralNetwork implements NetworkAccess {
    private record BoundStorage(GlobalPos pos,NetworkAnchor owner,StorageProvider provider) {}
    private record BoundResource(GlobalPos pos,NetworkAnchor owner,ResourceProvider provider) {}
    private record Scan(GlobalPos center,Iterator<BlockPos> positions) {}
    private final NetworkManager manager;
    private final MinecraftServer server;
    private final List<NetworkAnchor> nodes;
    private final Set<AnchorAddress> nodeLocations;
    private final GlobalPos origin;
    private final Map<String,BoundStorage> storages=new LinkedHashMap<>();
    private final Map<Object,String> identities=new HashMap<>();
    private final Map<GlobalPos,Set<String>> storagePositions=new HashMap<>();
    private final Map<String,BoundResource> resources=new LinkedHashMap<>();
    private final NetworkInventoryIndex<ItemKey,String> index=new NetworkInventoryIndex<>();
    private final NetworkInventoryIndex<ResourceKey,String> resourceIndex=new NetworkInventoryIndex<>();
    private final ArrayDeque<Scan> scans=new ArrayDeque<>();
    private final Set<GlobalPos> queuedScans=new HashSet<>();
    private final Map<String,List<GlobalPos>> visualPaths=new LinkedHashMap<>();
    private final ArrayDeque<GlobalPos> dirty=new ArrayDeque<>();
    private final Set<GlobalPos> dirtySet=new HashSet<>();
    private final ArrayDeque<String> pollQueue=new ArrayDeque<>();
    private final Map<String,Long> nextPoll=new HashMap<>();
    private final Set<GlobalPos> workstations=new LinkedHashSet<>();
    private final Map<GlobalPos,List<ItemStack>> library=new HashMap<>();
    private final Set<String> failed=new HashSet<>();
    private final CraftingService crafting;
    private final NetworkPower power;
    private long revision,throughput,libraryRevision;
    private int roundRobin,resourceCursor;
    private boolean powered=true;

    AstralNetwork(NetworkManager manager,MinecraftServer server,List<NetworkAnchor> nodes){
        this.manager=manager;this.server=server;this.nodes=List.copyOf(nodes);nodeLocations=new HashSet<>();nodes.forEach(n->nodeLocations.add(n.address()));
        origin=nodes.stream().filter(n->n.kind()==NodeKind.NEXUS).findFirst().map(NetworkManager::location).orElse(NetworkManager.location(nodes.getFirst()));
        nodes.forEach(n->{if(n.nearbyCoverage())scanAround(NetworkManager.location(n));else invalidate(NetworkManager.location(n));});
        power=new NetworkPower(this);crafting=new CraftingService(this);
    }
    private void scanAround(GlobalPos center){if(!queuedScans.add(center))return;int r=AstralConfig.coverageRange.get();scans.add(new Scan(center,BlockPos.betweenClosed(center.pos().offset(-r,-r,-r),center.pos().offset(r,r,r)).iterator()));}
    private NetworkAnchor owner(GlobalPos pos){return manager.owner(pos,nodeLocations);}
    public boolean covers(GlobalPos pos){NetworkAnchor owner=owner(pos);return owner!=null&&nodeLocations.contains(owner.address());}
    public void invalidate(GlobalPos pos){if(dirtySet.add(pos))dirty.add(pos);revision++;}
    public void rescanChunk(ServerLevel level,ChunkPos pos){for(var node:nodes)if(node.getLevel()==level && Math.abs((node.getBlockPos().getX()>>4)-pos.x)<=2 && Math.abs((node.getBlockPos().getZ()>>4)-pos.z)<=2)scanAround(NetworkManager.location(node));}
    public void unloadChunk(ServerLevel level,ChunkPos chunk){
        List<String> remove=storages.entrySet().stream().filter(e->inChunk(e.getValue().pos,level,chunk)).map(Map.Entry::getKey).toList();remove.forEach(this::removeStorage);
        resources.entrySet().removeIf(e->{if(inChunk(e.getValue().pos,level,chunk)){resourceIndex.remove(e.getKey());return true;}return false;});
        workstations.removeIf(p->inChunk(p,level,chunk));if(library.keySet().removeIf(p->inChunk(p,level,chunk)))libraryRevision++;revision++;
    }
    private static boolean inChunk(GlobalPos pos,ServerLevel level,ChunkPos chunk){return pos.dimension().equals(level.dimension())&&new ChunkPos(pos.pos()).equals(chunk);}
    public void tick(int discovery,int polling){
        for(int i=0;i<discovery;i++){
            GlobalPos pos;
            if(!dirty.isEmpty()){pos=dirty.remove();dirtySet.remove(pos);}else{
                Scan scan=scans.poll();if(scan==null)break;
                if(!scan.positions.hasNext()){queuedScans.remove(scan.center);continue;}
                pos=GlobalPos.of(scan.center.dimension(),scan.positions.next().immutable());if(scan.positions.hasNext())scans.add(scan);else queuedScans.remove(scan.center);
            }
            discover(pos);
        }
        for(int i=0;i<polling;i++)pollOne();
        if(server.getTickCount()%20==0){
            powered=power.upkeep();
            reconcileLibrary();
        }
        if(powered&&LogisticsTiming.automaticDue(server.getTickCount())){if(!routeStockItems())routeItems();routeResources();}
        if(powered)crafting.tick();
    }
    private void discover(GlobalPos pos){
        ServerLevel level=server.getLevel(pos.dimension());if(level==null||!level.hasChunkAt(pos.pos())||!covers(pos))return;
        NetworkAnchor owner=owner(pos);var state=level.getBlockState(pos.pos());
        if(VisualWorkbenchCompatibility.isCraftingTable(level,pos.pos())||state.is(Blocks.STONECUTTER)||com.cappleapple.astralrepository.crafting.ProcessingRules.isCandidate(level,pos.pos())||level.getBlockEntity(pos.pos()) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)workstations.add(pos);
        else if(level.getBlockEntity(pos.pos())==null)workstations.remove(pos);
        if(level.getBlockEntity(pos.pos()) instanceof ChiseledBookShelfBlockEntity shelf){readShelf(pos,shelf);return;}else removeShelf(pos);
        // Non-block-entity blocks cannot expose the supported storage capabilities; avoid allocations for air.
        if(!state.hasBlockEntity()) {removeAt(pos);return;}
        workstations.add(pos); // Third-party processing adapters decide which of these machines they support.
        Direction side=owner.attachedTo(pos)?owner.providerSide():null;
        try {
            List<StorageProvider> found=CompatibilityRegistry.discoverStorage(level,pos.pos(),side);
            Set<String> live=new HashSet<>();
            for(StorageProvider provider:found){String key=NetworkManager.id(pos)+"/"+provider.id();live.add(key);if(storages.containsKey(key)){if(storages.get(key).provider.valid())continue;removeStorage(key);}
                String alias=identities.get(provider.identity());if(alias!=null&&storages.containsKey(alias))continue;
                storages.put(key,new BoundStorage(pos,owner,provider));identities.put(provider.identity(),key);
                storagePositions.computeIfAbsent(pos,k->new HashSet<>()).add(key);manager.watchProvider(provider.identity(),this);
                pollQueue.add(key);revision++;
            }
            storages.entrySet().stream().filter(e->e.getValue().pos.equals(pos)&&!live.contains(e.getKey())).map(Map.Entry::getKey).toList().forEach(this::removeStorage);
            for(ResourceProvider provider:CompatibilityRegistry.discoverResources(level,pos.pos(),side)){
                String key=NetworkManager.id(pos)+"/"+provider.id();if(!resources.containsKey(key)||!resources.get(key).provider.valid()){resources.put(key,new BoundResource(pos,owner,provider));revision++;}
            }
        }catch(RuntimeException failure){quarantine(NetworkManager.id(pos),failure);}
    }
    private void removeAt(GlobalPos pos){storages.entrySet().stream().filter(e->e.getValue().pos.equals(pos)).map(Map.Entry::getKey).toList().forEach(this::removeStorage);resources.entrySet().removeIf(e->{if(e.getValue().pos.equals(pos)){resourceIndex.remove(e.getKey());return true;}return false;});}
    private void removeStorage(String key){
        BoundStorage removed=storages.remove(key);index.remove(key);nextPoll.remove(key);
        if(removed!=null){
            identities.remove(removed.provider.identity(),key);manager.unwatchProvider(removed.provider.identity(),this);
            var at=storagePositions.get(removed.pos);if(at!=null&&at.remove(key)&&at.isEmpty())storagePositions.remove(removed.pos);
        }
        revision++;
    }
    private void pollOne(){
        String key=pollQueue.poll();if(key==null)return;
        BoundStorage bound=storages.get(key);if(bound==null)return;pollQueue.add(key);
        if(failed.contains(key))return;
        if(CraftingService.isProcessorReserved(bound.pos)){index.remove(key);return;}
        if(server.getTickCount()<nextPoll.getOrDefault(key,0L))return;
        try{
            if(!loaded(bound.pos)||!bound.provider.valid()){removeStorage(key);invalidate(bound.pos);return;}
            var snapshot=bound.provider.poll(512);
            if(snapshot.isPresent()){index.update(key,snapshot.get());nextPoll.put(key,(long)server.getTickCount()+AstralConfig.reconciliationTicks.get());}
        }catch(RuntimeException failure){index.remove(key);quarantine(key,failure);}
    }
    private void refresh(BoundStorage bound){String key=keyOf(bound);nextPoll.remove(key);index.remove(key);if(dirtySet.add(bound.pos))dirty.add(bound.pos);revision++;}
    private String keyOf(BoundStorage bound){return NetworkManager.id(bound.pos)+"/"+bound.provider.id();}
    private boolean loaded(GlobalPos pos){ServerLevel level=server.getLevel(pos.dimension());return level!=null&&level.hasChunkAt(pos.pos());}
    private void readShelf(GlobalPos pos,ChiseledBookShelfBlockEntity shelf){
        List<ItemStack> products=new ArrayList<>();for(int i=0;i<shelf.getContainerSize();i++){ItemStack product=RecipeTomeItem.product(shelf.getItem(i),server.registryAccess());if(!product.isEmpty())products.add(product);}
        List<ItemStack> previous=library.get(pos);
        boolean changed=previous==null||previous.size()!=products.size();
        if(!changed)for(int i=0;i<products.size();i++)if(!ItemStack.isSameItemSameComponents(previous.get(i),products.get(i))){changed=true;break;}
        if(changed){library.put(pos,List.copyOf(products));libraryRevision++;revision++;}
    }
    private void removeShelf(GlobalPos pos){if(library.remove(pos)!=null){libraryRevision++;revision++;}}
    private void reconcileLibrary(){
        // Bookshelves have only six slots and are reconciled one at a time.
        if(library.isEmpty())return;var positions=new ArrayList<>(library.keySet());GlobalPos p=positions.get(Math.floorMod(server.getTickCount()/20,positions.size()));ServerLevel level=server.getLevel(p.dimension());
        if(level!=null&&level.hasChunkAt(p.pos())&&level.getBlockEntity(p.pos()) instanceof ChiseledBookShelfBlockEntity shelf)readShelf(p,shelf);else removeShelf(p);
    }
    public void providerIdentitiesChanged(Set<Object> changed){for(Object identity:changed)providerIdentityChanged(identity);}
    void providerIdentityChanged(Object identity){String key=identities.get(identity);if(key!=null){nextPoll.remove(key);revision++;}}
    public void providerChanged(GlobalPos pos){
        for(String key:storagePositions.getOrDefault(pos,Set.of()))nextPoll.remove(key);
        if(library.containsKey(pos))invalidate(pos);
        revision++;
    }
    @Override public void processorChanged(GlobalPos pos){
        for(String key:storagePositions.getOrDefault(pos,Set.of())){index.remove(key);nextPoll.remove(key);}
        invalidate(pos);
    }
    @Override public boolean payCrafting(long complexity){return powered&&power.operation(AstralConfig.jobCost.get()+complexity*AstralConfig.complexityCost.get());}
    @Override public List<CraftingProvider> craftingProviders(){
        List<CraftingProvider> result=new ArrayList<>();Set<Object> seen=new HashSet<>();
        for(GlobalPos pos:workstations){ServerLevel level=server.getLevel(pos.dimension());if(level==null||!level.hasChunkAt(pos.pos()))continue;
            for(CraftingProvider provider:CompatibilityRegistry.discoverCrafting(level,pos.pos(),null))if(provider.valid()&&seen.add(provider.identity()))result.add(provider);
        }
        return result;
    }
    @Override public ServerLevel level(){return server.getLevel(origin.dimension());}
    @Override public GlobalPos origin(){return origin;}
    @Override public List<GlobalPos> workstations(){return List.copyOf(workstations);}
    @Override public List<ItemStack> exposedProducts(){return library.values().stream().flatMap(Collection::stream).map(ItemStack::copy).toList();}
    @Override public Map<ItemKey,Long> snapshot(){return index.snapshot();}
    @Override public ItemStack extract(ItemKey key,int amount){return extractAt(key,amount,origin);}
    @Override public ItemStack reserve(ItemKey key,int amount,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt){return extractAt(key,amount,origin,receipt);}
    @Override public int travelTicks(GlobalPos from,GlobalPos to){
        if(AstralConfig.instantAutomaticLogistics.get()||from.equals(to))return 0;
        if(!from.dimension().equals(to.dimension()))return 20;
        var route=manager.route(from,to,nodes.getFirst().channel(),AstralConfig.relayRange.get());
        if(route.size()<2)throw new IllegalStateException("No loaded route to workstation");
        return TransferVisuals.duration(route.stream().map(GlobalPos::pos).toList());
    }
    @Override public ItemStack extractForDelivery(ItemKey key,int count,GlobalPos destination,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt){return extractAt(key,count,destination,receipt,true);}
    @Override public ItemStack extractTo(ItemKey key,int count,GlobalPos destination){return extractAt(key,count,destination);}
    @Override public ItemStack insertFrom(ItemStack stack,GlobalPos source){
        return insertExcept(stack,source,null,null,true);
    }
    @Override public boolean canRoute(GlobalPos from,GlobalPos to){return !from.dimension().equals(to.dimension())||!manager.route(from,to,nodes.getFirst().channel(),AstralConfig.relayRange.get()).isEmpty();}
    public ItemStack extractAt(ItemKey key,int amount,GlobalPos destination){return extractAt(key,amount,destination,null);}
    private ItemStack extractAt(ItemKey key,int amount,GlobalPos destination,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt){return extractAt(key,amount,destination,receipt,receipt==null);}
    private ItemStack extractAt(ItemKey key,int amount,GlobalPos destination,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt,boolean checkRoute){
        if(amount<=0)return ItemStack.EMPTY;ItemStack result=key.sample();result.setCount(0);
        for(String location:index.locations(key)){
            BoundStorage bound=storages.get(location);if(!usable(bound,location)||(checkRoute&&!canRoute(bound.pos,destination))||!bound.owner.networkExtractionFilter().matches(key.sample()))continue;
            long available=index.provider(location).getOrDefault(key,0L)-bound.owner.networkExtractionFilter().minimum();
            int wanted=(int)Math.min(amount-result.getCount(),Math.max(0,available));if(wanted<=0)continue;
            try{ItemStack extracted=bound.provider.extract(key,wanted,false);if(extracted.isEmpty())continue;
                if(!new ItemKey(extracted).equals(key)||extracted.getCount()>wanted)throw new IllegalStateException("Provider violated extraction contract");
                result.grow(extracted.getCount());adjustCount(location,key,-extracted.getCount());if(receipt==null)visual(bound.pos,destination,extracted,20);else receipt.accept(bound.pos,extracted.copy());throughput+=extracted.getCount();
            }catch(RuntimeException failure){quarantine(location,failure);index.remove(location);}
            if(result.getCount()>=amount)break;
        }
        return result;
    }
    @Override public ItemStack insert(ItemStack stack){return insertAt(stack,origin);}
    public ItemStack insertAt(ItemStack stack,GlobalPos source){return insertExcept(stack,source,null);}
    private ItemStack insertExcept(ItemStack stack,GlobalPos source,String excluded){return insertExcept(stack,source,excluded,null);}
    private ItemStack insertExcept(ItemStack stack,GlobalPos source,String excluded,GlobalPos excludedPosition){return insertExcept(stack,source,excluded,excludedPosition,false);}
    private ItemStack insertExcept(ItemStack stack,GlobalPos source,String excluded,GlobalPos excludedPosition,boolean craftingReturn){
        ItemStack remaining=stack.copy();if(remaining.isEmpty())return remaining;ItemKey key=new ItemKey(stack);
        List<Map.Entry<String,BoundStorage>> destinations=new ArrayList<>(storages.entrySet());
        destinations.sort(destinationOrder(source,key));
        for(var entry:destinations){String id=entry.getKey();BoundStorage bound=entry.getValue();
            if(id.equals(excluded)||bound.pos.equals(excludedPosition)||!usable(bound,id)||(craftingReturn&&crafting.isProcessingPosition(bound.pos))||!canRoute(source,bound.pos)||!bound.owner.networkInsertionFilter().matches(stack))continue;
            long room=bound.owner.networkInsertionFilter().target()-index.provider(id).getOrDefault(key,0L);int offer=(int)Math.min(remaining.getCount(),Math.max(0,room));if(offer<=0)continue;
            try{ItemStack offered=remaining.copyWithCount(offer);ItemStack rest=bound.provider.insert(offered,false);int accepted=offer-rest.getCount();if(accepted<0||accepted>offer)throw new IllegalStateException("Provider violated insertion contract");
                if(accepted>0){remaining.shrink(accepted);adjustCount(id,key,accepted);visual(source,bound.pos,stack.copyWithCount(accepted),20);throughput+=accepted;}
            }catch(RuntimeException failure){TransferRecoveryData.get(server).put(source,key,offer,true,id);remaining.shrink(offer);quarantine(id,failure);}
            if(remaining.isEmpty())break;
        }
        roundRobin++;return remaining;
    }
    private Comparator<Map.Entry<String,BoundStorage>> destinationOrder(GlobalPos source,ItemKey key){
        NetworkAnchor route=nodes.stream().filter(n->n.enabled()&&n.distributes())
                .min(Comparator.<NetworkAnchor>comparingDouble(n->n.getLevel().dimension().equals(source.dimension())
                        ?n.getBlockPos().distSqr(source.pos()):Double.MAX_VALUE)
                        .thenComparingInt(n->-n.priority()).thenComparing(n->NetworkManager.id(NetworkManager.location(n))))
                .orElse(owner(source));
        DistributionMode mode=route==null?DistributionMode.PRIORITY:route.distributionMode();
        Map<String,Integer> priorities=new HashMap<>();
        for(var entry:storages.entrySet()){var bound=entry.getValue();ServerLevel level=server.getLevel(bound.pos.dimension());priorities.put(entry.getKey(),level==null||!level.hasChunkAt(bound.pos.pos())?bound.owner.priority():ContainerRuneRules.priority(level,bound.pos.pos(),bound.owner.priority()));}
        Comparator<Map.Entry<String,BoundStorage>> base=Comparator.comparingInt(e->-priorities.get(e.getKey()));
        if(mode==DistributionMode.BALANCED)base=base.thenComparingLong(e->index.provider(e.getKey()).getOrDefault(key,0L));
        else if(mode==DistributionMode.NEAREST)base=base.thenComparingDouble(e->e.getValue().pos.dimension().equals(source.dimension())?e.getValue().pos.pos().distSqr(source.pos()):Double.MAX_VALUE);
        else if(mode==DistributionMode.ROUND_ROBIN){
            List<String> stable=storages.keySet().stream().sorted().toList();Map<String,Integer> order=new HashMap<>();
            for(int i=0;i<stable.size();i++)order.put(stable.get(i),Math.floorMod(i-roundRobin,Math.max(1,stable.size())));
            base=base.thenComparingInt(e->order.get(e.getKey()));
        }
        return base.thenComparing(Map.Entry::getKey);
    }

    /** Finite insertion targets pull from network stock even without an export crystal at the source. */
    private boolean routeStockItems(){
        int budget=RuneCadence.DEFAULT.rate(RuneCadence.Kind.ITEMS).amount();boolean moved=false;
        List<Map.Entry<String,BoundStorage>> targets=storages.entrySet().stream()
                .filter(e->attached(e.getValue()) && e.getValue().owner.stocks()
                        && e.getValue().owner.networkInsertionFilter().target()!=Long.MAX_VALUE)
                .sorted(Comparator.<Map.Entry<String,BoundStorage>>comparingInt(e->-e.getValue().owner.priority()).thenComparing(Map.Entry::getKey)).toList();
        if(targets.isEmpty())return false;
        for(var targetEntry:targets){
            String targetId=targetEntry.getKey();BoundStorage target=targetEntry.getValue();
            if(!usable(target,targetId))continue;
            for(ItemKey item:new ArrayList<>(index.snapshot().keySet())){
                if(!target.owner.networkInsertionFilter().matches(item.sample()))continue;
                long deficit=target.owner.networkInsertionFilter().target()-index.provider(targetId).getOrDefault(item,0L);
                if(deficit<=0)continue;
                for(String sourceId:new ArrayList<>(index.locations(item))){
                    if(budget<=0)return moved;
                    BoundStorage source=storages.get(sourceId);
                    if(sourceId.equals(targetId)||!usable(source,sourceId)||!canRoute(source.pos,target.pos)||!source.owner.networkExtractionFilter().matches(item.sample()))continue;
                    long keep=source.owner.networkExtractionFilter().minimum();
                    long ownTarget=source.owner.networkInsertionFilter().target();
                    if(ownTarget!=Long.MAX_VALUE&&source.owner.networkInsertionFilter().matches(item.sample()))keep=Math.max(keep,ownTarget);
                    int amount=(int)Math.min(Math.min(budget,deficit),Math.max(0,index.provider(sourceId).getOrDefault(item,0L)-keep));
                    if(amount<=0)continue;
                    try{
                        ItemStack offered=source.provider.extract(item,amount,true);if(offered.isEmpty())continue;
                        ItemStack simulatedRest=target.provider.insert(offered,true);
                        int accepting=offered.getCount()-simulatedRest.getCount();if(accepting<=0)continue;
                        if(!power.operation(AstralConfig.transferCost.get()+accepting*AstralConfig.itemCost.get()))continue;
                        ItemStack actual=source.provider.extract(item,accepting,false);
                        if(actual.isEmpty())continue;
                        if(!new ItemKey(actual).equals(item)||actual.getCount()>accepting)throw new IllegalStateException("Stock extraction violated provider contract");
                        adjustCount(sourceId,item,-actual.getCount());
                        ItemStack rest;
                        try{rest=target.provider.insert(actual,false);}
                        catch(RuntimeException failure){TransferRecoveryData.get(server).put(source.pos,item,actual.getCount(),true,targetId);quarantine(targetId,failure);break;}
                        int accepted=actual.getCount()-rest.getCount();
                        if(accepted<0||accepted>actual.getCount())throw new IllegalStateException("Stock insertion violated provider contract");
                        if(!rest.isEmpty())refund(source,sourceId,rest);
                        if(accepted>0){adjustCount(targetId,item,accepted);visual(source.pos,target.pos,actual.copyWithCount(accepted),20);
                            budget-=accepted;deficit-=accepted;throughput+=accepted;moved=true;}
                        if(deficit<=0)break;
                    }catch(RuntimeException failure){quarantine(sourceId,failure);break;}
                }
            }
        }
        return moved;
    }
    private void refund(BoundStorage source,String id,ItemStack stack){
        try{ItemStack rest=source.provider.insert(stack,false);adjustCount(id,new ItemKey(stack),stack.getCount()-rest.getCount());if(!rest.isEmpty())drop(source.pos,rest);}
        catch(RuntimeException failure){TransferRecoveryData.get(server).put(source.pos,new ItemKey(stack),stack.getCount(),true,id);quarantine(id,failure);}
    }
    private boolean usable(BoundStorage bound,String id){return bound!=null&&!failed.contains(id)&&loaded(bound.pos)&&!CraftingService.isProcessorReserved(bound.pos)&&bound.provider.valid()&&bound.owner.enabled();}
    private void adjustCount(String provider,ItemKey key,long delta){index.adjust(provider,key,delta);BoundStorage bound=storages.get(provider);if(bound!=null)manager.providerIdentitiesChanged(Set.of(bound.provider.identity()));}
    private boolean attached(BoundStorage bound){return bound.owner.attachedTo(bound.pos);}
    private void routeItems(){
        int budget=RuneCadence.DEFAULT.rate(RuneCadence.Kind.ITEMS).amount();
        for(var entry:new ArrayList<>(storages.entrySet())){
            BoundStorage source=entry.getValue();String key=entry.getKey();
            if(budget<=0)break;
            if(!usable(source,key)||!attached(source)||!source.owner.collects())continue;
            for(var item:new HashMap<>(index.provider(key)).entrySet()){
                if(!source.owner.networkExtractionFilter().matches(item.getKey().sample()))continue;
                int wanted=(int)Math.min(budget,Math.max(0,item.getValue()-source.owner.networkExtractionFilter().minimum()));if(wanted==0)continue;
                try{
                    ItemStack simulated=source.provider.extract(item.getKey(),wanted,true);if(simulated.isEmpty())continue;
                    int accepting=0;
                    for(var target:storages.entrySet())if(!target.getKey().equals(key)&&usable(target.getValue(),target.getKey())&&canRoute(source.pos,target.getValue().pos)&&target.getValue().owner.networkInsertionFilter().matches(simulated)){
                        long room=target.getValue().owner.networkInsertionFilter().target()-index.provider(target.getKey()).getOrDefault(item.getKey(),0L);
                        ItemStack offer=simulated.copyWithCount((int)Math.min(simulated.getCount(),Math.max(0,room)));if(offer.isEmpty())continue;
                        accepting+=offer.getCount()-target.getValue().provider.insert(offer,true).getCount();if(accepting>=simulated.getCount())break;
                    }
                    int amount=Math.min(wanted,accepting);if(amount<=0||!power.operation(AstralConfig.transferCost.get()+amount*AstralConfig.itemCost.get()))continue;
                    ItemStack actual=source.provider.extract(item.getKey(),amount,false);adjustCount(key,item.getKey(),-actual.getCount());
                    ItemStack remaining=insertExcept(actual,source.pos,key);
                    if(!remaining.isEmpty())refund(source,key,remaining);
                    budget-=actual.getCount();
                }catch(RuntimeException failure){quarantine(key,failure);break;}
                if(budget<=0)break;
            }
        }
    }
    private void routeResources(){
        retryResources();
        var resourceEntries=new ArrayList<>(resources.entrySet());
        if(resourceEntries.isEmpty())return;
        Collections.rotate(resourceEntries,-Math.floorMod(resourceCursor++,resourceEntries.size()));
        int examined=0;
        for(var entry:resourceEntries){
            if(examined++>=AstralConfig.reconciliationBudget.get())break;
            String id=entry.getKey();BoundResource source=entry.getValue();
            if(failed.contains(id)||!loaded(source.pos)||CraftingService.isProcessorReserved(source.pos))continue;
            try{
                if(!source.provider.valid()){resources.remove(id);resourceIndex.remove(id);invalidate(source.pos);continue;}
                resourceIndex.update(id,source.provider.snapshot());
                if(!source.owner.collects()||!source.owner.attachedTo(source.pos))continue;
                for(var resource:resourceIndex.provider(id).entrySet()){
                    ResourceKey key=resource.getKey();if(!matches(source.owner.networkExtractionFilter(),key))continue;
                    long rate=RuneCadence.DEFAULT.rate(RuneCadence.Kind.of(key.type())).amount();
                    long available=Math.max(0,resource.getValue()-source.owner.networkExtractionFilter().minimum());
                    for(var target:new ArrayList<>(resources.entrySet())){
                        BoundResource destination=target.getValue();
                        if(!canRoute(source.pos,destination.pos)||id.equals(target.getKey())||source.provider.identity().equals(destination.provider.identity())||failed.contains(target.getKey())||!loaded(destination.pos)||!key.type().equals(destination.provider.resourceType())||!destination.provider.valid()||!matches(destination.owner.networkInsertionFilter(),key))continue;
                        long targetCount=destination.provider.snapshot().getOrDefault(key,0L);
                        long room=destination.owner.networkInsertionFilter().target()-targetCount;
                        long move=Math.min(Math.min(rate,available),Math.max(0,room));
                        move=Math.min(move,source.provider.extract(key,move,true));move=Math.min(move,destination.provider.insert(key,move,true));if(move<=0)continue;
                        double unitCost=key.type().equals(ResourceKinds.FLUID)?AstralConfig.fluidCost.get():key.type().equals(ResourceKinds.SOURCE)?AstralConfig.sourceCost.get():AstralConfig.energyCost.get();
                        if(!power.operation(AstralConfig.transferCost.get()+move*unitCost))continue;
                        long extracted=source.provider.extract(key,move,false),inserted;
                        try{inserted=destination.provider.insert(key,extracted,false);}
                        catch(RuntimeException failure){TransferRecoveryData.get(server).put(source.pos,key,extracted,true,target.getKey());quarantine(target.getKey(),failure);break;}
                        if(inserted<extracted){
                            long refund=extracted-inserted;
                            try{long returned=source.provider.insert(key,refund,false);TransferRecoveryData.get(server).put(source.pos,key,refund-returned,false,id);}
                            catch(RuntimeException failure){TransferRecoveryData.get(server).put(source.pos,key,refund,true,id);quarantine(id,failure);}
                        }
                        resourceVisual(source.pos,destination.pos,key,18);available-=extracted;rate-=extracted;throughput+=inserted;
                        resourceIndex.update(target.getKey(),destination.provider.snapshot());
                        if(rate<=0||available<=0)break;
                    }
                    resourceIndex.update(id,source.provider.snapshot());
                }
            }catch(RuntimeException failure){quarantine(id,failure);resourceIndex.remove(id);}
        }
    }

    private void retryResources(){
        int budget=8;
        for(var entry:TransferRecoveryData.get(server).confirmed()){
            if(budget<=0)break;if(entry.provider().startsWith("rune:")||!covers(entry.source()))continue;budget--;
            long remaining=entry.amount();
            for(var target:resources.entrySet()){
                var bound=target.getValue();if(!bound.pos.equals(entry.source())||!bound.provider.resourceType().equals(entry.resource().type())||!loaded(bound.pos)||!bound.provider.valid())continue;
                try{remaining-=bound.provider.insert(entry.resource(),remaining,false);}catch(RuntimeException failure){TransferRecoveryData.get(server).put(entry.source(),entry.resource(),remaining,true,target.getKey());remaining=0;quarantine(target.getKey(),failure);}
                if(remaining<=0)break;
            }
            TransferRecoveryData.get(server).remaining(entry,remaining);
        }
    }
    private static boolean matches(FilterRules filter,ResourceKey key){return key instanceof FluidKey fluid?filter.matches(fluid.sample()):true;}
    public void drop(GlobalPos pos,ItemStack stack){ServerLevel level=server.getLevel(pos.dimension());if(level!=null&&!stack.isEmpty()){var entity=new net.minecraft.world.entity.item.ItemEntity(level,pos.pos().getX()+.5,pos.pos().getY()+1,pos.pos().getZ()+.5,stack);level.addFreshEntity(entity);}}
    private void resourceVisual(GlobalPos from,GlobalPos to,ResourceKey key,int duration){
        int style=key.type().equals(ResourceKinds.FLUID)?-2:key.type().equals(ResourceKinds.SOURCE)?-4:-3;
        var fluid=key instanceof FluidKey f?net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.sample().getFluid()):null;
        if(!from.dimension().equals(to.dimension())){sendVisual(from,from,ItemStack.EMPTY,duration,style,fluid);sendVisual(to,to,ItemStack.EMPTY,duration,style,fluid);return;}
        routedVisual(from,to,ItemStack.EMPTY,style,AstralConfig.relayRange.get(),key instanceof FluidKey f?net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.sample().getFluid()):null);
    }
    @Override public void visual(GlobalPos from,GlobalPos to,ItemStack stack,int duration){
        if(!from.dimension().equals(to.dimension())){sendVisual(from,from,stack,duration,-1);sendVisual(to,to,stack,duration,-1);return;}
        routedVisual(from,to,stack,-1,AstralConfig.relayRange.get());
    }
    private void routedVisual(GlobalPos from,GlobalPos to,ItemStack stack,int style,double range){routedVisual(from,to,stack,style,range,null);}
    private void routedVisual(GlobalPos from,GlobalPos to,ItemStack stack,int style,double range,net.minecraft.resources.ResourceLocation fluid){
        if(!movingVisualsObserved(from))return;
        var route=manager.route(from,to,nodes.getFirst().channel(),range);
        // Fuel and recipe inputs arrive together. A separate fuel port keeps both visible.
        TransferVisuals.Endpoint arrival=!stack.isEmpty()&&fuelTime(stack)>0?furnacePort(to):null;
        TransferVisuals.send(server,route,stack,color(nodes.getFirst()),style,furnacePort(from),arrival,fluid);
    }
    private boolean movingVisualsObserved(GlobalPos from){
        if(AstralConfig.particleDensity.get()<=0)return false;
        ServerLevel level=server.getLevel(from.dimension());return level!=null&&!level.players().isEmpty();
    }
    private TransferVisuals.Endpoint furnacePort(GlobalPos pos){
        ServerLevel world=server.getLevel(pos.dimension());
        return world!=null&&world.hasChunkAt(pos.pos())&&world.getBlockEntity(pos.pos()) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                ?new TransferVisuals.Endpoint(new net.minecraft.world.phys.Vec3(0,.65,0),Direction.UP):null;
    }
    private boolean runeReachable(GlobalPos host,GlobalPos storage){return !host.dimension().equals(storage.dimension())||!manager.route(host,storage,nodes.getFirst().channel(),com.cappleapple.astralrepository.AstralServerConfig.wandBindingRange.get()).isEmpty();}
    private void runeVisual(GlobalPos from,GlobalPos to,ItemStack stack,int style,GlobalPos host,TransferVisuals.Endpoint endpoint){runeVisual(from,to,stack,style,host,endpoint,null);}
    private void runeVisual(GlobalPos from,GlobalPos to,ItemStack stack,int style,GlobalPos host,TransferVisuals.Endpoint endpoint,net.minecraft.resources.ResourceLocation fluid){
        if(!from.dimension().equals(to.dimension())){sendVisual(from,from,stack,20,style,fluid);sendVisual(to,to,stack,20,style,fluid);return;}
        if(!movingVisualsObserved(from))return;
        var route=manager.route(from,to,nodes.getFirst().channel(),com.cappleapple.astralrepository.AstralServerConfig.wandBindingRange.get());
        TransferVisuals.send(server,route,stack,color(nodes.getFirst()),style,from.equals(host)?endpoint:null,to.equals(host)?endpoint:null,fluid);
    }
    @Override public void stagingVisual(GlobalPos workstation,List<ItemStack> grid,int duration){for(int i=0;i<Math.min(9,grid.size());i++)sendVisual(workstation,workstation,grid.get(i),duration,10+i);}
    @Override public void craftingVisual(GlobalPos workstation,List<ItemStack> ingredients,ItemStack output,int duration){for(int i=0;i<Math.min(9,ingredients.size());i++)if(!ingredients.get(i).isEmpty())sendVisual(workstation,workstation,ingredients.get(i),duration,i);sendVisual(workstation,workstation,output,duration,9);}
    private void sendVisual(GlobalPos from,GlobalPos to,ItemStack stack,int duration,int slot){sendVisual(from,to,stack,duration,slot,null);}
    private void sendVisual(GlobalPos from,GlobalPos to,ItemStack stack,int duration,int slot,net.minecraft.resources.ResourceLocation fluid){
        ServerLevel level=server.getLevel(from.dimension());if(level==null||level.players().isEmpty()||slot<0&&AstralConfig.particleDensity.get()<=0)return;var owner=owner(from);int color=owner==null?0x65D6CF:color(owner);
        var path=List.of(from.pos(),to.pos());
        TransferVisualDispatcher.enqueue(level,path,slot,()->new NetworkPackets.Visual(from.pos(),to.pos(),stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1),color,LogisticsTiming.visualTicks(duration),slot,path,null,null,fluid));
    }
    public static int color(NetworkAnchor node){return node.channel()<0?0x65D6CF:DyeColor.byId(node.channel()).getTextureDiffuseColor();}
    /** A rune sees network storage except its own physical host (including aliased double chests). */
    public StorageProvider runeItems(GlobalPos host,Set<Object> excluded,TransferVisuals.Endpoint endpoint){return new RuneRouting.Items(){
        public String id(){return "astral_network";} public Object identity(){return AstralNetwork.this;}
        public boolean valid(){return manager.networkAt(origin)==AstralNetwork.this;}
        public long capacity(){return AstralNetwork.this.capacity();}
        private ItemKey simulatedKey;
        private int simulatedTick;
        private List<Map.Entry<String,BoundStorage>> simulatedOrder;
        private List<Map.Entry<String,BoundStorage>> insertionOrder(ItemKey key,boolean simulate){
            List<Map.Entry<String,BoundStorage>> order;
            if(!simulate&&simulatedOrder!=null&&simulatedTick==server.getTickCount()&&key.equals(simulatedKey))order=simulatedOrder;
            else{order=new ArrayList<>(storages.entrySet());order.sort(destinationOrder(host,key));}
            // Reuse only the immediately following commit, never retain an order between transfers.
            if(simulate){simulatedKey=key;simulatedTick=server.getTickCount();simulatedOrder=order;}else{simulatedKey=null;simulatedOrder=null;}
            return order;
        }
        private boolean allowed(String id,BoundStorage b){return storages.get(id)==b&&!b.pos.equals(host)&&!excluded.contains(b.provider.identity())&&usable(b,id)&&runeReachable(host,b.pos);}
        public Map<ItemKey,Long> snapshot(){
            Map<ItemKey,Long> result=new HashMap<>();
            for(var entry:storages.entrySet()){
                var contents=index.provider(entry.getKey());if(contents.isEmpty())continue;
                var bound=entry.getValue();if(!allowed(entry.getKey(),bound))continue;
                var filter=bound.owner.networkExtractionFilter();boolean unrestricted=filter.unrestricted();
                for(var value:contents.entrySet())if(unrestricted||filter.matches(value.getKey().sample())){
                    long amount=Math.max(0,value.getValue()-filter.minimum());if(amount>0)result.merge(value.getKey(),amount,NetworkInventoryIndex::saturatingAdd);
                }
            }
            return Map.copyOf(result);
        }
        public List<RuneRouting.ItemEndpoint> sources(ItemKey key){
            if(!powered)return List.of();var result=new ArrayList<RuneRouting.ItemEndpoint>();
            for(String id:index.locations(key)){
                var bound=storages.get(id);if(bound==null||!allowed(id,bound))continue;
                var filter=bound.owner.networkExtractionFilter();
                if(filter.matches(key.sample())&&index.provider(id).getOrDefault(key,0L)>filter.minimum())result.add(runeItemEndpoint(bound));
            }
            return result;
        }
        public List<RuneRouting.ItemEndpoint> destinations(ItemKey key){
            if(!powered)return List.of();var result=new ArrayList<RuneRouting.ItemEndpoint>();
            var order=new ArrayList<>(storages.entrySet());order.sort(destinationOrder(host,key));
            for(var entry:order){var bound=entry.getValue();if(!allowed(entry.getKey(),bound))continue;
                var filter=bound.owner.networkInsertionFilter();
                if(filter.matches(key.sample())&&index.provider(entry.getKey()).getOrDefault(key,0L)<filter.target())result.add(runeItemEndpoint(bound));
            }
            return result;
        }
        public ItemStack extract(ItemKey key,int amount,boolean simulate){
            if(!powered||amount<=0)return ItemStack.EMPTY;ItemStack result=key.sample().copyWithCount(0);
            for(String id:index.locations(key)){var bound=storages.get(id);if(bound==null||!allowed(id,bound)||!bound.owner.networkExtractionFilter().unrestricted()&&!bound.owner.networkExtractionFilter().matches(key.sample()))continue;
                int offer=(int)Math.min(amount-result.getCount(),Math.max(0,index.provider(id).getOrDefault(key,0L)-bound.owner.networkExtractionFilter().minimum()));if(offer<=0)continue;
                if(!simulate&&!power.operation(AstralConfig.transferCost.get()+offer*AstralConfig.itemCost.get()))break;
                ItemStack extracted=bound.provider.extract(key,offer,simulate);if(extracted.getCount()>offer||!extracted.isEmpty()&&!new ItemKey(extracted).equals(key))throw new IllegalStateException("Invalid network extraction");result.grow(extracted.getCount());if(!simulate&&!extracted.isEmpty()){adjustCount(id,key,-extracted.getCount());runeVisual(bound.pos,host,extracted,-1,host,endpoint);}if(result.getCount()>=amount)break;
            }return result;
        }
        public ItemStack insert(ItemStack stack,boolean simulate){if(!powered)return stack.copy();ItemStack rest=stack.copy();ItemKey key=new ItemKey(stack);var order=insertionOrder(key,simulate);
            for(var entry:order){String id=entry.getKey();var bound=entry.getValue();if(!allowed(id,bound)||!bound.owner.networkInsertionFilter().matches(stack))continue;
                long room=Math.max(0,bound.owner.networkInsertionFilter().target()-index.provider(id).getOrDefault(key,0L));int offer=(int)Math.min(rest.getCount(),room);if(offer<=0)continue;
                if(!simulate&&!power.operation(AstralConfig.transferCost.get()+offer*AstralConfig.itemCost.get()))break;
                ItemStack remainder=bound.provider.insert(rest.copyWithCount(offer),simulate);if(remainder.getCount()>offer||!remainder.isEmpty()&&!new ItemKey(remainder).equals(key))throw new IllegalStateException("Invalid network insertion");int accepted=offer-remainder.getCount();rest.shrink(accepted);if(!simulate&&accepted>0){adjustCount(id,key,accepted);runeVisual(host,bound.pos,stack.copyWithCount(accepted),-1,host,endpoint);}if(rest.isEmpty())break;
            }return rest;
        }
    };}
    public ResourceProvider runeFluids(GlobalPos host,Set<Object> excluded,TransferVisuals.Endpoint endpoint){return runeResources(host,excluded,endpoint,ResourceKinds.FLUID);}
    public ResourceProvider runeResources(GlobalPos host,Set<Object> excluded,TransferVisuals.Endpoint endpoint,net.minecraft.resources.ResourceLocation kind){return new RuneRouting.Resources(){
        public String id(){return "astral_network_"+kind.getPath();}public Object identity(){return AstralNetwork.this;}public net.minecraft.resources.ResourceLocation resourceType(){return kind;}public boolean valid(){return manager.networkAt(origin)==AstralNetwork.this;}public long capacity(){return -1;}
        private int candidateTick;
        private List<Map.Entry<String,BoundResource>> candidates,insertionOrder;
        private Map<Object,List<Map.Entry<String,BoundResource>>> aliases=Map.of();
        private List<Map.Entry<String,BoundResource>> candidates(){
            if(candidates==null||candidateTick!=server.getTickCount()){
                candidateTick=server.getTickCount();insertionOrder=null;var values=new ArrayList<Map.Entry<String,BoundResource>>();
                for(var entry:resources.entrySet()){var bound=entry.getValue();if(bound.provider.resourceType().equals(kind)&&!bound.pos.equals(host)&&!excluded.contains(bound.provider.identity()))values.add(Map.entry(entry.getKey(),bound));}
                candidates=List.copyOf(values);aliases=new HashMap<>();var first=new HashMap<Object,Map.Entry<String,BoundResource>>();
                for(var entry:candidates){Object identity=entry.getValue().provider.identity();var previous=first.putIfAbsent(identity,entry);if(previous!=null){var group=aliases.computeIfAbsent(identity,ignored->{var list=new ArrayList<Map.Entry<String,BoundResource>>();list.add(previous);return list;});group.add(entry);}}
            }
            return candidates;
        }
        private boolean live(Map.Entry<String,BoundResource> entry){
            var bound=entry.getValue();return resources.get(entry.getKey())==bound&&!failed.contains(entry.getKey())&&loaded(bound.pos)&&bound.provider.valid()&&!CraftingService.isProcessorReserved(bound.pos)&&runeReachable(host,bound.pos);
        }
        private boolean available(Map.Entry<String,BoundResource> entry){
            if(!live(entry))return false;
            // Priority sorting must not change which physical alias owns a resource view.
            var group=aliases.get(entry.getValue().provider.identity());if(group!=null)for(var alias:group){if(alias==entry)break;if(live(alias))return false;}
            return true;
        }
        private List<Map.Entry<String,BoundResource>> ordered(boolean insert){
            var values=candidates();if(!insert)return values;
            if(insertionOrder==null){
                Map<String,Integer> priorities=new HashMap<>();
                for(var entry:values){var bound=entry.getValue();ServerLevel level=server.getLevel(bound.pos.dimension());priorities.put(entry.getKey(),level==null||!level.hasChunkAt(bound.pos.pos())?bound.owner.priority():ContainerRuneRules.priority(level,bound.pos.pos(),bound.owner.priority()));}
                var ranked=new ArrayList<>(values);ranked.sort(Comparator.comparingInt(entry->-priorities.get(entry.getKey())));insertionOrder=ranked;
            }
            return insertionOrder;
        }
        public Map<ResourceKey,Long> snapshot(){
            Map<ResourceKey,Long> result=new HashMap<>();Set<Object> seen=new HashSet<>();
            for(var entry:candidates()){var bound=entry.getValue();if(!available(entry)||!seen.add(bound.provider.identity()))continue;
                for(var value:bound.provider.snapshot().entrySet())if(matches(bound.owner.networkExtractionFilter(),value.getKey()))result.merge(value.getKey(),Math.max(0,value.getValue()-bound.owner.networkExtractionFilter().minimum()),NetworkInventoryIndex::saturatingAdd);
            }return Map.copyOf(result);
        }
        public List<RuneRouting.ResourceEndpoint> sources(ResourceKey key){return endpoints(key,false);}
        public List<RuneRouting.ResourceEndpoint> destinations(ResourceKey key){return endpoints(key,true);}
        private List<RuneRouting.ResourceEndpoint> endpoints(ResourceKey key,boolean insert){
            if(!powered||!key.type().equals(kind))return List.of();var result=new ArrayList<RuneRouting.ResourceEndpoint>();
            Set<Object> seen=new HashSet<>();
            for(var entry:ordered(insert)){var bound=entry.getValue();if(!available(entry)||!seen.add(bound.provider.identity()))continue;
                var filter=insert?bound.owner.networkInsertionFilter():bound.owner.networkExtractionFilter();
                if(matches(filter,key))result.add(runeResourceEndpoint(bound));
            }
            return result;
        }
        public long extract(ResourceKey key,long amount,boolean simulate){return move(key,amount,simulate,false);}
        public long insert(ResourceKey key,long amount,boolean simulate){return move(key,amount,simulate,true);}
        private long move(ResourceKey key,long amount,boolean simulate,boolean insert){
            if(!powered||amount<=0||!key.type().equals(kind))return 0;
            try{
                long moved=0;Set<Object> seen=new HashSet<>();
                for(var entry:ordered(insert)){
                    var bound=entry.getValue();if(!available(entry)||!seen.add(bound.provider.identity()))continue;
                    var filter=insert?bound.owner.networkInsertionFilter():bound.owner.networkExtractionFilter();if(!matches(filter,key))continue;
                    long limit;
                    if(insert&&filter.target()==Long.MAX_VALUE)limit=Long.MAX_VALUE;
                    else{long stored=bound.provider.snapshot().getOrDefault(key,0L);limit=insert?Math.max(0,filter.target()-stored):Math.max(0,stored-filter.minimum());}
                    long offer=Math.min(amount-moved,limit);if(offer<=0)continue;
                    if(!simulate&&!power.operation(AstralConfig.transferCost.get()+offer*(kind.equals(ResourceKinds.FLUID)?AstralConfig.fluidCost.get():kind.equals(ResourceKinds.SOURCE)?AstralConfig.sourceCost.get():AstralConfig.energyCost.get())))break;
                    long n=insert?bound.provider.insert(key,offer,simulate):bound.provider.extract(key,offer,simulate);if(n<0||n>offer)throw new IllegalStateException("Invalid network fluid transfer");moved+=n;
                    if(!simulate&&n>0){invalidate(bound.pos);runeVisual(insert?host:bound.pos,insert?bound.pos:host,ItemStack.EMPTY,kind.equals(ResourceKinds.FLUID)?-2:kind.equals(ResourceKinds.SOURCE)?-4:-3,host,endpoint,key instanceof FluidKey f?net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.sample().getFluid()):null);}
                    if(moved>=amount)break;
                }
                return moved;
            }finally{if(!simulate){candidates=null;insertionOrder=null;aliases=Map.of();}}
        }
    };}
    /** Restore saved flights before the incremental inventory index has caught up. */
    StorageProvider restoreRuneItems(GlobalPos pos,Direction side,String providerId){
        if(!loaded(pos))return null;
        NetworkAnchor owner=owner(pos);if(owner==null||runePhysicalNetwork(pos,owner,providerId)!=this)return null;
        ServerLevel level=server.getLevel(pos.dimension());
        for(StorageProvider provider:CompatibilityRegistry.discoverStorage(level,pos.pos(),side))
            if(provider.id().equals(providerId)&&provider.valid())return runeItemEndpoint(new BoundStorage(pos,owner,provider)).provider();
        return null;
    }
    ResourceProvider restoreRuneResource(GlobalPos pos,Direction side,String providerId,ResourceKey key){
        if(!loaded(pos))return null;
        NetworkAnchor owner=owner(pos);if(owner==null||runePhysicalNetwork(pos,owner,providerId)!=this)return null;
        ServerLevel level=server.getLevel(pos.dimension());
        for(ResourceProvider provider:CompatibilityRegistry.discoverResources(level,pos.pos(),side))
            if(provider.id().equals(providerId)&&provider.resourceType().equals(key.type())&&provider.valid())
                return runeResourceEndpoint(new BoundResource(pos,owner,provider)).provider();
        return null;
    }
    /** A flight retains its physical provider, while component policy and power follow rebuilds. */
    private AstralNetwork runePhysicalNetwork(GlobalPos pos,NetworkAnchor owner,String provider){
        if(!loaded(pos)||owner.isRemoved()||!owner.enabled()||CraftingService.isProcessorReserved(pos))return null;
        var network=manager.networkAt(owner.address());
        return network==null||network.failed.contains(NetworkManager.id(pos)+"/"+provider)?null:network;
    }
    private void runePhysicalItemsChanged(GlobalPos pos,StorageProvider provider,ItemKey key,long delta){
        String id=identities.get(provider.identity());
        if(id!=null)adjustCount(id,key,delta);
        else{invalidate(pos);manager.providerIdentitiesChanged(Set.of(provider.identity()));}
        ServerLevel level=server.getLevel(pos.dimension());if(level!=null)NetworkManager.providerChanged(level,pos.pos());
    }
    private RuneRouting.ItemEndpoint runeItemEndpoint(BoundStorage bound){
        StorageProvider delegate=bound.provider;
        StorageProvider routed=new RuneRouting.ItemPolicy(){
            public AnchorAddress policyAnchor(){return bound.owner.address();}
            public long insertionLimit(ItemKey key){
                if(current()==null)return 0;
                var filter=bound.owner.networkInsertionFilter();if(!filter.matches(key.sample()))return 0;
                return filter.target()==Long.MAX_VALUE?Long.MAX_VALUE:Math.max(0,filter.target()-delegate.snapshot().getOrDefault(key,0L));
            }
            private AstralNetwork current(){return delegate.valid()?runePhysicalNetwork(bound.pos,bound.owner,delegate.id()):null;}
            public String id(){return delegate.id();}public Object identity(){return delegate.identity();}
            public boolean valid(){return current()!=null;}public long capacity(){return delegate.capacity();}
            public long version(){return delegate.version();}
            public Map<ItemKey,Long> snapshot(){return valid()?delegate.snapshot():Map.of();}
            public Optional<Map<ItemKey,Long>> poll(int budget){return valid()?delegate.poll(budget):Optional.of(Map.of());}
            public ItemKey candidate(java.util.function.Predicate<ItemStack> matches){return valid()?delegate.candidate(matches):null;}
            public ItemStack extract(ItemKey key,int amount,boolean simulate){
                var network=current();if(network==null||!network.powered||amount<=0)return ItemStack.EMPTY;
                var filter=bound.owner.networkExtractionFilter();if(!filter.matches(key.sample()))return ItemStack.EMPTY;
                int offer=amount;
                if(filter.minimum()>0)offer=(int)Math.min(offer,Math.max(0,delegate.snapshot().getOrDefault(key,0L)-filter.minimum()));
                if(offer<=0||!simulate&&!network.power.operation(AstralConfig.transferCost.get()+offer*AstralConfig.itemCost.get()))return ItemStack.EMPTY;
                ItemStack result=delegate.extract(key,offer,simulate);
                if(result.getCount()<0||result.getCount()>offer||!result.isEmpty()&&!key.matches(result))throw new IllegalStateException("Invalid network extraction");
                if(!simulate&&!result.isEmpty())network.runePhysicalItemsChanged(bound.pos,delegate,key,-result.getCount());
                return result;
            }
            public ItemStack insert(ItemStack stack,boolean simulate){
                var network=current();if(network==null||!network.powered||stack.isEmpty())return stack.copy();
                var filter=bound.owner.networkInsertionFilter();if(!filter.matches(stack))return stack.copy();
                ItemKey key=new ItemKey(stack);int offer=stack.getCount();
                if(filter.target()!=Long.MAX_VALUE)offer=(int)Math.min(offer,Math.max(0,filter.target()-delegate.snapshot().getOrDefault(key,0L)));
                if(offer<=0||!simulate&&!network.power.operation(AstralConfig.transferCost.get()+offer*AstralConfig.itemCost.get()))return stack.copy();
                ItemStack remainder=delegate.insert(stack.copyWithCount(offer),simulate);
                if(remainder.getCount()<0||remainder.getCount()>offer||!remainder.isEmpty()&&!key.matches(remainder))throw new IllegalStateException("Invalid network insertion");
                int accepted=offer-remainder.getCount();
                if(!simulate&&accepted>0)network.runePhysicalItemsChanged(bound.pos,delegate,key,accepted);
                return stack.copyWithCount(stack.getCount()-accepted);
            }
        };
        return new RuneRouting.ItemEndpoint(bound.pos,bound.owner.attachedTo(bound.pos)?bound.owner.providerSide():null,routed);
    }
    private RuneRouting.ResourceEndpoint runeResourceEndpoint(BoundResource bound){
        ResourceProvider delegate=bound.provider;
        ResourceProvider routed=new RuneRouting.ResourcePolicy(){
            public AnchorAddress policyAnchor(){return bound.owner.address();}
            public long insertionLimit(ResourceKey key){
                if(current()==null||!key.type().equals(delegate.resourceType()))return 0;
                var filter=bound.owner.networkInsertionFilter();if(!matches(filter,key))return 0;
                return filter.target()==Long.MAX_VALUE?Long.MAX_VALUE:Math.max(0,filter.target()-delegate.snapshot().getOrDefault(key,0L));
            }
            private AstralNetwork current(){return delegate.valid()?runePhysicalNetwork(bound.pos,bound.owner,delegate.id()):null;}
            public String id(){return delegate.id();}public Object identity(){return delegate.identity();}
            public net.minecraft.resources.ResourceLocation resourceType(){return delegate.resourceType();}
            public boolean valid(){return current()!=null;}public long capacity(){return delegate.capacity();}
            public long version(){return delegate.version();}public String unit(){return delegate.unit();}
            public net.minecraft.resources.ResourceLocation visualization(){return delegate.visualization();}
            public Map<ResourceKey,Long> snapshot(){return valid()?delegate.snapshot():Map.of();}
            public long extract(ResourceKey key,long amount,boolean simulate){return move(key,amount,simulate,false);}
            public long insert(ResourceKey key,long amount,boolean simulate){return move(key,amount,simulate,true);}
            private long move(ResourceKey key,long amount,boolean simulate,boolean insert){
                var network=current();if(network==null||!network.powered||amount<=0||!key.type().equals(resourceType()))return 0;
                var filter=insert?bound.owner.networkInsertionFilter():bound.owner.networkExtractionFilter();if(!matches(filter,key))return 0;
                long offer=amount;
                if(insert&&filter.target()!=Long.MAX_VALUE||!insert&&filter.minimum()>0){
                    long stored=delegate.snapshot().getOrDefault(key,0L);
                    offer=Math.min(offer,Math.max(0,insert?filter.target()-stored:stored-filter.minimum()));
                }
                double cost=key.type().equals(ResourceKinds.FLUID)?AstralConfig.fluidCost.get():key.type().equals(ResourceKinds.SOURCE)?AstralConfig.sourceCost.get():AstralConfig.energyCost.get();
                if(offer<=0||!simulate&&!network.power.operation(AstralConfig.transferCost.get()+offer*cost))return 0;
                long result=insert?delegate.insert(key,offer,simulate):delegate.extract(key,offer,simulate);
                if(result<0||result>offer)throw new IllegalStateException("Invalid network resource transfer");
                if(!simulate&&result>0){network.invalidate(bound.pos);ServerLevel level=server.getLevel(bound.pos.dimension());if(level!=null)NetworkManager.providerChanged(level,bound.pos.pos());}
                return result;
            }
        };
        return new RuneRouting.ResourceEndpoint(bound.pos,bound.owner.attachedTo(bound.pos)?bound.owner.providerSide():null,routed);
    }
    /** Return confirmed remainders to a network endpoint, which need not expose its own tank. */
    long recoverRuneResource(GlobalPos source,ResourceKey key,long amount){
        if(key instanceof ItemKey item){int offer=(int)Math.min(64,amount);return offer-insertAt(item.sample().copyWithCount(offer),source).getCount();}
        long returned=0;Set<Object> visited=new HashSet<>();
        for(var entry:resources.entrySet()){var bound=entry.getValue();if(failed.contains(entry.getKey())||!bound.provider.resourceType().equals(key.type())||!loaded(bound.pos)||!bound.provider.valid()||CraftingService.isProcessorReserved(bound.pos)||!visited.add(bound.provider.identity()))continue;
            long offer=amount-returned;if(offer<=0)break;
            try{long accepted=bound.provider.insert(key,offer,false);if(accepted<0||accepted>offer)throw new IllegalStateException("Invalid recovery insertion");returned+=accepted;if(accepted>0)invalidate(bound.pos);}
            catch(RuntimeException failure){TransferRecoveryData.get(server).put(source,key,offer,true,entry.getKey());returned+=offer;quarantine(entry.getKey(),failure);}
        }return returned;
    }
    public CraftingService crafting(){return crafting;}
    public void cancelPlayerJobs(ServerPlayer player){for(var job:crafting.statuses())if(job.owner().equals(player.getUUID()))crafting.cancel(job.id());}
    public boolean payForAccess(ServerPlayer player,GlobalPos nexus,boolean remote){double cost=AstralConfig.transferCost.get();if(remote){cost+=AstralConfig.remoteCost.get();if(player.level().dimension().equals(nexus.dimension()))cost+=Math.sqrt(player.distanceToSqr(nexus.pos().getCenter()))*AstralConfig.distanceCost.get();else cost+=AstralConfig.dimensionalCost.get();}return power.operation(cost);}
    public List<NetworkAnchor> nodes(){return nodes;}
    public int storageCount(){return storages.size();}
    public long capacity(){long total=0;for(var bound:storages.values())try{total=NetworkInventoryIndex.saturatingAdd(total,Math.max(0,bound.provider.capacity()));}catch(RuntimeException ignored){}return total;}
    public int activeJobs(){return crafting.activeJobs();}
    /** Inventory counts and exposed products only; no polling clock or cosmetic invalidations. */
    public long menuStorageVersion(){return index.version()+libraryRevision;}
    public long version(){return index.version()+revision+server.getTickCount()/40;}
    public String status(){return nodes.size()+" crystals · "+storages.size()+" stores · "+activeJobs()+" jobs\n"+(powered?throughput+" moved": "Needs power")+(!scans.isEmpty()||!dirty.isEmpty()?" · discovering":"")+(failed.isEmpty()?"":" · "+failed.size()+" unavailable");}
    private void quarantine(String id,RuntimeException failure){if(failed.add(id))AstralRepository.LOGGER.error("Astral provider {} unavailable in {}: {}",id,origin,failure.toString(),failure);revision++;}
    public void close(){crafting.cancelAll();power.close();for(Object identity:identities.keySet())manager.unwatchProvider(identity,this);}
    private static int fuelTime(net.minecraft.world.item.ItemStack stack){Integer ticks=net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.get(stack.getItem());return ticks==null?0:ticks;}
}
