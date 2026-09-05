package com.morpheus.architecture.m26;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * The remote backup response identifies the backup; it does not locate it.
     *
     * <p>The backup directory is server-configured and restore is {@code EXPLICITLY_OFFLINE_ONLY}, so a remote
     * ADMIN has no use for the absolute pathname. The local CLI keeps it, because an operator passes it straight
     * back to {@code server backup verify --file}.</p>
     */
    @Test
    void remoteBackupResponseNamesTheFileWhileTheLocalCliKeepsThePathname() throws IOException {
        Path root = repositoryRoot();
        String remoteServer = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String cli = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/MorpheusServerCli.java"));
        String openApi = Files.readString(root.resolve("docs/openapi/morpheus-v1-remote-m26.yaml"));

        assertTrue(remoteServer.contains("view.put(\"fileName\", fileNameOf(backup.path()))"),
                "the remote backup view must project the backup to its file name");
        assertFalse(remoteServer.contains("view.put(\"path\", backup.path().toString())"),
                "the remote backup view must not expose the server's absolute backup pathname");
        assertTrue(cli.contains("view.put(\"path\", backup.path().toString())"),
                "the local CLI backup view must keep the pathname an operator passes back to --file");
        assertTrue(openApi.contains("required: [fileName, bytes, sha256, schemaVersion, integrityOk]"),
                "the remote OpenAPI backup schema must require fileName rather than path");
    }

    /**
     * Every HTTP surface that reports a server-side location must name it rather than locate it.
     *
     * <p>These three were found one after another and share a cause: an internal model carrying an absolute
     * pathname was rendered straight onto a response that a remote role can read. The CLI keeps the pathname in
     * each case, which is where an operator acts on it.</p>
     */
    @Test
    void httpSurfacesNameServerSideLocationsWhileTheCliKeepsThem() throws IOException {
        Path root = repositoryRoot();
        String projects = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectRegistryApiService.java"));
        String integrations = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/IntegrationStatusViews.java"));
        String cli = Files.readString(root.resolve("morpheus-cli/src/main/java/com/morpheus/cli/MorpheusCli.java"));

        assertTrue(projects.contains("result.put(\"workspaceName\", workspaceName(entry.rootLocator()))"),
                "the HTTP project view must name the workspace");
        assertFalse(projects.contains("result.put(\"workspace\", entry.rootLocator().value())"),
                "the HTTP project view must not expose the absolute workspace pathname");
        assertTrue(cli.contains("new ProjectView(item.id().toString(), item.rootLocator().value())"),
                "the local CLI must keep the workspace pathname an operator passes back to --workspace");

        assertTrue(integrations.contains("LOCATION_DETAIL_KEYS"),
                "integration launch locations must be reported as configured rather than named");
        assertTrue(integrations.contains("ServerLocationDisclosure.namesAServerLocation"),
                "the integration projection must consult the shared boundary predicate");

        String policy = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/security/ServerLocationDisclosure.java"));
        assertTrue(policy.contains("public static boolean isSafeToRelay("),
                "the boundary predicate must stay shared rather than duplicated per adapter");
    }

    /**
     * A response bound equal to the current schema version silently rots into rejecting valid responses at the
     * next migration, so the published ceiling must stay strictly above the normative constant.
     */
    @Test
    void remoteBackupSchemaCeilingCannotRotBelowTheSupportedSchemaVersion() throws IOException {
        Path root = repositoryRoot();
        String manager = Files.readString(root.resolve(
                "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java"));
        Matcher declaration = Pattern.compile("SUPPORTED_SCHEMA_VERSION\\s*=\\s*(\\d+)").matcher(manager);
        assertTrue(declaration.find(), "SqliteSchemaManager must declare SUPPORTED_SCHEMA_VERSION");
        int supported = Integer.parseInt(declaration.group(1));

        String openApi = Files.readString(root.resolve("docs/openapi/morpheus-v1-remote-m26.yaml"));
        Matcher ceiling = Pattern.compile("schemaVersion:\\s*\\R\\s*type: integer\\s*\\R\\s*minimum: \\d+\\s*\\R\\s*maximum: (\\d+)")
                .matcher(openApi);
        assertTrue(ceiling.find(), "the remote OpenAPI must bound the reported schemaVersion");
        assertTrue(Integer.parseInt(ceiling.group(1)) > supported,
                () -> "the published schemaVersion ceiling " + ceiling.group(1)
                        + " must stay above SUPPORTED_SCHEMA_VERSION " + supported);
    }

    @Test
    void remoteSurfaceManifestKeepsAdministrativeAndRemoteBoundariesExplicit() throws IOException {
        String manifest = Files.readString(repositoryRoot().resolve("contracts/public-surfaces.tsv"));

        assertTrue(manifest.contains("server.status\tREAD\tEXPLICITLY_REMOTE_ONLY\tEXPLICITLY_NOT_EXPOSED\tGET /api/v1/server/status"));
        assertTrue(manifest.contains("server.identity.revoke\tWRITE\tserver identity revoke\tEXPLICITLY_NOT_EXPOSED\tEXPLICITLY_LOCAL_ONLY"));
        assertTrue(manifest.contains("server.backup.create\tWRITE\tserver backup create\tEXPLICITLY_NOT_EXPOSED\tPOST /api/v1/server/backups"));
        assertTrue(manifest.contains("server.restore\tWRITE\tserver restore --confirm\tEXPLICITLY_NOT_EXPOSED\tEXPLICITLY_OFFLINE_ONLY"));
        assertTrue(manifest.contains("provider.plugins.probe\tWRITE\tprovider-plugins probe\tEXPLICITLY_NOT_EXPOSED\tPOST /api/v1/provider-plugins/probe"));
        assertTrue(manifest.contains(
                "server.identity.migrate-legacy\tWRITE\tserver identity migrate-legacy\tEXPLICITLY_NOT_EXPOSED\tEXPLICITLY_LOCAL_ONLY"));
    }

    /**
     * The three-field identity entry stays valid input, and leaving it behind stays an explicit operator act.
     *
     * <p>Expiring a credential nobody asked to change is how a remote server locks its own administrators out,
     * so the compatibility must not decay into an implicit conversion -- and the way out must not decay into
     * something that rotates tokens, which would break every client at once instead of giving them a deadline.</p>
     */
    @Test
    void leavingTheNonExpiringIdentityFormatStaysExplicitAndNeverRotatesTokens() throws IOException {
        Path root = repositoryRoot();
        String identityFile = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteIdentityFile.java"));
        String serverCli = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/MorpheusServerCli.java"));
        String guide = Files.readString(root.resolve("docs/user/TEAM_REMOTE_SERVER.md"));

        assertTrue(identityFile.contains("public static LegacyMigration migrateLegacyExpiry("),
                "an operable way out of non-expiring identities must exist");
        assertTrue(identityFile.contains("requireAdministratorOutliving(updated, expiry)"),
                "the migration must refuse to schedule an ADMIN lockout");
        assertTrue(identityFile.contains("Mutation.EXPIRY_MIGRATED"),
                "the migration must leave secret-free audit evidence");
        assertFalse(identityFile.contains("newCredential(normalizedPrincipal, identity.role()"),
                "the migration must never rotate token material");

        assertTrue(serverCli.contains("\"migrate-legacy\" -> identityMigrateLegacy(parsed, out)"));
        assertTrue(serverCli.contains("view.put(\"nonExpiringIdentities\", nonExpiring);"),
                "the listing must keep non-expiring identities visible to an operator");
        assertTrue(serverCli.contains("view.put(\"tokensRotated\", false);"));

        assertTrue(guide.contains("server identity migrate-legacy"),
                "the operator guide must document the way out");
        assertTrue(guide.contains("reste supporté en 1.2.1"),
                "the guide must keep saying the historical format is still supported");
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

    /**
     * A remote mutation has no upstream deadline, so a blocked one holds its slots until it really ends. That is
     * only tolerable while an operator can see it: the privileged gauge and the age of the oldest privileged
     * operation are the difference between a busy facade and a stuck one, and they must not rot back into an
     * aggregate request count that also moves with ordinary read traffic.
     */
    @Test
    void remoteStatusExposesPrivilegedOccupancyAndIsServedOutsideTheRequestBudget() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String runtime = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteRuntimeState.java"));

        assertTrue(runtime.contains("activePrivilegedRequests"),
                "remote status must expose how many privileged operations are in flight");
        assertTrue(runtime.contains("maxConcurrentPrivilegedRequests"));
        assertTrue(runtime.contains("oldestActivePrivilegedRequestMillis"),
                "remote status must expose the age of the longest-running privileged operation");
        assertTrue(runtime.contains("throttledPrivilegedRequests"));

        assertTrue(server.contains("private final Semaphore observabilityConcurrency;"),
                "the status lane must be bounded on its own semaphore");
        assertTrue(server.contains("static int observabilityConcurrencyLimit(int maxConcurrentRequests)"));
        // The gauge entry is still created exactly when the slot is taken and returned exactly when it is given
        // back; acquisition simply moved into a method that returns the ticket. The flag that used to sit beside
        // the ticket is now forbidden outright, because a boolean and a semaphore can disagree and a ticket that
        // is only ever zero or issued cannot.
        assertTrue(server.contains("privilegedTicket = acquirePrivilegedTicket("));
        assertTrue(server.contains("return runtime.privilegedRequestStarted();"));
        assertTrue(server.contains("runtime.privilegedRequestFinished(privilegedTicket);"),
                "every privileged slot must be given back with its gauge entry");
        assertFalse(server.contains("boolean privilegedSlot"),
                "the privileged slot must be tracked by its ticket alone, never by a parallel flag");
        assertTrue(server.contains("recordThrottledPrivilegedRequest()"));
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

    @Test
    void remoteProxyTransportStaysExtractedBoundedAndPolicyFree() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String transport = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteProxyTransport.java"));

        assertTrue(server.contains("private final MorpheusRemoteProxyTransport proxyTransport;"));
        assertFalse(server.contains("HttpClient"));
        assertFalse(server.contains("HttpResponse"));
        assertFalse(server.contains("HttpTimeoutException"));
        assertFalse(server.contains("private void copyBounded("));
        assertFalse(server.contains("proxyResponses"));
        assertTrue(transport.contains("final class MorpheusRemoteProxyTransport"));
        assertTrue(transport.contains("RESPONSE_BUDGET_EXHAUSTED"));
        assertTrue(transport.contains("UPSTREAM_LENGTH_REQUIRED"));
        assertTrue(transport.contains("UPSTREAM_RESPONSE_TOO_LARGE"));
        assertTrue(transport.contains("UPSTREAM_TIMEOUT"));
        assertTrue(transport.contains("copyBounded("));
        assertFalse(transport.contains("MorpheusRemoteRole"));
        assertFalse(transport.contains("MorpheusRemoteIdentityFile"));
        assertFalse(transport.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(transport.contains("MorpheusRemoteProxyTargetResolver"));
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
