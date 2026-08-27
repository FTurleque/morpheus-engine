package com.morpheus.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Exhaustive remote route policy.
 *
 * <p>Every remotely reachable API route is explicitly registered with the HTTP methods and minimum role it accepts.
 * Unknown paths fail closed with 404 and a known path invoked with an unregistered method fails with 405. In
 * particular, a future GET endpoint can never inherit READ authority merely because of its HTTP verb.</p>
 */
final class MorpheusRemoteRoutePolicy {
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";

    private static final List<RouteRule> ROUTES = List.of(
            route("metrics", Map.of(GET, MorpheusRemoteRole.ADMIN)),
            route("server/status", Map.of(GET, MorpheusRemoteRole.READ)),
            route("server/backups", Map.of(POST, MorpheusRemoteRole.ADMIN)),
            route("provider-plugins/discover", Map.of(GET, MorpheusRemoteRole.READ)),
            route("provider-plugins/probe", Map.of(POST, MorpheusRemoteRole.ADMIN)),

            route("queries/execute", Map.of(POST, MorpheusRemoteRole.READ)),
            route("executions/{executionId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("saved-views", Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("saved-views/{viewId}", Map.of(PUT, MorpheusRemoteRole.WRITE, DELETE, MorpheusRemoteRole.WRITE)),
            route("saved-views/{viewId}/execute", Map.of(POST, MorpheusRemoteRole.READ)),
            route("saved-views/{viewId}/export", Map.of(POST, MorpheusRemoteRole.READ)),
            route("exports", Map.of(POST, MorpheusRemoteRole.READ)),
            route("policies/evaluate", Map.of(POST, MorpheusRemoteRole.READ)),
            route("policies/dry-run", Map.of(POST, MorpheusRemoteRole.READ)),
            route("policy-runs/{executionId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("reasoning/analyze", Map.of(POST, MorpheusRemoteRole.READ)),
            route("reasoning/executions/{executionId}", Map.of(GET, MorpheusRemoteRole.READ)),

            route("projects", Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/providers", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/providers/{providerId}/sync", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/providers/{providerId}/specs", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/providers/{providerId}/spec-content", Map.of(GET, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/requirements",
                    Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/requirements/{requirementId}", Map.of(PUT, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/requirements/{requirementId}/lifecycle", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/requirements/{requirementId}/augmented-context",
                    Map.of(POST, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/changes", Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/changes/{changeId}", Map.of(PUT, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/changes/{changeId}/lifecycle", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/changes/{changeId}/augmented-context", Map.of(POST, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/transition-check", Map.of(POST, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/external-references",
                    Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/external-references/{referenceId}", Map.of(PUT, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/external-references/{referenceId}/resolve", Map.of(POST, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/overrides", Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/overrides/{ruleId}", Map.of(DELETE, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/conflicts", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/health", Map.of(GET, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/compositions",
                    Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/compositions/{compositionId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/compositions/{compositionId}/activate", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/compositions/{compositionId}/deactivate", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/compositions/{compositionId}/validate", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("compositions/{compositionId}/resolve", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("compositions/{compositionId}/simulate", Map.of(POST, MorpheusRemoteRole.WRITE)),

            route("providers/{providerId}/download-timeout", Map.of(GET, MorpheusRemoteRole.READ)),
            route("workspace/plugins", Map.of(GET, MorpheusRemoteRole.READ)),
            route("workspace/mcp-servers", Map.of(GET, MorpheusRemoteRole.READ)),
            route("providers/{providerId}/probe", Map.of(POST, MorpheusRemoteRole.WRITE)),

            route("projects/{projectId}/provider-baselines", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/provider-baselines/{providerId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/provider-baselines/{providerId}/versions", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/provider-baselines/{providerId}/versions/{versionId}",
                    Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/provider-baselines/{providerId}/versions/{versionId}/content",
                    Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/provider-baselines/{providerId}/versions/{versionId}/restore",
                    Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/provider-baselines/{providerId}/refresh", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/provider-baselines/{providerId}/integrity", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/provider-baselines/{providerId}/lifecycle", Map.of(GET, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/policies",
                    Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/policies/{scopeType}/{scopeKey}", Map.of(
                    GET, MorpheusRemoteRole.READ,
                    PUT, MorpheusRemoteRole.WRITE,
                    DELETE, MorpheusRemoteRole.WRITE)));

    private MorpheusRemoteRoutePolicy() {
    }

    static MorpheusRemoteRole requiredRole(String rawMethod, String path) {
        String method = normalizeMethod(rawMethod);
        RouteRule route = requireRoute(path);
        MorpheusRemoteRole role = route.methods().get(method);
        if (role == null) {
            throw new RoutePolicyException(405, "METHOD_NOT_ALLOWED", "HTTP method is not allowed for remote API route");
        }
        return role;
    }

    static boolean usesBoundedUpstreamTimeout(String rawMethod, String path) {
        return requiredRole(rawMethod, path) == MorpheusRemoteRole.READ;
    }

    private static RouteRule requireRoute(String path) {
        List<String> segments = apiSegments(path);
        return ROUTES.stream()
                .filter(route -> route.matches(segments))
                .findFirst()
                .orElseThrow(() -> new RoutePolicyException(404, "NOT_FOUND", "unknown remote API path"));
    }

    private static RouteRule route(String template, Map<String, MorpheusRemoteRole> methods) {
        return new RouteRule(List.of(template.split("/")), Map.copyOf(methods));
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

    private record RouteRule(List<String> template, Map<String, MorpheusRemoteRole> methods) {
        private boolean matches(List<String> actual) {
            if (template.size() != actual.size()) return false;
            for (int index = 0; index < template.size(); index++) {
                String expected = template.get(index);
                if (isVariable(expected)) continue;
                if (!expected.equals(actual.get(index))) return false;
            }
            return true;
        }

        private static boolean isVariable(String segment) {
            return segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}");
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
