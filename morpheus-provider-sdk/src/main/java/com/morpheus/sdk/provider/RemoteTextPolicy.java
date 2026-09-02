package com.morpheus.sdk.provider;

import com.morpheus.application.security.ServerLocationDisclosure;

/**
 * Decides whether a free-text value may cross the remote provider-plugin boundary.
 *
 * <p>The projections in {@link ProviderPluginViews} are allowlists: a field reaches a remote caller only because
 * it was named as remote-safe. This is the second gate applied to the values those allowlisted fields carry, and
 * it exists because two of them are not authored by MORPHEUS. A probe result is produced by third-party plugin
 * code, and a diagnostic reason can be derived from a filesystem exception whose message <em>is</em> a pathname.</p>
 *
 * <p>The decision itself lives in {@link ServerLocationDisclosure}, shared with the HTTP adapter's integration
 * status projection. A second copy of this predicate would drift, and the weaker copy is the one that leaks.</p>
 */
final class RemoteTextPolicy {
    /** Bounds what one relayed value can cost, independently of its shape. */
    static final int MAX_VALUE_LENGTH = ServerLocationDisclosure.MAX_RELAYED_LENGTH;

    private RemoteTextPolicy() {
    }

    /** True when the value carries no filesystem location and is safe to relay verbatim. */
    static boolean isRemoteSafe(String value) {
        return ServerLocationDisclosure.isSafeToRelay(value);
    }
}
