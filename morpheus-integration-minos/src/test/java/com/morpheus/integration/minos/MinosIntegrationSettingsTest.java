package com.morpheus.integration.minos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinosIntegrationSettingsTest {
    @TempDir
    Path tempDirectory;

    @Test
    void absentConfigurationDisablesMinosWithoutError() {
        MinosIntegrationSettings settings = MinosIntegrationSettings.resolve(Map.of(), new Properties());

        assertEquals(MinosIntegrationSettings.State.DISABLED, settings.state());
        assertFalse(settings.enabled());
        assertTrue(settings.configurationError().isEmpty());
        assertEquals(20, settings.timeout().toSeconds());
    }

    @Test
    void javaPropertiesOverrideEnvironmentAndValidJarEnablesIntegration() throws Exception {
        Path envJar = Files.createFile(tempDirectory.resolve("env-minos.jar"));
        Path propertyJar = Files.createFile(tempDirectory.resolve("property-minos.jar"));
        Properties properties = new Properties();
        properties.setProperty(MinosIntegrationSettings.JAR_PROPERTY, propertyJar.toString());
        properties.setProperty(MinosIntegrationSettings.TIMEOUT_PROPERTY, "41");
        properties.setProperty(MinosIntegrationSettings.JAVA_PROPERTY, "custom-java");

        MinosIntegrationSettings settings = MinosIntegrationSettings.resolve(
                Map.of(
                        MinosIntegrationSettings.JAR_ENV, envJar.toString(),
                        MinosIntegrationSettings.TIMEOUT_ENV, "12"),
                properties);

        assertEquals(MinosIntegrationSettings.State.CONFIGURED, settings.state());
        assertEquals(propertyJar.toAbsolutePath().normalize(), settings.jarPath().orElseThrow());
        assertEquals("custom-java", settings.javaCommand());
        assertEquals(41, settings.timeout().toSeconds());
    }

    @Test
    void invalidJarOrTimeoutIsNonFatalButIntegrationBecomesInvalid() {
        MinosIntegrationSettings missingJar = MinosIntegrationSettings.resolve(
                Map.of(MinosIntegrationSettings.JAR_ENV, tempDirectory.resolve("missing.jar").toString()),
                new Properties());
        assertEquals(MinosIntegrationSettings.State.INVALID, missingJar.state());
        assertFalse(missingJar.enabled());

        Properties properties = new Properties();
        properties.setProperty(MinosIntegrationSettings.TIMEOUT_PROPERTY, "999");
        MinosIntegrationSettings invalidTimeout = MinosIntegrationSettings.resolve(Map.of(), properties);
        assertEquals(MinosIntegrationSettings.State.INVALID, invalidTimeout.state());
    }

    @Test
    void pinnedJarIsReverifiedImmediatelyBeforeLaunch() throws Exception {
        Path jar = tempDirectory.resolve("minos.jar");
        Files.writeString(jar, "trusted-content");
        Properties properties = new Properties();
        properties.setProperty(MinosIntegrationSettings.JAR_PROPERTY, jar.toString());
        properties.setProperty(
                MinosIntegrationSettings.JAR_SHA256_PROPERTY,
                com.morpheus.application.security.ExternalJarIntegrity.sha256(jar));

        MinosIntegrationSettings settings = MinosIntegrationSettings.resolve(Map.of(), properties);
        assertEquals(MinosIntegrationSettings.State.CONFIGURED, settings.state());
        assertTrue(settings.jarSha256().isPresent());

        Files.writeString(jar, "substituted-content");
        MinosIntegrationException failure = assertThrows(
                MinosIntegrationException.class,
                () -> new MinosMcpCodeGateway(settings));
        assertTrue(failure.getMessage().contains("immediately before launch"));
    }

    @Test
    void pinWithoutJarOrMalformedPinFailsClosed() throws Exception {
        MinosIntegrationSettings missingJar = MinosIntegrationSettings.resolve(
                Map.of(MinosIntegrationSettings.JAR_SHA256_ENV, "0".repeat(64)),
                new Properties());
        assertEquals(MinosIntegrationSettings.State.INVALID, missingJar.state());

        Path jar = Files.createFile(tempDirectory.resolve("malformed.jar"));
        MinosIntegrationSettings malformed = MinosIntegrationSettings.resolve(
                Map.of(
                        MinosIntegrationSettings.JAR_ENV, jar.toString(),
                        MinosIntegrationSettings.JAR_SHA256_ENV, "not-a-digest"),
                new Properties());
        assertEquals(MinosIntegrationSettings.State.INVALID, malformed.state());
    }
}
