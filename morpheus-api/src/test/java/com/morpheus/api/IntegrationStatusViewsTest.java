package com.morpheus.api;

import com.morpheus.application.reference.ExternalIntegrationStatus;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the optional-integration status projection.
 *
 * <p>{@code GET /api/v1/integrations/{system}/status} is reachable remotely with the READ role and the MINOS and
 * NEXUS launch settings are server-configured, so the response must say whether each location is configured
 * without saying where it is.</p>
 */
class IntegrationStatusViewsTest {
    @Test
    void serverLaunchLocationsAreReportedAsConfiguredWithoutBeingNamed() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("jarPath", "C:\\Users\\alice\\minos\\minos-server.jar");
        details.put("javaCommand", "/usr/lib/jvm/temurin-21/bin/java");
        details.put("homeDirectory", "/home/alice/.minos");
        details.put("timeoutSeconds", "30");

        Map<String, Object> view = IntegrationStatusViews.status(
                new ExternalIntegrationStatus("MINOS", "AVAILABLE", true, "MINOS MCP server is reachable", details));

        assertEquals("true", detail(view, "jarPathConfigured"));
        assertEquals("true", detail(view, "javaCommandConfigured"));
        assertEquals("true", detail(view, "homeDirectoryConfigured"));
        assertEquals("30", detail(view, "timeoutSeconds"), "a non-location detail survives intact");

        String rendered = view.toString();
        for (String location : details.values()) {
            if (location.equals("30")) {
                continue;
            }
            assertFalse(rendered.contains(location),
                    () -> "integration status leaked a server location: " + rendered);
        }
    }

    /** An unavailable integration reports a launch failure whose message names what it could not reach. */
    @Test
    void aLaunchFailureMessageNamingAServerLocationIsNotRelayed() {
        List<String> failures = List.of(
                "Cannot run program \"/usr/lib/jvm/temurin-21/bin/java\": error=2",
                "cannot open C:\\Users\\alice\\minos\\minos-server.jar",
                "file:///home/alice/.minos/config.json is unreadable");

        for (String failure : failures) {
            Map<String, Object> view = IntegrationStatusViews.status(
                    new ExternalIntegrationStatus("MINOS", "UNAVAILABLE", true, failure, Map.of()));

            assertFalse(view.toString().contains(failure),
                    () -> "integration status relayed a launch failure naming a server location: " + view);
            assertEquals("UNAVAILABLE", view.get("state"),
                    "the caller must still learn that the integration is unreachable");
            assertTrue(view.get("message").toString().contains("not reachable"),
                    () -> "the replacement message must still explain the state: " + view);
        }
    }

    @Test
    void aMessageThatNamesNoLocationIsRelayedIntact() {
        Map<String, Object> view = IntegrationStatusViews.status(new ExternalIntegrationStatus(
                "NEXUS", "DISABLED", false, "NEXUS integration is not configured", Map.of()));

        assertEquals("NEXUS", view.get("system"));
        assertEquals("DISABLED", view.get("state"));
        assertEquals(false, view.get("configured"));
        assertEquals("NEXUS integration is not configured", view.get("message"));
        assertEquals(Map.of(), view.get("details"));
    }

    @Test
    void anUnconfiguredLocationReportsFalseRatherThanDisappearing() {
        Map<String, Object> view = IntegrationStatusViews.status(new ExternalIntegrationStatus(
                "MINOS", "INVALID", false, "invalid MINOS configuration", Map.of("jarPath", "")));

        assertEquals("false", detail(view, "jarPathConfigured"));
    }

    @SuppressWarnings("unchecked")
    private static String detail(Map<String, Object> view, String key) {
        return ((Map<String, String>) view.get("details")).get(key);
    }
}
