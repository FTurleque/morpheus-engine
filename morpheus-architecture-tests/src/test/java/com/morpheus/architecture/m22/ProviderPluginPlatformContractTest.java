package com.morpheus.architecture.m22;

import com.morpheus.application.product.ProductMetadata;
import com.morpheus.sdk.provider.ProviderPluginCandidate;
import com.morpheus.sdk.provider.ProviderPluginDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginPlatformContractTest {
    @TempDir
    Path tempDirectory;

    @Test
    void externalReferenceJarIsDiscoveredAsMetadataWithoutActivation() throws Exception {
        Path root = repoRoot();
        Path referenceJar = root.resolve(
                "morpheus-provider-reference/target/morpheus-provider-reference-"
                        + ProductMetadata.version()
                        + ".jar");
        assertTrue(Files.isRegularFile(referenceJar), "reference provider JAR must be built before architecture tests");

        Path pluginDirectory = Files.createDirectory(tempDirectory.resolve("plugins"));
        Path pluginJar = pluginDirectory.resolve("reference-provider.jar");
        Files.copy(referenceJar, pluginJar, StandardCopyOption.REPLACE_EXISTING);

        var discovery = new ProviderPluginDiscovery().discover(pluginDirectory);
        assertEquals(1, discovery.candidates().size());
        ProviderPluginCandidate candidate = discovery.candidates().getFirst();
        assertTrue(candidate.compatible(), candidate.diagnostics().toString());
        assertEquals("reference-provider-plugin", candidate.metadata().orElseThrow().pluginId());
    }

    @Test
    void domainAndApplicationDoNotDependOnSdkOrReferencePlugin() throws IOException {
        Path root = repoRoot();
        String domain = readTree(root.resolve("morpheus-domain/src/main/java"));
        String application = readTree(root.resolve("morpheus-application/src/main/java"));
        String domainPom = Files.readString(root.resolve("morpheus-domain/pom.xml"));
        String applicationPom = Files.readString(root.resolve("morpheus-application/pom.xml"));

        assertFalse(domain.contains("com.morpheus.sdk.provider"));
        assertFalse(domain.contains("com.morpheus.provider.reference"));
        assertFalse(application.contains("com.morpheus.sdk.provider"));
        assertFalse(application.contains("com.morpheus.provider.reference"));
        assertFalse(domainPom.contains("morpheus-provider-sdk"));
        assertFalse(applicationPom.contains("morpheus-provider-sdk"));
        assertFalse(domainPom.contains("morpheus-provider-reference"));
        assertFalse(applicationPom.contains("morpheus-provider-reference"));
    }

    @Test
    void referenceProviderIsNotEmbeddedAndExecutableSurfacesAreFailClosed() throws IOException {
        Path root = repoRoot();
        String cliPom = Files.readString(root.resolve("morpheus-cli/pom.xml"));
        String surfaces = Files.readString(root.resolve("contracts/public-surfaces.tsv"));
        String main = Files.readString(root.resolve("morpheus-cli/src/main/java/com/morpheus/cli/MorpheusMain.java"));
        String mcp = Files.readString(root.resolve("morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusProviderPluginMcpTools.java"));
        String http = Files.readString(root.resolve("morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String httpRoutes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProviderPluginHttpRoutes.java"));
        String activator = Files.readString(root.resolve("morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/ProviderPluginActivator.java"));

        assertFalse(cliPom.contains("morpheus-provider-reference"), "reference plugin must remain external to launcher runtime");
        assertTrue(surfaces.contains("provider.plugins.discover\tREAD\tprovider-plugins discover\tdiscover_provider_plugins\tGET /api/v1/provider-plugins/discover"));
        assertTrue(surfaces.contains("provider.plugins.probe\tWRITE\tprovider-plugins probe\tEXPLICITLY_NOT_EXPOSED\tPOST /api/v1/provider-plugins/probe"));
        assertTrue(main.contains("Provider plugins (M22, explicit only)"));
        assertTrue(mcp.contains("discover_provider_plugins"));
        assertTrue(mcp.contains("RETIRED_PROBE_TOOL"));
        assertFalse(mcp.contains("case RETIRED_PROBE_TOOL"));
        assertTrue(http.contains("new MorpheusProviderPluginHttpRoutes(providerPluginProbeEnabled)"));
        assertTrue(httpRoutes.contains("if (!probeEnabled)"));
        assertTrue(httpRoutes.contains("provider-plugin probe is remote-only"));
        assertTrue(activator.contains("provider plugin activation requires a trusted SHA-256 pin"));
    }

    /**
     * The remote provider-plugin surface must stay a projection, not a filter.
     *
     * <p>A denylist of path-shaped key names protects only the disclosures already known by name; it admits the
     * ones arriving under an innocent name and every field added later. This pins the inverse: the remote records
     * enumerate what they carry, no internal type reaches a remote caller whole, and the values of the allowlisted
     * fields still pass the text policy because a probe result is authored by third-party plugin code.</p>
     */
    @Test
    void theRemoteProviderSurfaceIsAnAllowlistedProjectionRatherThanAFilteredInternalModel() throws IOException {
        Path sdk = repoRoot().resolve("morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider");
        String views = Files.readString(sdk.resolve("ProviderPluginViews.java"));

        assertTrue(Files.isRegularFile(sdk.resolve("RemoteTextPolicy.java")),
                "the remote value gate must exist alongside the projections");
        assertTrue(views.contains("REMOTE_SAFE_DETAIL_KEYS"),
                "remote diagnostic details must be admitted by an allowlist");
        assertTrue(views.contains("REMOTE_SAFE_DETAIL_KEYS.contains(key) && RemoteTextPolicy.isRemoteSafe(value)"),
                "an allowlisted key must still carry a value the text policy accepts");
        assertFalse(views.contains("PATH_DETAIL_KEYS"),
                "remote details must not return to a denylist of path-shaped key names");
        assertFalse(views.contains("!PATH_DETAIL_KEYS.contains(key)"),
                "remote details must not be admitted by exclusion");

        assertTrue(views.contains("record RemoteProbeResultView("),
                "a probe result must be projected rather than relayed");
        assertTrue(views.contains("record RemoteProbeDiagnosticView("),
                "a plugin-authored diagnostic must be projected rather than relayed");
        assertTrue(views.contains("record RemoteSourceView("),
                "a source locator must be projected rather than relayed");
        assertFalse(views.contains("Optional<ProviderProbeResult> probe"),
                "the internal probe result must not cross the remote boundary whole");
        assertFalse(views.contains("List<ProviderPluginDiagnostic> diagnostics) {\n        public RemoteDiscoveryView"),
                "remote views must not carry the internal diagnostic type");

        String discovery = Files.readString(sdk.resolve("ProviderPluginDiscovery.java"));
        String service = Files.readString(sdk.resolve("ProviderPluginService.java"));
        for (String producer : java.util.List.of(discovery, service)) {
            assertTrue(producer.contains("\"reasonType\", failureType("),
                    "a producer relaying an exception message must also record the remote-safe failure type");
        }
    }

    private String readTree(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                result.append(Files.readString(file)).append('\n');
            }
        }
        return result.toString();
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml")) && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
