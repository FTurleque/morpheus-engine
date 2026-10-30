package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first-level packages of {@code morpheus-application} form no cycle, and the rule has no exception (ADR-0156).
 *
 * <p>A package here is a first-level slice, {@code com.morpheus.application.(*)..}: {@code query.dsl} and
 * {@code query.saved} are one slice, and a dependency between them is out of this rule's sight. The graph is read on
 * the bytecode: a type reached through a chained call counts, a constant that {@code javac} inlined does not.</p>
 */
class ApplicationPackageCyclesArchitectureTest {

    private static final String APPLICATION = "com.morpheus.application";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(APPLICATION);

    private static ArchRule firstLevelPackagesFreeOfCycles(String root) {
        return slices().matching(root + ".(*)..")
                .should().beFreeOfCycles()
                .because("a package of the application depends on the packages whose vocabulary it uses, never back: "
                        + "a type that two packages need lives in the lower one, or in the domain when it decides on "
                        + "a single domain value (ADR-0156)");
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
                        && message.contains("Slice beta") && message.contains("ADR-0156"),
                message);
    }
}
