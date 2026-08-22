package com.morpheus.sdk.provider;

import com.morpheus.application.security.ExternalJarIntegrity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginProcessIsolationTest {
    @TempDir
    Path directory;

    @Test
    void nonCooperativeProbeAndItsDescendantAreTerminatedWithoutBlockingMorpheus() throws Exception {
        Path jar = pluginJar(
                "blocking.jar",
                "blocking-plugin",
                "blocking-provider",
                TestBlockingProviderPlugin.class);
        ProviderPluginService service = service(Duration.ofSeconds(3));

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ProviderPluginProbeOutcome outcome = service.probe(
                    directory,
                    "blocking-plugin",
                    directory,
                    ExternalJarIntegrity.sha256(jar));

            assertFalse(outcome.success());
            assertTrue(outcome.diagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.code().equals("PLUGIN_PROBE_TIMEOUT")));
        });

        assertDescendantTerminated(directory.resolve(TestBlockingProviderPlugin.CHILD_PID_FILE));
    }

    @Test
    void successfulProbeCannotLeaveDescendantRunningAfterWorkerExit() throws Exception {
        Path jar = pluginJar(
                "successful-descendant.jar",
                "successful-descendant-plugin",
                "successful-descendant-provider",
                TestSuccessfulDescendantProviderPlugin.class);
        ProviderPluginService service = service(Duration.ofSeconds(5));

        ProviderPluginProbeOutcome outcome = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> service.probe(
                directory,
                "successful-descendant-plugin",
                directory,
                ExternalJarIntegrity.sha256(jar)));

        assertTrue(outcome.success(), outcome.diagnostics().toString());
        assertDescendantTerminated(directory.resolve(TestSuccessfulDescendantProviderPlugin.CHILD_PID_FILE));
    }

    @Test
    void childEnvironmentKeepsOnlyExplicitlySafeOperatingSystemValues() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("MORPHEUS_TOKEN", "secret");
        environment.put("JAVA_TOOL_OPTIONS", "-javaagent:unexpected.jar");
        environment.put("PATH", "/sensitive/custom/path");
        environment.put("LANG", "fr_FR.UTF-8");
        environment.put("TMPDIR", "/tmp/morpheus");

        ProviderPluginProbeProcess.sanitizeEnvironment(environment);

        assertEquals(Map.of(
                "LANG", "fr_FR.UTF-8",
                "TMPDIR", "/tmp/morpheus"), environment);
    }

    private ProviderPluginService service(Duration timeout) {
        return new ProviderPluginService(
                new ProviderPluginDiscovery(),
                new ProviderPluginActivator(),
                new ProviderPluginProbeProcess(timeout));
    }

    private void assertDescendantTerminated(Path childPidFile) throws Exception {
        assertTrue(Files.isRegularFile(childPidFile), "plugin must have spawned its descendant fixture");
        long childPid = Long.parseLong(Files.readString(childPidFile).trim());
        ProcessHandle.of(childPid).filter(ProcessHandle::isAlive).ifPresent(handle -> {
            try {
                handle.onExit().get(5, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError("probe descendant did not terminate", failure);
            }
        });
        assertTrue(
                ProcessHandle.of(childPid).map(handle -> !handle.isAlive()).orElse(true),
                "probe descendant must not survive the worker process");
    }

    private Path pluginJar(
            String fileName,
            String pluginId,
            String providerId,
            Class<? extends MorpheusProviderPlugin> pluginClass) throws Exception {
        Path jar = directory.resolve(fileName);
        Properties properties = new Properties();
        properties.setProperty("plugin.id", pluginId);
        properties.setProperty("provider.id", providerId);
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", Integer.toString(ProviderSdk.API_VERSION));
        properties.setProperty("morpheus.minVersion", "1.0.0");

        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/services/" + MorpheusProviderPlugin.class.getName()));
            output.write((pluginClass.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }
}
