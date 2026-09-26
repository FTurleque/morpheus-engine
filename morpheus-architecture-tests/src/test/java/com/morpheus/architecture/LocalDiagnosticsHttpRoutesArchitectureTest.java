package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two intentions live in this file, and they are not enforced the same way.
 *
 * <p><strong>Extraction</strong> — that the server delegates diagnostics routing instead of routing inline —
 * is partly a fact about wiring text and partly a fact about structure. MORPHEUS wires explicitly
 * ({@code .claude/rules/architecture.md}), and no bytecode rule can say <em>which argument</em> a constructor
 * received, so the wiring expressions stay textual. That the inlined router is gone, on the other hand, is
 * structural, and ArchUnit states it far more broadly than a string ever could.</p>
 *
 * <p><strong>Transport-freedom</strong> — that the router is a routing decision and touches no transport, no
 * serialization and no remote authorization — is a dependency intention, which is what ArchUnit exists to
 * express. A grep for a type name catches the <em>mention</em>; a rule catches the <em>dependency</em>, including
 * one reached by a path nobody thought to spell.</p>
 *
 * <p>Neither form dominates the other, so three literals keep their textual assertion <em>in addition to</em>
 * the rule. See {@link #identifiersJavacCanInlineStayTextuallyForbidden()} for the measured reason.</p>
 */
class LocalDiagnosticsHttpRoutesArchitectureTest {

    private static final String ROUTER = "MorpheusDiagnosticsHttpRoutes";
    private static final String SERVER = "MorpheusHttpServer";

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.morpheus");

    @Test
    void theServerDelegatesDiagnosticsRoutingInsteadOfInliningIt() throws IOException {
        String server = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));

        assertTrue(server.contains("MorpheusDiagnosticsHttpRoutes diagnosticsRoutes"));
        assertTrue(server.contains("new MorpheusDiagnosticsHttpRoutes(this.service)"));
        assertTrue(server.contains("diagnosticsRoutes.route(method, segments, query, projectId)"));

        classes()
                .that().haveSimpleName(SERVER)
                .should().dependOnClassesThat().haveSimpleName(ROUTER)
                .because("the delegation above must be a compiled call, not a commented-out one that still "
                        + "satisfies a source-text search")
                .check(classes);
    }

    /**
     * The extracted router must not come back, and the name is what must not come back — not one spelling of it.
     *
     * <p>The textual form this replaces forbade the literal {@code private MorpheusHttpRouteResponse
     * routeDiagnostics(}. Re-inlining the router under any other modifier, any other return type, or with the
     * signature wrapped across two lines would have satisfied it. Forbidding the method name reaches every one
     * of those, so this rule is strictly broader than the string it replaces.</p>
     */
    @Test
    void theInlinedDiagnosticsRouterCannotComeBack() {
        noMethods()
                .that().areDeclaredInClassesThat().haveSimpleName(SERVER)
                .should().haveName("routeDiagnostics")
                .because("diagnostics routing was extracted into " + ROUTER + " and re-inlining it under any "
                        + "signature would put transport and routing back in the same class")
                .check(classes);
    }

    @Test
    void theRouterIsBuiltFromTheApplicationFacadeAndItsGuards() throws IOException {
        String routes = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusDiagnosticsHttpRoutes.java"));

        assertTrue(routes.contains("MorpheusDiagnosticsApiService service"));
        assertTrue(routes.contains("MorpheusApiService facade"));
        assertTrue(routes.contains("Objects.requireNonNull(facade, \"facade\").diagnosticsService()"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireExactSegments(segments, 3)"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of())"));
        assertTrue(routes.contains("service.diagnostics(projectId)"));
    }

    /**
     * Forbidding the packages rather than the two type names widens the rule to everything they belong to.
     *
     * <p>{@code HttpExchange} and {@code HttpServer} were the two transport types anyone had thought to name;
     * the package also holds {@code Headers}, {@code HttpContext} and {@code Filter}, each of which would have
     * walked past a search for the other two. The same applies to {@code JsonMapper}, which is one class out of
     * the whole Jackson 3 surface.</p>
     */
    @Test
    void theRouterTouchesNeitherTheHttpTransportNorJsonSerialization() {
        noClasses()
                .that().haveSimpleName(ROUTER)
                .should().dependOnClassesThat().resideInAnyPackage("com.sun.net.httpserver..", "tools.jackson..")
                .because("the router decides a route and returns a value; encoding it onto the wire belongs to "
                        + SERVER)
                .check(classes);

        noClasses()
                .that().haveSimpleName(ROUTER)
                .should().dependOnClassesThat(simpleName("MorpheusHttpRequestDecoder")
                        .or(simpleName("MorpheusHttpResponseWriter"))
                        .or(simpleName("MorpheusHttpPathParser")))
                .because("request decoding, response writing and path parsing are the transport plumbing the "
                        + "router was extracted away from")
                .check(classes);
    }

    /**
     * The remote authorization model is a family of ten types, and the pair named textually was two of them.
     *
     * <p>A local diagnostics route reaching into {@code MorpheusRemoteRoutePolicy} or {@code MorpheusRemoteRole}
     * is the violation anyone would have predicted, so those two were the ones written down. Reaching into
     * {@code MorpheusRemoteProxyTransport} or {@code MorpheusRemoteIdentityFile} crosses exactly the same
     * boundary and was not forbidden at all. Matching the family name closes the other eight.</p>
     */
    @Test
    void theRouterDoesNotReachIntoTheRemoteAuthorizationModel() {
        noClasses()
                .that().haveSimpleName(ROUTER)
                .should().dependOnClassesThat().haveSimpleNameStartingWith("MorpheusRemote")
                .because("this router serves the loopback server only; the remote surface carries its own TLS, "
                        + "RBAC and audit obligations and must not be reachable from a local route")
                .check(classes);
    }

    /**
     * Three literals keep a textual assertion on top of their rule, for a reason that was measured rather than
     * assumed.
     *
     * <p>ArchUnit reads bytecode, and javac inlines references to compile-time constants: a class that writes
     * {@code MorpheusRemoteRoutePolicy.GET} carries the resulting {@code String} and no reference to the class
     * it came from. The rule above would pass; a search for the name would not. Of the eight forbidden types,
     * {@code MorpheusHttpResponseWriter} declares one such constant and {@code MorpheusRemoteRoutePolicy}
     * declares three. All four are {@code private} today, which closes the gap by accident rather than by
     * design — every one of these classes shares a package with the router, so widening a single constant to
     * package-private is a one-word change that opens it again with nothing to flag the change.</p>
     *
     * <p>{@code MorpheusRemoteRole} is an enum, whose constants are object references and are never inlined, so
     * the rule does see it. It is kept here anyway because it is the RBAC boundary itself, and
     * {@code .claude/rules/security.md} keeps security invariants asserted on the text.</p>
     */
    @Test
    void identifiersJavacCanInlineStayTextuallyForbidden() throws IOException {
        String routes = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusDiagnosticsHttpRoutes.java"));

        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
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
