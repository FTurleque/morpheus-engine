package com.morpheus.integration.minos;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.security.ExternalJarIntegrity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinosIntegrationRuntimeTest {
    @TempDir
    Path tempDirectory;

    @Test
    void anAbsentConfigurationIsReportedDisabledAndRegistersNoResolver() {
        MinosIntegrationRuntime runtime =
                MinosIntegrationRuntime.resolve(Map.of(), new Properties(), OperationalRecorder.noop());

        ExternalIntegrationStatus status = runtime.status();

        assertEquals("MINOS", status.system());
        assertEquals("DISABLED", status.state());
        assertFalse(status.configured());
        assertEquals("MINOS integration is not configured", status.message());
        assertEquals(List.of("javaCommand", "timeoutSeconds"), status.details().keySet().stream().sorted().toList());
        assertEquals(runtime.settings().javaCommand(), status.details().get("javaCommand"));
        assertEquals(Long.toString(MinosIntegrationSettings.DEFAULT_TIMEOUT_SECONDS),
                status.details().get("timeoutSeconds"));
        assertTrue(runtime.resolverRegistry().find(MinosMcpExternalReferenceResolver.SYSTEM).isEmpty());
    }

    @Test
    void anInvalidConfigurationIsReportedWithItsOwnErrorAndRegistersNoResolver() {
        Properties properties = new Properties();
        properties.setProperty(MinosIntegrationSettings.TIMEOUT_PROPERTY, "999");
        MinosIntegrationRuntime runtime =
                MinosIntegrationRuntime.resolve(Map.of(), properties, OperationalRecorder.noop());

        ExternalIntegrationStatus status = runtime.status();

        assertEquals("INVALID", status.state());
        assertFalse(status.configured());
        assertEquals(runtime.settings().configurationError().orElseThrow(), status.message());
        assertTrue(runtime.resolverRegistry().find("minos").isEmpty());
    }

    @Test
    void anUnpinnedJarIsConfiguredButUnavailableWithoutLaunchingAnything() throws Exception {
        Path jar = Files.createFile(tempDirectory.resolve("minos.jar"));
        MinosIntegrationRuntime runtime = MinosIntegrationRuntime.resolve(
                Map.of(MinosIntegrationSettings.JAR_ENV, jar.toString(),
                        MinosIntegrationSettings.HOME_ENV, tempDirectory.toString()),
                new Properties(),
                OperationalRecorder.noop());

        ExternalIntegrationStatus status = runtime.status();

        assertEquals(MinosIntegrationSettings.State.CONFIGURED, runtime.settings().state());
        assertEquals("UNAVAILABLE", status.state());
        assertTrue(status.configured());
        assertEquals("MINOS integration is not configured: CONFIGURED", status.message());
        assertEquals(runtime.settings().jarPath().orElseThrow().toString(), status.details().get("jarPath"));
        assertEquals(runtime.settings().homeDirectory().orElseThrow().toString(),
                status.details().get("homeDirectory"));
        assertTrue(runtime.resolverRegistry().find("MINOS").isEmpty());
    }

    @Test
    void aPinnedJarRegistersTheResolverAndASubstitutedJarIsUnavailableBeforeLaunch() throws Exception {
        Path jar = tempDirectory.resolve("minos-pinned.jar");
        Files.writeString(jar, "trusted-content");
        Properties properties = new Properties();
        properties.setProperty(MinosIntegrationSettings.JAR_PROPERTY, jar.toString());
        properties.setProperty(MinosIntegrationSettings.JAR_SHA256_PROPERTY, ExternalJarIntegrity.sha256(jar));
        properties.setProperty(MinosIntegrationSettings.TIMEOUT_PROPERTY, "3");
        MinosIntegrationRuntime runtime =
                MinosIntegrationRuntime.resolve(Map.of(), properties, OperationalRecorder.noop());
        Files.writeString(jar, "substituted-content");

        ExternalIntegrationStatus status = runtime.status();

        assertTrue(runtime.settings().enabled());
        assertEquals(MinosMcpExternalReferenceResolver.SYSTEM,
                runtime.resolverRegistry().find("minos").orElseThrow().system());
        assertEquals("UNAVAILABLE", status.state());
        assertTrue(status.configured());
        assertEquals("MINOS JAR integrity verification failed immediately before launch", status.message());
        assertEquals("3", status.details().get("timeoutSeconds"));
    }
}
