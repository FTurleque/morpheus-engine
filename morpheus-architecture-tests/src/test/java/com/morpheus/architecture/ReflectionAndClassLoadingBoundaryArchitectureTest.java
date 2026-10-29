package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.conditions.ArchConditions.never;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the product uses reflection, service loading and class loading, and that no framework wires it.
 *
 * <p>Outside the application, which {@link ApplicationTechnologyBoundaryArchitectureTest} confines under ADR-0150,
 * reflection and loading are reached only by the classes named below, each with its reason. A named class that no
 * longer reaches its family fails the build too, so each list is exactly the measured set and can only shrink.
 * Accessors of {@code java.lang.Class} ({@code getName}, {@code getSimpleName}, {@code isRecord},
 * {@code getClassLoader}) are not reflection here: what is refused is a dependency on {@code java.lang.reflect},
 * {@code java.lang.invoke}, a service or class loader, and a call to {@code Class.forName}. A lambda implementing
 * {@code InvocationHandler} and a type named by its qualified name are dependencies as much as an import is.</p>
 *
 * <p>The bytecode rules have a textual twin. A lambda whose only tie to {@code java.lang.reflect} is its target type,
 * {@code (java.lang.reflect.InvocationHandler) (proxy, method, args) -> ...}, leaves no dependency ArchUnit reports:
 * the type lives in an {@code invokedynamic} descriptor it does not read. The source names it, so the text sees it.</p>
 */
class ReflectionAndClassLoadingBoundaryArchitectureTest {
    private static final String PRODUCT = "com.morpheus";
    private static final String APPLICATION = PRODUCT + ".application";
    private static final String SQLITE_RUNTIME = PRODUCT + ".store.sqlite.runtime";
    private static final String PROVIDER_SDK = PRODUCT + ".sdk.provider";

    /** Class name to the reason it may reach {@code java.lang.reflect} or {@code java.lang.invoke}. */
    private static final Map<String, String> REFLECTION = Map.of(
            SQLITE_RUNTIME + ".SqliteConnectionScope",
            "a dynamic proxy of java.sql.Connection lets every store of an operation share one physical connection "
                    + "without closing it (ADR-0152)",
            SQLITE_RUNTIME + ".SqliteDatabaseLease",
            "a dynamic proxy of java.sql.Connection releases the database lease only once the driver close succeeded",
            SQLITE_RUNTIME + ".SqliteContentionMetrics",
            "reads the invocation handler of the two proxies above to find the contention counters they carry");

    /** Class name to the decision record that allows the service or class loading it performs. */
    private static final Map<String, String> LOADING = Map.of(
            PROVIDER_SDK + ".ProviderPluginActivator",
            "SEC-0129: a dedicated URLClassLoader over the SHA-256-verified copy, resolved by ServiceLoader",
            PROVIDER_SDK + ".ProviderPluginActivation",
            "SEC-0129: holds the dedicated URLClassLoader of an active plugin until it is closed");

    private static final List<String> INJECTION_FRAMEWORKS = List.of(
            "org.springframework..", "io.quarkus..", "io.micronaut..", "com.google.inject..",
            "jakarta.inject..", "javax.inject..", "jakarta.enterprise..", "javax.enterprise..");

    private static final Family REFLECTION_FAMILY = new Family("reflection",
            name -> name.startsWith("java.lang.reflect.") || name.startsWith("java.lang.invoke."), false);

    private static final Family LOADING_FAMILY = new Family("service or class loading",
            name -> name.equals("java.util.ServiceLoader") || name.startsWith("java.util.ServiceLoader$")
                    || name.equals("java.lang.ClassLoader") || name.equals("java.security.SecureClassLoader")
                    || name.equals("java.net.URLClassLoader"),
            true);

    static final Pattern REFLECTION_TEXT = Pattern.compile("\\bjava\\.lang\\.(?:reflect|invoke)\\b");
    static final Pattern LOADING_TEXT = Pattern.compile(
            "\\bServiceLoader\\b|\\b(?:URL|Secure)?ClassLoader\\b|\\bClass\\.forName\\b");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(PRODUCT);

    @Test
    void reflectionIsReachedOnlyByTheClassesNamedForIt() {
        classes().that().resideInAPackage(PRODUCT + "..")
                .and().resideOutsideOfPackage(APPLICATION + "..")
                .and(DescribedPredicate.not(namedIn(REFLECTION, REFLECTION_FAMILY)))
                .should(never(reach(REFLECTION_FAMILY)))
                .because("reflection is an implementation technique a class is named for, with its reason, in "
                        + "REFLECTION; the application is confined by ApplicationTechnologyBoundaryArchitectureTest")
                .check(classes);
    }

    @Test
    void serviceAndClassLoadingAreReachedOnlyByTheClassesNamedForThem() {
        classes().that().resideInAPackage(PRODUCT + "..")
                .and().resideOutsideOfPackage(APPLICATION + "..")
                .and(DescribedPredicate.not(namedIn(LOADING, LOADING_FAMILY)))
                .should(never(reach(LOADING_FAMILY)))
                .because("loading code is a decision: a class is named in LOADING with the ADR that allows it; the "
                        + "application is confined by ApplicationTechnologyBoundaryArchitectureTest")
                .check(classes);
    }

    @Test
    void everyNamedClassStillReachesItsFamily() {
        Map<String, Set<String>> named = new TreeMap<>();
        named.put(REFLECTION_FAMILY.name(), new TreeSet<>(REFLECTION.keySet()));
        named.put(LOADING_FAMILY.name(), new TreeSet<>(LOADING.keySet()));
        Map<String, Set<String>> reached = new TreeMap<>();
        reached.put(REFLECTION_FAMILY.name(), classesOutsideTheApplicationReaching(REFLECTION_FAMILY));
        reached.put(LOADING_FAMILY.name(), classesOutsideTheApplicationReaching(LOADING_FAMILY));
        assertEquals(named, reached, "each list names exactly the classes that reach its family: remove a class "
                + "that no longer reaches it, so each list only shrinks");
    }

    @Test
    void noProductClassDependsOnAnInjectionFramework() {
        noClasses().that().resideInAPackage(PRODUCT + "..")
                .should().dependOnClassesThat().resideInAnyPackage(INJECTION_FRAMEWORKS.toArray(String[]::new))
                .because("MORPHEUS is wired explicitly, in MorpheusMain and in the composition roots of ADR-0152, "
                        + "never by a framework or an injection annotation")
                .check(classes);
    }

    @Test
    void noSourceOutsideTheApplicationNamesReflectionOrLoadingUnlessItsClassIsNamed() throws IOException {
        Map<String, Set<String>> unnamed = new TreeMap<>();
        unnamed.put(REFLECTION_FAMILY.name(), new TreeSet<>());
        unnamed.put(LOADING_FAMILY.name(), new TreeSet<>());
        List<Path> sources = mainSourcesOutsideTheApplication();
        for (Path source : sources) {
            String code = RefusalPathTextArchitectureTest.withoutComments(Files.readString(source));
            String className = className(source);
            if (REFLECTION_TEXT.matcher(code).find() && !REFLECTION.containsKey(className)) {
                unnamed.get(REFLECTION_FAMILY.name()).add(className);
            }
            if (LOADING_TEXT.matcher(code).find() && !LOADING.containsKey(className)) {
                unnamed.get(LOADING_FAMILY.name()).add(className);
            }
        }
        assertTrue(sources.size() > 100, "the scan must read the product sources, read " + sources.size());
        assertEquals(Map.of(REFLECTION_FAMILY.name(), Set.of(), LOADING_FAMILY.name(), Set.of()), unnamed,
                "a source that names reflection or loading belongs to a class named for it in REFLECTION or LOADING");
    }

    @Test
    void theApplicationKeepsItsOwnConfinementOfReflectionAndLoading() throws IOException {
        String source = Files.readString(RepositoryRoot.locate().resolve("morpheus-architecture-tests/src/test/java/"
                + "com/morpheus/architecture/ApplicationTechnologyBoundaryArchitectureTest.java"));
        assertTrue(source.contains("new Family(\"reflection and class loading\"")
                        && source.contains("java.lang.reflect.") && source.contains("java.util.ServiceLoader")
                        && source.contains("\"forName\""),
                "this test leaves com.morpheus.application to ApplicationTechnologyBoundaryArchitectureTest, which "
                        + "must keep confining reflection and class loading there");
    }

    private static List<Path> mainSourcesOutsideTheApplication() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("morpheus-"))
                    .filter(path -> !path.getFileName().toString().equals("morpheus-application"))
                    .sorted().toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) continue;
                try (Stream<Path> tree = Files.walk(main)) {
                    tree.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().forEach(sources::add);
                }
            }
        }
        return sources;
    }

    private static String className(Path source) {
        Path main = source;
        while (!main.endsWith(Path.of("src", "main", "java"))) main = main.getParent();
        String relative = main.relativize(source).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
    }

    private Set<String> classesOutsideTheApplicationReaching(Family family) {
        Set<String> reaching = new TreeSet<>();
        for (JavaClass origin : classes) {
            String packageName = origin.getPackageName();
            boolean application = packageName.equals(APPLICATION) || packageName.startsWith(APPLICATION + ".");
            if (!application && family.reachedBy(origin)) {
                reaching.add(topLevelName(origin));
            }
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

    private static DescribedPredicate<JavaClass> namedIn(Map<String, String> names, Family family) {
        return DescribedPredicate.describe("named for " + family.name(),
                javaClass -> names.containsKey(topLevelName(javaClass)));
    }

    /** A nested, anonymous or lambda-holding class belongs to the top-level class that declares it. */
    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private record Family(String name, Predicate<String> types, boolean forName) {
        boolean reachedBy(JavaClass origin) {
            return !accessesOf(origin).isEmpty();
        }

        List<String> accessesOf(JavaClass origin) {
            Stream<String> typeAccesses = origin.getDirectDependenciesFromSelf().stream()
                    .filter(dependency -> types.test(dependency.getTargetClass().getName()))
                    .map(dependency -> dependency.getDescription());
            Stream<String> callAccesses = origin.getMethodCallsFromSelf().stream()
                    .filter(call -> forName && call.getTargetOwner().getName().equals("java.lang.Class")
                            && call.getName().equals("forName"))
                    .map(call -> call.getDescription());
            return Stream.concat(typeAccesses, callAccesses).distinct().sorted().toList();
        }
    }
}
