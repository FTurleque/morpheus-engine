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

class LocalHttpRouteGuardsArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void localRouteGuardsStayExtractedAndPure() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String rootRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRootHttpRoutes"));
        String projectSyncRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectSyncHttpRoutes"));
        String guards = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpRouteGuards"));
        String localHttpConsumers = server + "\n" + rootRoutes + "\n" + projectSyncRoutes;

        assertTrue(rootRoutes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertFalse(server.contains("private void requireMethod(String actual, String expected)"));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRouteGuards.requireExactSegments(segments, 3)"));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"POST\")"));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertFalse(localHttpConsumers.contains("throw ApiFailure.methodNotAllowed(\"expected HTTP \""));
        assertFalse(localHttpConsumers.contains("throw ApiFailure.notFound(\"unknown API route\")"));

        assertTrue(guards.contains("ApiFailure.methodNotAllowed(\"expected HTTP \" + expected + \" but received \" + actual)"));
        assertTrue(guards.contains("ApiFailure.notFound(\"unknown API route\")"));
    }

    /**
     * {@code MorpheusHttpRouteGuards} check a method and a path they are given, and touch neither the exchange nor a
     * server. The rule bans a dependency on any class whose simple name contains {@code HttpExchange},
     * {@code HttpServer}, as the text ban it replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code HttpExchange} field added to {@code MorpheusHttpRouteGuards} failed this rule.</p>
     */
    @Test
    void theRouteGuardsTouchNeitherTheExchangeNorAServer() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusHttpRouteGuards"))
                .should().dependOnClassesThat(simpleNameContaining("HttpExchange")
                        .or(simpleNameContaining("HttpServer")))
                .because("a guard decides on the values a router hands it; the exchange and the servers belong to the "
                + "transport")
                .check(PRODUCTION);
    }
}
