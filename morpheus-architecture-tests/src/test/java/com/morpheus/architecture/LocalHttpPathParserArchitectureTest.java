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

class LocalHttpPathParserArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void localHttpPathParsingStaysExtractedAndDoesNotAbsorbAllowPolicy() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String parser = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpPathParser"));

        assertTrue(server.contains("private final MorpheusHttpPathParser pathParser"));
        assertTrue(server.contains("return pathParser.segments(path);"));
        assertFalse(server.contains("new ArrayList"));
        assertFalse(server.contains("invalid API path"));
        assertFalse(server.contains("String suffix = path.substring(API_PREFIX.length())"));

        assertTrue(parser.contains("final class MorpheusHttpPathParser"));
        assertTrue(parser.contains("List<String> segments(String rawPath)"));
        assertTrue(parser.contains("invalid API path"));
        assertFalse(parser.contains("allowedMethods"));
    }

    /**
     * {@code MorpheusHttpServer} decodes no path and no segment itself: {@code MorpheusHttpPathParser} reads the path
     * once. The rule bans a dependency on any class whose simple name contains {@code URLDecoder},
     * {@code StandardCharsets}, as the text ban it replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code URLDecoder} field added to {@code MorpheusHttpServer} failed this rule.</p>
     */
    @Test
    void theLocalServerDecodesNoPathItself() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusHttpServer"))
                .should().dependOnClassesThat(simpleNameContaining("URLDecoder")
                        .or(simpleNameContaining("StandardCharsets")))
                .because("a second decoding of the path lets the server and the parser read one request two ways")
                .check(PRODUCTION);
    }

    /**
     * {@code MorpheusHttpPathParser} splits and decodes a raw path, and touches neither the exchange, the query string,
     * the allowed methods nor a form decoder. The rule bans a dependency on any class whose simple name contains
     * {@code URLDecoder}, {@code MorpheusHttpAllowedMethods}, {@code HttpExchange}, {@code MorpheusHttpQuery}, as the
     * text ban it replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code URLDecoder} field added to {@code MorpheusHttpPathParser} failed this rule.</p>
     */
    @Test
    void thePathParserReadsThePathAndNothingElse() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusHttpPathParser"))
                .should().dependOnClassesThat(simpleNameContaining("URLDecoder")
                        .or(simpleNameContaining("MorpheusHttpAllowedMethods"))
                        .or(simpleNameContaining("HttpExchange"))
                        .or(simpleNameContaining("MorpheusHttpQuery")))
                .because("the parser is the one reading of a request path; the query, the methods and the exchange are "
                + "read elsewhere, once each")
                .check(PRODUCTION);
    }
}
