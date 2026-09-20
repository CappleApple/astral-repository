package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.RuneLayer;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialIdentityTest {
    private static Constructor<?> constructor(String name,Class<?>... parameters) throws Exception {
        var result=Class.forName(name).getDeclaredConstructor(parameters);result.setAccessible(true);return result;
    }
    private static Constructor<?> physical() throws Exception {
        return constructor("com.cappleapple.astralrepository.compat.CompatibilityRegistry$PhysicalIdentity",String.class,long.class,long.class);
    }
    @Test void repeatedPhysicalPositionsKeepDenseFixtureHashesDistributed() throws Exception {
        var ctor=physical();var hashes=new HashSet<Integer>();var identities=new HashMap<Object,Integer>();
        int[] buckets=new int[16384];int ordinal=0;
        for(int group=0;group<50;group++)for(int bank=0;bank<2;bank++)for(int local=0;local<100;local++){
            var pos=new BlockPos(16+group%10*96+bank*40+local%10*2,64,16+group/10*64+local/10*2);
            long packed=pos.asLong();var identity=ctor.newInstance("minecraft:overworld",packed,packed);
            int hash=identity.hashCode();hashes.add(hash);buckets[(hash^(hash>>>16))&16383]++;
            identities.put(identity,ordinal);assertEquals(ordinal++,identities.get(ctor.newInstance("minecraft:overworld",packed,packed)));
        }
        assertEquals(10000,identities.size());assertTrue(hashes.size()>9900,"Repeated packed positions must not collapse into the record's linear hash");
        int busiest=0;for(int count:buckets)busiest=Math.max(busiest,count);
        assertTrue(busiest<8,"Dense physical identities must avoid systematic tree-bin collisions");
        long first=new BlockPos(-200,64,32).asLong(),second=new BlockPos(-199,64,32).asLong();
        var pair=ctor.newInstance("minecraft:overworld",Math.min(first,second),Math.max(first,second));
        assertEquals(pair,ctor.newInstance("minecraft:overworld",Math.min(second,first),Math.max(second,first)));
        assertNotEquals(pair,ctor.newInstance("minecraft:the_nether",Math.min(first,second),Math.max(first,second)));
        assertNotEquals(pair,ctor.newInstance("minecraft:overworld",first,first));
    }
    @Test void denseAddressAndRouteKeysPreserveEqualityAndHashDiversity() throws Exception {
        var origin=constructor("com.cappleapple.astralrepository.network.NetworkManager$RouteOrigin",GlobalPos.class,int.class,double.class,boolean.class);
        var route=constructor("com.cappleapple.astralrepository.network.NetworkManager$RouteKey",GlobalPos.class,GlobalPos.class,int.class,double.class,boolean.class);
        var sight=constructor("com.cappleapple.astralrepository.network.NetworkManager$SightKey",GlobalPos.class,GlobalPos.class);
        var hashes=new java.util.ArrayList<HashSet<Integer>>();for(int i=0;i<5;i++)hashes.add(new HashSet<>());
        for(int x=0;x<100;x++)for(int z=0;z<100;z++){
            var from=GlobalPos.of(Level.OVERWORLD,new BlockPos(x*2,64,z*2));
            var to=GlobalPos.of(Level.OVERWORLD,from.pos().offset(3,0,1));
            Object[] keys={new AnchorAddress(from,Direction.NORTH),new RuneLayer.Target(from,Direction.NORTH),
                    origin.newInstance(from,0,8D,true),route.newInstance(from,to,0,8D,true),sight.newInstance(from,to)};
            Object[] copies={new AnchorAddress(from,Direction.NORTH),new RuneLayer.Target(from,Direction.NORTH),
                    origin.newInstance(from,0,8D,true),route.newInstance(from,to,0,8D,true),sight.newInstance(from,to)};
            for(int i=0;i<keys.length;i++){hashes.get(i).add(keys[i].hashCode());assertEquals(keys[i],copies[i]);assertEquals(keys[i].hashCode(),copies[i].hashCode());}
        }
        for(var values:hashes)assertTrue(values.size()>9900,"Addresses and correlated route endpoints must retain spatial diversity");
        // Vanilla's linear BlockPos hash aliases these two distinct columns exactly.
        var aliasA=GlobalPos.of(Level.OVERWORLD,new BlockPos(961,64,0));
        var aliasB=GlobalPos.of(Level.OVERWORLD,new BlockPos(0,64,1));
        assertEquals(aliasA.pos().hashCode(),aliasB.pos().hashCode());
        assertNotEquals(new AnchorAddress(aliasA,Direction.UP).hashCode(),new AnchorAddress(aliasB,Direction.UP).hashCode());
        assertNotEquals(new RuneLayer.Target(aliasA,null).hashCode(),new RuneLayer.Target(aliasB,null).hashCode());
        assertNotEquals(origin.newInstance(aliasA,0,8D,true).hashCode(),origin.newInstance(aliasB,0,8D,true).hashCode());
        var aliasEndA=GlobalPos.of(Level.OVERWORLD,aliasA.pos().offset(3,0,1));
        var aliasEndB=GlobalPos.of(Level.OVERWORLD,aliasB.pos().offset(3,0,1));
        assertNotEquals(route.newInstance(aliasA,aliasEndA,0,8D,true).hashCode(),route.newInstance(aliasB,aliasEndB,0,8D,true).hashCode());
        assertNotEquals(sight.newInstance(aliasA,aliasEndA).hashCode(),sight.newInstance(aliasB,aliasEndB).hashCode());
        var pos=GlobalPos.of(Level.OVERWORLD,new BlockPos(-31,64,1));
        var nether=GlobalPos.of(Level.NETHER,pos.pos());
        assertNotEquals(new AnchorAddress(pos,null),new AnchorAddress(pos,Direction.NORTH));
        assertNotEquals(new RuneLayer.Target(pos,null),new RuneLayer.Target(nether,null));
        assertNotEquals(origin.newInstance(pos,0,8D,true),origin.newInstance(pos,1,8D,true));
        assertNotEquals(origin.newInstance(pos,0,8D,true),origin.newInstance(pos,0,9D,true));
        assertNotEquals(origin.newInstance(pos,0,8D,true),origin.newInstance(pos,0,8D,false));
        assertNotEquals(route.newInstance(pos,nether,0,8D,true),route.newInstance(nether,pos,0,8D,true));
        assertNotEquals(sight.newInstance(pos,nether),sight.newInstance(nether,pos));
    }
}
