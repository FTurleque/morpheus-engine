package com.morpheus.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Explicit remote route policy. Mutating methods fail closed to WRITE unless a concrete read-only route is listed. */
final class MorpheusRemoteRoutePolicy {
    private MorpheusRemoteRoutePolicy() {
    }

    static MorpheusRemoteRole requiredRole(String rawMethod, String path) {
        String method = normalizeMethod(rawMethod);
        List<String> segments = apiSegments(path);

        if (segments.equals(List.of("metrics"))) return MorpheusRemoteRole.ADMIN;
        if (segments.equals(List.of("server", "backups"))) return MorpheusRemoteRole.ADMIN;
        if (segments.equals(List.of("server", "status"))) return MorpheusRemoteRole.READ;

        if (segments.equals(List.of("provider-plugins", "discover"))) {
            requireMethod(method, "GET", "provider-plugin discovery requires GET");
            return MorpheusRemoteRole.READ;
        }
        if (segments.equals(List.of("provider-plugins", "probe"))) {
            requireMethod(method, "POST", "provider-plugin probe requires POST");
            return MorpheusRemoteRole.ADMIN;
        }

        if (method.equals("GET") || method.equals("HEAD")) return MorpheusRemoteRole.READ;
        if (method.equals("POST") && isExplicitReadOnlyPost(segments)) return MorpheusRemoteRole.READ;
        if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE")) {
            return MorpheusRemoteRole.WRITE;
        }
        throw new RoutePolicyException(405, "METHOD_NOT_ALLOWED", "unsupported remote HTTP method");
    }

    static boolean usesBoundedUpstreamTimeout(String rawMethod, String path) {
        String method = normalizeMethod(rawMethod);
        List<String> segments = apiSegments(path);
        if (method.equals("GET") || method.equals("HEAD")) return true;
        return method.equals("POST") && isExplicitReadOnlyPost(segments);
    }

    static boolean isExplicitReadOnlyPostPath(String path) {
        return isExplicitReadOnlyPost(apiSegments(path));
    }

    private static boolean isExplicitReadOnlyPost(List<String> segments) {
        if (segments.equals(List.of("queries", "execute"))
                || segments.equals(List.of("exports"))
                || segments.equals(List.of("policies", "evaluate"))
                || segments.equals(List.of("policies", "dry-run"))
                || segments.equals(List.of("reasoning", "analyze"))) {
            return true;
        }
        if (segments.size() == 3 && segments.getFirst().equals("saved-views")) {
            String action = segments.get(2);
            return action.equals("execute") || action.equals("export");
        }
        if (segments.size() == 5 && segments.getFirst().equals("projects")) {
            String resource = segments.get(2);
            String action = segments.get(4);
            if (resource.equals("requirements")) return action.equals("augmented-context");
            if (resource.equals("changes")) {
                return action.equals("augmented-context") || action.equals("transition-check");
            }
        }
        // Retain the legacy read-only external-reference resolution contract without using suffix/contains matching.
        return segments.size() == 5
                && segments.getFirst().equals("projects")
                && segments.get(2).equals("external-references")
                && segments.get(4).equals("resolve");
    }

    private static List<String> apiSegments(String path) {
        Objects.requireNonNull(path, "path");
        String prefix = MorpheusHttpServer.API_PREFIX;
        if (path.equals(prefix) || path.equals(prefix + "/")) return List.of();
        if (!path.startsWith(prefix + "/")) {
            throw new RoutePolicyException(404, "NOT_FOUND", "unknown remote API path");
        }
        String suffix = path.substring(prefix.length() + 1);
        if (suffix.endsWith("/")) suffix = suffix.substring(0, suffix.length() - 1);
        if (suffix.isEmpty()) return List.of();
        List<String> segments = new ArrayList<>();
        for (String segment : suffix.split("/", -1)) {
            if (segment.isEmpty()) {
                throw new RoutePolicyException(404, "NOT_FOUND", "invalid remote API path");
            }
            segments.add(segment);
        }
        return List.copyOf(segments);
    }

    private static String normalizeMethod(String rawMethod) {
        return Objects.requireNonNull(rawMethod, "rawMethod").toUpperCase(Locale.ROOT);
    }

    private static void requireMethod(String actual, String expected, String message) {
        if (!actual.equals(expected)) {
            throw new RoutePolicyException(405, "METHOD_NOT_ALLOWED", message);
        }
    }

    static final class RoutePolicyException extends RuntimeException {
        private final int status;
        private final String code;

        private RoutePolicyException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        int status() {
            return status;
        }

        String code() {
            return code;
        }
    }
}
