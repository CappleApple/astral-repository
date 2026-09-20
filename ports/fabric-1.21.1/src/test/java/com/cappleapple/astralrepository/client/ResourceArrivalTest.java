package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.NetworkPackets;
import com.cappleapple.astralrepository.network.TransferVisuals;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceArrivalTest {
    private static NetworkPackets.Visual packet(List<BlockPos> path,int slot,TransferVisuals.Endpoint arrival){
        return new NetworkPackets.Visual(path.getFirst(),path.getLast(),ItemStack.EMPTY,0xffffff,TransferVisuals.duration(path),slot,path,null,arrival);
    }
    @Test void stableSeededLandingsStayInsideTheDestinationWithConfigurableSpread(){
        var path=List.of(new BlockPos(0,8,0),new BlockPos(12,8,0));
        for(int slot=-4;slot<=-2;slot++){
            var packet=packet(path,slot,null);
            assertEquals(path.getLast().getCenter(),ResourceArrival.create(packet,7,0).endpoint());
            assertEquals(ResourceArrival.create(packet,7,.18),ResourceArrival.create(packet,7,.18));
            assertNotEquals(ResourceArrival.create(packet,7,.18).endpoint(),ResourceArrival.create(packet,8,.18).endpoint());
            for(long seed=0;seed<100;seed++){
                var arrival=ResourceArrival.create(packet,seed,.45);
                assertTrue(arrival.endpoint().distanceTo(path.getLast().getCenter())<=.45);
                assertEquals(0,arrival.opacity(arrival.endpoint()));
                assertEquals(0,arrival.position(TransferVisuals.position(packet,1),1).distanceTo(arrival.endpoint()),1e-10);
            }
        }
        assertNull(ResourceArrival.create(packet(path,-1,null),7,.18));
        assertNull(ResourceArrival.create(packet(List.of(BlockPos.ZERO,BlockPos.ZERO),-2,null),7,.18));
    }
    @Test void opacityStartsAtTheBlockBoundaryRegardlessOfFlightDurationOrDirection(){
        var path=List.of(new BlockPos(-400,0,0),BlockPos.ZERO);
        var arrival=ResourceArrival.create(packet(path,-2,null),2,.18);
        for(var face:Direction.values()){
            var normal=Vec3.atLowerCornerOf(face.getNormal());
            var boundary=BlockPos.ZERO.getCenter().add(normal.scale(.5));
            assertEquals(1,arrival.opacity(boundary));
            assertEquals(1,arrival.opacity(boundary.add(normal.scale(10))));
            float previous=1;
            for(int i=1;i<=100;i++){
                Vec3 point=boundary.lerp(arrival.endpoint(),i/100.0);
                float opacity=arrival.opacity(point);
                assertTrue(opacity<=previous+1e-6&&opacity>=0);
                previous=opacity;
            }
            assertEquals(0,previous,1e-10);
        }
    }
    @Test void finalLegAdjustmentKeepsRelayContinuityAndRuneArrivalDirection(){
        var path=List.of(BlockPos.ZERO,new BlockPos(8,0,0),new BlockPos(8,0,16));
        double relay=1.0/3,h=1e-6;
        for(var face:Direction.values()){
            var normal=Vec3.atLowerCornerOf(face.getNormal());
            var packet=packet(path,-2,new TransferVisuals.Endpoint(normal.scale(.502),face));
            var arrival=ResourceArrival.create(packet,3,.18);
            assertEquals(TransferVisuals.position(packet,0),arrival.position(TransferVisuals.position(packet,0),0));
            assertEquals(TransferVisuals.position(packet,relay),arrival.position(TransferVisuals.position(packet,relay),relay));
            var at=arrival.position(TransferVisuals.position(packet,relay),relay);
            var before=at.subtract(arrival.position(TransferVisuals.position(packet,relay-h),relay-h)).scale(1/h);
            var after=arrival.position(TransferVisuals.position(packet,relay+h),relay+h).subtract(at).scale(1/h);
            assertTrue(before.distanceTo(after)<.005);
            var finish=arrival.position(TransferVisuals.position(packet,1),1);
            var late=arrival.position(TransferVisuals.position(packet,1-h),1-h);
            // Normalize velocity, not the tiny displacement: Vec3.normalize returns ZERO below 1e-4 blocks.
            var velocity=finish.subtract(late).scale(1/h);
            var originalVelocity=TransferVisuals.position(packet,1).subtract(TransferVisuals.position(packet,1-h)).scale(1/h);
            assertTrue(velocity.length()>1,"Arrival retains nonzero motion on "+face);
            assertTrue(velocity.distanceTo(originalVelocity)<.001,"Arrival preserves the original velocity on "+face);
            assertTrue(velocity.normalize().dot(normal)<-.999,"Arrival follows the inward rune normal on "+face);
            assertEquals(0,arrival.opacity(finish),1e-10);
        }
    }
    @Test void longCurvedFlightKeepsFullOpacityUntilItActuallyEntersTheDestination(){
        var path=List.of(BlockPos.ZERO,new BlockPos(15,0,0),new BlockPos(15,4,80));
        var packet=packet(path,-3,null);
        var arrival=ResourceArrival.create(packet,19,.18);
        boolean entered=false;
        for(int i=0;i<=1000;i++){
            double t=i/1000.0;
            var point=arrival.position(TransferVisuals.variedPosition(packet,t,19,2),t);
            var target=path.getLast();
            boolean inside=point.x>target.getX()&&point.x<target.getX()+1&&point.y>target.getY()&&point.y<target.getY()+1&&point.z>target.getZ()&&point.z<target.getZ()+1;
            if(!inside)assertEquals(1,arrival.opacity(point));
            else{entered=true;assertTrue(arrival.opacity(point)<1);}
        }
        assertTrue(entered);
    }
}
