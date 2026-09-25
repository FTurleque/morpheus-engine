package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * An integration status crosses a remote or model-facing boundary through one projection only.
 *
 * <p>The status route projected the MINOS/NEXUS launch settings; the two augmented-context routes and the two
 * augmented-context MCP tools carried the same status object verbatim, three absolute server paths included. The
 * proposition is "every adapter that produces such a status calls the projection", which is a call that must
 * <em>exist</em> in a file, not a dependency that must be absent: a rule over compiled classes cannot see a call
 * that was never written (ADR-0103), so it is textual.</p>
 */
class IntegrationStatusProjectionArchitectureTest {

    private static final String PROJECTION = "IntegrationStatusDisclosure";
    private static final List<String> STATUS_SOURCES = List.of("new AugmentedContextService(", "Status.status()", "provider.status()");
    private static final List<String> BOUNDARY_MODULES = List.of("morpheus-api", "morpheus-mcp");

    @Test
    void everyRemoteOrModelFacingAdapterThatProducesAStatusGoesThroughTheProjection() throws IOException {
        List<String> producers = new ArrayList<>();
        List<String> unprojected = new ArrayList<>();
        for (String module : BOUNDARY_MODULES) {
            for (Path source : sources(module)) {
                String text = Files.readString(source);
                if (producesAStatus(text)) {
                    producers.add(source.getFileName().toString());
                    if (!goesThroughTheProjection(text)) {
                        unprojected.add(source.getFileName().toString());
                    }
                }
            }
        }
        assertTrue(producers.size() >= 3,
                "expected at least the two augmented-context adapters and the status route, found " + producers);
        assertEquals(List.of(), unprojected,
                "these adapters produce an integration status without " + PROJECTION);
    }

    @Test
    void theScannerRefusesAProducerThatNeverCallsTheProjection() {
        String verbatim = "return new AugmentedContextService(a, b).requirement(p, r, o).orElseThrow();";
        String projected = "return new AugmentedContextService(a, b).requirement(p, r, o)"
                + ".map(IntegrationStatusDisclosure::project).orElseThrow();";
        String viaViews = "return IntegrationStatusViews.status(provider.status());";

        assertTrue(producesAStatus(verbatim));
        assertFalse(goesThroughTheProjection(verbatim));
        assertTrue(goesThroughTheProjection(projected));
        assertTrue(goesThroughTheProjection(viaViews));
    }

    @Test
    void theCliKeepsTheFullSettingsWhereAnOperatorFixesThem() throws IOException {
        for (Path source : sources("morpheus-cli")) {
            assertFalse(Files.readString(source).contains(PROJECTION),
                    source.getFileName() + " must not project: the CLI is where the launch settings are fixed");
        }
    }

    @Test
    void theHttpViewIsATransformationOfTheSharedProjectionNotACopy() throws IOException {
        String views = Files.readString(repoRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/IntegrationStatusViews.java"));
        String projection = Files.readString(repoRoot().resolve(
                "morpheus-application/src/main/java/com/morpheus/application/security/IntegrationStatusDisclosure.java"));

        assertTrue(views.contains(PROJECTION + ".project("), "the status route must use the shared projection");
        assertFalse(views.contains("namesAServerLocation"), "the HTTP view must not carry its own copy of the predicate");
        assertTrue(projection.contains("LOCATION_KEYS"), "launch locations must be reported as configured rather than named");
        assertTrue(projection.contains("ServerLocationDisclosure.namesAServerLocation"),
                "the projection must consult the shared boundary predicate");
    }

    private static boolean producesAStatus(String text) {
        return STATUS_SOURCES.stream().anyMatch(text::contains);
    }

    private static boolean goesThroughTheProjection(String text) {
        return text.contains(PROJECTION + "::project")
                || text.contains(PROJECTION + ".project(")
                || text.contains("IntegrationStatusViews.status(");
    }

    private static List<Path> sources(String module) throws IOException {
        try (Stream<Path> tree = Files.walk(repoRoot().resolve(module + "/src/main/java"))) {
            return tree.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("IntegrationStatusViews.java"))
                    .sorted()
                    .toList();
        }
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))
                && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
