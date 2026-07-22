package com.morpheus.application.identity;

import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;

/** Provider-scoped external identity key used only for identity resolution. */
public record EntityIdentityKey(ProviderId providerId, String entityType, String externalId) {
    public EntityIdentityKey {
        Objects.requireNonNull(providerId, "providerId");
        entityType = requireNonBlank(entityType, "entityType");
        externalId = requireNonBlank(externalId, "externalId");
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
