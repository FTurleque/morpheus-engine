package com.morpheus.application.security;

import java.util.Locale;
import java.util.Objects;

/**
 * Decides whether a free-text value names a location on the server's filesystem.
 *
 * <p>Adapters that answer callers outside the machine project their internal models onto records that enumerate
 * what they carry. This is the second gate, applied to the values those enumerated fields hold, and it exists
 * because some of them are not authored by MORPHEUS: a provider probe result is produced by third-party plugin
 * code, and a failure message is produced by the platform — {@link java.nio.file.AccessDeniedException} reports
 * the pathname and nothing else.</p>
 *
 * <p>Callers reject rather than rewrite. A value that names a location is dropped or replaced by a stable code,
 * so a false positive costs detail while a false negative would disclose the server's layout. Nothing is stripped
 * out of a value and relayed, because a partially scrubbed pathname is still a pathname.</p>
 *
 * <p>This lives beside the other boundary primitives rather than in one adapter because both the HTTP adapter and
 * the provider SDK need exactly this decision. Two copies drift, and the weaker copy is the one that leaks.</p>
 */
public final class ServerLocationDisclosure {
    /** Bounds what one relayed value can cost, independently of its shape. */
    public static final int MAX_RELAYED_LENGTH = 512;

    private ServerLocationDisclosure() {
    }

    /** True when the value is short enough to relay and names no filesystem location. */
    public static boolean isSafeToRelay(String value) {
        if (value == null || value.length() > MAX_RELAYED_LENGTH) {
            return false;
        }
        String candidate = value.trim();
        return candidate.isEmpty() || !namesAServerLocation(candidate);
    }

    /** True when the value contains a filesystem location in any rendering that reaches a boundary. */
    public static boolean namesAServerLocation(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.toLowerCase(Locale.ROOT).replace('\\', '/');
        return value.indexOf('\\') >= 0
                || normalized.contains("file:")
                || normalized.startsWith("/")
                || normalized.startsWith("//")
                || normalized.contains("~/")
                || containsQuotedOrSpacedPath(normalized)
                || containsWindowsDriveRoot(normalized);
    }

    /**
     * A pathname embedded in a longer sentence. Process and filesystem failures quote it — {@code Cannot run
     * program "/usr/bin/java"} — or simply follow a space with it, so the delimiter set covers both.
     */
    private static boolean containsQuotedOrSpacedPath(String normalized) {
        int index = normalized.indexOf('/');
        while (index > 0) {
            char previous = normalized.charAt(index - 1);
            if (previous == ' ' || previous == '"' || previous == '\'' || previous == '('
                    || previous == '[' || previous == '=' || previous == ':' || previous == ',') {
                return true;
            }
            index = normalized.indexOf('/', index + 1);
        }
        return false;
    }

    /** Matches {@code c:/} at a token boundary, so a drive-rooted pathname inside a sentence is caught too. */
    private static boolean containsWindowsDriveRoot(String normalized) {
        for (int index = 0; index + 2 < normalized.length(); index++) {
            char letter = normalized.charAt(index);
            if (letter < 'a' || letter > 'z'
                    || normalized.charAt(index + 1) != ':'
                    || normalized.charAt(index + 2) != '/') {
                continue;
            }
            if (index == 0 || !Character.isLetterOrDigit(normalized.charAt(index - 1))) {
                return true;
            }
        }
        return false;
    }
}
