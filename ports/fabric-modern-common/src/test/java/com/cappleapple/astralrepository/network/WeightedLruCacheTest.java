package com.cappleapple.astralrepository.network;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeightedLruCacheTest {
    @Test void originAndRelayStateBudgetsAreIndependent(){
        var cache=new WeightedLruCache<String,List<Integer>>(2,5,List::size);
        cache.put("a",List.of(1,2));cache.put("b",List.of(1,2));cache.get("a");cache.put("c",List.of(1));
        assertNull(cache.get("b"));assertEquals(2,cache.size());assertEquals(3,cache.weight());
        cache.put("d",List.of(1,2,3,4,5));assertNull(cache.get("a"));assertNull(cache.get("c"));assertEquals(1,cache.size());
        cache.put("d",List.of(1,2,3,4,5,6));assertEquals(0,cache.size());assertEquals(0,cache.weight());
    }
    @Test void emptyTreesStillConsumeAnEntryAndClearReleasesWeight(){
        var cache=new WeightedLruCache<Integer,List<Integer>>(512,262144,List::size);
        for(int i=0;i<10000;i++)cache.put(i,List.of());
        assertEquals(512,cache.size());assertEquals(512,cache.weight());cache.clear();assertEquals(0,cache.size());assertEquals(0,cache.weight());
    }
}
