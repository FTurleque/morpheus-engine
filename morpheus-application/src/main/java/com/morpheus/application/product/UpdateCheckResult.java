package com.morpheus.application.product;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Result of one explicit, read-only update discovery operation. */
public record UpdateCheckResult(
        String currentVersion,
        String availableVersion,
        String channel,
        URI artifactUri,
        String sha256,
        URI manifestUri,
        Optional<URI> attestationUri,
        UpdateTrustLevel trustLevel,
        boolean updateAvailable) {

    /**
     * Source-compatible constructor for callers that only need the historical discovery fields.
     *
     * <p>Such a result has no propagated provenance reference and is explicitly discovery-only.</p>
     */
    public UpdateCheckResult(
            String currentVersion,
            String availableVersion,
            String channel,
            URI artifactUri,
            String sha256,
            URI manifestUri,
            boolean updateAvailable) {
        this(
                currentVersion,
                availableVersion,
                channel,
                artifactUri,
                sha256,
                manifestUri,
                Optional.empty(),
                UpdateTrustLevel.DISCOVERY_ONLY,
                updateAvailable);
    }

    public UpdateCheckResult {
        currentVersion = requireText(currentVersion, "currentVersion");
        availableVersion = requireText(availableVersion, "availableVersion");
        channel = requireText(channel, "channel");
        artifactUri = Objects.requireNonNull(artifactUri, "artifactUri");
        sha256 = requireText(sha256, "sha256");
        manifestUri = Objects.requireNonNull(manifestUri, "manifestUri");
        attestationUri = Objects.requireNonNull(attestationUri, "attestationUri");
        trustLevel = Objects.requireNonNull(trustLevel, "trustLevel");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
