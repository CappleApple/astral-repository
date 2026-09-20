package com.cappleapple.astralrepository.port;

import com.cappleapple.astralrepository.platform.fluids.FluidStack;
import com.cappleapple.astralrepository.platform.fluids.capability.templates.FluidTank;
import com.cappleapple.astralrepository.platform.transfer.ResourceHandler;
import com.cappleapple.astralrepository.platform.transfer.TransferPreconditions;
import com.cappleapple.astralrepository.platform.transfer.fluid.FluidResource;
import com.cappleapple.astralrepository.platform.transfer.transaction.SnapshotJournal;
import com.cappleapple.astralrepository.platform.transfer.transaction.TransactionContext;

/** A fluid store shared by network operations and NeoForge transactional automation. */
public final class AstralFluidTank extends FluidTank implements ResourceHandler<FluidResource> {
    private final Runnable changed;
    private final SnapshotJournal<FluidStack> journal = new SnapshotJournal<>() {
        @Override protected FluidStack createSnapshot() { return fluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack snapshot) { fluid = snapshot.copy(); }
        @Override protected void onRootCommit(FluidStack snapshot) { changed.run(); }
    };
    public AstralFluidTank(int capacity, Runnable changed) { super(capacity); this.changed = changed; }
    @Override protected void onContentsChanged() { if (!journal.isInTransaction()) changed.run(); }
    @Override public int size() { return 1; }
    @Override public FluidResource getResource(int index) { java.util.Objects.checkIndex(index, 1); return FluidResource.of(fluid); }
    @Override public long getAmountAsLong(int index) { java.util.Objects.checkIndex(index, 1); return fluid.getAmount(); }
    @Override public long getCapacityAsLong(int index, FluidResource resource) { java.util.Objects.checkIndex(index, 1); return capacity; }
    @Override public boolean isValid(int index, FluidResource resource) { java.util.Objects.checkIndex(index, 1); return resource.isEmpty() || isFluidValid(resource.toStack(1)); }
    @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        java.util.Objects.checkIndex(index, 1); TransferPreconditions.checkNonNegative(amount);
        if (resource.isEmpty() || amount == 0) return 0;
        FluidStack incoming = resource.toStack(amount);
        if (fill(incoming, FluidAction.SIMULATE) == 0) return 0;
        journal.updateSnapshots(transaction);
        return fill(incoming, FluidAction.EXECUTE);
    }
    @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        java.util.Objects.checkIndex(index, 1); TransferPreconditions.checkNonNegative(amount);
        if (resource.isEmpty() || amount == 0 || !resource.matches(fluid)) return 0;
        journal.updateSnapshots(transaction);
        return drain(amount, FluidAction.EXECUTE).getAmount();
    }
}
