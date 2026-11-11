package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameContaining;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpAllowedMethodsArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void localAllowHeaderMappingStaysExtractedAndRouteFailuresStayInLocalRouteLayer() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String rootRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRootHttpRoutes"));
        String projectRootRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectRootHttpRoutes"));
        String allowed = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpAllowedMethods"));

        assertTrue(server.contains("private final MorpheusHttpAllowedMethods allowedMethods"));
        assertTrue(server.contains("allowedMethods.forPath(exchange.getRequestURI().getRawPath())"));
        assertFalse(server.contains("private void requireMethod(String actual, String expected)"));
        assertTrue(server.contains("projectRootRoutes.route(exchange, method, segments, query)"));
        assertFalse(server.contains("ApiFailure.methodNotAllowed"));
        assertFalse(server.contains("projects supports GET and POST"));
        assertFalse(server.contains("private String allowedMethods(String path)"));

        assertTrue(rootRoutes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertFalse(rootRoutes.contains("getResponseHeaders().set(\"Allow\""));

        assertTrue(projectRootRoutes.contains("ApiFailure.methodNotAllowed(\"projects supports GET and POST\")"));
        assertFalse(projectRootRoutes.contains("getResponseHeaders().set(\"Allow\""));

        assertTrue(allowed.contains("final class MorpheusHttpAllowedMethods"));
        assertTrue(allowed.contains("String forPath(String path)"));
        assertTrue(allowed.contains("MorpheusHttpPathParser"));
        assertTrue(allowed.contains("MorpheusHttpRouteTable.allowHeader(segments)"));
        assertFalse(allowed.contains(".equals(\""), "the Allow header is read from the route table, not matched by hand");
    }

    /**
     * The four routers that register their own context set {@code Allow} on their own 405s; until 8 October 2026 each
     * computed it by hand and five routes answered a wrong one. They now read the server's
     * {@code MorpheusHttpAllowedMethods}, received at {@code register(...)} like the decoder and the writer.
     */
    @Test
    void routersRegisteringTheirOwnContextReadTheAllowHeaderFromTheRouteTable() throws IOException {
        for (String router : List.of("MorpheusPolicyHttpRoutes", "MorpheusPolicyManagementHttpRoutes",
                "MorpheusQueryHttpRoutes", "MorpheusReasoningHttpRoutes")) {
            String source = ProductionSources.readMain("morpheus-api", router);
            assertTrue(source.contains("exchange.getResponseHeaders().set(\"Allow\", allowedMethods.forPath("), router);
            assertFalse(source.contains("String allowed("), router);
        }
    }

    /** The table states routes and methods only: roles stay in the remote policy, which reads the table. */
    @Test
    void theRouteTableKnowsNothingOfTheRemoteAuthorizationModel() throws IOException {
        String table = ProductionSources.readMain("morpheus-api", "MorpheusHttpRouteTable");
        String policy = ProductionSources.readMain("morpheus-api", "MorpheusRemoteRoutePolicy");
        assertFalse(table.contains("MorpheusRemote"),
                "the route table names no type of the remote authorization model: roles stay in the remote policy");
        assertTrue(policy.contains("requireExactCover(MorpheusHttpRouteTable.templates(), ROUTES)"));
    }

    /**
     * {@code MorpheusRootHttpRoutes} and {@code MorpheusProjectRootHttpRoutes} leave the {@code Allow} header to the
     * server, which computes it from the route table. The rule bans a dependency on any class whose simple name
     * contains {@code MorpheusHttpAllowedMethods}, as the text ban it replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code MorpheusHttpAllowedMethods} field added to {@code MorpheusRootHttpRoutes} failed this
     * rule.</p>
     */
    @Test
    void theRoutersDoNotComputeTheAllowedMethodsThemselves() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusRootHttpRoutes", "MorpheusProjectRootHttpRoutes"))
                .should().dependOnClassesThat(simpleNameContaining("MorpheusHttpAllowedMethods"))
                .because("the Allow header of a 405 is computed once, from MorpheusHttpRouteTable, by the server that "
                + "answers it")
                .check(PRODUCTION);
    }

    /**
     * {@code MorpheusHttpAllowedMethods} compute a set of methods from the route table and answer nothing themselves.
     * The rule bans a dependency on any class whose simple name contains {@code HttpExchange}, {@code ApiFailure}, as
     * the text ban it replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code HttpExchange} field added to {@code MorpheusHttpAllowedMethods} failed this rule.</p>
     */
    @Test
    void theAllowedMethodsTouchNeitherTheExchangeNorAFailure() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusHttpAllowedMethods"))
                .should().dependOnClassesThat(simpleNameContaining("HttpExchange")
                        .or(simpleNameContaining("ApiFailure")))
                .because("computing the methods a route allows is a pure reading of the route table; answering the request"
                + " is the server's")
                .check(PRODUCTION);
    }
}
