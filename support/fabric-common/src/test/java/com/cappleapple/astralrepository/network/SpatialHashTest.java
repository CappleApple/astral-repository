package com.cappleapple.astralrepository.network;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialHashTest {
    @Test void denseGridCoordinatesAndDimensionsStayDistinct(){
        var index=SpatialHash.<Integer>positions();var hashes=new HashSet<Integer>();
        for(int x=0;x<100;x++)for(int z=0;z<100;z++){
            var pos=GlobalPos.of(Level.OVERWORLD,new BlockPos(x*2,64,z*2));
            index.put(pos,x*100+z);hashes.add(SpatialHash.POSITIONS.hashCode(pos));
        }
        assertEquals(10000,index.size());assertTrue(hashes.size()>9900,"Grid keys retain hash diversity");
        for(int x=0;x<100;x++)for(int z=0;z<100;z++){
            var pos=GlobalPos.of(Level.OVERWORLD,new BlockPos(x*2,64,z*2));
            assertEquals(x*100+z,index.get(pos));assertNull(index.get(GlobalPos.of(Level.NETHER,pos.pos())));
        }
        var last=GlobalPos.of(Level.OVERWORLD,new BlockPos(198,64,198));
        assertEquals(9999,index.remove(last));assertFalse(index.containsKey(last));
    }
}
