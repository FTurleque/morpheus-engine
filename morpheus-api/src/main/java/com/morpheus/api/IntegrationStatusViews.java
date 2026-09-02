package com.morpheus.api;

import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.security.ServerLocationDisclosure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Projects an optional-integration status onto the HTTP surface.
 *
 * <p>{@code GET /api/v1/integrations/{system}/status} is reachable remotely with the READ role, and the MINOS and
 * NEXUS launch settings are server-configured: a remote caller cannot choose them and cannot act on them. Relaying
 * the details verbatim therefore publishes where the server keeps its JAR, its home directory and its JVM, which
 * is the same disclosure the provider-plugin views were changed to stop making.</p>
 *
 * <p>The projection reports whether each location is configured rather than what it is, so an operator reading the
 * response still learns why an integration is unavailable. The CLI keeps the full settings, which is where an
 * operator fixes them.</p>
 */
final class IntegrationStatusViews {
    /** Detail keys whose values are server filesystem locations, reported as configured-or-not. */
    private static final Set<String> LOCATION_DETAIL_KEYS = Set.of("jarPath", "homeDirectory", "javaCommand");

    private IntegrationStatusViews() {
    }

    static Map<String, Object> status(ExternalIntegrationStatus status) {
        Objects.requireNonNull(status, "status");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("system", status.system());
        view.put("state", status.state());
        view.put("configured", status.configured());
        view.put("message", message(status));
        view.put("details", details(status.details()));
        return Map.copyOf(view);
    }

    /**
     * An {@code UNAVAILABLE} status carries the launch failure's own message, which for a process or filesystem
     * failure names the executable or directory it could not reach. The state already says the integration is
     * unreachable, so a message that locates the server is replaced rather than relayed.
     */
    private static String message(ExternalIntegrationStatus status) {
        return containsLocation(status.message())
                ? status.system() + " integration is not reachable; see the local CLI for the launch failure detail"
                : status.message();
    }

    private static Map<String, String> details(Map<String, String> details) {
        Map<String, String> projected = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (LOCATION_DETAIL_KEYS.contains(key)) {
                projected.put(key + "Configured", Boolean.toString(!value.isBlank()));
            } else if (!containsLocation(value)) {
                projected.put(key, value);
            }
        });
        return Map.copyOf(projected);
    }

    /**
     * Shared with the provider-plugin remote projection. A second copy of this predicate would drift, and the
     * weaker copy is the one that leaks.
     */
    private static boolean containsLocation(String value) {
        return ServerLocationDisclosure.namesAServerLocation(value);
    }
}
