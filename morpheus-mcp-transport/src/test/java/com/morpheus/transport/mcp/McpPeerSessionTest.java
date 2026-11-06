package com.morpheus.transport.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The life cycle both peer gateways open their peer through, over a real MCP STDIO child process.
 *
 * <p>The session knows no MORPHEUS type: it raises the failures of the peer it is handed in that peer's own exception
 * type, and tells what it sets aside to the observers it is handed. These tests hand it a peer of their own and count
 * on plain lists.</p>
 */
class McpPeerSessionTest {
    /** The request timeout also covers the start-up of the fixture JVM, which a loaded machine stretches. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration OPERATION_DEADLINE = Duration.ofSeconds(60);
    private static final Set<String> EVERY_TOOL = Set.of(
            FixtureSessionMcpPeer.TOOL_ECHO, FixtureSessionMcpPeer.TOOL_ERROR, FixtureSessionMcpPeer.TOOL_EMPTY,
            FixtureSessionMcpPeer.TOOL_PAIR, FixtureSessionMcpPeer.TOOL_SLOW);
    private static final McpPeerSession.Peer PEER = new McpPeerSession.Peer(
            "FIXTURE", EVERY_TOOL, 64 * 1024, PeerFailure::new);

    @TempDir
    Path temporaryDirectory;

    private final List<Throwable> shutdownFailures = new ArrayList<>();
    private final List<Throwable> residues = new ArrayList<>();
    private final List<Throwable> processTreeFailures = new ArrayList<>();
    private final McpPeerSession.Observers observers = new McpPeerSession.Observers(
            shutdownFailures::add, residues::add, processTreeFailures::add);

    @Test
    void aSessionCallsAToolAndDeletesItsVerifiedCopyWhenItCloses() throws IOException {
        Path copy = Files.writeString(temporaryDirectory.resolve("peer.jar"), "verified copy");

        try (McpPeerSession session = McpPeerSession.open(PEER, launch(OPERATION_DEADLINE, Optional.of(copy)),
                observers)) {
            assertEquals("hello", session.call(FixtureSessionMcpPeer.TOOL_ECHO, Map.of("value", "hello")));
            assertTrue(Files.exists(copy), "the copy is the peer's for as long as the session is open");
        }

        assertFalse(Files.exists(copy));
        assertEquals(List.of(), residues);
        assertEquals(List.of(), shutdownFailures);
    }

    @Test
    void aPeerWithoutARequiredToolIsRefusedInThePeersTypeAndItsCopyIsDeleted() throws IOException {
        Path copy = Files.writeString(temporaryDirectory.resolve("peer.jar"), "verified copy");

        PeerFailure failure = assertThrows(PeerFailure.class, () -> McpPeerSession.open(
                PEER, launch(OPERATION_DEADLINE, Optional.of(copy), "-Dfixture.echoOnly=true"), observers));

        assertEquals("FIXTURE MCP server is incompatible; required tools missing: "
                + "[session_empty, session_error, session_pair, session_slow]", failure.getMessage());
        assertFalse(Files.exists(copy));
    }

    @Test
    void aPeerThatCannotBeStartedFailsToStartAndItsCopyIsDeleted() throws IOException {
        Path copy = Files.writeString(temporaryDirectory.resolve("peer.jar"), "verified copy");
        McpPeerSession.Launch launch = new McpPeerSession.Launch(
                new McpPeerLaunch("morpheus-command-that-does-not-exist-20261106", List.of(), Map.of()),
                REQUEST_TIMEOUT, OPERATION_DEADLINE, Optional.of(copy));

        PeerFailure failure = assertThrows(PeerFailure.class, () -> McpPeerSession.open(PEER, launch, observers));

        assertEquals("cannot start or initialize FIXTURE MCP server", failure.getMessage());
        assertFalse(Files.exists(copy));
    }

    @Test
    void anErrorResultAnEmptyPayloadAndMoreThanOneContentAreRefusedWithTheirMessage() {
        try (McpPeerSession session = McpPeerSession.open(PEER, launch(OPERATION_DEADLINE, Optional.empty()),
                observers)) {
            assertEquals("FIXTURE tool failed: session_error: the peer refused", assertThrows(PeerFailure.class,
                    () -> session.call(FixtureSessionMcpPeer.TOOL_ERROR, Map.of())).getMessage());
            assertEquals("FIXTURE tool returned an empty payload: session_empty", assertThrows(PeerFailure.class,
                    () -> session.call(FixtureSessionMcpPeer.TOOL_EMPTY, Map.of())).getMessage());
            assertEquals("FIXTURE MCP response must contain exactly one TextContent item",
                    assertThrows(PeerFailure.class,
                            () -> session.call(FixtureSessionMcpPeer.TOOL_PAIR, Map.of())).getMessage());
        }
    }

    @Test
    void anOperationPastItsDeadlineEndsWithTheDeadlineInThePeersType() {
        try (McpPeerSession session = McpPeerSession.open(PEER, launch(Duration.ofSeconds(8), Optional.empty()),
                observers)) {
            PeerFailure failure = assertThrows(PeerFailure.class,
                    () -> session.call(FixtureSessionMcpPeer.TOOL_SLOW, Map.of()));

            assertEquals("FIXTURE MCP operation exceeded its 8000 ms deadline", failure.getMessage());
        }
    }

    @Test
    void anAnswerPastTheBoundIsRefusedInCharactersOrInUtf8Bytes() {
        McpPeerSession.Peer bounded = new McpPeerSession.Peer("FIXTURE", Set.of(), 8, PeerFailure::new);

        assertEquals("12345678", bounded.requireBoundedResponse("12345678", "fixture"));
        assertEquals("FIXTURE MCP response exceeds 8 bytes: fixture", assertThrows(PeerFailure.class,
                () -> bounded.requireBoundedResponse("123456789", "fixture")).getMessage());
        assertEquals("FIXTURE MCP response exceeds 8 bytes: fixture", assertThrows(PeerFailure.class,
                () -> bounded.requireBoundedResponse("ééééé", "fixture")).getMessage(),
                "five characters, ten UTF-8 bytes");
    }

    @Test
    void aCopyThatCannotBeDeletedIsToldToItsObserverAndOneThatCanIsNot() throws IOException {
        Path deletable = Files.writeString(temporaryDirectory.resolve("copy"), "jar");
        Path undeletable = Files.createDirectory(temporaryDirectory.resolve("occupied"));
        Files.writeString(undeletable.resolve("child"), "a directory that is not empty cannot be deleted");

        observers.deleteStagedCounting(Optional.of(deletable));
        observers.deleteStagedCounting(Optional.empty());
        assertFalse(Files.exists(deletable));
        assertEquals(List.of(), residues);

        observers.deleteStagedCounting(Optional.of(undeletable));

        assertTrue(Files.exists(undeletable));
        assertEquals(1, residues.size());
    }

    @Test
    void anAbortThatFailsIsToldToItsObserverAndStillFails() {
        AtomicInteger aborted = new AtomicInteger();
        observers.countingAbort(aborted::incrementAndGet).run();
        assertEquals(1, aborted.get());
        assertEquals(List.of(), shutdownFailures);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> observers.countingAbort(() -> {
            throw new IllegalStateException("the peer could not be closed");
        }).run());

        assertEquals("the peer could not be closed", failure.getMessage());
        assertEquals(List.of(failure), shutdownFailures);
    }

    @Test
    void aLaunchTrimsItsCommandAndNormalizesItsCopy() {
        McpPeerSession.Launch launch = new McpPeerSession.Launch(
                new McpPeerLaunch("  java  ", List.of("-version"), Map.of()), REQUEST_TIMEOUT, OPERATION_DEADLINE,
                Optional.of(Path.of("a", "..", "peer.jar")));

        assertEquals("java", launch.peer().command());
        assertEquals(Optional.of(Path.of("peer.jar").toAbsolutePath().normalize()), launch.stagedJar());
    }

    @Test
    void aPeerIsNamedAndBoundedOrRefused() {
        assertEquals("peer name must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new McpPeerSession.Peer(" ", Set.of(), 1, PeerFailure::new)).getMessage());
        assertEquals("peer response bound must be positive", assertThrows(IllegalArgumentException.class,
                () -> new McpPeerSession.Peer("FIXTURE", Set.of(), 0, PeerFailure::new)).getMessage());
    }

    private McpPeerSession.Launch launch(Duration operationDeadline, Optional<Path> copy, String... properties) {
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> arguments = new ArrayList<>(List.of(properties));
        arguments.addAll(List.of("-cp", testClasspath, FixtureSessionMcpPeer.class.getName()));
        return new McpPeerSession.Launch(
                new McpPeerLaunch(javaExecutable(), arguments, Map.of()), REQUEST_TIMEOUT, operationDeadline, copy);
    }

    private static String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
    }

    /** The exception type of the peer these tests hand the session, as a gateway hands it its own. */
    private static final class PeerFailure extends RuntimeException {
        PeerFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
