package com.morpheus.architecture.m22;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.sdk.provider.MorpheusProviderPlugin;
import com.morpheus.sdk.provider.ProviderPluginActivation;
import com.morpheus.sdk.provider.ProviderPluginActivator;
import com.morpheus.sdk.provider.ProviderPluginCandidate;
import com.morpheus.sdk.provider.ProviderPluginDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginPlatformContractTest {
    @TempDir
    Path tempDirectory;

    @Test
    void externalReferenceJarIsDiscoveredActivatedInDedicatedLoaderProbedAndRead() throws Exception {
        Path root = repoRoot();
        Path referenceJar = root.resolve("morpheus-provider-reference/target/morpheus-provider-reference-1.0.0.jar");
        assertTrue(Files.isRegularFile(referenceJar), "reference provider JAR must be built before architecture tests");

        Path pluginDirectory = Files.createDirectory(tempDirectory.resolve("plugins"));
        Path pluginJar = pluginDirectory.resolve("reference-provider.jar");
        Files.copy(referenceJar, pluginJar, StandardCopyOption.REPLACE_EXISTING);
        Path workspace = Files.createDirectory(tempDirectory.resolve("workspace"));
        Files.writeString(workspace.resolve("morpheus-reference.spec"), "reference\n");

        var discovery = new ProviderPluginDiscovery().discover(pluginDirectory);
        assertEquals(1, discovery.candidates().size());
        ProviderPluginCandidate candidate = discovery.candidates().getFirst();
        assertTrue(candidate.compatible(), candidate.diagnostics().toString());
        assertEquals("reference-provider-plugin", candidate.metadata().orElseThrow().pluginId());

        try (ProviderPluginActivation activation = new ProviderPluginActivator().activate(candidate)) {
            assertNotSame(
                    MorpheusProviderPlugin.class.getClassLoader(),
                    activation.plugin().getClass().getClassLoader(),
                    "external plugin implementation must live in its dedicated URLClassLoader");
            var probe = activation.provider().probe(workspace);
            assertTrue(probe.supported(), probe.diagnostics().toString());
            assertEquals("reference-plugin", probe.providerId().value());
            assertTrue(probe.capabilities().contains(ProviderCapability.DISCOVER_PROJECT));
            assertTrue(probe.capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS));

            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            var read = activation.contentReader().read(
                    new ProviderReadRequest(workspace, projectId, Set.of(ReadCategory.CURRENT_SPECIFICATIONS)),
                    stableResolver());
            assertEquals("reference-plugin", read.providerId().value());
            assertEquals(1, read.content().orElseThrow().specifications().size());
            assertEquals("reference-current", read.content().orElseThrow().specifications().getFirst().key());
            assertEquals(
                    ReadCategoryStatus.READ,
                    read.report(ReadCategory.CURRENT_SPECIFICATIONS).orElseThrow().status());
        }
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
    void referenceProviderIsNotEmbeddedAsALauncherDependencyAndSurfacesAreExplicit() throws IOException {
        Path root = repoRoot();
        String cliPom = Files.readString(root.resolve("morpheus-cli/pom.xml"));
        String surfaces = Files.readString(root.resolve("contracts/public-surfaces.tsv"));
        String main = Files.readString(root.resolve("morpheus-cli/src/main/java/com/morpheus/cli/MorpheusMain.java"));
        String mcp = Files.readString(root.resolve("morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusProviderPluginMcpTools.java"));
        String http = Files.readString(root.resolve("morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));

        assertFalse(cliPom.contains("morpheus-provider-reference"), "reference plugin must remain external to launcher runtime");
        assertTrue(surfaces.contains("provider.plugins.discover\tREAD\tprovider-plugins discover\tdiscover_provider_plugins\tGET /api/v1/provider-plugins/discover"));
        assertTrue(surfaces.contains("provider.plugins.probe\tREAD\tprovider-plugins probe\tprobe_provider_plugin\tGET /api/v1/provider-plugins/probe"));
        assertTrue(main.contains("Provider plugins (M22, explicit only)"));
        assertTrue(mcp.contains("discover_provider_plugins"));
        assertTrue(mcp.contains("probe_provider_plugin"));
        assertTrue(http.contains("segments.getFirst().equals(\"provider-plugins\")"));
    }

    private static EntityIdentityResolver stableResolver() {
        var identities = new ConcurrentHashMap<String, DomainIdentity>();
        return (providerId, entityType, externalId) -> identities.computeIfAbsent(
                providerId.value() + ":" + entityType + ":" + externalId,
                ignored -> DomainIdentity.generate());
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
