package com.morpheus.application.product;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Immutable, read-only release metadata used for explicit update discovery. */
public record UpdateManifest(String version, String channel, URI artifactUri, String sha256) {
    public UpdateManifest {
        version = requireText(version, "version");
        channel = requireText(channel, "channel").toLowerCase(Locale.ROOT);
        artifactUri = Objects.requireNonNull(artifactUri, "artifactUri");
        if (!artifactUri.isAbsolute()) {
            throw new IllegalArgumentException("artifactUri must be absolute");
        }
        sha256 = requireText(sha256, "sha256").toLowerCase(Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
