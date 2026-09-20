package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.RuneSurface;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned rune layers and undirected explicit links, including currently unloaded endpoints. */
public final class RuneSavedData extends SavedData {
    public record Link(AnchorAddress first,AnchorAddress second){
        public Link { if(first.compareTo(second)>0){AnchorAddress swap=first;first=second;second=swap;}if(first.equals(second))throw new IllegalArgumentException("Self link"); }
        public boolean contains(AnchorAddress address){return first.equals(address)||second.equals(address);}
        public AnchorAddress other(AnchorAddress address){return first.equals(address)?second:first;}
    }
    private final Map<AnchorAddress,RuneSurface> surfaces=new LinkedHashMap<>();
    private final Set<Link> links=new LinkedHashSet<>();
    private record ChunkKey(ResourceKey<Level> dimension,long position){}
    private final Map<GlobalPos,List<RuneSurface>> byPosition=SpatialHash.positions();
    private final Map<ChunkKey,List<RuneSurface>> byChunk=new HashMap<>();
    private final Map<AnchorAddress,Set<AnchorAddress>> adjacent=new HashMap<>();
    private List<RuneSurface> surfaceSnapshot;
    private Set<Link> linkSnapshot;
    private static ChunkKey chunk(GlobalPos pos){return new ChunkKey(pos.dimension(),ChunkPos.asLong(pos.pos()));}
    public static RuneSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RuneSavedData::new,(tag,registries)->load(server,tag,registries)),"astral_repository_runes");}
    public Collection<RuneSurface> surfaces(){if(surfaceSnapshot==null)surfaceSnapshot=List.copyOf(surfaces.values());return surfaceSnapshot;}
    /** Stable immutable snapshots, rebuilt only when a surface is added or removed. */
    public List<RuneSurface> surfaces(GlobalPos position){return byPosition.getOrDefault(position,List.of());}
    public List<RuneSurface> surfaces(ResourceKey<Level> dimension,ChunkPos chunk){return byChunk.getOrDefault(new ChunkKey(dimension,chunk.toLong()),List.of());}
    public RuneSurface surface(AnchorAddress address){return surfaces.get(address);}
    private void storeSurface(RuneSurface rune){
        RuneSurface old=surfaces.put(rune.address(),rune);if(old!=null)unindex(old);
        append(byPosition,rune.address().position(),rune);append(byChunk,chunk(rune.address().position()),rune);surfaceSnapshot=null;
    }
    private void unindex(RuneSurface rune){remove(byPosition,rune.address().position(),rune);remove(byChunk,chunk(rune.address().position()),rune);surfaceSnapshot=null;}
    private static <K,V> void append(Map<K,List<V>> index,K key,V value){var values=new ArrayList<>(index.getOrDefault(key,List.of()));values.add(value);index.put(key,List.copyOf(values));}
    private static <K,V> void remove(Map<K,List<V>> index,K key,V value){var values=new ArrayList<>(index.getOrDefault(key,List.of()));values.remove(value);if(values.isEmpty())index.remove(key);else index.put(key,List.copyOf(values));}
    public void addSurface(RuneSurface rune){storeSurface(rune);setDirty();}
    public boolean removeSurface(AnchorAddress address){RuneSurface removed=surfaces.remove(address);if(removed==null)return false;unindex(removed);removed.markRemoved();removeLinks(address);setDirty();return true;}
    public Set<Link> links(){if(linkSnapshot==null)linkSnapshot=Set.copyOf(links);return linkSnapshot;}
    public Set<AnchorAddress> links(AnchorAddress address){return adjacent.getOrDefault(address,Set.of());}
    public boolean linked(AnchorAddress first,AnchorAddress second){return !first.equals(second)&&links.contains(new Link(first,second));}
    private void adjacency(AnchorAddress from,AnchorAddress to,boolean add){var values=new LinkedHashSet<>(adjacent.getOrDefault(from,Set.of()));if(add)values.add(to);else values.remove(to);if(values.isEmpty())adjacent.remove(from);else adjacent.put(from,Collections.unmodifiableSet(values));}
    private void indexLink(Link link,boolean add){adjacency(link.first,link.second,add);adjacency(link.second,link.first,add);linkSnapshot=null;}
    public boolean toggle(AnchorAddress first,AnchorAddress second){Link link=new Link(first,second);boolean added=!links.remove(link);if(added)links.add(link);indexLink(link,added);setDirty();return added;}
    public void removeLinks(AnchorAddress address){var neighbors=links(address);if(neighbors.isEmpty())return;for(var other:neighbors){Link link=new Link(address,other);links.remove(link);indexLink(link,false);}setDirty();}
    public static RuneSavedData load(MinecraftServer server,CompoundTag tag,HolderLookup.Provider registries){
        RuneSavedData result=new RuneSavedData();ListTag runes=tag.getList("Runes",Tag.TAG_COMPOUND);
        for(int i=0;i<runes.size();i++)try{RuneSurface rune=RuneSurface.load(server,runes.getCompound(i),registries);result.storeSurface(rune);if(runes.getCompound(i).getInt("LayerVersion")<2)result.setDirty();}catch(RuntimeException ignored){}
        ListTag edges=tag.getList("Links",Tag.TAG_COMPOUND);for(int i=0;i<edges.size();i++)try{CompoundTag edge=edges.getCompound(i);Link link=new Link(AnchorAddress.load(edge.getCompound("First")),AnchorAddress.load(edge.getCompound("Second")));if(result.links.add(link))result.indexLink(link,true);}catch(RuntimeException ignored){}
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        ListTag runes=new ListTag();for(RuneSurface rune:surfaces.values())runes.add(rune.save(registries));tag.put("Runes",runes);
        ListTag edges=new ListTag();for(Link link:links){CompoundTag edge=new CompoundTag();edge.put("First",link.first.save());edge.put("Second",link.second.save());edges.add(edge);}tag.put("Links",edges);return tag;
    }
}