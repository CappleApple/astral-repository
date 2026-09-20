package com.cappleapple.astralrepository.network;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedPathCacheTest {
    @Test void tenThousandRecurringRoutesRemainWarm(){
        var cache=new BoundedPathCache<Integer,Integer>(16384,262144);
        for(int i=0;i<10000;i++)cache.put(i,List.of(i,i+1,i+2));
        for(int round=0;round<3;round++)for(int i=0;i<10000;i++)assertEquals(List.of(i,i+1,i+2),cache.get(i));
        assertEquals(30000,cache.points());
    }
    @Test void pathPointLimitEvictsLeastRecentlyUsedRoute(){
        var cache=new BoundedPathCache<String,Integer>(10,6);
        cache.put("a",List.of(1,2));cache.put("b",List.of(3,4));cache.put("c",List.of(5,6));
        cache.get("a");cache.put("d",List.of(7,8));
        assertNull(cache.get("b"));assertNotNull(cache.get("a"));assertEquals(6,cache.points());
    }
    @Test void negativeRoutesAndReplacementStillRespectBothLimits(){
        var cache=new BoundedPathCache<String,Integer>(2,4);
        cache.put("a",List.of());cache.put("b",List.of());cache.put("c",List.of());
        assertNull(cache.get("a"));assertEquals(2,cache.points());
        cache.put("b",List.of(1,2,3,4));assertNull(cache.get("c"));assertEquals(4,cache.points());
        cache.put("b",List.of(1,2,3,4,5));assertNull(cache.get("b"));assertEquals(0,cache.points());
        cache.put("d",List.of(1));cache.clear();assertEquals(0,cache.points());assertEquals(0,cache.size());
    }
    @Test void callerMutationCannotChangeCachedRouteOrWeight(){
        var cache=new BoundedPathCache<String,Integer>(2,4);var path=new ArrayList<>(List.of(1,2));
        cache.put("a",path);path.clear();assertEquals(List.of(1,2),cache.get("a"));assertEquals(2,cache.points());
        assertThrows(UnsupportedOperationException.class,()->cache.get("a").clear());
    }
}
