package com.morpheus.architecture;

import com.morpheus.architecture.CliOptionParsingRefusesUnknownOptionsTest.Unit;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every CLI sub-command reads its command token and writes its error line through one owner, beside
 * {@code GlobalArgs} in {@code com.morpheus.cli.command}.
 *
 * <p>The error line {@code MORPHEUS error [<code>]: <message>} is what a caller's script reads, and each sub-command
 * carried its own writer of it and its own reading of the command token. The two conversions are recognised by their
 * <b>signature</b>, not their name, so a pasted copy under another name is refused too:</p>
 * <ul>
 *   <li>the raw argument vector into the command: one {@code String[]} parameter, a {@link String} or a {@link List}
 *   returned;</li>
 *   <li>a code and a message into an error line: a {@link PrintStream} and a {@code CliExitCode} among the
 *   parameters.</li>
 * </ul>
 *
 * <p>The text a failure contributes to the line is formed by {@code FailureText.of}, whose ownership is held for every
 * module by {@code FailureTextOwnershipArchitectureTest}.</p>
 *
 * <p>A method with one of these signatures and another purpose is named in {@link #OTHER_PURPOSES} with its reason, and
 * a name there that no longer matches fails the rule. Compiler-generated methods (lambda bodies) are not declarations
 * of a type and are skipped.</p>
 *
 * <p>The format itself is a string literal, a compile-time constant that no bytecode rule attributes to the class that
 * spelled it, so it is held textually: the literal appears in one source file of {@code morpheus-cli/src/main/java},
 * comments blanked.</p>
 */
class CliArgumentBaseArchitectureTest {

    private static final String CLI = "com.morpheus.cli";
    private static final String ERROR_LINE_OWNER = CLI + ".command.CliErrorLine";
    private static final String COMMAND_TOKEN_OWNER = CLI + ".command.GlobalArgs";
    private static final String EXIT_CODE = CLI + ".command.CliExitCode";
    private static final String ERROR_LINE_LITERAL = "\"MORPHEUS error [";
    private static final String ERROR_LINE_FILE = "CliErrorLine.java";
    private static final Pattern AN_ERROR_PREFIX = Pattern.compile("\"MORPHEUS [^\"\\n]*\\berror\\b");

    /** Methods with one of the two signatures and another purpose, by full name, with that purpose. */
    static final Map<String, String> OTHER_PURPOSES = Map.of(
            CLI + ".CliCommandPaths.commandWords([Ljava.lang.String;)",
            "the command path checked against the declared paths: the words before the first option, the attached"
                    + " layout spelling of the server skipped, not the token that selects a capability class");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(CLI);

    private static final Predicate<JavaMethod> READS_THE_COMMAND = method ->
            method.getRawParameterTypes().size() == 1
                    && method.getRawParameterTypes().getFirst().isEquivalentTo(String[].class)
                    && (method.getRawReturnType().isEquivalentTo(String.class)
                    || method.getRawReturnType().isAssignableTo(List.class));

    private static final Predicate<JavaMethod> WRITES_AN_ERROR_LINE = method ->
            method.getRawParameterTypes().stream().anyMatch(type -> type.isEquivalentTo(PrintStream.class))
                    && method.getRawParameterTypes().stream().anyMatch(type -> type.getName().equals(EXIT_CODE));

    @Test
    void noCliTypeButTheOwnerReadsTheCommandTokenFromTheRawArguments() {
        check(READS_THE_COMMAND, "reads the command from the raw argument vector", COMMAND_TOKEN_OWNER);
    }

    @Test
    void noCliTypeButTheOwnerWritesTheErrorLine() {
        check(WRITES_AN_ERROR_LINE, "turns an exit code and a message into an error line", ERROR_LINE_OWNER);
    }

    @Test
    void theErrorLineFormatIsSpelledInOneSourceFile() throws IOException {
        Set<String> spelling = new TreeSet<>();
        CliOptionParsingRefusesUnknownOptionsTest.cliSources().forEach((name, source) -> {
            if (Unit.of(name, source).text().contains(ERROR_LINE_LITERAL)) {
                spelling.add(name);
            }
        });

        assertEquals(Set.of(ERROR_LINE_FILE), spelling,
                "the error line of the CLI is written by " + ERROR_LINE_FILE + " only; call it instead of spelling "
                        + ERROR_LINE_LITERAL + "\" again");
    }

    @Test
    void noCliSourceButTheWriterOfTheErrorLineSpellsAnErrorPrefix() throws IOException {
        Set<String> prefixed = new TreeSet<>();
        CliOptionParsingRefusesUnknownOptionsTest.cliSources().forEach((name, source) -> {
            if (!name.equals(ERROR_LINE_FILE) && AN_ERROR_PREFIX.matcher(Unit.of(name, source).text()).find()) {
                prefixed.add(name);
            }
        });

        assertEquals(Set.of(), prefixed,
                "a CLI source spells an error prefix of its own; refuse through " + ERROR_LINE_FILE
                        + " so that every failure reads MORPHEUS error [<code>]: <message>");
    }

    @Test
    void aFamilyPrefixIsAnErrorPrefixAndAnInformationLineIsNot() {
        String family = "final class A {\n    void a() {\n        err.println(\"MORPHEUS server usage error: \" + m);\n"
                + "    }\n}\n";
        String information = "final class B {\n    void b() {\n        err.println(\"MORPHEUS API listening on \" + uri);\n"
                + "    }\n}\n";
        String commented = "final class C {\n    // err.println(\"MORPHEUS usage error: \" + m);\n}\n";

        assertTrue(AN_ERROR_PREFIX.matcher(Unit.of("A.java", family).text()).find(), "a family prefix is refused");
        assertFalse(AN_ERROR_PREFIX.matcher(Unit.of("B.java", information).text()).find(),
                "a line that names no error is not a prefix");
        assertFalse(AN_ERROR_PREFIX.matcher(Unit.of("C.java", commented).text()).find(), "a comment is not a spelling");
    }

    @Test
    void aCommentOrAnotherWordingIsNotASpellingOfTheFormat() {
        String commented = "final class A {\n    // err.println(\"MORPHEUS error [\" + code);\n"
                + "    /** {@code \"MORPHEUS error [2]: x\"} */\n    void a() {\n        err.println(\"MORPHEUS usage error: \" + m);\n"
                + "    }\n}\n";
        String spelled = "final class B {\n    void b() {\n        err.println(\"MORPHEUS error [\" + code + \"]: \" + m);\n"
                + "    }\n}\n";

        assertFalse(Unit.of("A.java", commented).text().contains(ERROR_LINE_LITERAL), "a comment is not a spelling");
        assertTrue(Unit.of("B.java", spelled).text().contains(ERROR_LINE_LITERAL), "a string literal is a spelling");
    }

    private static void check(Predicate<JavaMethod> signature, String conversion, String owner) {
        methods().that(DescribedPredicate.describe("are declared in the CLI and " + conversion,
                        (JavaMethod method) -> declared(method) && signature.test(method)
                                && !OTHER_PURPOSES.containsKey(method.getFullName())))
                .should().beDeclaredIn(owner)
                .because("one owner " + conversion + " for every CLI sub-command; a copy drifts from it")
                .check(PRODUCTION);

        Set<String> stillMatching = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (OTHER_PURPOSES.containsKey(method.getFullName()) && (READS_THE_COMMAND.test(method)
                        || WRITES_AN_ERROR_LINE.test(method))) {
                    stillMatching.add(method.getFullName());
                }
            }
        }
        assertEquals(new TreeSet<>(OTHER_PURPOSES.keySet()), stillMatching,
                "a named exception is no longer declared with one of the two signatures; remove it from the list");
    }

    private static boolean declared(JavaMethod method) {
        return method.getOwner().getPackageName().startsWith(CLI)
                && !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                && !method.getName().startsWith("lambda$");
    }
}
