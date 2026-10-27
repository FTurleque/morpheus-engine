package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A public payload map is built by one owner, {@code com.morpheus.application.foundation.PayloadMap}.
 *
 * <p>Intent: no type of the product other than the owner declares a method that turns an argument array into a map.
 * A private copy depends on nothing but the JDK, so no dependency rule can see it; the rule is on the signature of
 * the members, whatever their name. The textual twin reads every {@code src/main/java} of the repository, because
 * {@code morpheus-provider-testkit} is not on this module's classpath.</p>
 *
 * <p>The owner sits in a leaf package: it depends on no other MORPHEUS package, so it cannot join a package cycle of
 * the application.</p>
 */
class PayloadMapConstructionArchitectureTest {

    static final String OWNER = "com.morpheus.application.foundation.PayloadMap";

    static final String FOUNDATION = "com.morpheus.application.foundation";

    private static final String OWNER_SOURCE = "morpheus-application/src/main/java/com/morpheus/application/foundation/"
            + "PayloadMap.java";

    /** A method declaration whose only parameter is an argument array and whose return type is a map. */
    static final Pattern ARGUMENT_ARRAY_TO_MAP = Pattern.compile(
            "\\bMap\\s*<[^;{()]*>\\s+\\w+\\s*\\(\\s*(?:final\\s+)?Object\\s*(?:\\.\\.\\.|\\[\\s*])\\s*\\w+\\s*\\)");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    private static final DescribedPredicate<JavaMethod> TURNS_AN_ARGUMENT_ARRAY_INTO_A_MAP =
            DescribedPredicate.describe("take only an argument array and return a map",
                    method -> method.getRawParameterTypes().size() == 1
                            && method.getRawParameterTypes().get(0).isEquivalentTo(Object[].class)
                            && method.getRawReturnType().isAssignableTo(Map.class));

    @Test
    void noTypeOtherThanTheOwnerDeclaresAMethodThatTurnsAnArgumentArrayIntoAMap() {
        methods().that(TURNS_AN_ARGUMENT_ARRAY_INTO_A_MAP)
                .should().beDeclaredIn(OWNER)
                .because("a payload map is built by PayloadMap, which refuses an odd count, a key that is not a "
                        + "String and a key given twice by name; a private copy casts the key and keeps the last "
                        + "value of a repeated key in silence")
                .check(PRODUCTION);
    }

    @Test
    void noMainSourceOtherThanTheOwnerDeclaresAMethodThatTurnsAnArgumentArrayIntoAMap() throws IOException {
        Path root = RepositoryRoot.locate();
        Set<String> offenders = new TreeSet<>();
        int scanned = 0;
        for (Path source : mainSources(root)) {
            scanned++;
            String relative = root.relativize(source).toString().replace('\\', '/');
            if (!relative.equals(OWNER_SOURCE) && ARGUMENT_ARRAY_TO_MAP.matcher(Files.readString(source)).find()) {
                offenders.add(relative);
            }
        }
        assertTrue(scanned > 500, "too few main sources scanned (" + scanned + "): the scan would pass on nothing");
        assertTrue(ARGUMENT_ARRAY_TO_MAP.matcher(Files.readString(root.resolve(OWNER_SOURCE))).find(),
                "the owner no longer matches the pattern the twin looks for: " + OWNER_SOURCE);
        assertEquals(Set.of(), offenders,
                "build a payload map with PayloadMap, not with a method of your own that takes Object...");
    }

    @Test
    void theTextualTwinRecognisesARenamedCopyAndNoOtherSignature() {
        assertTrue(ARGUMENT_ARRAY_TO_MAP.matcher(
                "private static Map<String, Object> props(Object... entries) {").find());
        assertTrue(ARGUMENT_ARRAY_TO_MAP.matcher(
                "static Map<String, ?> fields(final Object[] pairs) {").find());
        assertFalse(ARGUMENT_ARRAY_TO_MAP.matcher(
                "private static Map<String, Object> with(Map<String, Object> base, Object... overrides) {").find());
        assertFalse(ARGUMENT_ARRAY_TO_MAP.matcher("static List<String> required(String... names) {").find());
    }

    @Test
    void theOwnerOfThePayloadMapDependsOnNoOtherMorpheusPackage() {
        noClasses().that().resideInAPackage(FOUNDATION + "..")
                .should().dependOnClassesThat(resideInAPackage("com.morpheus..")
                        .and(DescribedPredicate.not(resideInAPackage(FOUNDATION + ".."))))
                .because("the foundation package is a leaf of the application: pure types every module may use, "
                        + "that no package cycle of the application can reach")
                .check(PRODUCTION);
    }

    private static List<Path> mainSources(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root, 16)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        return relative.contains("/src/main/java/") && !relative.startsWith(".");
                    })
                    .sorted()
                    .toList();
        }
    }

    /** The working directory of this module's tests is the module; the repository root is its parent. */
}
