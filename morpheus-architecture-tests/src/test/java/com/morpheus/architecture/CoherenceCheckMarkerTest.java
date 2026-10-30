package com.morpheus.architecture;

import com.morpheus.architecture.ArchitectureSuiteSources.Suite;
import com.morpheus.architecture.ArchitectureSuiteSources.TestClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A test class of the suite carries {@link CoherenceCheck} exactly when it evaluates no rule on bytecode, reads no
 * production source and loads no production class.
 *
 * <p>The property is derived from each class's source by {@link ArchitectureSuiteSources}, never declared in a list,
 * and compared with the marker in both directions: a documentation check left unmarked is named, and so is a marked
 * class that starts reading code. Marked classes stay in this module and in their milestone packages: the marker is a
 * selection handle and a classification that cannot lie, not a move. Intent: a property of the test sources, which
 * ArchUnit does not import - text.</p>
 */
@CoherenceCheck
class CoherenceCheckMarkerTest {

    /** A suite with no other class: a fixture is read on its own text. */
    private static final Suite ALONE = new Suite(Map.of(), Map.of());

    @Test
    void everyTestClassThatReadsNoCodeIsMarkedAndNoOtherIs() {
        List<String> unmarked = new ArrayList<>();
        List<String> wronglyMarked = new ArrayList<>();
        int marked = 0;
        Suite suite = ArchitectureSuiteSources.suite();
        for (TestClass testClass : suite.testClasses()) {
            boolean coherence = suite.isCoherenceCheck(testClass.source());
            boolean carries = ArchitectureSuiteSources.carriesTheCoherenceMarker(testClass.source());
            if (coherence && !carries) {
                unmarked.add(testClass.path());
            } else if (!coherence && carries) {
                wronglyMarked.add(testClass.path());
            }
            marked += carries ? 1 : 0;
        }
        assertTrue(marked > 0, "no test class carries @CoherenceCheck: the derivation or the marker is broken");
        assertAll(
                () -> assertEquals(List.of(), unmarked,
                        "these classes evaluate no rule, read no production source and load no production class: "
                                + "mark them @CoherenceCheck"),
                () -> assertEquals(List.of(), wronglyMarked,
                        "these classes carry @CoherenceCheck but evaluate a rule, read a production source or load a "
                                + "production class: remove the marker, or move that check to a class that reads code"));
    }

    @Test
    void aClassThatReadsOnlyDocumentsIsACoherenceCheck() {
        String source = """
                package com.morpheus.architecture.m99;

                import com.morpheus.architecture.RepositoryRoot;

                @CoherenceCheck
                class DocumentsTest {
                    @Test
                    void readsDocs() throws IOException {
                        String page = Files.readString(RepositoryRoot.locate().resolve("docs/README.md"));
                        assertTrue(page.contains("MORPHEUS"));
                    }
                }
                """;

        assertTrue(ALONE.isCoherenceCheck(source));
        assertTrue(ArchitectureSuiteSources.carriesTheCoherenceMarker(source));
    }

    @Test
    void aClassThatReadsAProductionSourceOrLoadsAProductionClassOrEvaluatesARuleIsNot() {
        String readsMain = """
                package com.morpheus.architecture;
                class ReadsMain {
                    @Test
                    void reads() throws IOException {
                        String server = Files.readString(Path.of("morpheus-api/src/main/java/Server.java"));
                    }
                }
                """;
        String readsThroughTheLocator = """
                package com.morpheus.architecture;
                class ReadsThroughTheLocator {
                    @Test
                    void reads() {
                        String server = ProductionSources.readMain("morpheus-api", "MorpheusHttpServer");
                    }
                }
                """;
        String loadsAClass = """
                package com.morpheus.architecture;
                import com.morpheus.domain.DomainIdentity;
                class LoadsAClass {
                    @Test
                    void loads() {
                        DomainIdentity.generate();
                    }
                }
                """;
        String livesInAProductionPackage = """
                package com.morpheus.cli;
                class LivesInAProductionPackage {
                    @Test
                    void reads() {
                        CliCommandPaths.paths();
                    }
                }
                """;
        String evaluatesARule = """
                package com.morpheus.architecture;
                import com.tngtech.archunit.core.importer.ClassFileImporter;
                class EvaluatesARule {
                    @Test
                    void evaluates() {
                    }
                }
                """;

        assertTrue(ArchitectureSuiteSources.readsAProductionSourceItself(readsMain));
        assertTrue(ArchitectureSuiteSources.readsAProductionSourceItself(readsThroughTheLocator));
        assertTrue(ArchitectureSuiteSources.loadsAProductionClass(loadsAClass));
        assertTrue(ArchitectureSuiteSources.loadsAProductionClass(livesInAProductionPackage));
        assertTrue(ArchitectureSuiteSources.evaluatesRulesOnBytecode(evaluatesARule));
        for (String source : List.of(readsMain, readsThroughTheLocator, loadsAClass, livesInAProductionPackage,
                evaluatesARule)) {
            assertFalse(ALONE.isCoherenceCheck(source), source);
        }
    }

    @Test
    void aProductionPathNamedOnlyInACommentIsNotARead() {
        String namedInAComment = """
                package com.morpheus.architecture;
                /** Reads what morpheus-api/src/main/java publishes, through docs/developer/API.md. */
                class NamedInAComment {
                    @Test
                    void reads() {
                        // ProductionSources.readMain("morpheus-api", "MorpheusHttpServer")
                    }
                }
                """;

        assertTrue(ALONE.isCoherenceCheck(namedInAComment));
    }

    @Test
    void aClassThatReadsCodeThroughAHelperOfAnotherClassOfTheSuiteIsFollowed() {
        String serverSources = """
                package com.morpheus.architecture;
                final class ServerSources {
                    static String serverText() throws IOException {
                        return Files.readString(Path.of("morpheus-api/src/main/java/Server.java"));
                    }
                }
                """;
        String pageSources = """
                package com.morpheus.architecture;
                final class PageSources {
                    static String apiPage() throws IOException {
                        return Files.readString(Path.of("docs/developer/API.md"));
                    }
                }
                """;
        String throughTheServerHelper = """
                package com.morpheus.architecture;
                class ThroughTheServerHelper {
                    @Test
                    void reads() throws IOException {
                        String server = ServerSources.serverText();
                        assertFalse(server.contains("Access-Control-Allow-Origin"));
                    }
                }
                """;
        String throughThePageHelper = """
                package com.morpheus.architecture;
                class ThroughThePageHelper {
                    @Test
                    void reads() throws IOException {
                        assertTrue(PageSources.apiPage().contains("loopback"));
                    }
                }
                """;
        Suite suite = new Suite(Map.of("ServerSources", serverSources, "PageSources", pageSources), Map.of());

        assertTrue(ALONE.isCoherenceCheck(throughTheServerHelper),
                "read alone, the class reads nothing: the helper decides");
        assertFalse(suite.isCoherenceCheck(throughTheServerHelper));
        assertTrue(suite.readsAProductionSource(throughTheServerHelper));
        assertTrue(suite.isCoherenceCheck(throughThePageHelper));
    }
}
