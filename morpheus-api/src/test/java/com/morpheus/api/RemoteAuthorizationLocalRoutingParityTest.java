package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote role policy and the local server read a request path alike.
 *
 * <p>The remote server authorizes a request against the route its path names, then forwards the path as it was sent to
 * the local server, which selects a route on its own. Two readings of one path are two chances to disagree: the policy
 * used to match the path the JDK had decoded, while the local server decoded each segment of that path a second time
 * with a form decoder. They disagreed exactly on the paths a caller would craft -- a doubly encoded literal was no
 * route for the policy and a route for the server.</p>
 *
 * <p>Each path below is given to the policy and sent to a real local server with a method no route accepts. The
 * server then answers which route it selected without running it: a 405 whose {@code Allow} lists the methods of that
 * route, or a 404 when it selected none. The policy must have named the same methods, or none. A router that checks
 * the method before the path answers an unknown path with a 405 whose {@code Allow} is empty, which RFC 9110 reads as
 * no method at all: that is no route either.</p>
 */
class RemoteAuthorizationLocalRoutingParityTest {
    private static final String UNROUTED = "no route";
    private static final List<String> PATHS = List.of(
            "/api/v1/health",
            "/api/v1/health/",
            "/api/v1/health//",
            "/api/v1//health",
            "/api/v1//",
            "/api/v1health",
            "/%61pi/v1/health",
            "/api%2Fv1/health",
            "/api/v1/saved-views/x/archive",
            "/api/v1/saved-views/x/%61rchive",
            "/api/v1/saved-views/x/%2561rchive",
            "/api/v1/saved-views/x/%41rchive",
            "/api/v1/saved-views/a%2Fb",
            "/api/v1/saved-views/a%2fb/versions",
            "/api/v1/saved-views/a%252Fb",
            "/api/v1/saved-views%2Fx",
            "/api/v1/saved-views/a+b",
            "/api/v1/saved-views/a%2Bb",
            "/api/v1/saved-views/%2e%2e",
            "/api/v1/saved-views/%2E%2E/versions",
            "/api/v1/saved-views/%252e%252e",
            "/api/v1/saved-viewsx",
            "/api/v1/policy-packsx",
            "/api/v1/policy-packs/x/overrides/a%2Fb",
            "/api/v1/policy-packs/x%2Foverrides/y",
            "/api/v1/policies/%2565valuate",
            "/api/v1/queries%2Fexecute",
            "/api/v1/reasoning%2Fadapters",
            "/api/v1/policy-activations%2Fx",
            "/api/v1/projects/p/versions/%2563ompare",
            "/api/v1/projects/p%2Fversions",
            "/api/v1/provider-plugins%2Fprobe",
            "/api/v1/provider-plugins/%2570robe",
            "/api/v1/server%2Fstatus");

    @TempDir
    Path temporaryDirectory;

    @Test
    void thePolicyAndTheLocalServerSelectTheSameRouteForEveryEncodingOfAPath() throws Exception {
        List<String> disagreements = new ArrayList<>();
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("parity.db"), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            URI base = URI.create(server.baseUri().toString());
            for (String path : PATHS) {
                String authorized = authorizedRoute(path);
                String served = servedRoute(client, base, path);
                if (!authorized.equals(served)) {
                    disagreements.add(path + " -> policy: " + authorized + ", local server: " + served);
                }
            }
        }

        assertEquals(List.of(), disagreements);
    }

    @Test
    void theControlPathsAreRoutesSoThatAgreementIsNotAgreementOnNothing() {
        assertEquals("GET", authorizedRoute("/api/v1/health"));
        assertEquals("POST", authorizedRoute("/api/v1/saved-views/x/archive"));
        assertEquals("POST", authorizedRoute("/api/v1/saved-views/x/%61rchive"));
        assertEquals("GET, PUT", authorizedRoute("/api/v1/saved-views/a%2Fb"));
        assertEquals(UNROUTED, authorizedRoute("/api/v1/saved-views/x/%2561rchive"));
    }

    private static String authorizedRoute(String rawPath) {
        String allowed = MorpheusRemoteRoutePolicy.allowedMethods(rawPath);
        return allowed.isEmpty() ? UNROUTED : allowed;
    }

    private static String servedRoute(HttpClient client, URI base, String rawPath) throws Exception {
        URI target = URI.create("http://" + base.getRawAuthority() + rawPath);
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(target).method("DELETE", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 405) {
            String allowed = response.headers().firstValue("Allow").orElse("");
            return allowed.isEmpty() ? UNROUTED : allowed;
        }
        assertTrue(response.statusCode() == 404 && response.body().contains("\"code\":\"NOT_FOUND\""),
                () -> rawPath + " answered neither a refused method nor an unknown route: "
                        + response.statusCode() + " " + response.body());
        return UNROUTED;
    }
}
