package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three boundaries hold for every HTTP router, so they are stated once over the family instead of once per router.
 *
 * <p>ADR-0103 left the generalisation of its pilot undecided; its amendment of 11/09/2026 decides it by capability,
 * and this is the first group. Most of what a router may touch depends on whether it reads a request body -- nine
 * of the seventeen legitimately carry {@code HttpExchange} and a decoder -- so it is not a family property and is
 * not stated here. These three are: no {@code *HttpRoutes} class reaches the remote authorization model, writes a
 * response itself, or parses a path itself.</p>
 *
 * <p>The gain is reach, not volume. Thirteen routers carried these bans as per-class text. Four -- policy,
 * policy-management, query and reasoning -- carried none, and a router added tomorrow would carry none until
 * someone wrote its test. Nothing is removed: the per-class assertions stay where they are.</p>
 *
 * <p>Each guard was broken before it was accepted (executions E0-E5 of the amendment). The two halves that do not
 * need a classpath -- the source scan and the router bijection -- replay their refusal on every build over synthetic
 * inputs. The three ArchUnit rules would need a synthetic classpath to do the same; each records instead the
 * execution that broke it.</p>
 */
class HttpRoutesFamilyArchitectureTest {

    private static final String ROUTER_SUFFIX = "HttpRoutes";

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.morpheus");

    /**
     * The rule sees every dependency on the ten {@code MorpheusRemote*} types; the text sees the ones javac inlines.
     *
     * <p>Unlike the pilot's two banned types, this family declares compile-time constants that are not private:
     * {@code MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS} and {@code DEFAULT_MAX_CONCURRENT_REQUESTS} are
     * public, three more of its limits are package-private, and {@code MorpheusRemoteIdentityFile} exposes four
     * public ones. A router writing any of them carries the value and no reference to the class, so the rule alone
     * would pass. ADR-0103 makes coexistence mandatory in exactly that case.</p>
     *
     * <p>Broken before acceptance, 11/09/2026. E1, {@code MorpheusRemoteProxyTransport.class} written into
     * {@code MorpheusPolicyHttpRoutes}: this rule failed, and the three other suites naming that router passed. E2,
     * {@code MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS} written into {@code MorpheusQueryHttpRoutes}: the rule
     * passed, javac having inlined the value, and only the text scan failed. The text scan is replayed by
     * {@link #theSourceScanNamesTheRouterThatMentionsTheLiteralAndNoOther}.</p>
     */
    @Test
    void noRouterReachesTheRemoteAuthorizationModel() throws IOException {
        noClasses()
                .that().haveSimpleNameEndingWith(ROUTER_SUFFIX)
                .should().dependOnClassesThat().haveSimpleNameStartingWith("MorpheusRemote")
                .because("routers serve the loopback server; the remote surface carries its own TLS, RBAC and "
                        + "audit obligations and must not be reachable from any route")
                .check(classes);

        assertNoRouterSourceMentions("MorpheusRemote");
    }

    /**
     * {@code MorpheusHttpResponseWriter} declares one compile-time constant, {@code JSON_CONTENT_TYPE}. It is private
     * today and shares a package with every router, so widening it is a one-word change: the text stays beside the
     * rule.
     *
     * <p>Broken before acceptance, 11/09/2026. E3, {@code MorpheusHttpResponseWriter.class} written into
     * {@code MorpheusReasoningHttpRoutes}: this method failed, and it alone.</p>
     */
    @Test
    void noRouterWritesTheResponseItself() throws IOException {
        noClasses()
                .that().haveSimpleNameEndingWith(ROUTER_SUFFIX)
                .should().dependOnClassesThat().haveSimpleName("MorpheusHttpResponseWriter")
                .because("a router decides a route and returns a MorpheusHttpRouteResponse; encoding it onto the "
                        + "exchange belongs to the server")
                .check(classes);

        assertNoRouterSourceMentions("MorpheusHttpResponseWriter");
    }

    /**
     * {@code MorpheusHttpPathParser} declares no compile-time constant, so the rule has no blind spot to cover.
     *
     * <p>Broken before acceptance, 11/09/2026. E4, {@code MorpheusHttpPathParser.class} written into
     * {@code MorpheusPolicyManagementHttpRoutes}: this method failed, and it alone.</p>
     */
    @Test
    void noRouterParsesThePathItself() {
        noClasses()
                .that().haveSimpleNameEndingWith(ROUTER_SUFFIX)
                .should().dependOnClassesThat().haveSimpleName("MorpheusHttpPathParser")
                .because("a router receives segments the server already parsed; parsing the raw path again lets "
                        + "the two disagree about which route is being served")
                .check(classes);
    }

    /**
     * A family rule is only as wide as the family it can see.
     *
     * <p>{@code failOnEmptyShould} catches a rule that retains no class at all; it does not catch one that retains
     * sixteen routers out of seventeen. The rules above see the ArchUnit classpath, which excludes
     * {@code morpheus-provider-reference} and {@code morpheus-provider-testkit}; the text scan sees every module's
     * sources. Holding the two to the same set means a router declared where the rules cannot follow is refused by
     * name instead of silently escaping them.</p>
     */
    @Test
    void theRulesSeeEveryRouterTheSourcesDeclare() throws IOException {
        TreeSet<String> imported = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            if (javaClass.getSimpleName().endsWith(ROUTER_SUFFIX)) {
                imported.add(javaClass.getSimpleName());
            }
        }
        assertSameRouters(routerSources(repositoryRoot()).keySet(), imported);
    }

    /**
     * The text half of the first two rules, replayed over a synthetic repository. E2 showed it is the only half that
     * sees an inlined constant; a scan that stopped naming routers, or that named every router once one matched,
     * would still pass on the real tree.
     */
    @Test
    void theSourceScanNamesTheRouterThatMentionsTheLiteralAndNoOther(@TempDir Path repository) throws IOException {
        Path api = Files.createDirectories(repository.resolve("api/src/main/java/com/example"));
        Files.writeString(api.resolve("AlphaHttpRoutes.java"),
                "class AlphaHttpRoutes { int limit = MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS; }");
        Files.writeString(api.resolve("BetaHttpRoutes.java"), "class BetaHttpRoutes { }");
        Files.writeString(api.resolve("MorpheusHttpServer.java"),
                "class MorpheusHttpServer { MorpheusHttpResponseWriter writer; MorpheusRemoteRole role; }");
        Path tests = Files.createDirectories(repository.resolve("api/src/test/java/com/example"));
        Files.writeString(tests.resolve("GammaHttpRoutes.java"), "class GammaHttpRoutes { MorpheusRemoteRole role; }");

        Map<String, Path> routers = routerSources(repository);
        assertEquals(Set.of("AlphaHttpRoutes", "BetaHttpRoutes"), routers.keySet(),
                "only *HttpRoutes sources under a module's src/main/java are routers");
        AssertionError refusal = assertThrows(AssertionError.class,
                () -> assertNoRouterSourceMentions(routers, "MorpheusRemote"));
        assertTrue(refusal.getMessage().startsWith("routers mentioning MorpheusRemote: [AlphaHttpRoutes]"),
                () -> "the refusal must name AlphaHttpRoutes and no other router: " + refusal.getMessage());
        assertDoesNotThrow(() -> assertNoRouterSourceMentions(routers, "MorpheusHttpResponseWriter"),
                "a literal mentioned only outside the routers must pass");
    }

    /**
     * The bijection fails in two directions, and each names a different defect: a router the rules cannot see (E5
     * declared one in {@code morpheus-provider-testkit}), or a router the rules check that no source scan reads. Both
     * are replayed, each named on its own side of the message.
     */
    @Test
    void theRouterBijectionNamesTheEscapingAndTheSourcelessRouterEachOnItsSide() {
        AssertionError escaping = assertThrows(AssertionError.class,
                () -> assertSameRouters(Set.of("AlphaHttpRoutes", "ProbeHttpRoutes"), Set.of("AlphaHttpRoutes")));
        assertTrue(escaping.getMessage().contains("escaping every rule here: [ProbeHttpRoutes]; "
                        + "imported without a source the text scan can read: []"),
                () -> "a declared router the rules do not import must be named as escaping: " + escaping.getMessage());

        AssertionError sourceless = assertThrows(AssertionError.class,
                () -> assertSameRouters(Set.of("AlphaHttpRoutes"), Set.of("AlphaHttpRoutes", "GhostHttpRoutes")));
        assertTrue(sourceless.getMessage().contains("escaping every rule here: []; "
                        + "imported without a source the text scan can read: [GhostHttpRoutes]"),
                () -> "an imported router without a source must be named as sourceless: " + sourceless.getMessage());

        assertDoesNotThrow(() -> assertSameRouters(Set.of("AlphaHttpRoutes"), Set.of("AlphaHttpRoutes")),
                "the same routers on both sides must pass");
    }

    private static void assertSameRouters(Set<String> declared, Set<String> imported) {
        assertFalse(declared.isEmpty(), "no *HttpRoutes source found under any module's src/main/java");

        TreeSet<String> escaping = new TreeSet<>(declared);
        escaping.removeAll(imported);
        TreeSet<String> sourceless = new TreeSet<>(imported);
        sourceless.removeAll(declared);
        assertTrue(escaping.isEmpty() && sourceless.isEmpty(),
                () -> "the family rules must see exactly the routers the sources declare; declared outside the "
                        + "architecture-test classpath, so escaping every rule here: " + escaping
                        + "; imported without a source the text scan can read: " + sourceless);
    }

    private void assertNoRouterSourceMentions(String literal) throws IOException {
        assertNoRouterSourceMentions(routerSources(repositoryRoot()), literal);
    }

    private static void assertNoRouterSourceMentions(Map<String, Path> routers, String literal) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Path> router : routers.entrySet()) {
            if (Files.readString(router.getValue()).contains(literal)) {
                violations.add(router.getKey());
            }
        }
        assertTrue(violations.isEmpty(), () -> "routers mentioning " + literal + ": " + violations);
    }

    private static Map<String, Path> routerSources(Path repository) throws IOException {
        Map<String, Path> routers = new TreeMap<>();
        List<Path> modules;
        try (var children = Files.list(repository)) {
            modules = children.filter(Files::isDirectory).sorted().toList();
        }
        for (Path module : modules) {
            Path sources = module.resolve("src/main/java");
            if (!Files.isDirectory(sources)) {
                continue;
            }
            try (var files = Files.walk(sources)) {
                files.filter(path -> path.getFileName().toString().endsWith(ROUTER_SUFFIX + ".java"))
                        .forEach(path -> routers.put(
                                path.getFileName().toString().replace(".java", ""), path));
            }
        }
        return routers;
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
