package com.morpheus.sdk.provider;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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
        Process child = spawnProcess(SpawningChild.class);
        long grandchildPid = -1L;
        try {
            grandchildPid = assertTimeoutPreemptively(GRACE, () -> readPublishedPid(child));
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
        }
    }

    @Test
    void aHandleSurvivingGracefulTerminationIsForciblyTerminated() {
        FakeProcessHandle stubborn = new FakeProcessHandle(4242).survivingNormalTermination();

        assertTrue(ProviderPluginDescendantTermination.terminate(List.of(stubborn), Duration.ofMillis(20)));
        assertFalse(stubborn.isAlive(), "escalation must terminate a handle that ignores graceful termination");
    }

    @Test
    void aHandleStillAliveAfterEscalationIsReportedAsNotTerminated() {
        FakeProcessHandle unkillable = new FakeProcessHandle(4243)
                .survivingNormalTermination()
                .throwingOnSignal();

        assertFalse(ProviderPluginDescendantTermination.terminate(List.of(unkillable), Duration.ofMillis(20)),
                "termination must report failure rather than claim a still-live process was cleaned up");
        assertTrue(unkillable.isAlive());
    }

    @Test
    void aFailedExitNotificationFallsBackToALivenessCheck() {
        FakeProcessHandle exited = new FakeProcessHandle(4244).withFailedExitSignal();

        assertTrue(ProviderPluginDescendantTermination.terminate(List.of(exited), Duration.ofMillis(20)),
                "a handle that is no longer alive counts as terminated even if its exit signal failed");
    }

    @Test
    void anInterruptedWaitRestoresTheInterruptFlagAndRechecksLiveness() {
        FakeProcessHandle stubborn = new FakeProcessHandle(4245).survivingNormalTermination();
        Thread.currentThread().interrupt();
        try {
            ProviderPluginDescendantTermination.terminate(List.of(stubborn), Duration.ofSeconds(5));
            assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must be restored");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDescendantAppearingBetweenSnapshotsIsStillTerminated() {
        FakeProcessHandle firstPass = new FakeProcessHandle(4246);
        FakeProcessHandle appearsLater = new FakeProcessHandle(4247);
        FakeProcessHandle root = new FakeProcessHandle(4248)
                .withDescendantSnapshots(List.of(firstPass), List.of(appearsLater));

        ProviderPluginDescendantTermination.reapDescendantsOf(root, Duration.ofMillis(20));

        assertFalse(firstPass.isAlive());
        assertFalse(appearsLater.isAlive(), "a descendant appearing between snapshots must still be terminated");
    }

    /** Blocks on the child's stdout rather than polling for a file, so no timer is involved. */
    private long readPublishedPid(Process child) throws IOException {
        try (BufferedReader output = new BufferedReader(
                new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line = output.readLine();
            while (line != null) {
                try {
                    return Long.parseLong(line.trim());
                } catch (NumberFormatException notThePidLine) {
                    line = output.readLine();
                }
            }
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
        return new ProcessBuilder(command).start();
    }

    /** Spawns one long-lived grandchild, publishes its PID on stdout, then parks so the root stays observable. */
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
            System.out.println(grandchild.pid());
            System.out.flush();
            while (true) {
                LockSupport.parkNanos(100_000_000L);
                Thread.interrupted();
            }
        }
    }
}
