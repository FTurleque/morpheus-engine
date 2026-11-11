package com.morpheus.architecture;

import com.morpheus.architecture.ArchitectureSuiteSources.TestClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The textual bans of a type whose target would let a rule on the bytecode hold them, and that no rule holds yet.
 *
 * <p>A textual ban - {@code assertFalse(<anything>.contains("SomeType")...)} - refuses the mention of a name; a rule
 * refuses the compiled dependency, indirect or unspelt ones included. The rule misses one thing only: a reference to a
 * compile-time constant, which javac inlines ({@code .claude/rules/architecture.md}, "Regle ArchUnit ou assertion
 * textuelle"). A ban is therefore <em>awaiting a rule</em> when its target is a type declared under the
 * {@code src/main/java} of a module this suite imports, declares no non-private {@code static final} field of type
 * {@code String} or of a primitive type (any such field of an interface), and is named by no class of the suite that
 * evaluates rules on bytecode. The count of those bans equals {@link #AWAITING_A_RULE_CEILING}: one more fails with the
 * members by file, one fewer fails until the ceiling is lowered in the same change. The ceiling equals its key in
 * {@code config/enforcement-ceiling-ratchets.properties}, so that raising it touches a governance file.</p>
 *
 * <p>The expression reads the code of the test classes, comments and text blocks blanked, string literals kept, with any
 * receiver before {@code .contains(}, with or without a message. A target named in two imported modules counts when
 * one of them qualifies. A rule class names a target when its simple name is a whole word of its code, string literals
 * included and the literals of its own {@code .contains(...)} calls excluded, or when it starts with the literal given
 * to a {@code haveSimpleNameStartingWith(...)}: a family rule names its family. Both readings over-approximate what
 * is held, so an approximation lets the text stand alone, never forces a rule that would miss an inlined constant. A
 * name cited by a rule class for another reason leaves the population; the review holds that case.</p>
 *
 * <p>The class reads the sources of the targets, so it is no coherence check.</p>
 */
class TypeBansAwaitingABytecodeRuleTest {

    /**
     * The textual bans of a type awaiting a rule on the bytecode. Lowered with each member that receives its rule, with
     * its ratchet key; never raised.
     */
    static final int AWAITING_A_RULE_CEILING = 0;

    private static final String SUITE_POM = "morpheus-architecture-tests/pom.xml";
    private static final String MAIN_SOURCES = "src/main/java";

    private static final Pattern TYPE_BAN = Pattern.compile(
            "\\bassertFalse\\(\\s*[^;]*?\\.contains\\(\"([A-Z][A-Za-z0-9_]*)\"\\)\\s*[,)]");
    private static final Pattern CONTAINS_LITERAL = Pattern.compile("\\.contains\\(\\s*\"[^\"]*\"\\s*\\)");
    private static final Pattern FAMILY_PREFIX = Pattern.compile("SimpleNameStartingWith\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern IMPORTED_MODULE = Pattern.compile(
            "<groupId>io\\.github\\.fturleque</groupId>\\s*<artifactId>([\\w-]+)</artifactId>");
    private static final String CONSTANT_TYPE = "(?:String|byte|short|int|long|float|double|boolean|char)";
    private static final Pattern FIELD_WITH_MODIFIERS = Pattern.compile(
            "(?:^|[{;}])\\s*((?:(?:public|protected|private|static|final|transient|volatile)\\s+)+)"
                    + CONSTANT_TYPE + "\\s+\\w+\\s*=", Pattern.MULTILINE);
    private static final Pattern INTERFACE_DECLARATION = Pattern.compile("\\binterface\\s+[A-Z]\\w*");
    private static final Pattern INTERFACE_FIELD = Pattern.compile(
            "(?:^|[{;}])\\s*(?:(?:public|static|final)\\s+)*" + CONSTANT_TYPE + "\\s+\\w+\\s*=", Pattern.MULTILINE);

    /** What the expression reads besides the ban: the sources of each candidate target, and the rule classes. */
    record Targets(Map<String, List<String>> sourcesBySimpleName, List<String> ruleClassSources) {
    }

    @Test
    void theTextualBansOfATypeAwaitingARuleOnTheBytecodeDoNotGrow() {
        Targets targets = repositoryTargets();
        Map<String, List<String>> membersByFile = new TreeMap<>();
        for (TestClass testClass : ArchitectureSuiteSources.suite().testClasses()) {
            List<String> members = awaitingMembers(testClass.source(), targets);
            if (!members.isEmpty()) {
                membersByFile.put(testClass.path(), members);
            }
        }
        long measured = membersByFile.values().stream().mapToLong(List::size).sum();
        if (measured > AWAITING_A_RULE_CEILING) {
            fail(measured + " textual bans of a type await a rule on the bytecode, the ceiling is "
                    + AWAITING_A_RULE_CEILING + ": their target declares no compile-time constant, so a rule sees "
                    + "every dependency the text sees and the indirect ones it misses. Write the rule in the "
                    + "*ArchitectureTest of the intention, the textual assertion may stay beside it. Members by file: "
                    + membersByFile);
        }
        assertEquals(AWAITING_A_RULE_CEILING, measured,
                "the textual bans of a type awaiting a rule fell to " + measured
                        + ": lower AWAITING_A_RULE_CEILING to " + measured + " in this change. Members by file: "
                        + membersByFile);
    }

    @Test
    void theCeilingEqualsItsRatchetKey() {
        assertEquals(List.of(), EnforcementMechanismAccountingTest.ceilingRefusals(
                        EnforcementMechanismAccountingTest.ceilingRatchets(),
                        EnforcementMechanismAccountingTest.AWAITING_CEILING_KEY, AWAITING_A_RULE_CEILING),
                "the ceiling of the bans awaiting a rule is held by a governance file, not by this constant alone");
    }

    @Test
    void theExpressionCountsABanWhoseTargetPermitsARuleThatNoRuleClassNames() {
        Targets targets = new Targets(
                Map.of(
                        "Member", List.of("final class Member { void run() { } }"),
                        "PublicConstant", List.of("public final class PublicConstant { static final int LIMIT = 3; }"),
                        "PrivateConstant", List.of(
                                "final class PrivateConstant {\n    private static final String TYPE = \"x\";\n}"),
                        "Named", List.of("final class Named { }"),
                        "Qualified", List.of("final class Qualified { }"),
                        "Quoted", List.of("final class Quoted { }"),
                        "FamilyMember", List.of("final class FamilyMember { }")),
                List.of("""
                        import com.tngtech.archunit.core.domain.JavaClasses;
                        class SomeRuleTest {
                            void rule() {
                                noClasses().should().dependOnClassesThat().haveSimpleName("Named").check(classes);
                                noClasses().should().dependOnClassesThat().haveSimpleNameStartingWith("Family");
                                assertFalse(source.contains("Member"), "the own literal of a ban names nothing");
                            }
                        }
                        """));
        String source = """
                class Sample {
                    void bans() {
                        assertFalse(source.contains("Member"), "no dependency on Member");
                        assertFalse(source.contains("PublicConstant"), "no mention of PublicConstant");
                        assertFalse(source.contains("PrivateConstant"), "no dependency on PrivateConstant");
                        assertFalse(source.contains("String"), "a type of the JDK");
                        assertFalse(source.contains("Named"), "held by a rule");
                        assertFalse(source.contains("FamilyMember"), "held by a family rule");
                        // assertFalse(source.contains("Quoted"), "a comment");
                        String quoted = "assertFalse(source.contains(\\"Quoted\\"), \\"a string\\")";
                        assertFalse(Files.readString(path).contains("Qualified"));
                    }
                }
                """;

        assertEquals(List.of("Member", "PrivateConstant", "Qualified"), awaitingMembers(source, targets));
    }

    @Test
    void aNonPrivateStaticFinalFieldOfAStringOrPrimitiveTypeIsAnInlinableConstant() {
        assertTrue(declaresAnInlinableConstant("class A {\n    public static final String NAME = \"a\";\n}"));
        assertTrue(declaresAnInlinableConstant("class A {\n    static final long LIMIT = 1L;\n}"));
        assertTrue(declaresAnInlinableConstant("interface A {\n    int LIMIT = 1;\n}"));
        assertFalse(declaresAnInlinableConstant("class A {\n    private static final String NAME = \"a\";\n}"),
                "a private constant is not readable from another class, so never inlined into it");
        assertFalse(declaresAnInlinableConstant("class A {\n    static final List<String> NAMES = List.of();\n}"),
                "a reference that is not a String is no compile-time constant");
        assertFalse(declaresAnInlinableConstant("class A {\n    final int count = 1;\n}"),
                "an instance field is no compile-time constant of the class");
        assertFalse(declaresAnInlinableConstant("class A {\n    // public static final String NAME = \"a\";\n}"),
                "a commented declaration declares nothing");
    }

    static List<String> awaitingMembers(String testSource, Targets targets) {
        List<String> members = new ArrayList<>();
        Matcher ban = TYPE_BAN.matcher(TestSourceText.codeAndStrings(testSource));
        while (ban.find()) {
            String type = ban.group(1);
            List<String> sources = targets.sourcesBySimpleName().getOrDefault(type, List.of());
            boolean permitsARule = sources.stream().anyMatch(source -> !declaresAnInlinableConstant(source));
            if (permitsARule && !namedByARuleClass(type, targets.ruleClassSources())) {
                members.add(type);
            }
        }
        return members;
    }

    static boolean declaresAnInlinableConstant(String source) {
        String code = TestSourceText.code(source);
        Matcher field = FIELD_WITH_MODIFIERS.matcher(code);
        while (field.find()) {
            List<String> modifiers = List.of(field.group(1).trim().split("\\s+"));
            if (modifiers.contains("static") && modifiers.contains("final") && !modifiers.contains("private")) {
                return true;
            }
        }
        return INTERFACE_DECLARATION.matcher(code).find() && INTERFACE_FIELD.matcher(code).find();
    }

    static boolean namedByARuleClass(String type, List<String> ruleClassSources) {
        Pattern wholeWord = Pattern.compile("\\b" + Pattern.quote(type) + "\\b");
        for (String source : ruleClassSources) {
            String text = CONTAINS_LITERAL.matcher(TestSourceText.codeAndStrings(source)).replaceAll(" ");
            if (wholeWord.matcher(text).find()) {
                return true;
            }
            Matcher prefix = FAMILY_PREFIX.matcher(text);
            while (prefix.find()) {
                if (type.startsWith(prefix.group(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Targets repositoryTargets() {
        Path root = RepositoryRoot.locate();
        Map<String, List<String>> sourcesBySimpleName = new TreeMap<>();
        try {
            String pom = Files.readString(root.resolve(SUITE_POM));
            Matcher module = IMPORTED_MODULE.matcher(pom.substring(pom.indexOf("<dependencies>")));
            while (module.find()) {
                Path sources = root.resolve(module.group(1)).resolve(MAIN_SOURCES);
                if (!Files.isDirectory(sources)) {
                    continue;
                }
                try (Stream<Path> tree = Files.walk(sources)) {
                    for (Path file : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                        String simpleName = file.getFileName().toString().replace(".java", "");
                        sourcesBySimpleName.computeIfAbsent(simpleName, name -> new ArrayList<>())
                                .add(Files.readString(file));
                    }
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        if (sourcesBySimpleName.isEmpty()) {
            throw new IllegalStateException("no main source found in the modules " + SUITE_POM + " imports");
        }
        List<String> ruleClasses = ArchitectureSuiteSources.suite().sourcesBySimpleName().values().stream()
                .filter(ArchitectureSuiteSources::evaluatesRulesOnBytecode)
                .toList();
        return new Targets(sourcesBySimpleName, ruleClasses);
    }
}
