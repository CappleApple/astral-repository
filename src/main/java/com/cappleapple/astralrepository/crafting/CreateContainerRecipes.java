package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Detached native container previews. Physical filling and emptying stay in Create's real actors. */
public final class CreateContainerRecipes {
    private static final String FILLING = "com.simibubi.create.content.fluids.transfer.GenericItemFilling";
    private static final String POTION = "com.simibubi.create.content.fluids.potion.PotionFluidHandler";
    private static final String TYPES = "com.simibubi.create.AllRecipeTypes";
    private static final int MAX_PAIRS = 131072;
    public enum Kind { FILL, EMPTY }
    public record Diagnostic(String subject,String status,String reason) {}
    public record Catalog(List<Cycle> cycles,List<Diagnostic> diagnostics,int fillingPairs,int emptyingItems,boolean complete) {
        public Catalog { cycles=List.copyOf(cycles); diagnostics=List.copyOf(diagnostics); }
    }
    public record Cycle(String id,Object nativeType,Kind kind,ItemKey itemInput,FluidKey fluidInput,long fluidCount,
                        Map<ResourceKey,Long> outputs,int durationTicks) {
        public Cycle { outputs=Map.copyOf(outputs); }
        public NativeMachineBackend.Recipe recipe() {
            List<CraftRecipe.Ingredient<ResourceKey>> inputs=new ArrayList<>(); inputs.add(new CraftRecipe.Ingredient<>(List.of(itemInput),1));
            if (fluidInput!=null) inputs.add(new CraftRecipe.Ingredient<>(List.of(fluidInput),fluidCount));
            return new NativeMachineBackend.Recipe(id,nativeType + "/container/" + kind.name().toLowerCase(Locale.ROOT),inputs,outputs,outputs,durationTicks,7200,this,false);
        }
    }
    private record Pair(ItemKey item,FluidKey fluid,long capacity) {}
    private record Preview(FluidKey fluid,long amount,Map<ResourceKey,Long> outputs) {}
    private record Base(Object generation,List<ItemKey> fillable,List<ItemKey> emptyable) {}
    private static final Map<ServerLevel,Base> BASES=new WeakHashMap<>();
    private final Map<Pair,Optional<Preview>> filled=lru(16384);
    private final Map<ItemKey,Optional<Preview>> emptied=lru(4096);
    private Object recipeGeneration;
    private static <K,V> Map<K,V> lru(int maximum) { return new LinkedHashMap<>(64,0.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest) { return size()>maximum; }
    }; }

    /** Includes exact stock/data outputs, then caches accepted and rejected detached identity pairs. */
    public Catalog discover(ServerLevel level,Map<ResourceKey,Long> stock,Collection<? extends ResourceKey> declaredOutputs) {
        return discover(level,stock,declaredOutputs,List.of((long)Integer.MAX_VALUE));
    }
    /** Live actor capacities allow partial native fills and their exact component-bearing intermediates. */
    public Catalog discover(ServerLevel level,Map<ResourceKey,Long> stock,Collection<? extends ResourceKey> declaredOutputs,Collection<Long> inputCapacities) {
        if(optionalClass(FILLING)==null || optionalClass(POTION)==null || optionalClass(TYPES)==null) return new Catalog(List.of(),List.of(),0,0,true);
        List<Diagnostic> diagnostics=new ArrayList<>(); List<Cycle> cycles=new ArrayList<>();
        Set<ItemKey> fillable=new LinkedHashSet<>(),emptyable=new LinkedHashSet<>(); Set<FluidKey> fluids=new LinkedHashSet<>();
        var entries=NativeRecipeSource.entries(level);
        Object generation=List.of(level.getRecipeManager(),entries.stream().map(entry->List.of(entry.id(),entry.value())).toList());
        if(!generation.equals(recipeGeneration)) { filled.clear(); emptied.clear(); recipeGeneration=generation; }
        Base base=BASES.get(level);
        if(base==null || !base.generation().equals(generation)) { base=base(level,generation,diagnostics); BASES.put(level,base); }
        fillable.addAll(base.fillable()); emptyable.addAll(base.emptyable());
        Set<ResourceKey> current=new LinkedHashSet<>(stock.keySet()); current.addAll(declaredOutputs);
        for(ResourceKey key:current) {
            if(key instanceof ItemKey item) classify(level,item,fillable,emptyable,diagnostics);
            if(key instanceof FluidKey fluid) fluids.add(fluid);
        }
        // Registered defaults include buckets/water and native mod fluids without inventing component variants.
        for(var fluid:BuiltInRegistries.FLUID) if(!fluid.defaultFluidState().isEmpty() && fluid.defaultFluidState().isSource())
            try { fluids.add(new FluidKey(new net.neoforged.neoforge.fluids.FluidStack(fluid,1))); }
            catch(RuntimeException unavailable) { diagnostics.add(new Diagnostic(BuiltInRegistries.FLUID.getKey(fluid).toString(),"UNSUPPORTED_FLUID",reason(unavailable))); }
        Object fillingType=type("FILLING"),emptyingType=type("EMPTYING");
        var nativeEmptying=entries.stream().filter(entry->entry.type()==emptyingType).toList();
        List<Long> capacities=inputCapacities.stream().filter(amount->amount!=null && amount>0 && amount<=Integer.MAX_VALUE).distinct().sorted().toList();
        Set<ItemKey> processedEmpty=new HashSet<>(); Set<Pair> processedPairs=new HashSet<>(); Map<String,Cycle> discovered=new LinkedHashMap<>();
        int emptyCount=0,pairs=0; boolean changed;
        do {
        changed=false;
        for(ItemKey input:List.copyOf(emptyable)) {
            if(!processedEmpty.add(input)) continue;
            emptyCount++; changed=true;
            try {
                // Native potion handling precedes registered recipes. Other registered emptying recipes own their RNG.
                boolean potion=isPotion(input.sample());
                if(!potion && nativeEmptying.stream().anyMatch(entry->NativeRecipeSource.matches(entry.value(),NativeRecipeSource.singleInput(input.sample()),level))) continue;
                Optional<Preview> preview=emptied.computeIfAbsent(input,key->Optional.ofNullable(empty(key,potion)));
                if(preview.isPresent()) {
                    Preview value=preview.get(); fluids.add(value.fluid());
                    Cycle cycle=cycle(level,emptyingType,Kind.EMPTY,input,null,0,value.outputs()); discovered.putIfAbsent(cycle.id(),cycle);
                    for(ResourceKey output:value.outputs().keySet()) if(output instanceof ItemKey item) classify(level,item,fillable,emptyable,diagnostics);
                    try { reverse(level,fillingType,input,value,capacities,discovered); }
                    catch(RuntimeException unsupported) { diagnostics.add(new Diagnostic(input.id().toString(),"UNSUPPORTED_REVERSE_FILLING",reason(unsupported))); }
                }
            } catch(RuntimeException unsupported) { diagnostics.add(new Diagnostic(input.id().toString(),"UNSUPPORTED_EMPTYING",reason(unsupported))); }
        }
        long product=(long)fillable.size()*fluids.size()*capacities.size();
        if(product>MAX_PAIRS) {
            diagnostics.add(new Diagnostic("create:container_filling","UNSUPPORTED_CANDIDATE_BOUND","Exact native item-fluid-capacity domain is " + product + ", exceeding finite bound" + MAX_PAIRS + "; validated native cycles remain available"));
            return new Catalog(List.copyOf(discovered.values()),diagnostics,pairs,emptyCount,false);
        }
        for(ItemKey input:List.copyOf(fillable)) for(FluidKey fluid:List.copyOf(fluids)) for(long capacity:capacities) {
            Pair pair=new Pair(input,fluid,capacity); if(!processedPairs.add(pair)) continue;
            if(++pairs>MAX_PAIRS) {
                diagnostics.add(new Diagnostic("create:container_filling","UNSUPPORTED_CANDIDATE_BOUND","Native component closure exceeded finite pair bound" + MAX_PAIRS + "; validated native cycles remain available"));
                return new Catalog(List.copyOf(discovered.values()),diagnostics,pairs,emptyCount,false);
            }
            changed=true;
            try {
                Optional<Preview> preview=filled.computeIfAbsent(pair,value->Optional.ofNullable(fill(level,value)));
                if(preview.isPresent()) {
                    Preview value=preview.get(); Cycle cycle=cycle(level,fillingType,Kind.FILL,input,fluid,value.amount(),value.outputs()); discovered.putIfAbsent(cycle.id(),cycle);
                    // Do not enumerate every hypothetical multi-tank mixture. Exact stock/taught/recipe
                    // targets enter above; native emptying derives their finite predecessor states.
                }
            } catch(RuntimeException unsupported) { diagnostics.add(new Diagnostic(input.id() + "/" + fluid.id(),"UNSUPPORTED_FILLING",reason(unsupported))); }
        }
        } while(changed);
        cycles.addAll(discovered.values()); return new Catalog(cycles,diagnostics,pairs,emptyCount,true);
    }
    private static Base base(ServerLevel level,Object generation,List<Diagnostic> diagnostics) {
        Set<ItemKey> fillable=new LinkedHashSet<>(),emptyable=new LinkedHashSet<>();
        for(Item item:BuiltInRegistries.ITEM) {
            ItemStack stack=item.getDefaultInstance(); if(!stack.isEmpty()) classify(level,new ItemKey(stack),fillable,emptyable,diagnostics);
        }
        // The brewing catalog already resolves registered potion holders and all native bottle variants.
        for(var entry:BrewingRecipes.entries(level,List.of())) {
            classify(level,new ItemKey(entry.input()),fillable,emptyable,diagnostics);
            classify(level,new ItemKey(entry.output()),fillable,emptyable,diagnostics);
        }
        return new Base(generation,List.copyOf(fillable),List.copyOf(emptyable));
    }
    private static void classify(ServerLevel level,ItemKey key,Set<ItemKey> fillable,Set<ItemKey> emptyable,List<Diagnostic> diagnostics) {
        try {
            ItemStack sample=key.sample();
            if(Boolean.TRUE.equals(call(optionalClass(FILLING),"canItemBeFilled",level,sample.copyWithCount(1)))) fillable.add(key);
            if(isPotion(sample)) emptyable.add(key);
            else {
                Object handler=handler(sample.copyWithCount(1));
                if(handler!=null) for(int index=0;index<number(call(handler,"getTanks"),0);index++) if(MekanismResourceSupport.amount(call(handler,"getFluidInTank",index))>0) { emptyable.add(key); break; }
            }
        } catch(RuntimeException unavailable) { diagnostics.add(new Diagnostic(key.id().toString(),"UNSUPPORTED_CONTAINER",reason(unavailable))); }
    }
    private static Preview fill(ServerLevel level,Pair pair) {
        ItemStack item=pair.item().sample().copyWithCount(1);
        Object available=MekanismResourceSupport.sample(pair.fluid(),pair.capacity());
        long required=number(call(optionalClass(FILLING),"getRequiredAmountForItem",level,item,available),-1);
        if(required<=0 || required>pair.capacity()) return null;
        if(item.getCount()!=1 || !pair.item().matches(item) || MekanismResourceSupport.amount(available)!=pair.capacity() || !MekanismResourceSupport.key(available).equals(pair.fluid()))
            throw new UnsupportedOperationException("Native filling simulation changed detached input before consumption");
        available=MekanismResourceSupport.sample(pair.fluid(),required);
        ItemStack output=(ItemStack)call(optionalClass(FILLING),"fillItem",level,(int)required,item,available);
        if(output.isEmpty()) return null;
        if(!item.isEmpty() || MekanismResourceSupport.amount(available)!=0) throw new UnsupportedOperationException("Native fill does not consume exactly one item and its declared fluid amount");
        return new Preview(pair.fluid(),required,Map.of(new ItemKey(output),(long)output.getCount()));
    }
    private static Preview empty(ItemKey key,boolean potion) {
        ItemStack item=key.sample().copyWithCount(1),container; Object fluid;
        if(potion) {
            Object pair=call(optionalClass(POTION),"emptyPotion",item,false);
            fluid=first(pair); container=(ItemStack)second(pair);
            if(!item.isEmpty()) throw new UnsupportedOperationException("Native potion emptying did not consume exactly one detached bottle");
        } else {
            Object handler=handler(item); if(handler==null) return null;
            Class<?> actions=optionalClass("net.neoforged.neoforge.fluids.capability.IFluidHandler$FluidAction");
            if(actions==null) actions=optionalClass("net.minecraftforge.fluids.capability.IFluidHandler$FluidAction");
            if(actions==null) throw new UnsupportedOperationException("Native item-fluid action contract unavailable");
            @SuppressWarnings({"rawtypes","unchecked"}) Object execute=Enum.valueOf((Class)actions,"EXECUTE");
            fluid=call(handler,"drain",1000,execute); container=((ItemStack)call(handler,"getContainer")).copy();
        }
        long amount=MekanismResourceSupport.amount(fluid); if(amount<=0) return null;
        FluidKey identity=(FluidKey)MekanismResourceSupport.key(fluid); Map<ResourceKey,Long> outputs=new LinkedHashMap<>(); outputs.put(identity,amount);
        if(!container.isEmpty()) outputs.merge(new ItemKey(container),(long)container.getCount(),Math::addExact);
        return new Preview(identity,amount,Map.copyOf(outputs));
    }
    private void reverse(ServerLevel level,Object nativeType,ItemKey target,Preview emptied,List<Long> capacities,Map<String,Cycle> cycles) {
        var containers=emptied.outputs().entrySet().stream().filter(entry->entry.getKey() instanceof ItemKey).toList();
        if(containers.size()!=1 || containers.getFirst().getValue()!=1) return;
        ItemKey predecessor=(ItemKey)containers.getFirst().getKey(); if(predecessor.equals(target)) return;
        for(long capacity:capacities) {
            ItemKey current=predecessor; long remaining=emptied.amount(); int steps=0;
            while(remaining>0) {
                if(++steps>4096) throw new UnsupportedOperationException("Requested native container predecessor path exceeds4096 physical cycles");
                Pair pair=new Pair(current,emptied.fluid(),Math.min(capacity,remaining));
                Optional<Preview> preview=filled.computeIfAbsent(pair,value->Optional.ofNullable(fill(level,value))); if(preview.isEmpty()) break;
                Preview value=preview.get(); if(value.amount()<=0 || value.amount()>remaining) break;
                Cycle cycle=cycle(level,nativeType,Kind.FILL,current,emptied.fluid(),value.amount(),value.outputs()); cycles.putIfAbsent(cycle.id(),cycle);
                var items=value.outputs().entrySet().stream().filter(entry->entry.getKey() instanceof ItemKey).toList();
                if(items.size()!=1 || items.getFirst().getValue()!=1) break;
                ItemKey next=(ItemKey)items.getFirst().getKey(); if(next.equals(current)) break;
                current=next; remaining-=value.amount();
            }
            if(remaining==0 && !current.equals(target)) throw new UnsupportedOperationException("Native container fill is not the inverse of its exact emptying metadata");
        }
    }
    private static Object handler(ItemStack item) {
        Class<?> capabilities=optionalClass("net.neoforged.neoforge.capabilities.Capabilities$FluidHandler");
        if(capabilities!=null) return optional(item,"getCapability",field(capabilities,"ITEM"));
        Class<?> forge=optionalClass("net.minecraftforge.common.capabilities.ForgeCapabilities");
        if(forge!=null) {
            Object optional=call(item,"getCapability",field(forge,"FLUID_HANDLER_ITEM"));
            return optional==null?null:call(optional,"orElse",(Object)null);
        }
        return null;
    }
    private static boolean isPotion(ItemStack stack) { return Boolean.TRUE.equals(call(optionalClass(POTION),"isPotionItem",stack)); }
    private static Object first(Object pair) { Object result=optional(pair,"getFirst"); return result!=null?result:call(pair,"getLeft"); }
    private static Object second(Object pair) { Object result=optional(pair,"getSecond"); return result!=null?result:call(pair,"getRight"); }
    private static Object type(String name) { return call(field(optionalClass(TYPES),name),"getType"); }
    private static Cycle cycle(ServerLevel level,Object type,Kind kind,ItemKey input,FluidKey fluid,long amount,Map<ResourceKey,Long> outputs) {
        Map<String,Object> profile=new LinkedHashMap<>(); profile.put("kind",kind.name()); profile.put("input",input); profile.put("outputs",outputs);
        if(fluid!=null) { profile.put("fluid",fluid); profile.put("amount",amount); }
        String fingerprint=NativeMachineAdapter.profile(level,profile);
        String id="create:container_" + kind.name().toLowerCase(Locale.ROOT) + "/" + fingerprint;
        String owner=kind==Kind.FILL?"com.simibubi.create.content.fluids.spout.SpoutBlockEntity":"com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity";
        int duration=(int)Math.max(1,number(field(optionalClass(owner),"FILLING_TIME"),20));
        return new Cycle(id,type,kind,input,fluid,amount,outputs,duration);
    }
    private static String reason(Throwable failure) { String message=failure.getMessage(); return message==null?failure.getClass().getName():message.length()>600?message.substring(0,600):message; }
}
