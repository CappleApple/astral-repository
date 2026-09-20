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

class ResourceDepartureTest {
    private static NetworkPackets.Visual packet(int slot,int duration,TransferVisuals.Endpoint departure){
        var path=List.of(BlockPos.ZERO,new BlockPos(8,0,0));
        return new NetworkPackets.Visual(path.getFirst(),path.getLast(),ItemStack.EMPTY,0xffffff,duration,slot,path,departure,null);
    }
    @Test void everyResourceAndRuneFaceFadesAndGrowsInThreeTicksRegardlessOfRouteDuration(){
        for(int slot=-4;slot<=-2;slot++)for(var face:Direction.values())for(int duration:List.of(20,100,1000)){
            var endpoint=new TransferVisuals.Endpoint(Vec3.atLowerCornerOf(face.getNormal()).scale(.502),face);
            var packet=packet(slot,duration,endpoint);
            assertEquals(0,ResourceDeparture.opacity(packet,-1));
            assertEquals(0,ResourceDeparture.opacity(packet,0));
            assertEquals(.5F,ResourceDeparture.opacity(packet,1.5));
            assertEquals(1,ResourceDeparture.opacity(packet,3));
            assertEquals(1,ResourceDeparture.opacity(packet,duration));
            assertEquals(.5F,ResourceDeparture.scale(packet,-1));
            assertEquals(.5F,ResourceDeparture.scale(packet,0));
            assertEquals(.75F,ResourceDeparture.scale(packet,1.5));
            assertEquals(1,ResourceDeparture.scale(packet,3));
            assertEquals(1,ResourceDeparture.scale(packet,duration));
            float previous=0,previousScale=.5F;
            for(int i=0;i<=30;i++){
                float opacity=ResourceDeparture.opacity(packet,i/10.0);
                assertTrue(opacity>=previous&&opacity<=1);previous=opacity;
                float scale=ResourceDeparture.scale(packet,i/10.0);
                assertTrue(scale>=previousScale&&scale<=1);previousScale=scale;
            }
        }
    }
    @Test void ordinaryItemsAndUninscribedSourcesKeepTheirExistingOpacityAndSize(){
        var endpoint=new TransferVisuals.Endpoint(new Vec3(.502,0,0),Direction.EAST);
        for(int slot:List.of(-1,0,9,-5)){assertEquals(1,ResourceDeparture.opacity(packet(slot,20,endpoint),0));assertEquals(1,ResourceDeparture.scale(packet(slot,20,endpoint),0));}
        for(int slot=-4;slot<=-2;slot++){assertEquals(1,ResourceDeparture.opacity(packet(slot,20,null),0));assertEquals(1,ResourceDeparture.scale(packet(slot,20,null),0));}
    }
}
