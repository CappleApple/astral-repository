package com.cappleapple.astralrepository.content;

public enum DistributionMode {
    PRIORITY, ROUND_ROBIN, BALANCED, NEAREST;
    public DistributionMode next() { return values()[(ordinal() + 1) % values().length]; }
}
