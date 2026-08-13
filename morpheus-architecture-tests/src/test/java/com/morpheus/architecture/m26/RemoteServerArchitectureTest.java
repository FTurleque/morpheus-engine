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
    void remoteBoundaryUsesTlsLiveHashedBearerRbacAndNeverForwardsAuthorization() throws IOException {
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
        assertTrue(server.contains("MorpheusRemoteIdentityFile.load(authFile)"));
        assertTrue(server.contains("PLUGIN_SHA256_REQUIRED"));
        assertTrue(server.contains("usesBoundedUpstreamTimeout"));
        assertTrue(server.contains("An ADMIN-approved provider probe executes third-party code"));
        assertTrue(server.contains("String upstreamMethod = exchange.getRequestMethod();"));
        assertFalse(server.contains("providerProbe ? \"GET\""));
        assertFalse(server.contains("request.header(\"Authorization\""));
        assertFalse(server.contains("Access-Control-Allow-Origin"));

        assertTrue(identities.contains("MessageDigest.isEqual"));
        assertTrue(identities.contains("SecureRandom"));
        assertTrue(identities.contains("TOKEN_BYTES = 32"));
        assertTrue(identities.contains("MAX_AUDIT_RECORDS = 512"));
        assertTrue(identities.contains("sha256"));
        assertTrue(identities.contains("FileChannel"));
        assertTrue(identities.contains("FileLock"));
        assertFalse(identities.contains("token + \"|\""));

        assertTrue(remoteOptions.contains("MORPHEUS_SERVER_TLS_PASSWORD"));
        assertFalse(remoteOptions.contains("--tls-password"));
        assertTrue(localOptions.contains("LoopbackHostPolicy.requireLoopback"));
        assertTrue(localServer.contains("LoopbackHostPolicy.requireLoopbackAddress"));
        assertTrue(loopbackPolicy.contains("isLoopbackAddress"));
        assertTrue(loopbackPolicy.contains("requires explicit remote mode"));
    }

    @Test
    void backupRestoreAndPluginProbeContractsAreFailClosed() throws IOException {
        Path root = repositoryRoot();
        String maintenance = Files.readString(root.resolve(
                "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteServerMaintenance.java"));
        String manifest = Files.readString(root.resolve("contracts/public-surfaces.tsv"));
        String openApi = Files.readString(root.resolve("docs/openapi/morpheus-v1-remote-m26.yaml"));
        String discovery = Files.readString(root.resolve(
                "morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/ProviderPluginDiscovery.java"));
        String localServer = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));

        assertTrue(maintenance.contains("VACUUM INTO"));
        assertTrue(maintenance.contains("PRAGMA integrity_check"));
        assertTrue(maintenance.contains("SUPPORTED_SCHEMA_VERSION = 15"));
        assertTrue(maintenance.contains("tryLock"));
        assertTrue(maintenance.contains("ATOMIC_MOVE"));
        assertTrue(maintenance.contains("explicit confirmation"));

        assertTrue(discovery.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(discovery.contains("Files.isSymbolicLink"));

        assertTrue(manifest.contains("server.status\tREAD\tEXPLICITLY_REMOTE_ONLY\tEXPLICITLY_NOT_EXPOSED\tGET /api/v1/server/status"));
        assertTrue(manifest.contains("server.identity.revoke\tWRITE\tserver identity revoke\tEXPLICITLY_NOT_EXPOSED\tEXPLICITLY_LOCAL_ONLY"));
        assertTrue(manifest.contains("server.backup.create\tWRITE\tserver backup create\tEXPLICITLY_NOT_EXPOSED\tPOST /api/v1/server/backups"));
        assertTrue(manifest.contains("server.restore\tWRITE\tserver restore --confirm\tEXPLICITLY_NOT_EXPOSED\tEXPLICITLY_OFFLINE_ONLY"));
        assertTrue(manifest.contains("provider.plugins.probe\tWRITE\tprovider-plugins probe\tEXPLICITLY_NOT_EXPOSED\tPOST /api/v1/provider-plugins/probe"));
        assertTrue(localServer.contains("provider-plugin probe is remote-only"));

        assertTrue(openApi.contains("scheme: bearer"));
        assertTrue(openApi.contains("/server/status:"));
        assertTrue(openApi.contains("/server/backups:"));
        assertTrue(openApi.contains("/provider-plugins/probe:"));
        assertTrue(openApi.contains("name: sha256"));
        assertTrue(openApi.contains("required: true"));
        assertFalse(openApi.contains("/server/restore"));
        assertTrue(openApi.contains("maximum: 512"));
        assertTrue(openApi.contains("maximum: 15"));
    }

    @Test
    void sqliteAndSyncFollowUpHardeningRemainExplicit() throws IOException {
        Path root = repositoryRoot();
        String transactionRunner = Files.readString(root.resolve(
                "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteTransactionRunner.java"));
        String sync = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/sync/IncrementalSyncService.java"));
        String query = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusQueryApiService.java"));
        String cli = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/CliRuntime.java"));

        assertTrue(transactionRunner.contains("catch (Error failure)"));
        assertTrue(transactionRunner.contains("rollbackSuppressing(connection, failure)"));
        assertTrue(sync.contains("SyncBaselineInconsistentException"));
        assertTrue(sync.contains("successfulCommitVisible"));
        assertTrue(sync.contains("baselineInconsistentVisible"));
        assertTrue(sync.contains("SCAN_INCOMPLETE"));
        assertTrue(query.contains("SqliteConnectionScope.open(databasePath)"));
        assertTrue(cli.contains("SqliteConnectionScope.open(databasePath)"));
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
