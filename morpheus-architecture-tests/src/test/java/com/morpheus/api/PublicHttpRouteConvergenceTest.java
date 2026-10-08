package com.morpheus.api;

import com.morpheus.architecture.m21.PublicSurfaceManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The public HTTP surface is served, declared in {@code contracts/public-surfaces.tsv} and documented in
 * {@code docs/openapi/} consistently, in both directions, route by route (method and path).
 *
 * <p>The served routes are read from {@link MorpheusHttpRouteTable#declaredRoutes()}, the only enumeration of the
 * HTTP surface the code holds: {@code MorpheusRemoteRoutePolicy} refuses to load unless its roles cover exactly that
 * table, and the local {@code Allow} header is computed from it. A table is a declaration too, so
 * {@link #theLocalServerDispatchesEveryRouteTheTableLists} binds it to the real dispatcher: a route the table lists and
 * the local server does not route fails it. The reverse -- a route the dispatcher serves and the table omits -- cannot
 * be enumerated from outside; such a route is refused remotely with 404, which {@code MorpheusRemoteRoutePolicy}
 * documents as fail-closed.</p>
 *
 * <p>Path parameters are compared by position, not by name: the manifest writes {@code {id}}, OpenAPI
 * {@code {savedViewId}} and the table {@code {viewId}} for the same segment.</p>
 *
 * <p>This class lives in {@code com.morpheus.api} because the route table is package-private, as
 * {@code com.morpheus.mcp.PublicSurfaceManifestCoversEveryServedToolTest} does for the MCP tool list.</p>
 */
class PublicHttpRouteConvergenceTest {
    private static final Pattern PARAMETER = Pattern.compile("\\{[^}/]+}");
    private static final Pattern OPENAPI_PATH = Pattern.compile("^  (/[^\\s:]*):\\s*$");
    private static final Pattern OPENAPI_OPERATION = Pattern.compile("^    (get|put|post|delete|patch|head|options):\\s*$");
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^[A-Za-z]");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"message\":\"([^\"]*)\"");
    private static final String ABSENT_ID = "01920000-0000-7000-8000-0000000000ff";
    private static final List<String> PROBE_METHODS = List.of("GET", "POST", "PUT", "DELETE");

    /** Served by the remote server only; the local server must refuse them. */
    private static final Map<String, String> REMOTE_ONLY = Map.of(
            "GET /api/v1/server/status", "answered by MorpheusRemoteHttpServer itself, never proxied",
            "POST /api/v1/server/backups", "answered by MorpheusRemoteHttpServer itself, never proxied",
            "POST /api/v1/provider-plugins/probe", "runs third-party code; refused locally as remote-only (M22)");

    /**
     * Served routes the manifest deliberately has no line for, each with its reason. The manifest lists capabilities,
     * and these four are transport operability probes, not capabilities (maintainer decision, 8 October 2026); every
     * other served route has a line.
     */
    private static final Map<String, String> NOT_IN_MANIFEST = Map.of(
            "GET /api/v1", "API root descriptor: the version and the entry points of this transport",
            "GET /api/v1/health", "liveness probe of the HTTP transport",
            "GET /api/v1/readiness", "readiness probe of the HTTP transport",
            "GET /api/v1/metrics", "operability counters and timings of the HTTP transport");

    @TempDir
    Path temporaryDirectory;

    @Test
    void everyServedRouteIsDocumentedInOpenApi() throws IOException {
        Set<String> documented = normalized(documentedRoutes());

        List<String> undocumented = servedRoutes().stream()
                .filter(route -> !documented.contains(normalize(route)))
                .toList();

        assertEquals(List.of(), undocumented, "served routes no OpenAPI document describes");
    }

    @Test
    void everyDocumentedRouteIsServed() throws IOException {
        Set<String> served = normalized(servedRoutes());

        List<String> unserved = documentedRoutes().stream()
                .filter(route -> !served.contains(normalize(route)))
                .toList();

        assertEquals(List.of(), unserved, "OpenAPI operations no server registers");
    }

    @Test
    void everyManifestRouteIsServedAndDocumented() throws IOException {
        Set<String> served = normalized(servedRoutes());
        Set<String> documented = normalized(documentedRoutes());
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, String> line : manifestRoutes().entrySet()) {
            if (!served.contains(normalize(line.getValue()))) {
                problems.add(line.getKey() + " names an unserved route: " + line.getValue());
            }
            if (!documented.contains(normalize(line.getValue()))) {
                problems.add(line.getKey() + " names an undocumented route: " + line.getValue());
            }
        }

        assertEquals(List.of(), problems);
    }

    @Test
    void everyServedRouteHasAManifestLineOrAReasonedExclusion() throws IOException {
        Set<String> declared = normalized(manifestRoutes().values());

        List<String> missing = servedRoutes().stream()
                .filter(route -> !declared.contains(normalize(route)))
                .filter(route -> !NOT_IN_MANIFEST.containsKey(route))
                .toList();

        assertEquals(List.of(), missing, "served routes with no manifest line and no reasoned exclusion");
    }

    @Test
    void everyExclusionNamesAServedRouteThatStillNeedsIt() throws IOException {
        Set<String> served = new TreeSet<>(servedRoutes());
        Set<String> declared = normalized(manifestRoutes().values());
        List<String> stale = new ArrayList<>();

        for (String route : NOT_IN_MANIFEST.keySet()) {
            if (!served.contains(route)) stale.add(route + " is excluded from the manifest but no longer served");
            if (declared.contains(normalize(route))) stale.add(route + " is excluded from the manifest but declared");
        }
        for (String route : REMOTE_ONLY.keySet()) {
            if (!served.contains(route)) stale.add(route + " is declared remote-only but no longer served");
        }

        assertEquals(List.of(), stale);
        NOT_IN_MANIFEST.values().forEach(reason -> assertFalse(reason.isBlank()));
    }

    @Test
    void theLocalServerDispatchesEveryRouteTheTableLists() throws Exception {
        Path database = temporaryDirectory.resolve("convergence.db");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        List<String> problems = new ArrayList<>();

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            for (String route : servedRoutes()) {
                String method = route.substring(0, route.indexOf(' '));
                String path = PARAMETER.matcher(route.substring(route.indexOf(' ') + 1)).replaceAll(ABSENT_ID);
                HttpResponse<String> response = client.send(request(server, method, path),
                        HttpResponse.BodyHandlers.ofString());
                boolean refusedAsUnrouted = response.statusCode() == 405 || unroutedNotFound(response);
                if (REMOTE_ONLY.containsKey(route)) {
                    if (response.statusCode() != 404) {
                        problems.add(route + " is remote-only but the local server answered " + response.statusCode());
                    }
                } else if (refusedAsUnrouted) {
                    problems.add(route + " is listed by the route table but not dispatched: "
                            + response.statusCode() + " " + response.body());
                }
            }
        }

        assertEquals(List.of(), problems);
    }

    /**
     * The binding above reads the dispatcher's refusals by their text, so a reworded refusal could make every route
     * look dispatched. Each route is therefore also probed two segments deeper, where nothing is routed (one segment
     * would reach the {@code /{id}} sibling of a collection): every family must still be refused as unrouted, which
     * proves the classifier recognises the text each router emits today, and that no router answers a path it does
     * not declare.
     */
    @Test
    void anUnroutedPathUnderEveryRouteIsRecognisedAsUnrouted() throws Exception {
        Path database = temporaryDirectory.resolve("unrouted.db");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        List<String> problems = new ArrayList<>();

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            for (String route : servedRoutes()) {
                if (REMOTE_ONLY.containsKey(route)) continue;
                String method = route.substring(0, route.indexOf(' '));
                String path = PARAMETER.matcher(route.substring(route.indexOf(' ') + 1)).replaceAll(ABSENT_ID)
                        + "/morpheus-unrouted/morpheus-unrouted";
                HttpResponse<String> response = client.send(request(server, method, path),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 405 && !unroutedNotFound(response)) {
                    problems.add(method + " " + path + " -> " + response.statusCode() + " " + response.body());
                }
            }
        }

        assertEquals(List.of(), problems);
    }

    /**
     * A 405 names, in {@code Allow}, exactly the methods the route accepts (RFC 9110 section 15.5.6): each route is
     * sent the first of {@link #PROBE_METHODS} it does not accept, and the header is compared as a set.
     */
    @Test
    void everyRouteRefusesAnUnacceptedMethodWithItsExactAllowHeader() throws Exception {
        Path database = temporaryDirectory.resolve("allow.db");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        List<String> problems = new ArrayList<>();

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            for (Map.Entry<String, Set<String>> route : MorpheusHttpRouteTable.declaredRoutes().entrySet()) {
                Set<String> accepted = route.getValue();
                if (accepted.stream().anyMatch(method -> REMOTE_ONLY.containsKey(method + " " + route.getKey()))) {
                    continue;
                }
                String refused = PROBE_METHODS.stream().filter(method -> !accepted.contains(method))
                        .findFirst().orElseThrow();
                String path = PARAMETER.matcher(route.getKey()).replaceAll(ABSENT_ID);
                HttpResponse<String> response = client.send(request(server, refused, path),
                        HttpResponse.BodyHandlers.ofString());
                String allow = response.headers().firstValue("Allow").orElse(null);
                if (response.statusCode() != 405) {
                    problems.add(refused + " " + route.getKey() + " -> " + response.statusCode() + ", expected 405");
                } else if (allow == null || !accepted.equals(methods(allow))) {
                    problems.add(refused + " " + route.getKey() + " -> Allow: " + allow + ", expected " + accepted);
                }
            }
        }

        assertEquals(List.of(), problems);
    }

    private static Set<String> methods(String allow) {
        Set<String> methods = new TreeSet<>();
        for (String method : allow.split(",")) {
            if (!method.isBlank()) methods.add(method.trim().toUpperCase(Locale.ROOT));
        }
        return methods;
    }

    /** A 404 that says the route does not exist, as opposed to a 404 for a resource the route did not find. */
    private static boolean unroutedNotFound(HttpResponse<String> response) {
        if (response.statusCode() != 404) return false;
        Matcher message = ERROR_MESSAGE.matcher(response.body());
        if (!message.find()) return true;
        String text = message.group(1);
        return text.equals("invalid API path") || (text.startsWith("unknown ") && text.contains("route"))
                || text.startsWith("unknown project API resource") || text.startsWith("unknown integration");
    }

    private static HttpRequest request(MorpheusHttpServer server, String method, String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://" + server.host() + ":" + server.port() + path));
        if (method.equals("GET")) {
            return builder.GET().build();
        }
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString("{}"))
                .build();
    }

    /** {@code METHOD /api/v1/path} for every method of every route the table lists, in declaration order. */
    private static List<String> servedRoutes() {
        List<String> routes = new ArrayList<>();
        MorpheusHttpRouteTable.declaredRoutes().forEach((path, methods) ->
                methods.forEach(method -> routes.add(method + " " + path)));
        return routes;
    }

    /** Every operation of every OpenAPI document, prefixed by {@code /api/v1}, the server URL of all of them. */
    private static List<String> documentedRoutes() throws IOException {
        List<String> routes = new ArrayList<>();
        List<Path> documents;
        try (Stream<Path> files = Files.list(repoRoot().resolve("docs/openapi"))) {
            documents = files.filter(file -> file.getFileName().toString().endsWith(".yaml")).sorted().toList();
        }
        for (Path document : documents) {
            boolean inPaths = false;
            String path = null;
            for (String line : Files.readAllLines(document)) {
                if (TOP_LEVEL_KEY.matcher(line).lookingAt()) {
                    inPaths = line.startsWith("paths:");
                    path = null;
                    continue;
                }
                if (!inPaths) continue;
                Matcher pathKey = OPENAPI_PATH.matcher(line);
                if (pathKey.matches()) {
                    path = pathKey.group(1).equals("/")
                            ? MorpheusHttpServer.API_PREFIX
                            : MorpheusHttpServer.API_PREFIX + pathKey.group(1);
                    continue;
                }
                Matcher operation = OPENAPI_OPERATION.matcher(line);
                if (operation.matches() && path != null) {
                    routes.add(operation.group(1).toUpperCase(Locale.ROOT) + " " + path);
                }
            }
        }
        return routes;
    }

    /** Capability to route, for every manifest line whose {@code http} cell names a route rather than a sentinel. */
    private static Map<String, String> manifestRoutes() throws IOException {
        Map<String, String> routes = new TreeMap<>();
        for (String[] row : PublicSurfaceManifest.rows(repoRoot())) {
            String cell = row[PublicSurfaceManifest.HTTP].trim();
            if (!cell.startsWith(PublicSurfaceManifest.SENTINEL_PREFIX)) {
                routes.put(row[PublicSurfaceManifest.CAPABILITY], cell);
            }
        }
        return routes;
    }

    private static Set<String> normalized(Collection<String> routes) {
        Set<String> result = new TreeSet<>();
        routes.forEach(route -> result.add(normalize(route)));
        return result;
    }

    private static String normalize(String route) {
        return PARAMETER.matcher(route).replaceAll("{}");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(PublicSurfaceManifest.MANIFEST))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("repository root with " + PublicSurfaceManifest.MANIFEST + " not found");
    }
}
