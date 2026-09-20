package com.cappleapple.astralrepository;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AstralConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue instantPlayerInteractions = B.comment("Complete player-triggered Nexus transfers, grid refill/clear actions and craft submissions immediately. False defers them by 20 server ticks and revalidates before committing.").define("instantPlayerInteractions", true);
    public static final ModConfigSpec.BooleanValue instantAutomaticLogistics = B.comment("Run automatic routing and default rune intervals every tick, and skip crafting delivery waits and Astral-managed table/stonecutter delays. Explicit rune intervals, quantity limits, power costs, and physical or external machine processing remain unchanged.").define("instantAutomaticLogistics", false);
    public static final ModConfigSpec.IntValue coverageRange = B.comment("Radius around each crystal that discovers workshop blocks.").defineInRange("coverageRange", 5, 1, 32);
    public static final ModConfigSpec.LongValue seedStorageCapacity = B.defineInRange("seedStorageCapacity", 16384L, 64L, 1000000000000L);
    public static final ModConfigSpec.LongValue moonStorageCapacity = B.defineInRange("moonStorageCapacity", 262144L, 64L, 1000000000000L);
    public static final ModConfigSpec.LongValue starStorageCapacity = B.defineInRange("starStorageCapacity", 4194304L, 64L, 1000000000000L);
    public static final ModConfigSpec.LongValue bufferCapacity = B.defineInRange("bufferCapacity", 4096L, 64L, 1000000000000L);
    public static final ModConfigSpec.BooleanValue requireLineOfSight = B.comment("Require clear loaded sight for same-dimension relay links and transfer legs. Transparent blocks pass; opaque shapes block where intersected. Uses relayRange, remoteRange, and server wandBindingRange for reach.").define("requireLineOfSight", true);
    public static final ModConfigSpec.IntValue relayRange = B.defineInRange("relayRange", 16, 2, 128);
    public static final ModConfigSpec.IntValue discoveryBudget = B.comment("Maximum block positions examined across all networks per server tick.").defineInRange("discoveryBudget", 512, 16, 65536);
    public static final ModConfigSpec.IntValue reconciliationBudget = B.defineInRange("reconciliationBudget", 8, 1, 1024);
    public static final ModConfigSpec.IntValue reconciliationTicks = B.defineInRange("reconciliationTicks", 100, 20, 72000);
    public static final ModConfigSpec.IntValue transferRate = B.defineInRange("transferRate", 16, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue fluidTransferRate = B.defineInRange("fluidTransferRate", 250, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue energyTransferRate = B.defineInRange("energyTransferRate", 1000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue sourceTransferRate = B.defineInRange("sourceTransferRate", 1000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue runeTransferInterval = B.comment("Default ticks between each rune resource transfer. Per-rune Cadence can override this, within the synchronized server transfer limits.").defineInRange("runeTransferInterval", 20, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue parallelism = B.defineInRange("parallelism", 32, 1, 256);
    public static final ModConfigSpec.IntValue remoteRange = B.defineInRange("remoteRange", 4096, 16, 30000000);
    public static final ModConfigSpec.IntValue maxNodes = B.comment("Safety limit; networks beyond this many loaded nodes are not joined.").defineInRange("maxNodes", 16384, 1, 1000000);
    public static final ModConfigSpec.DoubleValue particleDensity = B.defineInRange("particleDensity", 1.0, 0.0, 2.0);
    public static final ModConfigSpec.BooleanValue powerEnabled = B.define("powerEnabled", false);
    public static final ModConfigSpec.ConfigValue<String> powerMode = B.comment("any or all: how allowed power resources satisfy cost.").define("powerMode", "any");
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> powerProviders = B.defineListAllowEmpty("powerProviders", java.util.List.of("energy"), o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<String> itemFuel = B.define("itemFuel", "minecraft:amethyst_shard");
    public static final ModConfigSpec.ConfigValue<String> fluidFuel = B.define("fluidFuel", "minecraft:lava");
    public static final ModConfigSpec.IntValue itemFuelValue = B.defineInRange("itemFuelValue", 10000, 1, 1000000000);
    public static final ModConfigSpec.IntValue fluidFuelValue = B.defineInRange("fluidFuelValue", 10, 1, 1000000000);
    public static final ModConfigSpec.DoubleValue baseCost = cost("baseCost", 1);
    public static final ModConfigSpec.DoubleValue nodeCost = cost("nodeCost", .1);
    public static final ModConfigSpec.DoubleValue storageCost = cost("storageCost", .1);
    public static final ModConfigSpec.DoubleValue capacityCost = cost("capacityCost", 0);
    public static final ModConfigSpec.DoubleValue transferCost = cost("transferCost", 1);
    public static final ModConfigSpec.DoubleValue itemCost = cost("itemCost", .1);
    public static final ModConfigSpec.DoubleValue fluidCost = cost("fluidCost", .001);
    public static final ModConfigSpec.DoubleValue energyCost = cost("energyCost", 0);
    public static final ModConfigSpec.DoubleValue sourceCost = cost("sourceCost", .1);
    public static final ModConfigSpec.DoubleValue jobCost = cost("jobCost", 1);
    public static final ModConfigSpec.DoubleValue processorCost = cost("processorCost", 1);
    public static final ModConfigSpec.DoubleValue remoteCost = cost("remoteCost", 5);
    public static final ModConfigSpec.DoubleValue distanceCost = cost("distanceCost", .001);
    public static final ModConfigSpec.DoubleValue dimensionalCost = cost("dimensionalCost", 20);
    public static final ModConfigSpec.DoubleValue complexityCost = cost("complexityCost", 1);
    public static final ModConfigSpec SPEC = B.build();
    private static ModConfigSpec.DoubleValue cost(String key, double value) { return B.defineInRange(key, value, 0, 1000000000); }
    private AstralConfig() {}
}

