package com.morpheus.architecture;

import com.morpheus.architecture.CliOptionParsingRefusesUnknownOptionsTest.Block;
import com.morpheus.architecture.CliOptionParsingRefusesUnknownOptionsTest.Kind;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text of a failure -- its message, or the simple name of its type when the message is absent or blank -- is
 * formed by {@code FailureText.of}, and its relayed form, filtered for a caller outside the machine, by
 * {@code ServerLocationDisclosure.relayableText} (AUD-QUA-19).
 *
 * <p>Intent of the first rule: <em>no method outside the two owners turns a failure into text.</em> It is a property
 * of a declaration, read on the bytecode by its <b>signature</b> -- one parameter assignable to {@link Throwable}, a
 * {@link String} returned -- so a copy under another name or another parameter type ({@code Exception},
 * {@code RuntimeException}, a type of the project) is refused too. A method with that signature and another purpose is
 * named in {@link #OTHER_PURPOSES} with it, and a name there that no longer has the signature fails the rule.
 * Compiler-generated methods (lambda bodies) are not declarations and are skipped.</p>
 *
 * <p>Intent of the second rule: <em>the body of the conversion is written by its owners only.</em> A lambda, an
 * expression written in line and a module outside the imported classpath ({@code morpheus-provider-testkit}) escape
 * the bytecode, so a textual twin reads every {@code src/main/java} of the repository, comments and literals blanked,
 * and refuses a member -- a method, or the statement around a field initializer -- that reads the message of a failure
 * and its simple type name, outside the owners' files and the files of {@link #OTHER_SPELLINGS}. It is a heuristic: a
 * copy that reads the message through an intermediary escapes it, and the first rule sees it if it is declared.</p>
 */
class FailureTextOwnershipArchitectureTest {

    private static final String TEXT_OWNER = "com.morpheus.application.foundation.FailureText";
    private static final String RELAY_OWNER = "com.morpheus.application.security.ServerLocationDisclosure";
    private static final List<String> OWNER_FILES = List.of(
            "morpheus-application/src/main/java/com/morpheus/application/foundation/FailureText.java",
            "morpheus-application/src/main/java/com/morpheus/application/security/ServerLocationDisclosure.java");

    /** Methods that take one failure and return a {@link String} for another purpose, by full name, with it. */
    static final Map<String, String> OTHER_PURPOSES = Map.of(
            "com.morpheus.api.transport.BoundaryFailureMessage.safe(java.lang.Throwable)",
            "the name http-failure-disclosure gives the HTTP adapter's text of a failure: one call to"
                    + " ServerLocationDisclosure.relayableText",
            "com.morpheus.sdk.provider.ProviderPluginService.failureType(java.lang.Throwable)",
            "the type of a probe failure alone, the only part of it that crosses the remote boundary",
            "com.morpheus.sdk.provider.ProviderPluginDiscovery.failureType(java.lang.Exception)",
            "the type of a discovery failure alone, the only part of it that crosses the remote boundary",
            "com.morpheus.provider.openspec.OpenSpecSourceAttribution.failureType(java.lang.Throwable)",
            "the type of the original failure kept by an attributed one, without its message",
            "com.morpheus.transport.mcp.McpDiagnosticRedactor.describe(java.lang.Throwable)",
            "a diagnostic of an MCP peer: the type followed by the message with its secrets redacted, in a module"
                    + " that sees no type of MORPHEUS");

    /** Main sources, from the repository root, that read a failure's message and its type name for another purpose. */
    static final Map<String, String> OTHER_SPELLINGS = Map.of(
            "morpheus-mcp-transport/src/main/java/com/morpheus/transport/mcp/McpDiagnosticRedactor.java",
            "describe writes the type and the redacted message together, a diagnostic and not the text of a failure;"
                    + " the module sees no type of MORPHEUS");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    private static final Predicate<JavaMethod> TURNS_A_FAILURE_INTO_TEXT = method ->
            method.getRawParameterTypes().size() == 1
                    && method.getRawParameterTypes().getFirst().isAssignableTo(Throwable.class)
                    && method.getRawReturnType().isEquivalentTo(String.class);

    private static final Pattern READS_THE_MESSAGE = Pattern.compile("\\.\\s*get(?:Localized)?Message\\s*\\(\\s*\\)");
    private static final Pattern READS_THE_TYPE_NAME = Pattern.compile("\\.\\s*getSimpleName\\s*\\(\\s*\\)");
    private static final String MAIN_SOURCES = "/src/main/java/";

    @Test
    void noProductionMethodButTheTwoOwnersTurnsAFailureIntoText() {
        methods().that(DescribedPredicate.describe("are declared in production, take one failure and return a String",
                        (JavaMethod method) -> declared(method) && TURNS_A_FAILURE_INTO_TEXT.test(method)
                                && !OTHER_PURPOSES.containsKey(method.getFullName())))
                .should().beDeclaredIn(TEXT_OWNER).orShould().beDeclaredIn(RELAY_OWNER)
                .because("the text of a failure is formed by FailureText.of, and its relayed form by"
                        + " ServerLocationDisclosure.relayableText; a copy drifts from them")
                .check(PRODUCTION);
    }

    @Test
    void everyMethodNamedForAnotherPurposeStillTakesOneFailureAndReturnsAString() {
        Set<String> stillMatching = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (OTHER_PURPOSES.containsKey(method.getFullName()) && declared(method)
                        && TURNS_A_FAILURE_INTO_TEXT.test(method)) {
                    stillMatching.add(method.getFullName());
                }
            }
        }
        assertEquals(new TreeSet<>(OTHER_PURPOSES.keySet()), stillMatching,
                "a method named for another purpose is gone or no longer takes one failure and returns a String;"
                        + " remove its entry");
        OTHER_PURPOSES.forEach((method, purpose) ->
                assertFalse(purpose.isBlank(), "a method named for another purpose needs it written: " + method));
    }

    @Test
    void noMainSourceButTheOwnersSpellsTheConversion() throws IOException {
        Path root = RepositoryRoot.locate();
        Map<String, String> sources = new TreeMap<>();
        for (Path file : RepositoryTree.files(root)) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (relative.endsWith(".java") && ("/" + relative).contains(MAIN_SOURCES)) {
                sources.put(relative, Files.readString(file));
            }
        }
        assertTrue(sources.keySet().containsAll(OWNER_FILES), "the scan did not read the owners: " + OWNER_FILES);
        assertTrue(sources.keySet().stream().anyMatch(name -> name.startsWith("morpheus-provider-testkit/")),
                "the scan must read the modules the bytecode rule does not import");

        Set<String> spelling = spellings(sources);
        Set<String> expected = new TreeSet<>(OTHER_SPELLINGS.keySet());

        assertEquals(new TreeSet<>(), difference(spelling, expected),
                "a main source spells the conversion of a failure into text; call FailureText.of, or"
                        + " ServerLocationDisclosure.relayableText for a caller outside the machine");
        assertEquals(new TreeSet<>(), difference(expected, spelling),
                "a source named for another spelling no longer reads a failure's message and its type name;"
                        + " remove its entry");
        OTHER_SPELLINGS.forEach((file, purpose) ->
                assertFalse(purpose.isBlank(), "a source named for another spelling needs it written: " + file));
    }

    @Test
    void aCopyInALambdaOrInLineIsASpellingAndACommentOrAMessageAloneIsNot() {
        String lambda = "final class A {\n    static final Function<Throwable, String> TEXT =\n"
                + "            f -> f.getMessage() == null ? f.getClass().getSimpleName() : f.getMessage();\n}\n";
        String inLine = "final class B {\n    Entry read(Row row) {\n        try {\n            return decode(row);\n"
                + "        } catch (IllegalArgumentException failure) {\n"
                + "            String reason = failure.getMessage() == null\n"
                + "                    ? failure.getClass().getSimpleName() : failure.getMessage();\n"
                + "            return unreadable(reason);\n        }\n    }\n}\n";
        String commented = "final class C {\n    // failure.getMessage() == null ? failure.getClass().getSimpleName()\n"
                + "    String text(Throwable failure) {\n        return FailureText.of(failure);\n    }\n}\n";
        String messageAlone = "final class D {\n    String text(Throwable failure) {\n"
                + "        return \"failed: \" + failure.getMessage();\n    }\n}\n";
        String apart = "final class E {\n    String type(Throwable failure) {\n"
                + "        return failure.getClass().getSimpleName();\n    }\n\n"
                + "    String message(Throwable failure) {\n        return failure.getMessage();\n    }\n}\n";

        assertEquals(Set.of("A.java", "B.java"), spellings(Map.of("A.java", lambda, "B.java", inLine,
                "C.java", commented, "D.java", messageAlone, "E.java", apart)));
    }

    static Set<String> spellings(Map<String, String> sources) {
        Set<String> spelling = new TreeSet<>();
        sources.forEach((name, source) -> {
            if (OWNER_FILES.contains(name)) {
                return;
            }
            Unit unit = Unit.of(name, source.replace("\r\n", "\n"));
            Matcher message = READS_THE_MESSAGE.matcher(unit.code());
            while (message.find()) {
                if (READS_THE_TYPE_NAME.matcher(member(unit, message.start())).find()) {
                    spelling.add(name);
                    return;
                }
            }
        });
        return spelling;
    }

    /** The method around a position, or the statement around it when it is not in a method (a field initializer). */
    private static String member(Unit unit, int position) {
        Block method = unit.innermost(position, Kind.METHOD);
        if (method != null) {
            return unit.code().substring(method.open(), method.close());
        }
        String code = unit.code();
        int start = position;
        while (start > 0 && ";{}".indexOf(code.charAt(start - 1)) < 0) {
            start--;
        }
        int end = code.indexOf(';', position);
        return code.substring(start, end < 0 ? code.length() : end);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> difference = new TreeSet<>(left);
        difference.removeAll(right);
        return difference;
    }

    private static boolean declared(JavaMethod method) {
        return !method.getModifiers().contains(JavaModifier.SYNTHETIC) && !method.getName().startsWith("lambda$");
    }
}
