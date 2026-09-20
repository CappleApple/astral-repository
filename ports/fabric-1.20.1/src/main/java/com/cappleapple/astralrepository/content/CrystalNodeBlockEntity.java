package com.cappleapple.astralrepository.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.cappleapple.astralrepository.platform.energy.EnergyStorage;
import com.cappleapple.astralrepository.platform.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public final class CrystalNodeBlockEntity extends BlockEntity implements com.cappleapple.astralrepository.network.NetworkAnchor {
    private final CompoundTag persistentData=new CompoundTag();
    @Override public CompoundTag getPersistentData(){return persistentData;}
    private int storageTier;
    private boolean longRange;
    public int storageTier(){return storageTier==0?((CrystalNodeBlock)getBlockState().getBlock()).tier():storageTier;}
    public boolean longRange(){return longRange||kind().remote();}
    public void upgradeRange(){longRange=true;topologyChanged();}
    public boolean upgradeStorage(int tier){if(kind()!=NodeKind.STORAGE||tier!=storageTier()+1||tier>3)return false;storageTier=tier;topologyChanged();return true;}
    private int channel = -1;
    private int priority;
    private boolean dimensional;
    private boolean editingExtraction;
    private boolean excludeNext;
    private boolean enabled = true;
    @Nullable private GlobalPos partner;
    private DistributionMode distributionMode = DistributionMode.PRIORITY;
    private final FilterRules insertion = new FilterRules();
    private final FilterRules extraction = new FilterRules();
    private final CapacityInventory inventory = new CapacityInventory(this::capacity, this::changed);
    private final FluidTank tank = new FluidTank(32000) { @Override protected void onContentsChanged() { changed(); } };
    private final EnergyStorage energy = new EnergyStorage(1000000, 10000) {
        @Override public int receiveEnergy(int amount, boolean simulate) { int result = super.receiveEnergy(amount, simulate); if (result > 0 && !simulate) changed(); return result; }
        @Override public int extractEnergy(int amount, boolean simulate) { int result = super.extractEnergy(amount, simulate); if (result > 0 && !simulate) changed(); return result; }
    };


    public CrystalNodeBlockEntity(BlockPos pos, BlockState state) { super(AstralContent.NODE_ENTITY.get(), pos, state); }
    public NodeKind kind() { return ((CrystalNodeBlock) getBlockState().getBlock()).kind(); }
    public int channel() { return channel; }
    public int priority() { return priority; }
    public boolean dimensional() { return dimensional; }
    public boolean enabled() { return enabled; }
    @Nullable public GlobalPos partner() { return partner; }
    public Direction facing() { return getBlockState().getValue(CrystalNodeBlock.FACING); }
    public DistributionMode distributionMode() { return distributionMode; }
    public FilterRules insertionFilter() { return insertion; }
    public FilterRules extractionFilter() { return extraction; }
    public FilterRules selectedFilter() { return editingExtraction ? extraction : insertion; }
    public boolean editingExtraction() { return editingExtraction; }
    public boolean excludeNext() { return excludeNext; }
    public CapacityInventory inventory() { return inventory; }
    public FluidTank tank() { return tank; }
    public EnergyStorage energy() { return energy; }
    public boolean hasInventory() { return kind() == NodeKind.STORAGE || kind() == NodeKind.BUFFER || kind() == NodeKind.POWER; }
    public long capacity() { return AstralContent.capacityForTier.applyAsLong(storageTier()); }
    public void setChannel(int value) { channel = com.cappleapple.astralrepository.platform.Backport.clamp(value, -1, 15); topologyChanged(); }
    public void setPriority(int value) { priority = com.cappleapple.astralrepository.platform.Backport.clamp(value, -999, 999); topologyChanged(); }
    public void setPartner(@Nullable GlobalPos value) { partner = value; topologyChanged(); }
    public void setDimensional(boolean value) { dimensional = value; topologyChanged(); }
    public void cycleDistribution() { distributionMode = distributionMode.next(); topologyChanged(); }
    public void toggleDirection() { editingExtraction = !editingExtraction; changed(); }
    public void toggleExclusion() { excludeNext = !excludeNext; changed(); }
    public void toggleEnabled() { enabled = !enabled; topologyChanged(); }
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public void topologyChanged() {
        changed();
        if (level instanceof ServerLevel server) ContentHooks.topologyChanged.accept(server, worldPosition);
    }
    public void onLoad() { if (level instanceof ServerLevel server) ContentHooks.topologyChanged.accept(server, worldPosition); }
    @Override public void setRemoved() { if (level instanceof ServerLevel server) ContentHooks.topologyChanged.accept(server, worldPosition); super.setRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);tag.put("AstralPersistentData",persistentData.copy());
        HolderLookup.Provider registries=level==null?null:level.registryAccess();
        writeSettings(tag, registries);
        if (hasInventory()) { tag.put("Storage", inventory.save(registries)); tag.put("Tank", tank.writeToNBT(new CompoundTag())); tag.put("Energy", energy.serializeNBT()); }
    }
    private void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Channel", channel); tag.putInt("Priority", priority); tag.putBoolean("Dimensional", dimensional);
        tag.putBoolean("EditingExtraction", editingExtraction); tag.putBoolean("ExcludeNext", excludeNext); tag.putBoolean("Enabled", enabled);
        tag.putString("Distribution", distributionMode.name());tag.putInt("StorageTier",storageTier());tag.putBoolean("LongRange",longRange);
        tag.put("Insertion", insertion.save(registries)); tag.put("Extraction", extraction.save(registries));
        if (partner != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, partner).result().ifPresent(value -> tag.put("Partner", value));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);for(var key:java.util.Set.copyOf(persistentData.getAllKeys()))persistentData.remove(key);persistentData.merge(tag.getCompound("AstralPersistentData"));
        HolderLookup.Provider registries=level==null?null:level.registryAccess();
        channel = tag.contains("Channel") ? com.cappleapple.astralrepository.platform.Backport.clamp(tag.getInt("Channel"), -1, 15) : -1;
        priority = com.cappleapple.astralrepository.platform.Backport.clamp(tag.getInt("Priority"), -999, 999); dimensional = tag.getBoolean("Dimensional");
        editingExtraction = tag.getBoolean("EditingExtraction"); excludeNext = tag.getBoolean("ExcludeNext"); enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        storageTier=com.cappleapple.astralrepository.platform.Backport.clamp(tag.getInt("StorageTier"),0,3);longRange=tag.getBoolean("LongRange");
        try { distributionMode = DistributionMode.valueOf(tag.getString("Distribution")); } catch (IllegalArgumentException ignored) { distributionMode = DistributionMode.PRIORITY; }
        insertion.load(tag.getCompound("Insertion"), registries); extraction.load(tag.getCompound("Extraction"), registries);
        partner = tag.contains("Partner") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("Partner")).result().orElse(null) : null;
        if (tag.contains("Storage")) inventory.load(tag.getCompound("Storage"), registries);
        if (tag.contains("Tank")) tank.readFromNBT(tag.getCompound("Tank"));
        if (tag.contains("Energy")) energy.deserializeNBT(tag.get("Energy"));
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag(); writeSettings(tag, level==null?null:level.registryAccess()); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
