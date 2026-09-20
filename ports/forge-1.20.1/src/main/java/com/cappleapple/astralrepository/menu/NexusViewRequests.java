package com.cappleapple.astralrepository.menu;

/** Tracks client view intent independently from the server's incremental packet baseline. */
public final class NexusViewRequests {
    private long latest;
    public long next() { return ++latest; }
    public long latest() { return latest; }
    public int resolveRow(int desired, int acknowledgedRow, long acknowledgedRequest, boolean unsentSearch) {
        return !unsentSearch && acknowledgedRequest >= latest ? acknowledgedRow : desired;
    }
}
