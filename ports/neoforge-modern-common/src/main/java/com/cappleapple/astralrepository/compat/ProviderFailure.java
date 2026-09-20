package com.cappleapple.astralrepository.compat;

/** A backend failed while committing. Do not retry or roll back blindly: its mutation outcome is unknown. */
public final class ProviderFailure extends RuntimeException {
    private final String provider;
    public ProviderFailure(String provider, Throwable cause) {
        super("Provider failed during transfer: " + provider, cause);
        this.provider = provider;
    }
    public String provider() { return provider; }
}