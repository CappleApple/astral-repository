package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.NetworkPackets;
import com.cappleapple.astralrepository.network.TransferVisuals;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** A cosmetic arrival point inside the receiving block, sampled once for each resource flight. */
record ResourceArrival(BlockPos destination,Vec3 endpoint,Vec3 displacement,double finalLegStart) {
    static ResourceArrival create(NetworkPackets.Visual packet,long seed,double variance){
        if(packet.slot()<-4||packet.slot()>-2||packet.from().equals(packet.to())||packet.path().size()<2)return null;
        var random=new Random(seed^0x4b1d0a35df738e29L);
        double z=random.nextDouble()*2-1,angle=random.nextDouble()*Math.PI*2;
        double radius=Math.cbrt(random.nextDouble())*Math.clamp(variance,0,.45),horizontal=Math.sqrt(1-z*z);
        Vec3 offset=new Vec3(horizontal*Math.cos(angle),z,horizontal*Math.sin(angle)).scale(radius);
        Vec3 endpoint=Vec3.atCenterOf(packet.to()).add(offset);
        var path=packet.path();
        double lastLeg=TransferVisuals.legTicks(path.get(path.size()-2),path.getLast());
        return new ResourceArrival(packet.to(),endpoint,endpoint.subtract(TransferVisuals.position(packet,1)),1-lastLeg/TransferVisuals.duration(path));
    }
    Vec3 position(Vec3 originalSplinePoint,double progress){
        double t=Math.clamp((progress-finalLegStart)/(1-finalLegStart),0,1);
        // Zero slope at either end keeps the relay tangent and the rune's inward arrival direction.
        double blend=t*t*(3-2*t);
        return originalSplinePoint.add(displacement.scale(blend));
    }
    float opacity(Vec3 position){
        double x=relativeDistance(position.x,endpoint.x,destination.getX());
        double y=relativeDistance(position.y,endpoint.y,destination.getY());
        double z=relativeDistance(position.z,endpoint.z,destination.getZ());
        double distance=Math.clamp(Math.max(x,Math.max(y,z)),0,1);
        // Every face of the destination cube is fully visible; only its interior fades.
        return (float)(distance*distance*(3-2*distance));
    }
    private static double relativeDistance(double position,double endpoint,int block){
        return position>=endpoint?(position-endpoint)/(block+1-endpoint):(endpoint-position)/(endpoint-block);
    }
}
