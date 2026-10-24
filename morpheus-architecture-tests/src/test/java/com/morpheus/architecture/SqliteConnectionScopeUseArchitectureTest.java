package com.morpheus.architecture;

import com.morpheus.store.sqlite.runtime.SqliteConnectionScope;
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
 * <p>Since 15 October 2026 one class builds stores for an operation, {@code SqliteOperationRuntime}, and every transport
 * reads through it. The rules say so as closed lists: a class that builds a store either opens the scope itself - it is
 * then named in {@link #SCOPE_OWNERS} - or is named in {@link #ONE_STORE_PER_OPERATION} with the one operation it
 * serves. A new class that builds a store is refused until somebody names it, which is how sixteen classes building
 * stores for seven scope openings went unseen.</p>
 *
 * <p>The first rule is textual, because it is about how a class is wired: no rule on bytecode says whether two
 * constructions happen under one scope. It reads code, not comments - a scope opening that only a comment spelled used
 * to satisfy it - and requires the opening to come before the first construction. The second reads bytecode, over every
 * production class and not a list of modules: a class that builds a store calls {@code SqliteConnectionScope.open}
 * itself. It used to ask only for a dependency on the scope class, which a field of that type, or a catch of its
 * nested exception, satisfied. It sees a store built through a constructor reference, which the text does not spell
 * {@code new Sqlite...Store(}; it cannot see that the scope is open around the constructions, which the text holds
 * ({@code .claude/rules/architecture.md}).</p>
 */
class SqliteConnectionScopeUseArchitectureTest {
    private static final Pattern CONSTRUCTION = Pattern.compile("new Sqlite[A-Za-z]*Store\\(");
    private static final Pattern RESOURCES = Pattern.compile("try \\(([^{]*)\\{");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);
    private static final String SCOPE_OPENING = "SqliteConnectionScope.open(";
    private static final List<String> MODULES_THAT_BUILD_STORES =
            List.of("morpheus-api", "morpheus-mcp", "morpheus-cli", "morpheus-store-sqlite");
    private static final String SQLITE_ADAPTER = "com.morpheus.store.sqlite.adapter";

    /** The classes that build the stores of an operation under a scope they open themselves. */
    private static final Set<String> SCOPE_OWNERS = Set.of("SqliteOperationRuntime");

    /** Classes that build stores outside a scope, one store per operation, with how many and for which operation. */
    private static final Map<String, SingleStore> ONE_STORE_PER_OPERATION = Map.ofEntries(
            Map.entry("CliProjectWriteCapabilityResolver", new SingleStore(1,
                    "resolving the write capability of one project reads the knowledge store alone, from inside the "
                            + "operation of its caller: a store built while a scope is open on the thread borrows its "
                            + "connection, where a runtime opened there would be refused as a nested scope")),
            Map.entry("SqliteServerMaintenance", new SingleStore(1,
                    "a backup brings the live database to the current schema through the knowledge store alone")));

    private record SingleStore(int constructions, String operation) {
    }

    @Test
    void anAdapterClassThatBuildsStoresOpensAScopeOrIsNamedForItsSingleStore() throws IOException {
        Map<String, Integer> outsideAScope = new TreeMap<>();
        Set<String> underAScope = new TreeSet<>();
        for (Path source : adapterSources()) {
            String code = withoutComments(Files.readString(source));
            int constructions = occurrences(code);
            if (constructions == 0) {
                continue;
            }
            String name = className(source);
            int opening = code.indexOf(SCOPE_OPENING);
            if (opening < 0) {
                outsideAScope.put(name, constructions);
                continue;
            }
            Matcher first = CONSTRUCTION.matcher(code);
            assertTrue(first.find() && opening < first.start(), () -> name + " builds a SQLite store before it opens "
                    + "the connection scope: a store built first opens a physical connection of its own");
            underAScope.add(name);
        }

        Map<String, Integer> named = new TreeMap<>();
        ONE_STORE_PER_OPERATION.forEach((file, single) -> named.put(file, single.constructions()));
        assertEquals(named, outsideAScope, "a class of api, mcp, cli or store-sqlite that builds SQLite stores opens a "
                + "SqliteConnectionScope first, so that they share one physical connection and one schema check. A class "
                + "whose every operation builds a single store is named in ONE_STORE_PER_OPERATION with that operation; "
                + "a class that now opens a scope, or builds no store, is removed from it");
        assertEquals(new TreeSet<>(SCOPE_OWNERS), underAScope, "the classes that build stores under a scope they open "
                + "are named in SCOPE_OWNERS: an operation reads through SqliteOperationRuntime rather than assembling "
                + "a set of its own");
    }

    @Test
    void aClassNamedForItsSingleStoreNeverOpensTwoTogether() throws IOException {
        for (Path source : adapterSources()) {
            String file = className(source);
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

        assertEquals(List.of(2), resourceHeaders(shared).stream().map(SqliteConnectionScopeUseArchitectureTest::occurrences).toList());
        assertEquals(List.of(1, 1), resourceHeaders(apart).stream().map(SqliteConnectionScopeUseArchitectureTest::occurrences).toList());
    }

    /**
     * The same invariant read on bytecode, over every production class and not a list of modules: a class that builds a
     * SQLite store calls the opening of the scope, or is named for its single store.
     */
    @Test
    void aClassThatBuildsAStoreCallsTheScopeOpeningOrIsNamedForItsSingleStore() {
        Set<String> opening = new TreeSet<>();
        for (JavaClass javaClass : productionClasses()) {
            boolean opens = javaClass.getMethodCallsFromSelf().stream()
                    .anyMatch(call -> call.getTargetOwner().isEquivalentTo(SqliteConnectionScope.class)
                            && call.getName().equals("open"));
            if (opens) {
                opening.add(topLevelSimpleName(javaClass));
            }
        }
        Map<String, Set<String>> withoutAnOpening = new TreeMap<>();
        storesBuiltByTopLevelClass().forEach((builder, built) -> {
            if (!opening.contains(builder) && !ONE_STORE_PER_OPERATION.containsKey(builder)) {
                withoutAnOpening.put(builder, built);
            }
        });

        assertEquals(Map.of(), withoutAnOpening, "stores built outside a SqliteConnectionScope each open a physical "
                + "connection and check the schema again: a class that builds one for an operation calls "
                + "SqliteConnectionScope.open itself, or is named in ONE_STORE_PER_OPERATION. These classes build the "
                + "stores listed and never open the scope");
    }

    /** The bytecode rule must reach the operation runtime and every store it builds, or it holds nothing that matters. */
    @Test
    void theBytecodeRuleReachesTheOperationRuntimeAndEveryStoreItBuilds() {
        Map<String, Set<String>> stores = storesBuiltByTopLevelClass();
        Set<String> named = new TreeSet<>(SCOPE_OWNERS);
        named.addAll(ONE_STORE_PER_OPERATION.keySet());

        assertEquals(named, stores.keySet(), "the classes that build SQLite stores, read on bytecode, are the scope "
                + "owners and the classes named for their single store");
        assertTrue(stores.get("SqliteOperationRuntime").size() >= 12, () -> "the operation runtime builds every store "
                + "an operation reads through: " + stores.get("SqliteOperationRuntime"));
    }

    /** The store types each top-level class builds, its nested classes and lambdas included. */
    private static Map<String, Set<String>> storesBuiltByTopLevelClass() {
        Map<String, Set<String>> stores = new TreeMap<>();
        for (JavaClass javaClass : productionClasses()) {
            Set<String> built = storesBuiltBy(javaClass);
            if (!built.isEmpty()) {
                stores.computeIfAbsent(topLevelSimpleName(javaClass), ignored -> new TreeSet<>()).addAll(built);
            }
        }
        return stores;
    }

    private static String topLevelSimpleName(JavaClass javaClass) {
        String name = javaClass.getName().substring(javaClass.getPackageName().length() + 1);
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
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

    private static String withoutComments(String text) {
        return COMMENT.matcher(text).replaceAll(" ");
    }

    private static String className(Path source) {
        String file = source.getFileName().toString();
        return file.substring(0, file.length() - ".java".length());
    }

    private static int occurrences(String text) {
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
