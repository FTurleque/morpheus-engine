package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** {@code MorpheusHttpPathParser} declares no compile-time constant, so the rule has no blind spot to cover. */
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
        TreeSet<String> declared = new TreeSet<>(routerSources().keySet());
        TreeSet<String> imported = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            if (javaClass.getSimpleName().endsWith(ROUTER_SUFFIX)) {
                imported.add(javaClass.getSimpleName());
            }
        }
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
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Path> router : routerSources().entrySet()) {
            if (Files.readString(router.getValue()).contains(literal)) {
                violations.add(router.getKey());
            }
        }
        assertTrue(violations.isEmpty(), () -> "routers mentioning " + literal + ": " + violations);
    }

    private Map<String, Path> routerSources() throws IOException {
        Map<String, Path> routers = new TreeMap<>();
        List<Path> modules;
        try (var children = Files.list(repositoryRoot())) {
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
