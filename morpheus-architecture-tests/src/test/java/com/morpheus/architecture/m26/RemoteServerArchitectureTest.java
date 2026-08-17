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
        assertTrue(openApi.contains("maximum: 15"));
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
