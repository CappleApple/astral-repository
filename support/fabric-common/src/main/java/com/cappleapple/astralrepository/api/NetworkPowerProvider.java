package com.cappleapple.astralrepository.api;

import net.minecraft.resources.Identifier;

/** A consumable fuel or a live capacity requirement, such as available Create stress capacity. */
public interface NetworkPowerProvider {
    String id();
    /** Shared source identity, used to avoid counting one capacity source more than once. */
    default Object identity() { return id(); }
    Identifier resourceType();
    Mode mode();
    boolean valid();
    double available();
    /** For consumables: drain units. For capacity: reserve a share, without pretending it is stored energy. */
    double acquire(double amount, boolean simulate);
    /** Release a capacity reservation. Consumable providers do nothing. */
    default void release(double amount) { }
    enum Mode { CONSUMABLE, CAPACITY }
}