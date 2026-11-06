package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;
import com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler;
import com.cappleapple.astralrepository.platform.items.IItemHandler;
import static com.cappleapple.astralrepository.crafting.NativeContractAccess.*;

/** Native IE recipe contracts and formed multiblock process contexts, without a machine-name table. */
public final class ImmersiveEngineeringBackend implements NativeMachineBackend {
    private static final String API = "blusunrize.immersiveengineering.api.crafting.";
    private record Type(String id, RecipeType<?> type, Class<?> recipeClass) {}
    private record Bound(Object recipe, Class<?> recipeClass, String variant) {}
    private record Assembly(Object pattern,Object recipe,int index,String configuration,List<Boolean> fluidInputs) {}
    private record QueryChoice(CraftRecipe.Ingredient<ResourceKey> ingredient,boolean fluid) {}
    private record Association(Class<?> state,Class<?> logic) {}
    private static final Map<Association,Set<Class<?>>> ASSOCIATIONS=new HashMap<>();
    public record Classification(String id, String type, String contract, String classification, String reason) {}
    public record MachineContract(String registration,String logic,String state,List<String> recipes,String boundary) {}
    private List<Classification> audit = List.of();
    public List<Classification> audit() { return audit; }
    public List<MachineContract> machineInventory(){
        Class<?> registry=optional("blusunrize.immersiveengineering.common.register.IEMultiblockLogic");if(registry==null)return List.of();
        List<MachineContract> result=new ArrayList<>();
        for(var field:NativeBytecode.inspect(registry).fields())if((field.access()&8)!=0&&field.descriptor().endsWith("/MultiblockRegistration;")){
            Object registration=staticField(registry,field.name()),logic=call(registration,"logic");
            var matcher=java.util.regex.Pattern.compile("MultiblockRegistration<L([^;]+);").matcher(field.signature());
            Class<?> state=matcher.find()?optional(matcher.group(1).replace('/','.')):null;
            List<String> recipes=state==null?List.of():associated(state,logic.getClass()).stream().map(Class::getName).sorted().toList();
            result.add(new MachineContract(String.valueOf(call(registration,"id")),logic.getClass().getName(),state==null?"unknown-native-state":state.getName(),recipes,
                    recipes.isEmpty()?"Native storage, transport, control or world/ambient behavior; no recipe process context":"Native recipe context; formed structure, native setup, power/fuel, and physical output destinations required"));
        }
        Class<?> blockEntities=optional("blusunrize.immersiveengineering.common.register.IEBlockEntities");
        if(blockEntities!=null)for(var field:NativeBytecode.inspect(blockEntities).fields()){
            var matcher=java.util.regex.Pattern.compile("BlockEntityType<L(blusunrize/immersiveengineering/[^;]+);").matcher(field.signature());
            if(!matcher.find())continue;String name=matcher.group(1).replace('/','.');Class<?> type=optional(name);if(type==null)continue;
            List<String> referenced=NativeBytecode.hierarchy(type).stream().flatMap(shape -> shape.references().stream()).map(NativeBytecode.Reference::owner)
                    .filter(owner -> owner.startsWith(API)||owner.startsWith("blusunrize.immersiveengineering.api.energy.")).distinct().sorted().toList();
            String boundary=referenced.stream().anyMatch(owner -> owner.endsWith("ClocheRecipe"))?"Native crop producer: protected seed/soil, pooled water/fertilizer and FE consumption; no fictitious seed input"
                    :referenced.stream().anyMatch(owner -> owner.startsWith("blusunrize.immersiveengineering.api.energy."))?"Native FE resource source or energy device; stock provider observes actual stored energy"
                    :"Native inventory, transport, control, world interaction or manual device; not an invented machine recipe";
            result.add(new MachineContract(field.name(),name,name,referenced,boundary));
        }
        return List.copyOf(result);
    }
    @Override public String id() { return "immersiveengineering"; }

    private static List<Type> types() {
        Class<?> registry = optional(API+"IERecipeTypes"); if (registry == null) return List.of();
        List<Type> result = new ArrayList<>();
        for (var field : NativeBytecode.inspect(registry).fields()) {
            if ((field.access() & 8) == 0 || !field.descriptor().endsWith("IERecipeTypes$TypeWithClass;")) continue;
            Object pair = staticField(registry, field.name());
            Object nativeType = call(pair, "get"), recipeClass = call(pair, "recipeClass");
            if (nativeType instanceof RecipeType<?> type && recipeClass instanceof Class<?> contract)
                result.add(new Type(BuiltInRegistries.RECIPE_TYPE.getKey(type).toString(), type, contract));
        }
        return List.copyOf(result);
    }
    @Override public Object identityAt(ServerLevel level, BlockPos position) {
        Object helper = helper(level.getBlockEntity(position)); Object state = call(helper,"getState");
        return state == null ? null : state;
    }
    private static Object helper(BlockEntity entity) {
        return entity!=null&&entity.getClass().getName().startsWith("blusunrize.immersiveengineering.")&&contract(entity,"blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE") ? call(entity,"getHelper") : null;
    }
    @Override public Machine machine(CraftingService.NetworkAccess access, GlobalPos position) {
        ServerLevel level = access.level().getServer().getLevel(position.dimension());
        if (level == null || !level.hasChunkAt(position.pos())) return null;
        BlockEntity entity = level.getBlockEntity(position.pos()); Object helper = helper(entity);
        Object state = call(helper,"getState"), context = call(helper,"getContext");
        if (state == null || context == null) return null;
        Object registration = call(helper,"getMultiblock"), logic = call(registration,"logic");
        Set<Class<?>> contracts = associated(state.getClass(), logic.getClass());
        if (contracts.isEmpty() && field(state,"patterns") instanceof Object[] patterns && Arrays.stream(patterns).allMatch(pattern -> pattern.getClass().getName().endsWith(".CrafterPatternInventory")))
            return new AssemblyMachine(access,level,helper,state,context,registration);
        if (contracts.isEmpty()) return null;
        return new Multiblock(access, level, helper, state, context, registration, contracts);
    }
    private static Set<Class<?>> associated(Class<?> state, Class<?> logic) {
        return ASSOCIATIONS.computeIfAbsent(new Association(state,logic),key -> findAssociated(key.state,key.logic));
    }
    private static Set<Class<?>> findAssociated(Class<?> state, Class<?> logic) {
        List<Type> types = types(); Set<Class<?>> result = new LinkedHashSet<>();
        Set<Class<?>> visited = new HashSet<>(); ArrayDeque<Class<?>> pending = new ArrayDeque<>(List.of(state,logic));
        while (!pending.isEmpty() && visited.size() < 128) {
            Class<?> current = pending.remove(); if (!visited.add(current)) continue;
            var shape = NativeBytecode.inspect(current);
            String evidence = shape.fields().toString()+shape.references();
            for (Type type : types) if (evidence.contains(type.recipeClass.getName()) || evidence.contains(type.recipeClass.getName().replace('.','/'))) result.add(type.recipeClass);
            for (var field : shape.fields()) {
                if (!field.descriptor().startsWith("Lblusunrize/immersiveengineering/") || field.descriptor().contains("client/")) continue;
                Class<?> nested = optional(field.descriptor().substring(1,field.descriptor().length()-1).replace('/','.'));
                if (nested != null && !nested.getName().startsWith(API)) pending.add(nested);
            }
            for (var reference : shape.references()) {
                if (!reference.owner().startsWith("blusunrize.immersiveengineering.common.blocks.multiblocks.process.")
                        && !reference.owner().startsWith("blusunrize.immersiveengineering.common.blocks.multiblocks.logic.")) continue;
                Class<?> nested = optional(reference.owner()); if (nested != null) pending.add(nested);
            }
        }
        return Set.copyOf(result);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    @Override public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> machines) {
        List<Recipe> result = new ArrayList<>(); List<Classification> classified = new ArrayList<>();
        for (Type type : types()) for (Object entry : nativeRecipes(type,access.level())) {
            NativeRecipeSource.Entry holder = NativeRecipeSource.entry(entry); if(holder==null)continue; Object nativeRecipe = holder.value();
            try {
                List<CraftRecipe.Ingredient<ResourceKey>> inputs = inputs(nativeRecipe);
                Map<ResourceKey,Long> guaranteed = outputs(nativeRecipe,false), possible = outputs(nativeRecipe,true);
                if (inputs.isEmpty() || possible.isEmpty()) {
                    String classification="native-condition",reason="No finite consumed-resource conversion contract";
                    if(field(nativeRecipe,"seed") instanceof Ingredient&&field(nativeRecipe,"soil") instanceof Ingredient){classification="native-pooled-growth-source";reason="Native crop production keeps seed/soil, consumes pooled fertilizer/water and FE, and exposes only actually produced stock";}
                    else if(field(nativeRecipe,"growthModifier") instanceof Number){classification="native-growth-modifier";reason="Native fertilizer modifier affects the pooled growth source; no invented item recipe";}
                    else if(field(nativeRecipe,"burnTime") instanceof Number&&field(nativeRecipe,"input") instanceof Ingredient){classification="supported-native-furnace-fuel";reason="Native FurnaceHandler fuel policy selects and consumes this real fuel through supplemental delivery";}
                    else if(call(nativeRecipe,"getBurnTime") instanceof Number){classification="native-energy-fuel-data";reason="Native fuelled energy generation; FE providers expose actual stored energy";}
                    else if(field(nativeRecipe,"biomeTagPredicates")!=null){classification="native-world-deposit-source";reason="Native mineral veins and excavation depend on finite world deposits; only actual extracted stock is available";}
                    else if(field(nativeRecipe,"temperature") instanceof Number||field(nativeRecipe,"biomes")!=null){classification="native-ambient-energy-data";reason="Native world/biome energy conditions; FE providers expose actual generated energy without fictitious consumables";}
                    classified.add(new Classification(holder.id().toString(),type.id,type.recipeClass.getName(),classification,reason));
                    continue;
                }
                int duration = (int)Math.max(1,number(call(nativeRecipe,"getTotalProcessTime"),number(field(nativeRecipe,"time"),1)));
                if (call(nativeRecipe,"getOutputs") instanceof List<?> && field(nativeRecipe,"defaultOutputs") != null && !inputs.isEmpty()) {
                    // Recycling's native deterministic preview scales metals/nuggets by the exact input's durability.
                    Set<ItemKey> candidates = new LinkedHashSet<>();
                    for(ResourceKey key:inputs.getFirst().alternatives())if(key instanceof ItemKey item)candidates.add(item);
                    Object sized=field(nativeRecipe,"input"); Object ingredient=call(sized,"getBaseIngredient");
                    for(ResourceKey key:access.resourceSnapshot().keySet())if(key instanceof ItemKey item && ingredient instanceof Ingredient nativeIngredient && nativeIngredient.test(item.sample()))candidates.add(item);
                    int variant=0;
                    for(ItemKey item:candidates){
                        Map<ResourceKey,Long> recycled=new LinkedHashMap<>();
                        add(recycled,call(nativeRecipe,"generateActualOutput",item.sample(),NonNullList.create(),0L));
                        if(recycled.isEmpty())continue;
                        var exactInputs=new ArrayList<>(inputs); exactInputs.set(0,new CraftRecipe.Ingredient<>(List.of(item),inputs.getFirst().count()));
                        result.add(new Recipe("immersiveengineering:native_input/"+NativeMachineAdapter.profile(access.level(),item)+"/"+holder.id(),type.id,exactInputs,recycled,recycled,duration,Math.max(7200,duration*8),new Bound(nativeRecipe,type.recipeClass,"native"),false));
                    }
                    classified.add(new Classification(holder.id().toString(),type.id,type.recipeClass.getName(),"processing","Native durability-dependent recycling quantities, including stock component identities"));
                    continue;
                }
                Set<String> variants = new LinkedHashSet<>(); variants.add("native");
                if (field(nativeRecipe,"secondaryStripping") != null) variants = Set.of("blade","stripping");
                for (String variant : variants) {
                    Map<ResourceKey,Long> actualGuaranteed = guaranteed, actualPossible = possible;
                    if (!variant.equals("native")) {
                        actualGuaranteed = sawmillOutputs(nativeRecipe,variant.equals("blade")); actualPossible = actualGuaranteed;
                    }
                    if (actualPossible.isEmpty()) continue;
                    result.add(new Recipe("immersiveengineering:native/"+variant+"/"+holder.id(),type.id+"/"+variant,inputs,actualGuaranteed,actualPossible,duration,
                            Math.max(7200,duration*8),new Bound(nativeRecipe,type.recipeClass,variant),!actualGuaranteed.equals(actualPossible)));
                }
                classified.add(new Classification(holder.id().toString(),type.id,type.recipeClass.getName(),"processing","Counted native item/fluid inputs and observed physical products"));
            } catch (RuntimeException failure) {
                classified.add(new Classification(holder.id().toString(),type.id,type.recipeClass.getName(),"unsupported-contract",failure.getMessage()));
            }
        }
        for(Machine machine:machines)if(machine instanceof AssemblyMachine assembler)result.addAll(assembler.recipes(access,classified));
        audit = List.copyOf(classified); return List.copyOf(result);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static List<?> nativeRecipes(Type type,ServerLevel level){
        for(Class<?> contract:hierarchy(type.recipeClass))for(var field:NativeBytecode.inspect(contract).fields())
            if((field.access()&8)!=0 && field.descriptor().endsWith("/CachedRecipeList;")){
                Object list=call(staticField(contract,field.name()),"getRecipes",level);if(list instanceof List<?> recipes)return recipes;
            }
        return NativeRecipeSource.allFor(level,type.type);
    }
    private static List<CraftRecipe.Ingredient<ResourceKey>> inputs(Object recipe) {
        List<CraftRecipe.Ingredient<ResourceKey>> result = new ArrayList<>();
        Object nativeItems = call(recipe,"getItemInputs");
        List<?> items = nativeItems instanceof List<?> list ? list : fields(recipe,f -> f.descriptor().equals("L"+API.replace('.','/')+"IngredientWithSize;"));
        if(nativeItems==null&&items.isEmpty())items=fields(recipe,f -> f.descriptor().equals("Lnet/minecraft/world/item/crafting/Ingredient;"));
        for (Object input : items) {
            if(input instanceof Ingredient plain){addIngredient(result,plain,1);continue;}
            Object ingredient = call(input,"getBaseIngredient");
            if (ingredient instanceof Ingredient itemsIngredient) addIngredient(result,itemsIngredient,field(recipe,"creosoteOutput") instanceof Number?1:number(call(input,"getCount"),1));
        }
        for (Object input : list(call(recipe,"getFluidInputs"))) {
            long count = number(value(input,"amount","getAmount"),0);
            List<ResourceKey> alternatives = fluidRepresentations(input).stream().filter(stack -> !stack.isEmpty()).map(stack -> (ResourceKey)new FluidKey(stack)).distinct().toList();
            if (!alternatives.isEmpty() && count > 0) result.add(new CraftRecipe.Ingredient<>(alternatives,count));
        }
        return List.copyOf(result);
    }
    private static void addIngredient(List<CraftRecipe.Ingredient<ResourceKey>> result, Ingredient ingredient,long count) {
        List<ResourceKey> keys = Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty()).map(stack -> (ResourceKey)new ItemKey(stack)).distinct().toList();
        if (!keys.isEmpty() && count > 0) result.add(new CraftRecipe.Ingredient<>(keys,count));
    }
    private static void add(Map<ResourceKey,Long> result,Object value) {
        if(value instanceof Supplier<?> supplier){add(result,supplier.get());return;}
        if (value instanceof ItemStack stack && !stack.isEmpty()) result.merge(new ItemKey(stack),(long)stack.getCount(),Math::addExact);
        else if (value instanceof FluidStack stack && !stack.isEmpty()) result.merge(new FluidKey(stack),(long)stack.getAmount(),Math::addExact);
        else if (value instanceof Iterable<?> values) values.forEach(item -> add(result,item));
        else if (value != null && value.getClass().getName().startsWith(API+"TagOutput")) add(result,call(value,"get"));
    }
    private static Map<ResourceKey,Long> outputs(Object recipe,boolean possible) {
        Map<ResourceKey,Long> result = new LinkedHashMap<>();
        Object standard = call(recipe,"getItemOutputs");
        if (standard != null) add(result,standard); else {
            add(result,field(recipe,"output"));add(result,field(recipe,"slag"));
        }
        add(result,call(recipe,"getFluidOutputs"));
        Object slag = field(recipe,"slag"); if (standard != null) add(result,slag);
        for (Object secondary : list(field(recipe,"secondaryOutputs"))) {
            Object chance = call(secondary,"chance");
            if (chance instanceof Number number && number.doubleValue() > 0 && (possible || number.doubleValue() >= 1)) add(result,call(secondary,"stack"));
        }
        // Coke ovens expose the by-product amount on their recipe and the native fluid through IE's registry.
        Object creosote = field(recipe,"creosoteOutput");
        if (creosote instanceof Number amount && amount.intValue() > 0) {
            Class<?> registry = optional("blusunrize.immersiveengineering.common.register.IEFluids");
            Object fluid = registry == null ? null : unwrap(call(staticField(registry,"CREOSOTE"),"getStill"));
            if (fluid instanceof net.minecraft.world.level.material.Fluid nativeFluid) add(result,new FluidStack(nativeFluid,amount.intValue()));
        }
        return Map.copyOf(result);
    }
    private static Map<ResourceKey,Long> sawmillOutputs(Object recipe,boolean blade) {
        Map<ResourceKey,Long> result = new LinkedHashMap<>();
        add(result,field(recipe,blade ? "output" : "stripped")); add(result,field(recipe,"secondaryStripping"));
        if (blade) add(result,field(recipe,"secondaryOutputs")); return Map.copyOf(result);
    }
    private static List<FluidStack> fluidRepresentations(Object ingredient){return list(value(ingredient,"getFluids","getMatchingFluidStacks")).stream().filter(FluidStack.class::isInstance).map(FluidStack.class::cast).toList();}
    private static FluidStack fluidSample(FluidKey key,long amount){return (FluidStack)MekanismResourceSupport.sample(key,amount);}
    private static Object capability(Object reference){return reference instanceof Supplier<?>?unwrap(reference):value(reference,"getNullable","getValue");}
    private static BlockPos capabilityPosition(Object reference){
        Object direct=call(reference,"getCapabilityPos");if(direct instanceof BlockPos pos)return pos;
        Object supplier=field(reference,"getPos"),directional=supplier instanceof Supplier<?>?unwrap(supplier):null;
        Object position=call(directional,"position");return position instanceof BlockPos pos?pos:null;
    }

    private static class Multiblock implements Machine {
        final CraftingService.NetworkAccess access; final ServerLevel level; final Object helper,state,context,registration,mbLevel; final Set<Class<?>> recipes;
        final List<Object> processors; final List<GlobalPos> positions; final BlockPos master;
        Multiblock(CraftingService.NetworkAccess access,ServerLevel level,Object helper,Object state,Object context,Object registration,Set<Class<?>> recipes) {
            this.access=access;this.level=level;this.helper=helper;this.state=state;this.context=context;this.registration=registration;this.recipes=recipes;
            mbLevel=call(context,"getLevel"); master=(BlockPos)call(mbLevel,"toAbsolute",call(registration,"masterPosInMB"));
            processors=fields(state,f -> f.descriptor().contains("/MultiblockProcessor") || f.descriptor().contains("/FurnaceHandler;"));
            LinkedHashSet<GlobalPos> aliases = new LinkedHashSet<>(); aliases.add(GlobalPos.of(level.dimension(),master));
            Object structure = call(registration,"getStructure");
            if (structure instanceof java.util.function.Function<?,?> function) {
                @SuppressWarnings("unchecked") var getter=(java.util.function.Function<Object,Object>)function;
                for (Object info : list(getter.apply(level))) {
                    Object relative=call(info,"pos"); if (relative instanceof BlockPos pos) aliases.add(GlobalPos.of(level.dimension(),(BlockPos)call(mbLevel,"toAbsolute",pos)));
                }
            }
            for (Object output : drops()) {
                Object face=field(output,"relativeDropPos"); Object relative=call(face,"posInMultiblock");
                if (relative instanceof BlockPos pos) aliases.add(GlobalPos.of(level.dimension(),(BlockPos)call(mbLevel,"toAbsolute",pos)));
            }
            for(Object supplier:capabilityOutputs()){
                Object destination=capabilityPosition(supplier);
                if(destination instanceof BlockPos pos)aliases.add(GlobalPos.of(level.dimension(),pos));
            }
            positions=List.copyOf(aliases);
        }
        List<Object> drops() { return fields(state,f -> f.descriptor().endsWith("/DroppingMultiblockOutput;")); }
        List<Object> capabilityOutputs(){return fields(state,f -> (f.descriptor().equals("Ljava/util/function/Supplier;")||f.descriptor().endsWith("/CapabilityReference;")) && (f.signature().contains("IItemHandler")||f.signature().contains("IFluidHandler")));}
        public Object identity(){return state;}
        public Object instance(){return state;}
        public boolean valid(){ return level.hasChunkAt(master) && call(helper(level.getBlockEntity(master)),"getState")==state; }
        public List<GlobalPos> claimedPositions(){return positions;}
        public boolean accepts(Recipe recipe){return recipe.nativeRecipe() instanceof Bound bound && recipes.contains(bound.recipeClass);}
        public Object catalogState(){
            List<Object> setup=new ArrayList<>();
            for (Object value : fields(state,f -> f.descriptor().equals("Lnet/minecraft/world/item/ItemStack;"))) if (value instanceof ItemStack stack) setup.add(stack.isEmpty()?"":List.of(new ItemKey(stack),stack.getCount()));
            Object selected=field(state,"selectedRecipe");if(selected!=null)setup.add(selected);
            return List.of(registration,setup);
        }
        public boolean ready(Recipe recipe,List<CraftPlan.Selection<ResourceKey>> selected) {
            if (!accepts(recipe) || !valid() || !queueEmpty()) return false;
            Bound bound=(Bound)recipe.nativeRecipe();
            if(Boolean.FALSE.equals(call(state,"hasElectrodes")))return false;
            for(Object redstone:fields(state,f -> f.descriptor().endsWith("/RedstoneControl$RSState;")))if(Boolean.FALSE.equals(call(redstone,"isEnabled",context)))return false;
            if(field(state,"selectedRecipe") instanceof Number selectedRecipe){
                List<?> choices=list(staticCall(call(registration,"logic").getClass(),"getAvailableRecipes",level,state));
                int index=selectedRecipe.intValue();
                if(index<0||index>=choices.size())return false;
                NativeRecipeSource.Entry selectedHolder=NativeRecipeSource.entry(choices.get(index));if(selectedHolder==null||selectedHolder.value()!=bound.recipe)return false;
            }
            Object mold=field(bound.recipe,"mold");
            if (mold instanceof Item item && (!(field(state,"mold") instanceof ItemStack installed) || !installed.is(item))) return false;
            Object catalyst=field(bound.recipe,"catalyst");
            if (catalyst instanceof Ingredient ingredient && !ingredient.isEmpty()) {
                Object inventory=inventory(); if (!(inventory instanceof IItemHandler handler)) return false;
                boolean found=false; for(int slot=0;slot<handler.getSlots();slot++) if(ingredient.test(handler.getStackInSlot(slot))) found=true;
                if(!found)return false;
            }
            if (!bound.variant.equals("native")) {
                boolean blade=field(state,"sawblade") instanceof ItemStack stack && !stack.isEmpty();
                if (blade != bound.variant.equals("blade")) return false;
            }
            for(Object furnace:processors) if(field(furnace,"fuelSlot") instanceof Number slot && number(field(furnace,"burnTime"),0)<=0) {
                Object inventory=inventory();
                if(!(inventory instanceof IItemHandler handler))return false;
                if(number(call(state,"getBurnTimeOf",level,handler.getStackInSlot(slot.intValue())),0)<=0 && fuel(access,handler.getStackInSlot(slot.intValue()))==null)return false;
            }
            // In-world output must have a real receiving inventory; old ground drops are never collected.
            for(Object output:drops()) if(!(capability(field(output,"output")) instanceof IItemHandler))return false;
            return true;
        }
        boolean queueEmpty(){
            for(Object processor:processors) if(number(call(processor,"getQueueSize"),0)>0)return false;
            for(Object queue:fields(state,f -> f.signature().contains("Process"))) if(queue instanceof Collection<?> collection && !collection.isEmpty())return false;
            return true;
        }
        Object inventory(){
            Object inventory=call(state,"getInventory");
            if(inventory instanceof IItemHandler handler && handler.getSlots()>0)return inventory;
            return fields(state,f -> f.descriptor().endsWith("/SlotwiseItemHandler;")).stream().findFirst().orElse(inventory);
        }
        public boolean completed(Recipe recipe,int polls){return polls>0 && queueEmpty();}
        ItemKey fuel(CraftingService.NetworkAccess network,ItemStack current){
            return network.resourceSnapshot().entrySet().stream().filter(entry -> entry.getValue()>0 && entry.getKey() instanceof ItemKey)
                    .map(entry -> (ItemKey)entry.getKey()).filter(network::mayUseFuel).filter(key -> current.isEmpty()||new ItemKey(current).equals(key))
                    .filter(key -> number(call(state,"getBurnTimeOf",level,key.sample()),0)>0)
                    .min(Comparator.comparingLong(key -> number(call(state,"getBurnTimeOf",level,key.sample()),Long.MAX_VALUE))).orElse(null);
        }
        public List<Supplemental> supplemental(CraftingService.NetworkAccess network,Recipe recipe,List<CraftPlan.Selection<ResourceKey>> selected){
            List<Supplemental> requests=new ArrayList<>();Object inventory=inventory();if(!(inventory instanceof IItemHandler handler))return List.of();
            for(Object furnace:processors)if(field(furnace,"fuelSlot") instanceof Number index){
                int slot=index.intValue();ItemStack current=handler.getStackInSlot(slot);long burn=Math.max(0,number(field(furnace,"burnTime"),0));
                long remaining=number(field(furnace,"process"),0);if(remaining<=0)remaining=recipe.durationTicks();
                long currentBurn=number(call(state,"getBurnTimeOf",level,current),0);
                long needed=remaining-burn-Math.multiplyExact(currentBurn,current.getCount());if(needed<=0)continue;
                ItemKey fuel=fuel(network,current);if(fuel==null)continue;
                long each=number(call(state,"getBurnTimeOf",level,fuel.sample()),0);if(each<=0)continue;
                long count=Math.min((needed+each-1)/each,Math.min(handler.getSlotLimit(slot)-current.getCount(),fuel.sample().getMaxStackSize()));
                if(count>0)requests.add(new Supplemental(NativeMachinePorts.itemSlot(handler,slot,true,false),fuel,count));
            }
            return List.copyOf(requests);
        }
        public List<Port> ports(Recipe recipe){
            List<Port> result=new ArrayList<>(); Set<Object> seen=new HashSet<>();
            Set<Integer> outputSlots=new HashSet<>(); Object slotIndices=call(state,"getOutputSlots"); if(slotIndices instanceof int[] indices)for(int slot:indices)outputSlots.add(slot);
            Set<Integer> protectedSlots=new HashSet<>();
            for(Object processor:processors) {
                if(field(processor,"fuelSlot") instanceof Number slot)protectedSlots.add(slot.intValue());
                for(Object output:list(field(processor,"outputs")))outputSlots.add((int)number(field(output,"slotIndex"),-1));
            }
            List<Object> handlers=fields(state,f -> f.descriptor().endsWith("/IItemHandler;") || f.descriptor().endsWith("/SlotwiseItemHandler;") || f.descriptor().endsWith("/StoredCapability;")&&f.signature().contains("IItemHandler"))
                    .stream().map(handler -> {Object stored=call(handler,"getValue");return stored==null?handler:stored;}).toList();
            Object inventory=inventory(); if(handlers.isEmpty() && inventory instanceof IItemHandler)handlers=List.of(inventory);
            for(Object candidate:handlers) if(candidate instanceof IItemHandler handler){
                IItemHandler owner=rawItems(handler);
                List<?> constraints=list(field(candidate,"slotConstraints"));
                for(int slot=0;slot<handler.getSlots();slot++){
                    if(protectedSlots.contains(slot))continue;
                    boolean out=outputSlots.contains(slot);
                    if(Boolean.TRUE.equals(call(candidate,"allowExtract")) && Boolean.FALSE.equals(call(candidate,"allowInsert"))){
                        for(Object range:list(call(candidate,"allowedRanges")))if(slot>=number(call(range,"first"),-1)&&slot<number(call(range,"firstAfter"),-1))out=true;
                    }
                    if(slot<constraints.size() && constraints.get(slot)==staticField(constraints.get(slot).getClass(),"OUTPUT"))out=true;
                    final int index=slot; boolean in=!out && recipe.inputs().stream().anyMatch(input -> input.alternatives().stream().filter(ItemKey.class::isInstance).map(ItemKey.class::cast)
                            .anyMatch(key -> acceptsItem(handler,index,key,input.count(),recipe)));
                    if(!in&&!out)continue;
                    Object identity=NativeMachinePorts.identity(owner,slot,"item"); if(!seen.add(identity))continue;
                    Port nativeInsert=NativeMachinePorts.itemSlot(handler,slot,in,out), nativeExtract=NativeMachinePorts.itemSlot(owner,slot,in,out);
                    result.add(NativeMachinePorts.callbacks(identity,in,out,nativeInsert::snapshot,(key,amount,simulate)->{
                        long accepted=nativeInsert.insert(key,amount,simulate);
                        if(simulate&&accepted==0&&key instanceof ItemKey item&&canStageFluidRecipe(handler,item,amount,recipe))return amount;
                        return accepted;
                    },nativeExtract::extract));
                }
            }
            Object tanks=call(state,"getInternalTanks"); Set<Integer> outputTanks=new HashSet<>(); Object tankIndices=call(state,"getOutputTanks");
            if(tankIndices instanceof int[] indices)for(int tank:indices)outputTanks.add(tank);
            if(tanks instanceof Object[] nativeTanks)for(int tank=0;tank<nativeTanks.length;tank++) {
                if(nativeTanks[tank]==null)continue;
                boolean out=outputTanks.contains(tank); result.add(NativeMachinePorts.fluidTank(nativeTanks[tank],!out,out)); seen.add(nativeTanks[tank]);
            }
            for(Object tank:fields(state,f -> f.descriptor().endsWith("/FluidTank;")))if(NativeBackendReflection.has(tank,"getFluid") && seen.add(tank)) {
                boolean out=recipe.possible().keySet().stream().anyMatch(FluidKey.class::isInstance);
                boolean in=recipe.inputs().stream().flatMap(input -> input.alternatives().stream()).anyMatch(FluidKey.class::isInstance);
                result.add(NativeMachinePorts.fluidTank(tank,in,out));
            }
            for(Object tank:fields(state,f -> f.descriptor().endsWith("/MultiFluidTank;")))if(seen.add(tank)) {
                boolean in=recipe.inputs().stream().flatMap(input -> input.alternatives().stream()).anyMatch(FluidKey.class::isInstance);
                boolean out=recipe.possible().keySet().stream().anyMatch(FluidKey.class::isInstance);
                result.add(NativeMachinePorts.callbacks(NativeMachinePorts.identity(tank,0,"multi-fluid"),in,out,() -> {
                    Map<ResourceKey,Long> contents=new LinkedHashMap<>(); add(contents,field(tank,"fluids"));return contents;
                },(key,amount,simulate) -> key instanceof FluidKey fluid ? number(call(tank,"fill",fluidSample(fluid,Math.min(amount,Integer.MAX_VALUE)),simulate?com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction.SIMULATE:com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction.EXECUTE),0):0,
                (key,amount,simulate) -> {
                    if(!(key instanceof FluidKey fluid))return 0;
                    Object actual=call(tank,"drain",fluidSample(fluid,Math.min(amount,Integer.MAX_VALUE)),simulate?com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction.SIMULATE:com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    if(actual instanceof FluidStack stack && !stack.isEmpty()){
                        if(!new FluidKey(stack).equals(key))throw new IllegalStateException("IE native multi-fluid tank returned a different fluid");return stack.getAmount();
                    }
                    return 0;
                }));
            }
            for(Object drop:drops())if(capability(field(drop,"output")) instanceof IItemHandler handler)
                for(int slot=0;slot<handler.getSlots();slot++) { Object identity=NativeMachinePorts.identity(handler,slot,"item"); if(seen.add(identity))result.add(NativeMachinePorts.itemSlot(handler,slot,false,true)); }
            for(Object output:capabilityOutputs()){
                Object destination=capability(output);
                if(destination instanceof IItemHandler handler)for(int slot=0;slot<handler.getSlots();slot++){
                    Object identity=NativeMachinePorts.identity(handler,slot,"item");if(seen.add(identity))result.add(NativeMachinePorts.itemSlot(handler,slot,false,true));
                }
                if(destination instanceof IFluidHandler handler){
                    Object identity=NativeMachinePorts.identity(handler,0,"destination-fluid");if(!seen.add(identity))continue;
                    result.add(NativeMachinePorts.callbacks(identity,false,true,() -> {
                        Map<ResourceKey,Long> contents=new LinkedHashMap<>();for(int tank=0;tank<handler.getTanks();tank++)add(contents,handler.getFluidInTank(tank));return contents;
                    },(key,amount,simulate)->0,(key,amount,simulate)->{
                        if(!(key instanceof FluidKey fluid))return 0;
                        FluidStack actual=handler.drain(fluidSample(fluid,Math.min(amount,Integer.MAX_VALUE)),simulate?IFluidHandler.FluidAction.SIMULATE:IFluidHandler.FluidAction.EXECUTE);
                        if(actual.isEmpty())return 0;
                        if(!new FluidKey(actual).equals(key))throw new IllegalStateException("IE output destination returned another fluid");return actual.getAmount();
                    }));
                }
            }
            return List.copyOf(result);
        }
        static IItemHandler rawItems(IItemHandler handler){
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());IItemHandler current=handler;
            while(seen.add(current)){
                Object next=call(current,"getRawHandler");if(!(next instanceof IItemHandler))next=call(current,"wrapped");
                if(!(next instanceof IItemHandler raw))break;current=raw;
            }
            return current;
        }
        boolean acceptsItem(IItemHandler handler,int slot,ItemKey key,long count,Recipe recipe){
            if(count<1||count>key.sample().getMaxStackSize())return false;
            ItemStack offered=key.sample().copyWithCount((int)count);
            return handler.insertItem(slot,offered,true).getCount()<count||canStageFluidRecipe(handler,key,count,recipe);
        }
        boolean canStageFluidRecipe(IItemHandler handler,ItemKey key,long amount,Recipe recipe){
            Object nativeInventory=call(state,"getInventory");
            if(!(recipe.nativeRecipe() instanceof Bound bound)||nativeInventory instanceof IItemHandler inventory&&inventory.getSlots()!=0
                    ||!contract(state,"blusunrize.immersiveengineering.common.blocks.multiblocks.process.ProcessContext$ProcessContextInWorld")
                    ||handler.getSlots()!=1||amount>key.sample().getMaxStackSize())return false;
            for(var input:recipe.inputs())for(ResourceKey alternative:input.alternatives())if(alternative instanceof FluidKey fluid){
                Object selected=staticCall(bound.recipe.getClass(),"findRecipe",level,fluidSample(fluid,input.count()),new ItemStack[]{key.sample().copyWithCount((int)amount)});
                NativeRecipeSource.Entry holder=NativeRecipeSource.entry(selected);if(holder!=null&&holder.value()==bound.recipe)return true;
            }
            return false;
        }
    }

    /** Configured native pattern queries also cover vanilla and other mods' registered crafting recipes. */
    private static final class AssemblyMachine extends Multiblock{
        final String initialConfiguration;
        AssemblyMachine(CraftingService.NetworkAccess access,ServerLevel level,Object helper,Object state,Object context,Object registration){
            super(access,level,helper,state,context,registration,Set.of());initialConfiguration=configuration();
        }
        Object[] patterns(){return (Object[])field(state,"patterns");}
        String configuration(){
            List<Object> data=new ArrayList<>();for(Object pattern:patterns()){
                List<Object> grid=new ArrayList<>();for(Object value:list(field(pattern,"inv")))if(value instanceof ItemStack stack)grid.add(stack.isEmpty()?"":List.of(new ItemKey(stack),stack.getCount()));data.add(grid);
            }
            return NativeMachineAdapter.profile(level,data);
        }
        public boolean valid(){return super.valid()&&configuration().equals(initialConfiguration);}
        public Object catalogState(){return List.of(super.catalogState(),configuration());}
        public boolean accepts(Recipe recipe){return recipe.nativeRecipe() instanceof Assembly assembly&&assembly.configuration.equals(configuration())&&assembly.index<patterns().length&&patterns()[assembly.index]==assembly.pattern&&field(assembly.pattern,"recipe")==assembly.recipe;}
        List<Recipe> recipes(CraftingService.NetworkAccess access,List<Classification> audit){
            List<Recipe> result=new ArrayList<>();Object[] patterns=patterns();
            for(int patternIndex=0;patternIndex<patterns.length;patternIndex++){
                Object pattern=patterns[patternIndex],nativeRecipe=field(pattern,"recipe");if(nativeRecipe==null)continue;
                NativeRecipeSource.Entry holder=NativeRecipeSource.entries(level).stream().filter(value -> value.value()==nativeRecipe).findFirst().orElse(null);if(holder==null)continue;
                try{
                    List<?> queries=list(call(pattern,"getQueries",level));if(queries.isEmpty())continue;
                    List<?> original=list(field(pattern,"inv"));List<Integer> occupied=new ArrayList<>();for(int slot=0;slot<Math.min(9,original.size());slot++)if(original.get(slot) instanceof ItemStack stack&&!stack.isEmpty())occupied.add(slot);
                    List<List<QueryChoice>> choices=new ArrayList<>();
                    for(int index=0;index<queries.size();index++)choices.add(queryChoices(queries.get(index),access,index<occupied.size()?(ItemStack)original.get(occupied.get(index)):ItemStack.EMPTY));
                    List<List<QueryChoice>> combinations=new ArrayList<>();queryCombinations(choices,0,new ArrayList<>(),combinations);
                    for(List<QueryChoice> selected:combinations){
                        List<ItemStack> grid=new ArrayList<>();for(int slot=0;slot<9;slot++)grid.add(((ItemStack)original.get(slot)).copy());
                        Set<Integer> fluidSlots=new HashSet<>();
                        for(int index=0;index<selected.size()&&index<occupied.size();index++){
                            int slot=occupied.get(index);QueryChoice choice=selected.get(index);
                            if(choice.fluid)fluidSlots.add(slot);else if(choice.ingredient.alternatives().getFirst() instanceof ItemKey item)grid.set(slot,item.sample().copyWithCount((int)choice.ingredient.count()));
                        }
                        Map<ResourceKey,Long> outputs=new LinkedHashMap<>();add(outputs,original.get(9));
                        List<ItemStack> remainders=NativeRecipeSource.remainingItems(nativeRecipe,NativeRecipeSource.craftingInput(grid));for(int slot=0;slot<remainders.size();slot++)if(!fluidSlots.contains(slot))add(outputs,remainders.get(slot));
                        if(outputs.isEmpty())continue;
                        List<CraftRecipe.Ingredient<ResourceKey>> inputs=selected.stream().map(QueryChoice::ingredient).toList();List<Boolean> fluidInputs=selected.stream().map(QueryChoice::fluid).toList();
                        String profile=NativeMachineAdapter.profile(level,List.of(configuration(),patternIndex,fluidInputs,inputs.stream().map(input -> List.of(input.alternatives(),input.count())).toList(),outputs));
                        result.add(new Recipe("immersiveengineering:configured_assembly/"+profile+"/"+holder.id(),"immersiveengineering/configured_assembly/"+configuration()+"/"+patternIndex,inputs,outputs,outputs,16,7200,new Assembly(pattern,nativeRecipe,patternIndex,configuration(),fluidInputs),false));
                    }
                    audit.add(new Classification(holder.id().toString(),"minecraft:crafting",pattern.getClass().getName(),"processing","Configured native RecipeQuery counted item/fluid contracts; ghost patterns remain protected"));
                }catch(RuntimeException failure){audit.add(new Classification(holder.id().toString(),"minecraft:crafting",pattern.getClass().getName(),"unsupported-contract",failure.getMessage()));}
            }
            return List.copyOf(result);
        }
        static List<QueryChoice> queryChoices(Object query,CraftingService.NetworkAccess access,ItemStack ghost){
            boolean fluid=Boolean.TRUE.equals(call(query,"isFluid"));long itemCount=number(call(query,"getItemCount"),1),fluidCount=fluid?number(call(query,"getFluidSize"),0):0;
            Set<ResourceKey> candidates=new LinkedHashSet<>(access.resourceSnapshot().keySet());if(!ghost.isEmpty())candidates.add(new ItemKey(ghost));
            for(Object nativeInput:fields(query,field -> field.descriptor().endsWith("/Ingredient;")||field.descriptor().endsWith("/ItemStack;")||field.descriptor().endsWith("/SizedFluidIngredient;")||field.descriptor().endsWith("/FluidTagInput;")||field.descriptor().endsWith("/FluidStack;"))){
                if(nativeInput instanceof Ingredient ingredient){for(ItemStack stack:ingredient.getItems())if(!stack.isEmpty())candidates.add(new ItemKey(stack));}
                else if(nativeInput instanceof ItemStack stack&&!stack.isEmpty()){candidates.add(new ItemKey(stack));}
                else if(nativeInput instanceof FluidStack stack&&!stack.isEmpty()){candidates.add(new FluidKey(stack));}
                else{for(FluidStack stack:fluidRepresentations(nativeInput))if(!stack.isEmpty())candidates.add(new FluidKey(stack));}
            }
            Map<Object,List<ResourceKey>> groups=new LinkedHashMap<>();
            for(ResourceKey key:candidates){
                if(key instanceof ItemKey item&&Boolean.TRUE.equals(call(query,"matchesIgnoringSize",item.sample()))){
                    Object remaining=call(item.sample(),"getCraftingRemainingItem");ItemStack remainder=remaining instanceof ItemStack stack?stack:ItemStack.EMPTY;
                    Object group=List.of("item",itemCount,remainder.isEmpty()?"":new ItemKey(remainder));groups.computeIfAbsent(group,ignored -> new ArrayList<>()).add(key);
                }else if(fluid&&key instanceof FluidKey nativeFluid&&Boolean.TRUE.equals(call(query,"matchesFluid",fluidSample(nativeFluid,fluidCount))))groups.computeIfAbsent(List.of("fluid",fluidCount),ignored -> new ArrayList<>()).add(key);
            }
            List<QueryChoice> result=new ArrayList<>();for(var group:groups.entrySet()){
                List<?> description=(List<?>)group.getKey();boolean nativeFluid=description.getFirst().equals("fluid");result.add(new QueryChoice(new CraftRecipe.Ingredient<>(group.getValue(),nativeFluid?fluidCount:itemCount),nativeFluid));
            }
            if(result.isEmpty())throw new IllegalArgumentException("Native assembler query has no registered or current-stock representations: "+query.getClass().getName());return List.copyOf(result);
        }
        static void queryCombinations(List<List<QueryChoice>> choices,int index,List<QueryChoice> current,List<List<QueryChoice>> result){
            if(index==choices.size()){result.add(List.copyOf(current));return;}for(QueryChoice choice:choices.get(index)){current.add(choice);queryCombinations(choices,index+1,current,result);current.removeLast();}
        }
        public boolean ready(Recipe recipe,List<CraftPlan.Selection<ResourceKey>> selected){
            if(!accepts(recipe)||!valid())return false;
            for(Object redstone:fields(state,field -> field.descriptor().endsWith("/RedstoneControl$RSState;")))if(Boolean.FALSE.equals(call(redstone,"isEnabled",context)))return false;
            Assembly assembly=(Assembly)recipe.nativeRecipe();
            Map<ResourceKey,Long> supplied=new LinkedHashMap<>();for(var selection:selected)supplied.merge(selection.key(),selection.count(),Long::sum);
            for(int index=0;index<patterns().length;index++)if(index!=assembly.index&&field(patterns()[index],"recipe")!=null){
                if(index<assembly.index&&canRun(patterns()[index],supplied))return false;
                if(index>assembly.index&&canRun(patterns()[index],recipe.possible()))return false;
            }
            return true;
        }
        boolean canRun(Object pattern,Map<ResourceKey,Long> stock){
            Map<ResourceKey,Long> available=new LinkedHashMap<>(stock);
            for(Object query:list(call(pattern,"getQueries",level))){
                boolean fluid=Boolean.TRUE.equals(call(query,"isFluid")),matched=false;
                if(fluid){long amount=number(call(query,"getFluidSize"),0);for(var entry:available.entrySet())if(entry.getKey() instanceof FluidKey key&&entry.getValue()>=amount&&Boolean.TRUE.equals(call(query,"matchesFluid",fluidSample(key,amount)))){entry.setValue(entry.getValue()-amount);matched=true;break;}}
                if(matched)continue;long remaining=number(call(query,"getItemCount"),1);
                for(var entry:available.entrySet())if(entry.getKey() instanceof ItemKey key&&Boolean.TRUE.equals(call(query,"matchesIgnoringSize",key.sample()))){long used=Math.min(remaining,entry.getValue());remaining-=used;entry.setValue(entry.getValue()-used);}
                if(remaining>0)return false;
            }
            return true;
        }
        public List<Port> ports(Recipe recipe){
            List<Port> result=new ArrayList<>();Object inventory=inventory();if(!(inventory instanceof IItemHandler handler))return List.of();
            IItemHandler raw=rawItems(handler);int outputStart=handler.getSlots()-patterns().length;
            for(int slot=0;slot<handler.getSlots();slot++)result.add(NativeMachinePorts.itemSlot(raw,slot,slot<outputStart,true));
            if(field(state,"tanks") instanceof Object[] tanks)for(Object tank:tanks)result.add(NativeMachinePorts.fluidTank(tank,true,false));
            for(Object output:capabilityOutputs())if(capability(output) instanceof IItemHandler destination)for(int slot=0;slot<destination.getSlots();slot++)result.add(NativeMachinePorts.itemSlot(destination,slot,false,true));
            return List.copyOf(result);
        }
    }
}
