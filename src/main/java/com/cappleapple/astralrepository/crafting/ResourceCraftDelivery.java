package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ResourceKey;
import java.util.*;
import net.minecraft.core.GlobalPos;

/** Owns inputs during travel, then transfers ownership once to the real processor operation. */
final class ResourceCraftDelivery implements CraftScheduler.Operation<ResourceKey> {
    private record Shipment(ResourceKey key,long count,GlobalPos from,int due) {}
    private final CraftingService.NetworkAccess access;
    private final ResourceProcessingAdapter adapter;
    private final GlobalPos position;
    private final CraftPlan.Node<ResourceKey> node;
    private final CraftOrigins<ResourceKey,GlobalPos> origins;
    private final Set<GlobalPos> claimed;
    private final List<Shipment> shipments=new ArrayList<>();
    private final Map<ResourceKey,Long> arrived=new LinkedHashMap<>();
    private CraftScheduler.Operation<ResourceKey> operation;
    private int elapsed,latest;
    private boolean settled;
    ResourceCraftDelivery(CraftingService.NetworkAccess access,ResourceProcessingAdapter adapter,GlobalPos position,CraftPlan.Node<ResourceKey> node,CraftOrigins<ResourceKey,GlobalPos> origins,List<GlobalPos> claimed){
        this.access=access;this.adapter=adapter;this.position=position;this.node=node;this.origins=origins;
        this.claimed=Set.copyOf(claimed);
        // Read routes before taking origin claims, so validation failures cannot lose provenance.
        Map<GlobalPos,Integer> delays=new HashMap<>();for(var key:node.inputs().keySet())for(var from:origins.positions(key))delays.put(from,access.travelTicks(from,position));
        node.inputs().forEach((key,count)->origins.take(key,count,access.origin()).forEach((from,n)->{
            int delay=delays.getOrDefault(from,0);shipments.add(new Shipment(key,n,from,delay));latest=Math.max(latest,delay);
            try {access.visualResource(from,position,key,n,Math.max(1,delay));} catch(RuntimeException ignored) {}
        }));
    }
    @Override public Map<ResourceKey,Long> poll(){
        if(operation!=null)return operation.poll();
        var level=access.level().getServer().getLevel(position.dimension());if(level==null||!level.hasChunkAt(position.pos()))return null;
        if(!adapter.supports(access,position,node))throw new IllegalStateException("Workstation removed before ingredients arrived");
        var current=new HashSet<>(adapter.claimedPositions(access,position,node));current.add(position);
        if(!current.equals(claimed))throw new IllegalStateException("Native machine output destinations changed before delivery");
        elapsed++;boolean changed=false;
        for(var shipment:shipments)if(shipment.due()<=elapsed&&shipment.due()>elapsed-1){arrived.merge(shipment.key(),shipment.count(),Long::sum);changed=true;}
        if(elapsed==1)for(var shipment:shipments)if(shipment.due()==0){arrived.merge(shipment.key(),shipment.count(),Long::sum);changed=true;}
        if(elapsed<latest){if(changed)preview(Math.max(1,latest-elapsed));return null;}
        operation=adapter.start(access,position,node);
        if(operation==null){if(changed||elapsed%20==0)preview(25);return null;}
        clearPreview();return com.cappleapple.astralrepository.AstralConfig.instantAutomaticLogistics.get()?operation.poll():null;
    }
    private void preview(int duration){try{adapter.previewDelivery(access,position,node,Map.copyOf(arrived),duration);}catch(RuntimeException failure){CraftingService.LOGGER.debug("Assembly preview skipped",failure);}}
    private void clearPreview(){try{access.stagingVisual(position,Collections.nCopies(9,net.minecraft.world.item.ItemStack.EMPTY),1);}catch(RuntimeException failure){CraftingService.LOGGER.debug("Assembly preview cleanup skipped",failure);}}
    @Override public Map<ResourceKey,Long> cancel(){
        if(settled)return Map.of();settled=true;clearPreview();
        if(operation!=null){var returned=operation.cancel();returned.forEach((key,n)->origins.add(key,n,position));return returned;}
        for(var shipment:shipments)origins.add(shipment.key(),shipment.count(),shipment.due()<=elapsed?position:shipment.from());
        return node.inputs();
    }
    @Override public Map<ResourceKey,Long> recoverable(){return operation==null?node.inputs():operation.recoverable();}
    @Override public boolean mayReturnShortOutput(){return operation!=null&&operation.mayReturnShortOutput();}

}
