package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What another module may use of the HTTP adapter: six types and their nested types, wherever in the adapter they
 * reside.
 *
 * <p>Most classes of {@code morpheus-api} are package-private, so the compiler keeps other modules away from them. A
 * class that moves to a sub-package of the adapter has to become {@code public} for its neighbours, and the compiler
 * then lets every module in. These two checks hold the boundary instead: the bytecode rule sees a dependency however it
 * is spelled, the scan of the sources sees a compile-time constant that {@code javac} inlines and leaves no dependency
 * behind ({@code .claude/rules/architecture.md}).</p>
 *
 * <p>The six are a list and not a package because they sit at two levels of the adapter: the two servers and the
 * remote identity file use the rest of the adapter, while the other three are values that the adapter's services and
 * remote model use. A package holding all six would be a cycle with every other package of the adapter. The list is
 * the public surface of the module, measured on 24 October 2026; a type is added to it by a written decision.</p>
 */
class ApiEntryTypesArchitectureTest {

    private static final String API = "com.morpheus.api";
    private static final String API_MODULE = "morpheus-api";

    /** The types another module uses. */
    static final Set<String> ENTRY_TYPES = Set.of(
            "AllowedWorkspaceRoots",
            "LoopbackHostPolicy",
            "MorpheusHttpServer",
            "MorpheusRemoteHttpServer",
            "MorpheusRemoteIdentityFile",
            "MorpheusRemoteRole");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    private static final Pattern API_TYPE_NAME = Pattern.compile("\\bcom\\.morpheus\\.api(?:\\.[a-z][a-z0-9]*)*\\.([A-Z][A-Za-z0-9_]*|\\*)");

    @Test
    void noClassOutsideTheAdapterDependsOnATypeOfTheAdapterBesideTheEntryTypes() {
        Set<String> entryTypesReached = new TreeSet<>();
        for (JavaClass origin : PRODUCTION) {
            if (isInTheAdapter(origin)) {
                continue;
            }
            origin.getDirectDependenciesFromSelf().stream()
                    .map(dependency -> dependency.getTargetClass())
                    .filter(ApiEntryTypesArchitectureTest::isInTheAdapter)
                    .forEach(target -> entryTypesReached.add(topLevelSimpleName(target)));
        }
        assertTrue(entryTypesReached.containsAll(ENTRY_TYPES), () -> "the rule must see other modules reach each entry "
                + "type, or the list is wider than what is used: reached " + entryTypesReached);

        noClasses()
                .that(DescribedPredicate.describe("reside outside of " + API + "..", javaClass -> !isInTheAdapter(javaClass)))
                .should().dependOnClassesThat(DescribedPredicate.describe(
                        "are types of " + API + ".. other than " + new TreeSet<>(ENTRY_TYPES),
                        target -> isInTheAdapter(target) && !ENTRY_TYPES.contains(topLevelSimpleName(target))))
                .because("another module reaches the HTTP adapter through its entry types only; the rest of the "
                        + "adapter is public for its own packages, not for its neighbours")
                .check(PRODUCTION);
    }

    @Test
    void noMainSourceOutsideTheAdapterNamesATypeOfTheAdapterBesideTheEntryTypes() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> offenders = new ArrayList<>();
        Set<String> entryTypesNamed = new TreeSet<>();
        int scanned = 0;
        for (Path sources : mainSourceRootsOutsideTheAdapter(root)) {
            for (Path source : javaSources(sources)) {
                scanned++;
                Matcher named = API_TYPE_NAME.matcher(Files.readString(source));
                while (named.find()) {
                    String name = named.group(1);
                    if (ENTRY_TYPES.contains(name)) {
                        entryTypesNamed.add(name);
                    } else {
                        offenders.add(root.relativize(source).toString().replace('\\', '/') + " names " + name);
                    }
                }
            }
        }

        assertTrue(scanned > 100, "too few main sources scanned outside " + API_MODULE + ": " + scanned);
        assertEquals(new TreeSet<>(ENTRY_TYPES), entryTypesNamed,
                "the scan must see each entry type named, or the pattern it reads with is vacant");
        assertEquals(List.of(), offenders, "a main source outside " + API_MODULE + " names a type of the HTTP "
                + "adapter that is not an entry type: a compile-time constant read this way leaves no dependency "
                + "for the bytecode rule. Use an entry type, or add the type to ENTRY_TYPES by a written decision");
    }

    private static boolean isInTheAdapter(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.equals(API) || packageName.startsWith(API + ".");
    }

    private static String topLevelSimpleName(JavaClass javaClass) {
        String name = javaClass.getName().substring(javaClass.getPackageName().length() + 1);
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private static List<Path> mainSourceRootsOutsideTheAdapter(Path root) throws IOException {
        try (Stream<Path> modules = Files.list(root)) {
            return modules
                    .filter(Files::isDirectory)
                    .filter(module -> !module.getFileName().toString().equals(API_MODULE))
                    .map(module -> module.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> javaSources(Path sources) throws IOException {
        try (Stream<Path> tree = Files.walk(sources)) {
            return tree.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
