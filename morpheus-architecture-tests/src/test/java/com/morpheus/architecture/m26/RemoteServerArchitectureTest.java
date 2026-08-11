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
    void remoteBoundaryUsesTlsHashedBearerRbacAndNeverForwardsAuthorization() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String identities = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteIdentityFile.java"));
        String remoteOptions = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/RemoteApiLaunchOptions.java"));
        String localOptions = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/ApiLaunchOptions.java"));
        String localServer = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String loopbackPolicy = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/LoopbackHostPolicy.java"));

        assertTrue(server.contains("HttpsServer"));
        assertTrue(server.contains("PKCS12"));
        assertTrue(server.contains("TLSv1.3"));
        assertTrue(server.contains("TLSv1.2"));
        assertTrue(server.contains("MorpheusRemoteRole.ADMIN"));
        assertTrue(server.contains("TOO_MANY_REQUESTS"));
        assertTrue(server.contains("X-Frame-Options"));
        assertTrue(server.contains("Content-Security-Policy"));
        assertFalse(server.contains("request.header(\"Authorization\""));
        assertFalse(server.contains("Access-Control-Allow-Origin"));

        assertTrue(identities.contains("MessageDigest.isEqual"));
        assertTrue(identities.contains("SecureRandom"));
        assertTrue(identities.contains("TOKEN_BYTES = 32"));
        assertTrue(identities.contains("sha256"));
        assertFalse(identities.contains("token + \"|\""));

        assertTrue(remoteOptions.contains("MORPHEUS_SERVER_TLS_PASSWORD"));
        assertFalse(remoteOptions.contains("--tls-password"));
        assertTrue(localOptions.contains("LoopbackHostPolicy.requireLoopback"));
        assertTrue(localServer.contains("LoopbackHostPolicy.requireLoopbackAddress"));
        assertTrue(loopbackPolicy.contains("isLoopbackAddress"));
        assertTrue(loopbackPolicy.contains("requires explicit remote mode"));
    }

    @Test
    void backupRestoreIsVerifiedOfflineAndRemoteRestoreIsNotExposed() throws IOException {
        Path root = repositoryRoot();
        String maintenance = Files.readString(root.resolve(
                "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteServerMaintenance.java"));
        String manifest = Files.readString(root.resolve("contracts/public-surfaces.tsv"));
        String openApi = Files.readString(root.resolve("docs/openapi/morpheus-v1-remote-m26.yaml"));

        assertTrue(maintenance.contains("VACUUM INTO"));
        assertTrue(maintenance.contains("PRAGMA integrity_check"));
        assertTrue(maintenance.contains("SUPPORTED_SCHEMA_VERSION = 15"));
        assertTrue(maintenance.contains("tryLock"));
        assertTrue(maintenance.contains("ATOMIC_MOVE"));
        assertTrue(maintenance.contains("explicit confirmation"));

        assertTrue(manifest.contains("server.status\tREAD\tEXPLICITLY_REMOTE_ONLY\tEXPLICITLY_NOT_EXPOSED\tGET /api/v1/server/status"));
        assertTrue(manifest.contains("server.backup.create\tWRITE\tserver backup create\tEXPLICITLY_NOT_EXPOSED\tPOST /api/v1/server/backups"));
        assertTrue(manifest.contains("server.restore\tWRITE\tserver restore --confirm\tEXPLICITLY_NOT_EXPOSED\tEXPLICITLY_OFFLINE_ONLY"));

        assertTrue(openApi.contains("scheme: bearer"));
        assertTrue(openApi.contains("/server/status:"));
        assertTrue(openApi.contains("/server/backups:"));
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
