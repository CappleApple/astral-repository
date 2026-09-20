package com.cappleapple.astralrepository.crafting;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AsyncCraftPlannerTest {
    private CraftRecipe<String> chest(){return new CraftRecipe<>("chest","chest",1,"table",Collections.nCopies(8,new CraftRecipe.Ingredient<>(List.of("oak","birch"),1,"minecraft:planks")),0,2);}
    @Test void sixtyFourRepeatsReuseRecipeSearchAndAvoidRecursiveStackGrowth(){
        var planner=new CraftPlanner<>(List.of(chest()));var stock=Map.of("oak",512L);
        var result=planner.planAdditional("chest",64,stock,Set.of("table"));
        assertEquals(64,result.nodes().size());assertEquals(stock,result.reservations());
        int searches=planner.cachedSearches();planner.planAdditional("chest",1,stock,Set.of("table"));assertEquals(searches,planner.cachedSearches());
    }
    @Test void sixtyFourRepeatedNestedRecipesStayWithinStackBounds(){
        var planks=new CraftRecipe<>("oak","oak",4,"table",List.of(CraftRecipe.Ingredient.of("log")),0,1);
        var planner=new CraftPlanner<>(List.of(chest(),planks));var plan=planner.planAdditional("chest",64,Map.of("log",128L),Set.of("table"));
        assertEquals(192,plan.nodes().size());assertEquals(Map.of("log",128L),plan.reservations());
    }
    @Test void workerCachesOnlyIdenticalInventoryAndProcessSnapshots()throws Exception{
        var planner=new AsyncCraftPlanner<>(List.of(chest()));
        var first=planner.submit("chest",64,Map.of("oak",512L),Set.of("table")).get(5,TimeUnit.SECONDS);
        assertNotNull(first.plan());assertSame(first,planner.submit("chest",64,Map.of("oak",512L),Set.of("table")).get(5,TimeUnit.SECONDS));
        var missing=planner.submit("chest",64,Map.of("oak",5L,"birch",3L),Set.of("table")).get(5,TimeUnit.SECONDS);
        assertNull(missing.plan());assertEquals(1,missing.missing().size());assertEquals(504,missing.missing().getFirst().count());assertEquals("minecraft:planks",missing.missing().getFirst().tag());
        var noProcessor=planner.submit("chest",64,Map.of("oak",512L),Set.of()).get(5,TimeUnit.SECONDS);assertNull(noProcessor.plan());assertEquals(3,planner.cachedPlans());
    }
    @Test void multiStackTwelveLayerPlansStayOrderedAndReuseTheWorkerCache()throws Exception{
        var recipes=new ArrayList<CraftRecipe<String>>();var processes=List.of("crafting","smelting","blasting","smoking","stonecutting");
        for(int stage=1;stage<=12;stage++)recipes.add(new CraftRecipe<>("stage_"+stage,"stage_"+stage,1,processes.get((stage-1)%processes.size()),List.of(CraftRecipe.Ingredient.of("stage_"+(stage-1))),0,4));
        var planner=new AsyncCraftPlanner<>(recipes);var stock=Map.of("stage_0",256L);var available=Set.copyOf(processes);
        var first=planner.submit("stage_12",128,stock,available).get(10,TimeUnit.SECONDS);
        assertNotNull(first.plan(),first.error());assertEquals(1536,first.plan().nodes().size());assertEquals(Map.of("stage_0",128L),first.plan().reservations());
        int[] depth=new int[first.plan().nodes().size()];int maximum=0;
        for(var node:first.plan().nodes()){
            int current=1;for(int parent:node.dependencies()){assertTrue(parent<node.id());current=Math.max(current,depth[parent]+1);}depth[node.id()]=current;maximum=Math.max(maximum,current);
        }
        assertEquals(12,maximum);assertSame(first,planner.submit("stage_12",128,stock,available).get(10,TimeUnit.SECONDS));
        assertNull(planner.submit("stage_12",128,stock,Set.of("crafting","smelting","smoking","stonecutting")).get(10,TimeUnit.SECONDS).plan());
    }
    @Test void shortagesIncludeAllInputsAndDeductSharedStock(){
        var frame=new CraftRecipe<>("frame","frame",1,"table",List.of(new CraftRecipe.Ingredient<>(List.of("iron"),2),new CraftRecipe.Ingredient<>(List.of("oak","birch"),3,"minecraft:planks")),0,1);
        var result=new MissingIngredients<>(List.of(frame)).find("frame",4,Map.of("iron",3L,"oak",2L,"birch",3L),Set.of("table"));
        assertEquals(2,result.size());assertEquals(5,result.get(0).count());assertEquals(7,result.get(1).count());assertEquals("minecraft:planks",result.get(1).tag());
    }
    @Test void cancelledQueuedPlansImmediatelyReleaseExecutorCapacity()throws Exception{
        var entered=new java.util.concurrent.CountDownLatch(2);var release=new java.util.concurrent.CountDownLatch(1);
        class Key {
            @Override public int hashCode(){
                if(Thread.currentThread().getName().equals("Astral craft planner")){entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException();}}
                return System.identityHashCode(this);
            }
        }
        var key=new Key();var recipe=new CraftRecipe<>("blocked",key,1,"table",List.of(CraftRecipe.Ingredient.of(key)),0,1);
        var first=new AsyncCraftPlanner<>(List.of(recipe)).submit(key,1,Map.of(key,1L),Set.of("table"));
        var second=new AsyncCraftPlanner<>(List.of(recipe)).submit(key,1,Map.of(key,1L),Set.of("table"));
        var queued=new ArrayList<java.util.concurrent.Future<?>>();
        try{
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var workersField=AsyncCraftPlanner.class.getDeclaredField("WORKERS");workersField.setAccessible(true);var workers=(java.util.concurrent.ThreadPoolExecutor)workersField.get(null);
            int before=workers.getQueue().size();var planner=new AsyncCraftPlanner<>(List.of(chest()));
            for(int i=0;i<16;i++)queued.add(planner.submit("chest",64,Map.of("oak",512L),Set.of("table")));
            assertEquals(before+16,workers.getQueue().size());queued.forEach(future->assertTrue(future.cancel(true)));
            assertEquals(before,workers.getQueue().size(),"Cancelled network jobs cannot keep the bounded planner queue full");
        }finally{queued.forEach(future->future.cancel(true));release.countDown();first.cancel(true);second.cancel(true);}
    }
    @Test void cancellationDoesNotRunOnCallingThread()throws Exception{
        Set<String> names=java.util.concurrent.ConcurrentHashMap.newKeySet();
        class Key {public int hashCode(){names.add(Thread.currentThread().getName());return 1;}}
        var out=new Key();var raw=new Key();var recipe=new CraftRecipe<>("r",out,1,"table",List.of(CraftRecipe.Ingredient.of(raw)),0,1);
        var worker=new AsyncCraftPlanner<>(List.of(recipe));worker.submit(out,1,Map.of(raw,1L),Set.of("table")).get(5,TimeUnit.SECONDS);
        assertTrue(names.stream().anyMatch(n->n.startsWith("Astral craft planner")));
    }
}
