package com.morpheus.application.store;

/**
 * The request names an entity by its identifier and no such entity exists.
 *
 * <p>It stays an {@link IllegalArgumentException}: a transport that answers every refused request the same way keeps
 * doing so. A transport that distinguishes a missing entity from a malformed request catches this type first.</p>
 */
public final class EntityNotFoundException extends IllegalArgumentException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
