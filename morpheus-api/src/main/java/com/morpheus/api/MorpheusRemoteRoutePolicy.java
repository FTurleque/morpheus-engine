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

    private static final List<RouteRule> ROUTES = List.of(
            route("", Map.of(GET, MorpheusRemoteRole.READ)),
            route("health", Map.of(GET, MorpheusRemoteRole.READ)),
            route("readiness", Map.of(GET, MorpheusRemoteRole.READ)),
            route("metrics", Map.of(GET, MorpheusRemoteRole.ADMIN)),
            route("version", Map.of(GET, MorpheusRemoteRole.READ)),
            route("server/status", Map.of(GET, MorpheusRemoteRole.READ)),
            route("server/backups", Map.of(POST, MorpheusRemoteRole.ADMIN)),
            route("provider-plugins/discover", Map.of(GET, MorpheusRemoteRole.READ)),
            route("provider-plugins/probe", Map.of(POST, MorpheusRemoteRole.ADMIN)),

            route("portfolios", Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("portfolios/{portfolioId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("portfolios/{portfolioId}/members", Map.of(GET, MorpheusRemoteRole.READ)),
            route("portfolios/{portfolioId}/projects", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("portfolios/{portfolioId}/projects/{projectId}/missing", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("portfolios/{portfolioId}/projects/{projectId}/freshness", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("portfolios/{portfolioId}/references",
                    Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("portfolios/{portfolioId}/conflicts", Map.of(GET, MorpheusRemoteRole.READ)),
            route("portfolios/{portfolioId}/traverse", Map.of(POST, MorpheusRemoteRole.WRITE)),

            route("integrations/minos/status", Map.of(GET, MorpheusRemoteRole.READ)),
            route("integrations/nexus/status", Map.of(GET, MorpheusRemoteRole.READ)),

            route("projects", Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/sync", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/sync-status", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/composition", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/composition/conflicts", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/specifications", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/specifications/{specificationId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/specifications/{specificationId}/context", Map.of(GET, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/requirements", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/requirements/{requirementId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/requirements/{requirementId}/trace", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/requirements/{requirementId}/augmented-context",
                    Map.of(POST, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/changes", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/augmented-context", Map.of(POST, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/transition-check", Map.of(POST, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/lifecycle-transitions", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("projects/{projectId}/changes/{changeId}/constraints", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/acceptance-criteria", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/design-decisions", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/implementation-tasks", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/context", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/status", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/blocking-conditions", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/changes/{changeId}/orchestration", Map.of(GET, MorpheusRemoteRole.READ)),

            route("projects/{projectId}/versions", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/versions/compare", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/versions/{snapshotId}/requirements", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/diagnostics", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/external-references", Map.of(GET, MorpheusRemoteRole.READ)),
            route("projects/{projectId}/external-references/{referenceId}/resolution", Map.of(GET, MorpheusRemoteRole.READ)),

            route("queries/execute", Map.of(POST, MorpheusRemoteRole.READ)),
            route("exports", Map.of(POST, MorpheusRemoteRole.READ)),
            route("saved-views", Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("saved-views/{viewId}", Map.of(GET, MorpheusRemoteRole.READ, PUT, MorpheusRemoteRole.WRITE)),
            route("saved-views/{viewId}/versions", Map.of(GET, MorpheusRemoteRole.READ)),
            route("saved-views/{viewId}/execute", Map.of(POST, MorpheusRemoteRole.READ)),
            route("saved-views/{viewId}/archive", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("saved-views/{viewId}/export", Map.of(POST, MorpheusRemoteRole.READ)),

            route("policy-packs", Map.of(GET, MorpheusRemoteRole.READ, POST, MorpheusRemoteRole.WRITE)),
            route("policy-packs/{packId}", Map.of(GET, MorpheusRemoteRole.READ, PUT, MorpheusRemoteRole.WRITE)),
            route("policy-packs/{packId}/versions", Map.of(GET, MorpheusRemoteRole.READ)),
            route("policy-packs/{packId}/activate", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("policy-packs/{packId}/deactivate", Map.of(POST, MorpheusRemoteRole.WRITE)),
            route("policy-packs/{packId}/audit", Map.of(GET, MorpheusRemoteRole.READ)),
            route("policy-packs/{packId}/overrides/{ruleId}", Map.of(PUT, MorpheusRemoteRole.WRITE)),
            route("policies/evaluate", Map.of(POST, MorpheusRemoteRole.READ)),
            route("policies/dry-run", Map.of(POST, MorpheusRemoteRole.READ)),
            route("policy-overrides", Map.of(GET, MorpheusRemoteRole.READ)),
            route("policy-activations", Map.of(GET, MorpheusRemoteRole.READ)),
            route("policy-overrides/remove", Map.of(POST, MorpheusRemoteRole.WRITE)),

            route("reasoning/adapters", Map.of(GET, MorpheusRemoteRole.READ)),
            route("reasoning/analyze", Map.of(POST, MorpheusRemoteRole.READ)));

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

    /**
     * Timeout classification is only consulted after authorization in the live remote request path. Returning false for
     * an unknown path keeps this helper conservative for direct callers while {@link #requiredRole(String, String)}
     * remains the fail-closed authorization gate.
     */
    static boolean usesBoundedUpstreamTimeout(String rawMethod, String path) {
        try {
            return requiredRole(rawMethod, path) == MorpheusRemoteRole.READ;
        } catch (RoutePolicyException failure) {
            if (failure.status() == 404) return false;
            throw failure;
        }
    }

    private static RouteRule requireRoute(String path) {
        List<String> segments = apiSegments(path);
        return ROUTES.stream()
                .filter(route -> route.matches(segments))
                .findFirst()
                .orElseThrow(() -> new RoutePolicyException(404, "NOT_FOUND", "unknown remote API path"));
    }

    private static RouteRule route(String template, Map<String, MorpheusRemoteRole> methods) {
        List<String> segments = template.isEmpty() ? List.of() : List.of(template.split("/"));
        return new RouteRule(segments, Map.copyOf(methods));
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
