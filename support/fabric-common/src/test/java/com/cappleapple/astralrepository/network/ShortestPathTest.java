package com.cappleapple.astralrepository.network;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShortestPathTest {
    @Test void shortestDistanceCanUseMoreHops(){
        Map<String,Map<String,Double>> graph=Map.of("a",Map.of("b",9.0,"c",2.0),"b",Map.of("z",9.0),"c",Map.of("d",2.0),"d",Map.of("z",2.0),"z",Map.of());
        assertEquals(List.of("a","c","d","z"),ShortestPath.find(Map.of("a",0.0),n->n.equals("z")?0:Double.POSITIVE_INFINITY,n->graph.get(n).keySet(),(a,b)->graph.get(a).get(b)));
    }
    @Test void entryAndExitDistanceCountTowardTheWholeRoute(){
        assertEquals(List.of("near"),ShortestPath.find(Map.of("far",12.0,"near",1.0),n->n.equals("far")?0:2,n->List.<String>of(),(a,b)->0));
    }
    @Test void disconnectedAndCyclicGraphsTerminate(){assertEquals(List.of(),ShortestPath.find(Map.of("a",0.0),n->Double.POSITIVE_INFINITY,n->List.of("a"),(a,b)->0));}
    @Test void reusableTreeMatchesIndependentSearchesAcrossDestinations(){
        Random random=new Random(41817);
        for(int graphIndex=0;graphIndex<40;graphIndex++){
            var graph=new LinkedHashMap<Integer,Map<Integer,Double>>();
            for(int node=0;node<32;node++)graph.put(node,new LinkedHashMap<>());
            for(int a=0;a<32;a++)for(int b=0;b<32;b++)if(a!=b&&random.nextDouble()<.14)graph.get(a).put(b,(double)random.nextInt(9));
            Map<Integer,Double> starts=new LinkedHashMap<>();starts.put(0,3.0);starts.put(1,0.0);starts.put(2,5.0);
            var tree=ShortestPath.tree(starts,n->graph.get(n).keySet(),(a,b)->graph.get(a).get(b));
            for(int query=0;query<40;query++){
                var exits=new LinkedHashMap<Integer,Double>();for(int n=0;n<32;n++)if(random.nextDouble()<.12)exits.put(n,(double)random.nextInt(8));
                java.util.function.ToDoubleFunction<Integer> exit=n->exits.getOrDefault(n,Double.POSITIVE_INFINITY);
                assertEquals(ShortestPath.find(starts,exit,n->graph.get(n).keySet(),(a,b)->graph.get(a).get(b)),tree.path(exits.keySet(),exit));
            }
        }
    }
    @Test void treeKeepsSearchTieOrderAndZeroCostCyclesTerminate(){
        Map<String,Double> starts=new LinkedHashMap<>();starts.put("second",0.0);starts.put("first",0.0);
        var tree=ShortestPath.tree(starts,n->List.of("first","second","end"),(a,b)->0.0);
        assertEquals(List.of("second"),tree.path(List.of("first","second"),n->0));
        assertEquals(List.of("second","end"),tree.path(List.of("end"),n->0));
        assertEquals(3,tree.size());
    }
    @Test void treeRejectsInvalidCostsAndReusesGraphWithoutRetraversal(){
        var expansions=new java.util.concurrent.atomic.AtomicInteger();
        var tree=ShortestPath.tree(Map.of("a",0.0),n->{expansions.incrementAndGet();return n.equals("a")?List.of("bad","b"):List.<String>of();},(a,b)->b.equals("bad")?-1:2);
        for(int i=0;i<10000;i++)assertEquals(List.of("a","b"),tree.path(List.of("b"),n->3));
        assertEquals(2,expansions.get());assertEquals(List.of(),tree.path(List.of("bad"),n->0));
        assertEquals(List.of(),tree.path(List.of("b"),n->Double.NaN));
    }
    @Test void preparedRelayTreesMatchWeightedOriginSearchAcrossManyDestinations(){
        Random random=new Random(786291);
        for(int graphIndex=0;graphIndex<40;graphIndex++){
            var graph=new LinkedHashMap<Integer,Map<Integer,Double>>();
            for(int node=0;node<40;node++)graph.put(node,new LinkedHashMap<>());
            for(int a=0;a<40;a++)for(int b=0;b<40;b++)if(a!=b&&random.nextDouble()<.1)graph.get(a).put(b,(double)random.nextInt(9));
            var starts=new LinkedHashMap<Integer,Double>();starts.put(0,4.0);starts.put(1,0.0);starts.put(2,2.0);
            var prepared=new LinkedHashMap<Integer,ShortestPath.Tree<Integer>>();
            for(var root:starts.keySet())prepared.put(root,ShortestPath.tree(Map.of(root,0.0),n->graph.get(n).keySet(),(a,b)->graph.get(a).get(b)));
            var combined=ShortestPath.combine(starts,prepared);
            for(int query=0;query<40;query++){
                var exits=new LinkedHashMap<Integer,Double>();for(int n=0;n<40;n++)if(random.nextDouble()<.1)exits.put(n,(double)random.nextInt(9));
                java.util.function.ToDoubleFunction<Integer> exit=n->exits.getOrDefault(n,Double.POSITIVE_INFINITY);
                var reference=ShortestPath.find(starts,exit,n->graph.get(n).keySet(),(a,b)->graph.get(a).get(b));
                var actual=combined.path(exits.keySet(),exit);
                assertEquals(routeCost(reference,starts,graph,exits),routeCost(actual,starts,graph,exits));
                assertEquals(actual.size(),new HashSet<>(actual).size(),"Prepared paths contain no cycles");
            }
        }
    }
    private static double routeCost(List<Integer> path,Map<Integer,Double> starts,Map<Integer,Map<Integer,Double>> graph,Map<Integer,Double> exits){
        if(path.isEmpty())return Double.POSITIVE_INFINITY;
        double cost=starts.get(path.getFirst())+exits.get(path.getLast());
        for(int i=1;i<path.size();i++)cost+=graph.get(path.get(i-1)).get(path.get(i));return cost;
    }

}
