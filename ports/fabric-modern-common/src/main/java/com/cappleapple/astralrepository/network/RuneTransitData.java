package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;

/** Server-owned resources in flight. Buckets visit only arrivals due now, never every live packet. */
public final class RuneTransitData extends SavedData {
    public record Endpoint(GlobalPos position,Direction side,String provider,Object live,AnchorAddress policyAnchor) {
        public Endpoint(GlobalPos position,Direction side,String provider,Object live){this(position,side,provider,live,live instanceof RuneRouting.Policy policy?policy.policyAnchor():null);}
    }
    public record Flight(UUID rune,Endpoint from,Endpoint to,ResourceKey resource,long amount) {}
    private final NavigableMap<Long,List<Flight>> due=new TreeMap<>();
    private final Map<GlobalPos,Map<ResourceKey,Long>> incoming=SpatialHash.positions();
    private final Map<UUID,Map<ResourceKey,Long>> byRune=new HashMap<>();
    private long clock;
    private boolean relative;
    public static RuneTransitData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(com.cappleapple.astralrepository.port.NbtCodecs.savedType("astral_repository_rune_transit",RuneTransitData::new,server.registryAccess(),RuneTransitData::load,(data,registries)->data.save(new CompoundTag(),registries)));}
    public long pendingAmount(GlobalPos destination,ResourceKey resource){return incoming.getOrDefault(destination,Map.of()).getOrDefault(resource,0L);}
    public long pendingAmount(UUID rune,ResourceKey resource){return byRune.getOrDefault(rune,Map.of()).getOrDefault(resource,0L);}
    public void enqueue(long deadline,Flight flight){
        if(flight.amount<=0)throw new IllegalArgumentException("Empty rune shipment");
        due.computeIfAbsent(deadline,ignored->new ArrayList<>()).add(flight);
        adjust(incoming,flight.to.position,flight.resource,flight.amount);adjust(byRune,flight.rune,flight.resource,flight.amount);setDirty();
    }
    public void tick(long now,Consumer<Flight> arrival){
        clock=now;
        if(relative){
            var restored=new TreeMap<Long,List<Flight>>();
            for(var entry:due.entrySet())restored.put(now+entry.getKey(),entry.getValue());
            due.clear();due.putAll(restored);relative=false;
        }
        // Mark dirty while time elapses so a clean shutdown saves the remaining duration.
        if(!due.isEmpty())setDirty();
        while(!due.isEmpty()&&due.firstKey()<=now){
            var bucket=due.pollFirstEntry().getValue();
            for(var flight:bucket){
                adjust(incoming,flight.to.position,flight.resource,-flight.amount);adjust(byRune,flight.rune,flight.resource,-flight.amount);
                arrival.accept(flight);
            }
        }
    }
    private static <K> void adjust(Map<K,Map<ResourceKey,Long>> totals,K owner,ResourceKey key,long amount){
        var values=totals.computeIfAbsent(owner,ignored->new HashMap<>());long next=values.getOrDefault(key,0L)+amount;
        if(next<=0)values.remove(key);else values.put(key,next);if(values.isEmpty())totals.remove(owner);
    }
    public static RuneTransitData load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new RuneTransitData();data.relative=true;var entries=tag.getListOrEmpty("Flights");
        for(int i=0;i<entries.size();i++){var value=entries.getCompoundOrEmpty(i);try{
            ResourceKey resource;
            if(value.contains("Item"))resource=new ItemKey(com.cappleapple.astralrepository.port.NbtCodecs.item(registries,value.getCompoundOrEmpty("Item")));
            else if(value.contains("Fluid"))resource=new FluidKey(com.cappleapple.astralrepository.port.NbtCodecs.fluid(registries,value.getCompoundOrEmpty("Fluid")));
            else resource=new ResourceKinds.ScalarKey(Identifier.parse(value.getStringOr("Type","")),Identifier.parse(value.getStringOr("Resource","")));
            data.enqueue(Math.max(0,value.getLongOr("Remaining",0L)),new Flight(value.read("Rune",net.minecraft.core.UUIDUtil.CODEC).orElseThrow(),readEndpoint(value.getCompoundOrEmpty("From")),readEndpoint(value.getCompoundOrEmpty("To")),resource,value.getLongOr("Amount",0L)));
        }catch(RuntimeException failure){AstralRepository.LOGGER.error("Invalid saved rune shipment {}",i,failure);}}
        return data;
    }
    private static Endpoint readEndpoint(CompoundTag tag){
        var pos=GlobalPos.of(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,Identifier.parse(tag.getStringOr("Dimension",""))),BlockPos.of(tag.getLongOr("Position",0L)));
        AnchorAddress policy=null;
        if(tag.contains("Policy"))policy=AnchorAddress.load(tag.getCompoundOrEmpty("Policy"));
        return new Endpoint(pos,tag.contains("Side")?Direction.byName(tag.getStringOr("Side","")):null,tag.getStringOr("Provider",""),null,policy);
    }
    private static CompoundTag writeEndpoint(Endpoint endpoint){
        var tag=new CompoundTag();tag.putString("Dimension",endpoint.position.dimension().identifier().toString());tag.putLong("Position",endpoint.position.pos().asLong());tag.putString("Provider",endpoint.provider);
        if(endpoint.side!=null)tag.putString("Side",endpoint.side.getName());
        if(endpoint.policyAnchor!=null)tag.put("Policy",endpoint.policyAnchor.save());
        return tag;
    }
    public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var entries=new ListTag();
        for(var bucket:due.entrySet())for(var flight:bucket.getValue()){
            var value=new CompoundTag();value.putLong("Remaining",relative?bucket.getKey():Math.max(0,bucket.getKey()-clock));value.store("Rune",net.minecraft.core.UUIDUtil.CODEC,flight.rune);value.putLong("Amount",flight.amount);
            value.put("From",writeEndpoint(flight.from));value.put("To",writeEndpoint(flight.to));
            if(flight.resource instanceof ItemKey item)value.put("Item",com.cappleapple.astralrepository.port.NbtCodecs.save(item.sample(),registries));
            else if(flight.resource instanceof FluidKey fluid)value.put("Fluid",com.cappleapple.astralrepository.port.NbtCodecs.save(fluid.sample(),registries));
            else{value.putString("Type",flight.resource.type().toString());value.putString("Resource",flight.resource.id().toString());}
            entries.add(value);
        }
        tag.put("Flights",entries);return tag;
    }
}
