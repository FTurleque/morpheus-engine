package com.morpheus.sdk.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the value gate used by the remote provider-plugin projections.
 *
 * <p>The policy rejects rather than rewrites, so the cost of a false positive is a lost detail and the cost of a
 * false negative is a disclosure. The cases below are deliberately weighted towards the second.</p>
 */
class RemoteTextPolicyTest {
    @Test
    void filesystemLocationsAreRejectedInEveryRenderingThatReachesTheBoundary() {
        List<String> locations = List.of(
                "C:\\Users\\alice\\.morpheus\\plugins\\provider.jar",
                "c:/users/alice/plugins",
                "C:/work/secret-project",
                "\\\\server\\private\\share",
                "//server/private/share",
                "/home/alice/.morpheus/plugins/provider.jar",
                "/tmp/morpheus-provider-probe-123/result.properties",
                "file:///home/alice/private/spec.md",
                "file:/C:/Users/alice/private/spec.md",
                "~/private/workspace",
                "cannot read /home/alice/private/plugins",
                "access denied to 'C:/Users/alice/secret'",
                "failed opening \\\\nas\\share\\plugin.jar");

        for (String location : locations) {
            assertFalse(RemoteTextPolicy.isRemoteSafe(location),
                    () -> "must reject a filesystem location: " + location);
        }
    }

    @Test
    void valuesThatLocateNothingAreRelayed() {
        List<String> safe = List.of(
                "",
                "acme-provider",
                "1.4.2",
                "AccessDeniedException",
                "256",
                "openspec/config.yaml",
                "META-INF/morpheus-provider.properties",
                "provider plugin activation requires a trusted SHA-256 pin",
                "some requirements were skipped");

        for (String value : safe) {
            assertTrue(RemoteTextPolicy.isRemoteSafe(value), () -> "must relay: " + value);
        }
    }

    @Test
    void anOversizedValueIsRejectedRegardlessOfShape() {
        assertFalse(RemoteTextPolicy.isRemoteSafe("a".repeat(RemoteTextPolicy.MAX_VALUE_LENGTH + 1)));
        assertTrue(RemoteTextPolicy.isRemoteSafe("a".repeat(RemoteTextPolicy.MAX_VALUE_LENGTH)));
    }

    @Test
    void aNullValueIsNeverRelayed() {
        assertFalse(RemoteTextPolicy.isRemoteSafe(null));
    }

    /**
     * The projections fall back to the diagnostic code when a message cannot be relayed. That fallback must stay
     * unreachable for the messages MORPHEUS actually authors, or remote callers silently lose all of them.
     */
    @Test
    void everyMorpheusAuthoredPluginDiagnosticMessageSurvivesThePolicy() {
        List<String> authored = List.of(
                "Provider plugin directory does not exist; no optional plugins were discovered",
                "Provider plugin path must be a non-symbolic directory",
                "Cannot inspect provider plugin directory",
                "Provider plugin scan was truncated at the configured JAR limit",
                "Provider plugin JAR exceeds the scan size limit",
                "Provider plugin JAR does not contain " + ProviderSdk.METADATA_PATH,
                "Provider plugin metadata exceeds the configured size limit",
                "Provider plugin JAR metadata cannot be read or validated",
                "Provider plugin JAR changed identity or metadata during discovery",
                "Plugin SDK API version is not supported by this MORPHEUS runtime",
                "Plugin requires a newer MORPHEUS version",
                "Plugin does not declare compatibility with this MORPHEUS version",
                "Requested provider plugin was not discovered",
                "Multiple provider plugin JARs declare the requested plugin id; no plugin was activated",
                "Provider plugin was rejected before activation because its SHA-256 pin did not match",
                "Provider plugin probe exceeded its isolated execution deadline and was terminated",
                "Provider plugin activation or probe failed in its isolated process",
                "Provider plugin activation or probe failed without terminating MORPHEUS");

        for (String message : authored) {
            assertTrue(RemoteTextPolicy.isRemoteSafe(message),
                    () -> "a MORPHEUS-authored diagnostic message must reach remote callers intact: " + message);
        }
    }
}
