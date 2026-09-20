package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import java.util.*;
import net.minecraft.core.GlobalPos;

/** Owns inputs during travel, then transfers ownership once to the real processor operation. */
final class CraftDelivery implements CraftScheduler.Operation<ItemKey> {
    private record Shipment(ItemKey key,long count,GlobalPos from,int due) {}
    private final CraftingService.NetworkAccess access;
    private final ProcessingAdapter adapter;
    private final GlobalPos position;
    private final CraftPlan.Node<ItemKey> node;
    private final CraftOrigins<ItemKey,GlobalPos> origins;
    private final List<Shipment> shipments=new ArrayList<>();
    private final Map<ItemKey,Long> arrived=new LinkedHashMap<>();
    private CraftScheduler.Operation<ItemKey> operation;
    private int elapsed,latest;
    private boolean settled;
    CraftDelivery(CraftingService.NetworkAccess access,ProcessingAdapter adapter,GlobalPos position,CraftPlan.Node<ItemKey> node,CraftOrigins<ItemKey,GlobalPos> origins){
        this.access=access;this.adapter=adapter;this.position=position;this.node=node;this.origins=origins;
        // Read routes before taking origin claims, so validation failures cannot lose provenance.
        Map<GlobalPos,Integer> delays=new HashMap<>();for(var key:node.inputs().keySet())for(var from:origins.positions(key))delays.put(from,access.travelTicks(from,position));
        node.inputs().forEach((key,count)->origins.take(key,count,access.origin()).forEach((from,n)->{
            int delay=delays.getOrDefault(from,0);shipments.add(new Shipment(key,n,from,delay));latest=Math.max(latest,delay);
            CraftingService.animate(access,from,position,key.sample(),Math.max(1,delay));
        }));
    }
    @Override public Map<ItemKey,Long> poll(){
        if(operation!=null)return operation.poll();
        var level=access.level().getServer().getLevel(position.dimension());if(level==null||!level.hasChunkAt(position.pos()))return null;
        if(!adapter.supports(access,position,node))throw new IllegalStateException("Workstation removed before ingredients arrived");
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
    @Override public Map<ItemKey,Long> cancel(){
        if(settled)return Map.of();settled=true;clearPreview();
        if(operation!=null){var returned=operation.cancel();returned.forEach((key,n)->origins.add(key,n,position));return returned;}
        for(var shipment:shipments)origins.add(shipment.key(),shipment.count(),shipment.due()<=elapsed?position:shipment.from());
        return node.inputs();
    }
    @Override public Map<ItemKey,Long> recoverable(){return operation==null?node.inputs():operation.recoverable();}
}
