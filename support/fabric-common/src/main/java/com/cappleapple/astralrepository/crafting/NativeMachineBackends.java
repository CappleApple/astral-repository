package com.cappleapple.astralrepository.crafting;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** One discovery backend per mod's shared native processing API. */
final class NativeMachineBackends {
    private NativeMachineBackends() {}
    static List<NativeMachineBackend> create() {
        return List.of(new IndustrialForegoingBackend(), new IndustrialForegoingGeneratorBackend(), new IndustrialForegoingBrewingBackend(), new OritechBackend(), new PowahBackend(),
                new ImmersiveEngineeringBackend(), new EnderIoBackend(), new ThermalBackend(), new CreateBackend(), new CreateCrafterBackend());
    }
    private static final class IdentityLookups { static final List<NativeMachineBackend> ALL = create(); }
    static boolean reserved(ServerLevel level, BlockPos position, java.util.Set<Object> identities) {
        for (var backend : IdentityLookups.ALL) {
            try {
                Object identity = backend.identityAt(level, position);
                if (identity != null && identities.contains(identity)) return true;
            } catch (RuntimeException | LinkageError unavailable) {
                CraftingService.LOGGER.debug("Native {} alias unavailable at {}", backend.id(), position);
            }
        }
        return false;
    }
    static Object ownedIdentity(ServerLevel level, BlockPos position, java.util.Set<Object> identities) {
        for (var backend : IdentityLookups.ALL) try {
            Object identity = backend.identityAt(level, position);
            if (identity != null && identities.contains(identity)) return identity;
        } catch (RuntimeException | LinkageError unavailable) { /* No observer may change native ticking. */ }
        return null;
    }
}
