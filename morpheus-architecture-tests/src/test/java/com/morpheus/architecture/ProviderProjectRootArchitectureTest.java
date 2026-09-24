package com.morpheus.architecture;

import com.morpheus.application.read.ProviderProjectRoot;
import com.morpheus.domain.project.ProjectSpecification;
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
import java.util.TreeMap;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The project root a reader publishes is the workspace root it received, spelled by one point.
 *
 * <p>Publication compares that root with the one the project was registered under. The structured-markdown
 * reader once published the file it read instead, and every markdown-only workspace failed to publish on a
 * store collision. Two mechanisms hold the rule because neither sees all of it (ADR-0103): no rule on
 * bytecode can say which argument a constructor received, so the text holds the argument; the text sees
 * every provider module, including {@code morpheus-provider-reference}, which the ArchUnit classpath does
 * not; and the ArchUnit rule sees a construction the text would miss because it is not spelled
 * {@code new ProjectSpecification(}.</p>
 */
class ProviderProjectRootArchitectureTest {
    private static final String CONSTRUCTION = "new ProjectSpecification(";
    private static final String SINGLE_POINT = "ProviderProjectRoot.locator(";
    private static final List<String> READER_MODULES = List.of(
            "morpheus-provider-markdown",
            "morpheus-provider-openspec",
            "morpheus-provider-reference",
            "morpheus-provider-synthetic");

    @Test
    void everyProviderPublishesItsProjectRootThroughTheSinglePoint() throws IOException {
        Map<String, List<String>> constructionsByModule = new TreeMap<>();
        List<String> violations = new ArrayList<>();
        Path root = repoRoot();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("morpheus-provider-"))
                    .sorted()
                    .toList()) {
                Path sources = module.resolve("src/main/java");
                if (!Files.isDirectory(sources)) {
                    continue;
                }
                for (Path source : javaSources(sources)) {
                    String text = Files.readString(source);
                    for (String rootArgument : thirdArguments(text)) {
                        String site = root.relativize(source).toString().replace('\\', '/');
                        constructionsByModule
                                .computeIfAbsent(module.getFileName().toString(), ignored -> new ArrayList<>())
                                .add(site);
                        if (!rootArgument.startsWith(SINGLE_POINT)) {
                            violations.add(site + " publishes project root '" + rootArgument + "'");
                        }
                    }
                }
            }
        }

        assertEquals(List.of(), violations,
                "a provider publishes as project root the workspace root it received, written inline as "
                        + SINGLE_POINT + "...) in the ProjectSpecification it constructs; any other root "
                        + "makes its own publication collide with the registered project");
        for (String module : READER_MODULES) {
            assertTrue(constructionsByModule.containsKey(module),
                    () -> module + " constructs no ProjectSpecification that this scan can see; "
                            + "a scan that finds nothing proves nothing. Found: " + constructionsByModule);
        }
    }

    @Test
    void everyClassThatConstructsAProjectSpecificationObtainsItsRootFromTheSinglePoint() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.morpheus");

        classes()
                .that(constructProjectSpecification())
                .should(callProviderProjectRootLocator())
                .because("a reader publishes the workspace root it received as project root, and "
                        + "ProviderProjectRoot is the only place that root is spelled; a second spelling "
                        + "is how the structured-markdown reader came to publish a file")
                .check(imported);
    }

    private static DescribedPredicate<JavaClass> constructProjectSpecification() {
        return DescribedPredicate.describe("construct a ProjectSpecification", javaClass ->
                javaClass.getConstructorCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner().isEquivalentTo(ProjectSpecification.class)));
    }

    private static ArchCondition<JavaClass> callProviderProjectRootLocator() {
        return new ArchCondition<>("call ProviderProjectRoot.locator") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean calls = javaClass.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner().isEquivalentTo(ProviderProjectRoot.class)
                                && call.getName().equals("locator"));
                events.add(new SimpleConditionEvent(javaClass, calls,
                        javaClass.getName() + (calls ? " calls" : " does not call")
                                + " ProviderProjectRoot.locator"));
            }
        };
    }

    private static List<String> thirdArguments(String text) {
        List<String> arguments = new ArrayList<>();
        int from = 0;
        while (true) {
            int start = text.indexOf(CONSTRUCTION, from);
            if (start < 0) {
                return arguments;
            }
            List<String> split = topLevelArguments(text, start + CONSTRUCTION.length());
            arguments.add(split.size() == 3 ? split.get(2) : "<" + split.size() + " arguments>");
            from = start + CONSTRUCTION.length();
        }
    }

    private static List<String> topLevelArguments(String text, int afterOpeningParenthesis) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int index = afterOpeningParenthesis; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                if (depth == 0) {
                    arguments.add(current.toString().strip());
                    return arguments;
                }
                depth--;
            } else if (character == ',' && depth == 0) {
                arguments.add(current.toString().strip());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        throw new IllegalStateException("unterminated " + CONSTRUCTION + " expression");
    }

    private static List<Path> javaSources(Path sources) throws IOException {
        try (Stream<Path> tree = Files.walk(sources)) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("docs/adr"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
