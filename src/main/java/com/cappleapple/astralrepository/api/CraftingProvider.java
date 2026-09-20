package com.cappleapple.astralrepository.api;

import java.util.Map;
import java.util.UUID;

/** Optional external crafting adapter. Call all methods on the owning server thread. */
public interface CraftingProvider {
    String id();
    Object identity();
    boolean valid();
    Map<ItemKey, Long> craftableOutputs();
    /** Accept or reject without creating fake output. An accepted ticket must poll actual backend completion. */
    Ticket request(ItemKey output, long amount, CraftingContext context);
    interface Ticket {
        UUID id();
        State state();
        String message();
        void cancel();
    }
    enum State { RUNNING, COMPLETE, FAILED, CANCELLED }
}