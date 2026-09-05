package com.morpheus.sdk.provider;

import com.morpheus.application.security.ExternalJarIntegrity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final int LEAK_STRESS_ITERATIONS = 12;

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
    void descendantSpawnedJustBeforeWorkerExitIsTerminatedWithoutHavingBeenObserved() throws Exception {
        Path jar = pluginJar(
                "late-descendant.jar",
                "late-descendant-plugin",
                "late-descendant-provider",
                TestLateDescendantProviderPlugin.class);
        ProviderPluginService service = service(Duration.ofSeconds(10));

        ProviderPluginProbeOutcome outcome = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> service.probe(
                directory,
                "late-descendant-plugin",
                directory,
                ExternalJarIntegrity.sha256(jar)));

        assertTrue(outcome.success(), outcome.diagnostics().toString());
        assertDescendantTerminated(directory.resolve(TestLateDescendantProviderPlugin.CHILD_PID_FILE));
    }

    @Test
    void repeatedLateDescendantProbesNeverLeakAProcess() throws Exception {
        Path jar = pluginJar(
                "late-descendant-stress.jar",
                "late-descendant-plugin",
                "late-descendant-provider",
                TestLateDescendantProviderPlugin.class);
        ProviderPluginService service = service(Duration.ofSeconds(10));
        Path pidFile = directory.resolve(TestLateDescendantProviderPlugin.CHILD_PID_FILE);
        String sha256 = ExternalJarIntegrity.sha256(jar);
        List<Long> leaked = new ArrayList<>();

        for (int iteration = 0; iteration < LEAK_STRESS_ITERATIONS; iteration++) {
            Files.deleteIfExists(pidFile);
            ProviderPluginProbeOutcome outcome = service.probe(directory, "late-descendant-plugin", directory, sha256);
            assertTrue(outcome.success(), outcome.diagnostics().toString());

            long childPid = Long.parseLong(Files.readString(pidFile).trim());
            ProcessHandle.of(childPid).filter(ProcessHandle::isAlive).ifPresent(handle -> {
                handle.destroyForcibly();
                leaked.add(handle.pid());
            });
        }

        assertEquals(List.of(), leaked,
                "every probe descendant must be terminated by the worker before it exits");
    }

    @Test
    void aSuccessfulProbeLeavesNoStagingDirectoryBehind() throws Exception {
        Path jar = pluginJar(
                "staging-success.jar",
                "successful-descendant-plugin",
                "successful-descendant-provider",
                TestSuccessfulDescendantProviderPlugin.class);
        List<Path> before = stagingDirectories();

        ProviderPluginProbeOutcome outcome = service(Duration.ofSeconds(10))
                .probe(directory, "successful-descendant-plugin", directory, ExternalJarIntegrity.sha256(jar));

        assertTrue(outcome.success(), outcome.diagnostics().toString());
        assertEquals(before, stagingDirectories(), "a successful probe must remove its staging directory");
    }

    @Test
    void aFailedProbeAlsoLeavesNoStagingDirectoryBehind() throws Exception {
        Path jar = pluginJar(
                "staging-failure.jar",
                "blocking-plugin",
                "blocking-provider",
                TestBlockingProviderPlugin.class);
        List<Path> before = stagingDirectories();

        ProviderPluginProbeOutcome outcome = service(Duration.ofSeconds(2))
                .probe(directory, "blocking-plugin", directory, ExternalJarIntegrity.sha256(jar));

        assertFalse(outcome.success(), "the blocking fixture must not report success");
        assertEquals(before, stagingDirectories(), "a failed probe must remove its staging directory too");
    }

    private List<Path> stagingDirectories() throws IOException {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"));
        try (var entries = Files.list(temp)) {
            return entries
                    .filter(path -> path.getFileName().toString().startsWith("morpheus-provider-probe-"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void childEnvironmentKeepsExecutionVariablesButDropsSecretsAndJvmInjection() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("MORPHEUS_TOKEN", "secret");
        environment.put("JAVA_TOOL_OPTIONS", "-javaagent:unexpected.jar");
        environment.put("PATH", "/safe/execution/path");
        environment.put("LANG", "fr_FR.UTF-8");
        environment.put("TMPDIR", "/tmp/morpheus");

        ProviderPluginProbeProcess.sanitizeEnvironment(environment);

        assertEquals(Map.of(
                "PATH", "/safe/execution/path",
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
