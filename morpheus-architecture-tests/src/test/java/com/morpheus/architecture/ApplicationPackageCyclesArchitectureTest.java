package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SliceRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packages of {@code morpheus-application} form no cycle (ADR-0162), at two levels.
 *
 * <p>The first-level rule slices {@code com.morpheus.application.(*)..} and has no exception: {@code query.dsl} and
 * {@code query.saved} are one slice there. The query platform is sliced again one level down, its root package being a
 * slice of its own, and that rule tolerates one pair only: the return edges from {@code query.dsl} to
 * {@code query.saved} that {@link #QUERY_DSL_AND_SAVED_EDGES} names. That list holds every edge of the pair, in both
 * directions, exactly: an edge that appears or disappears fails until the list and its key in
 * {@code config/debt-counter-ratchets.properties} follow, and the key is only ever lowered.</p>
 *
 * <p>The graph of the slice rules is read on the bytecode: a type reached through a chained call counts, a constant
 * that {@code javac} inlined does not. The list is measured on both the bytecode and the source text, so that an
 * inlined constant is still an edge. A nested type counts as its top-level type.</p>
 */
class ApplicationPackageCyclesArchitectureTest {

    private static final String APPLICATION = "com.morpheus.application";
    private static final String QUERY = APPLICATION + ".query";
    private static final String QUERY_SOURCES = "morpheus-application/src/main/java/com/morpheus/application/query";
    private static final String ROOT_SLICE = "(root)";

    /**
     * Every edge between {@code query.dsl} and {@code query.saved}, by top-level origin type, relative to
     * {@code com.morpheus.application.query}. The {@code dsl} origins are the return edges the second-level rule
     * ignores; the {@code saved} origins are the natural direction, held here so that it does not grow.
     */
    private static final Map<String, Set<String>> QUERY_DSL_AND_SAVED_EDGES = Map.ofEntries(
            Map.entry("dsl.QueryPublicViews", Set.of("saved.SavedViewDefinition", "saved.SavedViewEntry",
                    "saved.SavedViewId", "saved.SavedViewStatus", "saved.SavedViewVersion")),
            Map.entry("saved.SavedViewDefinition", Set.of("dsl.QueryBudgets", "dsl.QueryDefinition")),
            Map.entry("saved.SavedViewService", Set.of("dsl.QueryBudgets", "dsl.QueryDefinition",
                    "dsl.QueryExecutionService", "dsl.QueryResult", "dsl.QueryScope", "dsl.QueryValidator")),
            Map.entry("saved.SavedViewStore", Set.of("dsl.QueryScope")),
            Map.entry("saved.SavedViewVersion", Set.of("dsl.QueryDefinition")));

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(APPLICATION);

    private static final String PAIR_FIXTURE = "com.morpheus.architecture.cycles.query.pair";
    private static final String PAIR_FIXTURE_SOURCES =
            "morpheus-architecture-tests/src/test/java/com/morpheus/architecture/cycles/query/pair";

    private static ArchRule firstLevelPackagesFreeOfCycles(String root) {
        return slices().matching(root + ".(*)..")
                .should().beFreeOfCycles()
                .because("a package of the application depends on the packages whose vocabulary it uses, never back: "
                        + "a type that two packages need lives in the lower one, or in the domain when it decides on "
                        + "a single domain value (ADR-0162)");
    }

    /**
     * The second-level packages under {@code root}, the root package being a slice of its own, form no cycle beside
     * the edges from the {@code returnFrom} package that {@code edges} lists.
     */
    private static ArchRule secondLevelPackagesFreeOfCycles(String root, String returnFrom,
                                                            Map<String, Set<String>> edges) {
        SliceRule rule = slices().assignedFrom(secondLevelUnder(root)).should().beFreeOfCycles();
        for (Map.Entry<String, SortedSet<String>> edge : sorted(edges).entrySet()) {
            if (!edge.getKey().startsWith(returnFrom + ".")) {
                continue;
            }
            String origin = root + "." + edge.getKey();
            Set<String> targets = new TreeSet<>();
            edge.getValue().forEach(target -> targets.add(root + "." + target));
            rule = rule.ignoreDependency(
                    DescribedPredicate.describe("top-level type " + origin,
                            (JavaClass javaClass) -> topLevelName(javaClass).equals(origin)),
                    DescribedPredicate.describe("a listed top-level type of " + targets,
                            (JavaClass javaClass) -> targets.contains(topLevelName(javaClass))));
        }
        return rule.because("the query platform is layered one level down as well: a package under "
                + root + " depends on the packages whose vocabulary it uses, never back, and the only return "
                + "edges tolerated are the listed ones from " + returnFrom + ", a debt that only shrinks (ADR-0162)");
    }

    private static SliceAssignment secondLevelUnder(String root) {
        return new SliceAssignment() {
            @Override
            public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                String slice = secondLevelSliceOf(root, javaClass.getPackageName());
                return slice == null ? SliceIdentifier.ignore() : SliceIdentifier.of(slice);
            }

            @Override
            public String getDescription() {
                return "the second-level packages under " + root + ", its root package included";
            }
        };
    }

    private static String secondLevelSliceOf(String root, String packageName) {
        if (packageName.equals(root)) {
            return ROOT_SLICE;
        }
        if (packageName.startsWith(root + ".")) {
            return packageName.substring(root.length() + 1).split("\\.")[0];
        }
        return null;
    }

    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getBaseComponentType().getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    /** The direct dependencies between the two packages, read on the bytecode, by top-level type. */
    private static SortedMap<String, SortedSet<String>> bytecodeEdges(JavaClasses classes, String root,
                                                                      String first, String second) {
        SortedMap<String, SortedSet<String>> edges = new TreeMap<>();
        for (JavaClass javaClass : classes) {
            String originSlice = secondLevelSliceOf(root, javaClass.getPackageName());
            String other = first.equals(originSlice) ? second : second.equals(originSlice) ? first : null;
            if (other == null) {
                continue;
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass().getBaseComponentType();
                if (other.equals(secondLevelSliceOf(root, target.getPackageName()))) {
                    edges.computeIfAbsent(relative(root, topLevelName(javaClass)), key -> new TreeSet<>())
                            .add(relative(root, topLevelName(target)));
                }
            }
        }
        return edges;
    }

    /** The types of the other package that each source of the two packages names, outside its comments. */
    private static SortedMap<String, SortedSet<String>> textEdges(Path sources, String root, String first,
                                                                  String second) throws IOException {
        SortedMap<String, SortedSet<String>> edges = new TreeMap<>();
        for (List<String> direction : List.of(List.of(first, second), List.of(second, first))) {
            String own = direction.get(0);
            String other = direction.get(1);
            String qualifiedOther = Pattern.quote(root + "." + other);
            Pattern named = Pattern.compile(
                    "\\b" + qualifiedOther + "\\.((?:[a-z][A-Za-z0-9_]*\\.)*[A-Z][A-Za-z0-9_]*)");
            Pattern wildcard = Pattern.compile("\\b" + qualifiedOther + "(?:\\.[a-z][A-Za-z0-9_]*)*\\.\\*");
            List<Path> files;
            try (Stream<Path> tree = Files.walk(sources.resolve(own))) {
                files = tree.filter(file -> file.toString().endsWith(".java")).sorted().toList();
            }
            for (Path file : files) {
                String text = withoutComments(Files.readString(file));
                if (wildcard.matcher(text).find()) {
                    throw new IllegalStateException(file + " imports a whole package of " + root + "." + other
                            + ": name each type, so that the edge list can read it");
                }
                String relativeFile = sources.relativize(file).toString().replace('\\', '/');
                String origin = relativeFile.substring(0, relativeFile.length() - ".java".length()).replace('/', '.');
                Matcher matcher = named.matcher(text);
                while (matcher.find()) {
                    edges.computeIfAbsent(origin, key -> new TreeSet<>()).add(other + "." + matcher.group(1));
                }
            }
        }
        return edges;
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static SortedMap<String, SortedSet<String>> union(Map<String, SortedSet<String>> first,
                                                              Map<String, SortedSet<String>> second) {
        SortedMap<String, SortedSet<String>> union = new TreeMap<>();
        for (Map<String, SortedSet<String>> edges : List.of(first, second)) {
            edges.forEach((origin, targets) -> union.computeIfAbsent(origin, key -> new TreeSet<>()).addAll(targets));
        }
        return union;
    }

    private static SortedMap<String, SortedSet<String>> sorted(Map<String, ? extends Set<String>> edges) {
        SortedMap<String, SortedSet<String>> sorted = new TreeMap<>();
        edges.forEach((origin, targets) -> sorted.put(origin, new TreeSet<>(targets)));
        return sorted;
    }

    private static SortedSet<String> arrows(Map<String, ? extends Set<String>> edges) {
        SortedSet<String> arrows = new TreeSet<>();
        edges.forEach((origin, targets) -> targets.forEach(target -> arrows.add(origin + " -> " + target)));
        return arrows;
    }

    private static String relative(String root, String qualifiedName) {
        return qualifiedName.substring(root.length() + 1);
    }

    @Test
    void theApplicationPackagesAreFreeOfCycles() {
        firstLevelPackagesFreeOfCycles(APPLICATION).check(PRODUCTION);
    }

    @Test
    void theCycleRuleSeesEveryFirstLevelPackageOfTheApplicationSources() throws IOException {
        Path sources = RepositoryRoot.locate().resolve("morpheus-application/src/main/java/com/morpheus/application");
        SortedSet<String> declared = new TreeSet<>();
        try (Stream<Path> files = Files.walk(sources)) {
            files.filter(file -> file.toString().endsWith(".java"))
                    .map(file -> sources.relativize(file))
                    .filter(relative -> relative.getNameCount() > 1)
                    .forEach(relative -> declared.add(relative.getName(0).toString()));
        }
        SortedSet<String> imported = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            String rest = javaClass.getPackageName().substring(APPLICATION.length());
            if (rest.startsWith(".")) {
                imported.add(rest.substring(1).split("\\.")[0]);
            }
        }
        assertTrue(declared.size() > 20, () -> "the application sources were read from " + sources + ": " + declared);
        assertEquals(declared, imported, "the cycle rule must judge every first-level package that "
                + "morpheus-application declares, read on its compiled classes");
    }

    @Test
    void theCycleRuleRefusesTwoPackagesThatDependOnEachOther() {
        String fixture = "com.morpheus.architecture.cycles.fixture";
        JavaClasses cyclic = new ClassFileImporter().importPackages(fixture);
        AssertionError refused = assertThrows(AssertionError.class,
                () -> firstLevelPackagesFreeOfCycles(fixture).check(cyclic));
        String message = refused.getMessage();
        assertTrue(message.contains("Cycle detected") && message.contains("Slice alpha")
                        && message.contains("Slice beta") && message.contains("ADR-0162"),
                message);
    }

    @Test
    void theQueryPlatformPackagesAreFreeOfCyclesBesideTheListedReturnEdges() {
        secondLevelPackagesFreeOfCycles(QUERY, "dsl", QUERY_DSL_AND_SAVED_EDGES).check(PRODUCTION);
    }

    @Test
    void theEdgesBetweenTheQueryDslAndTheSavedViewsAreExactlyTheListedOnes() throws IOException {
        SortedSet<String> measured = arrows(union(bytecodeEdges(PRODUCTION, QUERY, "dsl", "saved"),
                textEdges(RepositoryRoot.locate().resolve(QUERY_SOURCES), QUERY, "dsl", "saved")));
        SortedSet<String> listed = arrows(QUERY_DSL_AND_SAVED_EDGES);
        assertTrue(measured.stream().anyMatch(arrow -> arrow.startsWith("dsl."))
                        && measured.stream().anyMatch(arrow -> arrow.startsWith("saved.")),
                () -> "the edges of the pair were measured on " + QUERY_SOURCES + " and its classes: " + measured);
        SortedSet<String> appeared = new TreeSet<>(measured);
        appeared.removeAll(listed);
        SortedSet<String> disappeared = new TreeSet<>(listed);
        disappeared.removeAll(measured);
        assertEquals(new TreeSet<>(), appeared, "an edge between query.dsl and query.saved appeared: the list only "
                + "shrinks, so remove the dependency, or replace ADR-0162 by a consolidated decision that names it");
        assertEquals(new TreeSet<>(), disappeared, "an edge between query.dsl and query.saved is gone: remove it from "
                + "QUERY_DSL_AND_SAVED_EDGES and lower its key in config/debt-counter-ratchets.properties");
    }

    @Test
    void theQueryCycleRuleSeesEverySecondLevelPackageOfTheQuerySources() throws IOException {
        Path sources = RepositoryRoot.locate().resolve(QUERY_SOURCES);
        SortedSet<String> declared = new TreeSet<>();
        try (Stream<Path> files = Files.walk(sources)) {
            files.filter(file -> file.toString().endsWith(".java"))
                    .map(file -> sources.relativize(file))
                    .forEach(relative -> declared.add(
                            relative.getNameCount() > 1 ? relative.getName(0).toString() : ROOT_SLICE));
        }
        SortedSet<String> imported = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            String slice = secondLevelSliceOf(QUERY, javaClass.getPackageName());
            if (slice != null) {
                imported.add(slice);
            }
        }
        assertTrue(declared.containsAll(Set.of(ROOT_SLICE, "dsl", "saved")),
                () -> "the query sources were read from " + sources + ": " + declared);
        assertEquals(declared, imported, "the query cycle rule must judge every second-level package of "
                + QUERY + ", and its root, read on its compiled classes");
    }

    @Test
    void theQueryCycleRuleRefusesTwoSecondLevelPackagesThatDependOnEachOther() {
        JavaClasses cyclic = new ClassFileImporter().importPackages(PAIR_FIXTURE);
        AssertionError refused = assertThrows(AssertionError.class,
                () -> secondLevelPackagesFreeOfCycles(PAIR_FIXTURE, "head", Map.of()).check(cyclic));
        String message = refused.getMessage();
        assertTrue(message.contains("Cycle detected") && message.contains("Slice head")
                && message.contains("Slice tail") && message.contains("ADR-0162"), message);
    }

    @Test
    void theQueryCycleRuleIgnoresTheListedReturnEdgesAndRefusesAnUnlistedOne() {
        JavaClasses cyclic = new ClassFileImporter().importPackages(PAIR_FIXTURE);
        secondLevelPackagesFreeOfCycles(PAIR_FIXTURE, "head",
                Map.of("head.Head", Set.of("tail.Marker", "tail.Tail"))).check(cyclic);

        AssertionError refused = assertThrows(AssertionError.class,
                () -> secondLevelPackagesFreeOfCycles(PAIR_FIXTURE, "head",
                        Map.of("head.Head", Set.of("tail.Tail"))).check(cyclic));
        String message = refused.getMessage();
        assertTrue(message.contains("Cycle detected") && message.contains("Marker"), message);
    }

    @Test
    void theQueryCycleRuleRefusesACycleThroughTheRootPackage() {
        String fixture = "com.morpheus.architecture.cycles.query.rooted";
        JavaClasses cyclic = new ClassFileImporter().importPackages(fixture);
        AssertionError refused = assertThrows(AssertionError.class,
                () -> secondLevelPackagesFreeOfCycles(fixture, "leaf", Map.of()).check(cyclic));
        String message = refused.getMessage();
        assertTrue(message.contains("Cycle detected") && message.contains("Slice " + ROOT_SLICE)
                && message.contains("Slice leaf"), message);
    }

    @Test
    void theEdgeMeasurementSeesAnInlinedConstantByTheTextAndCountsANestedTypeAsItsTopLevelType()
            throws IOException {
        JavaClasses classes = new ClassFileImporter().importPackages(PAIR_FIXTURE);
        SortedMap<String, SortedSet<String>> bytecode = bytecodeEdges(classes, PAIR_FIXTURE, "head", "tail");
        SortedMap<String, SortedSet<String>> text =
                textEdges(RepositoryRoot.locate().resolve(PAIR_FIXTURE_SOURCES), PAIR_FIXTURE, "head", "tail");

        assertFalse(arrows(bytecode).contains("tail.Tail -> head.Limits"),
                () -> "javac inlines Limits.MAX, so the bytecode keeps no dependency on Limits: " + bytecode);
        assertTrue(arrows(text).contains("tail.Tail -> head.Limits"), () -> "the text names Limits: " + text);
        assertTrue(arrows(bytecode).contains("head.Head -> tail.Tail"),
                () -> "Head depends on Tail.Nested, counted as Tail: " + bytecode);
        assertEquals(new TreeSet<>(List.of("head.Head -> tail.Marker", "head.Head -> tail.Tail",
                        "tail.Tail -> head.Head", "tail.Tail -> head.Limits")),
                arrows(union(bytecode, text)));
    }

    @Test
    void theEdgeMeasurementRefusesAWholePackageImport(@TempDir Path sources) throws IOException {
        Path head = Files.createDirectories(sources.resolve("head"));
        Files.createDirectories(sources.resolve("tail"));
        Files.writeString(head.resolve("Head.java"), "package " + PAIR_FIXTURE + ".head;\n\nimport "
                + PAIR_FIXTURE + ".tail.*;\n\npublic final class Head {\n}\n");
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> textEdges(sources, PAIR_FIXTURE, "head", "tail"));
        assertTrue(refused.getMessage().contains("imports a whole package"), refused.getMessage());
    }
}
