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
