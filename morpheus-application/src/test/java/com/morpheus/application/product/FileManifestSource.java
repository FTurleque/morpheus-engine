package com.morpheus.application.product;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Reads a {@code file:} manifest within the stated size, so that the decisions of the discovery are tested without a
 * transport.
 */
final class FileManifestSource implements UpdateManifestSource {

    static UpdateDiscoveryService discovery() {
        return new UpdateDiscoveryService(new FileManifestSource(), UpdateDiscoveryService.DEFAULT_TIMEOUT);
    }

    /** A discovery whose source fails the test if it is asked for bytes: what it refuses, it refuses before I/O. */
    static UpdateDiscoveryService refusingDiscovery() {
        return new UpdateDiscoveryService((uri, byteLimit, deadline) -> {
            throw new AssertionError("the manifest source must not be called for " + uri);
        }, UpdateDiscoveryService.DEFAULT_TIMEOUT);
    }

    @Override
    public byte[] read(URI manifestUri, int byteLimit, Duration deadline) {
        try (InputStream input = Files.newInputStream(Path.of(manifestUri))) {
            return input.readNBytes(byteLimit + 1);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot read update manifest: " + manifestUri, failure);
        }
    }
}
