package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.ResourceKey;
import com.cappleapple.astralrepository.api.CraftingProvider;
import com.cappleapple.astralrepository.api.CraftingContext;
import com.mojang.logging.LogUtils;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import java.util.*;

/** A server-thread coordinator owned by one Storage Nexus network. */
public final class CraftingService {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<GlobalPos, UUID> PROCESSOR_OWNERS = new HashMap<>();
    private static final Map<Object, UUID> PROCESSOR_IDENTITIES = new HashMap<>();
    private static final Map<GlobalPos, Object> CLAIM_IDENTITIES = new HashMap<>();
    private static final Map<GlobalPos, List<GlobalPos>> CLAIM_POSITIONS = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();
    public interface NetworkAccess {
        ServerLevel level();
        Map<ItemKey, Long> snapshot();
        /** Item and non-item quantities in native resource units. The returned map is detached. */
        default Map<com.cappleapple.astralrepository.api.ResourceKey, Long> resourceSnapshot() { return Map.copyOf(snapshot()); }
        /** Transfer confirmed resources into job ownership and report each physical source. */
        default long reserveResource(com.cappleapple.astralrepository.api.ResourceKey key, long amount, java.util.function.BiConsumer<GlobalPos,Long> receipt) {
            if (!(key instanceof ItemKey item) || amount <= 0) return 0;
            int batch = (int)Math.min(amount, item.sample().getMaxStackSize());
            var sources = new ArrayList<Map.Entry<GlobalPos,ItemStack>>();
            var stack = reserve(item, batch, (from, part) -> sources.add(Map.entry(from, part.copy())));
            if (!stack.isEmpty() && (!item.matches(stack) || stack.getCount() > batch)) {
                // Preserve the actual wrong result. Never turn its quantity into the requested identity.
                try {
                    ItemStack rest = insertFrom(stack.copy(), origin());
                    if (!rest.isEmpty()) CraftRecoveryData.get(level().getServer()).put(UUID.randomUUID(), origin(), Map.of(new ItemKey(rest), (long)rest.getCount()));
                } catch (RuntimeException uncertain) {
                    CraftRecoveryData.get(level().getServer()).uncertain(origin(), Map.of(new ItemKey(stack), (long)stack.getCount()));
                }
                throw new IllegalStateException("Storage returned another item while reserving " + key);
            }
            if (stack.isEmpty()) return 0;
            boolean validSources = sources.stream().allMatch(source -> item.matches(source.getValue()))
                    && sources.stream().mapToLong(source -> source.getValue().getCount()).sum() == stack.getCount();
            if (validSources) sources.forEach(source -> receipt.accept(source.getKey(), (long)source.getValue().getCount()));
            else receipt.accept(origin(), (long)stack.getCount());
            return stack.getCount();
        }
        /** Return the amount that remains uninserted. */
        default long insertResourceFrom(com.cappleapple.astralrepository.api.ResourceKey key, long amount, GlobalPos source) {
            if (!(key instanceof ItemKey item) || amount <= 0) return amount;
            int count = (int)Math.min(amount, item.sample().getMaxStackSize());
            return amount - count + insertFrom(item.sample().copyWithCount(count), source).getCount();
        }
        default void visualResource(GlobalPos from, GlobalPos to, com.cappleapple.astralrepository.api.ResourceKey key, long amount, int durationTicks) {
            if (key instanceof ItemKey item) visual(from, to, item.sample().copyWithCount((int)Math.min(amount, item.sample().getMaxStackSize())), durationTicks);
        }

        ItemStack extract(ItemKey key, int count);
        default ItemStack reserve(ItemKey key,int count,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt){var stack=extract(key,count);if(!stack.isEmpty())receipt.accept(origin(),stack);return stack;}
        default int travelTicks(GlobalPos from,GlobalPos to){return AstralConfig.instantAutomaticLogistics.get()?0:com.cappleapple.astralrepository.network.TransferVisuals.legTicks(from.pos(),to.pos());}
        default ItemStack extractForDelivery(ItemKey key,int count,GlobalPos destination,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt){return reserve(key,count,receipt);}
        default void stagingVisual(GlobalPos table,List<ItemStack> grid,int durationTicks) {}
        default ItemStack extractTo(ItemKey key,int count,GlobalPos destination){return reserve(key,count,(source,stack)->visual(source,destination,stack,20));}
        default ItemStack insertFrom(ItemStack stack,GlobalPos source){return insert(stack);}
        default boolean canRoute(GlobalPos from,GlobalPos to){return true;}
        /** Return the exact uninserted remainder. */
        ItemStack insert(ItemStack stack);
        List<GlobalPos> workstations();
        List<ItemStack> exposedProducts();
        default Set<String> excludedRecipes(ItemStack target) { return Set.of(); }
        default boolean mayUseFuel(ItemKey key) { return true; }
        GlobalPos origin();
        void visual(GlobalPos from, GlobalPos to, ItemStack stack, int durationTicks);
        default void craftingVisual(GlobalPos table, List<ItemStack> grid, ItemStack output, int durationTicks) {}
        /** Invalidate all indexed snapshots containing a machine when its ownership changes. */
        default void processorChanged(GlobalPos machine) {}
        default boolean payCrafting(long complexity) { return true; }
        default List<CraftingProvider> craftingProviders() { return List.of(); }
    }
    public record RequestResult(boolean accepted, String message, UUID jobId) {}
    public record JobStatus(UUID id, UUID owner, ItemStack target, long count, String state, int completed, int total, int active, String message,List<MissingIngredients.Missing<ItemKey>> missing) {
        public JobStatus(UUID id,UUID owner,ItemStack target,long count,String state,int completed,int total,int active,String message){this(id,owner,target,count,state,completed,total,active,message,List.of());}
    }
    private record Job(UUID id, UUID owner, ItemStack target, long count, CraftScheduler<ResourceKey, GlobalPos> scheduler,CraftOrigins<ResourceKey,GlobalPos> origins,long produced) {}
    private final NetworkAccess access;
    private final List<ResourceProcessingAdapter> adapters;
    private final ReservationLedger<ResourceKey> ledger = new ReservationLedger<>();
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private record ExternalJob(UUID id, UUID owner, ItemStack target, long count, CraftingProvider provider, CraftingProvider.Ticket ticket) {}
    private final Map<UUID, ExternalJob> externalJobs = new LinkedHashMap<>();
    private record Finished(JobStatus status, long until) {}
    private final ArrayDeque<Finished> history = new ArrayDeque<>();
    private final CraftRecoveryData recovery;
    private int ticks;
    private static final class Planning {
        final UUID id,owner;final ItemStack target;final int count,requested;final long produced;
        java.util.concurrent.Future<AsyncCraftPlanner.Result<ResourceKey>> future;
        Map<ResourceKey,Long> snapshot=Map.of();Set<String> processes=Set.of(),exclusions=Set.of();
        AsyncCraftPlanner<ResourceKey> catalog;
        Map<String,ResourceProcessingAdapter> owners=Map.of();
        List<MissingIngredients.Missing<ResourceKey>> missing=List.of();
        String state="CALCULATING",error="";
        Planning(UUID id,UUID owner,ItemStack target,int count){this(id,owner,target,count,count,0);}
        Planning(UUID id,UUID owner,ItemStack target,int count,int requested,long produced){this.id=id;this.owner=owner;this.target=target.copyWithCount(1);this.count=count;this.requested=requested;this.produced=produced;}
        JobStatus status(){return new JobStatus(id,owner,target.copy(),requested,state,0,0,0,error,ResourcePresentation.missing(missing));}
    }
    private final Map<UUID,Planning> planning=new LinkedHashMap<>();
    private List<CraftRecipe<ResourceKey>> catalogRecipes=List.of();
    private Map<String,ResourceProcessingAdapter> catalogOwners=Map.of();
    private Map<String,ResourceProcessingAdapter> capturedOwners=Map.of();
    private List<?> recipeHolders=List.of(),processingRules=List.of(),adapterStates=List.of();
    private Set<ResourceKey> catalogVariants=Set.of();
    private AsyncCraftPlanner<ResourceKey> catalog;
    private final Map<Set<String>,AsyncCraftPlanner<ResourceKey>> filteredCatalogs=new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Set<String>,AsyncCraftPlanner<ResourceKey>> entry){return size()>32;}
    };
    private record ProcessorProbe(ResourceProcessingAdapter adapter,CraftPlan.Node<ResourceKey> node) {}
    private List<ProcessorProbe> processorProbes=List.of();
    /** Detached server-owned recipe knowledge; browsing never starts or reserves a craft. */
    public List<CraftRecipe<ResourceKey>> catalogSnapshot() {
        refreshCatalog(access.resourceSnapshot());
        return catalogRecipes;
    }
    public List<CraftRecipe<ResourceKey>> catalogSnapshot(ItemStack desiredOutput) {
        if (!desiredOutput.isEmpty()) {
            var key = new ItemKey(desiredOutput);
            adapters.forEach(adapter -> adapter.requestOutput(access, key));
        }
        return catalogSnapshot();
    }
    public String workstationLabel(String recipeId) {
        var adapter = catalogOwners.get(recipeId);
        return adapter == null ? "" : adapter.workstationLabel(access, recipeId);
    }
    /** Read the captured native dependency identities, never infer them from an encoded alias. */
    public Set<String> sourceRecipes(String recipeId) {
        var adapter = capturedOwners.get(recipeId);
        return adapter == null ? Set.of(recipeId) : adapter.sourceRecipes(recipeId);
    }
    private void refreshCatalog(Map<ResourceKey,Long> stock){
        var holders=List.copyOf(access.level().getServer().getRecipeManager().getRecipes());
        var rules=List.copyOf(ProcessingRules.rules());
        var variants=stock.keySet().stream().filter(k->{if(!(k instanceof ItemKey item))return true;var sample=item.sample();return !new ItemKey(new ItemStack(sample.getItem())).equals(item);}).collect(java.util.stream.Collectors.toUnmodifiableSet());
        var states=adapters.stream().map(adapter->adapter.catalogState(access)).toList();
        if(catalog!=null&&holders.equals(recipeHolders)&&rules.equals(processingRules)&&variants.equals(catalogVariants)&&states.equals(adapterStates))return;
        var recipes=new ArrayList<CraftRecipe<ResourceKey>>();var owners=new LinkedHashMap<String,ResourceProcessingAdapter>();
        for(var adapter:adapters)try{for(var recipe:adapter.recipes(access))if(owners.putIfAbsent(recipe.id(),adapter)==null)recipes.add(recipe);}
        catch(RuntimeException failure){LOGGER.warn("Processing adapter unavailable",failure);}
        catalogRecipes=List.copyOf(recipes);catalogOwners=Map.copyOf(owners);catalog=new AsyncCraftPlanner<>(catalogRecipes);
        var captured=new IdentityHashMap<ResourceProcessingAdapter,ResourceProcessingAdapter>();
        var frozen=new LinkedHashMap<String,ResourceProcessingAdapter>();
        owners.forEach((id,adapter)->frozen.put(id,captured.computeIfAbsent(adapter,ResourceProcessingAdapter::capturePlan)));
        capturedOwners=Map.copyOf(frozen);
        recipeHolders=holders;processingRules=rules;catalogVariants=variants;adapterStates=states;filteredCatalogs.clear();
    }
    private Set<String> availableProcesses(){
        var processes=new HashSet<String>();var machines=access.workstations();var seen=new HashSet<Map.Entry<ResourceProcessingAdapter,String>>();var probes=new ArrayList<ProcessorProbe>();
        for(var recipe:catalogRecipes){var adapter=catalogOwners.get(recipe.id());
            if(!seen.add(Map.entry(adapter,recipe.process())))continue;
            var probe=new CraftPlan.Node<ResourceKey>(0,recipe,List.of(),Set.of());probes.add(new ProcessorProbe(adapter,probe));
            if(machines.stream().anyMatch(at->adapter.supports(access,at,probe)))processes.add(recipe.process());
        }
        processorProbes=List.copyOf(probes);return Set.copyOf(processes);
    }
    /** Craft results return to storage without filling idle machines with unrelated outputs. */
    public boolean isProcessingPosition(GlobalPos position){
        var world=access.level().getServer().getLevel(position.dimension());if(world==null||!world.hasChunkAt(position.pos()))return false;
        if(world.getBlockEntity(position.pos()) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity
                || FurnaceAccess.isCandidate(world.getBlockEntity(position.pos()))
                || VanillaProcessingAdapter.isCraftingWorkstation(world,position.pos())
                || world.getBlockState(position.pos()).is(net.minecraft.world.level.block.Blocks.STONECUTTER)
                || ProcessingRules.isCandidate(world,position.pos())
                || adapters.stream().anyMatch(adapter->adapter.isProcessingPosition(access,position)))return true;
        return processorProbes.stream().anyMatch(probe->probe.adapter().supports(access,position,probe.node()));
    }
    private void calculate(Planning job,Map<ResourceKey,Long> stock,Set<String> processes){
        job.snapshot=Map.copyOf(stock);job.processes=processes;job.catalog=catalog;job.owners=capturedOwners;
        job.exclusions=Set.copyOf(access.excludedRecipes(job.target));
        var worker=job.exclusions.isEmpty()?catalog:filteredCatalogs.computeIfAbsent(job.exclusions,
                excluded->new AsyncCraftPlanner<>(catalogRecipes.stream().filter(recipe->RecipeExclusions.allows(recipe.id(),sourceRecipes(recipe.id()),excluded)).toList()));
        job.state="CALCULATING";job.missing=List.of();job.error="";
        try{job.future=worker.submit(new ItemKey(job.target),job.count,ledger.available(stock),processes);}
        catch(java.util.concurrent.RejectedExecutionException busy){job.future=null;}
    }
    private void tickPlanning(){
        if(planning.isEmpty())return;
        var stock=access.resourceSnapshot();refreshCatalog(stock);var processes=availableProcesses();
        for(var job:List.copyOf(planning.values())){
            if(job.future==null){if(job.state.equals("CALCULATING")||job.catalog!=catalog||!job.snapshot.equals(stock)||!job.processes.equals(processes)||!job.exclusions.equals(access.excludedRecipes(job.target)))calculate(job,stock,processes);continue;}
            if(!job.future.isDone())continue;
            try{
                var result=job.future.get();job.future=null;
                var currentStock=stock;
                boolean staleStock=result.plan()==null?!job.snapshot.equals(stock):result.plan().reservations().entrySet().stream().anyMatch(e->currentStock.getOrDefault(e.getKey(),0L)<e.getValue());
                if(job.catalog!=catalog||staleStock||!job.processes.equals(processes)||!job.exclusions.equals(access.excludedRecipes(job.target))){calculate(job,stock,processes);continue;}
                if(result.plan()==null){
                    var external=job.exclusions.isEmpty()?tryExternal(job.owner,job.id,job.target,job.count,result.error()):new RequestResult(false,result.error(),null);
                    if(external.accepted()){planning.remove(job.id);continue;}
                    job.state=result.missing().isEmpty()?"WAITING":"MISSING";job.missing=result.missing();job.error=result.error();continue;
                }
                planning.remove(job.id);
                var accepted=startPlanned(job,result.plan());
                if(!accepted.accepted())remember(new JobStatus(job.id,job.owner,job.target,job.requested,"FAILED",0,0,0,accepted.message()));
                stock=access.resourceSnapshot();
            }catch(InterruptedException failure){Thread.currentThread().interrupt();return;}
            catch(java.util.concurrent.ExecutionException|RuntimeException failure){planning.remove(job.id);remember(new JobStatus(job.id,job.owner,job.target,job.requested,"FAILED",0,0,0,"Craft calculation failed"));LOGGER.warn("Craft calculation failed",failure);}
        }
    }

    public CraftingService(NetworkAccess access) {
        this.access = access;
        this.adapters = ProcessingAdapters.createResourceAdapters();
        this.recovery = CraftRecoveryData.get(access.level().getServer());
    }
    static void animate(NetworkAccess access, GlobalPos from, GlobalPos to, ItemStack stack, int ticks) {
        try { access.visual(from, to, stack, com.cappleapple.astralrepository.network.LogisticsTiming.visualTicks(ticks)); } catch (RuntimeException failure) { LOGGER.debug("Cosmetic crafting transport failed", failure); }
    }
    static void animateTable(NetworkAccess access, GlobalPos table, List<ItemStack> grid, ItemStack output, int ticks) {
        try { access.craftingVisual(table, grid, output, com.cappleapple.astralrepository.network.LogisticsTiming.visualTicks(ticks)); } catch (RuntimeException failure) { LOGGER.debug("Cosmetic table animation failed", failure); }
    }
    public static boolean isProcessorReserved(GlobalPos position) { return PROCESSOR_OWNERS.containsKey(position); }
    static boolean hasProcessorClaims() { return !PROCESSOR_IDENTITIES.isEmpty(); }
    static NativeWorldOutputs.Claim nativeOutputClaim(ServerLevel level, net.minecraft.core.BlockPos position) {
        if (PROCESSOR_IDENTITIES.isEmpty()) return null;
        GlobalPos at = GlobalPos.of(level.dimension(), position);
        if (CLAIM_IDENTITIES.containsKey(at)) return new NativeWorldOutputs.Claim(PROCESSOR_OWNERS.get(at), at);
        Object identity = null;
        try {
            Object handler = MekanismMachineAccess.handler(level.getBlockEntity(position));
            if (handler != null && PROCESSOR_IDENTITIES.containsKey(MekanismMachineAccess.identity(handler))) identity = MekanismMachineAccess.identity(handler);
        } catch (RuntimeException | LinkageError unavailable) { /* Other native backends remain independent. */ }
        if (identity == null) identity = NativeMachineBackends.ownedIdentity(level, position, PROCESSOR_IDENTITIES.keySet());
        if (identity == null) return null;
        UUID request = PROCESSOR_IDENTITIES.get(identity);
        for (var entry : CLAIM_IDENTITIES.entrySet()) if (entry.getValue().equals(identity)) return new NativeWorldOutputs.Claim(request, entry.getKey());
        return null;
    }
    /** Direct rune paths also respect native multiblock aliases outside their originating network. */
    public static boolean isProcessorReserved(net.minecraft.server.MinecraftServer server, GlobalPos position) {
        if (isProcessorReserved(position)) return true;
        var level = server.getLevel(position.dimension());
        if (level == null || !level.hasChunkAt(position.pos()) || PROCESSOR_IDENTITIES.isEmpty()) return false;
        try {
            Object handler = MekanismMachineAccess.handler(level.getBlockEntity(position.pos()));
            if (handler != null && PROCESSOR_IDENTITIES.containsKey(MekanismMachineAccess.identity(handler))) return true;
        } catch (RuntimeException | LinkageError unavailable) { /* Other mod identities remain independently discoverable. */ }
        return NativeMachineBackends.reserved(level, position.pos(), PROCESSOR_IDENTITIES.keySet());
    }
    public boolean isProcessorAliasReserved(GlobalPos position) {
        if (isProcessorReserved(position)) return true;
        for (var adapter : adapters) if (adapter.isProcessingPosition(access, position)
                && PROCESSOR_IDENTITIES.containsKey(adapter.processorIdentity(access, position))) return true;
        return false;
    }
    public RequestResult request(ServerPlayer player, ItemStack target, int count) {
        return com.cappleapple.astralrepository.network.LogisticsTiming.automaticOperation(() -> requestNow(player, target, count));
    }
    private RequestResult requestNow(ServerPlayer player, ItemStack target, int count) {
        if (target.isEmpty() || count < 1 || count > 4096) return new RequestResult(false, "Choose between 1 and 4096 items", null);
        if (jobs.values().stream().filter(job -> job.owner().equals(player.getUUID())).count()
                + externalJobs.values().stream().filter(job -> job.owner().equals(player.getUUID())).count()
                + planning.values().stream().filter(job -> job.owner.equals(player.getUUID())).count() >= 48)
            return new RequestResult(false, "Finish or cancel an existing request before opening more than 48 crafting jobs", null);
        ItemKey key = new ItemKey(target);
        if (access.exposedProducts().stream().noneMatch(stack -> !stack.isEmpty() && new ItemKey(stack).equals(key)))
            return new RequestResult(false, "Place a Recipe Tome for this product in a connected Chiseled Bookshelf", null);
        UUID id = UUID.randomUUID();
        planning.put(id,new Planning(id,player.getUUID(),target,count));
        return new RequestResult(true,"Calculating",id);
    }
    private RequestResult startPlanned(Planning job,CraftPlan<ResourceKey> plan){
        UUID id=job.id;ItemStack target=job.target;int count=job.count;
        var owners=job.owners;var snapshot=access.resourceSnapshot();
        var excluded=Set.copyOf(access.excludedRecipes(target));
        if(plan.nodes().stream().anyMatch(node->{var owner=owners.get(node.recipe().id());return owner==null||!RecipeExclusions.allows(node.recipe().id(),owner.sourceRecipes(node.recipe().id()),excluded);}))
            return new RequestResult(false,"A recipe used by this craft is blocked by its Recipe Tome",null);
        NetworkAccess jobAccess=new CraftingJobAccess(access,new ItemKey(target));
        Map<ResourceKey,Long> extracted=new LinkedHashMap<>();
        CraftOrigins<ResourceKey,GlobalPos> origins=new CraftOrigins<>();
        try {
            if (!access.payCrafting(plan.nodes().size())) return new RequestResult(false, "Network cannot pay the autocrafting cost", null);
            if (!ledger.reserve(id, plan.reservations(), snapshot)) return new RequestResult(false, "Inventory changed; please retry", null);
            int reservationCalls = 0;
            for (var entry : plan.reservations().entrySet()) {
                long remaining = entry.getValue();
                while (remaining > 0) {
                    long batch = Math.min(remaining, entry.getKey() instanceof ItemKey item ? item.sample().getMaxStackSize() : Integer.MAX_VALUE);
                    if (++reservationCalls > 8192) throw new IllegalStateException("Craft reservation exceeds the transfer budget; request a smaller batch");
                    long taken = access.reserveResource(entry.getKey(), batch, (at, amount) -> origins.add(entry.getKey(), amount, at));
                    if (taken < 0 || taken > batch) throw new IllegalStateException("Invalid resource extraction amount");
                    if (taken > 0) extracted.merge(entry.getKey(), taken, Math::addExact);
                    if (taken == 0) throw new IllegalStateException("Inventory changed while reserving " + entry.getKey());
                    remaining -= taken;
                }
            }
            ledger.release(id); // Physical escrow now owns the claims and cannot be indexed or stolen by another request.
            CraftScheduler<ResourceKey, GlobalPos> scheduler = new CraftScheduler<>(plan, extracted, new CraftScheduler.Processor<>() {
                @Override public boolean supports(GlobalPos position, CraftPlan.Node<ResourceKey> node) {
                    return !isProcessorAliasReserved(position) && node.inputs().keySet().stream().allMatch(key->origins.positions(key).stream().allMatch(from->access.canRoute(from,position))) && owners.get(node.recipe().id()).supports(jobAccess, position, node);
                }
                @Override public CraftScheduler.Operation<ResourceKey> start(GlobalPos position, CraftPlan.Node<ResourceKey> node) {
                    var adapter = owners.get(node.recipe().id());
                    Object identity = adapter.processorIdentity(jobAccess, position);
                    var claimed = new LinkedHashSet<GlobalPos>(); claimed.add(position);
                    claimed.addAll(adapter.claimedPositions(jobAccess, position, node));
                    for (var endpoint : claimed) {
                        var level = access.level().getServer().getLevel(endpoint.dimension());
                        if (level == null || !level.hasChunkAt(endpoint.pos()) || isProcessorAliasReserved(endpoint)) return null;
                    }
                    if (PROCESSOR_IDENTITIES.putIfAbsent(identity, id) != null) return null;
                    for (var endpoint : claimed) PROCESSOR_OWNERS.put(endpoint, id);
                    CLAIM_IDENTITIES.put(position, identity);
                    CLAIM_POSITIONS.put(position, List.copyOf(claimed));
                    CraftScheduler.Operation<ResourceKey> operation;
                    try {
                        claimed.forEach(access::processorChanged);
                        operation = new ResourceCraftDelivery(jobAccess, adapter, position, node, origins, List.copyOf(claimed));
                    }
                    catch (RuntimeException failure) { release(position, id); throw failure; }
                    return new CraftScheduler.Operation<>() {
                        @Override public Map<ResourceKey, Long> poll() {
                            Map<ResourceKey, Long> result = operation.poll();
                            if (result != null) {result.forEach((key,n)->origins.add(key,n,position));release(position, id);}
                            return result;
                        }
                        @Override public Map<ResourceKey, Long> cancel() {
                            try {return operation.cancel();} finally { release(position, id); }
                        }
                        @Override public Map<ResourceKey, Long> recoverable() { return operation.recoverable(); }
                        @Override public boolean mayReturnShortOutput() { return operation.mayReturnShortOutput(); }
                    };
                }
            });
            jobs.put(id, new Job(id, job.owner, target.copyWithCount(1), job.requested, scheduler,origins,job.produced));
            ACTIVE.add(id);
            recovery.put(id, access.origin(), scheduler.recoverySnapshot());
            return new RequestResult(true, "Queued " + count + " " + target.getHoverName().getString() + " (" + plan.nodes().size() + " operations)", id);
        } catch (RuntimeException failure) {
            ledger.release(id);
            Map<ResourceKey, Long> pending = returnItems(extracted,origins);
            if (!pending.isEmpty()) recovery.put(id, access.origin(), pending);
            return new RequestResult(false,failure.getMessage() == null ? "Craft planning failed" : failure.getMessage(),id);
        }
    }
    private RequestResult tryExternal(UUID owner, UUID id, ItemStack target, int count, String fallback) {
        ItemKey key = new ItemKey(target);
        for (CraftingProvider provider : access.craftingProviders()) {
            try {
                if (!provider.valid() || !provider.craftableOutputs().containsKey(key)) continue;
                if (!access.payCrafting(1)) return new RequestResult(false, "Network cannot pay the autocrafting cost", null);
                CraftingContext context = CraftingContext.root().enter("astral_repository:" + access.origin());
                long missing = count;
                CraftingProvider.Ticket ticket = provider.request(key, missing, context);
                if (ticket == null || ticket.state() == CraftingProvider.State.FAILED || ticket.state() == CraftingProvider.State.CANCELLED) continue;
                externalJobs.put(id, new ExternalJob(id, owner, target.copyWithCount(1), count, provider, ticket));
                return new RequestResult(true, "Craft requested from " + provider.id(), id);
            } catch (RuntimeException failure) { LOGGER.warn("External crafting provider {} rejected a request", provider.id(), failure); }
        }
        return new RequestResult(false, fallback, null);
    }
    private void tickExternal() {
        for (ExternalJob job : List.copyOf(externalJobs.values())) {
            try {
                CraftingProvider.State state = job.ticket().state();
                if (state == CraftingProvider.State.RUNNING) continue;
                String message = state == CraftingProvider.State.COMPLETE ? "External craft completed; output remains in its provider storage" : job.ticket().message();
                completeExternal(job, state.name(), message);
            } catch (RuntimeException failure) {
                try { job.ticket().cancel(); } catch (RuntimeException ignored) {}
                completeExternal(job, "FAILED", "External provider unavailable; resources remain with its provider");
                LOGGER.warn("External crafting ticket failed", failure);
            }
        }
    }
    private void completeExternal(ExternalJob job, String state, String message) {
        externalJobs.remove(job.id());
        remember(new JobStatus(job.id(), job.owner(), job.target().copy(), job.count(), state, state.equals("COMPLETE") ? 1 : 0, 1, 0, message));
    }
    private void remember(JobStatus status) {
        long duration=status.state().equals("COMPLETE")||status.state().equals("CANCELLED")?100:400;
        history.addFirst(new Finished(status,access.level().getServer().getTickCount()+duration));
        while(history.size()>16)history.removeLast();
    }    private void release(GlobalPos position, UUID id) {
        if (PROCESSOR_OWNERS.remove(position, id)) {
            Object identity = CLAIM_IDENTITIES.remove(position);
            if (identity != null) PROCESSOR_IDENTITIES.remove(identity, id);
            var claimed = CLAIM_POSITIONS.remove(position);
            if (claimed != null) for (var endpoint : claimed) {
                PROCESSOR_OWNERS.remove(endpoint, id);
                access.processorChanged(endpoint);
            }
        }
        access.processorChanged(position);
    }
    public void tick() {
        com.cappleapple.astralrepository.network.LogisticsTiming.automaticOperation(()->{tickPlanning();return null;});
        for (Job job : new ArrayList<>(jobs.values())) {
            int available = Math.max(job.scheduler().active(), AstralConfig.parallelism.get() - activeProcessors() + job.scheduler().active());
            job.scheduler().tick(access.workstations(), available);
            recovery.put(job.id(), access.origin(), job.scheduler().recoverySnapshot());
            if (job.scheduler().terminal()) finish(job);
        }
        tickExternal();
        if (++ticks % 100 == 0) recoverInterrupted();
    }
    /** User cancellation is authorized by both the active menu and this job owner check. */
    public boolean cancel(UUID id, UUID owner) {
        var pending=planning.get(id);if(pending!=null&&pending.owner.equals(owner))return cancel(id);
        Job job=jobs.get(id);ExternalJob external=externalJobs.get(id);
        return ((job!=null&&job.owner().equals(owner))||(external!=null&&external.owner().equals(owner)))&&cancel(id);
    }
    public boolean cancel(UUID id) {
        var pending=planning.remove(id);if(pending!=null){if(pending.future!=null)pending.future.cancel(true);remember(new JobStatus(pending.id,pending.owner,pending.target,pending.requested,"CANCELLED",0,0,0,""));return true;}
        Job job = jobs.get(id);
        if (job == null) {
            ExternalJob external = externalJobs.get(id);
            if (external == null) return false;
            try { external.ticket().cancel(); } catch (RuntimeException failure) { LOGGER.warn("External cancellation failed", failure); }
            completeExternal(external, "CANCELLED", "External craft cancelled; resources remain with its provider");
            return true;
        }
        job.scheduler().cancel();
        finish(job);
        return true;
    }
    public void cancelAll() { for(UUID id:List.copyOf(planning.keySet()))cancel(id); for (UUID id : List.copyOf(jobs.keySet())) cancel(id); for (UUID id : List.copyOf(externalJobs.keySet())) cancel(id); }
    public List<JobStatus> statuses() {
        List<JobStatus> result = new ArrayList<>();
        planning.values().forEach(job->result.add(job.status()));
        jobs.values().forEach(job -> result.add(status(job)));
        externalJobs.values().forEach(job -> result.add(new JobStatus(job.id(), job.owner(), job.target().copy(), job.count(), "EXTERNAL", 0, 1, 1, job.provider().id())));
        history.forEach(finished -> result.add(finished.status()));
        return List.copyOf(result);
    }
    /** Current jobs plus five-second completions/cancellations and twenty-second failures. */
    public List<JobStatus> visibleStatuses(UUID owner) {
        List<JobStatus> result=new ArrayList<>();
        planning.values().stream().filter(job->job.owner.equals(owner)).forEach(job->result.add(job.status()));
        jobs.values().stream().filter(job->job.owner().equals(owner)).forEach(job->result.add(status(job)));
        externalJobs.values().stream().filter(job->job.owner().equals(owner)).forEach(job->result.add(new JobStatus(job.id(),job.owner(),job.target().copy(),job.count(),"EXTERNAL",0,1,1,job.provider().id())));
        long now=access.level().getServer().getTickCount();
        history.stream().filter(finished->finished.until()>now&&finished.status().owner().equals(owner)).forEach(finished->result.add(finished.status()));
        return List.copyOf(result);
    }
    public Map<GlobalPos, String> processorLabels() {
        Map<GlobalPos, String> result = new LinkedHashMap<>();
        PROCESSOR_OWNERS.forEach((position, id) -> {
            Job job = jobs.get(id);
            if (job != null) result.put(position, "Crafting " + job.count() + " " + job.target().getHoverName().getString()
                    + "\n" + job.scheduler().completed() + "/" + job.scheduler().total() + " operations completed"
                    + "\nRequest " + id.toString().substring(0, 8));
        });
        return Map.copyOf(result);
    }
    public int activeJobs() { return jobs.size() + externalJobs.size() + planning.size(); }
    public int activeProcessors() { return jobs.values().stream().mapToInt(job -> job.scheduler().active()).sum(); }
    private JobStatus status(Job job) {
        var scheduler = job.scheduler();
        String message = scheduler.error();
        if (scheduler.state() == CraftScheduler.State.WAITING) message = "Waiting for an available processor or fuel";
        return new JobStatus(job.id(), job.owner(), job.target().copy(), job.count(), scheduler.state().name(), scheduler.completed(), scheduler.total(), scheduler.active(), message);
    }
    private void finish(Job job) {
        Map<ResourceKey,Long> actual = job.scheduler().drain();
        Map<ResourceKey, Long> pending = returnItems(actual,job.origins());
        jobs.remove(job.id());
        ACTIVE.remove(job.id());
        if (job.scheduler().retryRequired()) {
            // An optimistic chance-output step yielded less than planned. Only actual output
            // counts toward this request; surplus/refunds are real storage before replanning.
            recovery.remove(job.id());
            if (!pending.isEmpty()) recovery.put(UUID.randomUUID(), access.origin(), pending);
            long produced = Math.addExact(job.produced(), job.scheduler().targetCredit(actual));
            if (produced < job.count()) {
                planning.put(job.id(), new Planning(job.id(), job.owner(), job.target(), Math.toIntExact(job.count()-produced), Math.toIntExact(job.count()), produced));
            } else remember(new JobStatus(job.id(),job.owner(),job.target(),job.count(),"COMPLETE",job.scheduler().completed(),job.scheduler().total(),0,""));
            return;
        }
        if (pending.isEmpty()) recovery.remove(job.id()); else recovery.put(job.id(), access.origin(), pending);
        remember(status(job));
    }
    private void recoverInterrupted() {
        recovery.pending(access.origin(), ACTIVE).forEach((id, entry) -> {
            Map<ResourceKey, Long> pending = returnItems(entry.items());
            if (pending.isEmpty()) recovery.remove(id); else recovery.put(id, access.origin(), pending);
        });
    }
    private Map<ResourceKey,Long> returnItems(Map<ResourceKey,Long> items){return returnItems(items,new CraftOrigins<>());}
    private Map<ResourceKey, Long> returnItems(Map<ResourceKey, Long> items,CraftOrigins<ResourceKey,GlobalPos> origins) {
        Map<ResourceKey, Long> pending = new LinkedHashMap<>();
        items.forEach((key, quantity) -> {
            long left = quantity;
            while (left > 0) {
                long amount = Math.min(left, key instanceof ItemKey item ? item.sample().getMaxStackSize() : Integer.MAX_VALUE);
                long uninserted = 0;
                for(var part:origins.take(key,amount,access.origin()).entrySet()){
                    long offered = part.getValue();
                    try {
                        long rest = access.insertResourceFrom(key, offered, part.getKey());
                        if (rest < 0 || rest > offered) throw new IllegalStateException("Invalid resource insertion remainder");
                        uninserted = Math.addExact(uninserted, rest);
                    } catch(RuntimeException failure){LOGGER.error("Craft refund insertion outcome is uncertain; saved without replay",failure);recovery.uncertain(part.getKey(),Map.of(key,offered));}
                }
                if (uninserted > 0) {
                    ServerLevel world = access.level().getServer().getLevel(access.origin().dimension());
                    var position = access.origin().pos();
                    if (!(key instanceof ItemKey item) || world == null || !world.hasChunkAt(position)
                            || !world.addFreshEntity(new ItemEntity(world, position.getX()+.5, position.getY()+1, position.getZ()+.5, item.sample().copyWithCount((int)uninserted))))
                        pending.merge(key, uninserted, Math::addExact);
                }
                left -= amount;
            }
        });
        return Map.copyOf(pending);
    }
}
