package com.morpheus.application.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shared boundary predicate.
 *
 * <p>Both the provider-plugin remote projection and the HTTP integration-status projection consult this, so a gap
 * here is a gap on two surfaces at once. The cases are weighted towards renderings that appear inside a longer
 * failure sentence, which is where a duplicated, weaker copy of this check previously let a pathname through.</p>
 */
class ServerLocationDisclosureTest {
    @Test
    void everyRenderingOfAServerLocationIsRecognized() {
        List<String> locations = List.of(
                "C:\\Users\\alice\\.morpheus\\plugins\\provider.jar",
                "c:/users/alice/plugins",
                "\\\\server\\private\\share",
                "//server/private/share",
                "/home/alice/.morpheus/plugins/provider.jar",
                "/tmp/morpheus-provider-probe-123/result.properties",
                "file:///home/alice/private/spec.md",
                "file:/C:/Users/alice/private/spec.md",
                "~/private/workspace",
                "cannot read /home/alice/private/plugins",
                "Cannot run program \"/usr/lib/jvm/temurin-21/bin/java\": error=2",
                "access denied to 'C:/Users/alice/secret'",
                "workspace=/srv/morpheus/data",
                "roots:[/srv/morpheus/one,/srv/morpheus/two]",
                "failed opening \\\\nas\\share\\plugin.jar");

        for (String location : locations) {
            assertTrue(ServerLocationDisclosure.namesAServerLocation(location),
                    () -> "must recognize a server location: " + location);
            assertFalse(ServerLocationDisclosure.isSafeToRelay(location),
                    () -> "must refuse to relay: " + location);
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
                "MINOS integration is not configured",
                "provider plugin activation requires a trusted SHA-256 pin");

        for (String value : safe) {
            assertTrue(ServerLocationDisclosure.isSafeToRelay(value), () -> "must relay: " + value);
        }
    }

    @Test
    void anOversizedValueIsRefusedRegardlessOfShape() {
        assertFalse(ServerLocationDisclosure.isSafeToRelay(
                "a".repeat(ServerLocationDisclosure.MAX_RELAYED_LENGTH + 1)));
        assertTrue(ServerLocationDisclosure.isSafeToRelay(
                "a".repeat(ServerLocationDisclosure.MAX_RELAYED_LENGTH)));
    }

    @Test
    void aNullValueIsNeverRelayed() {
        assertFalse(ServerLocationDisclosure.isSafeToRelay(null));
    }
}
