package com.morpheus.sdk.provider;

import java.util.Locale;

/**
 * Decides whether a free-text value may cross the remote provider-plugin boundary.
 *
 * <p>The projections in {@link ProviderPluginViews} are allowlists: a field reaches a remote caller only because
 * it was named as remote-safe. This is the second gate applied to the values those allowlisted fields carry, and
 * it exists because two of them are not authored by MORPHEUS. A probe result is produced by third-party plugin
 * code, and a diagnostic reason can be derived from a filesystem exception whose message <em>is</em> a pathname.</p>
 *
 * <p>The policy rejects rather than rewrites. A value that looks like a filesystem location is dropped and the
 * caller sees the stable code instead, so a mistake here costs detail and never discloses a location. It is not a
 * sanitizer: nothing is stripped out of a value and returned, because a partially scrubbed pathname is still a
 * disclosure.</p>
 */
final class RemoteTextPolicy {
    /** Bounds what one relayed value can cost, independently of its shape. */
    static final int MAX_VALUE_LENGTH = 512;

    private RemoteTextPolicy() {
    }

    /** True when the value carries no filesystem location and is safe to relay verbatim. */
    static boolean isRemoteSafe(String value) {
        if (value == null || value.length() > MAX_VALUE_LENGTH) {
            return false;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            return true;
        }
        return !containsFilesystemLocation(candidate);
    }

    private static boolean containsFilesystemLocation(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('\\', '/');
        return normalized.contains("file:")
                || normalized.startsWith("//")
                || normalized.contains(" //")
                || startsWithPosixAbsolutePath(normalized)
                || containsPosixAbsolutePathSegment(normalized)
                || containsWindowsDriveRoot(normalized)
                || normalized.contains("~/")
                || value.indexOf('\\') >= 0;
    }

    private static boolean startsWithPosixAbsolutePath(String normalized) {
        return normalized.startsWith("/");
    }

    /** A pathname quoted inside a longer sentence, which is how filesystem exceptions report one. */
    private static boolean containsPosixAbsolutePathSegment(String normalized) {
        int index = normalized.indexOf('/');
        while (index > 0) {
            char previous = normalized.charAt(index - 1);
            if (previous == ' ' || previous == '\'' || previous == '"' || previous == '(' || previous == '[') {
                return true;
            }
            index = normalized.indexOf('/', index + 1);
        }
        return false;
    }

    /** Matches {@code c:/} anywhere, so a drive-rooted pathname inside a sentence is caught too. */
    private static boolean containsWindowsDriveRoot(String normalized) {
        for (int index = 0; index + 2 < normalized.length() + 1 && index + 1 < normalized.length(); index++) {
            char letter = normalized.charAt(index);
            if (letter < 'a' || letter > 'z' || normalized.charAt(index + 1) != ':') {
                continue;
            }
            boolean startsToken = index == 0 || !Character.isLetterOrDigit(normalized.charAt(index - 1));
            boolean rooted = index + 2 < normalized.length() && normalized.charAt(index + 2) == '/';
            if (startsToken && rooted) {
                return true;
            }
        }
        return false;
    }
}
