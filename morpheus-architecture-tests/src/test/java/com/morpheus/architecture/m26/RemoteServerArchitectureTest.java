package com.morpheus.architecture.m26;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteServerArchitectureTest {

    @Test
    void domainAndApplicationDoNotDependOnRemoteServerAdapters() {
        var classes = new ClassFileImporter().importPackages("com.morpheus");
        noClasses()
                .that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..cli..", "..api..")
                .check(classes);
    }

    @Test
    void remoteSurfaceManifestKeepsAdministrativeAndRemoteBoundariesExplicit() throws IOException {
        String manifest = Files.readString(repositoryRoot().resolve("contracts/public-surfaces.tsv"));

        assertTrue(manifest.contains("server.status\tREAD\tEXPLICITLY_REMOTE_ONLY\tEXPLICITLY_NOT_EXPOSED\tGET /api/v1/server/status"));
        assertTrue(manifest.contains("server.identity.revoke\tWRITE\tserver identity revoke\tEXPLICITLY_NOT_EXPOSED\tEXPLICITLY_LOCAL_ONLY"));
        assertTrue(manifest.contains("server.backup.create\tWRITE\tserver backup create\tEXPLICITLY_NOT_EXPOSED\tPOST /api/v1/server/backups"));
        assertTrue(manifest.contains("server.restore\tWRITE\tserver restore --confirm\tEXPLICITLY_NOT_EXPOSED\tEXPLICITLY_OFFLINE_ONLY"));
        assertTrue(manifest.contains("provider.plugins.probe\tWRITE\tprovider-plugins probe\tEXPLICITLY_NOT_EXPOSED\tPOST /api/v1/provider-plugins/probe"));
    }

    @Test
    void remoteOpenApiRequiresBearerAuthenticationAndPluginIntegrityPin() throws IOException {
        String openApi = Files.readString(repositoryRoot().resolve("docs/openapi/morpheus-v1-remote-m26.yaml"));

        assertTrue(openApi.contains("scheme: bearer"));
        assertTrue(openApi.contains("/server/status:"));
        assertTrue(openApi.contains("/server/backups:"));
        assertTrue(openApi.contains("/provider-plugins/probe:"));
        assertTrue(openApi.contains("name: sha256"));
        assertTrue(openApi.contains("required: true"));
    }

    @Test
    void remoteOpenApiKeepsOfflineRestorePrivateAndPublicLimitsBounded() throws IOException {
        String openApi = Files.readString(repositoryRoot().resolve("docs/openapi/morpheus-v1-remote-m26.yaml"));

        assertFalse(openApi.contains("/server/restore"));
        assertTrue(openApi.contains("maximum: 512"));
        assertTrue(openApi.contains("maximum: 16"));
        assertTrue(openApi.contains("maxProxyResponseBytes"));
        assertTrue(openApi.contains("maxProxyInFlightBytes"));
        assertTrue(openApi.contains("maxConcurrentBufferedProxyResponses"));
    }

    @Test
    void remoteRuntimeStateStaysExtractedAndPolicyFree() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String runtime = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteRuntimeState.java"));

        assertTrue(server.contains("private final MorpheusRemoteRuntimeState runtime;"));
        assertFalse(server.contains("class RuntimeState"));
        assertTrue(runtime.contains("final class MorpheusRemoteRuntimeState"));
        assertFalse(runtime.contains("MorpheusRemoteRole"));
        assertFalse(runtime.contains("MorpheusRemoteIdentityFile"));
    }

    @Test
    void remoteResponseRenderingStaysExtractedAndPolicyFree() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String responses = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteResponseWriter.java"));

        assertTrue(server.contains("private final MorpheusRemoteResponseWriter responses"));
        assertFalse(server.contains("CanonicalJsonSerializer"));
        assertFalse(server.contains("private void sendJson("));
        assertFalse(server.contains("private static void applySecurityHeaders("));
        assertTrue(responses.contains("final class MorpheusRemoteResponseWriter"));
        assertTrue(responses.contains("Content-Security-Policy"));
        assertFalse(responses.contains("MorpheusRemoteRole"));
        assertFalse(responses.contains("MorpheusRemoteIdentityFile"));
        assertFalse(responses.contains("MorpheusRemoteRoutePolicy"));
    }

    @Test
    void remoteProxyTargetResolutionStaysExtractedAndTransportFree() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String resolver = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteProxyTargetResolver.java"));

        assertTrue(server.contains("private final MorpheusRemoteProxyTargetResolver proxyTargets;"));
        assertFalse(server.contains("private URI localTarget("));
        assertFalse(server.contains("private static Map<String, String> parseQuery("));
        assertFalse(server.contains("private static String encodeQuery("));
        assertFalse(server.contains("URLDecoder"));
        assertFalse(server.contains("URLEncoder"));
        assertTrue(resolver.contains("final class MorpheusRemoteProxyTargetResolver"));
        assertTrue(resolver.contains("SERVER_CONFIGURED_PLUGIN_DIRECTORY"));
        assertTrue(resolver.contains("PLUGIN_SHA256_REQUIRED"));
        assertFalse(resolver.contains("HttpClient"));
        assertFalse(resolver.contains("MorpheusRemoteRole"));
        assertFalse(resolver.contains("MorpheusRemoteIdentityFile"));
        assertFalse(resolver.contains("MorpheusRemoteRoutePolicy"));
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate MORPHEUS repository root");
    }
}
