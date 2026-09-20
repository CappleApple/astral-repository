package com.cappleapple.astralrepository.network;

/** Independent progression gates for handheld access and paired network bridges. */
public final class RemoteRules {
    public static boolean access(boolean sameDimension, boolean handheldAttuned, double squaredDistance, double range) {
        return sameDimension ? squaredDistance <= range * range : handheldAttuned;
    }
    public static boolean bridge(boolean sameDimension, boolean firstAttuned, boolean secondAttuned, boolean reciprocal) {
        return reciprocal && (sameDimension || firstAttuned && secondAttuned);
    }
    private RemoteRules() {}
}
