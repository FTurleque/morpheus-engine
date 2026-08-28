package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusRemoteProxyTargetResolverTest {
    private static final int LOCAL_PORT = 8123;
    private static final String VALID_SHA_UPPER = "A".repeat(64);

    @TempDir
    Path tempDirectory;

    @Test
    void plainRoutesKeepRawPathAndQueryUntouched() throws IOException {
        Fixture fixture = fixture();

        URI target = fixture.resolver().resolve(URI.create("/api/v1/projects?name=a%20b&ref=%2Fraw"));
        URI withoutQuery = fixture.resolver().resolve(URI.create("/api/v1/health"));

        assertEquals("http://127.0.0.1:" + LOCAL_PORT + "/api/v1/projects?name=a%20b&ref=%2Fraw", target.toString());
        assertEquals("http://127.0.0.1:" + LOCAL_PORT + "/api/v1/health", withoutQuery.toString());
    }

    @Test
    void discoverInjectsOnlyServerConfiguredPluginDirectory() throws IOException {
        Fixture fixture = fixture();

        URI target = fixture.resolver().resolve(URI.create("/api/v1/provider-plugins/discover?flag"));
        Map<String, String> query = decodedQuery(target);

        assertEquals("", query.get("flag"));
        assertEquals(fixture.pluginDirectory().toAbsolutePath().normalize().toString(), query.get("directory"));
        assertEquals(2, query.size());
    }

    @Test
    void clientCannotOverrideServerConfiguredPluginDirectory() throws IOException {
        Fixture fixture = fixture();

        MorpheusRemoteProxyTargetResolver.ResolutionException failure = assertThrows(
                MorpheusRemoteProxyTargetResolver.ResolutionException.class,
                () -> fixture.resolver().resolve(URI.create(
                        "/api/v1/provider-plugins/discover?directory=%2Ftmp%2Fevil")));

        assertEquals(400, failure.status());
        assertEquals("SERVER_CONFIGURED_PLUGIN_DIRECTORY", failure.code());
        assertEquals(
                "provider-plugin directory is configured by the remote server and must not be supplied by the client",
                failure.getMessage());
    }

    @Test
    void probeNormalizesIntegrityPinAndInjectsCanonicalAllowedWorkspace() throws IOException {
        Fixture fixture = fixture();
        Path workspace = Files.createDirectory(fixture.workspaceRoot().resolve("project"));
        String encodedWorkspace = URLEncoder.encode(workspace.toString(), StandardCharsets.UTF_8);

        URI target = fixture.resolver().resolve(URI.create(
                "/api/v1/provider-plugins/probe?sha256=" + VALID_SHA_UPPER + "&workspace=" + encodedWorkspace));
        Map<String, String> query = decodedQuery(target);

        assertEquals("a".repeat(64), query.get("sha256"));
        assertEquals(workspace.toRealPath().toString(), query.get("workspace"));
        assertEquals(fixture.pluginDirectory().toAbsolutePath().normalize().toString(), query.get("directory"));
        assertEquals(3, query.size());
    }

    @Test
    void probeRequiresValidIntegrityPin() throws IOException {
        Fixture fixture = fixture();

        MorpheusRemoteProxyTargetResolver.ResolutionException missing = assertThrows(
                MorpheusRemoteProxyTargetResolver.ResolutionException.class,
                () -> fixture.resolver().resolve(URI.create("/api/v1/provider-plugins/probe")));
        MorpheusRemoteProxyTargetResolver.ResolutionException blank = assertThrows(
                MorpheusRemoteProxyTargetResolver.ResolutionException.class,
                () -> fixture.resolver().resolve(URI.create("/api/v1/provider-plugins/probe?sha256=")));
        MorpheusRemoteProxyTargetResolver.ResolutionException invalid = assertThrows(
                MorpheusRemoteProxyTargetResolver.ResolutionException.class,
                () -> fixture.resolver().resolve(URI.create("/api/v1/provider-plugins/probe?sha256=abc")));

        assertEquals("PLUGIN_SHA256_REQUIRED", missing.code());
        assertEquals("PLUGIN_SHA256_REQUIRED", blank.code());
        assertEquals("PLUGIN_SHA256_INVALID", invalid.code());
        assertEquals(400, invalid.status());
    }

    @Test
    void probeKeepsGenericWorkspaceValidationAsBadRequestInput() throws IOException {
        Fixture fixture = fixture();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.resolver().resolve(URI.create(
                        "/api/v1/provider-plugins/probe?sha256=" + VALID_SHA_UPPER)));

        assertEquals("workspace is required", failure.getMessage());
    }

    @Test
    void providerQueryRejectsBlankAndDuplicateParameterNames() throws IOException {
        Fixture fixture = fixture();

        MorpheusRemoteProxyTargetResolver.ResolutionException blank = assertThrows(
                MorpheusRemoteProxyTargetResolver.ResolutionException.class,
                () -> fixture.resolver().resolve(URI.create("/api/v1/provider-plugins/discover?=value")));
        MorpheusRemoteProxyTargetResolver.ResolutionException duplicate = assertThrows(
                MorpheusRemoteProxyTargetResolver.ResolutionException.class,
                () -> fixture.resolver().resolve(URI.create("/api/v1/provider-plugins/discover?x=1&&x=2")));

        assertEquals("BAD_REQUEST", blank.code());
        assertEquals("query parameter name must not be blank", blank.getMessage());
        assertEquals("BAD_REQUEST", duplicate.code());
        assertEquals("duplicate query parameter: x", duplicate.getMessage());
    }

    private Fixture fixture() throws IOException {
        Path pluginDirectory = Files.createDirectory(tempDirectory.resolve("plugins"));
        Path workspaceRoot = Files.createDirectory(tempDirectory.resolve("workspaces"));
        AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(workspaceRoot));
        return new Fixture(
                new MorpheusRemoteProxyTargetResolver(LOCAL_PORT, pluginDirectory, roots),
                pluginDirectory,
                workspaceRoot);
    }

    private static Map<String, String> decodedQuery(URI uri) {
        Map<String, String> result = new LinkedHashMap<>();
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) return result;
        for (String part : uri.getRawQuery().split("&")) {
            int separator = part.indexOf('=');
            String key = URLDecoder.decode(separator < 0 ? part : part.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(separator < 0 ? "" : part.substring(separator + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private record Fixture(
            MorpheusRemoteProxyTargetResolver resolver,
            Path pluginDirectory,
            Path workspaceRoot) {
    }
}
