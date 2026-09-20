package com.cappleapple.astralrepository.network;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NetworkLogicTest {
    private NetworkTopology.Node node(String id,String dimension,int x,int channel,boolean remote,boolean attuned){return new NetworkTopology.Node(id,dimension,x,0,0,channel,remote,attuned);}
    @Test void nearbySameChannelAnchorsStaySeparateWithoutAnExplicitLink(){
        var nodes=List.of(node("a","overworld",0,11,false,false),node("b","overworld",2,11,false,false));
        assertEquals(2,NetworkTopology.components(nodes,List.of(),16,128).size());
        assertEquals(1,NetworkTopology.components(nodes,List.of(new NetworkTopology.Edge("a","b")),16,128).size());
    }
    @Test void explicitLinksStillRespectChannelsRangeAndDimensionalAttunement(){
        var a=node("a","overworld",0,3,true,true);var b=node("b","nether",1000,3,true,false);var edge=List.of(new NetworkTopology.Edge("a","b"));
        assertEquals(2,NetworkTopology.components(List.of(a,b),edge,16,128).size());
        assertEquals(1,NetworkTopology.components(List.of(a,node("b","nether",1000,3,true,true)),edge,16,128).size());
        assertEquals(2,NetworkTopology.components(List.of(a,node("b","nether",1000,4,true,true)),edge,16,128).size());
        assertFalse(NetworkTopology.allowed(a,node("b","overworld",129,3,true,true),16,128));
        assertFalse(NetworkTopology.allowed(a,node("b","overworld",17,3,false,true),16,128));
    }
    @Test void handheldGateDoesNotBypassRangeOrBridge(){assertTrue(RemoteRules.access(true,false,100,16));assertFalse(RemoteRules.access(true,true,1000,16));assertFalse(RemoteRules.access(false,false,0,16));assertTrue(RemoteRules.access(false,true,0,16));assertFalse(RemoteRules.bridge(false,true,false,true));}
    @Test void incrementalIndexRemovesOldCountsAndLocations(){var index=new NetworkInventoryIndex<String,String>();index.update("chest",Map.of("iron",64L));index.update("barrel",Map.of("iron",32L,"copper",5L));assertEquals(96,index.count("iron"));index.update("chest",Map.of("gold",4L));assertEquals(32,index.count("iron"));assertEquals(List.of("barrel"),index.locations("iron"));index.remove("barrel");assertEquals(0,index.count("iron"));assertEquals(Map.of("gold",4L),index.snapshot());}
    @Test void unchangedReconciliationDoesNotInvalidatePages(){var index=new NetworkInventoryIndex<String,String>();index.update("a",Map.of("iron",1L));long version=index.version();index.update("a",Map.of("iron",1L));assertEquals(version,index.version());}
    @Test void largeSyntheticIndexHasNoProviderAccessDuringQueries(){var index=new NetworkInventoryIndex<Integer,Integer>();for(int p=0;p<2000;p++){Map<Integer,Long> items=new HashMap<>();for(int k=0;k<100;k++)items.put(p*100+k,64L);index.update(p,items);}assertEquals(200000,index.size());var snapshot=index.snapshot();for(int k=0;k<200000;k++)assertEquals(64L,snapshot.get(k));for(int p=0;p<2000;p+=2)index.remove(p);assertEquals(100000,index.size());assertEquals(0,index.count(0));assertEquals(64,index.count(100));}
    @Test void explicitGraphJoinsLongRelayChainsWithoutSpatialEdges(){
        List<NetworkTopology.Node> nodes=new ArrayList<>();List<NetworkTopology.Edge> edges=new ArrayList<>();
        for(int i=0;i<10000;i++){nodes.add(node("n"+i,"overworld",i*12,-1,false,false));if(i>0)edges.add(new NetworkTopology.Edge("n"+(i-1),"n"+i));}
        assertEquals(1,NetworkTopology.components(nodes,edges,16,128).size());
        edges.remove(4999);assertEquals(2,NetworkTopology.components(nodes,edges,16,128).size());
    }
    @Test void committedDeltasPreserveOtherKeysAndReconcileExactly() {
        var index=new NetworkInventoryIndex<Integer,Integer>();
        var expected=new HashMap<Integer,Map<Integer,Long>>();var random=new Random(7291);
        for(int i=0;i<20000;i++){
            int provider=random.nextInt(120),key=random.nextInt(80);long delta=random.nextInt(129)-64;
            var items=expected.computeIfAbsent(provider,ignored->new HashMap<>());
            long next=Math.max(0,items.getOrDefault(key,0L)+delta);
            if(next==0)items.remove(key);else items.put(key,next);
            index.adjust(provider,key,delta);
            assertEquals(items,index.provider(provider));
        }
        var totals=new HashMap<Integer,Long>();
        expected.values().forEach(items->items.forEach((key,value)->totals.merge(key,value,Long::sum)));
        assertEquals(totals,index.snapshot());
        for(var entry:totals.entrySet())assertEquals(expected.entrySet().stream().filter(e->e.getValue().containsKey(entry.getKey())).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()),new HashSet<>(index.locations(entry.getKey())));
        long revision=index.version();index.adjust(1000,999,-64);assertEquals(revision,index.version());
    }
    @Test void committedDeltasHandleOverflowAndRemoveOnlyTheRequestedKey() {
        var index=new NetworkInventoryIndex<String,String>();
        index.adjust("a","iron",Long.MAX_VALUE);index.adjust("b","iron",100);
        index.adjust("a","copper",8);index.adjust("a","iron",-50);
        assertEquals(Long.MAX_VALUE,index.count("iron"));
        index.adjust("a","iron",Long.MIN_VALUE);
        assertEquals(100,index.count("iron"));assertEquals(8,index.count("copper"));
        assertEquals(List.of("b"),index.locations("iron"));
    }
    @Test void saturatedReconciliationAndProviderRemovalRecoverRemainingCounts() {
        var index=new NetworkInventoryIndex<String,String>();
        index.update("a",Map.of("iron",Long.MAX_VALUE));index.update("b",Map.of("iron",100L));
        index.update("a",Map.of("iron",Long.MAX_VALUE-50));assertEquals(Long.MAX_VALUE,index.count("iron"));
        index.remove("a");assertEquals(100,index.count("iron"));assertEquals(List.of("b"),index.locations("iron"));
    }
}