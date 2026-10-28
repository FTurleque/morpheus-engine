package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.conditions.ArchConditions.never;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which technology the application layer may reach, and through which classes (ADR-0150).
 *
 * <p>A family the application does not use is refused in every package. Outbound network and threads are among them:
 * the transport of update discovery is an adapter of {@code morpheus-cli} behind a port of {@code application.product}.
 * A family it uses is reachable only from the classes ADR-0150 names for it, and each list is exactly the set of
 * classes that reach the family, so a list can shrink and cannot grow in silence. {@code Path}, {@code LinkOption}, {@code IOException} and in-memory streams are
 * vocabulary, not access. The textual bans of {@code application.reasoning} and {@code application.policy} are
 * separate and stricter: they also refuse a mention.</p>
 */
class ApplicationTechnologyBoundaryArchitectureTest {
    private static final String APPLICATION = "com.morpheus.application";

    private static final String HARDENER = APPLICATION + ".security.LocalWritePermissionHardener";
    private static final String ROTATING_LOG = APPLICATION + ".operability.RotatingOperationalLogFile";
    private static final String PEER_SETTINGS = APPLICATION + ".security.PeerLaunchSettingsResolver";

    private static final List<Family> UNUSED_FAMILIES = List.of(
            Family.ofTypes("SQL", name -> name.startsWith("java.sql.") || name.startsWith("javax.sql.")),
            Family.ofTypes("JSON library", name -> name.startsWith("tools.jackson.") || name.startsWith("com.fasterxml.")),
            Family.ofTypes("process execution", name -> Set.of("java.lang.ProcessBuilder", "java.lang.Process",
                    "java.lang.ProcessHandle", "java.lang.Runtime").contains(name)),
            Family.ofTypes("outbound network", name -> name.startsWith("java.net.http.") || name.startsWith("javax.net.")
                    || Set.of("java.net.URL", "java.net.URLConnection", "java.net.HttpURLConnection",
                    "java.net.Socket", "java.net.ServerSocket", "java.net.DatagramSocket",
                    "java.net.InetAddress").contains(name)),
            new Family("threads", name -> Set.of("java.util.concurrent.Executors", "java.util.concurrent.ExecutorService",
                    "java.util.concurrent.ScheduledExecutorService", "java.util.concurrent.ThreadPoolExecutor",
                    "java.util.concurrent.ForkJoinPool", "java.util.concurrent.CompletableFuture",
                    "java.lang.Thread$Builder", "java.util.Timer").contains(name),
                    (owner, method, arity) -> owner.equals("java.lang.Thread")
                            && Set.of("<init>", "ofVirtual", "ofPlatform", "startVirtualThread").contains(method),
                    Set.of()));

    private static final Map<Family, Set<String>> CONFINED_FAMILIES = Map.of(
            Family.ofTypes("file access", name -> Set.of("java.nio.file.Files", "java.nio.file.FileSystems",
                            "java.nio.file.FileSystem", "java.nio.file.WatchService", "java.nio.file.WatchKey",
                            "java.nio.file.DirectoryStream", "java.io.File", "java.io.FileInputStream",
                            "java.io.FileOutputStream", "java.io.FileReader", "java.io.FileWriter",
                            "java.io.RandomAccessFile").contains(name)
                            || name.startsWith("java.nio.file.attribute.") || name.startsWith("java.nio.file.spi.")
                            || name.startsWith("java.nio.channels.")),
                    Set.of(HARDENER, ROTATING_LOG,
                            APPLICATION + ".files.SafeWorkspaceFileResolver",
                            APPLICATION + ".sync.LocalSourceInventoryScanner",
                            APPLICATION + ".sync.ScanWorkspaceBoundary",
                            APPLICATION + ".sync.SourceFingerprint",
                            APPLICATION + ".security.ExternalJarIntegrity",
                            PEER_SETTINGS,
                            APPLICATION + ".sync.LocalSourceWatcher",
                            APPLICATION + ".discovery.ProjectDiscoveryService",
                            APPLICATION + ".discovery.WorkspaceRootResolver"),
            Family.ofCalls("process environment", (owner, method, arity) -> owner.equals("java.lang.System")
                            && Set.of("getenv", "getProperty", "getProperties", "setProperty").contains(method)),
                    Set.of(HARDENER, PEER_SETTINGS,
                            APPLICATION + ".operability.LocalOperationalRuntime",
                            APPLICATION + ".operability.SensitiveValueRedactor",
                            APPLICATION + ".product.ProductMetadata"),
            new Family("standard streams", name -> false,
                    (owner, method, arity) -> owner.equals("java.lang.System")
                            && Set.of("console", "setOut", "setErr", "setIn").contains(method),
                    Set.of("out", "err", "in")),
                    Set.of(ROTATING_LOG, APPLICATION + ".operability.OperationalRuntime"),
            Family.ofCalls("wall clock", (owner, method, arity) ->
                            (owner.startsWith("java.time.") && method.equals("now") && arity == 0)
                                    || (owner.equals("java.time.Clock") && method.startsWith("system"))
                                    || (owner.equals("java.lang.System") && method.equals("currentTimeMillis"))),
                    Set.of(APPLICATION + ".operability.LocalOperabilityService",
                            APPLICATION + ".operability.OperationalRecorder",
                            APPLICATION + ".lifecycle.mutation.ControlledChangeLifecycleMutationService",
                            APPLICATION + ".policy.PolicyPackService",
                            APPLICATION + ".portfolio.PortfolioRegistryService",
                            APPLICATION + ".query.saved.SavedViewService"),
            new Family("reflection and class loading", name -> name.startsWith("java.lang.reflect.")
                    || name.startsWith("java.lang.invoke.")
                    || Set.of("java.util.ServiceLoader", "java.lang.ClassLoader", "java.net.URLClassLoader").contains(name),
                    (owner, method, arity) -> owner.equals("java.lang.Class") && method.equals("forName"),
                    Set.of()),
                    Set.of(APPLICATION + ".query.compact.CanonicalJsonSerializer",
                            APPLICATION + ".reasoning.ReasoningAdapterRegistry"));

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(APPLICATION);

    @Test
    void aFamilyTheApplicationDoesNotUseIsRefusedInEveryPackage() {
        ArchRule rule = CompositeArchRule.of(UNUSED_FAMILIES.stream()
                .map(family -> (ArchRule) classes().that().resideInAPackage(APPLICATION + "..")
                        .should(never(reach(family)))
                        .because("ADR-0150, decision 1: the application uses no " + family.name()))
                .toList());
        rule.check(classes);
    }

    @Test
    void aConfinedFamilyIsReachedOnlyByTheClassesNamedForIt() {
        ArchRule rule = CompositeArchRule.of(CONFINED_FAMILIES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> left.name().compareTo(right.name())))
                .map(entry -> (ArchRule) classes().that().resideInAPackage(APPLICATION + "..")
                        .and(DescribedPredicate.not(namedFor(entry.getValue(), entry.getKey())))
                        .should(never(reach(entry.getKey())))
                        .because("ADR-0150, decision 2: " + entry.getKey().name() + " is reached only by the "
                                + "classes it names; adding one takes a consolidated ADR that replaces it"))
                .toList());
        rule.check(classes);
    }

    @Test
    void everyNamedClassStillReachesItsFamily() {
        Map<String, Set<String>> named = new TreeMap<>();
        Map<String, Set<String>> reached = new TreeMap<>();
        CONFINED_FAMILIES.forEach((family, names) -> {
            named.put(family.name(), new TreeSet<>(names));
            reached.put(family.name(), classesReaching(family));
        });
        assertEquals(named, reached, "ADR-0150 names exactly the classes that reach each family: remove a class "
                + "that no longer reaches it, so each list only shrinks");
    }

    @Test
    void aPathUsedAsAValueIsNotAnAccess() {
        Set<String> pathOnly = new TreeSet<>();
        for (JavaClass origin : classes) {
            boolean usesPath = origin.getDirectDependenciesFromSelf().stream()
                    .anyMatch(dependency -> dependency.getTargetClass().getName().equals("java.nio.file.Path"));
            boolean reachesAFamily = Stream.concat(UNUSED_FAMILIES.stream(), CONFINED_FAMILIES.keySet().stream())
                    .anyMatch(family -> family.reachedBy(origin));
            if (usesPath && !reachesAFamily) pathOnly.add(topLevelName(origin));
        }
        assertTrue(pathOnly.contains(APPLICATION + ".read.ProviderReadRequest")
                        && pathOnly.contains(APPLICATION + ".provider.SpecificationProvider"),
                "a port that carries a Path and opens nothing must not count as file access: " + pathOnly);
    }

    private Set<String> classesReaching(Family family) {
        Set<String> reaching = new TreeSet<>();
        for (JavaClass origin : classes) {
            if (family.reachedBy(origin)) reaching.add(topLevelName(origin));
        }
        return reaching;
    }

    private static ArchCondition<JavaClass> reach(Family family) {
        return new ArchCondition<>("reach " + family.name()) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                List<String> accesses = family.accessesOf(item);
                if (accesses.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(item, item.getName() + " does not reach " + family.name()));
                }
                accesses.forEach(access -> events.add(SimpleConditionEvent.satisfied(item,
                        item.getName() + " reaches " + family.name() + " through " + access)));
            }
        };
    }

    private static DescribedPredicate<JavaClass> namedFor(Set<String> names, Family family) {
        return DescribedPredicate.describe("named by ADR-0150 for " + family.name(),
                javaClass -> names.contains(topLevelName(javaClass)));
    }

    /** A nested, anonymous or lambda-holding class belongs to the top-level class that declares it. */
    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    @FunctionalInterface
    private interface CallMatcher {
        boolean matches(String owner, String method, int arity);
    }

    private record Family(String name, Predicate<String> types, CallMatcher calls, Set<String> systemFields) {
        static Family ofTypes(String name, Predicate<String> types) {
            return new Family(name, types, (owner, method, arity) -> false, Set.of());
        }

        static Family ofCalls(String name, CallMatcher calls) {
            return new Family(name, typeName -> false, calls, Set.of());
        }

        boolean reachedBy(JavaClass origin) {
            return !accessesOf(origin).isEmpty();
        }

        List<String> accessesOf(JavaClass origin) {
            Stream<String> typeAccesses = origin.getDirectDependenciesFromSelf().stream()
                    .filter(dependency -> types.test(dependency.getTargetClass().getName()))
                    .map(dependency -> dependency.getDescription());
            Stream<String> callAccesses = origin.getCodeUnitCallsFromSelf().stream()
                    .filter(call -> calls.matches(call.getTargetOwner().getName(), call.getName(),
                            call.getTarget().getRawParameterTypes().size()))
                    .map(call -> call.getDescription());
            Stream<String> fieldAccesses = origin.getFieldAccessesFromSelf().stream()
                    .filter(access -> access.getTargetOwner().getName().equals("java.lang.System")
                            && systemFields.contains(access.getName()))
                    .map(access -> access.getDescription());
            return Stream.of(typeAccesses, callAccesses, fieldAccesses).flatMap(stream -> stream).distinct().sorted()
                    .toList();
        }
    }
}
