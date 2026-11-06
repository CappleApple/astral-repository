package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.fluids.FluidStack;
import com.cappleapple.astralrepository.port.storage.IFluidHandler;
import com.cappleapple.astralrepository.port.storage.IItemHandler;

/** Titanium component and native recipe discovery, independent of machine block IDs and slot numbers. */
public final class IndustrialForegoingBackend implements NativeMachineBackend {
    private static final String MACHINE = "com.buuz135.industrial.block.tile.IndustrialMachineTile";
    private static final String PROCESSING = "com.buuz135.industrial.block.tile.IndustrialProcessingTile";
    private static final String ACTIVE = "com.hrznstudio.titanium.block.tile.ActiveTile";
    private static final String INVENTORY = "com.hrznstudio.titanium.component.inventory.InventoryComponent";
    private static final String TANK = "com.hrznstudio.titanium.component.fluid.FluidTankComponent";
    public record Diagnostic(String subject, String nativeClass, String status, String reason) {}
    private record NativeRecipe(Object recipe, Object type) {}
    private record Role(String name, int slot, boolean fluid) {}
    private record FiniteRecipe(String type, Object state, Map<Role, TitaniumRecipeEvaluator.Quantity> consumed,
                                Map<Role, TitaniumRecipeEvaluator.Quantity> produced,
                                Map<Role, TitaniumRecipeEvaluator.Quantity> representative,
                                Map<Role, TitaniumRecipeEvaluator.Quantity> setup, Object storedRecipe, int storedSlot, ItemKey randomTarget, Set<Role> internalInputs,boolean pooledBatch,
                                Map<Role,TitaniumRecipeEvaluator.Quantity> retainedInputs) {}
    private record Surface(boolean machine, boolean processing, List<NativeBytecode.Shape> shapes) {}
    private static final ClassValue<Surface> SURFACES = new ClassValue<>() {
        @Override protected Surface computeValue(Class<?> type) {
            if (!contract(type, MACHINE)) return new Surface(false, false, List.of());
            List<NativeBytecode.Shape> shapes = new ArrayList<>();
            for (Class<?> current = type; current != null && !current.getName().equals(MACHINE); current = current.getSuperclass())
                shapes.add(NativeBytecode.inspect(current));
            return new Surface(true, contract(type, PROCESSING), List.copyOf(shapes));
        }
    };
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private Map<Object,Set<String>> sourceRecipeIds=Map.of();
    @Override public String id() { return "industrialforegoing"; }
    @Override public boolean usesRequestedOutputs() { return true; }
    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }

    /** Classifies every registered native block-entity surface, including unsupported world/entity machinery. */
    public List<Diagnostic> auditProcessors(ServerLevel level) {
        List<Diagnostic> result = new ArrayList<>();
        indexSources(NativeRecipeSource.entries(level));
        List<ResourceKey> candidates = candidates(Map.of());
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("industrialforegoing") || !(block instanceof EntityBlock factory)) continue;
            try {
                BlockEntity tile = factory.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                if (tile == null) { result.add(new Diagnostic(id.toString(), block.getClass().getName(), "NO_BLOCK_ENTITY", "Native factory returns no block entity")); continue; }
                Surface surface = SURFACES.get(tile.getClass());
                if (!surface.machine()) {
                    result.add(new Diagnostic(id.toString(), tile.getClass().getName(), "OTHER_NATIVE_SURFACE", "Does not implement the native IndustrialMachineTile processing lifecycle"));
                } else if (!surface.processing()) {
                    String reason = surface.shapes().stream().flatMap(shape -> shape.references().stream())
                            .filter(reference -> reference.owner().startsWith("net.minecraft.world.entity.") || reference.descriptor().contains("Lnet/minecraft/world/level/Level;"))
                            .map(reference -> reference.owner() + "." + reference.name()).distinct().limit(8).reduce((a,b) -> a + "; " + b).orElse("Native working lifecycle has no declared inventory recipe provider");
                    result.add(new Diagnostic(id.toString(), tile.getClass().getName(), "WORLD_OR_ENTITY_WORKER", reason));
                } else {
                    Host host = new Host(level, BlockPos.ZERO, tile);
                    if(IndustrialForegoingBrewingBackend.matches(tile.getClass())) {
                        result.add(new Diagnostic(id.toString(),tile.getClass().getName(),"NATIVE_BREWING_PREFIX","Whole native brewing prefixes retain intermediate bottles and water setup; real blaze fuel is charged natively"));
                        continue;
                    }
                    if(NativeEnchantmentPreview.usesNativeEnchanting(tile.getClass())) {
                        result.add(new Diagnostic(id.toString(),tile.getClass().getName(),"TARGETED_NATIVE_TRANSFORM","Native random enchanting validates exact requested/taught targets without rolling; the external essence feed must be empty"));
                        continue;
                    }
                    if (!host.recipeTypes().isEmpty()) result.add(new Diagnostic(id.toString(), tile.getClass().getName(), "RECIPE_PROCESSOR", host.recipeTypes().toString()));
                    else {
                        int before = diagnostics.size(); var profiles = finite(host, candidates);
                        var classified=diagnostics.subList(before,diagnostics.size());
                        String reasons = classified.stream().map(Diagnostic::reason).reduce((a,b) -> a + "; " + b).orElse("Native transformation returned no finite data");
                        String status=profiles.isEmpty()?(classified.stream().anyMatch(value->value.status().equals("POOLED_RESOURCE_SOURCE"))?"POOLED_RESOURCE_SOURCE":"UNSUPPORTED_TRANSFORM"):"FINITE_TRANSFORM";
                        result.add(new Diagnostic(id.toString(), tile.getClass().getName(), status, reasons));
                    }
                }
            } catch (RuntimeException | LinkageError unavailable) {
                result.add(new Diagnostic(id.toString(), block.getClass().getName(), "NATIVE_CONSTRUCTION_UNAVAILABLE", unavailable.getClass().getSimpleName() + ": " + unavailable.getMessage()));
            }
        }
        return List.copyOf(result);
    }

    @Override public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
        ServerLevel level = access.level().getServer().getLevel(position.dimension());
        return find(level, position.pos());
    }
    @Override public Object identityAt(ServerLevel level, BlockPos position) {
        var machine = find(level, position);
        return machine == null ? null : machine.identity();
    }
    private Host find(ServerLevel level, BlockPos position) {
        if (level == null || !level.hasChunkAt(position)) return null;
        BlockEntity tile = level.getBlockEntity(position);
        if (tile == null || !SURFACES.get(tile.getClass()).machine()) return null;
        // Area/world processors are classified by the audit; no inventory-only execution is inferred.
        if (!SURFACES.get(tile.getClass()).processing()) return null;
        return new Host(level, position.immutable(), tile);
    }

    @Override public List<NativeMachineBackend.Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> machines) {
        return recipes(access,machines,List.of());
    }
    @Override public List<NativeMachineBackend.Recipe> recipes(CraftingService.NetworkAccess access,List<Machine> machines,List<ResourceKey> requestedOutputs) {
        diagnostics.clear();
        List<NativeMachineBackend.Recipe> result = new ArrayList<>();
        Map<ResourceKey, Long> stock = access.resourceSnapshot();
        NativeStockSamples indexed = new NativeStockSamples(stock);
        Set<Object> types = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Machine machine : machines) if (machine instanceof Host host) types.addAll(host.recipeTypes());
        var nativeEntries=NativeRecipeSource.entries(access.level());
        if(!machines.isEmpty()) indexSources(nativeEntries); else sourceRecipeIds=Map.of();
        for (NativeRecipeSource.Entry holder : nativeEntries) {
            var recipe = holder.value();
            String type = NativeRecipeSource.typeKey(recipe).toString();
            if (!type.startsWith("industrialforegoing:") && !types.contains(holder.type())) continue;
            try {
                NativeMachineBackend.Recipe decoded = decode(holder, indexed, access.level());
                result.add(decoded);
                Set<Object> profiled = new HashSet<>();
                for (Machine machine : machines) if (machine instanceof Host host && host.recipeTypes().contains(holder.type()) && host.hasVanillaRecipeCache()
                        && profiled.add(host.catalogState())) result.addAll(stored(host, decoded));
                diagnostics.add(new Diagnostic(holder.id().toString(), recipe.getClass().getName(), "CATALOGUED", type));
            } catch (UnsupportedOperationException unsupported) {
                diagnostics.add(new Diagnostic(holder.id().toString(), recipe.getClass().getName(), "UNSUPPORTED", unsupported.getMessage()));
            } catch (RuntimeException unsupported) {
                diagnostics.add(new Diagnostic(holder.id().toString(), recipe.getClass().getName(), "CONTRACT_UNAVAILABLE", unsupported.getMessage()));
            }
        }
        for (Machine machine : machines) if (machine instanceof Host host)
            diagnostics.add(new Diagnostic(host.position.toString(), host.tile.getClass().getName(),
                    host.recipeTypes().isEmpty() ? "NATIVE_TRANSFORM" : "RECIPE_PROCESSOR",
                    host.recipeTypes().isEmpty() ? "No stored recipe provider; finite native transformation evaluation required" : host.recipeTypes().toString()));
        if (!machines.isEmpty()) {
            List<ResourceKey> candidates = new ArrayList<>(candidates(stock));
            Set<ResourceKey> desired=new LinkedHashSet<>(requestedOutputs);
            for(ItemStack output:access.exposedProducts()) if(!output.isEmpty()) desired.add(new ItemKey(output));
            Set<ItemKey> targets=new LinkedHashSet<>();
            for(ResourceKey output:requestedOutputs) if(output instanceof ItemKey item) targets.add(item);
            for(ItemStack output:access.exposedProducts()) if(!output.isEmpty()) targets.add(new ItemKey(output));
            for(ResourceKey output:stock.keySet()) if(output instanceof ItemKey item && NativeEnchantmentTargets.isTarget(item.sample())) targets.add(item);
            targets.removeIf(target->!NativeEnchantmentTargets.isTarget(target.sample()));
            for(ItemKey target:targets) { ItemStack input=NativeEnchantmentTargets.inputForTarget(target.sample()); if(!input.isEmpty() && !candidates.contains(new ItemKey(input))) candidates.add(new ItemKey(input)); }
            Set<String> profiles = new HashSet<>();
            for (Machine machine : machines) if (machine instanceof Host host) {
                if(IndustrialForegoingBrewingBackend.matches(host.tile.getClass())) continue;
                var contexts=TitaniumRecipeEvaluator.subprocessInputs(host.tile,host.level);
                if(!contexts.isEmpty()) {
                    var relevant=contextCandidates(host.level,indexed,desired,stock.keySet());
                    for(Object input:contexts) {
                        host.previewInput=input;
                        for(var recipe:finite(host,relevant)) if(profiles.add(recipe.id())) result.add(recipe);
                    }
                    host.previewInput=null;
                    // The same native callback may also have an independent
                    // fluid-funded generator path alongside its item substeps.
                    for(var recipe:finite(host,candidates.stream().filter(key->key instanceof FluidKey).toList()))
                        if(profiles.add(recipe.id())) result.add(recipe);
                    continue;
                }
                if(!host.recipeTypes().isEmpty()) continue;
                if(NativeEnchantmentPreview.usesNativeEnchanting(host.tile.getClass())) {
                    if(targets.isEmpty()) diagnostics.add(new Diagnostic(host.position.toString(),host.tile.getClass().getName(),"TARGETED_NATIVE_TRANSFORM","Native random output uses exact requested/taught metadata; no target is currently supplied"));
                    for(ItemKey target:targets) {
                        host.previewTarget=target;
                        var relevant=candidates.stream().filter(key->!(key instanceof ItemKey item) || NativeEnchantmentTargets.inputMayProduce(item.sample(),target.sample())).toList();
                        for(var recipe:finite(host,relevant)) if(profiles.add(recipe.id())) result.add(recipe);
                    }
                    host.previewTarget=null;
                } else {
                    List<ResourceKey> relevant=candidates;
                    if(NativeEnchantmentPreview.usesEnchantMutation(host.tile.getClass())) {
                        Set<ResourceKey> nativeInputs=new LinkedHashSet<>(stock.keySet()); nativeInputs.add(new ItemKey(new ItemStack(net.minecraft.world.item.Items.BOOK)));
                        for(ResourceKey output:desired) if(output instanceof ItemKey item) {
                            nativeInputs.add(new ItemKey(NativeEnchantmentTargets.inputForTarget(item.sample())));
                            if(NativeEnchantmentTargets.isTarget(item.sample())) nativeInputs.add(new ItemKey(NativeEnchantmentTargets.carrierForTarget(item.sample())));
                        }
                        candidates.stream().filter(key->!(key instanceof ItemKey)).forEach(nativeInputs::add);
                        relevant=List.copyOf(nativeInputs);
                    }
                    for (var recipe : finite(host, relevant)) if (profiles.add(recipe.id())) result.add(recipe);
                }
            }
        }
        return List.copyOf(result);
    }
    private void indexSources(List<NativeRecipeSource.Entry> entries) {
        Map<Object,Set<String>> index=new IdentityHashMap<>();
        for(var entry:entries) index.computeIfAbsent(entry.value(),ignored->new TreeSet<>()).add(entry.id().toString());
        index.replaceAll((recipe,ids)->Set.copyOf(ids));
        sourceRecipeIds=Collections.unmodifiableMap(index);
    }
    private Set<String> sources(TitaniumRecipeEvaluator.Result result) {
        Set<String> ids=new TreeSet<>();
        for(Object value:result.nativeSources()) ids.addAll(sourceRecipeIds.getOrDefault(value,Set.of()));
        return Set.copyOf(ids);
    }

    private List<ResourceKey> candidates(Map<ResourceKey, Long> stock) {
        LinkedHashSet<ResourceKey> result = new LinkedHashSet<>(stock.keySet());
        for (var item : BuiltInRegistries.ITEM) { ItemStack stack = new ItemStack(item); if (!stack.isEmpty()) result.add(new ItemKey(stack)); }
        for (var fluid : BuiltInRegistries.FLUID) if (!fluid.defaultFluidState().isEmpty() && fluid.defaultFluidState().isSource()) result.add(new FluidKey(new FluidStack(fluid, 1)));
        // Native ore fluids carry a material component, so registry-default FluidStacks
        // alone cannot represent the washer/fermenter/sieve chain.
        try {
            Class<?> module = Class.forName("com.buuz135.industrial.module.ModuleCore", false, getClass().getClassLoader());
            Class<?> helper = Class.forName("com.buuz135.industrial.fluid.OreTitaniumFluidType", false, module.getClassLoader());
            Class<?> family = Class.forName("com.buuz135.industrial.fluid.OreFluidInstance", false, module.getClassLoader());
            Class<?> itemHelper = Class.forName("com.buuz135.industrial.utils.ItemStackUtils", false, module.getClassLoader());
            var oreTag = itemHelper.getMethod("getOreTag", ItemStack.class);
            var construct = helper.getMethod("getFluidWithTag", family, int.class, net.minecraft.resources.Identifier.class);
            Set<net.minecraft.resources.Identifier> tags = new LinkedHashSet<>();
            for (ResourceKey key : List.copyOf(result)) if (key instanceof ItemKey item) {
                Object tag = oreTag.invoke(null, item.sample()); if (tag instanceof net.minecraft.resources.Identifier id) tags.add(id);
            }
            for (var field : NativeBytecode.inspect(module).fields()) if ((field.access() & 8) != 0 && field.descriptor().equals("Lcom/buuz135/industrial/fluid/OreFluidInstance;")) {
                Object nativeFamily = new NativeBytecode.Reference(178, module.getName(), field.name(), field.descriptor()).readStatic(module.getClassLoader());
                for (var tag : tags) {
                    FluidStack sample = (FluidStack) construct.invoke(null, nativeFamily, 1, tag);
                    if (!sample.isEmpty()) result.add(new FluidKey(sample));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            diagnostics.add(new Diagnostic("native-ore-fluid-data", getClass().getName(), "DATA_UNAVAILABLE", unavailable.getMessage()));
        }
        return List.copyOf(result);
    }
    private static List<ResourceKey> contextCandidates(ServerLevel level,NativeStockSamples stock,Set<ResourceKey> desired,Set<ResourceKey> physical) {
        Map<ItemKey,List<Object>> byOutput=new HashMap<>();
        for(var entry:NativeRecipeSource.entries(level)) try {
            ItemStack output=NativeRecipeSource.output(entry.value(),level.registryAccess());
            if(!output.isEmpty()) byOutput.computeIfAbsent(new ItemKey(output),ignored->new ArrayList<>()).add(entry.value());
        } catch(RuntimeException unsupported) { /* World/opaque recipes do not define a detached item boundary. */ }
        Set<ResourceKey> result=new LinkedHashSet<>(); Deque<ResourceKey> pending=new ArrayDeque<>(desired); Set<ResourceKey> visited=new HashSet<>();
        while(!pending.isEmpty() && visited.size()<4096) {
            ResourceKey key=pending.removeFirst(); if(!visited.add(key)) continue;
            for(Object recipe:byOutput.getOrDefault(key,List.of())) for(Ingredient ingredient:NativeRecipeSource.ingredients(recipe)) {
                if(ingredient.isEmpty()) continue;
                Set<ItemKey> alternatives=new LinkedHashSet<>(stock.matching(ingredient));
                for(ItemStack sample:stock.representations(ingredient)) if(!sample.isEmpty()) alternatives.add(new ItemKey(sample));
                for(ItemKey input:alternatives) if(result.add(input)) pending.addLast(input);
            }
        }
        if(result.isEmpty()) result.addAll(physical);
        return List.copyOf(result);
    }
    private List<NativeMachineBackend.Recipe> finite(Host host, List<ResourceKey> candidates) {
        List<NativeMachineBackend.Recipe> result = new ArrayList<>();
        Set<String> reasons = new LinkedHashSet<>();
        try {
            Set<Object> outputs = host.previewTarget==null && host.previewInput==null?TitaniumRecipeEvaluator.outputComponents(host.tile, host.level,
                    host.enchantFeed==null?null:new NativeEnchantmentPreview(host.level,host.enchantFeed,null)):Set.of();
            Set<Object> referenced = TitaniumRecipeEvaluator.referencedComponents(host.tile);
            List<TitaniumRecipeEvaluator.Address> addresses = new ArrayList<>(); List<List<ResourceKey>> choices = new ArrayList<>();
            long combinations = 1;
            for (var address : TitaniumRecipeEvaluator.inputs(host.tile)) {
                if(host.previewInput!=null && address.owner()!=host.previewInput) continue;
                if (!referenced.contains(address.owner())) continue;
                if (outputs.contains(address.owner())) continue;
                List<ResourceKey> accepted = candidates.stream().filter(key -> host.previewInput!=null?key instanceof ItemKey:TitaniumRecipeEvaluator.accepts(address, key)).toList();
                if (accepted.isEmpty()) continue;
                if (accepted.size() > 1024) throw new UnsupportedOperationException("Native candidate bound exceeded for component " + role(address));
                addresses.add(address); choices.add(accepted);
                combinations=host.previewInput==null?Math.multiplyExact(combinations,accepted.size()):Math.min(8193,combinations*accepted.size());
                if (addresses.size() > 9 || host.previewInput==null && combinations > 8192) throw new UnsupportedOperationException("Native finite candidate-product bound exceeded: " + combinations);
            }
            if (addresses.isEmpty()) throw new UnsupportedOperationException("No finite consumed item/fluid candidate contract; retained setup, energy, loot, or world inputs require separate native data");
            if(host.previewInput!=null) {
                for(int index=0;index<addresses.size();index++)
                    enumerate(host,List.of(addresses.get(index)),List.of(choices.get(index)),0,new LinkedHashMap<>(),result,reasons);
                if(addresses.size()>1) {
                    if(combinations<=8192) enumerate(host,addresses,choices,0,new LinkedHashMap<>(),result,reasons);
                    else reasons.add("Native joint-slot alternatives exceed8192 combinations; independently proven slot profiles remain available");
                }
            } else enumerate(host, addresses, choices, 0, new LinkedHashMap<>(), result, reasons);
        } catch (UnsupportedOperationException unsupported) { reasons.add(unsupported.getMessage()); }
        catch (RuntimeException unavailable) { reasons.add("Native finite data unavailable: " + unavailable.getClass().getSimpleName() + ": " + unavailable.getMessage()); }
        Map<String,NativeMachineBackend.Recipe> unique=new LinkedHashMap<>(); for(var recipe:result) unique.putIfAbsent(recipe.id(),recipe); result=new ArrayList<>(unique.values());
        if (result.isEmpty() && reasons.isEmpty()) reasons.add("Native guard rejected every finite resource candidate under the current setup and configuration");
        String status=result.isEmpty()?(reasons.stream().anyMatch(value->value.startsWith("POOLED_RESOURCE_SOURCE:"))?"POOLED_RESOURCE_SOURCE":"UNSUPPORTED_TRANSFORM"):"FINITE_TRANSFORM";
        diagnostics.add(new Diagnostic(host.position.toString(), host.tile.getClass().getName(),status,result.size() + " profiles; " + String.join("; ", reasons)));
        return List.copyOf(result);
    }
    private void enumerate(Host host, List<TitaniumRecipeEvaluator.Address> addresses, List<List<ResourceKey>> choices, int index,
                           Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> seed,
                           List<NativeMachineBackend.Recipe> result, Set<String> reasons) {
        if (index < addresses.size()) {
            var address = addresses.get(index);
            for (ResourceKey key : choices.get(index)) {
                int capacity = TitaniumRecipeEvaluator.capacity(address);
                int amount = address.fluid() ? capacity : Math.min(capacity, ((ItemKey)key).sample().getMaxStackSize());
                seed.put(address, new TitaniumRecipeEvaluator.Quantity(key, amount));
                enumerate(host, addresses, choices, index + 1, seed, result, reasons);
            }
            seed.remove(address); return;
        }
        try {
            var evaluated = evaluator(host,seed).evaluate();
            if (evaluated == null) return;
            var consumed = evaluated.consumed(); var produced = evaluated.produced();
            if(consumed.isEmpty() && !produced.isEmpty() && !TitaniumRecipeEvaluator.pooledState(host.tile).isEmpty()) throw new UnsupportedOperationException("POOLED_RESOURCE_SOURCE: native preloaded charges produce background output without a new consumed inventory input");
            if (consumed.isEmpty() || produced.isEmpty()) throw new UnsupportedOperationException("Native cycle has no finite consumed inventory input/output pair");
            Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> minimal = new LinkedHashMap<>(seed);
            for (var entry : consumed.entrySet()) {
                var original=minimal.get(entry.getKey()); var quantity=entry.getValue();
                long low=quantity.count(),high=original.count();
                minimal.put(entry.getKey(),quantity);
                if(!same(evaluator(host,minimal).evaluate(),consumed,produced)) {
                    while(low<high) {
                        long midpoint=(low+high)>>>1;
                        minimal.put(entry.getKey(),new TitaniumRecipeEvaluator.Quantity(quantity.key(),midpoint));
                        if(same(evaluator(host,minimal).evaluate(),consumed,produced)) high=midpoint; else low=midpoint+1;
                    }
                    minimal.put(entry.getKey(),new TitaniumRecipeEvaluator.Quantity(quantity.key(),low));
                }
            }
            var exact = evaluator(host,minimal).evaluate();
            if (exact == null || !exact.consumed().equals(consumed) || !exact.produced().equals(produced))
                throw new UnsupportedOperationException("Native consumed/setup quantities do not define an isolated finite cycle");
            for (var address : addresses) if (!consumed.containsKey(address)) {
                var original = minimal.remove(address); var without = evaluator(host,minimal).evaluate();
                if (same(without, consumed, produced)) continue;
                long low = 1, high = original.count();
                while (low < high) {
                    long midpoint = (low + high) >>> 1; minimal.put(address, new TitaniumRecipeEvaluator.Quantity(original.key(), midpoint));
                    var probe = evaluator(host,minimal).evaluate();
                    if (same(probe, consumed, produced)) high = midpoint; else low = midpoint + 1;
                }
                minimal.put(address, new TitaniumRecipeEvaluator.Quantity(original.key(), low));
            }
            TitaniumRecipeEvaluator.Batch batch=null;
            if(!TitaniumRecipeEvaluator.pooledState(host.tile).isEmpty() && host.previewTarget==null) {
                batch=evaluator(host,minimal).evaluateBatch();
                if(batch==null || batch.resources().consumed().isEmpty() || !batch.resources().choices().isEmpty()) throw new UnsupportedOperationException("Native pooled batch has no deterministic funded finite boundary");
                consumed=batch.resources().consumed(); produced=batch.resources().produced();
            }
            Map<Role, TitaniumRecipeEvaluator.Quantity> inputRoles = roles(consumed), representative = roles(produced), outputRoles = new LinkedHashMap<>(representative), setup = new LinkedHashMap<>(),retained=new LinkedHashMap<>();
            for(var entry:consumed.entrySet()) {
                var staged=minimal.get(entry.getKey());
                if(staged!=null && staged.key().equals(entry.getValue().key()) && staged.count()>entry.getValue().count())
                    retained.put(role(entry.getKey()),new TitaniumRecipeEvaluator.Quantity(staged.key(),staged.count()-entry.getValue().count()));
            }
            var funded=consumed;
            minimal.forEach((address, quantity) -> { if (!funded.containsKey(address)) setup.put(role(address), quantity); });
            List<CraftRecipe.Ingredient<ResourceKey>> inputs = inputRoles.values().stream().map(value -> new CraftRecipe.Ingredient<>(List.of(value.key()), value.count())).toList();
            Map<ResourceKey, Long> outputs = new LinkedHashMap<>(), guaranteed = null; Set<String> sourceIds=new TreeSet<>();
            for (var outcome : batch==null?outcomes(host,minimal):List.of(batch.resources())) {
                sourceIds.addAll(sources(outcome));
                if (!outcome.consumed().equals(consumed)) throw new UnsupportedOperationException("Symbolic native tag choices change consumed quantities; no isolated single-cycle contract");
                Map<ResourceKey, Long> current = new LinkedHashMap<>(); outcome.produced().values().forEach(value -> current.merge(value.key(), value.count(), Math::addExact));
                current.forEach((key, count) -> outputs.merge(key, count, Math::max));
                if (guaranteed == null) guaranteed = new LinkedHashMap<>(current);
                else { Map<ResourceKey, Long> present = current; guaranteed.replaceAll((key, count) -> Math.min(count, present.getOrDefault(key, 0L))); guaranteed.values().removeIf(count -> count <= 0); }
                roles(outcome.produced()).forEach(outputRoles::putIfAbsent);
            }
            if (guaranteed == null) throw new UnsupportedOperationException("Native symbolic alternatives have no completed outcomes");
            if(host.previewTarget!=null) guaranteed.clear();
            Object state = batch==null?host.catalogState():host.configuration(false);
            List<Object> setupIdentity = setup.entrySet().stream().map(entry -> (Object)Map.of("role", entry.getKey().toString(), "key", entry.getValue().key(), "count", entry.getValue().count())).toList();
            List<Object> retainedIdentity=retained.entrySet().stream().map(entry->(Object)Map.of("role",entry.getKey().toString(),"key",entry.getValue().key(),"count",entry.getValue().count())).toList();
            String fingerprint = fingerprint(host.level, inputs, outputs, List.of(state, setupIdentity,retainedIdentity, guaranteed,sourceIds));
            String type = host.tile.getClass().getName();
            String id = "industrialforegoing:intrinsic/" + type.replace('.', '/').toLowerCase(Locale.ROOT) + "/" + fingerprint;
            if(sourceIds.size()==1) id+="/"+sourceIds.iterator().next();
            int ticks = Math.max(1, ((Number) call(host.tile, PROCESSING, "getMaxProgress")).intValue());
            if(batch!=null) ticks=Math.multiplyExact(ticks,batch.cycles());
            result.add(new NativeMachineBackend.Recipe(id, "intrinsic/" + type + "/" + fingerprint, inputs, guaranteed, outputs, ticks, Math.max(7200, ticks * 20),
                    new FiniteRecipe(type, state, inputRoles, Collections.unmodifiableMap(outputRoles), representative, Map.copyOf(setup), null, 0,host.previewTarget,
                            host.previewInput==null?Set.of():Set.copyOf(inputRoles.keySet()),batch!=null,Map.copyOf(retained)), !guaranteed.equals(outputs),sourceIds.isEmpty()?Set.of(id):Set.copyOf(sourceIds)));
        } catch (UnsupportedOperationException unsupported) { if (reasons.size() < 32) reasons.add(unsupported.getMessage()); }
    }
    private static List<TitaniumRecipeEvaluator.Result> outcomes(Host host, Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> seed) {
        List<TitaniumRecipeEvaluator.Result> result = new ArrayList<>(); Set<Map<String, Integer>> visited = new HashSet<>();
        ArrayDeque<Map<String, Integer>> pending = new ArrayDeque<>(); pending.add(Map.of());
        while (!pending.isEmpty()) {
            Map<String, Integer> selected = pending.removeFirst(); if (!visited.add(selected)) continue;
            if (visited.size() > 256) throw new UnsupportedOperationException("Symbolic native tag-choice product exceeds256 alternatives");
            var value = new TitaniumRecipeEvaluator(host.tile, seed, selected).inLevel(host.level)
                    .enchanting(host.enchantFeed==null && host.previewTarget==null?null:new NativeEnchantmentPreview(host.level,host.enchantFeed,host.previewTarget)).fullyFundedPools().evaluate();
            if (value == null) throw new UnsupportedOperationException("A symbolic native tag choice violates its ready contract");
            result.add(value);
            value.choices().forEach((name, choices) -> {
                for (int index = 1; index < choices.size(); index++) {
                    Map<String, Integer> next = new LinkedHashMap<>(selected); next.put(name, index); if (!visited.contains(next)) pending.addLast(Map.copyOf(next));
                }
            });
        }
        return List.copyOf(result);
    }
    private static TitaniumRecipeEvaluator evaluator(Host host,Map<TitaniumRecipeEvaluator.Address,TitaniumRecipeEvaluator.Quantity> seed) {
        return new TitaniumRecipeEvaluator(host.tile,seed).inLevel(host.level)
                .enchanting(host.enchantFeed==null && host.previewTarget==null?null:new NativeEnchantmentPreview(host.level,host.enchantFeed,host.previewTarget)).fullyFundedPools();
    }
    /** Native typed recipe caches let the shadow engine discover machine-specific coproducts. */
    private List<NativeMachineBackend.Recipe> stored(Host host, NativeMachineBackend.Recipe decoded) {
        List<NativeMachineBackend.Recipe> result = new ArrayList<>();
        try { stored(host, decoded, 0, new LinkedHashMap<>(), result, new int[]{0}); }
        catch (UnsupportedOperationException failure) { diagnostics.add(new Diagnostic(decoded.id(), host.tile.getClass().getName(), "UNSUPPORTED_NATIVE_COMPLETION", failure.getMessage())); }
        return List.copyOf(result);
    }
    private void stored(Host host, NativeMachineBackend.Recipe decoded, int index, Map<TitaniumRecipeEvaluator.Address,TitaniumRecipeEvaluator.Quantity> seed,
                        List<NativeMachineBackend.Recipe> result, int[] budget) {
        if (++budget[0] > 8192) throw new UnsupportedOperationException("Native stored-recipe alternative evaluation exceeds8192");
        if (index < decoded.inputs().size()) {
            var ingredient = decoded.inputs().get(index);
            for (ResourceKey key : ingredient.alternatives()) {
                var address = TitaniumRecipeEvaluator.inputs(host.tile).stream().filter(candidate -> !seed.containsKey(candidate) && TitaniumRecipeEvaluator.accepts(candidate,key)).filter(candidate -> {
                    if (candidate.fluid()) return true;
                    @SuppressWarnings("unchecked") var predicate = (BiPredicate<ItemStack,Integer>)call(candidate.owner(), INVENTORY, "getInsertPredicate");
                    return key instanceof ItemKey item && predicate.test(item.sample(), candidate.slot());
                }).sorted(Comparator.comparingInt(TitaniumRecipeEvaluator.Address::slot)).findFirst();
                if (address.isEmpty()) continue;
                seed.put(address.get(),new TitaniumRecipeEvaluator.Quantity(key,ingredient.count()));
                stored(host,decoded,index+1,seed,result,budget); seed.remove(address.get());
            }
            return;
        }
        int slot = seed.keySet().stream().filter(address -> !address.fluid()).mapToInt(TitaniumRecipeEvaluator.Address::slot).min().orElse(0);
        Object nativeRecipe = ((NativeRecipe)decoded.nativeRecipe()).recipe();
        var evaluated = new TitaniumRecipeEvaluator(host.tile,seed).stored(nativeRecipe,host.level.registryAccess(),slot);
        if (!evaluated.consumed().equals(seed)) throw new UnsupportedOperationException("Native completion did not consume exactly the staged stored-recipe inputs");
        Map<ResourceKey,Long> outputs = new LinkedHashMap<>(); evaluated.produced().values().forEach(quantity -> outputs.merge(quantity.key(),quantity.count(),Math::addExact));
        if (outputs.isEmpty() || !evaluated.choices().isEmpty()) throw new UnsupportedOperationException("Native stored-recipe completion has no deterministic finite output");
        var consumed = roles(evaluated.consumed()); var produced = roles(evaluated.produced());
        var inputs = consumed.values().stream().map(quantity -> new CraftRecipe.Ingredient<ResourceKey>(List.of(quantity.key()),quantity.count())).toList();
        Object state = host.catalogState(); String fingerprint = fingerprint(host.level,inputs,outputs,state);
        String process = "stored/" + host.tile.getClass().getName() + "/" + fingerprint;
        result.add(new NativeMachineBackend.Recipe("industrialforegoing:stored/" + fingerprint + "/" + decoded.id(),process,inputs,outputs,outputs,decoded.durationTicks(),decoded.timeoutTicks(),
                new FiniteRecipe(host.tile.getClass().getName(),state,consumed,produced,produced,Map.of(),nativeRecipe,slot,null,Set.of(),false,Map.of()),false,decoded.sourceRecipes()));
    }
    private static boolean same(TitaniumRecipeEvaluator.Result value, Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> consumed,
                                Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> produced) {
        return value != null && value.consumed().equals(consumed) && value.produced().equals(produced);
    }
    private static Map<Role, TitaniumRecipeEvaluator.Quantity> roles(Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> values) {
        Map<Role, TitaniumRecipeEvaluator.Quantity> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Comparator.comparing(entry -> role(entry.getKey()).toString())).forEach(entry -> {
            if (result.putIfAbsent(role(entry.getKey()), entry.getValue()) != null) throw new UnsupportedOperationException("Native component roles are not unique");
        });
        return Collections.unmodifiableMap(result);
    }
    private static Role role(TitaniumRecipeEvaluator.Address address) {
        return new Role(call(address.owner(), address.fluid() ? TANK : INVENTORY, "getName").toString(), address.slot(), address.fluid());
    }
    private static String fingerprint(ServerLevel level, List<CraftRecipe.Ingredient<ResourceKey>> inputs, Map<ResourceKey,Long> outputs, Object state) {
        List<Object> ingredients = inputs.stream().map(value -> (Object)Map.of("alternatives", value.alternatives(), "count", value.count(), "tag", value.tag())).toList();
        return NativeMachineAdapter.profile(level, Map.of("inputs", ingredients, "outputs", outputs, "state", state));
    }

    private static NativeMachineBackend.Recipe decode(NativeRecipeSource.Entry holder, NativeStockSamples stock, ServerLevel level) {
        var recipe = holder.value();
        List<CraftRecipe.Ingredient<ResourceKey>> inputs = new ArrayList<>();
        Map<ResourceKey, Long> outputs = new LinkedHashMap<>();
        int nativeTicks = NativeRecipeSource.cookingTime(recipe), ticks = nativeTicks > 1 ? nativeTicks : 200;
        boolean nativeFields = false;
        for (var shape : recipeShapes(recipe.getClass())) for (var field : shape.fields()) {
            String name = field.name().toLowerCase(Locale.ROOT);
            if ((field.access() & 8) != 0) continue;
            if (field.descriptor().equals("L"+net.minecraft.world.level.block.state.BlockState.class.getName().replace('.','/')+";"))
                throw new UnsupportedOperationException("Native world-block transition requires real placed-block ownership: " + field.name());
            if (!name.startsWith("input") && !name.startsWith("output") && !name.equals("processingtime")) continue;
            Object value = field(recipe, shape.name(), field);
            if (name.equals("processingtime") && value instanceof Number amount) { ticks = Math.max(1, amount.intValue()); continue; }
            nativeFields = true;
            if (name.startsWith("input")) addInput(value, stock, inputs);
            else addOutput(value, outputs);
        }
        if (!nativeFields) {
            for (Ingredient ingredient : NativeRecipeSource.ingredients(recipe)) if (!ingredient.isEmpty()) addInput(ingredient, stock, inputs);
            addOutput(NativeRecipeSource.output(recipe, level.registryAccess()), outputs);
        }
        if (inputs.isEmpty()) throw new UnsupportedOperationException("No declared consumed inventory inputs; native setup/world/generator contract requires separate classification");
        if (outputs.isEmpty()) throw new UnsupportedOperationException("No deterministic native item/fluid output definition");
        return new NativeMachineBackend.Recipe(holder.id().toString(), NativeRecipeSource.typeKey(recipe).toString(),
                inputs, outputs, outputs, ticks, Math.max(7200, ticks * 20), new NativeRecipe(recipe, holder.type()), false);
    }

    private static void addInput(Object value, NativeStockSamples stock, List<CraftRecipe.Ingredient<ResourceKey>> inputs) {
        if (value == null) return;
        if (value instanceof Optional<?> optional) { optional.ifPresent(nested -> addInput(nested, stock, inputs)); return; }
        if (value instanceof Collection<?> collection) { collection.forEach(nested -> addInput(nested, stock, inputs)); return; }
        if (value.getClass().isArray()) { for (int i = 0; i < Array.getLength(value); i++) addInput(Array.get(value, i), stock, inputs); return; }
        Ingredient itemIngredient = NativeRecipeSource.nativeIngredient(value);
        if (itemIngredient != null) {
            LinkedHashSet<ResourceKey> alternatives = new LinkedHashSet<>();
            alternatives.addAll(stock.matching(itemIngredient));
            for (ItemStack stack : stock.representations(itemIngredient)) alternatives.add(new ItemKey(stack));
            if (alternatives.isEmpty()) throw new UnsupportedOperationException("Native item ingredient has no enabled representations");
            inputs.add(new CraftRecipe.Ingredient<>(List.copyOf(alternatives), 1, VanillaProcessingAdapter.ingredientTag(itemIngredient)));
        } else if (value.getClass().getName().endsWith(".fluids.crafting.SizedFluidIngredient")) {
            Object ingredient = NativeBackendReflection.call(value, "ingredient");
            long amount = ((Number)NativeBackendReflection.call(value, "amount")).longValue();
            LinkedHashSet<ResourceKey> alternatives = new LinkedHashSet<>();
            alternatives.addAll(stock.matchingFluid(ingredient));
            for (Object sample : NativeBackendReflection.list(NativeBackendReflection.call(value, "getFluids"))) if (sample instanceof FluidStack stack && !stack.isEmpty()) alternatives.add(new FluidKey(stack));
            if (alternatives.isEmpty()) throw new UnsupportedOperationException("Native fluid ingredient has no enabled representations");
            inputs.add(new CraftRecipe.Ingredient<>(List.copyOf(alternatives), amount));
        } else if (value instanceof ItemStack stack && !stack.isEmpty()) {
            inputs.add(new CraftRecipe.Ingredient<>(List.of(new ItemKey(stack)), stack.getCount()));
        } else if (value instanceof FluidStack stack && !stack.isEmpty()) {
            inputs.add(new CraftRecipe.Ingredient<>(List.of(new FluidKey(stack)), stack.getAmount()));
        } else throw new UnsupportedOperationException("Unsupported native input data contract " + value.getClass().getName());
    }
    private static void addOutput(Object value, Map<ResourceKey, Long> outputs) {
        if (value == null) return;
        if (value instanceof Optional<?> optional) { optional.ifPresent(nested -> addOutput(nested, outputs)); return; }
        if (value instanceof Collection<?> collection) { collection.forEach(nested -> addOutput(nested, outputs)); return; }
        if (value instanceof ItemStack stack) { if (!stack.isEmpty()) outputs.merge(new ItemKey(stack), (long) stack.getCount(), Math::addExact); }
        else if (value instanceof FluidStack stack) { if (!stack.isEmpty()) outputs.merge(new FluidKey(stack), (long) stack.getAmount(), Math::addExact); }
        else if (value instanceof Ingredient ingredient) {
            ItemStack[] representations = ingredient.items().map(net.minecraft.world.item.ItemStack::new).toArray(net.minecraft.world.item.ItemStack[]::new);
            if (representations.length > 0) addOutput(representations[0], outputs);
        } else throw new UnsupportedOperationException("Unsupported native output data contract " + value.getClass().getName());
    }

    private static List<NativeBytecode.Shape> recipeShapes(Class<?> type) {
        List<NativeBytecode.Shape> result = new ArrayList<>();
        for (Class<?> current = type; current != null && !current.getName().startsWith("net.minecraft.") && !current.getName().startsWith("java."); current = current.getSuperclass())
            result.add(NativeBytecode.inspect(current));
        return result;
    }

    private final class Host implements Machine {
        private final ServerLevel level;
        private final BlockPos position;
        private final BlockEntity tile;
        private final NativeEnchantmentPreview.FeedGuard enchantFeed;
        private ItemKey previewTarget;
        private Object previewInput;
        private List<Object> pooledExpectedState=List.of();
        private Map<ResourceKey,Long> pooledExpectedProduction=Map.of();
        private Map<Role,TitaniumRecipeEvaluator.Quantity> pooledExpectedUnused=Map.of();
        private Set<Object> types;
        Host(ServerLevel level, BlockPos position, BlockEntity tile) {
            this.level = level; this.position = position; this.tile = tile;
            enchantFeed=NativeEnchantmentPreview.usesExternalFeed(tile.getClass())?new NativeEnchantmentPreview.FeedGuard(level,position):null;
        }
        @Override public Object identity() { return tile; }
        @Override public Object instance() { return tile; }
        @Override public boolean valid() { return level.hasChunkAt(position) && !tile.isRemoved() && level.getBlockEntity(position) == tile && (enchantFeed==null || enchantFeed.valid()); }
        @Override public boolean accepts(NativeMachineBackend.Recipe recipe) {
            if (recipe.nativeRecipe() instanceof FiniteRecipe finite) return finite.type().equals(tile.getClass().getName()) && finite.state().equals(finite.pooledBatch()?configuration(false):catalogState()) && setupPresent(finite);
            return recipe.nativeRecipe() instanceof NativeRecipe nativeRecipe && !hasVanillaRecipeCache() && recipeTypes().contains(nativeRecipe.type());
        }
        private boolean setupPresent(FiniteRecipe finite) {
            Map<Role, TitaniumRecipeEvaluator.Address> addresses = addresses();
            for(var entry:finite.retainedInputs().entrySet()) {
                var address=addresses.get(entry.getKey());
                if(address==null || !port(address,false,false).snapshot().equals(Map.of(entry.getValue().key(),entry.getValue().count()))) return false;
            }
            if(!finite.internalInputs().isEmpty()) for(var address:TitaniumRecipeEvaluator.inputs(tile)) {
                Role role=role(address);
                if(!finite.consumed().containsKey(role) && !finite.produced().containsKey(role) && !finite.setup().containsKey(role)
                        && !port(address,false,false).snapshot().isEmpty()) return false;
            }
            for (var entry : finite.setup().entrySet()) {
                var address = addresses.get(entry.getKey());
                if (address == null || port(address, false, false).snapshot().getOrDefault(entry.getValue().key(), 0L) < entry.getValue().count()) return false;
            }
            return true;
        }
        @Override public boolean ready(NativeMachineBackend.Recipe recipe, List<CraftPlan.Selection<ResourceKey>> selected) {
            if (!(recipe.nativeRecipe() instanceof FiniteRecipe finite)) return true;
            if(enchantFeed!=null && !enchantFeed.valid()) return false;
            Map<Role, TitaniumRecipeEvaluator.Address> addresses = addresses();
            if(finite.randomTarget()!=null || enchantFeed!=null || !finite.internalInputs().isEmpty() || finite.pooledBatch()) for(Role role:finite.produced().keySet()) {
                var address=addresses.get(role); if(address==null || !port(address,false,true).snapshot().isEmpty()) return false;
            }
            Map<TitaniumRecipeEvaluator.Address, TitaniumRecipeEvaluator.Quantity> seed = new LinkedHashMap<>();
            for (var entry : finite.consumed().entrySet()) {
                var address = addresses.get(entry.getKey()); if (address == null) return false; seed.put(address, entry.getValue());
            }
            for(var entry:finite.retainedInputs().entrySet()) {
                var address=addresses.get(entry.getKey()); if(address==null) return false;
                var own=seed.get(address); var retained=entry.getValue();
                if(own==null || !own.key().equals(retained.key())) return false;
                seed.put(address,new TitaniumRecipeEvaluator.Quantity(own.key(),Math.addExact(own.count(),retained.count())));
            }
            for (var entry : finite.setup().entrySet()) {
                var address = addresses.get(entry.getKey()); if (address == null) return false;
                Map<ResourceKey, Long> actual = port(address, false, false).snapshot();
                if (actual.getOrDefault(entry.getValue().key(), 0L) < entry.getValue().count()) return false;
                seed.put(address, entry.getValue());
            }
            try {
                var evaluator = new TitaniumRecipeEvaluator(tile, seed).inLevel(level)
                        .enchanting(enchantFeed==null && finite.randomTarget()==null?null:new NativeEnchantmentPreview(level,enchantFeed,finite.randomTarget()));
                if(finite.pooledBatch()) {
                    var batch=evaluator.evaluateBatch(); if(batch==null) return false;
                    for(var entry:roles(batch.resources().consumed()).entrySet()) {
                        var owned=finite.consumed().get(entry.getKey());
                        if(owned==null || !owned.key().equals(entry.getValue().key()) || owned.count()<entry.getValue().count()) return false;
                    }
                    Map<ResourceKey,Long> produced=new HashMap<>(); batch.resources().produced().values().forEach(value->produced.merge(value.key(),value.count(),Math::addExact));
                    if(!recipe.guaranteed().entrySet().stream().allMatch(entry->produced.getOrDefault(entry.getKey(),0L)>=entry.getValue())) return false;
                    Map<Role,TitaniumRecipeEvaluator.Quantity> unused=new HashMap<>();
                    for(Role role:finite.consumed().keySet()) {
                        var quantity=batch.resources().after().get(addresses.get(role));
                        if(quantity!=null) unused.put(role,quantity);
                    }
                    pooledExpectedState=batch.pooledAfter(); pooledExpectedProduction=Map.copyOf(produced); pooledExpectedUnused=Map.copyOf(unused);
                    return true;
                }
                var evaluated = finite.storedRecipe() == null ? evaluator.evaluate() : evaluator.stored(finite.storedRecipe(),level.registryAccess(),finite.storedSlot());
                return evaluated != null && roles(evaluated.consumed()).equals(finite.consumed()) && roles(evaluated.produced()).equals(finite.representative());
            } catch (UnsupportedOperationException unavailable) { return false; }
        }
        @Override public Object catalogState() {
            return configuration(true);
        }
        private Object configuration(boolean transientPools) {
            List<Object> result = new ArrayList<>();
            result.add(tile.getClass().getName());
            if(enchantFeed!=null) result.add(enchantFeed.catalogState());
            for(Object pool:TitaniumRecipeEvaluator.pooledState(tile)) {
                var values=(List<?>)pool;
                result.add(transientPools?values:List.of(values.get(0),values.get(1),values.get(3)));
            }
            result.addAll(recipeTypes().stream().map(type -> BuiltInRegistries.RECIPE_TYPE.getKey((RecipeType<?>)type).toString()).sorted().toList());
            Object augments = call(tile, "com.hrznstudio.titanium.block.tile.MachineTile", "getInstalledAugments");
            if (augments instanceof List<?> values) for (Object value : values) if (value instanceof ItemStack stack && !stack.isEmpty()) result.add(new ItemKey(stack));
            for (var shape : SURFACES.get(tile.getClass()).shapes()) {
                for (var method : shape.methods()) if (method.name().equals("saveSettings")) for (var reference : method.references()) if (reference.opcode() == 180) {
                    try { Object value = reference.readInstance(tile); if (value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Enum<?>) result.add(reference.owner() + "." + reference.name() + "=" + value); }
                    catch (RuntimeException unsupported) { result.add(reference.owner() + "." + reference.name() + "=unavailable"); }
                }
                for (var reference : shape.references()) if (reference.staticRead() && (reference.descriptor().equals("I") || reference.descriptor().equals("J") || reference.descriptor().equals("Z") || reference.descriptor().equals("D") || reference.descriptor().equals("F"))) {
                    try { result.add(reference.owner() + "." + reference.name() + "=" + reference.readStatic(tile.getClass().getClassLoader())); }
                    catch (RuntimeException unsupported) { result.add(reference.owner() + "." + reference.name() + "=unavailable"); }
                }
                for (var reference : shape.references()) if (reference.staticRead() && reference.descriptor().equals("L"+net.minecraft.tags.TagKey.class.getName().replace('.','/')+";")) {
                    Object value = reference.readStatic(tile.getClass().getClassLoader());
                    if (value instanceof net.minecraft.tags.TagKey<?> tag && tag.registry().equals(net.minecraft.core.registries.Registries.ITEM)) {
                        @SuppressWarnings("unchecked") var itemTag = (net.minecraft.tags.TagKey<net.minecraft.world.item.Item>)tag;
                        List<String> entries = BuiltInRegistries.ITEM.get(itemTag).map(set -> set.stream().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).sorted().toList()).orElse(List.of());
                        result.add(List.of(tag.location().toString(), entries));
                    }
                }
            }
            return List.copyOf(result);
        }
        @Override public List<GlobalPos> claimedPositions() {
            return enchantFeed==null?List.of(GlobalPos.of(level.dimension(),position)):
                    List.of(GlobalPos.of(level.dimension(),position),GlobalPos.of(level.dimension(),enchantFeed.position));
        }
        @Override public boolean completed(NativeMachineBackend.Recipe recipe,int polls,Map<ResourceKey,Long> produced) {
            if(recipe.nativeRecipe() instanceof FiniteRecipe finite && finite.pooledBatch()) {
                if(pooledExpectedProduction.isEmpty() || !pooledExpectedProduction.entrySet().stream().allMatch(entry->produced.getOrDefault(entry.getKey(),0L)>=entry.getValue())) return false;
                Object progress=call(tile,PROCESSING,"getProgressBar");
                if(((Number)call(progress,"com.hrznstudio.titanium.component.progress.ProgressBarComponent","getProgress")).intValue()!=0
                        || !TitaniumRecipeEvaluator.pooledState(tile).equals(pooledExpectedState)) return false;
                var addresses=addresses();
                for(Role role:finite.consumed().keySet()) {
                    var address=addresses.get(role); if(address==null) return false;
                    var unused=pooledExpectedUnused.get(role);
                    Map<ResourceKey,Long> expected=unused==null?Map.of():Map.of(unused.key(),unused.count());
                    if(!port(address,false,false).snapshot().equals(expected)) return false;
                }
                return true;
            }
            return recipe.nativeRecipe() instanceof FiniteRecipe finite && finite.randomTarget()!=null
                    && produced.entrySet().stream().anyMatch(entry->entry.getKey() instanceof ItemKey && entry.getValue()>0);
        }
        @Override public boolean requiresNativeCompletion(NativeMachineBackend.Recipe recipe) { return recipe.nativeRecipe() instanceof FiniteRecipe finite && finite.pooledBatch(); }
        @Override public boolean allowsUnusedInputs(NativeMachineBackend.Recipe recipe) { return recipe.nativeRecipe() instanceof FiniteRecipe finite && finite.pooledBatch(); }
        boolean hasVanillaRecipeCache() {
            for (var shape : SURFACES.get(tile.getClass()).shapes()) for (var field : shape.fields()) {
                String descriptor = field.descriptor(); while (descriptor.startsWith("[")) descriptor = descriptor.substring(1);
                if (!descriptor.startsWith("Lnet/minecraft/") || !descriptor.endsWith(";")) continue;
                try { if (net.minecraft.world.item.crafting.Recipe.class.isAssignableFrom(Class.forName(descriptor.substring(1,descriptor.length()-1).replace('/','.'),false,tile.getClass().getClassLoader()))) return true; }
                catch (ClassNotFoundException unavailable) { /* The optional recipe type cannot be used. */ }
            }
            return false;
        }
        Set<Object> recipeTypes() {
            if (types != null) return types;
            Set<Object> discovered = Collections.newSetFromMap(new IdentityHashMap<>());
            ClassLoader loader = tile.getClass().getClassLoader();
            for (var shape : SURFACES.get(tile.getClass()).shapes()) for (var reference : shape.references()) {
                if (!reference.staticRead() || !(reference.descriptor().contains("RecipeType;") || reference.descriptor().contains("DeferredHolder;"))) continue;
                try {
                    Object value = reference.readStatic(loader);
                    if (value instanceof java.util.function.Supplier<?> supplier) value = supplier.get();
                    if (value instanceof RecipeType<?>) discovered.add(value);
                } catch (RuntimeException unavailable) {
                    diagnostics.add(new Diagnostic(position.toString(), tile.getClass().getName(), "PROVIDER_UNAVAILABLE", reference.owner() + "." + reference.name()));
                }
            }
            // Native typed cache fields also advertise vanilla/custom recipe families without a provider field.
            for (var shape : SURFACES.get(tile.getClass()).shapes()) for (var field : shape.fields()) {
                String descriptor = field.descriptor();
                while (descriptor.startsWith("[")) descriptor = descriptor.substring(1);
                if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) continue;
                String name = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                if (!name.contains("Recipe") || name.contains("RecipeType")) continue;
                for (var holder : NativeRecipeSource.entries(level)) if (inherits(holder.value().getClass(), name)) discovered.add(holder.type());
            }
            types = Collections.unmodifiableSet(discovered);
            return types;
        }
        @Override public List<Port> ports(NativeMachineBackend.Recipe recipe) {
            if (recipe.nativeRecipe() instanceof FiniteRecipe finite) {
                Map<Role, TitaniumRecipeEvaluator.Address> addresses = addresses(); List<Port> result = new ArrayList<>();
                Set<Role> roles = new LinkedHashSet<>(finite.consumed().keySet()); roles.addAll(finite.produced().keySet());
                for (Role role : roles) {
                    var address = addresses.get(role); if (address == null) throw new IllegalStateException("Native resource role disappeared: " + role);
                    boolean input = finite.consumed().containsKey(role), output = finite.produced().containsKey(role);
                    var quantity = finite.consumed().get(role); Port port = address.fluid()?port(address,input,output):new ItemPort((IItemHandler)address.owner(),address.slot(),input,output,finite.internalInputs().contains(role));
                    Port owned=NativeMachinePorts.callbacks(port.identity(), input, output, port::snapshot,
                            (key, amount, simulate) -> quantity != null && key.equals(quantity.key()) && amount <= quantity.count() ? port.insert(key, amount, simulate) : 0, port::extract);
                    var retained=finite.retainedInputs().get(role);
                    if(retained!=null) owned=NativeMachinePorts.retainedInput(owned,Map.of(retained.key(),retained.count()));
                    result.add(finite.pooledBatch() && output?NativeMachinePorts.streaming(owned):owned);
                }
                return List.copyOf(result);
            }
            List<Port> ports = new ArrayList<>();
            Object augment = call(tile, "com.hrznstudio.titanium.block.tile.MachineTile", "getAugmentInventory");
            Object inventories = call(tile, ACTIVE, "getMultiInventoryComponent");
            for (Object component : inventories==null?List.of():(Collection<?>) call(inventories, "com.hrznstudio.titanium.component.inventory.MultiInventoryComponent", "getInventoryHandlers")) {
                if (component == augment || !(component instanceof IItemHandler inventory)) continue;
                @SuppressWarnings("unchecked") var insert = (BiPredicate<ItemStack, Integer>) call(component, INVENTORY, "getInsertPredicate");
                @SuppressWarnings("unchecked") var extract = (BiPredicate<ItemStack, Integer>) call(component, INVENTORY, "getExtractPredicate");
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    int index = slot;
                    boolean input = recipe.inputs().stream().flatMap(value -> value.alternatives().stream()).anyMatch(key -> key instanceof ItemKey item && insert.test(item.sample(), index));
                    boolean output = recipe.possible().keySet().stream().anyMatch(key -> key instanceof ItemKey item && extract.test(item.sample(), index));
                    if (input || output) ports.add(new ItemPort(inventory, slot, input, output));
                }
            }
            Object tanks = call(tile, ACTIVE, "getMultiTankComponent");
            for (Object component : tanks==null?List.of():(Collection<?>) call(tanks, "com.hrznstudio.titanium.component.fluid.MultiTankComponent", "getTanks")) {
                Object action = call(component, TANK, "getTankAction");
                boolean input = Boolean.TRUE.equals(call(action, TANK + "$Action", "canFill"));
                boolean output = Boolean.TRUE.equals(call(action, TANK + "$Action", "canDrain"));
                if (input || output) ports.add(new FluidPort(component, input, output));
            }
            return List.copyOf(ports);
        }
        private Map<Role, TitaniumRecipeEvaluator.Address> addresses() {
            Map<Role, TitaniumRecipeEvaluator.Address> result = new LinkedHashMap<>();
            // Include output-only tanks as well as candidate input components.
            for (var address : TitaniumRecipeEvaluator.inputs(tile)) result.put(role(address), address);
            Object tanks = call(tile, ACTIVE, "getMultiTankComponent");
            for (Object tank : tanks==null?List.of():(Collection<?>) call(tanks, "com.hrznstudio.titanium.component.fluid.MultiTankComponent", "getTanks")) {
                var address = new TitaniumRecipeEvaluator.Address(tank, 0, true); result.put(role(address), address);
            }
            return result;
        }
    }

    private static Port port(TitaniumRecipeEvaluator.Address address, boolean input, boolean output) {
        return address.fluid() ? new FluidPort(address.owner(), input, output) : new ItemPort((IItemHandler) address.owner(), address.slot(), input, output);
    }

    private record ItemIdentity(Object inventory, int slot) {}
    private record ItemPort(IItemHandler inventory, int slot, boolean input, boolean output,boolean internal) implements Port {
        ItemPort(IItemHandler inventory,int slot,boolean input,boolean output) { this(inventory,slot,input,output,false); }
        @Override public Object identity() { return new ItemIdentity(inventory, slot); }
        @Override public Map<ResourceKey, Long> snapshot() {
            ItemStack stack = inventory.getStackInSlot(slot);
            return stack.isEmpty() ? Map.of() : Map.of(new ItemKey(stack), (long) stack.getCount());
        }
        @Override public long insert(ResourceKey key, long amount, boolean simulate) {
            if (!input || !(key instanceof ItemKey item) || amount <= 0 || amount > Integer.MAX_VALUE) return 0;
            ItemStack offered = item.sample().copyWithCount((int) amount);
            if (!internal && !inventory.isItemValid(slot, offered)) return 0;
            ItemStack rest = inventory.insertItem(slot, offered, simulate);
            if (!rest.isEmpty() && (!item.matches(rest) || rest.getCount() > amount)) throw new IllegalStateException("Titanium returned another insertion resource");
            return amount - rest.getCount();
        }
        @Override public long extract(ResourceKey key, long amount, boolean simulate) {
            if (!(key instanceof ItemKey item) || amount <= 0 || !item.matches(inventory.getStackInSlot(slot))) return 0;
            if(input) {
                // The shared executor admits empty consumed slots and bounds recovery by
                // its confirmed insertions. Titanium's automation output filter also
                // blocks GUI-owned input recovery, so use its native mutable-slot API.
                ItemStack present=inventory.getStackInSlot(slot);
                int recovered=(int)Math.min(amount,present.getCount());
                if(!simulate) NativeBackendReflection.call(inventory,"setStackInSlot",slot,present.copyWithCount(present.getCount()-recovered));
                return recovered;
            }
            ItemStack result = inventory.extractItem(slot, (int) Math.min(Integer.MAX_VALUE, amount), simulate);
            if (!result.isEmpty() && (!item.matches(result) || result.getCount() > amount)) throw new IllegalStateException("Titanium extracted another resource");
            return result.getCount();
        }
    }
    private record FluidPort(Object tank, boolean input, boolean output) implements Port {
        @Override public Object identity() { return tank; }
        @Override public Map<ResourceKey, Long> snapshot() {
            FluidStack stack = (FluidStack) call(tank, TANK, "getFluid");
            return stack.isEmpty() ? Map.of() : Map.of(new FluidKey(stack), (long) stack.getAmount());
        }
        @Override public long insert(ResourceKey key, long amount, boolean simulate) {
            if (!input || !(key instanceof FluidKey fluid) || amount <= 0 || amount > Integer.MAX_VALUE) return 0;
            return ((Number) call(tank, TANK, "fill", new Class<?>[]{FluidStack.class, IFluidHandler.FluidAction.class}, fluid.sample().copyWithAmount((int) amount), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE)).longValue();
        }
        @Override public long extract(ResourceKey key, long amount, boolean simulate) {
            if (!(key instanceof FluidKey fluid) || amount <= 0) return 0;
            FluidStack result = (FluidStack) call(tank, TANK, "drainForced", new Class<?>[]{FluidStack.class, IFluidHandler.FluidAction.class}, fluid.sample().copyWithAmount((int) Math.min(Integer.MAX_VALUE, amount)), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (!result.isEmpty() && (!new FluidKey(result).equals(key) || result.getAmount() > amount)) throw new IllegalStateException("Titanium extracted another fluid");
            return result.getAmount();
        }
    }

    private static boolean inherits(Class<?> type, String name) { return contract(type, name); }
    private static boolean contract(Class<?> type, String name) {
        if (type == null) return false;
        if (type.getName().equals(name)) return true;
        for (Class<?> iface : type.getInterfaces()) if (contract(iface, name)) return true;
        return contract(type.getSuperclass(), name);
    }
    private static Object field(Object target, String owner, NativeBytecode.Field field) {
        return new NativeBytecode.Reference(180, owner, field.name(), field.descriptor()).readInstance(target);
    }
    static Object call(Object target, String owner, String method) { return call(target, owner, method, new Class<?>[0]); }
    static Object call(Object target, String owner, String method, Class<?>[] signature, Object... arguments) {
        if (target == null) throw new IllegalStateException("Null native receiver for " + owner + "." + method);
        try {
            Class<?> api = Class.forName(owner, false, target.getClass().getClassLoader());
            return api.getMethod(method, signature).invoke(target, arguments);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Unsupported Titanium contract " + owner + "." + method, failure);
        }
    }
}
