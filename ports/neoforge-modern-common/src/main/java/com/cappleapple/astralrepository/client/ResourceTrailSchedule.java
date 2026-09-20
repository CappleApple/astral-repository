package com.cappleapple.astralrepository.client;

import java.util.Random;

/** Staggered sub-tick releases avoid repeated trail positions on otherwise identical routes. */
final class ResourceTrailSchedule {
    private final Random random;
    private double nextEmission;
    ResourceTrailSchedule(double started,long seed){
        random=new Random(seed^0x175f39815e82b4d3L);
        nextEmission=started+random.nextDouble()*2;
    }
    double poll(double now){
        if(now<nextEmission)return Double.NaN;
        double emission=Math.max(now-1,nextEmission);
        nextEmission+=1+random.nextDouble()*2;
        // Do not catch up with a burst after particles were disabled or the client skipped ticks.
        if(nextEmission<=now)nextEmission=now+1+random.nextDouble()*2;
        return emission;
    }
}
