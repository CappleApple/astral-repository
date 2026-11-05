package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.CraftingProvider;
import com.cappleapple.astralrepository.api.ItemKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Preserves the requested net gain by preventing processors from burning existing target stock. */
final class CraftingJobAccess implements CraftingService.NetworkAccess {
    private final CraftingService.NetworkAccess delegate;
    private final ItemKey target;
    CraftingJobAccess(CraftingService.NetworkAccess delegate,ItemKey target){this.delegate=delegate;this.target=target;}
    @Override public boolean mayUseFuel(ItemKey key){return !target.equals(key)&&delegate.mayUseFuel(key);}
    @Override public ServerLevel level(){return delegate.level();}
    @Override public Map<ItemKey,Long> snapshot(){return delegate.snapshot();}
    @Override public Map<com.cappleapple.astralrepository.api.ResourceKey,Long> resourceSnapshot(){return delegate.resourceSnapshot();}
    @Override public long reserveResource(com.cappleapple.astralrepository.api.ResourceKey key,long amount,BiConsumer<GlobalPos,Long> receipt){return delegate.reserveResource(key,amount,receipt);}
    @Override public long insertResourceFrom(com.cappleapple.astralrepository.api.ResourceKey key,long amount,GlobalPos source){return delegate.insertResourceFrom(key,amount,source);}
    @Override public void visualResource(GlobalPos from,GlobalPos to,com.cappleapple.astralrepository.api.ResourceKey key,long amount,int duration){delegate.visualResource(from,to,key,amount,duration);}
    @Override public ItemStack extract(ItemKey key,int count){return delegate.extract(key,count);}
    @Override public ItemStack reserve(ItemKey key,int count,BiConsumer<GlobalPos,ItemStack> receipt){return delegate.reserve(key,count,receipt);}
    @Override public int travelTicks(GlobalPos from,GlobalPos to){return delegate.travelTicks(from,to);}
    @Override public ItemStack extractForDelivery(ItemKey key,int count,GlobalPos destination,BiConsumer<GlobalPos,ItemStack> receipt){return delegate.extractForDelivery(key,count,destination,receipt);}
    @Override public void stagingVisual(GlobalPos table,List<ItemStack> grid,int durationTicks){delegate.stagingVisual(table,grid,durationTicks);}
    @Override public ItemStack extractTo(ItemKey key,int count,GlobalPos destination){return delegate.extractTo(key,count,destination);}
    @Override public ItemStack insertFrom(ItemStack stack,GlobalPos source){return delegate.insertFrom(stack,source);}
    @Override public boolean canRoute(GlobalPos from,GlobalPos to){return delegate.canRoute(from,to);}
    @Override public ItemStack insert(ItemStack stack){return delegate.insert(stack);}
    @Override public List<GlobalPos> workstations(){return delegate.workstations();}
    @Override public List<ItemStack> exposedProducts(){return delegate.exposedProducts();}
    @Override public Set<String> excludedRecipes(ItemStack output){return delegate.excludedRecipes(output);}
    @Override public GlobalPos origin(){return delegate.origin();}
    @Override public void visual(GlobalPos from,GlobalPos to,ItemStack stack,int durationTicks){delegate.visual(from,to,stack,durationTicks);}
    @Override public void craftingVisual(GlobalPos table,List<ItemStack> grid,ItemStack output,int durationTicks){delegate.craftingVisual(table,grid,output,durationTicks);}
    @Override public void processorChanged(GlobalPos machine){delegate.processorChanged(machine);}
    @Override public boolean payCrafting(long complexity){return delegate.payCrafting(complexity);}
    @Override public List<CraftingProvider> craftingProviders(){return delegate.craftingProviders();}
}
