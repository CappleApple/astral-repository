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
import com.cappleapple.astralrepository.port.storage.EnergyStorage;
import com.cappleapple.astralrepository.port.storage.FluidTank;
import javax.annotation.Nullable;

public final class CrystalNodeBlockEntity extends BlockEntity implements com.cappleapple.astralrepository.network.NetworkAnchor {
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
    private final com.cappleapple.astralrepository.port.AstralFluidTank tank = new com.cappleapple.astralrepository.port.AstralFluidTank(32000, this::changed);
    private final com.cappleapple.astralrepository.port.AstralEnergyStorage energy = new com.cappleapple.astralrepository.port.AstralEnergyStorage(1000000, 10000, this::changed);

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
    public com.cappleapple.astralrepository.port.AstralFluidTank tank() { return tank; }
    public com.cappleapple.astralrepository.port.AstralEnergyStorage energy() { return energy; }
    public boolean hasInventory() { return kind() == NodeKind.STORAGE || kind() == NodeKind.BUFFER || kind() == NodeKind.POWER; }
    public long capacity() { return AstralContent.capacityForTier.applyAsLong(storageTier()); }
    public void setChannel(int value) { channel = Math.clamp(value, -1, 15); topologyChanged(); }
    public void setPriority(int value) { priority = Math.clamp(value, -999, 999); topologyChanged(); }
    public void setPartner(@Nullable GlobalPos value) { partner = value; topologyChanged(); }
    public void setDimensional(boolean value) { dimensional = value; topologyChanged(); }
    public void cycleDistribution() { distributionMode = distributionMode.next(); topologyChanged(); }
    public void toggleDirection() { editingExtraction = !editingExtraction; changed(); }
    public void toggleExclusion() { excludeNext = !excludeNext; changed(); }
    public void toggleEnabled() { enabled = !enabled; topologyChanged(); }
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public void topologyChanged() {
        changed();
        if (level instanceof ServerLevel server) ContentHooks.topologyChanged.accept(server, worldPosition);
    }
    @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel server) ContentHooks.topologyChanged.accept(server, worldPosition); }
    @Override public void setRemoved() { if (level instanceof ServerLevel server) ContentHooks.topologyChanged.accept(server, worldPosition); super.setRemoved(); }

    @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        var registries = level.registryAccess();
        CompoundTag tag = new CompoundTag();
        writeSettings(tag, registries);
        if (hasInventory()) { tag.put("Storage", inventory.save(registries)); tag.put("Tank", com.cappleapple.astralrepository.port.NbtCodecs.save(tank.getFluid(),registries)); tag.putInt("Energy", energy.getEnergyStored()); }
        output.store(tag);
    }
    private void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Channel", channel); tag.putInt("Priority", priority); tag.putBoolean("Dimensional", dimensional);
        tag.putBoolean("EditingExtraction", editingExtraction); tag.putBoolean("ExcludeNext", excludeNext); tag.putBoolean("Enabled", enabled);
        tag.putString("Distribution", distributionMode.name());tag.putInt("StorageTier",storageTier());tag.putBoolean("LongRange",longRange);
        tag.put("Insertion", insertion.save(registries)); tag.put("Extraction", extraction.save(registries));
        if (partner != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, partner).result().ifPresent(value -> tag.put("Partner", value));
    }
    @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        var registries = input.lookup();
        CompoundTag tag = input.read(com.mojang.serialization.MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
        channel = tag.contains("Channel") ? Math.clamp(tag.getIntOr("Channel",0), -1, 15) : -1;
        priority = Math.clamp(tag.getIntOr("Priority",0), -999, 999); dimensional = tag.getBooleanOr("Dimensional",false);
        editingExtraction = tag.getBooleanOr("EditingExtraction",false); excludeNext = tag.getBooleanOr("ExcludeNext",false); enabled = !tag.contains("Enabled") || tag.getBooleanOr("Enabled",false);
        storageTier=Math.clamp(tag.getIntOr("StorageTier",0),0,3);longRange=tag.getBooleanOr("LongRange",false);
        try { distributionMode = DistributionMode.valueOf(tag.getStringOr("Distribution","")); } catch (IllegalArgumentException ignored) { distributionMode = DistributionMode.PRIORITY; }
        insertion.load(tag.getCompoundOrEmpty("Insertion"), registries); extraction.load(tag.getCompoundOrEmpty("Extraction"), registries);
        partner = tag.contains("Partner") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("Partner")).result().orElse(null) : null;
        if (tag.contains("Storage")) inventory.load(tag.getCompoundOrEmpty("Storage"), registries);
        if (tag.contains("Tank")) tank.setFluid(com.cappleapple.astralrepository.port.NbtCodecs.fluid(registries, tag.getCompoundOrEmpty("Tank")));
        if (tag.contains("Energy")) energy.setStored(tag.getIntOr("Energy",0));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); writeSettings(tag, registries); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
