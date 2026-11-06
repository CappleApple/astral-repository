package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;
import com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler.FluidAction;
import com.cappleapple.astralrepository.platform.items.IItemHandler;
import static com.cappleapple.astralrepository.crafting.NativeContractAccess.*;

/** Ender IO's shared task hosts, native inventory layouts and counted recipe definitions. */
public final class EnderIoBackend implements NativeMachineBackend {
    private record Bound(Object recipe, Object type, Object configuration) {}
    private record SetupConfiguration(List<Object> setup) {}
    private record ConfiguredCraft(Object recipe, Object template, String profile) {}
    private record StoredType(Object type,Class<?> recipeClass) {}
    private record TankBound(Object recipe,Object type,boolean filling,FluidKey product) {}
    private static List<StoredType> storedTypes;
    private static final Map<Class<?>,Optional<StoredType>> CACHED_FLUID_TYPES=new HashMap<>();
    public record Classification(String id,String contract,String classification,String reason) {}
    public record MachineContract(String registration,String blockEntity,String classification,List<String> nativeReferences) {}
    private List<Classification> audit=List.of();
    public List<Classification> audit(){return audit;}
    public List<MachineContract> machineInventory(){
        Class<?> registry=optional("com.enderio.enderio.init.EIOBlockEntities");if(registry==null)registry=optional("com.enderio.machines.common.init.MachineBlockEntities");if(registry==null)return List.of();
        List<MachineContract> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(var field:NativeBytecode.inspect(registry).fields()){
            var matcher=java.util.regex.Pattern.compile("(?:BlockEntityType|BlockEntityEntry)<L(com/enderio/[^;]+);").matcher(field.signature());
            while(matcher.find()){
                String name=matcher.group(1).replace('/','.');if(!seen.add(name))continue;Class<?> type=optional(name);if(type==null)continue;
                boolean task=hierarchy(type).stream().flatMap(current -> NativeBytecode.inspect(current).fields().stream()).anyMatch(value -> value.descriptor().contains("MachineTaskHost"));
                List<String> references=NativeBytecode.hierarchy(type).stream().flatMap(shape -> shape.references().stream()).map(NativeBytecode.Reference::owner).filter(owner -> owner.startsWith("com.enderio.")&&!owner.contains(".client.")).distinct().sorted().toList();
                boolean crafting=NativeBytecode.hierarchy(type).stream().flatMap(shape -> shape.references().stream()).anyMatch(reference -> reference.owner().equals("net.minecraft.world.item.crafting.RecipeType")&&reference.name().equals("CRAFTING"));
                result.add(new MachineContract(field.name(),name,task?"native-task-host":crafting?"native-configured-crafting":cachedFluidType(type)!=null?"native-cached-fluid-processing":"native-world-storage-or-transport",references));
            }
        }
        return List.copyOf(result);
    }
    public String id(){return "enderio";}
    private static List<Object> hosts(Object entity){return fields(entity,f -> f.descriptor().endsWith("MachineTaskHost;") || f.descriptor().contains("CraftingMachineTaskHost"));}
    private static List<StoredType> storedTypes(){
        if(storedTypes!=null)return storedTypes;
        Class<?> registry=optional("com.enderio.enderio.init.EIORecipes");if(registry==null)registry=optional("com.enderio.machines.common.init.MachineRecipes");if(registry==null)return List.of();List<StoredType> result=new ArrayList<>();
        for(var field:NativeBytecode.inspect(registry).fields()){
            var matcher=java.util.regex.Pattern.compile("RecipeTypeSerializerPair<L([^;]+);").matcher(field.signature());if(!matcher.find())continue;
            Class<?> recipeClass=optional(matcher.group(1).replace('/','.'));Object type=call(call(staticField(registry,field.name()),"type"),"get");
            if(recipeClass!=null&&type!=null)result.add(new StoredType(type,recipeClass));
        }
        storedTypes=List.copyOf(result);return storedTypes;
    }
    private static StoredType cachedFluidType(Object entity){return cachedFluidType(entity.getClass());}
    private static StoredType cachedFluidType(Class<?> entityType){return CACHED_FLUID_TYPES.computeIfAbsent(entityType,type -> Optional.ofNullable(findCachedFluidType(type))).orElse(null);}
    private static StoredType findCachedFluidType(Class<?> entityType){
        for(StoredType type:storedTypes()){
            var shape=NativeBytecode.inspect(type.recipeClass);
            boolean fluid=shape.fields().stream().anyMatch(field -> field.descriptor().endsWith("/SizedFluidIngredient;")||field.descriptor().endsWith("/FluidStack;"));
            boolean mode=shape.fields().stream().anyMatch(field -> field.name().equals("mode")||field.name().equals("isEmptying"));
            if(!fluid||!mode)continue;
            String name=type.recipeClass.getName().replace('.','/');
            boolean cache=hierarchy(entityType).stream().flatMap(clazz -> NativeBytecode.inspect(clazz).fields().stream()).anyMatch(field -> field.signature().contains(name));
            boolean tick=hierarchy(entityType).stream().flatMap(clazz -> NativeBytecode.inspect(clazz).methods().stream()).anyMatch(method -> method.name().equals("serverTick"));
            if(cache&&tick)return type;
        }
        return null;
    }
    public Object identityAt(ServerLevel level,BlockPos position){
        BlockEntity entity=level.getBlockEntity(position);
        return entity!=null && entity.getClass().getName().startsWith("com.enderio.") && (!hosts(entity).isEmpty()||configuredCrafter(entity)||cachedFluidType(entity)!=null)?entity:null;
    }
    public Machine machine(CraftingService.NetworkAccess access,GlobalPos position){
        ServerLevel level=access.level().getServer().getLevel(position.dimension());
        if(level==null||!level.hasChunkAt(position.pos()))return null;
        BlockEntity entity=level.getBlockEntity(position.pos());
        if(entity==null||!entity.getClass().getName().startsWith("com.enderio."))return null;
        List<Object> hosts=hosts(entity).stream().filter(host -> field(host,"recipeType") instanceof RecipeType<?>).toList();
        if(!hosts.isEmpty())return new TaskMachine(level,entity,hosts);
        if(configuredCrafter(entity))return new ConfiguredCrafter(level,entity);
        StoredType cachedFluid=cachedFluidType(entity);return cachedFluid==null?null:new CachedFluidMachine(level,entity,cachedFluid);
    }
    private static boolean configuredCrafter(Object entity){
        NativeRecipeSource.Entry holder=NativeRecipeSource.entry(field(entity,"recipe"));
        return holder!=null&&NativeRecipeSource.typeKey(holder.value()).toString().equals("minecraft:crafting")&&NativeBytecode.hierarchy(entity.getClass()).stream().flatMap(shape -> shape.methods().stream()).anyMatch(method -> method.name().equals("craftItem"));
    }
    public List<Recipe> recipes(CraftingService.NetworkAccess access,List<Machine> loaded){
        List<Recipe> result=new ArrayList<>(); List<Classification> classified=new ArrayList<>();
        for(NativeRecipeSource.Entry holder:NativeRecipeSource.entries(access.level())){
            Object nativeRecipe=holder.value();
            if(!nativeRecipe.getClass().getName().startsWith("com.enderio."))continue;
            if(!hierarchy(nativeRecipe.getClass()).stream().anyMatch(type -> type.getSimpleName().equals("MachineRecipe")&&type.getName().startsWith("com.enderio."))){
                Object definition=value(nativeRecipe,"fluid","getFluid"),mode=value(nativeRecipe,"mode","isEmptying");
                if(definition!=null&&(mode instanceof Enum<?>||mode instanceof Boolean)&&value(nativeRecipe,"input","getInput") instanceof Ingredient ingredient&&value(nativeRecipe,"output","getOutput") instanceof ItemStack output){
                    try{
                        List<CraftRecipe.Ingredient<ResourceKey>> inputs=new ArrayList<>();addInput(inputs,ingredient,1,access.resourceSnapshot());
                        Object sizedFluid=definition;long amount=sizedFluid instanceof FluidStack fluid?fluid.getAmount():number(value(sizedFluid,"amount","getAmount"),0);boolean filling=mode instanceof Enum<?> nativeMode?nativeMode.name().equals("FILL"):!((Boolean)mode);
                        List<FluidStack> fluids=sizedFluid instanceof FluidStack fluid?List.of(fluid.copy()):list(staticCall(optional("com.enderio.enderio.foundation.util.SizedFluidIngredientHelper"),"getFluidStacksInPreferredOrder",sizedFluid)).stream().filter(FluidStack.class::isInstance).map(FluidStack.class::cast).toList();
                        if(fluids.isEmpty()||amount<1)throw new IllegalArgumentException("Native fluid-container recipe has no fluid representation");
                        Map<ResourceKey,Long> outputs=new LinkedHashMap<>();add(outputs,output);FluidKey product=null;
                        if(filling)inputs.add(new CraftRecipe.Ingredient<>(fluids.stream().map(fluid -> (ResourceKey)new FluidKey(fluid)).distinct().toList(),amount));
                        else{product=new FluidKey(fluids.get(0));outputs.merge(product,amount,Math::addExact);}
                        String direction=filling?"fill":"empty",process="enderio/native_fluid_container/"+direction+(filling?"":"/"+NativeMachineAdapter.profile(access.level(),product));
                        result.add(new Recipe("enderio:native_fluid_container/"+direction+"/"+holder.id(),process,inputs,outputs,outputs,5,7200,new TankBound(nativeRecipe,holder.type(),filling,product),false));
                        classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),"processing","Native cached fluid-container conversion with protected input/output roles"));
                    }catch(RuntimeException failure){classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),"unsupported-contract",failure.getMessage()));}
                    continue;
                }
                classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),"native-condition","Manual, world-event or storage recipe outside native machine task execution"));continue;
            }
            try{
                List<TaskMachine> matched=loaded.stream().filter(TaskMachine.class::isInstance).map(TaskMachine.class::cast).filter(machine -> machine.acceptsType(holder.type())).toList();
                List<CraftRecipe.Ingredient<ResourceKey>> inputs=inputs(nativeRecipe,access.resourceSnapshot());
                if(call(nativeRecipe,"getExpCost") instanceof Number){
                    if(matched.isEmpty()){classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),"awaiting-native-input","Native legacy component binding requires its actual input container and filled soul stock"));continue;}
                    inputs=matched.get(0).completeLegacyInputs(nativeRecipe,inputs,access.resourceSnapshot());
                    if(inputs.isEmpty()){classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),"awaiting-native-input","Native component binding requires matching filled soul stock in its real input roles"));continue;}
                }
                Map<ResourceKey,Long> guaranteed=outputs(nativeRecipe,access,false,null),possible=outputs(nativeRecipe,access,true,null);
                if(inputs.isEmpty()||possible.isEmpty()){
                    classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),"native-condition",possible.isEmpty()?"Native world-state effect has no collectable item/fluid product":"No consumed-input recipe contract"));continue;
                }
                Object dynamicOutputs=value(nativeRecipe,"outputs","getOutputs");
                boolean componentOutput=NativeBytecode.inspect(nativeRecipe.getClass()).methods().stream().filter(method -> method.name().equals("craft"))
                        .flatMap(method -> method.references().stream()).anyMatch(reference -> reference.owner().endsWith("EIODataComponents")||reference.owner().endsWith("EnderIOCapabilities")||reference.owner().endsWith("EIOCapabilities")||reference.owner().equals("net.minecraft.nbt.CompoundTag"));
                boolean variableFluid=call(nativeRecipe,"firstReagent")!=null && call(nativeRecipe,"secondReagent")!=null;
                if(componentOutput||variableFluid){
                    List<TaskMachine> sources=matched.isEmpty()?Collections.singletonList(null):matched;
                    int[] projected={0};
                    for(TaskMachine machine:sources)for(List<ResourceKey> selection:combinations(inputs)){
                        Object nativeInput=previewInput(nativeRecipe,machine,selection,inputs);
                        if(nativeInput==null||!NativeRecipeSource.matches(nativeRecipe,nativeInput,access.level()))continue;
                        Map<ResourceKey,Long> actual=new LinkedHashMap<>();
                        // Verified native deterministic data transformations copy stacks; no machine or output roll runs here.
                        add(actual,call(nativeRecipe,"craft",nativeInput,access.level().registryAccess()));if(actual.isEmpty())continue;
                        List<CraftRecipe.Ingredient<ResourceKey>> exact=new ArrayList<>();for(int i=0;i<inputs.size();i++)exact.add(new CraftRecipe.Ingredient<>(List.of(selection.get(i)),inputs.get(i).count()));
                        int duration=(int)Math.max(1,number(call(nativeRecipe,"ticks"),number(call(nativeRecipe,"getBaseEnergyCost"),1)));
                        Object setup=machine==null?new SetupConfiguration(List.of()):machine.setup(nativeRecipe);
                        String profile=NativeMachineAdapter.profile(access.level(),List.of(immutableConfiguration(setup),selection,actual));projected[0]++;
                        result.add(new Recipe("enderio:native_components/"+profile+"/"+holder.id(),BuiltInRegistries.RECIPE_TYPE.getKey(holder.type())+"/setup/"+NativeMachineAdapter.profile(access.level(),immutableConfiguration(setup)),exact,actual,actual,duration,
                                Math.max(7200,duration*8),new Bound(nativeRecipe,holder.type(),setup),false));
                    }
                    classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),projected[0]>0?"processing":"awaiting-native-input",
                            projected[0]>0?"Exact native component or reagent-modifier output variants":"Requires matching component-bearing inputs or a configured native template"));continue;
                }
                Set<Object> configurations=new LinkedHashSet<>();
                if(dynamicOutputs instanceof List<?> values && values.stream().anyMatch(value -> call(value,"chance") instanceof Number)) {
                    for(TaskMachine machine:matched)configurations.add(machine.grinding());
                }
                if(configurations.isEmpty())configurations.add("");
                int variant=0;
                for(Object configuration:configurations){
                    Map<ResourceKey,Long> exactGuaranteed=outputs(nativeRecipe,access,false,configuration),exactPossible=outputs(nativeRecipe,access,true,configuration);
                    int duration=(int)Math.max(1,number(call(nativeRecipe,"ticks"),Math.max(1,number(call(nativeRecipe,"getBaseEnergyCost"),1))));
                    String profile=NativeMachineAdapter.profile(access.level(),immutableConfiguration(configuration));
                    result.add(new Recipe("enderio:native/"+profile+"/"+holder.id(),BuiltInRegistries.RECIPE_TYPE.getKey(holder.type())+"/profile/"+profile,inputs,exactGuaranteed,exactPossible,
                            duration,Math.max(7200,duration*8),new Bound(nativeRecipe,holder.type(),configuration),!exactGuaranteed.equals(exactPossible)));
                }
                classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),"processing","Native counted inputs, item/fluid outputs and task-host recipe association"));
            }catch(RuntimeException failure){classified.add(new Classification(holder.id().toString(),nativeRecipe.getClass().getName(),"unsupported-contract",failure.getMessage()));}
        }
        for(Machine machine:loaded)if(machine instanceof ConfiguredCrafter crafter){
            var recipe=crafter.recipe();if(recipe!=null){result.add(recipe);classified.add(new Classification(recipe.id(),"native-configured-crafting","processing","Native ghost template, registered crafting recipe, input/output layout and real output buffer"));}
        }
        audit=List.copyOf(classified);return List.copyOf(result);
    }
    private static List<CraftRecipe.Ingredient<ResourceKey>> inputs(Object recipe,Map<ResourceKey,Long> stock){
        List<CraftRecipe.Ingredient<ResourceKey>> result=new ArrayList<>();
        Object sized=value(recipe,"inputs","getInputs");
        if(sized instanceof List<?> list && !list.isEmpty())for(Object input:list){
            if(input instanceof Ingredient ingredient)addInput(result,ingredient,1,stock);
            else if(call(input,"ingredient") instanceof Ingredient ingredient)addInput(result,ingredient,number(call(input,"count"),1),stock);
        }
        else for(Ingredient input:NativeRecipeSource.ingredients(recipe))addInput(result,input,1,stock);
        Object fluid=call(recipe,"input");
        if(fluid!=null && call(fluid,"amount") instanceof Number amount){
            Object alternatives=call(fluid,"getFluids");
            if(alternatives instanceof FluidStack[] fluids){
                List<ResourceKey> keys=Arrays.stream(fluids).filter(stack -> !stack.isEmpty()).map(stack -> (ResourceKey)new FluidKey(stack)).distinct().toList();
                if(!keys.isEmpty())result.add(new CraftRecipe.Ingredient<>(keys,amount.longValue()));
            }
        }
        Object experience=value(recipe,"experience","getExpCost");
        if(experience instanceof Integer levels && NativeBytecode.inspect(recipe.getClass()).methods().stream().anyMatch(method -> method.name().equals("craft")&&method.references().stream().anyMatch(reference -> reference.owner().endsWith("EnderIOCapabilities")||reference.owner().endsWith("EIOCapabilities")))){
            Class<?> utility=optional("com.enderio.enderio.foundation.util.ExperienceUtil");if(utility==null)utility=optional("com.enderio.base.common.util.ExperienceUtil");
            long amount=number(staticCall(utility,"getFluidFromLevel",levels),0);Object nativeFluid=xpFluid();
            if(amount>0 && nativeFluid instanceof net.minecraft.world.level.material.Fluid source)result.add(new CraftRecipe.Ingredient<>(List.of(new FluidKey(new FluidStack(source,1))),amount));
        }
        return List.copyOf(result);
    }
    private static List<List<ResourceKey>> combinations(List<CraftRecipe.Ingredient<ResourceKey>> inputs){
        List<List<ResourceKey>> result=new ArrayList<>();combinations(inputs,0,new ArrayList<>(),result);return result;
    }
    private static void combinations(List<CraftRecipe.Ingredient<ResourceKey>> inputs,int index,List<ResourceKey> selected,List<List<ResourceKey>> result){
        if(index==inputs.size()){result.add(List.copyOf(selected));return;}
        for(ResourceKey alternative:inputs.get(index).alternatives()){selected.add(alternative);combinations(inputs,index+1,selected,result);selected.remove(selected.size() - 1);}
    }
    private static Object previewInput(Object recipe,TaskMachine machine,List<ResourceKey> selected,List<CraftRecipe.Ingredient<ResourceKey>> ingredients){
        Object template=machine==null?null:call(machine.entity,"createRecipeInput");
        if(template==null&&machine!=null){Object legacy=machine.legacyPreview(recipe,selected,ingredients);if(legacy!=null)return legacy;}
        Class<?> type=template==null?optional(recipe.getClass().getName()+"$Input"):template.getClass();if(type==null)return null;
        List<ItemStack> items=new ArrayList<>();List<FluidStack> fluids=new ArrayList<>();
        for(int index=0;index<selected.size();index++){
            ResourceKey key=selected.get(index);long amount=ingredients.get(index).count();
            if(key instanceof ItemKey item)items.add(item.sample().copyWithCount(Math.toIntExact(amount)));
            if(key instanceof FluidKey fluid)fluids.add((FluidStack)MekanismResourceSupport.sample(fluid,amount));
        }
        List<Object> arguments=new ArrayList<>();int itemIndex=0,fluidIndex=0;
        for(var member:NativeBytecode.inspect(type).fields())if((member.access()&8)==0){
            Object original=template==null?null:field(template,member.name());
            if(member.descriptor().equals("Lnet/minecraft/world/item/ItemStack;"))arguments.add(itemIndex<items.size()?items.get(itemIndex++):original instanceof ItemStack stack?stack.copy():ItemStack.EMPTY);
            else if(member.descriptor().endsWith("/FluidStack;"))arguments.add(fluidIndex<fluids.size()?fluids.get(fluidIndex++):original instanceof FluidStack stack?stack.copy():FluidStack.EMPTY);
            else if(member.descriptor().equals("Ljava/util/List;"))arguments.add(items);
            else if(member.descriptor().equals("I"))arguments.add(1);
            else arguments.add(original);
        }
        return construct(type,arguments.toArray());
    }
    private static void addInput(List<CraftRecipe.Ingredient<ResourceKey>> result,Ingredient ingredient,long count,Map<ResourceKey,Long> stock){
        if(ingredient.isEmpty())return;
        Set<ResourceKey> keys=new LinkedHashSet<>();
        for(ItemStack stack:ingredient.getItems())if(!stack.isEmpty())keys.add(new ItemKey(stack));
        for(ResourceKey key:stock.keySet())if(key instanceof ItemKey item && ingredient.test(item.sample()))keys.add(key);
        if(keys.isEmpty())throw new IllegalArgumentException("Native ingredient has no registry or current-stock representation");
        result.add(new CraftRecipe.Ingredient<>(List.copyOf(keys),count));
    }
    private static void add(Map<ResourceKey,Long> result,Object output){
        if(output instanceof ItemStack stack && !stack.isEmpty())result.merge(new ItemKey(stack),(long)stack.getCount(),Math::addExact);
        else if(output instanceof FluidStack stack && !stack.isEmpty())result.merge(new FluidKey(stack),(long)stack.getAmount(),Math::addExact);
        else if(output instanceof Iterable<?> list)list.forEach(value -> add(result,value));
        else if(output!=null && output.getClass().getName().equals("com.enderio.core.common.recipes.OutputStack")){add(result,call(output,"getItem"));add(result,call(output,"getFluid"));}
    }
    private static Map<ResourceKey,Long> outputs(Object recipe,CraftingService.NetworkAccess access,boolean possible,Object configuration){
        Map<ResourceKey,Long> result=new LinkedHashMap<>();
        List<?> nativeOutputs=list(value(recipe,"outputs","getOutputs"));
        if(!nativeOutputs.isEmpty() && nativeOutputs.stream().allMatch(output -> call(output,"chance") instanceof Number)){
            Object bonusType=value(recipe,"bonusType","getBonusType");
            double multiply=Boolean.TRUE.equals(call(bonusType,"canMultiply"))?decimal(value(configuration,"outputMultiplier","getOutputMultiplier"),1):1;
            double chanceMultiply=Boolean.TRUE.equals(call(bonusType,"doChance"))?decimal(value(configuration,"bonusMultiplier","getBonusMultiplier"),1):1;
            for(Object output:nativeOutputs){
                if(Boolean.FALSE.equals(call(output,"isPresent")))continue;
                double chance=decimal(call(output,"chance"),0)*chanceMultiply;
                long rolls=possible?(long)Math.ceil(multiply):chance>=1?(long)Math.floor(multiply):0;
                if(chance<=0||rolls<=0)continue;
                Object item=call(output,"getItemStack");if(item instanceof ItemStack stack && !stack.isEmpty())add(result,stack.copyWithCount(Math.toIntExact(Math.multiplyExact(stack.getCount(),rolls))));
            }
        }else add(result,call(recipe,"getResultStacks",access.level().registryAccess()));
        return Map.copyOf(result);
    }
    private static double decimal(Object value,double fallback){return value instanceof Number number?number.doubleValue():fallback;}
    private static Object immutableConfiguration(Object configuration){
        if(configuration instanceof SetupConfiguration setup)return setup.setup();
        if(configuration==null||configuration instanceof String)return configuration==null?"":configuration;
        return List.of(decimal(value(configuration,"outputMultiplier","getOutputMultiplier"),1),decimal(value(configuration,"bonusMultiplier","getBonusMultiplier"),1),decimal(value(configuration,"powerUse","getPowerUse"),1),number(value(configuration,"durability","getDurability"),0));
    }
    private static Object xpFluid(){
        Class<?> registry=optional("com.enderio.enderio.init.EIOFluids");if(registry==null)registry=optional("com.enderio.base.common.init.EIOFluids");if(registry==null)return null;
        Object holder=staticField(registry,"XP_JUICE"),source=value(holder,"source","getSource");return unwrap(source==null?holder:source);
    }
    private static Object nativeTank(Object entity){Object tank=call(entity,"getFluidTank");return tank==null?call(call(entity,"getFluidHandler"),"getTank",0):tank;}
    private static Port fluidPort(BlockEntity entity,Object handler,int index,boolean input,boolean output){
        Object tank=call(handler,"getTank",index);Port nativePort=NativeMachinePorts.fluidTank(tank,input,output);
        if(field(tank,"handler")==handler)return nativePort;
        Runnable changed=()->{call(handler,"onContentsChanged",index);Object listener=field(handler,"changeListener");if(listener instanceof java.util.function.IntConsumer consumer)consumer.accept(index);entity.setChanged();};
        return NativeMachinePorts.callbacks(nativePort.identity(),input,output,nativePort::snapshot,(key,amount,simulate)->{
            long accepted=nativePort.insert(key,amount,simulate);if(!simulate&&accepted>0)changed.run();return accepted;
        },(key,amount,simulate)->{long extracted=nativePort.extract(key,amount,simulate);if(!simulate&&extracted>0)changed.run();return extracted;});
    }

    /** Native configured crafting is a separate shared protocol from Ender IO's task hosts. */
    private static final class ConfiguredCrafter implements Machine{
        final ServerLevel level;final BlockEntity entity;final Object ghostAccess;final String initialProfile;
        ConfiguredCrafter(ServerLevel level,BlockEntity entity){
            this.level=level;this.entity=entity;Object ghost=null;
            for(Class<?> type:hierarchy(entity.getClass()))for(var method:NativeBytecode.inspect(type).methods())if(method.name().equals("updateRecipe"))for(var reference:method.references())
                if(reference.staticRead()&&reference.descriptor().endsWith("/MultiSlotAccess;")){ghost=reference.readStatic(type.getClassLoader());break;}
            ghostAccess=ghost;
            initialProfile=profile();
        }
        List<ItemStack> ghostItems(){
            Object inventory=call(entity,"getInventory"),items=call(ghostAccess,"getItemStacks",inventory);
            List<?> values=items==null?list(call(ghostAccess,"getAccesses")).stream().map(access -> call(access,"getItemStack",inventory)).toList():list(items);
            return values.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast).map(ItemStack::copy).toList();
        }
        Object template(){List<ItemStack> items=ghostItems();return items.size()==9?NativeRecipeSource.craftingInput(items):null;}
        String profile(){List<ItemStack> items=ghostItems();if(items.isEmpty())return "";List<Object> data=new ArrayList<>();for(ItemStack stack:items)data.add(stack.isEmpty()?"":new ItemKey(stack));return NativeMachineAdapter.profile(level,data);}
        Recipe recipe(){
            NativeRecipeSource.Entry holder=NativeRecipeSource.entry(field(entity,"recipe"));if(holder==null)return null;Object nativeRecipe=holder.value();
            Object input=template();if(input==null||!NativeRecipeSource.matches(nativeRecipe,input,level))return null;
            List<CraftRecipe.Ingredient<ResourceKey>> inputs=new ArrayList<>();for(ItemStack stack:ghostItems())if(!stack.isEmpty())inputs.add(new CraftRecipe.Ingredient<>(List.of(new ItemKey(stack)),1));
            Map<ResourceKey,Long> outputs=new LinkedHashMap<>();add(outputs,NativeRecipeSource.assemble(nativeRecipe,input,level.registryAccess()));add(outputs,NativeRecipeSource.remainingItems(nativeRecipe,input));
            if(inputs.isEmpty()||outputs.isEmpty())return null;String profile=profile();
            return new Recipe("enderio:configured_crafting/"+profile+"/"+holder.id(),"enderio/configured_crafting/"+profile,inputs,outputs,outputs,20,7200,new ConfiguredCraft(nativeRecipe,input,profile),false);
        }
        public Object identity(){return entity;}
        public Object instance(){return entity;}
        public boolean valid(){return !entity.isRemoved()&&level.hasChunkAt(entity.getBlockPos())&&level.getBlockEntity(entity.getBlockPos())==entity&&profile().equals(initialProfile);}
        public boolean accepts(Recipe recipe){NativeRecipeSource.Entry holder=NativeRecipeSource.entry(field(entity,"recipe"));return recipe.nativeRecipe() instanceof ConfiguredCraft configured&&holder!=null&&holder.value()==configured.recipe&&profile().equals(configured.profile);}
        public Object catalogState(){return profile();}
        public List<GlobalPos> claimedPositions(){return List.of(GlobalPos.of(level.dimension(),entity.getBlockPos()));}
        public boolean ready(Recipe recipe,List<CraftPlan.Selection<ResourceKey>> selected){return valid()&&accepts(recipe)&&!Boolean.FALSE.equals(call(entity,"canAct"))&&list(field(entity,"outputBuffer")).isEmpty();}
        public boolean completed(Recipe recipe,int polls){return polls>0&&list(field(entity,"outputBuffer")).isEmpty();}
        public List<Port> ports(Recipe recipe){
            Object inventory=call(entity,"getInventory"),layout=call(inventory,"getLayout");if(!(inventory instanceof IItemHandler handler))return List.of();
            int capacitor=(int)number(call(layout,"getCapacitorSlot"),-1);List<Port> ports=new ArrayList<>();
            for(int slot=0;slot<handler.getSlots();slot++)if(slot!=capacitor){boolean in=Boolean.TRUE.equals(call(layout,"canInsert",slot)),out=Boolean.TRUE.equals(call(layout,"canExtract",slot));if(in||out)ports.add(NativeMachinePorts.itemSlot(handler,slot,in,out));}return List.copyOf(ports);
        }
    }

    /** Cached native fluid conversion is shared by every tank tier, independently of task-host machines. */
    private static final class CachedFluidMachine implements Machine{
        final ServerLevel level;final BlockEntity entity;final StoredType type;final List<Integer> recipeSlots;
        CachedFluidMachine(ServerLevel level,BlockEntity entity,StoredType type){
            this.level=level;this.entity=entity;this.type=type;List<Integer> slots=TaskMachine.recipeSlots(entity.getClass());
            if(slots.size()<2){Object drain=staticField(entity.getClass(),"FLUID_DRAIN_INPUT"),fill=staticField(entity.getClass(),"FLUID_FILL_INPUT");if(drain!=null&&fill!=null)slots=List.of((int)number(call(drain,"getIndex"),-1),(int)number(call(fill,"getIndex"),-1));}
            recipeSlots=slots;
        }
        public Object identity(){return entity;}
        public Object instance(){return entity;}
        public boolean valid(){return !entity.isRemoved()&&level.hasChunkAt(entity.getBlockPos())&&level.getBlockEntity(entity.getBlockPos())==entity;}
        public List<GlobalPos> claimedPositions(){return List.of(GlobalPos.of(level.dimension(),entity.getBlockPos()));}
        public Object catalogState(){return type.type;}
        public boolean accepts(Recipe recipe){
            if(!(recipe.nativeRecipe() instanceof TankBound bound)||bound.type!=type.type)return false;
            Object tank=nativeTank(entity),contents=call(tank,"getFluid");
            return bound.filling||!(contents instanceof FluidStack fluid)||fluid.isEmpty()||new FluidKey(fluid).equals(bound.product);
        }
        public boolean ready(Recipe recipe,List<CraftPlan.Selection<ResourceKey>> selected){return valid()&&accepts(recipe)&&!Boolean.FALSE.equals(call(entity,"canAct"));}
        public boolean completed(Recipe recipe,int polls){return polls>0;}
        public List<Port> ports(Recipe recipe){
            if(!(recipe.nativeRecipe() instanceof TankBound bound)||recipeSlots.size()<2)return List.of();
            Object inventory=call(entity,"getInventory"),layout=call(inventory,"getLayout");if(!(inventory instanceof IItemHandler handler))return List.of();List<Port> ports=new ArrayList<>();
            int input=recipeSlots.get(bound.filling?0:1);
            for(int slot=0;slot<handler.getSlots();slot++){boolean out=Boolean.TRUE.equals(call(layout,"canExtract",slot));if(slot==input||out)ports.add(NativeMachinePorts.itemSlot(handler,slot,slot==input,out));}
            Object tank=nativeTank(entity);if(tank!=null)ports.add(fluidPort(entity,call(entity,"getFluidHandler"),0,bound.filling,!bound.filling));return List.copyOf(ports);
        }
    }

    private static final class TaskMachine implements Machine {
        final ServerLevel level;final BlockEntity entity;final List<Object> hosts;final List<Integer> recipeSlots;
        TaskMachine(ServerLevel level,BlockEntity entity,List<Object> hosts){this.level=level;this.entity=entity;this.hosts=hosts;recipeSlots=recipeSlots(entity.getClass());}
        static List<Integer> recipeSlots(Class<?> entityType){
            LinkedHashSet<Integer> slots=new LinkedHashSet<>();
            for(Class<?> type:hierarchy(entityType))for(var method:NativeBytecode.inspect(type).methods())if(method.name().equals("createRecipeInput"))for(var reference:method.references()){
                if(!reference.staticRead() || !(reference.descriptor().endsWith("/SingleSlotAccess;")||reference.descriptor().endsWith("/MultiSlotAccess;")))continue;
                Object access=reference.readStatic(type.getClassLoader());Object index=call(access,"getIndex");
                if(index instanceof Number number)slots.add(number.intValue());
                for(Object single:list(call(access,"getAccesses")))slots.add((int)number(call(single,"getIndex"),-1));
            }
            return slots.stream().filter(slot -> slot>=0).toList();
        }
        public Object identity(){return entity;}
        public Object instance(){return entity;}
        public boolean valid(){return !entity.isRemoved() && level.hasChunkAt(entity.getBlockPos()) && level.getBlockEntity(entity.getBlockPos())==entity;}
        boolean acceptsType(Object type){return hosts.stream().anyMatch(host -> field(host,"recipeType")==type);}
        public boolean accepts(Recipe recipe){
            if(!(recipe.nativeRecipe() instanceof Bound bound)||!acceptsType(bound.type))return false;
            if(bound.configuration instanceof SetupConfiguration configured)return configured.equals(setup(bound.recipe));
            return bound.configuration.equals("")||immutableConfiguration(bound.configuration).equals(immutableConfiguration(grinding()));
        }
        Object grinding(){Object value=call(entity,"getGrindingBallData");return value==null?"":value;}
        public Object catalogState(){Object mode=call(entity,"getMode");return List.of(mode==null?"":mode,immutableConfiguration(grinding()),setupState());}
        Object templateRecipe(){for(Object host:hosts){List<NativeRecipeSource.Entry> recipes=NativeRecipeSource.allFor(level,field(host,"recipeType"));if(!recipes.isEmpty())return recipes.get(0).value();}return null;}
        Object container(Object nativeRecipe){for(Object host:hosts)if(field(host,"recipeType")==NativeRecipeSource.type(nativeRecipe)){Object container=call(host,"getContainer");if(container!=null)return container;}return null;}
        List<Integer> slots(Object nativeRecipe){
            if(!recipeSlots.isEmpty())return recipeSlots;Object container=container(nativeRecipe),access=call(container,"getSlotAccess");List<Integer> direct=accessSlots(access);if(!direct.isEmpty())return direct;
            TreeSet<Integer> found=new TreeSet<>();for(Class<?> type:hierarchy(nativeRecipe.getClass()))for(var reference:NativeBytecode.inspect(type).references())if(reference.staticRead()&&(reference.descriptor().endsWith("/SingleSlotAccess;")||reference.descriptor().endsWith("/MultiSlotAccess;")))found.addAll(accessSlots(reference.readStatic(type.getClassLoader())));
            if(found.isEmpty()){
                // Older indexed Container recipes use their ingredient order directly, without SlotAccess constants.
                Object inventory=call(entity,"getInventory"),layout=call(inventory,"getLayout");
                long count=NativeRecipeSource.ingredients(nativeRecipe).stream().filter(ingredient -> !ingredient.isEmpty()).count();
                int capacitor=(int)number(call(layout,"getCapacitorSlot"),-1);
                if(inventory instanceof IItemHandler handler)for(int slot=0;slot<handler.getSlots()&&found.size()<count;slot++)
                    if(slot!=capacitor&&Boolean.TRUE.equals(call(layout,"canInsert",slot))&&!Boolean.TRUE.equals(call(layout,"canExtract",slot)))found.add(slot);
            }
            return List.copyOf(found);
        }
        static List<Integer> accessSlots(Object access){
            Object index=call(access,"getIndex");if(index instanceof Number number)return List.of(number.intValue());
            return list(call(access,"getAccesses")).stream().map(single -> (int)number(call(single,"getIndex"),-1)).filter(slot -> slot>=0).toList();
        }
        boolean multi(Object nativeRecipe){
            if(hasMultiInput(entity.getClass())||call(container(nativeRecipe),"getSlotAccess")!=null)return true;
            return NativeBytecode.hierarchy(nativeRecipe.getClass()).stream().flatMap(shape -> shape.references().stream()).anyMatch(reference -> reference.staticRead()&&reference.descriptor().endsWith("/MultiSlotAccess;"));
        }
        List<Integer> consumedSlots(Object nativeRecipe,long count){
            List<Integer> slots=slots(nativeRecipe);if(!multi(nativeRecipe)&&!(call(nativeRecipe,"getExpCost") instanceof Number)&&count>0&&slots.size()>count)return slots.subList(0,(int)count);return slots;
        }
        List<CraftRecipe.Ingredient<ResourceKey>> completeLegacyInputs(Object nativeRecipe,List<CraftRecipe.Ingredient<ResourceKey>> declared,Map<ResourceKey,Long> stock){
            Object inventory=call(entity,"getInventory");if(!(inventory instanceof IItemHandler handler))return List.of();
            List<CraftRecipe.Ingredient<ResourceKey>> remaining=new ArrayList<>(declared.stream().filter(input -> input.alternatives().get(0) instanceof ItemKey).toList()),result=new ArrayList<>();
            for(int slot:slots(nativeRecipe)){
                CraftRecipe.Ingredient<ResourceKey> matched=remaining.stream().filter(input -> input.alternatives().stream().filter(ItemKey.class::isInstance).map(ItemKey.class::cast).anyMatch(key -> handler.isItemValid(slot,key.sample()))).findFirst().orElse(null);
                if(matched!=null){remaining.remove(matched);result.add(matched);continue;}
                List<ResourceKey> candidates=stock.keySet().stream().filter(ItemKey.class::isInstance).filter(key -> handler.isItemValid(slot,((ItemKey)key).sample())).toList();
                if(candidates.isEmpty())return List.of();result.add(new CraftRecipe.Ingredient<>(candidates,1));
            }
            declared.stream().filter(input -> input.alternatives().get(0) instanceof FluidKey).forEach(result::add);return List.copyOf(result);
        }
        Object legacyPreview(Object nativeRecipe,List<ResourceKey> selected,List<CraftRecipe.Ingredient<ResourceKey>> ingredients){
            Object template=container(nativeRecipe),inventory=call(entity,"getInventory");if(template==null||!(inventory instanceof IItemHandler handler))return null;
            Class<?> stacks=optional(IItemHandler.class.getPackageName()+".ItemStackHandler");if(stacks==null)return null;Object copy=construct(stacks,handler.getSlots());
            for(int slot=0;slot<handler.getSlots();slot++)call(copy,"setStackInSlot",slot,handler.getStackInSlot(slot).copy());
            List<Integer> slots=consumedSlots(nativeRecipe,selected.stream().filter(ItemKey.class::isInstance).count());for(int slot:slots)call(copy,"setStackInSlot",slot,ItemStack.EMPTY);
            int next=0;long fluidAmount=0;for(int index=0;index<selected.size();index++){
                ResourceKey key=selected.get(index);if(key instanceof ItemKey item&&next<slots.size())call(copy,"setStackInSlot",slots.get(next++),item.sample().copyWithCount(Math.toIntExact(ingredients.get(index).count())));
                if(key instanceof FluidKey)fluidAmount=ingredients.get(index).count();
            }
            for(var method:NativeBytecode.inspect(template.getClass()).methods())if(method.name().equals("<init>")){
                java.lang.invoke.MethodType signature=java.lang.invoke.MethodType.fromMethodDescriptorString(method.descriptor(),template.getClass().getClassLoader());List<Object> arguments=new ArrayList<>();boolean supported=true;
                for(Class<?> parameter:signature.parameterArray()){
                    if(parameter.isInstance(copy))arguments.add(copy);
                    else if(parameter==boolean.class)arguments.add(Boolean.TRUE.equals(field(template,"isPrimitive")));
                    else if(java.util.function.Supplier.class.isAssignableFrom(parameter)){
                        if(call(nativeRecipe,"getExpCost") instanceof Number){int amount=Math.toIntExact(fluidAmount);arguments.add((java.util.function.Supplier<Integer>)()->amount);}
                        else{Object supplier=fields(template,field -> field.descriptor().equals("Ljava/util/function/Supplier;")).stream().findFirst().orElse(null);if(supplier==null){supported=false;break;}arguments.add(supplier);}
                    }else{supported=false;break;}
                }
                if(supported)return construct(template.getClass(),arguments.toArray());
            }
            return null;
        }
        List<Object> setupState(){
            Object inventory=call(entity,"getInventory"),nativeRecipe=templateRecipe();if(!(inventory instanceof IItemHandler handler)||nativeRecipe==null)return List.of();
            long count=NativeRecipeSource.ingredients(nativeRecipe).stream().filter(input -> !input.isEmpty()).count();List<Integer> consumed=consumedSlots(nativeRecipe,count);Object layout=call(inventory,"getLayout");
            List<Object> setup=new ArrayList<>();for(int slot=0;slot<handler.getSlots();slot++)if(!consumed.contains(slot)&&Boolean.TRUE.equals(call(layout,"canInsert",slot))){
                ItemStack stack=handler.getStackInSlot(slot);setup.add(stack.isEmpty()?"":List.of(new ItemKey(stack),stack.getCount()));
            }
            return List.copyOf(setup);
        }
        SetupConfiguration setup(Object nativeRecipe){
            long consumed=NativeRecipeSource.ingredients(nativeRecipe).stream().filter(ingredient -> !ingredient.isEmpty()).count();
            if(multi(nativeRecipe)||call(nativeRecipe,"getExpCost") instanceof Number)return new SetupConfiguration(List.of());
            Object inventory=call(entity,"getInventory");List<Object> setup=new ArrayList<>();
            List<Integer> slots=slots(nativeRecipe);if(inventory instanceof IItemHandler handler)for(int index=(int)consumed;index<slots.size();index++){
                ItemStack stack=handler.getStackInSlot(slots.get(index));setup.add(stack.isEmpty()?"":new ItemKey(stack));
            }
            return new SetupConfiguration(List.copyOf(setup));
        }
        public List<GlobalPos> claimedPositions(){return List.of(GlobalPos.of(level.dimension(),entity.getBlockPos()));}
        public boolean ready(Recipe recipe,List<CraftPlan.Selection<ResourceKey>> selected){
            if(!valid()||!accepts(recipe))return false;
            for(Object host:hosts)if(Boolean.TRUE.equals(call(host,"hasTask")))return false;
            if(Boolean.FALSE.equals(call(entity,"canAct")))return false;
            Bound bound=(Bound)recipe.nativeRecipe();Object mode=call(entity,"getMode");
            Object smelting=call(bound.recipe,"isSmelting");
            if(smelting instanceof Boolean smelt && mode!=null && !Boolean.TRUE.equals(call(mode,smelt?"canSmelt":"canAlloy")))return false;
            return true;
        }
        public boolean completed(Recipe recipe,int polls){return polls>0 && hosts.stream().noneMatch(host -> Boolean.TRUE.equals(call(host,"hasTask")));}
        public List<Port> ports(Recipe recipe){
            List<Port> result=new ArrayList<>();Object inventory=call(entity,"getInventory"),layout=call(inventory,"getLayout");
            long itemInputCount=recipe.inputs().stream().filter(input -> input.alternatives().stream().anyMatch(ItemKey.class::isInstance)).count();
            if(inventory instanceof IItemHandler handler){
                int capacitor=(int)number(call(layout,"getCapacitorSlot"),-1);List<Integer> nativeInputSlots=consumedSlots(((Bound)recipe.nativeRecipe()).recipe,itemInputCount);
                for(int slot=0;slot<handler.getSlots();slot++){
                    if(slot==capacitor)continue;
                    boolean out=Boolean.TRUE.equals(call(layout,"canExtract",slot));
                    boolean in=!out && Boolean.TRUE.equals(call(layout,"canInsert",slot)) && nativeInputSlots.contains(slot);
                    if(in||out)result.add(NativeMachinePorts.itemSlot(handler,slot,in,out));
                }
            }
            Object fluids=call(entity,"getFluidHandler"),fluidLayout=call(fluids,"getLayout");int tankCount=(int)number(call(fluids,"getTanks"),0);
            for(int tank=0;tank<tankCount;tank++){
                Object nativeTank=call(fluids,"getTank",tank);boolean in=Boolean.TRUE.equals(call(fluidLayout,"canInsert",tank)),out=Boolean.TRUE.equals(call(fluidLayout,"canExtract",tank));
                if(nativeTank!=null&&(in||out))result.add(fluidPort(entity,fluids,tank,in,out));
            }
            return List.copyOf(result);
        }
        static boolean hasMultiInput(Class<?> type){
            for(Class<?> current:hierarchy(type))for(var method:NativeBytecode.inspect(current).methods())if(method.name().equals("createRecipeInput"))for(var reference:method.references())
                if(reference.staticRead()&&reference.descriptor().endsWith("/MultiSlotAccess;"))return true;
            return false;
        }
    }
}
