package com.morpheus.cli;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Late-resolving handle on the remote TLS keystore password.
 *
 * <p>The JVM already holds this secret as a {@link String} the moment it comes from {@code System.getenv()}, and
 * nothing in MORPHEUS can erase that. What MORPHEUS can decide is whether it makes a second,
 * longer-lived copy of its own -- and it used to: the parsed launch options carried the password as a field for
 * the entire lifetime of the running server, and the record's generated {@code toString()} would have printed
 * it into any diagnostic that rendered the options.</p>
 *
 * <p>This holds the way back to the original value instead of the value itself. The password becomes a
 * {@code char[]} only at the moment the keystore is opened, and the caller wipes that buffer as soon as the
 * {@link javax.net.ssl.SSLContext} exists. Presence is still checked while parsing, so a misconfigured launch
 * fails before anything is started -- it is only the value that is not retained.</p>
 *
 * <p>The environment is the only source. A JVM property is not: {@code -Dmorpheus.server.tls.password=...} is a
 * command-line argument, readable by every account through {@code /proc/<pid>/cmdline} on a default Linux host.
 * Restoring that fallback turns {@code RemoteApiLaunchOptionsTest#aTlsPasswordGivenOnlyAsAJvmPropertyIsRefused}
 * red, and the textual guard in {@code RemoteHttpServerBootstrapArchitectureTest} with it (observed 2026-09-22).
 * See ADR-0105.</p>
 */
final class TlsKeystorePassword {
    static final String MISSING =
            "remote mode requires the TLS keystore password in the MORPHEUS_SERVER_TLS_PASSWORD environment variable";

    private final Supplier<Optional<String>> source;

    TlsKeystorePassword(Supplier<Optional<String>> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Resolves the password into a buffer the caller owns and must wipe.
     *
     * <p>Resolving again is deliberate rather than cached: a cache would be exactly the additional long-lived
     * copy this type exists to avoid.</p>
     */
    char[] resolve() {
        return source.get()
                .orElseThrow(() -> new IllegalArgumentException(MISSING))
                .toCharArray();
    }

    boolean isPresent() {
        return source.get().isPresent();
    }

    /** Never renders the secret: this type exists so that no diagnostic can reach it by accident. */
    @Override
    public String toString() {
        return "TlsKeystorePassword[value=<redacted>]";
    }
}
