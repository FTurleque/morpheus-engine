package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderProbeResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Executes third-party provider probe code in a killable child JVM. */
final class ProviderPluginProbeProcess {
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration GRACEFUL_TERMINATION = Duration.ofMillis(500);

    private final Duration timeout;

    ProviderPluginProbeProcess() {
        this(DEFAULT_TIMEOUT);
    }

    ProviderPluginProbeProcess(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("provider plugin probe timeout must be positive");
        }
    }

    ProviderProbeResult probe(
            ProviderPluginCandidate candidate,
            Path workspaceRoot,
            String trustedSha256) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(trustedSha256, "trustedSha256");

        Path resultFile = null;
        Process process = null;
        try {
            resultFile = Files.createTempFile("morpheus-provider-probe-", ".properties");
            Files.deleteIfExists(resultFile);
            process = new ProcessBuilder(command(candidate, workspaceRoot, trustedSha256, resultFile))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminate(process);
                throw new ProviderPluginProbeProcessException(
                        "provider plugin probe exceeded the " + timeout.toSeconds() + " second execution limit",
                        true);
            }
            if (process.exitValue() != 0) {
                throw new ProviderPluginProbeProcessException(
                        "isolated provider plugin probe exited with code " + process.exitValue(),
                        false);
            }
            if (!Files.isRegularFile(resultFile)) {
                throw new ProviderPluginProbeProcessException(
                        "isolated provider plugin probe produced no result",
                        false);
            }
            return ProviderProbeResultCodec.read(resultFile);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (process != null) terminate(process);
            throw new ProviderPluginProbeProcessException(
                    "isolated provider plugin probe was interrupted",
                    false,
                    interrupted);
        } catch (IOException failure) {
            if (process != null) terminate(process);
            throw new ProviderPluginProbeProcessException(
                    "cannot execute isolated provider plugin probe",
                    false,
                    failure);
        } finally {
            if (resultFile != null) {
                try {
                    Files.deleteIfExists(resultFile);
                } catch (IOException ignored) {
                    // The result file contains no secret material; preserve the primary outcome.
                }
            }
        }
    }

    private List<String> command(
            ProviderPluginCandidate candidate,
            Path workspaceRoot,
            String trustedSha256,
            Path resultFile) {
        String classPath = System.getProperty("surefire.test.class.path");
        if (classPath == null || classPath.isBlank()) {
            classPath = System.getProperty("java.class.path");
        }
        if (classPath == null || classPath.isBlank()) {
            throw new ProviderPluginProbeProcessException("current JVM classpath is unavailable", false);
        }
        return List.of(
                javaExecutable().toString(),
                "-cp",
                classPath,
                ProviderPluginProbeWorker.class.getName(),
                candidate.jarPath().toString(),
                workspaceRoot.toAbsolutePath().normalize().toString(),
                trustedSha256,
                resultFile.toString());
    }

    private Path javaExecutable() {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        Path executable = Path.of(
                System.getProperty("java.home"),
                "bin",
                windows ? "java.exe" : "java")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(executable)) {
            throw new ProviderPluginProbeProcessException(
                    "Java executable is unavailable for isolated provider plugin probe",
                    false);
        }
        return executable;
    }

    private void terminate(Process process) {
        if (!process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(GRACEFUL_TERMINATION.toMillis(), TimeUnit.MILLISECONDS) && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(GRACEFUL_TERMINATION.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
