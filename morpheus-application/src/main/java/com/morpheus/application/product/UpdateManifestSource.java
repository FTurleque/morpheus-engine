package com.morpheus.application.product;

import java.net.URI;
import java.time.Duration;

/**
 * Reads the bytes of an update manifest that {@link UpdateDiscoveryService} has already accepted: a {@code file:} or
 * an {@code https:} address, never {@code http:}. The discovery states the bounds and the source enforces them where
 * the stream is.
 *
 * <ul>
 *   <li>At most {@code byteLimit + 1} bytes are returned, so that the discovery can refuse a manifest larger than the
 *   limit by name.</li>
 *   <li>A remote body that does not complete within {@code deadline} is refused, and a redirect is never followed.</li>
 *   <li>A failure is an {@link IllegalArgumentException} whose message names the manifest address; an interrupted
 *   read is an {@link IllegalStateException} and keeps the interrupt flag of the calling thread.</li>
 * </ul>
 */
@FunctionalInterface
public interface UpdateManifestSource {
    byte[] read(URI manifestUri, int byteLimit, Duration deadline);
}
