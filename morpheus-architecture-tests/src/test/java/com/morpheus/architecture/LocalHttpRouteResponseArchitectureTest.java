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

class LocalHttpRouteResponseArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void localRouteResponseCarrierStaysExtractedAndPure() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String response = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpRouteResponse"));

        assertTrue(server.contains("MorpheusHttpRouteResponse"));
        assertFalse(server.contains("private record RouteResponse"));

        assertTrue(response.contains("record MorpheusHttpRouteResponse(int status, Object data)"));
        assertTrue(response.contains("route status must be between 200 and 599"));
        assertTrue(response.contains("Objects.requireNonNull(data, \"data\")"));
    }

    /**
     * {@code MorpheusHttpRouteResponse} is a value a router returns: it touches neither the exchange nor a failure. The
     * rule bans a dependency on any class whose simple name contains {@code HttpExchange}, {@code ApiFailure}, as the
     * text ban it replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code HttpExchange} field added to {@code MorpheusHttpRouteResponse} failed this rule.</p>
     */
    @Test
    void aRouteResponseIsAValueThatTouchesNeitherTheExchangeNorAFailure() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusHttpRouteResponse"))
                .should().dependOnClassesThat(simpleNameContaining("HttpExchange")
                        .or(simpleNameContaining("ApiFailure")))
                .because("a route response carries a status and a body; writing it and failing it are the server's")
                .check(PRODUCTION);
    }
}
