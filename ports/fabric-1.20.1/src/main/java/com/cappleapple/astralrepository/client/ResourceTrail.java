package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.NetworkPackets;
import net.minecraft.world.phys.Vec3;

/** Detached cosmetic sprite; time is measured in client world ticks. */
record ResourceTrail(NetworkPackets.Visual packet,Vec3 origin,Vec3 velocity,double started,int lifetime,float radius,float initialOpacity) {
    ResourceTrail(NetworkPackets.Visual packet,Vec3 origin,Vec3 velocity,double started,int lifetime,float radius){
        this(packet,origin,velocity,started,lifetime,radius,1);
    }
    Vec3 position(double now){
        double age=com.cappleapple.astralrepository.platform.Backport.clamp(now-started,0,lifetime);
        return origin.add(velocity.scale(age)).add(0,-.5*.004*age*age,0);
    }
    float opacity(double now){return com.cappleapple.astralrepository.platform.Backport.clamp(initialOpacity,0,1)*(float)com.cappleapple.astralrepository.platform.Backport.clamp(1-(now-started)/lifetime,0,1);}
    boolean expired(long now){return now-started>=lifetime;}
}
