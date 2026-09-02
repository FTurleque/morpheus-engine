package com.morpheus.sdk.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the remote discovery view against disclosure of the server's filesystem layout.
 *
 * <p>{@code GET /api/v1/provider-plugins/discover} is reachable remotely with only the READ role, and the plugin
 * directory is server-configured for those callers. Echoing absolute pathnames back therefore tells the caller
 * nothing it needs while describing where the server keeps its files.
 */
class ProviderPluginRemoteViewTest {
    @TempDir
    Path pluginDirectory;

    @Test
    void theRemoteViewCarriesNoAbsolutePathFromTheServer() throws Exception {
        Path jar = Files.writeString(
                pluginDirectory.resolve("acme-provider.jar"), "not a real jar", StandardCharsets.UTF_8);
        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(pluginDirectory);

        ProviderPluginViews.RemoteDiscoveryView remote = ProviderPluginViews.remoteDiscovery(result);
        String rendered = remote.toString();

        assertFalse(rendered.contains(pluginDirectory.toString()),
                () -> "remote view leaked the plugin directory: " + rendered);
        assertFalse(rendered.contains(jar.toString()),
                () -> "remote view leaked the absolute JAR path: " + rendered);
        assertFalse(rendered.contains(System.getProperty("user.home")),
                () -> "remote view leaked the server user home: " + rendered);
    }

    @Test
    void theRemoteViewKeepsTheJarNameThatIdentifiesAPluginToAnAdministrator() throws Exception {
        Files.writeString(pluginDirectory.resolve("acme-provider.jar"), "not a real jar", StandardCharsets.UTF_8);
        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(pluginDirectory);

        ProviderPluginViews.RemoteDiscoveryView remote = ProviderPluginViews.remoteDiscovery(result);

        assertEquals(1, remote.candidates().size());
        assertEquals("acme-provider.jar", remote.candidates().getFirst().jarName());
    }

    @Test
    void pathBearingDiagnosticDetailsAreDroppedFromTheRemoteView() {
        ProviderPluginDiagnostic withPath = ProviderPluginDiagnostic.error(
                "PLUGIN_DIRECTORY_UNREADABLE",
                "Provider plugin directory cannot be scanned",
                Map.of("directory", "/srv/morpheus/plugins", "reason", "permission denied"));

        ProviderPluginViews.RemoteDiscoveryView remote = ProviderPluginViews.remoteDiscovery(
                new ProviderPluginDiscoveryResult(pluginDirectory, List.of(), List.of(withPath)));

        ProviderPluginDiagnostic relayed = remote.diagnostics().getFirst();
        assertFalse(relayed.details().containsKey("directory"),
                "the server pathname must not be relayed through diagnostic details");
        assertEquals("permission denied", relayed.details().get("reason"),
                "the actionable reason must survive redaction");
        assertEquals("PLUGIN_DIRECTORY_UNREADABLE", relayed.code());
    }

    @Test
    void theRemoteProbeViewCarriesNoAbsoluteJarPath() {
        Path jar = pluginDirectory.resolve("acme-provider.jar");
        ProviderPluginProbeOutcome outcome = new ProviderPluginProbeOutcome(
                "acme-plugin",
                jar.toString(),
                Optional.empty(),
                Optional.empty(),
                List.of(ProviderPluginDiagnostic.error(
                        "PLUGIN_PROBE_FAILED",
                        "probe failed",
                        Map.of("jarPath", jar.toString(), "reason", "activation refused"))));

        ProviderPluginViews.RemoteProbeView remote = ProviderPluginViews.remoteProbe(outcome);
        String rendered = remote.toString();

        assertEquals("acme-provider.jar", remote.jarName());
        assertFalse(rendered.contains(jar.toString()),
                () -> "remote probe view leaked the absolute JAR path: " + rendered);
        assertFalse(remote.diagnostics().getFirst().details().containsKey("jarPath"),
                "diagnostic details must not relay the absolute JAR path");
        assertEquals("activation refused", remote.diagnostics().getFirst().details().get("reason"));
    }

    @Test
    void aDiagnosticWithoutPathDetailsIsRelayedUnchanged() {
        ProviderPluginDiagnostic clean = ProviderPluginDiagnostic.warning(
                "PLUGIN_JAR_TOO_LARGE", "JAR exceeds the scan size limit", Map.of("limitBytes", "1024"));

        ProviderPluginViews.RemoteDiscoveryView remote = ProviderPluginViews.remoteDiscovery(
                new ProviderPluginDiscoveryResult(pluginDirectory, List.of(), List.of(clean)));

        assertSame(clean, remote.diagnostics().getFirst(),
                "a diagnostic carrying no server pathname needs no rewriting");
    }

    @Test
    void aProbeOutcomeWithoutAJarPathYieldsAnEmptyJarName() {
        ProviderPluginViews.RemoteProbeView remote = ProviderPluginViews.remoteProbe(
                new ProviderPluginProbeOutcome(
                        "acme-plugin", "", Optional.empty(), Optional.empty(), List.of()));

        assertEquals("", remote.jarName());
    }

    @Test
    void aFilesystemRootJarPathDoesNotDereferenceANullFileName() {
        Path root = pluginDirectory.getRoot();
        assertNotNull(root, "this platform must expose a filesystem root for the check to mean anything");

        ProviderPluginViews.RemoteProbeView remote = ProviderPluginViews.remoteProbe(
                new ProviderPluginProbeOutcome(
                        "acme-plugin", root.toString(), Optional.empty(), Optional.empty(), List.of()));

        assertEquals(root.toString(), remote.jarName());
    }

    @Test
    void theLocalViewStillCarriesFullPathsForOperatorTooling() throws Exception {
        Files.writeString(pluginDirectory.resolve("acme-provider.jar"), "not a real jar", StandardCharsets.UTF_8);
        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(pluginDirectory);

        ProviderPluginViews.DiscoveryView local = ProviderPluginViews.discovery(result);

        assertEquals(pluginDirectory.toString(), local.directory());
        assertTrue(local.candidates().getFirst().jarPath().endsWith("acme-provider.jar"));
    }
}
