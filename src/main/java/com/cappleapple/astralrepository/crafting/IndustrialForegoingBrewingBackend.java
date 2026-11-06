package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Whole native brewing prefixes; intermediate bottles remain inside the real multi-stage processor. */
public final class IndustrialForegoingBrewingBackend implements NativeMachineBackend {
    private static final String PROCESSING="com.buuz135.industrial.block.tile.IndustrialProcessingTile";
    private static final String ACTIVE="com.hrznstudio.titanium.block.tile.ActiveTile";
    private static final String BUNDLE="com.hrznstudio.titanium.component.bundle.LockableInventoryBundle";
    private static final String PROGRESS="com.hrznstudio.titanium.component.progress.ProgressBarComponent";
    private record Surface(boolean brewing,List<NativeBytecode.Shape> shapes,int retainedWater) {}
    private record Binding(String host,Object configuration,List<ItemKey> reagents,ItemKey output,int batch) {}
    public record Diagnostic(String subject,String status,String reason) {}
    private final List<Diagnostic> diagnostics=new ArrayList<>();
    private static final ClassValue<Surface> SURFACES=new ClassValue<>() {
        @Override protected Surface computeValue(Class<?> type) {
            if(!contract(type,PROCESSING)) return new Surface(false,List.of(),0);
            List<NativeBytecode.Shape> shapes=new ArrayList<>();
            for(Class<?> current=type;current!=null && !current.getName().equals(PROCESSING);current=current.getSuperclass()) shapes.add(NativeBytecode.inspect(current));
            boolean nativeBrewing=shapes.stream().flatMap(shape->shape.references().stream()).anyMatch(reference->
                    reference.owner().equals(net.minecraft.world.item.alchemy.PotionBrewing.class.getName())
                    || reference.owner().equals("net.minecraftforge.common.brewing.BrewingRecipeRegistry"));
            int retained=0;
            for(var shape:shapes) for(var method:shape.methods()) if(method.name().equals("canIncrease")) {
                var code=method.instructions();
                for(int index=0;index+2<code.size();index++) {
                    var reference=code.get(index).reference();
                    if(reference!=null && reference.name().equals("getFluidAmount") && code.get(index+1).constant() instanceof Integer quantity
                            && quantity>0 && code.get(index+2).opcode()==160) retained=quantity;
                }
            }
            return new Surface(nativeBrewing,List.copyOf(shapes),retained);
        }
    };
    @Override public String id() { return "industrialforegoing_brewing"; }
    @Override public boolean usesRequestedOutputs() { return true; }
    static boolean matches(Class<?> type) { return SURFACES.get(type).brewing(); }
    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }
    @Override public Machine machine(CraftingService.NetworkAccess access,GlobalPos position) {
        return at(access.level().getServer().getLevel(position.dimension()),position.pos());
    }
    @Override public Object identityAt(ServerLevel level,BlockPos position) { var host=at(level,position); return host==null?null:host.identity(); }
    private Host at(ServerLevel level,BlockPos position) {
        if(level==null || !level.hasChunkAt(position)) return null;
        BlockEntity tile=level.getBlockEntity(position); if(tile==null || !SURFACES.get(tile.getClass()).brewing()) return null;
        try { return new Host(level,tile); }
        catch(RuntimeException unavailable) { return null; }
    }
    @Override public List<Recipe> recipes(CraftingService.NetworkAccess access,List<Machine> machines) { return recipes(access,machines,List.of()); }
    @Override public List<Recipe> recipes(CraftingService.NetworkAccess access,List<Machine> machines,List<ResourceKey> requested) {
        diagnostics.clear(); List<Recipe> result=new ArrayList<>();
        if(machines.isEmpty()) return List.of();
        List<ItemStack> stock=access.resourceSnapshot().keySet().stream().filter(ItemKey.class::isInstance).map(ItemKey.class::cast).map(ItemKey::sample).toList();
        var nativeRecipes=BrewingRecipes.entries(access.level(),stock);
        Map<ItemKey,List<BrewingRecipes.Entry>> next=new HashMap<>();
        for(var entry:nativeRecipes) next.computeIfAbsent(new ItemKey(entry.input()),ignored->new ArrayList<>()).add(entry);
        Set<ItemKey> desired=new LinkedHashSet<>();
        for(ResourceKey key:requested) if(key instanceof ItemKey item) desired.add(item);
        for(ItemStack sample:access.exposedProducts()) if(!sample.isEmpty()) desired.add(new ItemKey(sample));
        Set<ItemKey> reachableOutputs=new HashSet<>();
        for(var entry:nativeRecipes) reachableOutputs.add(new ItemKey(entry.output()));
        reachableOutputs.add(new ItemKey(PotionContents.createItemStack(Items.POTION,Potions.WATER)));
        desired.retainAll(reachableOutputs);
        Set<String> seen=new HashSet<>();
        for(Machine machine:machines) if(machine instanceof Host host) {
            if(host.surface.retainedWater()<1) { diagnostics.add(new Diagnostic(host.tile.getClass().getName(),"UNSUPPORTED_NATIVE_GUARD","Native retained-water equality contract is unavailable")); continue; }
            Deque<Path> pending=new ArrayDeque<>(); pending.add(new Path(new ItemKey(PotionContents.createItemStack(Items.POTION,Potions.WATER)),List.of(),Set.of()));
            int explored=0;
            while(!pending.isEmpty()) {
                if(++explored>8192) { diagnostics.add(new Diagnostic(host.tile.getClass().getName(),"UNSUPPORTED_PATH_BOUND","Native configured brewing paths exceed8192")); break; }
                Path path=pending.removeFirst();
                if(desired.isEmpty() || desired.contains(path.potion())) for(int batch=1;batch<=Math.min(3,host.output.getSlots());batch++) {
                    List<CraftRecipe.Ingredient<ResourceKey>> inputs=new ArrayList<>(); inputs.add(new CraftRecipe.Ingredient<>(List.of(new ItemKey(new ItemStack(Items.GLASS_BOTTLE))),batch));
                    for(ItemKey reagent:path.reagents()) inputs.add(new CraftRecipe.Ingredient<>(List.of(reagent),1));
                    Object configuration=host.catalogState(); Map<ResourceKey,Long> outputs=Map.of(path.potion(),(long)batch);
                    String fingerprint=NativeMachineAdapter.profile(host.level,Map.of("class",host.tile.getClass().getName(),"configuration",configuration,"reagents",path.reagents(),"output",path.potion(),"batch",batch));
                    String id="industrialforegoing:brewing/"+fingerprint;
                    if(seen.add(id)) {
                        int duration=Math.multiplyExact(Math.max(1,((Number)call(host.tile,"getMaxProgress")).intValue()),path.reagents().size()+1);
                        result.add(new Recipe(id,"native_brewing/"+fingerprint,inputs,outputs,outputs,duration,Math.max(7200,duration*8),new Binding(host.tile.getClass().getName(),configuration,path.reagents(),path.potion(),batch),false));
                    }
                }
                if(path.reagents().size()>=host.reagents.getSlots()) continue;
                for(var entry:next.getOrDefault(path.potion(),List.of())) {
                    ItemKey output=new ItemKey(entry.output()); if(path.visited().contains(output) || output.equals(path.potion())) continue;
                    int slot=path.reagents().size();
                    if(!host.reagents.isItemValid(slot,entry.reagent())) continue;
                    List<ItemKey> reagents=new ArrayList<>(path.reagents()); reagents.add(new ItemKey(entry.reagent()));
                    Set<ItemKey> visited=new HashSet<>(path.visited()); visited.add(path.potion());
                    pending.addLast(new Path(output,List.copyOf(reagents),Set.copyOf(visited)));
                }
            }
            diagnostics.add(new Diagnostic(host.tile.getClass().getName(),"NATIVE_BREWING_PREFIX",explored+" pure native paths; real water setup, fuel and buffered intermediates remain native"));
        }
        return List.copyOf(result);
    }
    private record Path(ItemKey potion,List<ItemKey> reagents,Set<ItemKey> visited) {}
    private final class Host implements Machine {
        final ServerLevel level; final BlockEntity tile; final Surface surface;
        final Object bundle,water,blaze; final IItemHandler reagents,bottles,fuel,output;
        final Object configuration;
        Host(ServerLevel level,BlockEntity tile) {
            this.level=level; this.tile=tile; surface=SURFACES.get(tile.getClass());
            Object foundBundle=null,foundWater=null,foundBlaze=null;
            for(var shape:surface.shapes()) for(var field:shape.fields()) if((field.access()&8)==0) {
                if(field.descriptor().equals("L"+BUNDLE.replace('.','/')+";")) foundBundle=read(tile,shape,field);
                if(field.descriptor().endsWith("/SidedFluidTankComponent;")) { if(foundWater!=null) throw new IllegalStateException("Ambiguous native brewing setup tanks"); foundWater=read(tile,shape,field); }
                if(field.descriptor().equals("L"+PROGRESS.replace('.','/')+";")) { if(foundBlaze!=null) throw new IllegalStateException("Ambiguous native brewing fuel pool"); foundBlaze=read(tile,shape,field); }
            }
            if(foundBundle==null || foundWater==null || foundBlaze==null) throw new IllegalStateException("Native multi-stage brewing roles unavailable");
            bundle=foundBundle; water=foundWater; blaze=foundBlaze; reagents=(IItemHandler)call(bundle,"getInventory");
            IItemHandler bottleRole=null,fuelRole=null,outputRole=null;
            Object augment=call(tile,"getAugmentInventory");
            Object inventories=call(tile,"getMultiInventoryComponent");
            for(Object component:list(call(inventories,"getInventoryHandlers"))) if(component instanceof IItemHandler handler && component!=reagents && component!=augment) {
                if(handler.getSlots()==1 && handler.isItemValid(0,new ItemStack(Items.GLASS_BOTTLE))) { if(bottleRole!=null) throw new IllegalStateException("Ambiguous native bottle input"); bottleRole=handler; }
                else if(handler.getSlots()==1 && handler.isItemValid(0,new ItemStack(Items.BLAZE_POWDER))) { if(fuelRole!=null) throw new IllegalStateException("Ambiguous native fuel input"); fuelRole=handler; }
                else if(handler.getSlots()>=3 && !handler.isItemValid(0,new ItemStack(Items.GLASS_BOTTLE))) {
                    if(outputRole!=null) throw new IllegalStateException("Ambiguous native brewing output"); outputRole=handler;
                }
            }
            if(bottleRole==null || fuelRole==null || outputRole==null || reagents.getSlots()<1 || reagents.getSlots()>16) throw new IllegalStateException("Native brewing buffer shape unavailable");
            bottles=bottleRole; fuel=fuelRole; output=outputRole; configuration=configuration();
        }
        @Override public Object identity() { return tile; }
        @Override public Object instance() { return tile; }
        @Override public boolean valid() { return level.hasChunkAt(tile.getBlockPos()) && !tile.isRemoved() && level.getBlockEntity(tile.getBlockPos())==tile
                && configuration.equals(configuration()) && ((Number)call(water,"getFluidAmount")).intValue()==surface.retainedWater(); }
        private Object configuration() {
            List<Object> state=new ArrayList<>(); state.add(tile.getClass().getName()); state.add(surface.retainedWater()); state.add(reagents.getSlots());
            state.add(Boolean.TRUE.equals(call(bundle,"isLocked")));
            for(Object sample:list(call(bundle,"getFilter"))) if(sample instanceof ItemStack stack) state.add(stack.isEmpty()?"empty":new ItemKey(stack));
            return List.copyOf(state);
        }
        @Override public Object catalogState() { return configuration(); }
        @Override public boolean accepts(Recipe recipe) { return recipe.nativeRecipe() instanceof Binding binding && binding.host().equals(tile.getClass().getName()) && binding.configuration().equals(configuration()); }
        @Override public boolean ready(Recipe recipe,List<CraftPlan.Selection<ResourceKey>> selected) {
            Binding binding=(Binding)recipe.nativeRecipe();
            for(int slot=0;slot<output.getSlots();slot++) if(!output.getStackInSlot(slot).isEmpty()) return false;
            for(int slot=0;slot<reagents.getSlots();slot++) if(!reagents.getStackInSlot(slot).isEmpty()) return false;
            for(int slot=0;slot<binding.reagents().size();slot++) if(!reagents.isItemValid(slot,binding.reagents().get(slot).sample())) return false;
            return bottles.getStackInSlot(0).isEmpty();
        }
        @Override public List<Port> ports(Recipe recipe) {
            Binding binding=(Binding)recipe.nativeRecipe(); List<Port> result=new ArrayList<>(); result.add(port(bottles,0,true,false));
            for(int slot=0;slot<binding.reagents().size();slot++) result.add(port(reagents,slot,true,false));
            for(int slot=0;slot<output.getSlots();slot++) result.add(port(output,slot,false,true));
            return List.copyOf(result);
        }
        @Override public List<Supplemental> supplemental(CraftingService.NetworkAccess access,Recipe recipe,List<CraftPlan.Selection<ResourceKey>> selected) {
            Binding binding=(Binding)recipe.nativeRecipe(); if(binding.reagents().isEmpty() || ((Number)call(blaze,"getProgress")).intValue()>0 || !fuel.getStackInSlot(0).isEmpty()) return List.of();
            ItemKey powder=new ItemKey(new ItemStack(Items.BLAZE_POWDER));
            return access.mayUseFuel(powder)?List.of(new Supplemental(port(fuel,0,true,false),powder,1)):List.of();
        }
        @Override public List<GlobalPos> claimedPositions() { return List.of(GlobalPos.of(level.dimension(),tile.getBlockPos())); }
    }
    private static Port port(IItemHandler handler,int slot,boolean input,boolean output) {
        Port delegate=NativeMachinePorts.itemSlot(handler,slot,input,output);
        return NativeMachinePorts.callbacks(delegate.identity(),input,output,delegate::snapshot,
                (key,amount,simulate)->key instanceof ItemKey item && handler.isItemValid(slot,item.sample())?delegate.insert(key,amount,simulate):0,delegate::extract);
    }
    private static Object read(Object instance,NativeBytecode.Shape owner,NativeBytecode.Field field) { return new NativeBytecode.Reference(180,owner.name(),field.name(),field.descriptor()).readInstance(instance); }
}
