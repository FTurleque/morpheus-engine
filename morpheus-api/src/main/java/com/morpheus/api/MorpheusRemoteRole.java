package com.morpheus.api;

/** Closed M26 remote-server authorization roles. */
public enum MorpheusRemoteRole {
    READ,
    WRITE,
    ADMIN;

    public boolean allows(MorpheusRemoteRole required) {
        return ordinal() >= required.ordinal();
    }
}
