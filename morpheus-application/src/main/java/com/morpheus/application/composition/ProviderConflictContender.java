package com.morpheus.application.composition;

import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;

/** One concrete provider contribution participating in a logical-entity conflict. */
public record ProviderConflictContender(
        ProviderId providerId,
        String entityId,
        int precedence) {

    public ProviderConflictContender {
        Objects.requireNonNull(providerId, "providerId");
        entityId = requireNonBlank(entityId, "entityId");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
