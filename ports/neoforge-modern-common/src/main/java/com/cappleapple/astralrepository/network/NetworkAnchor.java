package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Server-thread graph endpoint shared by physical crystals and face-mounted rune surfaces. */
public interface NetworkAnchor {
    Level getLevel();
    BlockPos getBlockPos();
    boolean isRemoved();
    NodeKind kind();
    int channel(); int priority(); boolean dimensional(); boolean enabled(); Direction facing();
    DistributionMode distributionMode();
    FilterRules insertionFilter(); FilterRules extractionFilter(); FilterRules selectedFilter();
    default FilterRules networkInsertionFilter() { return insertionFilter(); }
    default FilterRules networkExtractionFilter() { return extractionFilter(); }
    boolean editingExtraction(); boolean excludeNext();
    void setChannel(int value); void setPriority(int value); void setDimensional(boolean value);
    void cycleDistribution(); void toggleDirection(); void toggleExclusion(); void toggleEnabled();
    void changed(); void topologyChanged(); void setChanged(); CompoundTag getPersistentData();
    default AnchorAddress address() { return new AnchorAddress(GlobalPos.of(getLevel().dimension(),getBlockPos()),null); }
    default BlockPos providerPosition() { return getBlockPos().relative(facing()); }
    default Direction providerSide() { return facing().getOpposite(); }
    default boolean longRange(){return kind().remote();}
    default boolean nearbyCoverage() { return true; }
    default boolean collects() { return kind()==NodeKind.COLLECTION; }
    default boolean stocks() { return kind()==NodeKind.ROUTING||kind()==NodeKind.DISTRIBUTION; }
    default boolean distributes() { return kind()==NodeKind.DISTRIBUTION; }
    default boolean attachedTo(GlobalPos position) { return getLevel()!=null&&getLevel().dimension().equals(position.dimension())&&providerPosition().equals(position.pos()); }
}