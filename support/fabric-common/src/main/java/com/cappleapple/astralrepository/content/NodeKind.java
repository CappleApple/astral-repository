package com.cappleapple.astralrepository.content;

public enum NodeKind {
    NEXUS, RELAY, DISTRIBUTION, COLLECTION, ROUTING, BUFFER, REMOTE, GATEWAY, POWER, STORAGE;

    public boolean remote() { return this == REMOTE || this == GATEWAY; }
}
