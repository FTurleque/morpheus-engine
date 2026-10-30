package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.context.TechnicalContextOptions;
import com.morpheus.application.context.TechnicalContextRequest;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.security.ExternalJarIntegrity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusIntegrationRuntimeTest {
    private static final TechnicalContextRequest REQUEST = new TechnicalContextRequest(
            "Implement session expiration", TechnicalContextOptions.defaults("nexus-project"));

    @TempDir
    Path tempDirectory;

    @Test
    void anAbsentConfigurationIsDisabledAndBuildsAnExplicitAbsence() {
        NexusIntegrationRuntime runtime =
                NexusIntegrationRuntime.resolve(Map.of(), new Properties(), OperationalRecorder.noop());

        ExternalIntegrationStatus status = runtime.status();
        TechnicalContextObservation observation = runtime.build(REQUEST);

        assertEquals(NexusMcpTechnicalContextProvider.SYSTEM, runtime.system());
        assertEquals("DISABLED", status.state());
        assertFalse(status.configured());
        assertEquals("NEXUS integration is not configured", status.message());
        assertEquals(status, observation.status());
        assertTrue(observation.bundle().isEmpty());
    }

    @Test
    void anInvalidConfigurationIsReportedWithItsOwnError() {
        Properties properties = new Properties();
        properties.setProperty(NexusIntegrationSettings.TIMEOUT_PROPERTY, "999");
        NexusIntegrationSettings settings = NexusIntegrationSettings.resolve(Map.of(), properties);
        NexusIntegrationRuntime runtime = new NexusIntegrationRuntime(settings, OperationalRecorder.noop());

        ExternalIntegrationStatus status = runtime.status();

        assertEquals("INVALID", status.state());
        assertFalse(status.configured());
        assertEquals(settings.configurationError().orElseThrow(), status.message());
        assertEquals("INVALID", runtime.build(REQUEST).status().state());
    }

    @Test
    void anUnpinnedJarIsConfiguredButUnavailableWithoutLaunchingAnything() throws Exception {
        Path jar = Files.createFile(tempDirectory.resolve("nexus.jar"));
        NexusIntegrationRuntime runtime = NexusIntegrationRuntime.resolve(
                Map.of(NexusIntegrationSettings.JAR_ENV, jar.toString()),
                new Properties(),
                OperationalRecorder.noop());

        ExternalIntegrationStatus status = runtime.status();
        TechnicalContextObservation observation = runtime.build(REQUEST);

        assertEquals("UNAVAILABLE", status.state());
        assertTrue(status.configured());
        assertEquals("NEXUS integration is unavailable: NEXUS integration is not configured: CONFIGURED",
                status.message());
        assertEquals(status, observation.status());
        assertTrue(observation.bundle().isEmpty());
    }

    @Test
    void aPinnedJarThatWasSubstitutedYieldsAnUnavailableObservationBeforeLaunch() throws Exception {
        Path jar = tempDirectory.resolve("nexus-pinned.jar");
        Files.writeString(jar, "trusted-content");
        Properties properties = new Properties();
        properties.setProperty(NexusIntegrationSettings.JAR_PROPERTY, jar.toString());
        properties.setProperty(NexusIntegrationSettings.JAR_SHA256_PROPERTY, ExternalJarIntegrity.sha256(jar));
        NexusIntegrationRuntime runtime =
                NexusIntegrationRuntime.resolve(Map.of(), properties, OperationalRecorder.noop());
        Files.writeString(jar, "substituted-content");

        TechnicalContextObservation observation = runtime.build(REQUEST);

        assertEquals("UNAVAILABLE", observation.status().state());
        assertTrue(observation.status().configured());
        assertTrue(observation.status().message().startsWith("NEXUS integration is unavailable: "),
                observation.status().message());
        assertTrue(observation.status().message().contains("immediately before launch"),
                observation.status().message());
        assertTrue(observation.bundle().isEmpty());
        assertEquals(jar.toAbsolutePath().normalize().toString(), observation.status().details().get("jar"));
    }
}
