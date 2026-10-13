package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classes that read the process-wide operational runtime are named, and their number only goes down.
 *
 * <p>{@code LocalOperationalRuntime} is one set of counters and one sink for the whole process. A class that reads it
 * directly cannot be observed by a test without every other test of the same JVM writing to the same counters, and
 * cannot be given another destination. The direction is to hand each class its recorder or its metrics by
 * constructor, as most of the classes below already accept, and to keep the static runtime as the default of the
 * process only.</p>
 *
 * <p>This rule holds the direction while that is done. It is textual: it is about which source mentions the name. A
 * class that starts to mention it is refused; a class that stops is removed from the list, which is the only edit the
 * list accepts. The same rule covers the tests, where the list is what has to reach zero.</p>
 */
class StaticOperationalRuntimeUsersArchitectureTest {
    private static final String RUNTIME = "LocalOperationalRuntime";

    /** Main classes that mention the static runtime, besides the runtime itself. Measured on 13/10/2026. */
    private static final Set<String> MAIN_USERS = Set.of(
            "AugmentedContextService.java",
            "ExternalJarIntegrity.java",
            "ExternalReferenceResolutionService.java",
            "IncrementalSyncService.java",
            "LocalSourceInventoryScanner.java",
            "LocalSourceWatcher.java",
            "MinosMcpCodeGateway.java",
            "MinosMcpExternalReferenceResolver.java",
            "MorpheusCli.java",
            "MorpheusCompositionCli.java",
            "MorpheusMain.java",
            "MorpheusOperabilityApiService.java",
            "MorpheusProjectSyncApiService.java",
            "MorpheusReasoningApiService.java",
            "MorpheusReasoningCli.java",
            "MorpheusReasoningMcpTools.java",
            "MorpheusRemoteHttpServer.java",
            "MultiProviderReadService.java",
            "NexusMcpContextGateway.java",
            "OperationalSlf4jServiceProvider.java",
            "SnapshotRecoveryService.java",
            "SqliteChangeLifecycleMutationStore.java",
            "SqliteContentionMetrics.java",
            "SqliteDatabaseLease.java",
            "UnforeseenFailureTrace.java");

    /**
     * Tests that read the static runtime. {@code SqliteContentionObservabilityTest} reads its counters as deltas
     * because the store path writes to the process-wide ones; {@code FixtureThirdPartyLogProcess} is a launched
     * process that names the log directory as the launcher does. No test is added.
     */
    private static final Set<String> TEST_USERS = Set.of(
            "FixtureThirdPartyLogProcess.java",
            "SqliteContentionObservabilityTest.java");

    @Test
    void noMainClassStartsToReadTheStaticRuntime() throws IOException {
        Set<String> found = sourcesMentioning("src/main/java");
        assertTrue(found.remove("LocalOperationalRuntime.java"), "the runtime itself was not found: " + found);

        assertEquals(new TreeSet<>(MAIN_USERS), found, "a class that reads " + RUNTIME + " directly is one a test "
                + "cannot observe alone. Take the recorder or the metrics by constructor and let the caller pass the "
                + "process-wide one; a class that no longer mentions it is removed from MAIN_USERS, and none is added");
    }

    @Test
    void noTestStartsToReadTheStaticRuntime() throws IOException {
        assertEquals(new TreeSet<>(TEST_USERS), sourcesMentioning("src/test/java"), "a test that reads " + RUNTIME
                + " observes what every other test of the JVM wrote. Build an OperationalMetrics of its own and hand "
                + "it to the code under test; a test that no longer mentions it is removed from TEST_USERS");
    }

    private static Set<String> sourcesMentioning(String sourceRoot) throws IOException {
        Path root = repositoryRoot();
        Set<String> found = new TreeSet<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path sources = module.resolve(sourceRoot);
                if (!module.getFileName().toString().startsWith("morpheus-") || !Files.isDirectory(sources)
                        || module.getFileName().toString().equals("morpheus-architecture-tests")) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(sources)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                        if (Files.readString(file).contains(RUNTIME)) {
                            assertTrue(found.add(file.getFileName().toString()),
                                    () -> "two sources share the name " + file.getFileName());
                        }
                    }
                }
            }
        }
        return found;
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
