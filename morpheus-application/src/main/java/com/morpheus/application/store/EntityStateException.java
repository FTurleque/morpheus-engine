package com.morpheus.application.store;

/**
 * The request is well formed, but the persisted state refuses it: a relation it requires is absent, or performing it
 * would leave the state in a condition the system forbids.
 *
 * <p>It stays an {@link IllegalArgumentException}: a transport that answers every refused request the same way keeps
 * doing so. A transport that distinguishes a state refusal from a malformed request catches this type first.</p>
 */
public final class EntityStateException extends IllegalArgumentException {
    public EntityStateException(String message) {
        super(message);
    }
}
