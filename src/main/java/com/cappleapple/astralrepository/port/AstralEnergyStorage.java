package com.cappleapple.astralrepository.port;

import com.cappleapple.astralrepository.port.storage.EnergyStorage;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Energy storage with rollback and one dirty notification after root commit. */
public final class AstralEnergyStorage extends EnergyStorage implements EnergyHandler {
    private final Runnable changed;
    private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
        @Override protected Integer createSnapshot() { return energy; }
        @Override protected void revertToSnapshot(Integer snapshot) { energy = snapshot; }
        @Override protected void onRootCommit(Integer snapshot) { changed.run(); }
    };
    public AstralEnergyStorage(int capacity, int transfer, Runnable changed) { super(capacity, transfer); this.changed = changed; }
    public void setStored(int value) { energy = Math.clamp(value, 0, capacity); }
    @Override public int receiveEnergy(int amount, boolean simulate) {
        int result = super.receiveEnergy(amount, simulate);
        if (result > 0 && !simulate && !journal.isInTransaction()) changed.run();
        return result;
    }
    @Override public int extractEnergy(int amount, boolean simulate) {
        int result = super.extractEnergy(amount, simulate);
        if (result > 0 && !simulate && !journal.isInTransaction()) changed.run();
        return result;
    }
    @Override public long getAmountAsLong() { return energy; }
    @Override public long getCapacityAsLong() { return capacity; }
    @Override public int insert(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        if (receiveEnergy(amount, true) == 0) return 0;
        journal.updateSnapshots(transaction);
        return receiveEnergy(amount, false);
    }
    @Override public int extract(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        if (extractEnergy(amount, true) == 0) return 0;
        journal.updateSnapshots(transaction);
        return extractEnergy(amount, false);
    }
}
