package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderProbeResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Executes hash-pinned third-party provider probe code in a killable, environment-minimized child JVM.
 *
 * <p>This process boundary protects MORPHEUS from non-cooperative probe lifecycle failures and accidental environment
 * disclosure. It is deliberately not described as an OS security sandbox: an approved plugin still runs as the same
 * operating-system account and therefore must be treated as trusted code for filesystem and network access.</p>
 */
final class ProviderPluginProbeProcess {
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration GRACEFUL_TERMINATION = Duration.ofMillis(500);
    private static final long TERMINATION_POLL_MILLIS = 10L;
    private static final Set<String> SAFE_ENVIRONMENT_KEYS = Set.of(
            "SYSTEMROOT",
            "WINDIR",
            "TEMP",
            "TMP",
            "TMPDIR",
            "LANG",
            "LC_ALL",
            "LC_CTYPE");

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
        Map<Long, ProcessHandle> observed = new LinkedHashMap<>();
        try {
            resultFile = Files.createTempFile("morpheus-provider-probe-", ".properties");
            Files.deleteIfExists(resultFile);
            ProcessBuilder builder = new ProcessBuilder(command(candidate, workspaceRoot, trustedSha256, resultFile))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD);
            sanitizeEnvironment(builder.environment());
            process = builder.start();

            boolean completed = waitForCompletion(process, observed);
            if (!completed) {
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
            throw new ProviderPluginProbeProcessException(
                    "isolated provider plugin probe was interrupted",
                    false,
                    interrupted);
        } catch (IOException failure) {
            throw new ProviderPluginProbeProcessException(
                    "cannot execute isolated provider plugin probe",
                    false,
                    failure);
        } finally {
            if (process != null) terminate(process, observed);
            if (resultFile != null) {
                try {
                    Files.deleteIfExists(resultFile);
                } catch (IOException ignored) {
                    // The result file contains no secret material; preserve the primary outcome.
                }
            }
        }
    }

    /** Removes inherited credentials and JVM injection variables before the plugin worker is started. */
    static void sanitizeEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        Map<String, String> inherited = new LinkedHashMap<>(environment);
        environment.clear();
        for (Map.Entry<String, String> entry : inherited.entrySet()) {
            if (SAFE_ENVIRONMENT_KEYS.contains(entry.getKey().toUpperCase(Locale.ROOT))) {
                environment.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean waitForCompletion(Process process, Map<Long, ProcessHandle> observed) throws InterruptedException {
        ProcessHandle root = process.toHandle();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            collectTree(root, observed);
            if (!process.isAlive()) {
                collectTree(root, observed);
                return true;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return !process.isAlive();
            }
            long waitMillis = Math.max(1L, Math.min(
                    TERMINATION_POLL_MILLIS,
                    TimeUnit.NANOSECONDS.toMillis(remaining)));
            if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS)) {
                collectTree(root, observed);
                return true;
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

    /**
     * Terminates every descendant observed during execution, not only descendants still attached to the worker JVM.
     * Retaining handles while the worker is alive is important because a child can be re-parented as soon as the worker
     * exits successfully.
     */
    private void terminate(Process process, Map<Long, ProcessHandle> observed) {
        ProcessHandle root = process.toHandle();
        collectTree(root, observed);

        try {
            signalDescendants(root, observed, false);
            collectTree(root, observed);
            signalDescendants(root, observed, false);
            signal(root, false);

            if (awaitTreeExit(root, observed, GRACEFUL_TERMINATION, false)) {
                return;
            }

            collectTree(root, observed);
            signalDescendants(root, observed, true);
            signal(root, true);
            awaitTreeExit(root, observed, GRACEFUL_TERMINATION, true);
        } catch (InterruptedException interrupted) {
            collectTree(root, observed);
            signalDescendants(root, observed, true);
            signal(root, true);
            Thread.currentThread().interrupt();
        }
    }

    private boolean awaitTreeExit(
            ProcessHandle root,
            Map<Long, ProcessHandle> observed,
            Duration duration,
            boolean forcibly) throws InterruptedException {
        long deadline = System.nanoTime() + duration.toNanos();
        while (true) {
            collectTree(root, observed);
            signalDescendants(root, observed, forcibly);
            if (forcibly) {
                signal(root, true);
            }
            if (observed.values().stream().noneMatch(ProcessHandle::isAlive)) {
                return true;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return observed.values().stream().noneMatch(ProcessHandle::isAlive);
            }
            long sleepMillis = Math.max(1L, Math.min(
                    TERMINATION_POLL_MILLIS,
                    TimeUnit.NANOSECONDS.toMillis(remaining)));
            TimeUnit.MILLISECONDS.sleep(sleepMillis);
        }
    }

    private void collectTree(ProcessHandle root, Map<Long, ProcessHandle> observed) {
        observed.putIfAbsent(root.pid(), root);
        for (ProcessHandle seed : List.copyOf(observed.values())) {
            if (!seed.isAlive()) continue;
            try {
                seed.descendants().forEach(handle -> observed.putIfAbsent(handle.pid(), handle));
            } catch (RuntimeException ignored) {
                // A process can disappear between isAlive() and descendants(); retained handles are still signalled.
            }
        }
    }

    private void signalDescendants(
            ProcessHandle root,
            Map<Long, ProcessHandle> observed,
            boolean forcibly) {
        for (ProcessHandle handle : List.copyOf(observed.values())) {
            if (handle.pid() == root.pid()) continue;
            signal(handle, forcibly);
        }
    }

    private void signal(ProcessHandle handle, boolean forcibly) {
        if (!handle.isAlive()) return;
        try {
            if (forcibly) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
            }
        } catch (RuntimeException ignored) {
            // Continue terminating the rest of the observed tree; a later forced pass gets another opportunity.
        }
    }
}
