package com.morpheus.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The public HTTP surface: every {@code /api/v1} route template and the methods it accepts, and nothing else.
 *
 * <p>It is the one place that says which methods a route accepts. The local {@code Allow} header is read from it, and
 * the remote route policy refuses to load unless it assigns a role to exactly these routes and methods. It knows
 * nothing of roles, so the local surface reads it without reaching the remote authorization model.</p>
 */
final class MorpheusHttpRouteTable {
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";

    private static final List<Route> ROUTES = List.of(
            route("", GET),
            route("health", GET),
            route("readiness", GET),
            route("metrics", GET),
            route("version", GET),
            route("server/status", GET),
            route("server/backups", POST),
            route("provider-plugins/discover", GET),
            route("provider-plugins/probe", POST),

            route("portfolios", GET, POST),
            route("portfolios/{portfolioId}", GET),
            route("portfolios/{portfolioId}/members", GET),
            route("portfolios/{portfolioId}/projects", POST),
            route("portfolios/{portfolioId}/projects/{projectId}/missing", POST),
            route("portfolios/{portfolioId}/projects/{projectId}/freshness", POST),
            route("portfolios/{portfolioId}/references", GET, POST),
            route("portfolios/{portfolioId}/conflicts", GET),
            route("portfolios/{portfolioId}/traverse", POST),

            route("integrations/minos/status", GET),
            route("integrations/nexus/status", GET),

            route("projects", GET, POST),
            route("projects/{projectId}", GET),
            route("projects/{projectId}/sync", POST),
            route("projects/{projectId}/sync-status", GET),
            route("projects/{projectId}/composition", GET),
            route("projects/{projectId}/composition/conflicts", GET),
            route("projects/{projectId}/specifications", GET),
            route("projects/{projectId}/specifications/{specificationId}", GET),
            route("projects/{projectId}/specifications/{specificationId}/context", GET),

            route("projects/{projectId}/requirements", GET),
            route("projects/{projectId}/requirements/{requirementId}", GET),
            route("projects/{projectId}/requirements/{requirementId}/trace", GET),
            route("projects/{projectId}/requirements/{requirementId}/augmented-context", POST),

            route("projects/{projectId}/changes", GET),
            route("projects/{projectId}/changes/{changeId}", GET),
            route("projects/{projectId}/changes/{changeId}/augmented-context", POST),
            route("projects/{projectId}/changes/{changeId}/transition-check", POST),
            route("projects/{projectId}/changes/{changeId}/lifecycle-transitions", POST),
            route("projects/{projectId}/changes/{changeId}/constraints", GET),
            route("projects/{projectId}/changes/{changeId}/acceptance-criteria", GET),
            route("projects/{projectId}/changes/{changeId}/design-decisions", GET),
            route("projects/{projectId}/changes/{changeId}/implementation-tasks", GET),
            route("projects/{projectId}/changes/{changeId}/context", GET),
            route("projects/{projectId}/changes/{changeId}/status", GET),
            route("projects/{projectId}/changes/{changeId}/blocking-conditions", GET),
            route("projects/{projectId}/changes/{changeId}/orchestration", GET),

            route("projects/{projectId}/versions", GET),
            route("projects/{projectId}/versions/compare", GET),
            route("projects/{projectId}/versions/{snapshotId}/requirements", GET),
            route("projects/{projectId}/diagnostics", GET),
            route("projects/{projectId}/external-references", GET),
            route("projects/{projectId}/external-references/{referenceId}/resolution", GET),

            route("queries/execute", POST),
            route("exports", POST),
            route("saved-views", GET, POST),
            route("saved-views/{viewId}", GET, PUT),
            route("saved-views/{viewId}/versions", GET),
            route("saved-views/{viewId}/execute", POST),
            route("saved-views/{viewId}/archive", POST),
            route("saved-views/{viewId}/export", POST),

            route("policy-packs", GET, POST),
            route("policy-packs/{packId}", GET, PUT),
            route("policy-packs/{packId}/versions", GET),
            route("policy-packs/{packId}/activate", POST),
            route("policy-packs/{packId}/deactivate", POST),
            route("policy-packs/{packId}/audit", GET),
            route("policy-packs/{packId}/overrides/{ruleId}", PUT),
            route("policies/evaluate", POST),
            route("policies/dry-run", POST),
            route("policy-overrides", GET),
            route("policy-activations", GET),
            route("policy-overrides/remove", POST),

            route("reasoning/adapters", GET),
            route("reasoning/analyze", POST));

    private MorpheusHttpRouteTable() {
    }

    /** Every route template below {@code /api/v1} (empty for the root), mapped to its sorted methods, in order. */
    static Map<String, Set<String>> templates() {
        Map<String, Set<String>> templates = new LinkedHashMap<>();
        ROUTES.forEach(route -> templates.put(String.join("/", route.template()), route.methods()));
        return Collections.unmodifiableMap(templates);
    }

    /**
     * Every route as a full {@code /api/v1} path template mapped to the methods it accepts, in declaration order: the
     * only enumeration of the public HTTP surface the code holds.
     */
    static Map<String, Set<String>> declaredRoutes() {
        Map<String, Set<String>> routes = new LinkedHashMap<>();
        templates().forEach((template, methods) -> routes.put(template.isEmpty()
                ? MorpheusHttpServer.API_PREFIX
                : MorpheusHttpServer.API_PREFIX + "/" + template, methods));
        return Collections.unmodifiableMap(routes);
    }

    /** The template, below {@code /api/v1}, of the first route whose shape the segments match. */
    static Optional<String> templateOf(List<String> segments) {
        return ROUTES.stream()
                .filter(route -> route.matches(segments))
                .map(route -> String.join("/", route.template()))
                .findFirst();
    }

    /** The methods of the route the segments match, as an {@code Allow} value; empty when no route matches. */
    static String allowHeader(List<String> segments) {
        return ROUTES.stream()
                .filter(route -> route.matches(segments))
                .map(route -> String.join(", ", route.methods()))
                .findFirst()
                .orElse("");
    }

    private static Route route(String template, String... methods) {
        List<String> segments = template.isEmpty() ? List.of() : List.of(template.split("/"));
        return new Route(segments, Collections.unmodifiableSet(new TreeSet<>(List.of(methods))));
    }

    private record Route(List<String> template, Set<String> methods) {
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
}
