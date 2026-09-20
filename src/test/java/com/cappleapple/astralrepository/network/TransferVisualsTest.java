package com.cappleapple.astralrepository.network;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TransferVisualsTest {
    @Test void randomBatchesKeepEndpointsAndContinuousVelocityWithoutRelayPinching(){
        var path=List.of(BlockPos.ZERO,new BlockPos(8,0,0),new BlockPos(8,0,16));
        var packet=new NetworkPackets.Visual(path.get(0),path.get(path.size()-1),net.minecraft.world.item.ItemStack.EMPTY,0xffffff,60,-2,path);
        double relay=1.0/3,h=1e-6;
        for(long seed=0;seed<30;seed++){
            assertEquals(TransferVisuals.position(packet,0),TransferVisuals.variedPosition(packet,0,seed,2));
            assertEquals(0,TransferVisuals.position(packet,1).distanceTo(TransferVisuals.variedPosition(packet,1,seed,2)),1e-8);
            var point=TransferVisuals.variedPosition(packet,relay,seed,2);
            assertTrue(point.distanceTo(TransferVisuals.position(packet,relay))>0.001);
            assertTrue(point.distanceTo(TransferVisuals.position(packet,relay))<=2.000001);
            var before=point.subtract(TransferVisuals.variedPosition(packet,relay-h,seed,2)).scale(1/h);
            var after=TransferVisuals.variedPosition(packet,relay+h,seed,2).subtract(point).scale(1/h);assertTrue(before.distanceTo(after)<.005);
        }
        assertNotEquals(TransferVisuals.variedPosition(packet,.15,1,.2),TransferVisuals.variedPosition(packet,.15,2,.2));
        assertEquals(TransferVisuals.position(packet,.15),TransferVisuals.variedPosition(packet,.15,2,0));
    }
    @Test void relaySpreadMatchesMidflightSpreadAndNeverExceedsConfiguredVariation(){
        var path=List.of(BlockPos.ZERO,new BlockPos(8,0,0),new BlockPos(8,0,16));
        var packet=new NetworkPackets.Visual(path.get(0),path.get(path.size()-1),net.minecraft.world.item.ItemStack.EMPTY,0xffffff,60,-1,path);
        double relayEnergy=0,midEnergy=0;
        for(long seed=0;seed<200;seed++){
            relayEnergy+=TransferVisuals.variedPosition(packet,1.0/3,seed,.2).distanceToSqr(TransferVisuals.position(packet,1.0/3));
            midEnergy+=TransferVisuals.variedPosition(packet,1.0/6,seed,.2).distanceToSqr(TransferVisuals.position(packet,1.0/6));
            for(int i=0;i<=60;i++){
                double t=i/60.0;
                assertTrue(TransferVisuals.variedPosition(packet,t,seed,.2).distanceTo(TransferVisuals.position(packet,t))<=.200001);
            }
        }
        assertTrue(relayEnergy/midEnergy>.8&&relayEnergy/midEnergy<1.2,"Relay spread must not narrow relative to the open leg");
    }
    @Test void variedPathsKeepRuneDepartureDirectionAndHandleRepeatedNodes(){
        var path=List.of(BlockPos.ZERO,BlockPos.ZERO,new BlockPos(4,1,0));
        var normal=new net.minecraft.world.phys.Vec3(0,1,0);
        var endpoint=new TransferVisuals.Endpoint(normal.scale(.502),net.minecraft.core.Direction.UP);
        var packet=new NetworkPackets.Visual(path.get(0),path.get(path.size()-1),net.minecraft.world.item.ItemStack.EMPTY,0xffffff,40,-1,path,endpoint,endpoint,null);
        for(int i=0;i<=100;i++)assertTrue(Double.isFinite(TransferVisuals.variedPosition(packet,i/100.0,7,2).lengthSqr()));
        var start=TransferVisuals.variedPosition(packet,0,7,2);
        assertTrue(TransferVisuals.variedPosition(packet,1e-5,7,2).subtract(start).scale(100000).normalize().dot(normal)>.999);
        var end=TransferVisuals.variedPosition(packet,1,7,2);
        assertTrue(end.subtract(TransferVisuals.variedPosition(packet,1-1e-5,7,2)).scale(100000).normalize().dot(normal)<-.999);
    }
    @Test void flightPassesNearRelayInsteadOfThroughCenter(){
        var a=new BlockPos(0,0,0);var b=new BlockPos(8,0,0);var c=new BlockPos(8,0,16);var path=List.of(a,b,c);
        assertEquals(60,TransferVisuals.duration(path));
        assertEquals(a.getCenter(),TransferVisuals.position(path,0));
        assertEquals(.45,b.getCenter().distanceTo(TransferVisuals.position(path,1.0/3)),1e-8);
        assertEquals(c.getCenter(),TransferVisuals.position(path,1));
        assertEquals(c.getCenter(),TransferVisuals.position(path,2));
        assertTrue(TransferVisuals.position(path,.2).x<8.5);
        assertTrue(TransferVisuals.position(path,.7).x>8.5,"The spline bends around the corner rather than stopping and turning");
    }
    @Test void relayHasContinuousNonzeroVelocityWithUnequalLegDurations(){
        var path=List.of(BlockPos.ZERO,new BlockPos(8,0,0),new BlockPos(8,0,16));double t=1.0/3,h=1e-6;
        var at=TransferVisuals.position(path,t);
        var incoming=at.subtract(TransferVisuals.position(path,t-h)).scale(1/h);
        var outgoing=TransferVisuals.position(path,t+h).subtract(at).scale(1/h);
        assertTrue(incoming.length()>1);assertTrue(incoming.distanceTo(outgoing)<.001);
    }
    @Test void allRuneFacesLaunchOutwardAndArriveInward(){
        var path=List.of(BlockPos.ZERO,new BlockPos(8,2,4));
        for(var face:net.minecraft.core.Direction.values()){
            var normal=net.minecraft.world.phys.Vec3.atLowerCornerOf(face.getNormal());
            var endpoint=new TransferVisuals.Endpoint(normal.scale(.502),face);
            var start=TransferVisuals.position(path,0,endpoint,endpoint);var finish=TransferVisuals.position(path,1,endpoint,endpoint);
            assertEquals(path.get(0).getCenter().add(endpoint.offset()),start);assertEquals(path.get(path.size()-1).getCenter().add(endpoint.offset()),finish);
            assertTrue(TransferVisuals.position(path,1e-4,endpoint,endpoint).subtract(start).scale(100000).normalize().dot(normal)>.999);
            assertTrue(finish.subtract(TransferVisuals.position(path,1-1e-4,endpoint,endpoint)).normalize().dot(normal)<-.999);
        }
    }
    @Test void repeatedNodesStayFiniteAndMalformedEndpointsAreRejected(){
        var path=List.of(BlockPos.ZERO,BlockPos.ZERO,new BlockPos(4,1,0));
        for(int i=0;i<=100;i++)assertTrue(Double.isFinite(TransferVisuals.position(path,i/100.0).lengthSqr()));
        assertThrows(IllegalArgumentException.class,()->new TransferVisuals.Endpoint(new net.minecraft.world.phys.Vec3(Double.NaN,0,0),net.minecraft.core.Direction.UP));
    }
    @Test void shortAndLongLegsHaveBoundedDurations(){assertEquals(20,TransferVisuals.legTicks(BlockPos.ZERO,new BlockPos(1,0,0)));assertEquals(100,TransferVisuals.legTicks(BlockPos.ZERO,new BlockPos(4096,0,0)));}
}
