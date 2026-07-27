package com.morpheus.application.security;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Security policy for references found in specification sources. External references may be retained but not fetched implicitly. */
public record ExternalLinkPolicy(boolean followNetworkLinks) {

    public static ExternalLinkPolicy safeDefaults() {
        return new ExternalLinkPolicy(false);
    }

    public boolean mayFollow(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isBlank()) {
            return true;
        }
        String normalized = scheme.toLowerCase(Locale.ROOT);
        if (normalized.equals("http") || normalized.equals("https")) {
            return followNetworkLinks;
        }
        return !normalized.equals("ftp") && !normalized.equals("ftps");
    }
}
