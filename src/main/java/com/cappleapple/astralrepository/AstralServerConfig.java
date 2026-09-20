package com.cappleapple.astralrepository;

import io.github.fabricators_of_create.porting_lib.config.ModConfigSpec;

/** World settings are synchronized by NeoForge to every connected client. */
public final class AstralServerConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue runeResolution = B.comment("Visible rune canvas width and height. Larger saved designs are cropped from the top-left, never resized or erased.").defineInRange("runeResolution",32,8,128);
    public static final ModConfigSpec.IntValue maxRunesPerFace = B.comment("Maximum newly placed runes on one container face. Existing saved runes are preserved if this is lowered. Placement still requires free space.").defineInRange("maxRunesPerFace",8,1,64);
    public static final ModConfigSpec.DoubleValue runeSize = B.comment("Rune width/height in blocks. Placement fits the host face; existing centers are preserved.").defineInRange("runeSize",0.20,0.0625,0.75);
    public static final ModConfigSpec.DoubleValue wandBindingRange = B.comment("Direct rune transfer range and maximum distance from a rune/container to its first/last relay. Longer assignments need a loaded same-channel relay route.").defineInRange("wandBindingRange",8.0,2.0,30000000.0);
    public static final ModConfigSpec.IntValue maxItemTransfer = B.comment("Maximum items in one automatic rune transfer, including saved cadence overrides. Bulk storage may transfer more than one stack when this is raised.").defineInRange("maxItemTransfer",64,1,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue maxFluidTransfer = B.comment("Maximum fluid in mB in one automatic rune transfer.").defineInRange("maxFluidTransfer",1000,1,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue maxEnergyTransfer = B.comment("Maximum energy in FE/RF in one automatic rune transfer.").defineInRange("maxEnergyTransfer",10000,1,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue maxSourceTransfer = B.comment("Maximum Ars Source in one automatic rune transfer.").defineInRange("maxSourceTransfer",1000,1,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue minItemTransferTicks = B.comment("Minimum ticks between a rune's item transfers. Applies even with instant automatic logistics enabled.").defineInRange("minItemTransferTicks",1,1,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue minFluidTransferTicks = B.comment("Minimum ticks between a rune's fluid transfers.").defineInRange("minFluidTransferTicks",1,1,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue minEnergyTransferTicks = B.comment("Minimum ticks between a rune's energy transfers.").defineInRange("minEnergyTransferTicks",1,1,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue minSourceTransferTicks = B.comment("Minimum ticks between a rune's Source transfers.").defineInRange("minSourceTransferTicks",1,1,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue maxVisualsPerPlayerTick = B.comment("Maximum new moving-resource animations sent to each nearby player per tick. Busy networks use a fair sample; real transfers are unaffected and cosmetic work never accumulates in a backlog. Station displays have a separate bounded queue.").defineInRange("maxVisualsPerPlayerTick",128,1,1024);
    public static final ModConfigSpec.IntValue maxVisualBytesPerPlayerTick = B.comment("Maximum bytes in each player's once-per-tick animation batch, including station displays. Oversized cosmetic icons are omitted without changing their real items.").defineInRange("maxVisualBytesPerPlayerTick",65536,4096,262144);
    public static final ModConfigSpec SPEC = B.build();
    private AstralServerConfig() {}
}
