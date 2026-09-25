package com.morpheus.application.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * One projection for every remote or model-facing integration status.
 *
 * <p>NEXUS names its launch settings {@code jar}/{@code home} where MINOS names them {@code jarPath}/{@code
 * homeDirectory}. The first projection knew only the MINOS spellings, so a NEXUS status had its two locations
 * dropped by the generic path filter (an absolute path) or, for a relative path, relayed in clear.</p>
 */
class IntegrationStatusDisclosureTest {

    private static ExternalIntegrationStatus nexus(String state, String message, Map<String, String> extra) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("javaCommand", "/usr/lib/jvm/temurin-21/bin/java");
        details.put("timeoutSeconds", "30");
        details.put("jar", "/opt/nexus/nexus-server.jar");
        details.put("home", "/home/alice/.nexus");
        details.putAll(extra);
        return new ExternalIntegrationStatus("NEXUS", state, true, message, details);
    }

    @Test
    void theNexusSpellingsAreFoldedOntoTheSameConfiguredKeysAsMinos() {
        ExternalIntegrationStatus projected = IntegrationStatusDisclosure.project(
                nexus("AVAILABLE", "NEXUS MCP integration is available", Map.of()));

        assertEquals("true", projected.details().get("jarPathConfigured"));
        assertEquals("true", projected.details().get("homeDirectoryConfigured"));
        assertEquals("true", projected.details().get("javaCommandConfigured"));
        assertEquals("30", projected.details().get("timeoutSeconds"));
        assertFalse(projected.details().containsKey("jar"));
        assertFalse(projected.details().containsKey("home"));
    }

    @Test
    void aRelativeJarPathIsNotRelayedEitherBecauseItsKeyIsKnown() {
        ExternalIntegrationStatus projected = IntegrationStatusDisclosure.project(
                nexus("AVAILABLE", "NEXUS MCP integration is available",
                        Map.of("jar", "tools/nexus/nexus-server.jar", "home", "nexus-home")));

        assertEquals("true", projected.details().get("jarPathConfigured"));
        assertEquals("true", projected.details().get("homeDirectoryConfigured"));
        assertFalse(projected.details().toString().contains("nexus-server.jar"), projected.details().toString());
        assertFalse(projected.details().toString().contains("nexus-home"), projected.details().toString());
    }

    @Test
    void aBlankLocationIsReportedAsNotConfiguredRatherThanDisappearing() {
        ExternalIntegrationStatus projected = IntegrationStatusDisclosure.project(
                nexus("INVALID", "invalid NEXUS integration configuration", Map.of("jar", " ")));

        assertEquals("false", projected.details().get("jarPathConfigured"));
    }

    @Test
    void theUsefulDetailsOfAnAvailableBuildSurviveTheProjection() {
        ExternalIntegrationStatus projected = IntegrationStatusDisclosure.project(
                nexus("AVAILABLE", "NEXUS technical context built successfully",
                        Map.of("projectId", "nexus-project-id", "projectName", "morpheus-engine",
                                "estimatedTokens", "222")));

        assertEquals("nexus-project-id", projected.details().get("projectId"));
        assertEquals("morpheus-engine", projected.details().get("projectName"));
        assertEquals("222", projected.details().get("estimatedTokens"));
        assertEquals("AVAILABLE", projected.state());
        assertTrue(projected.configured());
        assertEquals("NEXUS technical context built successfully", projected.message());
    }

    @Test
    void anUnavailableMessageNamingAServerLocationIsReplacedByAStableSentence() {
        ExternalIntegrationStatus projected = IntegrationStatusDisclosure.project(nexus(
                "UNAVAILABLE", "NEXUS integration is unavailable: Cannot run program \"/usr/bin/java\": error=2",
                Map.of()));

        assertEquals("UNAVAILABLE", projected.state());
        assertTrue(projected.message().contains("not reachable"), projected.message());
        assertFalse(projected.message().contains("/usr/bin/java"), projected.message());
    }

    @Test
    void noValueOfAProjectedStatusNamesAServerLocation() {
        ExternalIntegrationStatus projected = IntegrationStatusDisclosure.project(nexus(
                "UNAVAILABLE", "NEXUS integration is unavailable: cannot open C:\\Users\\alice\\nexus.jar",
                Map.of("workspace", "/srv/workspace", "note", "C:\\temp\\x")));

        projected.details().values().forEach(value ->
                assertFalse(ServerLocationDisclosure.namesAServerLocation(value), value));
        assertFalse(ServerLocationDisclosure.namesAServerLocation(projected.message()), projected.message());
    }

    @Test
    void anObservationKeepsItsBundleAndItsStatusInvariantAfterProjection() {
        ExternalIntegrationStatus unavailable = nexus("UNAVAILABLE", "NEXUS integration is unavailable: boom", Map.of());

        TechnicalContextObservation projected = IntegrationStatusDisclosure.project(
                TechnicalContextObservation.unavailable(unavailable));

        assertTrue(projected.bundle().isEmpty());
        assertEquals("UNAVAILABLE", projected.status().state());
        assertEquals("true", projected.status().details().get("jarPathConfigured"));
    }
}
