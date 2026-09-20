package com.cappleapple.astralrepository.compat;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CompatConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue arsNouveau = BUILDER.define("arsNouveau", true);
    public static final ForgeConfigSpec.BooleanValue create = BUILDER.define("create", true);
    public static final ForgeConfigSpec.BooleanValue appliedEnergistics2 = BUILDER.define("appliedEnergistics2", true);
    public static final ForgeConfigSpec.BooleanValue refinedStorage = BUILDER.define("refinedStorage", true);
    public static final ForgeConfigSpec.BooleanValue stacksNotSlots = BUILDER.define("stacksNotSlots", true);
    public static final ForgeConfigSpec SPEC = BUILDER.build();
    private CompatConfig() { }
}