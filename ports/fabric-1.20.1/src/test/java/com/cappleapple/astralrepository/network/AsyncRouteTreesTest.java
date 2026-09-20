package com.cappleapple.astralrepository.network;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncRouteTreesTest {
    @Test void workersUseCopiedGraphAndRetainShortestRoutes()throws Exception{
        var edges=new LinkedHashMap<String,Double>();edges.put("b",2.0);edges.put("c",10.0);
        var graph=new LinkedHashMap<String,Map<String,Double>>();graph.put("a",edges);graph.put("b",Map.of("c",3.0));graph.put("c",Map.of());
        try(var scheduler=new AsyncRouteTrees<String>(1,4,128)){
            scheduler.reset(graph);edges.clear();graph.clear();
            var tree=await(scheduler,"a");
            assertEquals(List.of("a","b","c"),tree.path(List.of("c"),n->0));
            assertSame(tree,scheduler.request("a"));assertTrue(scheduler.retainedStates()<=128);
            var threadField=AsyncRouteTrees.class.getDeclaredField("lastWorkerThreadId");threadField.setAccessible(true);
            assertNotEquals(Thread.currentThread().getId(),threadField.getLong(scheduler),"Graph traversal ran on a worker, not the owner thread");
            assertTrue(threadField.getLong(scheduler)>0);
        }
    }
    @Test void revisionResetCannotPublishOldQueuedOrCompletedTrees()throws Exception{
        try(var scheduler=new AsyncRouteTrees<String>(1,4,128)){
            scheduler.reset(Map.of("a",Map.of("old",1.0),"old",Map.of()));scheduler.request("a");
            scheduler.reset(Map.of("a",Map.of("new",1.0),"new",Map.of()));
            var tree=await(scheduler,"a");
            assertEquals(List.of(),tree.path(List.of("old"),n->0));
            assertEquals(List.of("a","new"),tree.path(List.of("new"),n->0));
            scheduler.invalidate();assertNull(scheduler.request("a"));assertEquals(0,scheduler.pendingCount());assertEquals(0,scheduler.retainedStates());
        }
    }
    @Test void queuesAndRetainedPredecessorsAreBoundedAndCloseCancelsWork()throws Exception{
        var graph=new LinkedHashMap<Integer,Map<Integer,Double>>();
        for(int n=0;n<64;n++)graph.put(n,Map.of((n+1)%64,1.0));
        var scheduler=new AsyncRouteTrees<Integer>(1,32,128);
        scheduler.reset(graph);
        for(int n=0;n<64;n++)scheduler.request(n);
        assertTrue(scheduler.pendingCount()<=2,"Pending predecessor budget limits roots even below the job-count limit");
        await(scheduler,0);scheduler.drain();assertTrue(scheduler.retainedStates()<=128);
        scheduler.close();assertNull(scheduler.request(0));assertEquals(0,scheduler.pendingCount());assertEquals(0,scheduler.retainedStates());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!scheduler.terminated()&&System.nanoTime()<deadline)Thread.sleep(1);
        assertTrue(scheduler.terminated(),"Stopped server does not retain route workers");
    }
    @Test void schedulerRejectsOffOwnerCalls()throws Exception{
        try(var scheduler=new AsyncRouteTrees<String>(1,4,128)){
            var failure=CompletableFuture.runAsync(()->assertThrows(IllegalStateException.class,()->scheduler.request("a")));
            failure.get(3,TimeUnit.SECONDS);
        }
    }
    private static <T> ShortestPath.Tree<T> await(AsyncRouteTrees<T> scheduler,T root)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(System.nanoTime()<deadline){scheduler.drain();var tree=scheduler.request(root);if(tree!=null)return tree;Thread.sleep(1);}
        fail("Route worker did not complete");return null;
    }
}
