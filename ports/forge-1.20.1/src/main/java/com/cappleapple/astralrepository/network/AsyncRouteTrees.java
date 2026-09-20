package com.cappleapple.astralrepository.network;

import java.util.*;
import java.util.concurrent.*;

/** Owner-thread scheduler. Workers receive only immutable node IDs, adjacency maps, and numeric costs. */
final class AsyncRouteTrees<T> implements AutoCloseable {
    private record Computed<T>(ShortestPath.Tree<T> tree,long workerThreadId){}
    private record Pending<T>(long revision,FutureTask<Computed<T>> future){}
    private final Thread owner=Thread.currentThread();
    private final ThreadPoolExecutor workers;
    private final int pendingLimit,stateLimit;
    private final Map<T,Pending<T>> pending=new HashMap<>();
    private final WeightedLruCache<T,ShortestPath.Tree<T>> ready;
    private Map<T,Map<T,Double>> graph=Map.of();
    private long revision;
    private long submitted,completed,cancelled,lastWorkerThreadId;
    private boolean closed;

    AsyncRouteTrees(){this(2,64,262144);}
    AsyncRouteTrees(int threads,int pendingLimit,int stateLimit){
        if(threads<1||pendingLimit<1)throw new IllegalArgumentException("Positive worker limits required");
        this.pendingLimit=pendingLimit;this.stateLimit=stateLimit;this.ready=new WeightedLruCache<>(8192,stateLimit,ShortestPath.Tree::size);
        workers=new ThreadPoolExecutor(threads,threads,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(pendingLimit),r->{
            var thread=new Thread(r,"Astral route planner");thread.setDaemon(true);return thread;
        },new ThreadPoolExecutor.AbortPolicy());
        workers.allowCoreThreadTimeOut(true);
    }
    private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Route scheduler accessed off its owner thread");}
    void reset(Map<T,? extends Map<T,Double>> snapshot){
        owner();invalidate();if(closed)return;
        var copy=new LinkedHashMap<T,Map<T,Double>>();
        snapshot.forEach((node,edges)->copy.put(node,Collections.unmodifiableMap(new LinkedHashMap<>(edges))));
        graph=Collections.unmodifiableMap(copy);
    }
    void invalidate(){
        owner();revision++;graph=Map.of();ready.clear();
        for(var task:pending.values())if(task.future.cancel(true))cancelled++;
        pending.clear();workers.purge();
    }
    /** Returns a finished immutable tree, or schedules bounded preparation and immediately returns null. */
    ShortestPath.Tree<T> request(T root){
        owner();if(closed)return null;
        var cached=ready.get(root);if(cached!=null)return cached;
        var task=pending.get(root);
        if(task!=null){
            if(task.future.isDone()){pending.remove(root);return publish(root,task);}
            return null;
        }
        // Bound queued/completed tree state as well as job count; large snapshots admit fewer roots.
        int allowed=Math.min(pendingLimit,stateLimit/Math.max(1,graph.size()));
        if(!graph.containsKey(root)||pending.size()>=allowed)return null;
        var snapshot=graph;
        var future=new FutureTask<>(()->new Computed<>(ShortestPath.tree(Map.of(root,0.0),node->{
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            return snapshot.getOrDefault(node,Map.of()).keySet();
        },(a,b)->snapshot.getOrDefault(a,Map.of()).getOrDefault(b,Double.POSITIVE_INFINITY)),Thread.currentThread().getId()));
        task=new Pending<>(revision,future);pending.put(root,task);
        try{workers.execute(future);submitted++;}catch(RejectedExecutionException full){pending.remove(root);future.cancel(false);}
        return null;
    }
    /** Only completed futures are read; server ticks never wait for route workers. */
    void drain(){
        owner();var iterator=pending.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();if(!entry.getValue().future.isDone())continue;
            iterator.remove();publish(entry.getKey(),entry.getValue());
        }
    }
    private ShortestPath.Tree<T> publish(T root,Pending<T> task){
        if(closed||task.revision!=revision||task.future.isCancelled())return null;
        try{var result=task.future.get();ready.put(root,result.tree);completed++;lastWorkerThreadId=result.workerThreadId;return result.tree;}
        catch(CancellationException|ExecutionException failure){return null;}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return null;}
    }
    int pendingCount(){owner();return pending.size();}
    int retainedStates(){owner();return ready.weight();}
    boolean terminated(){return workers.isTerminated();}
    @Override public void close(){owner();closed=true;invalidate();workers.shutdownNow();}
}
