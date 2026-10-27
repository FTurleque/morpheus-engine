package com.morpheus.architecture;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.provider.openspec.OpenSpecSpecificationProvider;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A transport reads a project through the provider port, and leaves to the provider the directories it reads.
 *
 * <p>The first rule is a dependency: a class of {@code api}, {@code cli} or {@code mcp} reaches a provider module only
 * through a type that implements {@link SpecificationContentReader} or {@link SpecificationProvider}; an aggregator
 * outside the port, or a provider's internal reader, is refused by name. The second is a mention: the directories a
 * provider reads are its constant ({@link OpenSpecSpecificationProvider#SOURCE_DIRECTORIES}), and their names do not
 * appear as a string literal in the main sources of a transport. A string literal leaves nothing in the bytecode that
 * a dependency rule could see, so the second rule reads the text, without its comments.</p>
 */
class TransportsReadAProjectThroughTheProviderPortTest {
    private static final List<String> TRANSPORT_MODULES = List.of("morpheus-api", "morpheus-cli", "morpheus-mcp");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api", "com.morpheus.cli", "com.morpheus.mcp", "com.morpheus.provider");

    @Test
    void aTransportReachesAProviderOnlyThroughThePort() {
        noClasses()
                .that().resideInAnyPackage("com.morpheus.api..", "com.morpheus.cli..", "com.morpheus.mcp..")
                .should().dependOnClassesThat(providerTypesOutsideThePort())
                .because("the content of a project reaches a transport through SpecificationContentReader, so that "
                        + "every provider is read the same way and a publication decides once what it stores")
                .check(PRODUCTION);
    }

    @Test
    void aTransportNamesNoDirectoryAProviderReads() {
        Map<String, String> offenders = new TreeMap<>();
        for (Path directory : OpenSpecSpecificationProvider.SOURCE_DIRECTORIES) {
            String literal = "\"" + directory + "\"";
            for (String module : TRANSPORT_MODULES) {
                for (Path source : mainSources(module)) {
                    if (codeOf(source).contains(literal)) {
                        offenders.put(RepositoryRoot.locate().relativize(source).toString(), literal);
                    }
                }
            }
        }
        assertFalse(OpenSpecSpecificationProvider.SOURCE_DIRECTORIES.isEmpty(), "the provider states no directory");
        assertEquals(Map.of(), offenders, "a transport names a directory a provider reads: pass the provider's "
                + "SOURCE_DIRECTORIES with its reader instead");
    }

    private static DescribedPredicate<JavaClass> providerTypesOutsideThePort() {
        return DescribedPredicate.describe(
                "are types of com.morpheus.provider.. that implement neither SpecificationContentReader nor "
                        + "SpecificationProvider",
                target -> {
                    JavaClass type = target.getBaseComponentType();
                    return type.getPackageName().startsWith("com.morpheus.provider")
                            && !type.isAssignableTo(SpecificationContentReader.class)
                            && !type.isAssignableTo(SpecificationProvider.class);
                });
    }

    private static List<Path> mainSources(String module) {
        Path root = RepositoryRoot.locate().resolve(module).resolve("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String codeOf(Path source) {
        try {
            return COMMENT.matcher(Files.readString(source)).replaceAll("");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
