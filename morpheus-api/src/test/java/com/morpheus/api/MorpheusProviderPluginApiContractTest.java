package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusProviderPluginApiContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void discoveryIsExplicitMetadataOnlyAndMissingDirectoryIsNonFatal() {
        Path database = tempDirectory.resolve("morpheus.db");
        String missing = tempDirectory.resolve("missing-plugins").toString();
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response response = http.get(
                    server,
                    "/provider-plugins/discover?directory=" + encode(missing));

            assertEquals(200, response.status(), response.body());
            assertTrue(response.body().contains("\"compatibleCount\":0"), response.body());
            assertTrue(response.body().contains("PLUGIN_DIRECTORY_NOT_FOUND"), response.body());
        }
    }

    @Test
    void discoveryMethodAndQueryValidationRemainStableAfterExtraction() {
        Path database = tempDirectory.resolve("validation.db");
        String missing = tempDirectory.resolve("missing-plugins").toString();
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response wrongMethod = http.post(
                    server,
                    "/provider-plugins/discover?directory=" + encode(missing));
            ApiTestSupport.Response unknownQuery = http.get(
                    server,
                    "/provider-plugins/discover?directory=" + encode(missing) + "&unexpected=true");

            assertEquals(405, wrongMethod.status(), wrongMethod.body());
            assertTrue(wrongMethod.body().contains("expected HTTP GET but received POST"), wrongMethod.body());
            assertEquals(400, unknownQuery.status(), unknownQuery.body());
            assertTrue(unknownQuery.body().contains("unexpected query parameter: unexpected"), unknownQuery.body());
        }
    }

    @Test
    void ordinaryLocalHttpDoesNotExposeExecutableProbe() {
        Path database = tempDirectory.resolve("morpheus.db");
        String query = "/provider-plugins/probe?directory=" + encode(tempDirectory.toString())
                + "&pluginId=example&workspace=" + encode(tempDirectory.toString())
                + "&sha256=" + "0".repeat(64);
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response get = http.get(server, query);
            ApiTestSupport.Response post = http.post(server, query);

            assertEquals(404, get.status(), get.body());
            assertEquals(404, post.status(), post.body());
            assertTrue(get.body().contains("remote-only"), get.body());
            assertTrue(post.body().contains("remote-only"), post.body());
        }
    }

    @Test
    void unknownProviderPluginRouteAndExtraSegmentsKeepTheirDistinct404Contracts() {
        Path database = tempDirectory.resolve("routing.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response unknown = http.get(server, "/provider-plugins/unknown");
            ApiTestSupport.Response extra = http.get(server, "/provider-plugins/discover/extra");

            assertEquals(404, unknown.status(), unknown.body());
            assertTrue(unknown.body().contains("unknown provider-plugin route"), unknown.body());
            assertEquals(404, extra.status(), extra.body());
            assertTrue(extra.body().contains("unknown API route: /api/v1/provider-plugins/discover/extra"), extra.body());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
