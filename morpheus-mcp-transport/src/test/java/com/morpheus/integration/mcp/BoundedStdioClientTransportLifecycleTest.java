package com.morpheus.integration.mcp;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One transport, one peer -- proved from the peers' side.
 *
 * <p>The transport used to record its peer in a plain reference that {@code connect()} overwrote. Nothing
 * refused a second connect, so a retry, a double subscription or two threads racing each started a process, and
 * the surviving reference named only the last of them. The others kept running under the MORPHEUS account with
 * nothing left in the process able to name them, which is precisely the state the process-tree cleanup exists to
 * prevent. Every test here counts launches recorded by the peers themselves, because a transport that started
 * two peers and kept one reference is indistinguishable from a correct one when asked from the inside.</p>
 */
class BoundedStdioClientTransportLifecycleTest {

    @TempDir
    Path tempDir;

    @Test
    @Timeout(30)
    void aSecondSequentialConnectIsRefusedAndStartsNoSecondPeer() throws Exception {
        Path launches = tempDir.resolve("sequential-launches.txt");
        BoundedStdioClientTransport transport = recordingTransport(launches);
        try {
            transport.connect(message -> message).block();
            awaitLaunches(launches, 1);

            IllegalStateException refused = assertThrows(
                    IllegalStateException.class, () -> transport.connect(message -> message).block());
            assertTrue(refused.getMessage().contains("cannot connect twice"), refused.getMessage());

            assertEquals(1, recordedLaunches(launches).size(), "a refused connect must not start a peer");
            assertEquals(BoundedStdioClientTransport.State.CONNECTED, transport.state());
        } finally {
            transport.closeGracefully().block();
            destroyRecorded(launches);
        }
    }

    @Test
    @Timeout(60)
    void concurrentConnectsResolveToExactlyOnePeer() throws Exception {
        Path launches = tempDir.resolve("concurrent-launches.txt");
        BoundedStdioClientTransport transport = recordingTransport(launches);
        int racers = 8;
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger connected = new AtomicInteger();

        try (ExecutorService callers = Executors.newFixedThreadPool(racers)) {
            List<Future<?>> attempts = new ArrayList<>();
            for (int index = 0; index < racers; index++) {
                attempts.add(callers.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        transport.connect(message -> message).block();
                        connected.incrementAndGet();
                    } catch (IllegalStateException refused) {
                        assertTrue(refused.getMessage().contains("cannot connect twice")
                                        || refused.getMessage().contains("closed while connecting"),
                                refused.getMessage());
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(30, TimeUnit.SECONDS);
            }

            assertEquals(1, connected.get(), "exactly one caller may win the lifecycle");
            awaitLaunches(launches, 1);
            // Give a lost racer every chance to have started a peer of its own before counting.
            TimeUnit.MILLISECONDS.sleep(200);
            assertEquals(1, recordedLaunches(launches).size(), "a race must never produce a second peer");
        } finally {
            transport.closeGracefully().block();
            destroyRecorded(launches);
        }
    }

    @Test
    @Timeout(30)
    void connectAfterCloseIsRefusedRatherThanStartingAPeerWhoseTeardownAlreadyRan() throws Exception {
        Path launches = tempDir.resolve("after-close-launches.txt");
        BoundedStdioClientTransport transport = recordingTransport(launches);
        transport.connect(message -> message).block();
        awaitLaunches(launches, 1);
        transport.closeGracefully().block();

        IllegalStateException refused = assertThrows(
                IllegalStateException.class, () -> transport.connect(message -> message).block());

        assertTrue(refused.getMessage().contains("cannot connect twice"), refused.getMessage());
        assertEquals(BoundedStdioClientTransport.State.CLOSED, transport.state());
        assertEquals(1, recordedLaunches(launches).size());
        assertNoRecordedPeerSurvives(launches);
    }

    @Test
    @Timeout(30)
    void concurrentClosesTearDownOnceAndLeaveNoPeerBehind() throws Exception {
        Path launches = tempDir.resolve("concurrent-close-launches.txt");
        BoundedStdioClientTransport transport = recordingTransport(launches);
        transport.connect(message -> message).block();
        awaitLaunches(launches, 1);

        int closers = 6;
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService callers = Executors.newFixedThreadPool(closers)) {
            List<Future<?>> closes = new ArrayList<>();
            for (int index = 0; index < closers; index++) {
                closes.add(callers.submit(() -> {
                    go.await();
                    transport.closeGracefully().block();
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> close : closes) {
                close.get(20, TimeUnit.SECONDS);
            }
        }

        assertEquals(BoundedStdioClientTransport.State.CLOSED, transport.state());
        assertNoRecordedPeerSurvives(launches);
        assertNoTransportThreadsSurvive();
    }

    /**
     * A close observed as complete really is complete.
     *
     * <p>Returning early from a concurrent close would be the easy way to make it idempotent, and it would tell
     * the second caller the peer is gone while it is still being destroyed. Every close returns only once the
     * peer really has been terminated.</p>
     */
    @Test
    @Timeout(30)
    void aSecondCloseDoesNotReturnBeforeTheFirstOneHasFinished() throws Exception {
        Path launches = tempDir.resolve("close-ordering-launches.txt");
        BoundedStdioClientTransport transport = recordingTransport(launches);
        transport.connect(message -> message).block();
        awaitLaunches(launches, 1);
        long peerPid = recordedLaunches(launches).getFirst();

        transport.closeGracefully().block();
        transport.closeGracefully().block();

        assertFalse(isAlive(peerPid), "the peer must be gone by the time any close reports success");
    }

    @Test
    @Timeout(30)
    void aFailedConnectEndsInATerminalStateThatCannotBeReconnected() {
        ServerParameters missingCommand =
                ServerParameters.builder("morpheus-command-that-does-not-exist-20260904").build();
        BoundedStdioClientTransport transport = new BoundedStdioClientTransport(
                missingCommand, McpJsonDefaults.getMapper(), 1024);

        IllegalStateException unstarted = assertThrows(
                IllegalStateException.class, () -> transport.connect(message -> message).block());
        assertTrue(unstarted.getMessage().contains("failed to start MCP process"), unstarted.getMessage());
        assertEquals(BoundedStdioClientTransport.State.FAILED, transport.state());

        IllegalStateException refused = assertThrows(
                IllegalStateException.class, () -> transport.connect(message -> message).block());
        assertTrue(refused.getMessage().contains("cannot connect twice"), refused.getMessage());

        // Closing a transport that never started must still be safe, and must not undo the terminal state.
        transport.closeGracefully().block();
        assertEquals(BoundedStdioClientTransport.State.FAILED, transport.state());
        assertNoTransportThreadsSurvive();
    }

    private BoundedStdioClientTransport recordingTransport(Path launches) {
        return new BoundedStdioClientTransport(
                peerParameters(FixtureLaunchRecordingMcpPeer.class, launches.toString()),
                McpJsonDefaults.getMapper(),
                4096);
    }

    private void awaitLaunches(Path launches, int expected) throws InterruptedException {
        awaitCondition(Duration.ofSeconds(20), () -> recordedLaunches(launches).size() >= expected);
    }

    private List<Long> recordedLaunches(Path launches) {
        if (!Files.isRegularFile(launches)) return List.of();
        try {
            List<Long> pids = new ArrayList<>();
            for (String line : Files.readAllLines(launches, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) pids.add(Long.parseLong(trimmed));
            }
            return List.copyOf(pids);
        } catch (IOException | NumberFormatException stillBeingWritten) {
            return List.of();
        }
    }

    private void assertNoRecordedPeerSurvives(Path launches) throws InterruptedException {
        for (long pid : recordedLaunches(launches)) {
            awaitCondition(Duration.ofSeconds(10), () -> !isAlive(pid));
        }
    }

    /**
     * The four threads a transport owns must go with it.
     *
     * <p>They are named after their role precisely so this can be asserted rather than assumed; an anonymous
     * pool thread left behind by a half-torn-down transport is invisible until the process runs out of them.</p>
     */
    private void assertNoTransportThreadsSurvive() {
        assertTrue(awaitQuietly(Duration.ofSeconds(10), () -> Thread.getAllStackTraces().keySet().stream()
                        .noneMatch(thread -> thread.isAlive() && thread.getName().startsWith("morpheus-mcp-"))),
                "no transport scheduler thread may outlive its transport");
    }

    private void destroyRecorded(Path launches) {
        for (long pid : recordedLaunches(launches)) {
            ProcessHandle.of(pid).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    private boolean awaitQuietly(Duration timeout, BooleanSupplier condition) {
        try {
            awaitCondition(timeout, condition);
            return true;
        } catch (AssertionError | InterruptedException notSatisfied) {
            if (notSatisfied instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private void awaitCondition(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition was not satisfied within " + timeout);
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    private boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private ServerParameters peerParameters(Class<?> mainClass, String... extraArguments) {
        List<String> arguments = new ArrayList<>(peerArguments(mainClass));
        arguments.addAll(List.of(extraArguments));
        return ServerParameters.builder(javaExecutable())
                .args(arguments.toArray(String[]::new))
                .build();
    }

    private String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
    }

    private List<String> peerArguments(Class<?> mainClass) {
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));
        return List.of("-cp", testClasspath, mainClass.getName());
    }
}
