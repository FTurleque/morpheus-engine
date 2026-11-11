package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameContaining;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpQueryArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void localHttpQueryParsingStaysExtractedAndRouteAgnostic() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String query = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpQuery"));

        assertTrue(server.contains("MorpheusHttpQuery query = MorpheusHttpQuery.parse("));
        assertFalse(server.contains("private record Query("));
        assertFalse(server.contains("new LinkedHashMap"));
        assertFalse(server.contains("duplicate query parameter:"));
        assertFalse(server.contains("query parameter name must not be blank"));
        assertFalse(server.contains("unknown query parameter:"));

        assertTrue(query.contains("final class MorpheusHttpQuery"));
        assertTrue(query.contains("static MorpheusHttpQuery parse(String rawQuery)"));
        assertTrue(query.contains("int intValue("));
        assertTrue(query.contains("long longValue("));
        assertTrue(query.contains("void rejectUnknown("));
        assertTrue(query.contains("duplicate query parameter:"));
    }

    /**
     * {@code MorpheusHttpQuery} reads a query string it is given and never the exchange it came from. The rule bans a
     * dependency on any class whose simple name contains {@code HttpExchange}, as the text ban it replaces matched the
     * name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code HttpExchange} field added to {@code MorpheusHttpQuery} failed this rule.</p>
     */
    @Test
    void theQueryReaderNeverTouchesTheExchange() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusHttpQuery"))
                .should().dependOnClassesThat(simpleNameContaining("HttpExchange"))
                .because("a query reader that held the exchange could read the request a second way")
                .check(PRODUCTION);
    }
}
