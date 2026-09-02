package com.morpheus.sdk.provider;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the subtree termination primitive in-process.
 *
 * <p>The probe worker itself only ever runs in a child JVM, where no coverage agent is attached, so the logic lives
 * in its own unit rather than inside the worker's {@code main}.
 */
class ProviderPluginDescendantTerminationTest {
    private static final Duration GRACE = Duration.ofSeconds(5);

    @Test
    void anEmptyHandleListIsAlreadyTerminated() {
        assertTrue(ProviderPluginDescendantTermination.terminate(List.of(), GRACE));
    }

    @Test
    void aLiveProcessIsTerminated() throws Exception {
        Process child = spawnPersistentProcess();
        try {
            assertTrue(ProviderPluginDescendantTermination.terminate(List.of(child.toHandle()), GRACE));
            assertFalse(child.isAlive(), "the handle must not survive termination");
        } finally {
            child.destroyForcibly();
        }
    }

    @Test
    void anAlreadyExitedProcessIsSkipped() throws Exception {
        Process child = spawnPersistentProcess();
        child.destroyForcibly();
        assertTrue(child.waitFor(GRACE.toSeconds(), TimeUnit.SECONDS), "fixture process did not exit");

        assertTrue(ProviderPluginDescendantTermination.terminate(List.of(child.toHandle()), GRACE));
    }

    @Test
    void aProcessOutlivingTheGracePeriodIsForciblyTerminated() throws Exception {
        Process child = spawnPersistentProcess();
        try {
            // A one-millisecond grace guarantees the graceful wait times out, so the forcible escalation runs.
            assertTrue(ProviderPluginDescendantTermination.terminate(
                    List.of(child.toHandle()), Duration.ofMillis(1)));
            assertTrue(child.waitFor(GRACE.toSeconds(), TimeUnit.SECONDS), "escalation must terminate the process");
        } finally {
            child.destroyForcibly();
        }
    }

    @Test
    void descendantsOfARootAreReapedWhileThatRootIsStillAlive() throws Exception {
        Path grandchildPidFile = Files.createTempFile("morpheus-reaper-grandchild-", ".pid");
        Files.deleteIfExists(grandchildPidFile);
        Process child = spawnProcess(SpawningChild.class, grandchildPidFile.toString());
        long grandchildPid = -1L;
        try {
            grandchildPid = awaitPublishedPid(grandchildPidFile);
            assertTrue(ProcessHandle.of(grandchildPid).map(ProcessHandle::isAlive).orElse(false),
                    "fixture grandchild must be running before the reap");

            ProviderPluginDescendantTermination.reapDescendantsOf(child.toHandle(), GRACE);

            long reapedPid = grandchildPid;
            assertFalse(ProcessHandle.of(reapedPid).map(ProcessHandle::isAlive).orElse(false),
                    "a grandchild must be reaped through the still-live root");
            assertTrue(child.isAlive(), "reaping descendants must not terminate the root itself");
        } finally {
            if (grandchildPid > 0) {
                ProcessHandle.of(grandchildPid).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroyForcibly);
            }
            child.destroyForcibly();
            Files.deleteIfExists(grandchildPidFile);
        }
    }

    private long awaitPublishedPid(Path path) throws Exception {
        long deadline = System.nanoTime() + GRACE.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(path)) {
                String published = Files.readString(path).trim();
                if (!published.isEmpty()) {
                    return Long.parseLong(published);
                }
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError("fixture grandchild did not publish its PID");
    }

    private Process spawnPersistentProcess() throws IOException {
        return spawnProcess(TestLateDescendantProviderPlugin.PersistentChild.class);
    }

    private Process spawnProcess(Class<?> mainClass, String... arguments) throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                windows ? "java.exe" : "java").toAbsolutePath().normalize();
        String classPath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));

        List<String> command = new java.util.ArrayList<>(List.of(
                java.toString(), "-cp", classPath, mainClass.getName()));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    /** Spawns one long-lived grandchild, publishes its PID, then parks so the root stays observable. */
    public static final class SpawningChild {
        private SpawningChild() {
        }

        public static void main(String[] args) throws Exception {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path java = Path.of(
                    System.getProperty("java.home"),
                    "bin",
                    windows ? "java.exe" : "java").toAbsolutePath().normalize();
            Process grandchild = new ProcessBuilder(
                    java.toString(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    TestLateDescendantProviderPlugin.PersistentChild.class.getName())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Files.writeString(Path.of(args[0]), Long.toString(grandchild.pid()));
            while (true) {
                LockSupport.parkNanos(100_000_000L);
                Thread.interrupted();
            }
        }
    }
}
