package com.cappleapple.astralrepository.network;

import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/** A crystal uses a block address; a surface endpoint additionally identifies its outward face. */
public record AnchorAddress(GlobalPos position, @Nullable Direction face) implements Comparable<AnchorAddress> {
    public AnchorAddress { position=GlobalPos.of(position.dimension(),position.pos().immutable()); }
    @Override public int hashCode(){return 31*SpatialHash.POSITIONS.hashCode(position)+(face==null?0:face.ordinal()+1);}
    public static AnchorAddress crystal(ServerLevel level,BlockPos pos) { return new AnchorAddress(GlobalPos.of(level.dimension(),pos),null); }
    public static AnchorAddress rune(ServerLevel level,BlockPos pos,Direction face) { return new AnchorAddress(GlobalPos.of(level.dimension(),pos),java.util.Objects.requireNonNull(face)); }
    public String id() { return NetworkManager.id(position)+"/"+(face==null?"crystal":face.getName()); }
    public CompoundTag save() {
        CompoundTag tag=new CompoundTag(); tag.putString("Dimension",position.dimension().identifier().toString()); tag.putLong("Position",position.pos().asLong());
        if(face!=null)tag.putString("Face",face.getName()); return tag;
    }
    public static AnchorAddress load(CompoundTag tag) {
        var dimension=net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,Identifier.parse(tag.getStringOr("Dimension","")));
        Direction face=tag.contains("Face")?Direction.byName(tag.getStringOr("Face","")):null;
        if(tag.contains("Face")&&face==null)throw new IllegalArgumentException("Invalid rune face");
        return new AnchorAddress(GlobalPos.of(dimension,BlockPos.of(tag.getLongOr("Position",0L))),face);
    }
    @Override public int compareTo(AnchorAddress other) { return id().compareTo(other.id()); }
}