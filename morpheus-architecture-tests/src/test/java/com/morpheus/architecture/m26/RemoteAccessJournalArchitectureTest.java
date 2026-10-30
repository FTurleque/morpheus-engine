package com.morpheus.architecture.m26;

import com.morpheus.architecture.ClassesOfASource;
import com.morpheus.architecture.ProductionSources;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote server does not end an attributed request without its record.
 *
 * <p>Where the record is written is textual, because it is about wiring: no rule on bytecode says in which block a
 * call sits. The record of an outcome is written in the {@code finally} of {@code serve}, the one place every exit
 * goes through - served by the facade, proxied, refused, failed. Written in each branch instead, the next branch added
 * would be the one without a record. What the journal can read is a dependency, and a rule holds it.</p>
 */
class RemoteAccessJournalArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api.remote");

    /**
     * The accessors through which a token or its verifier is read. {@code jdk.httpserver} and {@code java.net.URI}
     * declare no compile-time constant, so the rule alone covers the exchange and the path. Broken before acceptance,
     * 30/10/2026: an {@code HttpExchange} parameter added to a method of {@code RemoteAccessJournal} failed the rule,
     * and so did a call to {@code Identity.tokenHash()}.
     */
    private static final Set<String> TOKEN_ACCESSORS = Set.of("token", "tokenHash");
    @Test
    void theOutcomeIsRecordedOnceInTheFinallyOfServeAndTheAcceptanceBeforeAnythingIsServed() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRemoteHttpServer"));
        String serve = server.substring(
                server.indexOf("private void serve(HttpExchange exchange)"),
                server.indexOf("private long acquirePrivilegedTicket("));
        String closing = serve.substring(serve.lastIndexOf("} finally {"));

        assertEquals(1, occurrences(server, "accessJournal.completed("),
                "the outcome of a request is recorded in one place");
        assertTrue(closing.contains("accessJournal.completed(attributed, status)"),
                "the outcome is recorded in the finally of serve, which every exit goes through");
        int statusRead = closing.indexOf("int status = exchange.getResponseCode();");
        int closed = closing.indexOf("exchange.close()");
        assertTrue(statusRead >= 0 && statusRead < closed, "the status is read before the exchange is closed");
        assertTrue(closed < closing.indexOf("accessJournal.completed("),
                "the exchange is closed before the record is written: a file write between the response and the "
                        + "close lets the server drop a connection whose request body was not read, and the "
                        + "client's next request times out");

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
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRemoteHttpServer"));

        assertTrue(server.contains("exchange.getRequestMethod(), route.template());"),
                "the record takes the template the route table matched");
        noClasses()
                .that(ClassesOfASource.declaredIn("RemoteAccessJournal"))
                .should().dependOnClassesThat(resideInAPackage("com.sun.net.httpserver..").or(type(URI.class)))
                .orShould().callMethodWhere(DescribedPredicate.describe("reads a token or its verifier",
                        call -> TOKEN_ACCESSORS.contains(call.getName())))
                .because("a record holds nothing the caller chose and no form of a token: the journal reads neither "
                        + "the exchange, nor a request URI, nor a token or its verifier")
                .check(PRODUCTION);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }
}
