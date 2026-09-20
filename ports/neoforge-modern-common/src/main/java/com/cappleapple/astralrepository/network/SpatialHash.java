package com.cappleapple.astralrepository.network;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.util.Map;
import net.minecraft.core.GlobalPos;

/** Spatial indexes avoid the dense-grid collisions of BlockPos's linear hash. */
public final class SpatialHash {
    public static final Hash.Strategy<GlobalPos> POSITIONS=new Hash.Strategy<>() {
        public int hashCode(GlobalPos value){
            if(value==null)return 0;
            long mixed=HashCommon.mix(value.pos().asLong());
            return (int)(mixed^(mixed>>>32))^value.dimension().hashCode();
        }
        public boolean equals(GlobalPos a,GlobalPos b){return java.util.Objects.equals(a,b);}
    };
    public static int pair(GlobalPos first,GlobalPos second){
        // Mixing a correlated endpoint pair before folding avoids cancelling the spatial hash bits.
        long mixed=HashCommon.mix(first.pos().asLong()+Long.rotateLeft(second.pos().asLong(),21));
        return (int)(mixed^(mixed>>>32))^(31*first.dimension().hashCode()+second.dimension().hashCode());
    }
    public static <V> Map<GlobalPos,V> positions(){return new Object2ObjectOpenCustomHashMap<>(POSITIONS);}
    private SpatialHash(){}
}
