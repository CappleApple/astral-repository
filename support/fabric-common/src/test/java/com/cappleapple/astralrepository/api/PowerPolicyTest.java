package com.cappleapple.astralrepository.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.cappleapple.astralrepository.api.PowerPolicy.Component.*;
import static org.junit.jupiter.api.Assertions.*;

class PowerPolicyTest {
    @Test void independentScalingComponentsSumWithoutChargingDisabledCategories() {
        var quantities=Map.of(BASE,1D,NODES,100D,STORAGE_PROVIDERS,50D,INDEXED_CAPACITY,1_000_000D,
                CRAFTING_JOBS,4D,PROCESSORS,8D,DIMENSIONAL_LINKS,2D);
        var rates=Map.of(BASE,2D,NODES,.1D,STORAGE_PROVIDERS,.2D,INDEXED_CAPACITY,0D,
                CRAFTING_JOBS,3D,PROCESSORS,4D,DIMENSIONAL_LINKS,20D);
        assertEquals(106,PowerPolicy.cost(quantities,rates));
        assertEquals(0,PowerPolicy.cost(quantities,Map.of()));
    }
    @Test void operationCostsScaleAmountsDistancesAndComplexitySeparately() {
        var quantities=Map.of(TRANSFERS,1D,ITEMS,64D,FLUID,1000D,ENERGY,5000D,SOURCE,10D,REMOTE_USES,1D,DISTANCE,3000D,COMPLEXITY,15D);
        var rates=Map.of(TRANSFERS,2D,ITEMS,.5D,FLUID,.01D,ENERGY,0D,SOURCE,2D,REMOTE_USES,5D,DISTANCE,.001D,COMPLEXITY,1D);
        assertEquals(87,PowerPolicy.cost(quantities,rates));
    }
    @Test void sharedStorageIdentityDeduplicatesWithinAResourceKindOnly() {
        var totals=PowerPolicy.available(List.of(new PowerPolicy.Source("machine","energy",100),
                new PowerPolicy.Source("machine","energy",100),new PowerPolicy.Source("machine","fluid",200),
                new PowerPolicy.Source("other","energy",50)));
        assertEquals(Map.of("energy",150D,"fluid",200D),totals);
    }
    @Test void orUsesFirstSufficientTypeAndAndRequiresAllTypes() {
        var amounts=Map.of("energy",10D,"source",50D);
        assertEquals(List.of("source"),PowerPolicy.choose(List.of("energy","source"),false,20,amounts).orElseThrow());
        assertTrue(PowerPolicy.choose(List.of("energy","source"),true,20,amounts).isEmpty());
        assertEquals(List.of("energy","source"),PowerPolicy.choose(List.of("energy","source","energy"),true,10,amounts).orElseThrow());
        assertTrue(PowerPolicy.choose(List.of(),false,1,amounts).isEmpty());
        assertTrue(PowerPolicy.choose(List.of(),true,0,amounts).isPresent());
    }
    @Test void invalidAndOverflowingCostsCannotBecomeNegativeOrFree() {
        assertThrows(IllegalArgumentException.class,() -> PowerPolicy.cost(Map.of(BASE,-1D),Map.of(BASE,1D)));
        assertThrows(IllegalArgumentException.class,() -> PowerPolicy.choose(List.of("energy"),false,Double.NaN,Map.of()));
        assertEquals(Double.MAX_VALUE,PowerPolicy.cost(Map.of(ITEMS,Double.MAX_VALUE),Map.of(ITEMS,2D)));
    }
}