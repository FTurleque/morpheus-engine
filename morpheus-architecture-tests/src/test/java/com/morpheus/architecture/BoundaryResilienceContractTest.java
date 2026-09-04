package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three boundaries that were bounded in every dimension except the one an adversary actually uses.
 *
 * <p>Each of these was already careful. The remote facade bounded request bodies, response memory and
 * concurrency, but not how long a client could take to read a response. The query parser validated names,
 * values, duplicates and unknown keys, but only after splitting and percent-decoding whatever arrived. The MCP
 * transport bounded frames, queues, environment and process trees, but nothing stopped it from starting a second
 * peer it could no longer name. In all three the missing bound is the one that costs time or identity rather
 * than memory, which is why none of them showed up as a leak.</p>
 *
 * <p>These are textual assertions on purpose, in the style of the rest of this suite: what has to survive is not
 * a class diagram but a handful of specific decisions that read as removable when the reason for them is no
 * longer in anyone's head.</p>
 */
class BoundaryResilienceContractTest {
    private static final Path RESPONSE_WRITER =
            Path.of("morpheus-api/src/main/java/com/morpheus/api/TimedBoundedResponseWriter.java");
    private static final Path REMOTE_SERVER =
            Path.of("morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java");
    private static final Path REMOTE_RESPONSES =
            Path.of("morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteResponseWriter.java");
    private static final Path PROXY_TRANSPORT =
            Path.of("morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteProxyTransport.java");
    private static final Path QUERY_BUDGET =
            Path.of("morpheus-api/src/main/java/com/morpheus/api/HttpQueryBudget.java");
    private static final Path LOCAL_QUERY =
            Path.of("morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpQuery.java");
    private static final Path PROXY_TARGETS =
            Path.of("morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteProxyTargetResolver.java");
    private static final Path MCP_TRANSPORT = Path.of(
            "morpheus-mcp-transport/src/main/java/com/morpheus/integration/mcp/BoundedStdioClientTransport.java");

    /**
     * Every response the remote facade writes is written under a deadline.
     *
     * <p>A permit taken before the write and released after it is held for exactly as long as the client is
     * willing to keep the socket full. Covering only the large proxied responses would be the tempting
     * half-measure -- they are where it hurts -- and it would leave the envelope path, which is what every
     * refusal and every error travels on, unbounded.</p>
     */
    @Test
    void bothRemoteResponseWritePathsRunUnderTheResponseDeadline() throws IOException {
        String writer = read(RESPONSE_WRITER);
        String server = read(REMOTE_SERVER);
        String responses = read(REMOTE_RESPONSES);
        String proxy = read(PROXY_TRANSPORT);

        assertTrue(writer.contains("RESPONSE_STALL_TIMEOUT"),
                "a stall budget is what separates a slow client from a stopped one");
        assertTrue(writer.contains("RESPONSE_TOTAL_TIMEOUT"),
                "a stall budget alone would let a client trickle one chunk per window forever");
        assertTrue(writer.contains("writer.interrupt()"),
                "the deadline is enforced by interrupting the blocked writer, which closes its channel");

        assertTrue(server.contains("private final TimedBoundedResponseWriter boundedResponses"));
        assertTrue(server.contains("boundedResponses::close"),
                "the deadline's watchdog thread must be released with the facade that owns it");
        assertTrue(server.contains("runtime.recordResponseWriteTimeout()"),
                "an abandoned response must be counted, because no other counter shows it");

        assertTrue(responses.contains("bounded.write("),
                "the envelope path must be written under the deadline too");
        assertTrue(proxy.contains("bounded.write("),
                "the proxied-body path must be written under the deadline");
        assertTrue(proxy.contains("progress.made()"),
                "each chunk that reaches the client must rearm the stall budget");
    }

    /**
     * The deadline must not be reduced to an undocumented JDK system property.
     *
     * <p>{@code sun.net.httpserver.maxRspTime} would look like the same guarantee for a fraction of the code. It
     * is unspecified, unsupported and set outside the process, so a deployment that forgot it would silently
     * have no bound at all.</p>
     */
    @Test
    void theResponseDeadlineDoesNotRestOnAnInternalJdkProperty() throws IOException {
        for (Path source : java.util.List.of(RESPONSE_WRITER, REMOTE_SERVER, PROXY_TRANSPORT)) {
            String content = read(source);
            assertFalse(content.contains("System.setProperty(\"sun.net.httpserver"),
                    () -> source + " must not configure the JDK server through an internal system property");
        }
    }

    /**
     * A response-abandonment scenario must be produced at the socket, not by a body handler that reads normally.
     *
     * <p>{@code HttpResponse.BodyHandlers.discarding()} consumes the body exactly like any other handler and
     * discards it afterwards. A test that calls that an abandoned client asserts nothing about abandonment, and
     * passes for reasons unrelated to what it claims to cover.</p>
     */
    @Test
    void abandonmentIsExercisedFromASocketRatherThanFromADiscardingBodyHandler() throws IOException {
        String loadProfile = read(Path.of(
                "morpheus-api/src/test/java/com/morpheus/api/MorpheusRemoteLoadProfileTest.java"));
        String adversarial = read(Path.of(
                "morpheus-api/src/test/java/com/morpheus/api/MorpheusRemoteAdversarialClientTest.java"));

        assertFalse(loadProfile.contains("HttpResponse.BodyHandlers.discarding()"),
                "discarding() drains the body; it cannot stand for a client that stopped reading");
        assertTrue(adversarial.contains("SSLSocket"),
                "abandonment has to be driven from a socket the test controls");
        assertTrue(adversarial.contains("readHeadersThenStop"));
        assertTrue(adversarial.contains("drainWithPauses"));
        assertTrue(adversarial.contains("setSoLinger(true, 0)"),
                "an abrupt disconnect must really be abrupt");
    }

    /**
     * Query strings are bounded before they are split, decoded or mapped.
     *
     * <p>Both parsers share one budget because they parse the same input for the same server: the remote facade
     * resolves its upstream target from the query, and the local facade parses it again on the other side of the
     * proxy hop. Two copies of the limits is how one of them gets raised alone.</p>
     */
    @Test
    void everyQueryParserSharesOneBudgetAppliedBeforeMaterialization() throws IOException {
        String budget = read(QUERY_BUDGET);
        String local = read(LOCAL_QUERY);
        String targets = read(PROXY_TARGETS);

        assertTrue(budget.contains("MAX_QUERY_BYTES"));
        assertTrue(budget.contains("MAX_PARAMETERS"));
        assertTrue(budget.contains("MAX_PARAMETER_NAME_BYTES"));
        assertTrue(budget.contains("MAX_PARAMETER_VALUE_BYTES"));
        assertTrue(budget.contains("static boolean exceedsUtf8(String value, int maxBytes)"),
                "the budget is in UTF-8 bytes, like every other MORPHEUS input budget");

        for (String parser : java.util.List.of(local, targets)) {
            assertTrue(parser.contains("HttpQueryBudget.requireBoundedQuery("),
                    "the whole query must be refused before it is split");
            assertTrue(parser.contains("HttpQueryBudget.requireBoundedParameterCount("));
            assertTrue(parser.contains("HttpQueryBudget.requireBoundedParameterName("));
            assertTrue(parser.contains("HttpQueryBudget.requireBoundedParameterValue("));
        }
        assertFalse(local.contains("rawQuery.split(\"&\")"),
                "splitting the whole query first is the allocation the budget exists to refuse");
        assertFalse(targets.contains("rawQuery.split(\"&\")"));
    }

    /**
     * One transport instance owns at most one peer process, for its whole life.
     *
     * <p>The peer used to be recorded in a reference that a second connect overwrote, so a retry or a race left
     * a running process that nothing in MORPHEUS could still name -- and naming it is the whole basis of the
     * process-tree cleanup. The state has to be claimed before anything is spawned, and the claim has to be the
     * same one teardown takes, or the two race for the same process.</p>
     */
    @Test
    void theMcpTransportRefusesASecondConnectBeforeStartingAnything() throws IOException {
        String transport = read(MCP_TRANSPORT);

        assertTrue(transport.contains("enum State"), "the lifecycle must be an explicit state, not a flag");
        assertTrue(transport.contains("state.compareAndSet(State.NEW, State.CONNECTING)"),
                "the claim must be atomic and must happen before the peer is started");
        assertTrue(transport.contains("MCP transport cannot connect twice"));
        assertTrue(transport.contains("private final Object lifecycleLock"),
                "starting the peer and tearing it down must not race for the same process");
        assertTrue(transport.contains("private boolean claimTeardown()"),
                "graceful close and fail-closed must claim one shared teardown");
        assertFalse(transport.contains("private volatile boolean closing;"),
                "a boolean cannot distinguish 'never connected' from 'already closed'");
    }

    /**
     * A corrupted audit history cannot stand between an operator and a compromised credential.
     *
     * <p>The audit is evidence about credential mutations. Preserving it strictly across a write made it an
     * authority over them, so one unreadable historical line failed every later revoke and rotate while the
     * credential itself stayed valid -- the audit denying service to the operation it exists to record.</p>
     */
    @Test
    void aCorruptedIdentityAuditIsQuarantinedRatherThanBlockingCredentialMutations() throws IOException {
        String identities = read(Path.of(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteIdentityFile.java"));

        assertTrue(identities.contains("private static RetainedAudit retainableAudit(List<String> lines)"),
                "the mutation path must salvage the audit rather than require it");
        assertTrue(identities.contains("if (salvaged.quarantined() > 0) retainedAudit.add(quarantineRecord());"),
                "dropping unreadable history must leave evidence rather than happen silently");
        assertTrue(identities.contains("Mutation.AUDIT_QUARANTINED"));
        assertFalse(identities.contains("retainedAudit.addAll(parseAudit(readLinesSecurely(file,"),
                "the strict reader must not be what a credential mutation depends on");
        assertTrue(identities.contains("AUDIT_QUARANTINE_SUBJECT = \"morpheus.audit\""),
                "the quarantine record must name a reserved subject, never quote the line it replaced");
    }

    private String read(Path relative) throws IOException {
        return Files.readString(repositoryRoot().resolve(relative));
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate MORPHEUS repository root");
    }
}
