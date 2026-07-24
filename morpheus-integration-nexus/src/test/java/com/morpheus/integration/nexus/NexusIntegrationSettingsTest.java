package com.morpheus.integration.nexus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusIntegrationSettingsTest {
    @TempDir
    Path tempDirectory;

    @Test
    void absentConfigurationDisablesNexusWithoutError() {
        NexusIntegrationSettings settings = NexusIntegrationSettings.resolve(Map.of(), new Properties());
        assertEquals(NexusIntegrationSettings.State.DISABLED, settings.state());
        assertFalse(settings.enabled());
        assertTrue(settings.configurationError().isEmpty());
        assertEquals(20, settings.timeout().toSeconds());
    }

    @Test
    void javaPropertiesOverrideEnvironmentAndValidRunnerEnablesIntegration() throws Exception {
        Path envJar = Files.createFile(tempDirectory.resolve("env-nexus.jar"));
        Path propertyJar = Files.createFile(tempDirectory.resolve("property-nexus.jar"));
        Properties properties = new Properties();
        properties.setProperty(NexusIntegrationSettings.JAR_PROPERTY, propertyJar.toString());
        properties.setProperty(NexusIntegrationSettings.TIMEOUT_PROPERTY, "37");
        properties.setProperty(NexusIntegrationSettings.JAVA_PROPERTY, "custom-java");

        NexusIntegrationSettings settings = NexusIntegrationSettings.resolve(
                Map.of(
                        NexusIntegrationSettings.JAR_ENV, envJar.toString(),
                        NexusIntegrationSettings.TIMEOUT_ENV, "12"),
                properties);

        assertEquals(NexusIntegrationSettings.State.CONFIGURED, settings.state());
        assertEquals(propertyJar.toAbsolutePath().normalize(), settings.jarPath().orElseThrow());
        assertEquals("custom-java", settings.javaCommand());
        assertEquals(37, settings.timeout().toSeconds());
    }

    @Test
    void invalidRunnerOrTimeoutIsNonFatalButIntegrationBecomesInvalid() {
        NexusIntegrationSettings missingJar = NexusIntegrationSettings.resolve(
                Map.of(NexusIntegrationSettings.JAR_ENV, tempDirectory.resolve("missing.jar").toString()),
                new Properties());
        assertEquals(NexusIntegrationSettings.State.INVALID, missingJar.state());
        assertFalse(missingJar.enabled());

        Properties properties = new Properties();
        properties.setProperty(NexusIntegrationSettings.TIMEOUT_PROPERTY, "999");
        NexusIntegrationSettings invalidTimeout = NexusIntegrationSettings.resolve(Map.of(), properties);
        assertEquals(NexusIntegrationSettings.State.INVALID, invalidTimeout.state());
    }
}
