package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every ArchUnit rule of this module is only as wide as the classes it imports. {@code failOnEmptyShould} refuses a
 * rule whose subject is empty, but a forbidden package that matches nothing passes in silence, and a reactor module
 * missing from this module's test classpath escapes every rule without any of them noticing.
 *
 * <p>This test holds the imported production set to the reactor: each package that carries Java sources under
 * {@code src/main/java} in a module of the root POM must contribute at least one imported class, unless its module
 * is named in {@link #NOT_ON_THE_CLASSPATH} with the reason it is absent. The module list is read from the root POM,
 * not copied here, so declaring a module makes it mandatory in the same gesture.</p>
 */
class ImportedClasspathCompletenessTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    /** Reactor modules deliberately absent from this module's test classpath, each with its reason. */
    private static final Map<String, String> NOT_ON_THE_CLASSPATH = Map.of(
            "morpheus-provider-testkit",
            "test support consumed by plugin tests only; a test-scoped dependency that no class here references "
                    + "is reported unused by dependency:analyze, which fails the build");

    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");

    @Test
    void theImportedProductionSetIsNotEmpty() {
        int count = 0;
        for (JavaClass ignored : PRODUCTION) {
            count++;
        }
        assertTrue(count > 0, "the architecture tests import no production class at all");
    }

    @Test
    void everySourcePackageOfEveryReactorModuleIsImported() throws IOException {
        Set<String> imported = importedPackages();
        Map<String, Set<String>> missing = new TreeMap<>();
        for (Map.Entry<String, Set<String>> module : sourcePackagesByModule().entrySet()) {
            if (NOT_ON_THE_CLASSPATH.containsKey(module.getKey())) {
                continue;
            }
            Set<String> absent = new TreeSet<>(module.getValue());
            absent.removeAll(imported);
            if (!absent.isEmpty()) {
                missing.put(module.getKey(), absent);
            }
        }
        assertEquals(Map.of(), missing,
                "these source packages contribute no class to the import, so every ArchUnit rule of this module "
                        + "is blind to them; add the module to morpheus-architecture-tests/pom.xml, or name it in "
                        + "NOT_ON_THE_CLASSPATH with the reason it is absent");
    }

    @Test
    void everyDeclaredAbsenceIsAReactorModuleThatIsStillAbsent() throws IOException {
        Map<String, Set<String>> sources = sourcePackagesByModule();
        Set<String> imported = importedPackages();
        for (String module : NOT_ON_THE_CLASSPATH.keySet()) {
            assertTrue(sources.containsKey(module),
                    module + " is declared absent but is not a reactor module with sources: remove the entry");
            Set<String> present = new TreeSet<>(sources.get(module));
            present.retainAll(imported);
            assertTrue(present.isEmpty(),
                    module + " is declared absent but its packages " + present + " are imported: remove the entry");
        }
    }

    @Test
    void theReactorDeclaresModulesWithSources() throws IOException {
        assertFalse(sourcePackagesByModule().isEmpty(), "no reactor module with sources was read from the root POM");
    }

    static Set<String> importedPackages() {
        Set<String> packages = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            packages.add(javaClass.getPackageName());
        }
        return packages;
    }

    private static Map<String, Set<String>> sourcePackagesByModule() throws IOException {
        Path root = repoRoot();
        Matcher matcher = MODULE.matcher(Files.readString(root.resolve("pom.xml")));
        Map<String, Set<String>> modules = new TreeMap<>();
        while (matcher.find()) {
            String module = matcher.group(1).trim();
            Path sources = root.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sources)) {
                continue;
            }
            Set<String> packages = new TreeSet<>();
            try (Stream<Path> files = Files.walk(sources)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                        .forEach(path -> packages.add(
                                sources.relativize(path.getParent()).toString().replace('\\', '.').replace('/', '.')));
            }
            if (!packages.isEmpty()) {
                modules.put(module, packages);
            }
        }
        return modules;
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
