package com.morpheus.architecture.m26;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote server does not end an attributed request without its record.
 *
 * <p>Textual, because it is about wiring: no rule on bytecode says in which block a call sits. The record of an
 * outcome is written in the {@code finally} of {@code serve}, the one place every exit goes through - served by the
 * facade, proxied, refused, failed. Written in each branch instead, the next branch added would be the one without a
 * record.</p>
 */
class RemoteAccessJournalArchitectureTest {
    @Test
    void theOutcomeIsRecordedOnceInTheFinallyOfServeAndTheAcceptanceBeforeAnythingIsServed() throws IOException {
        String server = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String serve = server.substring(
                server.indexOf("private void serve(HttpExchange exchange)"),
                server.indexOf("private long acquirePrivilegedTicket("));
        String closing = serve.substring(serve.lastIndexOf("} finally {"));

        assertEquals(1, occurrences(server, "accessJournal.completed("),
                "the outcome of a request is recorded in one place");
        assertTrue(closing.contains("accessJournal.completed(attributed, exchange.getResponseCode())"),
                "the outcome is recorded in the finally of serve, which every exit goes through");
        assertTrue(closing.indexOf("accessJournal.completed(") < closing.indexOf("exchange.close()"),
                "the status is read before the exchange is closed");

        assertEquals(1, occurrences(server, "accessJournal.accepted("));
        int accepted = serve.indexOf("accessJournal.accepted(attributed)");
        for (String served : new String[] {"serveStatus(exchange)", "maintenance.createBackup(", "proxy(exchange, route)"}) {
            assertTrue(accepted >= 0 && accepted < serve.indexOf(served),
                    () -> "a request is recorded as accepted before " + served + " runs");
        }
        assertTrue(serve.indexOf("identity.role().allows(route.role())") < accepted,
                "a request that is refused its role is not recorded as accepted");
    }

    @Test
    void aRecordIsBuiltFromTheRouteTemplateAndNeverFromThePath() throws IOException {
        String journal = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/RemoteAccessJournal.java"));
        String server = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));

        assertTrue(server.contains("exchange.getRequestMethod(), route.template());"),
                "the record takes the template the route table matched");
        for (String callerChosen : new String[] {
                "HttpExchange", "getRequestURI", "getRawPath", "getRawQuery", "getRequestHeaders", "tokenHash",
                "token()"}) {
            assertFalse(journal.contains(callerChosen),
                    () -> "the journal must not be able to read " + callerChosen
                            + ": a record holds nothing the caller chose and no form of a token");
        }
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
