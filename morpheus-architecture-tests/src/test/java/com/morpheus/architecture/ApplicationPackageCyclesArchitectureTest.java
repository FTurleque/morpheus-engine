package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dependencies between the packages of {@code morpheus-application} that lie on a cycle are named, and their list
 * only shrinks (ADR-0152).
 *
 * <p>A package here is a first-level slice, {@code com.morpheus.application.(*)..}: {@code query.dsl} and
 * {@code query.saved} are one slice, and a dependency between them is out of this rule's sight. The suite computes, from
 * the compiled classes, the dependency graph between slices, its strongly connected components, and every
 * slice-to-slice dependency inside a component of more than one slice. That set must equal
 * {@link #DEPENDENCIES_ON_A_CYCLE}: a dependency that closes a cycle and is not named fails, and a name whose dependency
 * no longer lies on a cycle fails until it is removed. No count of cycles is a threshold: the number of elementary
 * cycles is combinatorial, and ArchUnit reports it only up to {@code archunit.cycles.maxNumberToDetect}.</p>
 *
 * <p>The graph is read on the bytecode: a type reached through a chained call counts, a constant that {@code javac}
 * inlined does not.</p>
 */
class ApplicationPackageCyclesArchitectureTest {

    private static final String APPLICATION = "com.morpheus.application";

    /** The package of the store ports, which takes part in no cycle. */
    private static final String STORE = "store";

    /** Source slice, then target slice: the dependencies that lie on a cycle, and only they. */
    static final Set<String> DEPENDENCIES_ON_A_CYCLE = Set.of(
            "context -> operability",
            "context -> query",
            "context -> reference",
            "ingestion -> operability",
            "ingestion -> read",
            "ingestion -> security",
            "ingestion -> snapshot",
            "ingestion -> traceability",
            "lifecycle -> orchestration",
            "operability -> security",
            "orchestration -> lifecycle",
            "orchestration -> quality",
            "quality -> lifecycle",
            "query -> traceability",
            "read -> ingestion",
            "reference -> operability",
            "security -> context",
            "security -> operability",
            "security -> reference",
            "snapshot -> operability",
            "traceability -> ingestion");

    /** The strongly connected components of more than one slice that the named dependencies form. */
    static final Set<Set<String>> CYCLIC_COMPONENTS = Set.of(
            Set.of("context", "ingestion", "operability", "query", "read", "reference", "security", "snapshot",
                    "traceability"),
            Set.of("lifecycle", "orchestration", "quality"));

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(APPLICATION);

    @Test
    void noDependencyBetweenApplicationPackagesLiesOnACycleBesideTheNamedOnes() {
        SliceGraph graph = SliceGraph.of(PRODUCTION);
        SortedSet<String> unnamed = new TreeSet<>(graph.dependenciesOnACycle());
        unnamed.removeAll(DEPENDENCIES_ON_A_CYCLE);
        assertTrue(unnamed.isEmpty(), () -> "these dependencies between application packages close or extend a "
                + "cycle and are not named in DEPENDENCIES_ON_A_CYCLE (ADR-0152); remove the dependency, or move the "
                + "type to the package whose vocabulary it speaks:\n" + graph.evidence(unnamed));
    }

    @Test
    void everyNamedDependencyStillLiesOnACycle() {
        SortedSet<String> stale = new TreeSet<>(DEPENDENCIES_ON_A_CYCLE);
        stale.removeAll(SliceGraph.of(PRODUCTION).dependenciesOnACycle());
        assertTrue(stale.isEmpty(), () -> "these named dependencies no longer lie on a cycle between application "
                + "packages: remove them from DEPENDENCIES_ON_A_CYCLE, so the list only shrinks (ADR-0152): " + stale);
    }

    @Test
    void theCyclicComponentsAreTheNamedOnesInFull() {
        SliceGraph graph = SliceGraph.of(PRODUCTION);
        assertTrue(graph.slices().size() > 20, () -> "the slice graph is computed over the application's packages; "
                + "a graph of " + graph.slices().size() + " slices means the import read less than the layer: "
                + graph.slices());
        assertEquals(CYCLIC_COMPONENTS, graph.cyclicComponents(),
                "the strongly connected components of the application's package graph must be the named ones, each "
                        + "in full (ADR-0152)");
    }

    @Test
    void theStorePortsPackageTakesPartInNoCycle() {
        SliceGraph graph = SliceGraph.of(PRODUCTION);
        SortedSet<String> storeOnACycle = new TreeSet<>();
        for (String dependency : graph.dependenciesOnACycle()) {
            String[] ends = dependency.split(" -> ");
            if (ends[0].equals(STORE) || ends[1].equals(STORE)) {
                storeOnACycle.add(dependency);
            }
        }
        assertTrue(storeOnACycle.isEmpty(), () -> "com.morpheus.application.store takes part in no cycle: a store "
                + "port lives with the capability whose vocabulary it speaks, or does not speak it (ADR-0152):\n"
                + graph.evidence(storeOnACycle));
        assertTrue(DEPENDENCIES_ON_A_CYCLE.stream().noneMatch(name -> name.startsWith(STORE + " -> ")
                        || name.endsWith(" -> " + STORE)),
                "no named dependency on a cycle may start or end at the store ports package (ADR-0152)");
    }

    @Test
    void theComponentComputationFindsEveryCycleOfAKnownGraph() {
        SortedMap<String, SortedSet<String>> edges = new TreeMap<>();
        edges.put("a", new TreeSet<>(Set.of("b")));
        edges.put("b", new TreeSet<>(Set.of("c", "d")));
        edges.put("c", new TreeSet<>(Set.of("a")));
        edges.put("d", new TreeSet<>(Set.of("e")));
        edges.put("e", new TreeSet<>(Set.of("d", "f")));
        edges.put("f", new TreeSet<>());
        SliceGraph graph = new SliceGraph(edges, new TreeMap<>());

        assertEquals(Set.of(Set.of("a", "b", "c"), Set.of("d", "e")), graph.cyclicComponents());
        assertEquals(new TreeSet<>(Set.of("a -> b", "b -> c", "c -> a", "d -> e", "e -> d")),
                graph.dependenciesOnACycle());
    }

    /** The dependency graph between first-level application slices, with one pair of types per edge as evidence. */
    record SliceGraph(SortedMap<String, SortedSet<String>> edges, SortedMap<String, SortedSet<String>> typePairs) {

        static SliceGraph of(JavaClasses classes) {
            SortedMap<String, SortedSet<String>> edges = new TreeMap<>();
            SortedMap<String, SortedSet<String>> typePairs = new TreeMap<>();
            for (JavaClass origin : classes) {
                String source = sliceOf(origin);
                if (source == null) continue;
                edges.computeIfAbsent(source, ignored -> new TreeSet<>());
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetSlice = sliceOf(target);
                    if (targetSlice == null || targetSlice.equals(source)) continue;
                    edges.get(source).add(targetSlice);
                    edges.computeIfAbsent(targetSlice, ignored -> new TreeSet<>());
                    typePairs.computeIfAbsent(source + " -> " + targetSlice, ignored -> new TreeSet<>())
                            .add(topLevelName(origin) + " -> " + topLevelName(target));
                }
            }
            return new SliceGraph(edges, typePairs);
        }

        Set<String> slices() {
            return edges.keySet();
        }

        /** Tarjan's algorithm, iterated in name order so that the result does not depend on the import order. */
        Set<Set<String>> cyclicComponents() {
            Map<String, Integer> index = new HashMap<>();
            Map<String, Integer> lowLink = new HashMap<>();
            Deque<String> stack = new ArrayDeque<>();
            Set<String> onStack = new TreeSet<>();
            Set<Set<String>> components = new LinkedHashSet<>();
            int[] counter = {0};
            for (String slice : edges.keySet()) {
                if (!index.containsKey(slice)) {
                    connect(slice, index, lowLink, stack, onStack, components, counter);
                }
            }
            return Set.copyOf(components);
        }

        private void connect(String slice, Map<String, Integer> index, Map<String, Integer> lowLink,
                             Deque<String> stack, Set<String> onStack, Set<Set<String>> components, int[] counter) {
            index.put(slice, counter[0]);
            lowLink.put(slice, counter[0]);
            counter[0]++;
            stack.push(slice);
            onStack.add(slice);
            for (String target : edges.getOrDefault(slice, new TreeSet<>())) {
                if (!index.containsKey(target)) {
                    connect(target, index, lowLink, stack, onStack, components, counter);
                    lowLink.put(slice, Math.min(lowLink.get(slice), lowLink.get(target)));
                } else if (onStack.contains(target)) {
                    lowLink.put(slice, Math.min(lowLink.get(slice), index.get(target)));
                }
            }
            if (lowLink.get(slice).equals(index.get(slice))) {
                Set<String> component = new TreeSet<>();
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(slice));
                if (component.size() > 1) {
                    components.add(Set.copyOf(component));
                }
            }
        }

        SortedSet<String> dependenciesOnACycle() {
            SortedSet<String> onACycle = new TreeSet<>();
            for (Set<String> component : cyclicComponents()) {
                for (String source : component) {
                    for (String target : edges.get(source)) {
                        if (component.contains(target)) {
                            onACycle.add(source + " -> " + target);
                        }
                    }
                }
            }
            return onACycle;
        }

        String evidence(SortedSet<String> dependencies) {
            StringBuilder evidence = new StringBuilder();
            for (String dependency : dependencies) {
                evidence.append("  ").append(dependency).append(", for example ")
                        .append(typePairs.getOrDefault(dependency, new TreeSet<>()).stream().limit(3).toList())
                        .append('\n');
            }
            return evidence.toString();
        }

        private static String sliceOf(JavaClass javaClass) {
            String packageName = javaClass.getPackageName();
            if (!packageName.startsWith(APPLICATION + ".")) return null;
            String rest = packageName.substring(APPLICATION.length() + 1);
            int dot = rest.indexOf('.');
            return dot < 0 ? rest : rest.substring(0, dot);
        }

        private static String topLevelName(JavaClass javaClass) {
            String name = javaClass.getName();
            int nested = name.indexOf('$');
            return nested < 0 ? name : name.substring(0, nested);
        }
    }
}
