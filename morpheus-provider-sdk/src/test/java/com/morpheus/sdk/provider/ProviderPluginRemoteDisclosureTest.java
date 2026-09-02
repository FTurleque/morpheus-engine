package com.morpheus.sdk.provider;

import com.morpheus.application.security.ExternalJarIntegrity;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the remote provider-plugin boundary against filesystem disclosure through every vector that reaches it.
 *
 * <p>Removing a list of suspicious keys protected only the disclosures already known by name. These cases cover
 * the ones that arrive under an innocent name, the ones authored by third-party plugin code, and the ones a
 * future field would introduce.</p>
 */
class ProviderPluginRemoteDisclosureTest {
    /** Windows, POSIX, UNC and URI renderings of a server location, as they appear in real failure text. */
    private static final List<String> SERVER_LOCATIONS = List.of(
            "C:\\Users\\alice\\.morpheus\\plugins\\provider.jar",
            "C:\\work\\secret-project",
            "\\\\server\\private\\share",
            "/home/alice/.morpheus/plugins/provider.jar",
            "/tmp/morpheus-provider-probe-123/result.properties",
            "file:///home/alice/private/spec.md",
            "file:/C:/Users/alice/private/spec.md");

    @TempDir
    Path pluginDirectory;

    @Test
    void noServerLocationSurvivesTheRemoteDiscoveryViewThroughAnyDetailKey() {
        for (String location : SERVER_LOCATIONS) {
            ProviderPluginDiagnostic leaky = ProviderPluginDiagnostic.error(
                    "PLUGIN_DIRECTORY_READ_FAILED",
                    "Cannot inspect provider plugin directory",
                    Map.of(
                            "directory", location,
                            "jarPath", location,
                            "path", location,
                            "workspace", location,
                            "reason", location,
                            "stagingDirectory", location,
                            "someFutureKey", location));

            String rendered = ProviderPluginViews.remoteDiscovery(
                    new ProviderPluginDiscoveryResult(pluginDirectory, List.of(), List.of(leaky))).toString();

            assertFalse(rendered.contains(location),
                    () -> "remote discovery view relayed a server location: " + rendered);
        }
    }

    /**
     * {@code reason} is the vector a denylist of path-shaped key names does not close: the key sounds harmless
     * and the value is whatever the filesystem said. {@link AccessDeniedException#getMessage()} is the pathname.
     */
    @Test
    void aFilesystemExceptionMessageNeverCrossesTheRemoteBoundary() {
        AccessDeniedException denied = new AccessDeniedException("/home/alice/private/plugins");
        ProviderPluginDiagnostic diagnostic = ProviderPluginDiagnostic.error(
                "PLUGIN_DIRECTORY_READ_FAILED",
                "Cannot inspect provider plugin directory",
                Map.of(
                        "reason", denied.getMessage(),
                        "reasonType", denied.getClass().getSimpleName()));

        ProviderPluginViews.RemoteDiscoveryView remote = ProviderPluginViews.remoteDiscovery(
                new ProviderPluginDiscoveryResult(pluginDirectory, List.of(), List.of(diagnostic)));
        ProviderPluginViews.RemoteProviderDiagnostic relayed = remote.diagnostics().getFirst();

        assertFalse(relayed.details().containsKey("reason"),
                "an exception message can be a pathname and must not be relayed");
        assertFalse(remote.toString().contains("/home/alice/private/plugins"),
                () -> "remote view leaked the denied pathname: " + remote);
        assertEquals("AccessDeniedException", relayed.details().get("reasonType"),
                "the failure type must survive, so a remote administrator still learns what went wrong");
        assertEquals("PLUGIN_DIRECTORY_READ_FAILED", relayed.code());
    }

    /** The whole discovery pipeline, not just a hand-built diagnostic: a real scan of a real directory. */
    @Test
    void aRealDiscoveryScanRelaysNeitherTheDirectoryNorTheJarPathnames() throws Exception {
        java.nio.file.Files.writeString(pluginDirectory.resolve("acme-provider.jar"), "not a real jar");
        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(pluginDirectory);

        ProviderPluginViews.RemoteDiscoveryView remote = ProviderPluginViews.remoteDiscovery(result);
        String rendered = remote.toString();

        assertFalse(rendered.contains(pluginDirectory.toString()),
                () -> "remote view leaked the plugin directory: " + rendered);
        assertFalse(rendered.contains(System.getProperty("user.home")),
                () -> "remote view leaked the server user home: " + rendered);
        assertEquals("acme-provider.jar", remote.candidates().getFirst().jarName(),
                "the JAR name identifies the plugin to an administrator and must survive");
    }

    /**
     * A probe result is authored by the third-party plugin, so every free-text member of it is attacker-shaped
     * input rather than MORPHEUS output.
     */
    @Test
    void aPluginAuthoredProbeResultCannotSmuggleALocationThroughAnyOfItsTextFields() {
        for (String location : SERVER_LOCATIONS) {
            ProviderProbeResult hostile = new ProviderProbeResult(
                    new ProviderId("acme"),
                    location,
                    ProviderProbeStatus.SUPPORTED,
                    Optional.of(location),
                    Optional.of(location),
                    Optional.of(new SourceLocator("file", location)),
                    ProviderCapabilitySet.of(ProviderCapability.DISCOVER_PROJECT),
                    false,
                    List.of(new Diagnostic(
                            DiagnosticCode.INVALID_SOURCE,
                            DiagnosticSeverity.ERROR,
                            "cannot read " + location,
                            Map.of("path", location, "anything", location),
                            Optional.of(location))));

            String rendered = ProviderPluginViews.remoteProbe(new ProviderPluginProbeOutcome(
                    "acme-plugin",
                    pluginDirectory.resolve("acme.jar").toString(),
                    Optional.empty(),
                    Optional.of(hostile),
                    List.of())).toString();

            assertFalse(rendered.contains(location),
                    () -> "remote probe view relayed a plugin-supplied location: " + rendered);
        }
    }

    /** Dropping the location must not cost the caller the facts it actually needs. */
    @Test
    void aWellBehavedProbeResultKeepsEverythingActionable() {
        ProviderProbeResult probe = new ProviderProbeResult(
                new ProviderId("acme"),
                "1.4.2",
                ProviderProbeStatus.SUPPORTED,
                Optional.of("acme-spec"),
                Optional.of("2"),
                Optional.of(SourceLocator.file("openspec/config.yaml")),
                ProviderCapabilitySet.of(ProviderCapability.DISCOVER_PROJECT),
                false,
                List.of(Diagnostic.warning(
                        DiagnosticCode.PARTIAL_INGESTION, "some requirements were skipped", Map.of())));

        ProviderPluginViews.RemoteProbeView remote = ProviderPluginViews.remoteProbe(
                new ProviderPluginProbeOutcome(
                        "acme-plugin",
                        pluginDirectory.resolve("acme.jar").toString(),
                        Optional.empty(),
                        Optional.of(probe),
                        List.of()));
        ProviderPluginViews.RemoteProbeResultView view = remote.probe().orElseThrow();

        assertEquals("acme", view.providerId());
        assertEquals("1.4.2", view.providerVersion());
        assertEquals(ProviderProbeStatus.SUPPORTED, view.status());
        assertEquals(Optional.of("acme-spec"), view.schema());
        assertEquals(Optional.of("2"), view.formatVersion());
        assertEquals("file", view.source().orElseThrow().scheme());
        assertEquals(Optional.of("openspec/config.yaml"), view.source().orElseThrow().value(),
                "a workspace-relative locator names a file inside the caller's own workspace and stays");
        assertEquals(java.util.Set.of(ProviderCapability.DISCOVER_PROJECT), view.capabilities());
        assertEquals(DiagnosticCode.PARTIAL_INGESTION, view.diagnostics().getFirst().code());
        assertEquals("some requirements were skipped", view.diagnostics().getFirst().message());
        assertEquals("acme.jar", remote.jarName());
    }

    /** An absolute locator loses its value but keeps its scheme, which still says what kind of source matched. */
    @Test
    void anAbsoluteSourceLocatorKeepsItsSchemeAndLosesItsValue() {
        ProviderProbeResult probe = new ProviderProbeResult(
                new ProviderId("acme"),
                "1.0.0",
                ProviderProbeStatus.SUPPORTED,
                Optional.empty(),
                Optional.empty(),
                Optional.of(SourceLocator.file("/home/alice/workspace")),
                ProviderCapabilitySet.of(),
                false,
                List.of());

        ProviderPluginViews.RemoteSourceView source = ProviderPluginViews.remoteProbe(
                        new ProviderPluginProbeOutcome(
                                "acme-plugin", "", Optional.empty(), Optional.of(probe), List.of()))
                .probe().orElseThrow().source().orElseThrow();

        assertEquals("file", source.scheme());
        assertEquals(Optional.empty(), source.value(),
                "an absolute locator names a place on the server, not a place in the caller's workspace");
    }

    /**
     * The guarantee that matters most for the future: a detail nobody has reviewed does not reach a remote
     * caller merely because its name was not on a list of forbidden ones.
     */
    @Test
    void aDetailKeyNobodyHasReviewedIsDroppedRatherThanRelayed() {
        ProviderPluginDiagnostic future = ProviderPluginDiagnostic.warning(
                "PLUGIN_SCAN_LIMIT_REACHED",
                "Provider plugin scan was truncated at the configured JAR limit",
                Map.of("limit", "256", "scanRootAddedNextYear", "harmless-looking-value"));

        ProviderPluginViews.RemoteProviderDiagnostic relayed = ProviderPluginViews.remoteDiscovery(
                        new ProviderPluginDiscoveryResult(pluginDirectory, List.of(), List.of(future)))
                .diagnostics().getFirst();

        assertEquals(Map.of("limit", "256"), relayed.details(),
                "only reviewed, allowlisted details reach a remote caller");
    }

    /** A filesystem root names no plugin and locates the server, so it yields no name at all. */
    @Test
    void aFilesystemRootJarPathYieldsNoJarName() {
        Path root = pluginDirectory.getRoot();
        assertTrue(root != null, "this platform must expose a filesystem root for the check to mean anything");

        ProviderPluginViews.RemoteProbeView remote = ProviderPluginViews.remoteProbe(
                new ProviderPluginProbeOutcome(
                        "acme-plugin", root.toString(), Optional.empty(), Optional.empty(), List.of()));

        assertEquals("", remote.jarName());
        assertFalse(remote.toString().contains(root.toString()),
                () -> "remote probe view leaked the filesystem root: " + remote);
    }

    /**
     * The whole probe pipeline against a real JAR that fails to activate. This is the path that produced the
     * disclosure: the service catches the failure, records its message, and the outcome is projected for remote.
     */
    @Test
    void arealActivationFailureReachesTheRemoteViewWithATypeAndWithoutAPathname() throws Exception {
        Path jar = pluginDirectory.resolve("metadata-only.jar");
        writeMetadataOnlyJar(jar);

        ProviderPluginProbeOutcome outcome = new ProviderPluginService()
                .probe(pluginDirectory, "metadata-only", pluginDirectory, ExternalJarIntegrity.sha256(jar));

        assertFalse(outcome.success());
        ProviderPluginViews.RemoteProbeView remote = ProviderPluginViews.remoteProbe(outcome);
        String rendered = remote.toString();

        assertFalse(rendered.contains(pluginDirectory.toString()),
                () -> "a real activation failure leaked the plugin directory: " + rendered);
        assertFalse(rendered.contains(jar.toString()),
                () -> "a real activation failure leaked the JAR pathname: " + rendered);
        assertEquals("metadata-only.jar", remote.jarName());

        ProviderPluginViews.RemoteProviderDiagnostic failure = remote.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code().equals("PLUGIN_ACTIVATION_OR_PROBE_FAILED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected an activation failure diagnostic: " + remote));
        assertFalse(failure.details().containsKey("reason"),
                "the raw failure message stays local");
        assertTrue(failure.details().containsKey("reasonType"),
                () -> "a remote caller must still learn the failure type: " + failure);
        assertEquals("metadata-only", failure.details().get("pluginId"));
    }

    /** The SHA-256 pin rejection is the other failure path that records an exception message. */
    @Test
    void anIntegrityRejectionReachesTheRemoteViewWithATypeAndWithoutAPathname() throws Exception {
        Path jar = pluginDirectory.resolve("metadata-only.jar");
        writeMetadataOnlyJar(jar);

        ProviderPluginProbeOutcome outcome = new ProviderPluginService()
                .probe(pluginDirectory, "metadata-only", pluginDirectory, "0".repeat(64));

        assertFalse(outcome.success());
        ProviderPluginViews.RemoteProbeView remote = ProviderPluginViews.remoteProbe(outcome);

        ProviderPluginViews.RemoteProviderDiagnostic rejection = remote.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code().equals("PLUGIN_INTEGRITY_VERIFICATION_FAILED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected an integrity rejection: " + remote));

        assertFalse(remote.toString().contains(pluginDirectory.toString()),
                () -> "an integrity rejection leaked the plugin directory: " + remote);
        assertFalse(rejection.details().containsKey("reason"), "the raw rejection message stays local");
        assertEquals("IllegalArgumentException", rejection.details().get("reasonType"));
    }

    private void writeMetadataOnlyJar(Path jar) throws Exception {
        java.util.Properties metadata = new java.util.Properties();
        metadata.setProperty("plugin.id", "metadata-only");
        metadata.setProperty("provider.id", "metadata-only-provider");
        metadata.setProperty("plugin.version", "1.0.0");
        metadata.setProperty("sdk.apiVersion", Integer.toString(ProviderSdk.API_VERSION));
        metadata.setProperty("morpheus.minVersion", "0.0.1");
        try (var output = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.zip.ZipEntry(ProviderSdk.METADATA_PATH));
            metadata.store(output, null);
            output.closeEntry();
        }
    }

    @Test
    void theLocalViewStillCarriesFullPathsForOperatorTooling() throws Exception {
        java.nio.file.Files.writeString(pluginDirectory.resolve("acme-provider.jar"), "not a real jar");
        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(pluginDirectory);

        ProviderPluginViews.DiscoveryView local = ProviderPluginViews.discovery(result);

        assertEquals(pluginDirectory.toString(), local.directory());
        assertTrue(local.candidates().getFirst().jarPath().endsWith("acme-provider.jar"));
    }
}
