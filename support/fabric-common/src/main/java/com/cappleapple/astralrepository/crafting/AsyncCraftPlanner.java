package com.cappleapple.astralrepository.crafting;

import java.util.*;
import java.util.concurrent.*;

/** Shared bounded workers; callers provide value snapshots, never worlds or capabilities. */
public final class AsyncCraftPlanner<K> {
    private static final ThreadPoolExecutor WORKERS=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"Astral craft planner");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    static {WORKERS.allowCoreThreadTimeOut(true);}
    public record Result<K>(CraftPlan<K> plan,List<MissingIngredients.Missing<K>> missing,String error){}
    private record Key<K>(K target,long count,Map<K,Long> stock,Set<String> processes){}
    private final List<CraftRecipe<K>> recipes;
    private CraftPlanner<K> planner;
    private MissingIngredients<K> shortages;
    private final Map<Key<K>,Result<K>> cache=new LinkedHashMap<>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<Key<K>,Result<K>> e){return size()>32;}};
    public AsyncCraftPlanner(Collection<CraftRecipe<K>> recipes){this.recipes=List.copyOf(recipes);}
    public Future<Result<K>> submit(K target,long count,Map<K,Long> stock,Set<String> processes){
        var key=new Key<>(target,count,Map.copyOf(stock),Set.copyOf(processes));
        var task=new FutureTask<Result<K>>(() -> calculate(key)){
            @Override protected void done(){if(isCancelled())WORKERS.remove(this);}
        };
        WORKERS.execute(task);return task;
    }
    private synchronized Result<K> calculate(Key<K> key){
        if(planner==null){planner=new CraftPlanner<>(recipes);shortages=new MissingIngredients<>(recipes);}
        var cached=cache.get(key);if(cached!=null)return cached;
        Result<K> result;
        try{result=new Result<>(planner.planAdditional(key.target,key.count,key.stock,key.processes),List.of(),"");}
        catch(CraftPlanner.PlanningException failure){result=new Result<>(null,shortages.find(key.target,key.count,key.stock,key.processes),failure.getMessage());}
        cache.put(key,result);return result;
    }
    public synchronized int cachedPlans(){return cache.size();}
}
