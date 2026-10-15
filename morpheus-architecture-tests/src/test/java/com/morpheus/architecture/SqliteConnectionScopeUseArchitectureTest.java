package com.morpheus.architecture;

import com.morpheus.store.sqlite.SqliteConnectionScope;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An adapter that builds SQLite stores for one operation shares one connection between them.
 *
 * <p>A store built outside a {@code SqliteConnectionScope} opens a physical connection of its own and checks the
 * schema again. The MCP runtime built seven that way for one tool call, where the HTTP and CLI runtimes share one
 * connection between nine stores: transports the manifest declares equivalent put different pressure on the database
 * and read it through different connections.</p>
 *
 * <p>The first rule is textual, because it is about how a class is wired: no rule on bytecode says whether two
 * constructions happen under one scope. A class of {@code api}, {@code mcp}, {@code cli} or of the SQLite adapter itself
 * that builds a SQLite store either opens a scope, or is named below as a class whose every operation builds a single
 * store - one store is one connection, and a scope would add nothing. A named class is refused the moment one
 * try-with-resources opens two stores together.</p>
 *
 * <p>The textual rule read the three transport adapters only, and that is where it was blind: the query and policy
 * store sets live in the SQLite adapter, opened five and eight stores without a scope, and served the CLI, the MCP
 * server and, for policy, HTTP. It now reads that module too, and a rule on bytecode stands beside it, because the two
 * do not see the same thing (ADR-0103). The bytecode rule knows no list of modules - every production class on the
 * classpath that builds two different stores must depend on the scope - and it sees a store built through a
 * constructor reference, which the text does not spell {@code new Sqlite...Store(}. It cannot say that the scope is
 * open around the constructions, nor see one store type built twice in one block: the text holds those.</p>
 */
class SqliteConnectionScopeUseArchitectureTest {
    private static final Pattern CONSTRUCTION = Pattern.compile("new Sqlite[A-Za-z]*Store\\(");
    private static final Pattern RESOURCES = Pattern.compile("try \\(([^{]*)\\{");
    private static final List<String> SCOPE_OPENINGS = List.of(
            "SqliteConnectionScope.open(", "MorpheusMcpRuntime.openScope(", "SqliteStoreConnection.openScope(");
    private static final List<String> MODULES_THAT_BUILD_STORES =
            List.of("morpheus-api", "morpheus-mcp", "morpheus-cli", "morpheus-store-sqlite");
    private static final String SQLITE_ADAPTER = "com.morpheus.store.sqlite";

    /** Classes that build stores outside a scope, one store per operation, with how many and for which operation. */
    private static final Map<String, SingleStore> ONE_STORE_PER_OPERATION = Map.ofEntries(
            Map.entry("MorpheusLocalHttpServerBootstrap.java", new SingleStore(1,
                    "the snapshot recovery run once when the local server starts")),
            Map.entry("MorpheusOperabilityApiService.java", new SingleStore(1,
                    "the readiness and operability reads, one store each time")),
            Map.entry("MorpheusPolicyManagementHttpRoutes.java", new SingleStore(2,
                    "listing activations and removing an override, two routes that each open the policy-pack store")),
            Map.entry("MorpheusPortfolioApiService.java", new SingleStore(1,
                    "every portfolio operation opens the portfolio store alone")),
            Map.entry("MorpheusMcpServer.java", new SingleStore(1,
                    "the snapshot recovery run once when the MCP server starts")),
            Map.entry("MorpheusPolicyMcpManagementTools.java", new SingleStore(1,
                    "every policy management tool opens the policy-pack store alone")),
            Map.entry("MorpheusPortfolioMcpTools.java", new SingleStore(1,
                    "every portfolio tool opens the portfolio store alone")),
            Map.entry("CliProjectWriteCapabilityResolver.java", new SingleStore(1,
                    "resolving the write capability of one project reads the knowledge store alone")),
            Map.entry("MorpheusPortfolioCli.java", new SingleStore(1,
                    "every portfolio command opens the portfolio store alone")),
            Map.entry("SqliteServerMaintenance.java", new SingleStore(1,
                    "a backup brings the live database to the current schema through the knowledge store alone")));

    private record SingleStore(int constructions, String operation) {
    }

    @Test
    void anAdapterClassThatBuildsStoresOpensAScopeOrIsNamedForItsSingleStore() throws IOException {
        Map<String, Integer> outsideAScope = new TreeMap<>();
        int underAScope = 0;
        for (Path source : adapterSources()) {
            String text = Files.readString(source);
            int constructions = occurrences(text);
            if (constructions == 0) {
                continue;
            }
            if (SCOPE_OPENINGS.stream().anyMatch(text::contains)) {
                underAScope++;
            } else {
                outsideAScope.put(source.getFileName().toString(), constructions);
            }
        }

        Map<String, Integer> named = new TreeMap<>();
        ONE_STORE_PER_OPERATION.forEach((file, single) -> named.put(file, single.constructions()));
        assertEquals(named, outsideAScope, "a class of api, mcp, cli or store-sqlite that builds SQLite stores opens a "
                + "SqliteConnectionScope first, so that they share one physical connection and one schema check. A class "
                + "whose every operation builds a single store is named in ONE_STORE_PER_OPERATION with that operation; "
                + "a class that now opens a scope, or builds no store, is removed from it");
        assertTrue(underAScope >= 6, "too few classes open a scope for the rule to have read the three transport "
                + "adapters and the store sets of the SQLite adapter: " + underAScope);
    }

    @Test
    void aClassNamedForItsSingleStoreNeverOpensTwoTogether() throws IOException {
        for (Path source : adapterSources()) {
            String file = source.getFileName().toString();
            if (!ONE_STORE_PER_OPERATION.containsKey(file)) {
                continue;
            }
            assertFalse(ONE_STORE_PER_OPERATION.get(file).operation().isBlank(), file);
            for (String resources : resourceHeaders(Files.readString(source))) {
                assertTrue(occurrences(resources) <= 1, () -> file + " opens two SQLite stores together outside a "
                        + "scope - two physical connections for one operation: open a SqliteConnectionScope first\n"
                        + resources.strip());
            }
        }
    }

    @Test
    void theShapeOfTwoStoresOpenedTogetherIsRecognised() {
        String shared = """
                try (SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(databasePath);
                     SqliteCompositionStateStore compositions = new SqliteCompositionStateStore(databasePath)) {
                    use(snapshots, compositions);
                }
                """;
        String apart = """
                try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(databasePath)) {
                    first(store);
                }
                try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(databasePath)) {
                    second(store);
                }
                """;

        assertEquals(List.of(2), resourceHeaders(shared).stream().map(this::occurrences).toList());
        assertEquals(List.of(1, 1), resourceHeaders(apart).stream().map(this::occurrences).toList());
    }

    /**
     * The same invariant read on bytecode, over every production class and not a list of modules: a class that builds
     * two different SQLite stores serves an operation that needs both, and reaches them through one scope.
     */
    @Test
    void aClassThatBuildsTwoDifferentStoresDependsOnTheConnectionScope() {
        Map<String, Set<String>> withoutAScope = new TreeMap<>();
        for (JavaClass javaClass : productionClasses()) {
            Set<String> stores = storesBuiltBy(javaClass);
            boolean reachesTheScope = javaClass.getDirectDependenciesFromSelf().stream()
                    .anyMatch(dependency -> dependency.getTargetClass().isEquivalentTo(SqliteConnectionScope.class));
            if (stores.size() >= 2 && !reachesTheScope) {
                withoutAScope.put(javaClass.getName(), stores);
            }
        }

        assertEquals(Map.of(), withoutAScope, "stores built outside a SqliteConnectionScope each open a physical "
                + "connection and check the schema again: a class that builds several for one operation opens the "
                + "scope first. These classes build the stores listed and never reach the scope");
    }

    /** The bytecode rule must reach the runtimes and the store sets, or it holds nothing that matters. */
    @Test
    void theBytecodeRuleReachesTheRuntimesAndTheStoreSets() {
        Set<String> builders = new TreeSet<>();
        for (JavaClass javaClass : productionClasses()) {
            if (storesBuiltBy(javaClass).size() >= 2) {
                builders.add(javaClass.getSimpleName());
            }
        }

        assertTrue(builders.containsAll(Set.of(
                        "ApiRuntime", "CliRuntime", "MorpheusMcpRuntime", "SqliteQueryStores", "SqlitePolicyStores")),
                () -> "the classes that build several SQLite stores must include the three runtimes and the two store "
                        + "sets: " + builders);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.morpheus");
    }

    /** The store types a class constructs, by a constructor call or a constructor reference; never its own. */
    private static Set<String> storesBuiltBy(JavaClass javaClass) {
        Set<String> stores = new TreeSet<>();
        Stream.concat(
                        javaClass.getConstructorCallsFromSelf().stream().map(call -> call.getTargetOwner()),
                        javaClass.getConstructorReferencesFromSelf().stream().map(reference -> reference.getTargetOwner()))
                .filter(owner -> owner.getPackageName().equals(SQLITE_ADAPTER))
                .filter(owner -> owner.getSimpleName().matches("Sqlite[A-Za-z]*Store"))
                .filter(owner -> !owner.equals(javaClass))
                .forEach(owner -> stores.add(owner.getName()));
        return stores;
    }

    /** The resources of each try-with-resources: what is opened together, for one block. */
    private static List<String> resourceHeaders(String text) {
        List<String> headers = new java.util.ArrayList<>();
        Matcher header = RESOURCES.matcher(text);
        while (header.find()) {
            headers.add(header.group(1));
        }
        return headers;
    }

    private int occurrences(String text) {
        Matcher construction = CONSTRUCTION.matcher(text);
        int count = 0;
        while (construction.find()) {
            count++;
        }
        return count;
    }

    private static List<Path> adapterSources() throws IOException {
        Path root = repositoryRoot();
        List<Path> sources = new java.util.ArrayList<>();
        for (String module : MODULES_THAT_BUILD_STORES) {
            try (Stream<Path> files = Files.walk(root.resolve(module).resolve("src/main/java"))) {
                files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(sources::add);
            }
        }
        return sources;
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
