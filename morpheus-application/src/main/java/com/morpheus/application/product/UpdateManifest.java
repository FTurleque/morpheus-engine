package com.morpheus.application.product;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Immutable, read-only release metadata used for explicit update discovery. */
public record UpdateManifest(
        String version,
        String channel,
        URI artifactUri,
        String sha256,
        Optional<URI> attestationUri) {

    public UpdateManifest(String version, String channel, URI artifactUri, String sha256) {
        this(version, channel, artifactUri, sha256, Optional.empty());
    }

    public UpdateManifest {
        version = requireText(version, "version");
        channel = requireText(channel, "channel").toLowerCase(Locale.ROOT);
        artifactUri = requireAllowedUri(artifactUri, "artifactUri");
        sha256 = requireText(sha256, "sha256").toLowerCase(Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
        }
        Objects.requireNonNull(attestationUri, "attestationUri");
        attestationUri = attestationUri.map(uri -> requireAllowedUri(uri, "attestationUri"));
    }

    /**
     * Remote manifests are only considered suitable for a future installer when both the artifact and its provenance
     * are transported over HTTPS. Local file manifests remain available for explicit diagnostics and test fixtures.
     */
    void requireRemoteTrust(URI manifestUri) {
        Objects.requireNonNull(manifestUri, "manifestUri");
        if (!"https".equalsIgnoreCase(manifestUri.getScheme())) return;
        if (!"https".equalsIgnoreCase(artifactUri.getScheme())) {
            throw new IllegalArgumentException("remote update artifactUri must use https");
        }
        URI attestation = attestationUri.orElseThrow(() -> new IllegalArgumentException(
                "remote update manifest must declare attestationUri"));
        if (!"https".equalsIgnoreCase(attestation.getScheme())) {
            throw new IllegalArgumentException("remote update attestationUri must use https");
        }
    }

    private static URI requireAllowedUri(URI uri, String name) {
        Objects.requireNonNull(uri, name);
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be absolute");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("file") && !scheme.equals("https")) {
            throw new IllegalArgumentException(name + " must use file or https");
        }
        return uri;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
