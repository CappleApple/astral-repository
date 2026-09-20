package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.NexusMenu;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import com.cappleapple.astralrepository.platform.event.level.*;
import com.cappleapple.astralrepository.platform.event.tick.ServerTickEvent;
import com.cappleapple.astralrepository.platform.event.server.ServerStoppingEvent;
import com.cappleapple.astralrepository.platform.event.server.ServerStoppedEvent;
import com.cappleapple.astralrepository.platform.network.PacketDistributor;
import java.util.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;

/** Server-owned graph of loaded crystals, automatic relay edges, and saved explicit links. */
public final class NetworkManager {
    private static final Map<MinecraftServer,NetworkManager> SERVERS=new IdentityHashMap<>();
    private final MinecraftServer server;
    private final DirectRuneTransfers runeTransfers;
    private final Map<AnchorAddress,NetworkAnchor> nodes=new HashMap<>();
    private final Map<AnchorAddress,Set<AnchorAddress>> effectiveLinks=new HashMap<>();
    private final Map<AnchorAddress,AstralNetwork> membership=new HashMap<>();
    private final List<AstralNetwork> networks=new ArrayList<>();
    private static final class SpatialIndex {
        private final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,Long2ObjectOpenHashMap<List<NetworkAnchor>>> dimensions=new HashMap<>();
        List<NetworkAnchor> at(GlobalPos position,int x,int z){
            var chunks=dimensions.get(position.dimension());return chunks==null?List.of():chunks.getOrDefault(ChunkPos.asLong(x,z),List.of());
        }
        void add(NetworkAnchor node){
            var position=location(node);var chunks=dimensions.computeIfAbsent(position.dimension(),ignored->new Long2ObjectOpenHashMap<>());
            chunks.computeIfAbsent(ChunkPos.asLong(position.pos()),ignored->new ArrayList<>()).add(node);
        }
        void replace(SpatialIndex other){dimensions.clear();dimensions.putAll(other.dimensions);}
    }
    private final SpatialIndex spatial=new SpatialIndex();
    private record CoveredFace(RuneSurface surface,AstralNetwork network){}
    private record Coverage(AstralNetwork nearest,List<CoveredFace> faces){}
    private static final Coverage NO_COVERAGE=new Coverage(null,List.of());
    private final Object2ObjectLinkedOpenCustomHashMap<GlobalPos,Coverage> coverage=new Object2ObjectLinkedOpenCustomHashMap<>(SpatialHash.POSITIONS);
    private int cachedCoverageRange=-1;
    private final ArrayDeque<Runnable> changes=new ArrayDeque<>();
    private final Set<GlobalPos> changedPositions=new LinkedHashSet<>();
    private final Map<Object,Set<AstralNetwork>> providerNetworks=new HashMap<>();
    private final ArrayDeque<AnchorAddress> validations=new ArrayDeque<>();
    private boolean topologyDirty,forceRebuild,stopping;
    private final Map<Set<AnchorAddress>,CachedComponent> cachedComponents=new HashMap<>();
    private record AnchorSettings(AnchorAddress address,NodeKind kind,int channel,int priority,boolean dimensional,boolean longRange,
                                  Direction facing,BlockPos providerPosition,Direction providerSide,boolean coverage,
                                  boolean collects,boolean stocks,boolean distributes,DistributionMode distribution,
                                  CompoundTag insertion,CompoundTag extraction) {}
    private record ComponentSignature(List<AnchorSettings> members,Set<AnchorSettings> competitors,
                                      Set<RuneSavedData.Link> links,int coverageRange,int relayRange,int remoteRange) {}
    private record CachedComponent(AstralNetwork network,ComponentSignature signature) {}
    private record ComponentPlan(Set<AnchorAddress> addresses,List<NetworkAnchor> nodes,
                                 ComponentSignature signature,AstralNetwork reusable) {}
    private int rotation;
    private record RouteKey(GlobalPos from,GlobalPos to,int channel,double range,boolean lineOfSight){
        @Override public int hashCode(){return routeHash(SpatialHash.pair(from,to),channel,range,lineOfSight);}
    }
    private record CachedRoute(List<GlobalPos> points,List<CrystalNodeBlockEntity> relays,List<net.minecraft.world.level.chunk.LevelChunk> chunks){
        private CachedRoute{points=List.copyOf(points);relays=List.copyOf(relays);chunks=List.copyOf(chunks);}
        int weight(){return points.size()+relays.size()+chunks.size();}
        boolean valid(ServerLevel level,int channel){
            for(int i=0;i<relays.size();i++){
                var node=relays.get(i);
                // Ticket demotion precedes the eventual unload callback and block-entity removal.
                if(!chunks.get(i).getFullStatus().isOrAfter(FullChunkStatus.FULL)
                        ||node.getLevel()!=level||node.isRemoved()||!node.enabled()||node.channel()!=channel||!transit(node))return false;
            }
            return true;
        }
    }
    // Path points, retained relay instances and chunk references count toward the state budget.
    // Chunk events, occlusion edits and graph rebuilds retire these references synchronously.
    private final WeightedLruCache<RouteKey,CachedRoute> routes=new WeightedLruCache<>(16384,262144,CachedRoute::weight);
    private record RouteOrigin(GlobalPos from,int channel,double range,boolean lineOfSight){
        @Override public int hashCode(){return routeHash(SpatialHash.POSITIONS.hashCode(from),channel,range,lineOfSight);}
    }
    private static int routeHash(int positions,int channel,double range,boolean lineOfSight){
        return 31*(31*(31*positions+channel)+Double.hashCode(range))+Boolean.hashCode(lineOfSight);
    }
    private final WeightedLruCache<RouteOrigin,ShortestPath.Tree<AnchorAddress>> routeTrees=
            new WeightedLruCache<>(8192,262144,ShortestPath.Tree::size);
    private final AsyncRouteTrees<AnchorAddress> preparedRouteTrees=new AsyncRouteTrees<>();
    private boolean routeSnapshotSight;
    private long preparedRouteOrigins,synchronousRouteOrigins;
    private int lastRelayRange=-1,lastRemoteRange=-1;
    private boolean lastSight=true;
    private record SightKey(GlobalPos from,GlobalPos to){
        @Override public int hashCode(){return SpatialHash.pair(from,to);}
    }
    private final Map<SightKey,Boolean> sight=new LinkedHashMap<>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<SightKey,Boolean> e){return size()>8192;}
    };
    private final Set<String> sightChunks=new HashSet<>();
    public static void visibilityChanged(ServerLevel level,BlockPos pos){
        if(!level.getServer().isSameThread())return;
        var manager=SERVERS.get(level.getServer());
        if(manager!=null&&!manager.stopping&&manager.sightChunks.contains(cell(GlobalPos.of(level.dimension(),pos),pos.getX()>>4,pos.getZ()>>4)))manager.invalidateSight();
    }
    private void invalidateSight(){coverage.clear();sight.clear();routes.clear();routeTrees.clear();preparedRouteTrees.invalidate();sightChunks.clear();topologyDirty=true;}
    private boolean visible(GlobalPos from,GlobalPos to){
        if(!from.dimension().equals(to.dimension()))return true; // Explicit dimensional bridges have no physical intervening blocks.
        if(!AstralConfig.requireLineOfSight.get())return true;
        var key=from.pos().compareTo(to.pos())<=0?new SightKey(from,to):new SightKey(to,from);
        return sight.computeIfAbsent(key,k->{var level=server.getLevel(k.from.dimension());return level!=null&&LineOfSight.clear(level,k.from.pos(),k.to.pos(),p->sightChunks.add(cell(k.from,p.getX()>>4,p.getZ()>>4)));});
    }
    private boolean allowed(NetworkAnchor a,NetworkAnchor b){return NetworkTopology.allowed(graphNode(a),graphNode(b),AstralConfig.relayRange.get(),AstralConfig.remoteRange.get())&&visible(location(a),location(b));}
    private NetworkManager(MinecraftServer server){this.server=server;this.runeTransfers=new DirectRuneTransfers(server);}
    public static NetworkManager get(MinecraftServer server){return SERVERS.computeIfAbsent(server,NetworkManager::new);}
    public static String id(GlobalPos pos){return pos.dimension().location()+"@"+pos.pos().asLong();}
    public static GlobalPos location(NetworkAnchor node){return node.address().position();}
    private static String cell(GlobalPos pos,int x,int z){return pos.dimension().location()+"/"+x+"/"+z;}
    public static void changed(ServerLevel level,BlockPos pos){var manager=get(level.getServer());if(!manager.stopping){manager.coverage.clear();manager.changedPositions.add(GlobalPos.of(level.dimension(),pos.immutable()));}}
    private void refresh(GlobalPos pos){
        ServerLevel level=server.getLevel(pos.dimension());if(level==null||!level.hasChunkAt(pos.pos()))return;
        AnchorAddress address=new AnchorAddress(pos,null);
        if(level.getBlockEntity(pos.pos()) instanceof CrystalNodeBlockEntity node&&!node.isRemoved()){nodes.put(address,node);topologyDirty=true;}
        else {if(nodes.remove(address)!=null)topologyDirty=true;RuneSavedData.get(server).removeLinks(address);}
        for(RuneSurface rune:RuneSurfaces.at(level,pos.pos())){
            if(rune.hostPresent()){nodes.put(rune.address(),rune);runeTransfers.track(rune);}
            else {nodes.remove(rune.address());RuneSurfaces.remove(level,pos.pos(),rune.facing());}
            topologyDirty=true;
        }
        for(Direction side:Direction.values()){
            var addressOnFace=new AnchorAddress(pos,side);
            if(RuneSavedData.get(server).surface(addressOnFace)==null&&nodes.remove(addressOnFace)!=null)topologyDirty=true;
        }
        for(AstralNetwork network:coveringNetworks(pos))network.invalidate(pos);
    }
    public NetworkAnchor resolve(AnchorAddress address){
        ServerLevel level=server.getLevel(address.position().dimension());if(level==null)return null;
        var pos=address.position().pos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return null;
        NetworkAnchor anchor=address.face()==null?(chunk.getBlockEntity(pos) instanceof CrystalNodeBlockEntity node?node:null):RuneSurfaces.get(level,pos,address.face());
        return anchor==null||anchor.isRemoved()?null:anchor;
    }
    public record LinkResult(boolean success,boolean linked,String message){}
    public LinkResult toggleLink(AnchorAddress first,AnchorAddress second){
        if(first.equals(second))return new LinkResult(false,false,"Select a different crystal or rune surface.");
        NetworkAnchor a=resolve(first),b=resolve(second);
        if(a==null||b==null)return new LinkResult(false,false,"Both endpoints must exist in loaded chunks.");
        RuneSavedData data=RuneSavedData.get(server);
        if(data.linked(first,second)){data.toggle(first,second);topologyDirty=true;return new LinkResult(true,false,"Link removed.");}
        if(!a.enabled()||!b.enabled())return new LinkResult(false,false,"Enable both endpoints before linking them.");
        if(a.channel()!=b.channel())return new LinkResult(false,false,"Both endpoints must use the same dye channel.");
        if(!allowed(a,b))
            return new LinkResult(false,false,"Link is out of range or needs two dimensionally attuned remote crystals.");
        nodes.put(first,a);nodes.put(second,b);data.toggle(first,second);topologyDirty=true;
        return new LinkResult(true,true,"Link established.");
    }
    public Set<AnchorAddress> links(AnchorAddress address){return RuneSavedData.get(server).links(address);}
    public List<NetworkAnchor> adjacent(NetworkAnchor anchor){return effectiveLinks.getOrDefault(anchor.address(),Set.of()).stream().sorted().map(nodes::get).filter(Objects::nonNull).filter(n->membership.get(n.address())==membership.get(anchor.address())&&allowed(anchor,n)).toList();}
    public static void blockChanged(BlockEvent event){if(event.getLevel() instanceof ServerLevel level)changed(level,event.getPos());}
    public static void chunkLoad(ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level))return;NetworkManager manager=get(level.getServer());if(manager.stopping)return;ChunkPos pos=event.getChunk().getPos();
        manager.invalidateSight();
        manager.changes.add(()->{
            if(!level.hasChunk(pos.x,pos.z))return;var chunk=level.getChunkSource().getChunkNow(pos.x,pos.z);if(chunk==null)return;
            for(var be:chunk.getBlockEntities().values())if(be instanceof CrystalNodeBlockEntity node){manager.nodes.put(node.address(),node);manager.topologyDirty=true;}
            for(RuneSurface rune:RuneSavedData.get(level.getServer()).surfaces(level.dimension(),pos)){
                if(rune.hostPresent()){manager.nodes.put(rune.address(),rune);manager.runeTransfers.track(rune);}else RuneSurfaces.remove(level,rune.getBlockPos(),rune.facing());manager.topologyDirty=true;
            }
            for(AstralNetwork network:manager.networks)network.rescanChunk(level,pos);
        });
    }
    public static void chunkUnload(ChunkEvent.Unload event){
        if(!(event.getLevel() instanceof ServerLevel level))return;NetworkManager manager=get(level.getServer());if(manager.stopping)return;ChunkPos chunk=event.getChunk().getPos();manager.invalidateSight();
        manager.changes.add(()->{
            for(NetworkAnchor node:manager.nodes.values())if(node instanceof RuneSurface rune&&rune.getLevel()==level&&new ChunkPos(rune.getBlockPos()).equals(chunk))rune.unloaded();
            boolean removed=manager.nodes.keySet().removeIf(a->a.position().dimension().equals(level.dimension())&&new ChunkPos(a.position().pos()).equals(chunk));
            manager.topologyDirty|=removed;for(AstralNetwork network:manager.networks)network.unloadChunk(level,chunk);
        });
    }
    public static void tick(ServerTickEvent.Post event){get(event.getServer()).tick();}
    private void tick(){
        if(stopping)return;
        if(lastSight!=AstralConfig.requireLineOfSight.get()){lastSight=AstralConfig.requireLineOfSight.get();invalidateSight();}
        if(lastRelayRange!=AstralConfig.relayRange.get()||lastRemoteRange!=AstralConfig.remoteRange.get()){lastRelayRange=AstralConfig.relayRange.get();lastRemoteRange=AstralConfig.remoteRange.get();topologyDirty=true;}
        for(int i=0;i<4096&&!changes.isEmpty();i++)changes.remove().run();
        for(int i=0;i<4096&&!changedPositions.isEmpty();i++){
            var iterator=changedPositions.iterator();var position=iterator.next();iterator.remove();refresh(position);
        }
        validateAnchors();if(topologyDirty)rebuild();preparedRouteTrees.drain();runeTransfers.tick();if(networks.isEmpty())return;
        int discovery=AstralConfig.discoveryBudget.get(),poll=AstralConfig.reconciliationBudget.get();
        for(int i=0;i<networks.size();i++){AstralNetwork network=networks.get((rotation+i)%networks.size());int work=discovery/(networks.size()-i),checks=poll/(networks.size()-i);network.tick(work,checks);discovery-=work;poll-=checks;}
        rotation=(rotation+1)%networks.size();if(server.getTickCount()%20==0)diagnostics();
    }
    private void validateAnchors(){
        for(int i=0;i<16&&!validations.isEmpty();i++){
            AnchorAddress address=validations.remove();NetworkAnchor node=nodes.get(address);if(node==null)continue;
            if(!node.isRemoved()){validations.add(address);continue;}
            discardInvalidAnchor(address,node);topologyDirty=true;
        }
    }
    private void discardInvalidAnchor(AnchorAddress address,NetworkAnchor node){
        nodes.remove(address,node);ServerLevel level=server.getLevel(address.position().dimension());
        if(level!=null&&level.hasChunkAt(address.position().pos())){
            if(node instanceof RuneSurface rune)RuneSurfaces.remove(level,rune.getBlockPos(),rune.facing());
            else RuneSavedData.get(server).removeLinks(address);
        }else if(node instanceof RuneSurface rune)rune.unloaded();
    }
    private static NetworkTopology.Node graphNode(NetworkAnchor node){var pos=node.address().position();return new NetworkTopology.Node(node.address().id(),pos.dimension().location().toString(),pos.pos().getX(),pos.pos().getY(),pos.pos().getZ(),node.channel(),node.longRange(),node.dimensional());}
    private AnchorSettings settings(NetworkAnchor node){
        return new AnchorSettings(node.address(),node.kind(),node.channel(),node.priority(),node.dimensional(),node.longRange(),
                node.facing(),node.providerPosition().immutable(),node.providerSide(),node.nearbyCoverage(),
                node.collects(),node.stocks(),node.distributes(),node.distributionMode(),
                node.networkInsertionFilter().save(server.registryAccess()),node.networkExtractionFilter().save(server.registryAccess()));
    }
    private static boolean sameInstances(List<NetworkAnchor> old,List<NetworkAnchor> current){
        if(old.size()!=current.size())return false;
        for(int i=0;i<old.size();i++)if(old.get(i)!=current.get(i))return false;
        return true;
    }
    private ComponentSignature signature(List<NetworkAnchor> component,Set<AnchorAddress> addresses,
                                         Map<AnchorAddress,AnchorSettings> settings,SpatialIndex nextSpatial,
                                         Map<AnchorAddress,List<RuneSavedData.Link>> incident){
        int radius=AstralConfig.coverageRange.get(),reach=radius*2,cells=(reach+15)/16;
        Set<AnchorSettings> competitors=new HashSet<>();
        // An unlinked crystal can still change which component owns a nearby inventory.
        for(NetworkAnchor member:component){
            GlobalPos pos=location(member);int cx=pos.pos().getX()>>4,cz=pos.pos().getZ()>>4;
            for(int x=-cells;x<=cells;x++)for(int z=-cells;z<=cells;z++)
                for(NetworkAnchor other:nextSpatial.at(pos,cx+x,cz+z)){
                    if(other instanceof RuneSurface||addresses.contains(other.address()))continue;
                    if(other.getBlockPos().distSqr(pos.pos())<=(double)reach*reach)competitors.add(settings.get(other.address()));
                }
        }
        Set<RuneSavedData.Link> internal=new HashSet<>();
        for(var address:addresses)for(var link:incident.getOrDefault(address,List.of()))
            if(addresses.contains(link.first())&&addresses.contains(link.second()))internal.add(link);
        return new ComponentSignature(component.stream().map(n->settings.get(n.address())).toList(),Set.copyOf(competitors),Set.copyOf(internal),
                radius,AstralConfig.relayRange.get(),AstralConfig.remoteRange.get());
    }
    private void rebuild(){
        topologyDirty=false;coverage.clear();routes.clear();routeTrees.clear();
        for(var entry:new ArrayList<>(nodes.entrySet()))if(entry.getValue().isRemoved())discardInvalidAnchor(entry.getKey(),entry.getValue());
        validations.clear();validations.addAll(nodes.keySet());
        Map<String,NetworkAnchor> byId=new TreeMap<>();List<NetworkTopology.Node> graph=new ArrayList<>();
        SpatialIndex nextSpatial=new SpatialIndex();Map<AnchorAddress,AnchorSettings> settings=new HashMap<>();
        // Standalone rune transfers do not need an inventory index or crafting coordinator.
        // Explicitly linked rune faces still participate in their existing graph component.
        Set<RuneSavedData.Link> links=new HashSet<>(RuneSavedData.get(server).links());
        Set<AnchorAddress> linkedFaces=new HashSet<>();
        for(var link:links){linkedFaces.add(link.first());linkedFaces.add(link.second());}
        for(var entry:nodes.entrySet().stream().filter(e->e.getValue().enabled()
                &&(!(e.getValue() instanceof RuneSurface)||linkedFaces.contains(e.getKey())))
                .sorted(Map.Entry.comparingByKey()).limit(AstralConfig.maxNodes.get()).toList()){
            var node=entry.getValue();var pos=entry.getKey().position();byId.put(entry.getKey().id(),node);graph.add(graphNode(node));
            settings.put(entry.getKey(),settings(node));
            nextSpatial.add(node);
        }
        connectNearby(links,byId.values().stream().filter(NetworkManager::transit).toList(),AstralConfig.relayRange.get());
        connectNearby(links,byId.values().stream().filter(n->transit(n)&&n.longRange()).toList(),AstralConfig.remoteRange.get());
        links.removeIf(link->{var a=byId.get(link.first().id());var b=byId.get(link.second().id());return a==null||b==null||!allowed(a,b);});
        effectiveLinks.clear();
        for(var link:links){var a=byId.get(link.first().id());var b=byId.get(link.second().id());
            if(a==null||b==null||!allowed(a,b))continue;
            effectiveLinks.computeIfAbsent(link.first(),k->new TreeSet<>()).add(link.second());
            effectiveLinks.computeIfAbsent(link.second(),k->new TreeSet<>()).add(link.first());
        }
        Map<AnchorAddress,List<RuneSavedData.Link>> incident=new HashMap<>();
        for(var link:links){incident.computeIfAbsent(link.first(),k->new ArrayList<>()).add(link);incident.computeIfAbsent(link.second(),k->new ArrayList<>()).add(link);}
        List<NetworkTopology.Edge> edges=links.stream().map(link->new NetworkTopology.Edge(link.first().id(),link.second().id())).toList();
        List<ComponentPlan> plans=new ArrayList<>();Set<AstralNetwork> retained=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Set<String> component:NetworkTopology.components(graph,edges,AstralConfig.relayRange.get(),AstralConfig.remoteRange.get())){
            List<NetworkAnchor> componentNodes=component.stream().sorted().map(byId::get).toList();
            Set<AnchorAddress> addresses=componentNodes.stream().map(NetworkAnchor::address).collect(java.util.stream.Collectors.toUnmodifiableSet());
            ComponentSignature signature=signature(componentNodes,addresses,settings,nextSpatial,incident);
            CachedComponent previous=cachedComponents.get(addresses);AstralNetwork reusable=null;
            if(!forceRebuild&&previous!=null&&previous.signature().equals(signature)&&sameInstances(previous.network().nodes(),componentNodes)){
                reusable=previous.network();retained.add(reusable);
            }
            plans.add(new ComponentPlan(addresses,componentNodes,signature,reusable));
        }
        // Refund changed components against their old ownership map before installing the new graph.
        for(AstralNetwork network:networks)if(!retained.contains(network))network.close();
        networks.clear();membership.clear();spatial.replace(nextSpatial);cachedComponents.clear();
        for(ComponentPlan plan:plans){
            AstralNetwork network=plan.reusable()!=null?plan.reusable():new AstralNetwork(this,server,plan.nodes());
            networks.add(network);plan.nodes().forEach(n->membership.put(n.address(),network));
            cachedComponents.put(plan.addresses(),new CachedComponent(network,plan.signature()));
        }
        captureRouteGraph();forceRebuild=false;
    }
    private static boolean transit(NetworkAnchor node){
        return node instanceof CrystalNodeBlockEntity && switch(node.kind()){
            case RELAY,NEXUS,STORAGE,REMOTE,GATEWAY -> true;default -> false;
        };
    }
    private record RelayCell(String dimension,int channel,int x,int y,int z){}
    private void connectNearby(Set<RuneSavedData.Link> links,List<NetworkAnchor> anchors,int range){
        Map<RelayCell,List<NetworkAnchor>> cells=new HashMap<>();
        for(var a:anchors){var pos=a.getBlockPos();String dimension=location(a).dimension().location().toString();int x=Math.floorDiv(pos.getX(),range),y=Math.floorDiv(pos.getY(),range),z=Math.floorDiv(pos.getZ(),range);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)
                for(var b:cells.getOrDefault(new RelayCell(dimension,a.channel(),x+dx,y+dy,z+dz),List.of()))autoLink(links,a,b);
            cells.computeIfAbsent(new RelayCell(dimension,a.channel(),x,y,z),k->new ArrayList<>()).add(a);
        }
    }
    private void autoLink(Set<RuneSavedData.Link> links,NetworkAnchor a,NetworkAnchor b){
        if(!transit(b)||a.address().equals(b.address())||!a.address().position().dimension().equals(b.address().position().dimension()))return;
        if(allowed(a,b))links.add(new RuneSavedData.Link(a.address(),b.address()));
    }
    /** Direct first, then shortest-distance through loaded same-channel crystals. Never loads chunks. */
    public List<GlobalPos> route(GlobalPos from,GlobalPos to,int channel,double range){
        if(!from.dimension().equals(to.dimension()))return List.of();
        ServerLevel level=server.getLevel(from.dimension());
        if(level==null||!level.hasChunkAt(from.pos())||!level.hasChunkAt(to.pos()))return List.of();
        double squared=(double)range*range;
        if(from.pos().distSqr(to.pos())<=squared&&visible(from,to))return List.of(from,to);
        var key=new RouteKey(from,to,channel,range,AstralConfig.requireLineOfSight.get());var cached=routes.get(key);
        if(cached!=null&&cached.valid(level,channel))return cached.points();
        var result=findRoute(from,to,channel,squared);var retained=captureRoute(result,level);
        if(retained!=null){routes.put(key,retained);return result;}
        routes.remove(key);return List.of();
    }
    private CachedRoute captureRoute(List<GlobalPos> path,ServerLevel level){
        List<CrystalNodeBlockEntity> relays=new ArrayList<>(Math.max(0,path.size()-2));
        List<net.minecraft.world.level.chunk.LevelChunk> chunks=new ArrayList<>(Math.max(0,path.size()-2));
        for(int i=1;i<path.size()-1;i++){
            var position=path.get(i).pos();var chunk=level.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4);
            if(chunk==null||!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                    ||!(chunk.getBlockEntity(position) instanceof CrystalNodeBlockEntity node))return null;
            relays.add(node);chunks.add(chunk);
        }
        return new CachedRoute(path,relays,chunks);
    }
    private boolean validRoute(List<GlobalPos> path,ServerLevel level,int channel){
        // Occlusion edits and chunk events synchronously clear routes; only live relay state needs rechecking.
        for(int i=0;i<path.size();i++){
            var position=path.get(i).pos();
            var chunk=level.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4);if(chunk==null)return false;
            if(i>0&&i<path.size()-1){
                // Revalidate the actual loaded block entity once, without redundant chunk lookups.
                if(!(chunk.getBlockEntity(position) instanceof CrystalNodeBlockEntity node)
                        ||node.isRemoved()||!transit(node)||!node.enabled()||node.channel()!=channel)return false;
            }
        }
        return true;
    }
    private boolean routeCandidate(NetworkAnchor node,GlobalPos from,int channel,double squared){
        return transit(node)&&node.enabled()&&node.channel()==channel&&location(node).dimension().equals(from.dimension())
                &&node.getBlockPos().distSqr(from.pos())<=squared&&membership.containsKey(node.address())&&!node.isRemoved();
    }
    private List<NetworkAnchor> routeStarts(GlobalPos from,int channel,double squared){
        int radius=(int)Math.ceil(Math.sqrt(squared));
        int minX=(int)Math.floor(((double)from.pos().getX()-radius)/16),maxX=(int)Math.floor(((double)from.pos().getX()+radius)/16);
        int minZ=(int)Math.floor(((double)from.pos().getZ()-radius)/16),maxZ=(int)Math.floor(((double)from.pos().getZ()+radius)/16);
        long buckets=(long)(maxX-minX+1)*(maxZ-minZ+1);
        List<NetworkAnchor> candidates=new ArrayList<>();
        // Very large configured binding ranges should scan nodes, not millions of empty chunk buckets.
        if(buckets>nodes.size()){
            for(var node:nodes.values())if(routeCandidate(node,from,channel,squared))candidates.add(node);
        }else{
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
                for(var node:spatial.at(from,x,z))if(routeCandidate(node,from,channel,squared))candidates.add(node);
        }
        candidates.sort(Comparator.comparing(NetworkAnchor::address));return candidates;
    }
    private List<AnchorAddress> routeNeighbors(AnchorAddress address,GlobalPos from,int channel){
        List<AnchorAddress> result=new ArrayList<>();
        // Effective links are already ordered, and only crystal nodes can carry a transfer through the graph.
        for(var neighbor:effectiveLinks.getOrDefault(address,Set.of())){
            var node=nodes.get(neighbor);
            if(node!=null&&transit(node)&&node.enabled()&&node.channel()==channel
                    &&neighbor.position().dimension().equals(from.dimension())&&!node.isRemoved()
                    &&visible(address.position(),neighbor.position()))result.add(neighbor);
        }
        return result;
    }
    /** Live world, channel and LOS checks stay here; workers see only this immutable weighted graph. */
    private void captureRouteGraph(){
        Map<AnchorAddress,Map<AnchorAddress,Double>> graph=new LinkedHashMap<>();
        for(var address:effectiveLinks.keySet().stream().sorted().toList()){
            var node=nodes.get(address);if(node==null||!transit(node)||!node.enabled()||node.isRemoved())continue;
            Map<AnchorAddress,Double> edges=new LinkedHashMap<>();
            for(var neighbor:routeNeighbors(address,address.position(),node.channel()))
                edges.put(neighbor,Math.sqrt(address.position().pos().distSqr(neighbor.position().pos())));
            graph.put(address,edges);
        }
        routeSnapshotSight=AstralConfig.requireLineOfSight.get();preparedRouteTrees.reset(graph);
    }
    private ShortestPath.Tree<AnchorAddress> routeTree(GlobalPos from,int channel,double squared,boolean allowPrepared){
        Map<AnchorAddress,Double> starts=new LinkedHashMap<>();
        for(var node:routeStarts(from,channel,squared))if(visible(from,location(node)))
            starts.put(node.address(),Math.sqrt(node.getBlockPos().distSqr(from.pos())));
        if(allowPrepared&&!starts.isEmpty()&&routeSnapshotSight==AstralConfig.requireLineOfSight.get()){
            Map<AnchorAddress,ShortestPath.Tree<AnchorAddress>> prepared=new LinkedHashMap<>();
            for(var root:starts.keySet()){
                var tree=preparedRouteTrees.request(root);if(tree!=null)prepared.put(root,tree);
            }
            if(prepared.size()==starts.size()){preparedRouteOrigins++;return ShortestPath.combine(starts,prepared);}
        }
        // First use remains immediate. Never join workers or postpone an otherwise due transfer.
        synchronousRouteOrigins++;return ShortestPath.tree(starts,address->routeNeighbors(address,from,channel),
                (a,b)->Math.sqrt(a.position().pos().distSqr(b.position().pos())));
    }
    private List<GlobalPos> treeRoute(ShortestPath.Tree<AnchorAddress> tree,GlobalPos from,GlobalPos to,int channel,double squared){
        Map<AnchorAddress,Double> exits=new LinkedHashMap<>();
        for(var node:routeStarts(to,channel,squared))if(visible(location(node),to))
            exits.put(node.address(),Math.sqrt(node.getBlockPos().distSqr(to.pos())));
        var selected=tree.path(exits.keySet(),address->exits.getOrDefault(address,Double.POSITIVE_INFINITY));
        if(selected.isEmpty()||selected.size()>128)return List.of();
        List<GlobalPos> path=new ArrayList<>();path.add(from);
        for(var address:selected)if(!path.getLast().equals(address.position()))path.add(address.position());
        if(!path.getLast().equals(to))path.add(to);return List.copyOf(path);
    }
    private List<GlobalPos> findRoute(GlobalPos from,GlobalPos to,int channel,double squared){
        var key=new RouteOrigin(from,channel,Math.sqrt(squared),AstralConfig.requireLineOfSight.get());
        var tree=routeTrees.get(key);
        if(tree==null){tree=routeTree(from,channel,squared,true);routeTrees.put(key,tree);}
        var result=treeRoute(tree,from,to,channel,squared);
        if(!validRoute(result,server.getLevel(from.dimension()),channel)){
            // Prepared/cached trees can predate a removal or retune waiting in the topology queue.
            routeTrees.remove(key);tree=routeTree(from,channel,squared,false);routeTrees.put(key,tree);
            result=treeRoute(tree,from,to,channel,squared);
        }
        return result;
    }

    public NetworkAnchor owner(GlobalPos pos){
        NetworkAnchor closest=null;int bestRank=-1;double best=Double.MAX_VALUE;int radius=AstralConfig.coverageRange.get(),cells=(radius+15)/16;
        int cx=pos.pos().getX()>>4,cz=pos.pos().getZ()>>4;
        for(int x=-cells;x<=cells;x++)for(int z=-cells;z<=cells;z++)for(var node:spatial.at(pos,cx+x,cz+z)){
            if(node instanceof RuneSurface||!node.enabled()||node.isRemoved())continue;
            boolean attached=node.attachedTo(pos);if(!node.nearbyCoverage()&&!attached)continue;
            double distance=node.getBlockPos().distSqr(pos.pos());int rank=!node.nearbyCoverage()?2:attached&&(node.collects()||node.stocks()||node.distributes())?1:0;
            if(distance<=(double)radius*radius&&(rank>bestRank||rank==bestRank&&(distance<best||distance==best&&(closest==null||node.priority()>closest.priority()||node.priority()==closest.priority()&&node.address().compareTo(closest.address())<0)))){best=distance;closest=node;bestRank=rank;}
        }
        return closest;
    }
    /** A linked face reserves its sided view for its own component; automatic coverage keeps global nearest ownership. */
    public NetworkAnchor owner(GlobalPos pos,Set<AnchorAddress> component){
        ServerLevel level=server.getLevel(pos.dimension());NetworkAnchor selected=null;
        if(level!=null&&level.hasChunkAt(pos.pos()))for(RuneSurface rune:RuneSurfaces.at(level,pos.pos())){
            if(!component.contains(rune.address())||!rune.enabled()||rune.isRemoved())continue;
            if(selected==null||rune.priority()>selected.priority()||rune.priority()==selected.priority()&&rune.address().compareTo(selected.address())<0)selected=rune;
        }
        if(selected!=null)return selected;
        NetworkAnchor nearest=owner(pos);return nearest!=null&&component.contains(nearest.address())?nearest:null;
    }
    public AstralNetwork networkAt(GlobalPos pos){return membership.get(new AnchorAddress(pos,null));}
    public AstralNetwork networkAt(AnchorAddress address){return membership.get(address);}
    public boolean canAccess(ServerPlayer player,GlobalPos origin,boolean remote){
        return accessFailure(player,origin,remote)==null;
    }
    /** Null means access is allowed; checking availability never loads the destination chunk. */
    public String accessFailure(ServerPlayer player,GlobalPos origin,boolean remote){
        ServerLevel level=server.getLevel(origin.dimension());
        if(level==null)return "The bound Nexus dimension is unavailable. Bind this Astral Nexus to another Storage Nexus.";
        if(!level.hasChunkAt(origin.pos()))return "The bound Nexus chunk is unloaded. Load that area before using remote access.";
        if(!(level.getBlockEntity(origin.pos()) instanceof CrystalNodeBlockEntity n) || n.kind()!=NodeKind.NEXUS && n.kind()!=NodeKind.STORAGE && n.kind()!=NodeKind.BUFFER)
            return "The bound Nexus is no longer there. Use this Astral Nexus on a Storage Nexus to bind it again.";
        boolean sameDimension=player.level().dimension().equals(origin.dimension());
        double distance=player.distanceToSqr(origin.pos().getCenter());
        if(!remote)return sameDimension&&distance<=64?null:"Move within 8 blocks of this Nexus to open it.";
        boolean bound=false,attuned=false;
        for(ItemStack stack:player.getInventory().items)if(stack.is(AstralContent.ASTRAL_NEXUS.get())&&origin.equals(RemoteData.bound(stack))){bound=true;attuned|=RemoteData.attuned(stack);}
        ItemStack offhand=player.getOffhandItem();
        if(offhand.is(AstralContent.ASTRAL_NEXUS.get())&&origin.equals(RemoteData.bound(offhand))){bound=true;attuned|=RemoteData.attuned(offhand);}
        if(!bound)return "Keep an Astral Nexus bound to this Storage Nexus in your inventory.";
        if(RemoteRules.access(sameDimension,attuned,distance,AstralConfig.remoteRange.get()))return null;
        if(!sameDimension)return "This Astral Nexus is not dimensionally attuned.";
        return "The bound Nexus is outside the same-dimension range of "+AstralConfig.remoteRange.get()+" blocks. Move closer; dimensional attunement does not extend this range.";
    }
    public static void open(ServerPlayer player,GlobalPos origin){open(player,origin,false);}
    private static void open(ServerPlayer player,GlobalPos origin,boolean remote){
        NetworkManager manager=get(player.server);
        String failure=manager.accessFailure(player,origin,remote); if(failure!=null){player.displayClientMessage(Component.literal(failure),true);return;}
        if(manager.networkAt(origin)==null){manager.refresh(origin);manager.rebuild();}
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new NexusMenu(id,inventory,origin,remote),serverTitle(player,origin,remote)));
    }
    private static Component serverTitle(ServerPlayer player,GlobalPos origin,boolean remote){
        var level=player.server.getLevel(origin.dimension());
        return !remote&&level!=null?level.getBlockState(origin.pos()).getBlock().getName():Component.translatable("block.astral_repository.storage_nexus");
    }
    public static void remote(ServerPlayer player,ItemStack stack){
        GlobalPos origin=RemoteData.bound(stack);
        if(origin==null){player.displayClientMessage(Component.literal("Bind this Astral Nexus by using it on a Storage Nexus."),true);return;}
        open(player,origin,true);
    }

    private void diagnostics(){
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            if(!GogglesEquipment.isWearing(player))continue;
            List<NetworkPackets.DiagnosticNode> visible=new ArrayList<>();List<NetworkPackets.DiagnosticEdge> edges=new ArrayList<>();Set<AnchorAddress> shown=new HashSet<>();
            for(var entry:nodes.entrySet()){
                var pos=entry.getKey().position();if(!pos.dimension().equals(player.level().dimension())||player.distanceToSqr(pos.pos().getCenter())>4096||visible.size()>=128)continue;
                var node=entry.getValue();AstralNetwork network=membership.get(entry.getKey());String text=node.kind()+" · "+(node.channel()<0?"neutral":net.minecraft.world.item.DyeColor.byId(node.channel()).getName())+" · "+node.distributionMode();
                if(network!=null)text+="\n"+network.status();visible.add(new NetworkPackets.DiagnosticNode(pos.pos(),AstralNetwork.color(node),text));shown.add(entry.getKey());
            }
            for(RuneSavedData.Link link:effectiveLinks.entrySet().stream().flatMap(e->e.getValue().stream().filter(b->e.getKey().compareTo(b)<0).map(b->new RuneSavedData.Link(e.getKey(),b))).toList())if(shown.contains(link.first())&&shown.contains(link.second())&&edges.size()<512&&membership.get(link.first())==membership.get(link.second())&&NetworkTopology.allowed(graphNode(nodes.get(link.first())),graphNode(nodes.get(link.second())),AstralConfig.relayRange.get(),AstralConfig.remoteRange.get()))
                edges.add(new NetworkPackets.DiagnosticEdge(link.first().position().pos(),link.second().position().pos(),AstralNetwork.color(nodes.get(link.first()))));
            for(var network:networks)for(var processor:network.crafting().processorLabels().entrySet()){if(visible.size()>=256)break;if(processor.getKey().dimension().equals(player.level().dimension())&&player.distanceToSqr(processor.getKey().pos().getCenter())<4096)visible.add(new NetworkPackets.DiagnosticNode(processor.getKey().pos(),0xE8C987,processor.getValue()));}
            PacketDistributor.sendToPlayer(player,new NetworkPackets.Diagnostics(visible,edges));
        }
    }
    public static void datapacks(com.cappleapple.astralrepository.platform.event.OnDatapackSyncEvent event){if(event.getPlayer()!=null)return;NetworkManager manager=SERVERS.get(event.getPlayerList().getServer());if(manager!=null){manager.coverage.clear();manager.topologyDirty=true;manager.forceRebuild=true;}}
    void watchProvider(Object identity,AstralNetwork network){providerNetworks.computeIfAbsent(identity,k->new HashSet<>()).add(network);}
    void unwatchProvider(Object identity,AstralNetwork network){var watchers=providerNetworks.get(identity);if(watchers!=null&&watchers.remove(network)&&watchers.isEmpty())providerNetworks.remove(identity);}
    public void providerIdentitiesChanged(Set<Object> identities){
        runeTransfers.invalidateIdentities(identities);
        for(Object identity:identities)notifyIdentityWatchers(identity);
    }
    /** Notify the union of two stable endpoint identity sets without allocating a merged set. */
    public void providerIdentitiesChanged(Set<Object> identities,Set<Object> additional){
        providerIdentitiesChanged(identities);
        for(Object identity:additional)if(!identities.contains(identity)){
            runeTransfers.invalidateIdentity(identity);notifyIdentityWatchers(identity);
        }
    }
    private void notifyIdentityWatchers(Object identity){
        var watchers=providerNetworks.get(identity);
        if(watchers!=null)for(AstralNetwork network:watchers)network.providerIdentityChanged(identity);
    }
    private Coverage coverageAt(GlobalPos pos){
        if(networks.isEmpty())return NO_COVERAGE;
        int radius=AstralConfig.coverageRange.get();if(cachedCoverageRange!=radius){cachedCoverageRange=radius;coverage.clear();}
        var cached=coverage.getAndMoveToLast(pos);
        if(cached==null){
            NetworkAnchor closest=owner(pos);AstralNetwork nearest=closest==null?null:membership.get(closest.address());
            List<CoveredFace> faces=new ArrayList<>();ServerLevel level=server.getLevel(pos.dimension());
            if(level!=null&&level.hasChunkAt(pos.pos()))for(RuneSurface surface:RuneSurfaces.at(level,pos.pos())){
                AstralNetwork network=membership.get(surface.address());if(network!=null)faces.add(new CoveredFace(surface,network));
            }
            cached=new Coverage(nearest,List.copyOf(faces));
            coverage.putAndMoveToLast(GlobalPos.of(pos.dimension(),pos.pos().immutable()),cached);
            if(coverage.size()>32768)coverage.removeFirst();
        }
        return cached;
    }
    private Set<AstralNetwork> coveringNetworks(GlobalPos pos){
        Coverage cached=coverageAt(pos);
        if(cached.faces.isEmpty())return cached.nearest==null?Set.of():Set.of(cached.nearest);
        Set<AstralNetwork> result=new HashSet<>();if(cached.nearest!=null)result.add(cached.nearest);
        for(var face:cached.faces)if(face.surface.enabled()&&!face.surface.isRemoved())result.add(face.network);
        return result;
    }
    public static void providerChanged(ServerLevel level,BlockPos pos){
        NetworkManager manager=SERVERS.get(level.getServer());if(manager==null||!level.getServer().isSameThread())return;
        GlobalPos location=GlobalPos.of(level.dimension(),pos);
        Coverage cached=manager.coverageAt(location);
        if(cached.nearest!=null)cached.nearest.providerChanged(location);
        // There are at most six faces. Remember delivered faces in bits instead of allocating
        // a set for every inventory mutation; enabled/removed state remains live on each call.
        int notified=0;
        for(int i=0;i<cached.faces.size();i++){
            CoveredFace face=cached.faces.get(i);
            if(face.network==cached.nearest||!face.surface.enabled()||face.surface.isRemoved())continue;
            boolean duplicate=false;
            for(int earlier=0;earlier<i;earlier++)if((notified&(1<<earlier))!=0&&cached.faces.get(earlier).network==face.network){duplicate=true;break;}
            if(!duplicate){face.network.providerChanged(location);notified|=1<<i;}
        }
    }
    public static void stop(ServerStoppingEvent event){
        NetworkManager manager=SERVERS.get(event.getServer());if(manager==null)return;
        // Chunk unloads continue during shutdown; keep this closed manager until ServerStopped.
        manager.stopping=true;manager.coverage.clear();manager.preparedRouteTrees.close();manager.networks.forEach(AstralNetwork::close);
    }
    public static void stopped(ServerStoppedEvent event){
        NetworkManager manager=SERVERS.remove(event.getServer());if(manager!=null)manager.preparedRouteTrees.close();
    }
}