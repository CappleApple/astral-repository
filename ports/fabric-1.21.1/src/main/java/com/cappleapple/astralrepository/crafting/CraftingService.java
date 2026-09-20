package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.api.ItemKey;
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
    private static final Set<UUID> ACTIVE = new HashSet<>();
    public interface NetworkAccess {
        ServerLevel level();
        Map<ItemKey, Long> snapshot();
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
    private record Job(UUID id, UUID owner, ItemStack target, long count, CraftScheduler<ItemKey, GlobalPos> scheduler,CraftOrigins<ItemKey,GlobalPos> origins) {}
    private final NetworkAccess access;
    private final List<ProcessingAdapter> adapters;
    private final ReservationLedger<ItemKey> ledger = new ReservationLedger<>();
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private record ExternalJob(UUID id, UUID owner, ItemStack target, long count, CraftingProvider provider, CraftingProvider.Ticket ticket) {}
    private final Map<UUID, ExternalJob> externalJobs = new LinkedHashMap<>();
    private record Finished(JobStatus status, long until) {}
    private final ArrayDeque<Finished> history = new ArrayDeque<>();
    private final CraftRecoveryData recovery;
    private int ticks;
    private static final class Planning {
        final UUID id,owner;final ItemStack target;final int count;
        java.util.concurrent.Future<AsyncCraftPlanner.Result<ItemKey>> future;
        Map<ItemKey,Long> snapshot=Map.of();Set<String> processes=Set.of();
        AsyncCraftPlanner<ItemKey> catalog;
        Map<String,ProcessingAdapter> owners=Map.of();
        List<MissingIngredients.Missing<ItemKey>> missing=List.of();
        String state="CALCULATING",error="";
        Planning(UUID id,UUID owner,ItemStack target,int count){this.id=id;this.owner=owner;this.target=target.copyWithCount(1);this.count=count;}
        JobStatus status(){return new JobStatus(id,owner,target.copy(),count,state,0,0,0,error,missing);}
    }
    private final Map<UUID,Planning> planning=new LinkedHashMap<>();
    private List<CraftRecipe<ItemKey>> catalogRecipes=List.of();
    private Map<String,ProcessingAdapter> catalogOwners=Map.of();
    private List<?> recipeHolders=List.of(),processingRules=List.of();
    private Set<ItemKey> catalogVariants=Set.of();
    private AsyncCraftPlanner<ItemKey> catalog;
    private record ProcessorProbe(ProcessingAdapter adapter,CraftPlan.Node<ItemKey> node) {}
    private List<ProcessorProbe> processorProbes=List.of();
    private void refreshCatalog(Map<ItemKey,Long> stock){
        var holders=List.copyOf(access.level().getRecipeManager().getRecipes());
        var rules=List.copyOf(ProcessingRules.rules());
        var variants=stock.keySet().stream().filter(k->{var sample=k.sample();return !ItemStack.isSameItemSameComponents(sample,new ItemStack(sample.getItem()));}).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if(catalog!=null&&holders.equals(recipeHolders)&&rules.equals(processingRules)&&variants.equals(catalogVariants))return;
        var recipes=new ArrayList<CraftRecipe<ItemKey>>();var owners=new LinkedHashMap<String,ProcessingAdapter>();
        for(var adapter:adapters)try{for(var recipe:adapter.recipes(access))if(owners.putIfAbsent(recipe.id(),adapter)==null)recipes.add(recipe);}
        catch(RuntimeException failure){LOGGER.warn("Processing adapter unavailable",failure);}
        catalogRecipes=List.copyOf(recipes);catalogOwners=Map.copyOf(owners);catalog=new AsyncCraftPlanner<>(catalogRecipes);
        recipeHolders=holders;processingRules=rules;catalogVariants=variants;
    }
    private Set<String> availableProcesses(){
        var processes=new HashSet<String>();var machines=access.workstations();var seen=new HashSet<String>();var probes=new ArrayList<ProcessorProbe>();
        for(var recipe:catalogRecipes){var adapter=catalogOwners.get(recipe.id());
            if(!seen.add(adapter.getClass().getName()+":"+recipe.process()))continue;
            var probe=new CraftPlan.Node<ItemKey>(0,recipe,List.of(),Set.of());probes.add(new ProcessorProbe(adapter,probe));
            if(machines.stream().anyMatch(at->adapter.supports(access,at,probe)))processes.add(recipe.process());
        }
        processorProbes=List.copyOf(probes);return Set.copyOf(processes);
    }
    /** Craft results return to storage without filling idle machines with unrelated outputs. */
    public boolean isProcessingPosition(GlobalPos position){
        var world=access.level().getServer().getLevel(position.dimension());if(world==null||!world.hasChunkAt(position.pos()))return false;
        if(world.getBlockEntity(position.pos()) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                || com.cappleapple.astralrepository.compat.VisualWorkbenchCompatibility.isCraftingTable(world,position.pos())
                || world.getBlockState(position.pos()).is(net.minecraft.world.level.block.Blocks.STONECUTTER)
                || ProcessingRules.isCandidate(world,position.pos()))return true;
        return processorProbes.stream().anyMatch(probe->probe.adapter().supports(access,position,probe.node()));
    }
    private void calculate(Planning job,Map<ItemKey,Long> stock,Set<String> processes){
        job.snapshot=Map.copyOf(stock);job.processes=processes;job.catalog=catalog;job.owners=catalogOwners;
        job.state="CALCULATING";job.missing=List.of();job.error="";
        try{job.future=catalog.submit(new ItemKey(job.target),job.count,ledger.available(stock),processes);}
        catch(java.util.concurrent.RejectedExecutionException busy){job.future=null;}
    }
    private void tickPlanning(){
        if(planning.isEmpty())return;
        var stock=access.snapshot();refreshCatalog(stock);var processes=availableProcesses();
        for(var job:List.copyOf(planning.values())){
            if(job.future==null){if(job.state.equals("CALCULATING")||job.catalog!=catalog||!job.snapshot.equals(stock)||!job.processes.equals(processes))calculate(job,stock,processes);continue;}
            if(!job.future.isDone())continue;
            try{
                var result=job.future.get();job.future=null;
                var currentStock=stock;
                boolean staleStock=result.plan()==null?!job.snapshot.equals(stock):result.plan().reservations().entrySet().stream().anyMatch(e->currentStock.getOrDefault(e.getKey(),0L)<e.getValue());
                if(job.catalog!=catalog||staleStock||!job.processes.equals(processes)){calculate(job,stock,processes);continue;}
                if(result.plan()==null){
                    var external=tryExternal(job.owner,job.id,job.target,job.count,result.error());
                    if(external.accepted()){planning.remove(job.id);continue;}
                    job.state=result.missing().isEmpty()?"WAITING":"MISSING";job.missing=result.missing();job.error=result.error();continue;
                }
                planning.remove(job.id);
                var accepted=startPlanned(job,result.plan());
                if(!accepted.accepted())remember(new JobStatus(job.id,job.owner,job.target,job.count,"FAILED",0,0,0,accepted.message()));
                stock=access.snapshot();
            }catch(InterruptedException failure){Thread.currentThread().interrupt();return;}
            catch(java.util.concurrent.ExecutionException|RuntimeException failure){planning.remove(job.id);remember(new JobStatus(job.id,job.owner,job.target,job.count,"FAILED",0,0,0,"Craft calculation failed"));LOGGER.warn("Craft calculation failed",failure);}
        }
    }

    public CraftingService(NetworkAccess access) {
        this.access = access;
        this.adapters = ProcessingAdapters.create();
        this.recovery = CraftRecoveryData.get(access.level().getServer());
    }
    static void animate(NetworkAccess access, GlobalPos from, GlobalPos to, ItemStack stack, int ticks) {
        try { access.visual(from, to, stack, com.cappleapple.astralrepository.network.LogisticsTiming.visualTicks(ticks)); } catch (RuntimeException failure) { LOGGER.debug("Cosmetic crafting transport failed", failure); }
    }
    static void animateTable(NetworkAccess access, GlobalPos table, List<ItemStack> grid, ItemStack output, int ticks) {
        try { access.craftingVisual(table, grid, output, com.cappleapple.astralrepository.network.LogisticsTiming.visualTicks(ticks)); } catch (RuntimeException failure) { LOGGER.debug("Cosmetic table animation failed", failure); }
    }
    public static boolean isProcessorReserved(GlobalPos position) { return PROCESSOR_OWNERS.containsKey(position); }
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
    private RequestResult startPlanned(Planning job,CraftPlan<ItemKey> plan){
        UUID id=job.id;ItemStack target=job.target;int count=job.count;
        var owners=job.owners;var snapshot=access.snapshot();
        Map<ItemKey,Long> extracted=new LinkedHashMap<>();
        CraftOrigins<ItemKey,GlobalPos> origins=new CraftOrigins<>();
        try {
            if (!access.payCrafting(plan.nodes().size())) return new RequestResult(false, "Network cannot pay the autocrafting cost", null);
            if (!ledger.reserve(id, plan.reservations(), snapshot)) return new RequestResult(false, "Inventory changed; please retry", null);
            for (var entry : plan.reservations().entrySet()) {
                long remaining = entry.getValue();
                while (remaining > 0) {
                    int batch = (int)Math.min(remaining, entry.getKey().sample().getMaxStackSize());
                    ItemStack stack = access.reserve(entry.getKey(), batch,(at,part)->origins.add(new ItemKey(part),part.getCount(),at));
                    if (!stack.isEmpty()) VanillaProcessingAdapter.add(extracted, stack);
                    if (stack.isEmpty() || !new ItemKey(stack).equals(entry.getKey()) || stack.getCount() != batch)
                        throw new IllegalStateException("Inventory changed while reserving " + entry.getKey());
                    remaining -= stack.getCount();
                }
            }
            ledger.release(id); // Physical escrow now owns the claims and cannot be indexed or stolen by another request.
            CraftScheduler<ItemKey, GlobalPos> scheduler = new CraftScheduler<>(plan, extracted, new CraftScheduler.Processor<>() {
                @Override public boolean supports(GlobalPos position, CraftPlan.Node<ItemKey> node) {
                    return !isProcessorReserved(position) && node.inputs().keySet().stream().allMatch(key->origins.positions(key).stream().allMatch(from->access.canRoute(from,position))) && owners.get(node.recipe().id()).supports(access, position, node);
                }
                @Override public CraftScheduler.Operation<ItemKey> start(GlobalPos position, CraftPlan.Node<ItemKey> node) {
                    if (PROCESSOR_OWNERS.putIfAbsent(position, id) != null) return null;
                    access.processorChanged(position);
                    CraftScheduler.Operation<ItemKey> operation;
                    try { operation = new CraftDelivery(access,owners.get(node.recipe().id()),position,node,origins); }
                    catch (RuntimeException failure) { release(position, id); throw failure; }
                    return new CraftScheduler.Operation<>() {
                        @Override public Map<ItemKey, Long> poll() {
                            Map<ItemKey, Long> result = operation.poll();
                            if (result != null) {result.forEach((key,n)->origins.add(key,n,position));release(position, id);}
                            return result;
                        }
                        @Override public Map<ItemKey, Long> cancel() {
                            try {return operation.cancel();} finally { release(position, id); }
                        }
                        @Override public Map<ItemKey, Long> recoverable() { return operation.recoverable(); }
                    };
                }
            });
            jobs.put(id, new Job(id, job.owner, target.copyWithCount(1), count, scheduler,origins));
            ACTIVE.add(id);
            recovery.put(id, access.origin(), scheduler.recoverySnapshot());
            return new RequestResult(true, "Queued " + count + " " + target.getHoverName().getString() + " (" + plan.nodes().size() + " operations)", id);
        } catch (RuntimeException failure) {
            ledger.release(id);
            Map<ItemKey, Long> pending = returnItems(extracted,origins);
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
        PROCESSOR_OWNERS.remove(position, id);
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
        var pending=planning.remove(id);if(pending!=null){if(pending.future!=null)pending.future.cancel(true);remember(new JobStatus(pending.id,pending.owner,pending.target,pending.count,"CANCELLED",0,0,0,""));return true;}
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
        Map<ItemKey, Long> pending = returnItems(job.scheduler().drain(),job.origins());
        if (pending.isEmpty()) recovery.remove(job.id()); else recovery.put(job.id(), access.origin(), pending);
        jobs.remove(job.id());
        ACTIVE.remove(job.id());
        remember(status(job));
    }
    private void recoverInterrupted() {
        recovery.pending(access.origin(), ACTIVE).forEach((id, entry) -> {
            Map<ItemKey, Long> pending = returnItems(entry.items());
            if (pending.isEmpty()) recovery.remove(id); else recovery.put(id, access.origin(), pending);
        });
    }
    private Map<ItemKey,Long> returnItems(Map<ItemKey,Long> items){return returnItems(items,new CraftOrigins<>());}
    private Map<ItemKey, Long> returnItems(Map<ItemKey, Long> items,CraftOrigins<ItemKey,GlobalPos> origins) {
        Map<ItemKey, Long> pending = new LinkedHashMap<>();
        items.forEach((key, quantity) -> {
            long left = quantity;
            while (left > 0) {
                int amount = (int)Math.min(left, key.sample().getMaxStackSize());
                ItemStack stack = key.sample().copyWithCount(amount);
                ItemStack remainder=ItemStack.EMPTY;
                for(var part:origins.take(key,amount,access.origin()).entrySet()){
                    int offer=Math.toIntExact(part.getValue());ItemStack rest;
                    try {rest=access.insertFrom(stack.copyWithCount(offer),part.getKey());}
                    catch(RuntimeException failure){LOGGER.error("Craft refund insertion outcome is uncertain; saved without replay",failure);recovery.uncertain(part.getKey(),Map.of(key,(long)offer));continue;}
                    if(!rest.isEmpty()){if(remainder.isEmpty())remainder=rest.copy();else remainder.grow(rest.getCount());}
                }
                if (!remainder.isEmpty()) {
                    ServerLevel originLevel = access.level().getServer().getLevel(access.origin().dimension());
                    var origin = access.origin().pos();
                    if (originLevel == null || !originLevel.hasChunkAt(origin) || !originLevel.addFreshEntity(new ItemEntity(originLevel, origin.getX() + .5, origin.getY() + 1, origin.getZ() + .5, remainder.copy())))
                        pending.merge(new ItemKey(remainder), (long)remainder.getCount(), Math::addExact);
                }
                left -= amount;
            }
        });
        return Map.copyOf(pending);
    }
}
