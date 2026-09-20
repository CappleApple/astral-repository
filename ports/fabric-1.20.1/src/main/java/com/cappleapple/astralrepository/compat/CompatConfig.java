package com.cappleapple.astralrepository.compat;

import io.github.fabricators_of_create.porting_lib.config.ModConfigSpec;

public final class CompatConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue arsNouveau = BUILDER.define("arsNouveau", true);
    public static final ModConfigSpec.BooleanValue create = BUILDER.define("create", true);
    public static final ModConfigSpec.BooleanValue appliedEnergistics2 = BUILDER.define("appliedEnergistics2", true);
    public static final ModConfigSpec.BooleanValue refinedStorage = BUILDER.define("refinedStorage", true);
    public static final ModConfigSpec.BooleanValue stacksNotSlots = BUILDER.define("stacksNotSlots", true);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private CompatConfig() { }
}