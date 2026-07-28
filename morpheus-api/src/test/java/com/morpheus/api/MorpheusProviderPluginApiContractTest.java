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
    void probeRequiresEveryExplicitParameter() {
        Path database = tempDirectory.resolve("morpheus.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response response = http.get(
                    server,
                    "/provider-plugins/probe?directory=" + encode(tempDirectory.toString()));

            assertEquals(400, response.status(), response.body());
            assertTrue(response.body().contains("query parameter is required: pluginId"), response.body());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
