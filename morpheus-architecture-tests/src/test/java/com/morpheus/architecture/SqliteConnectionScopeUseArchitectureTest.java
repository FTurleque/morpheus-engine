package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * <p>The rule is textual, because it is about how a class is wired: no rule on bytecode says whether two constructions
 * happen under one scope. A class of {@code api}, {@code mcp} or {@code cli} that builds a SQLite store either opens a
 * scope, or is named below as a class whose every operation builds a single store - one store is one connection, and a
 * scope would add nothing. A named class is refused the moment one try-with-resources opens two stores together.</p>
 */
class SqliteConnectionScopeUseArchitectureTest {
    private static final Pattern CONSTRUCTION = Pattern.compile("new Sqlite[A-Za-z]*Store\\(");
    private static final Pattern RESOURCES = Pattern.compile("try \\(([^{]*)\\{");
    private static final List<String> SCOPE_OPENINGS =
            List.of("SqliteConnectionScope.open(", "MorpheusMcpRuntime.openScope(");

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
                    "every portfolio command opens the portfolio store alone")));

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
        assertTrue(underAScope >= 4, "too few classes open a scope for the rule to have read the adapters: " + underAScope);

        Map<String, Integer> named = new TreeMap<>();
        ONE_STORE_PER_OPERATION.forEach((file, single) -> named.put(file, single.constructions()));
        assertEquals(named, outsideAScope, "a class of api, mcp or cli that builds SQLite stores opens a "
                + "SqliteConnectionScope first, so that they share one physical connection and one schema check. A class "
                + "whose every operation builds a single store is named in ONE_STORE_PER_OPERATION with that operation; "
                + "a class that now opens a scope, or builds no store, is removed from it");
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
        for (String module : List.of("morpheus-api", "morpheus-mcp", "morpheus-cli")) {
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
