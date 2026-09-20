package com.cappleapple.astralrepository.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceTrailTest {
    @Test void shedSpritesFallWithIncreasingDownwardSpeedWhileKeepingTheirReleaseOrigin(){
        var origin=new Vec3(10,5,2);
        var trail=new ResourceTrail(null,origin,new Vec3(.02,0,0),100,16,.05F);
        assertEquals(origin,trail.position(100));
        assertTrue(trail.position(108).y<origin.y);
        assertTrue(trail.position(112).y-trail.position(108).y<trail.position(108).y-trail.position(104).y);
        assertEquals(10.16,trail.position(108).x,1e-8);
        assertEquals(2,trail.position(108).z,1e-8);
    }
    @Test void aFadingBatchReleasesAnEquallyFaintTrailWithoutAnOpaquePop(){
        var trail=new ResourceTrail(null,Vec3.ZERO,Vec3.ZERO,10.25,16,.05F,.2F);
        assertEquals(.2F,trail.opacity(10.25));
        assertEquals(.1F,trail.opacity(18.25));
        assertEquals(0,trail.opacity(26.25));
    }
    @Test void trailFadesAndExpiresIndependentlyOfItsParentFlight(){
        var trail=new ResourceTrail(null,Vec3.ZERO,Vec3.ZERO,10,16,.05F);
        assertEquals(1,trail.opacity(10));assertEquals(.5F,trail.opacity(18));
        assertFalse(trail.expired(25));assertTrue(trail.expired(26));assertEquals(0,trail.opacity(27));
        assertEquals(trail.position(26),trail.position(30));
    }
}
