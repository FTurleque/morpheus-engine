package com.morpheus.architecture;

import com.morpheus.domain.source.SourceLocator;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The path of a refusal is not written as a locator, nor by the platform separator (PRV-6).
 *
 * <p>Two texts designate one file and obey opposite rules. A {@code SourceLocator} is a stable, comparable
 * designation and normalizes: it rewrites a backslash to a slash, and stores and deltas persist the result. The text
 * of a refusal names the file as the operator will find it and substitutes nothing, because on Linux a backslash is a
 * legal character of a file name and a rewritten name is another file. Both are right, and code that holds the
 * second duty and reaches for the first rewrites the operator's file name, which is how
 * {@code OpenSpecSourceAttribution} and the evidence budget of the three OpenSpec readers came to name a path that
 * does not exist.</p>
 *
 * <p><b>What this guard can tell apart, and what it cannot.</b> A static reading cannot tell a refusal site from a
 * locator site by intent: {@code SourceLocator.file(...)} and {@code replace('\\', '/')} are legitimate where a
 * locator or a key is built, and this repository builds many of them. Two things a reading does see.</p>
 * <ol>
 *   <li><b>A class that declares itself a refusal builder</b> by calling {@code WorkspaceRelativePathText.of(} (or
 *   importing it statically). The text scan <em>discovers</em> those classes, in every {@code morpheus-*} module, and
 *   refuses in each of them seven ways of writing a path by locator, by substitution or by platform:
 *   {@code SourceLocator.file(}, {@code new SourceLocator(}, {@code new SourcePath(}, a {@code replace} or a
 *   {@code replaceAll} of a backslash by a slash, {@code File.separator} and {@code getSeparator(}. It does not say
 *   the refusal <em>is</em> built from components, only that it is not built these ways.</li>
 *   <li><b>A budget call that is given a locator as its text.</b> The {@code source} argument of every
 *   {@code ProviderIngestionBudget} method is the text of a refusal. The scan reads every call of those methods in
 *   every module, and refuses a last argument that ends in {@code .value()} or {@code .toString()} or names
 *   {@code SourceLocator}. That is the shape of the defect the three OpenSpec evidence sites had.</li>
 * </ol>
 *
 * <p>Comments are not code and are ignored; a string literal is kept, so a forbidden call spelled inside a message is
 * a false positive nobody has written.</p>
 *
 * <p>The ArchUnit rule holds one class by name: {@code OpenSpecSourceAttribution} must not depend on
 * {@code SourceLocator}. It covers what the text misses, a dependency that is not spelled {@code SourceLocator.file(}
 * (a factory, a static import), and it covers the class even if it stopped calling {@code WorkspaceRelativePathText}
 * and left the discovered set. The text covers what the rule misses, every other module, including
 * {@code morpheus-provider-reference} that the ArchUnit classpath does not hold (ADR-0103).</p>
 *
 * <p><b>Not covered, stated rather than implied.</b></p>
 * <ul>
 *   <li>A refusal builder that never calls {@code WorkspaceRelativePathText} is invisible to the first scan.
 *   {@code LocalSourceInventoryScanner#display} writes the source of a scan failure with a {@code replace} and is
 *   such a site: it sits beside {@code SourcePath}, the persisted identity of the same file, which normalizes the
 *   same way, so the two could not be corrected apart. It is left and named in ADR-0028.</li>
 *   <li>A class of the floor that <em>receives</em> the text of its refusal as a {@code String} parameter is not held
 *   on what its callers pass. {@code ProviderIngestionBudget} is such a class: the first scan proves it writes its
 *   own document names by the point, and only the second scan, which reads call sites, sees a caller that passes a
 *   locator. A caller that passes it through a variable or a helper ({@code String name = locator.value();}) is not
 *   seen by either.</li>
 *   <li>{@code addEvidenceFragment(String)}, which takes no text, is attributed by the session to the last document
 *   it read. A reader that cited a fragment from another file than the last one it read would be refused under the
 *   wrong name; nothing here checks that order, which the readers keep by reading a file and citing it in one
 *   method.</li>
 *   <li>The scan is per class, not per method: a class that builds a locator in one method and a refusal in another
 *   is refused for the locator, and its author must split it.</li>
 *   <li>A path written through a helper that substitutes, or by another spelling ({@code Path.toString()} then a
 *   {@code replace} with other arguments, a {@code Pattern}), is not seen.</li>
 * </ul>
 */
class RefusalPathTextArchitectureTest {
    private static final String REFUSAL_POINT = "WorkspaceRelativePathText.of(";
    private static final String REFUSAL_POINT_STATIC_IMPORT =
            "import static com.morpheus.application.files.WorkspaceRelativePathText.";
    private static final Set<String> KNOWN_REFUSAL_BUILDERS = Set.of(
            "OpenSpecSourceAttribution.java", "ProviderIngestionBudget.java", "SafeWorkspaceFileResolver.java");
    private static final Set<String> MODULES_CALLING_THE_BUDGET = Set.of(
            "morpheus-application", "morpheus-provider-markdown", "morpheus-provider-openspec",
            "morpheus-provider-synthetic");

    /** The text {@code '\\'}: a character literal holding one backslash, as it is written in source. */
    private static final String BACKSLASH_CHARACTER_LITERAL = Pattern.quote("'\\\\'");
    /** The text {@code "\\"}: a string literal holding one backslash, as it is written in source. */
    private static final String BACKSLASH_STRING_LITERAL = Pattern.quote("\"\\\\\"");
    /** Four backslashes, as a regular expression for one backslash is written in source. */
    private static final String BACKSLASH_REGULAR_EXPRESSION = Pattern.quote("\\\\\\\\");

    private static final List<Forbidden> FORBIDDEN = List.of(
            new Forbidden("SourceLocator.file(", Pattern.compile("SourceLocator\\s*\\.\\s*file\\s*\\(")),
            new Forbidden("new SourceLocator(", Pattern.compile("new\\s+SourceLocator\\s*\\(")),
            new Forbidden("new SourcePath(", Pattern.compile("new\\s+SourcePath\\s*\\(")),
            new Forbidden("a backslash replaced by a slash", Pattern.compile(
                    "\\.\\s*replace\\s*\\(\\s*(" + BACKSLASH_CHARACTER_LITERAL + "|" + BACKSLASH_STRING_LITERAL
                            + ")\\s*,\\s*('/'|\"/\")\\s*\\)")),
            new Forbidden("a backslash replaced by a slash (replaceAll)", Pattern.compile(
                    "\\.\\s*replaceAll\\s*\\(\\s*\"[^\"]*" + BACKSLASH_REGULAR_EXPRESSION
                            + "[^\"]*\"\\s*,\\s*\"/\"\\s*\\)")),
            new Forbidden("File.separator", Pattern.compile("File\\s*\\.\\s*separator")),
            new Forbidden("getSeparator(", Pattern.compile("getSeparator\\s*\\(")));

    private static final Pattern BUDGET_CALL = Pattern.compile("\\.\\s*(addBlocks|addEntities|addEvidenceFragment"
            + "|requireAdditionalFiles|requireFiles|requireDocumentBytes|requireAggregateBytes|requireLines"
            + "|requireEntities|requireBlocks|requireEvidenceBytes|requireUtf8Document)\\s*\\(");

    @Test
    void aClassThatWritesARefusalPathThroughThePointDoesNotAlsoWriteItAsALocatorOrByThePlatform() throws IOException {
        Path root = repoRoot();
        Set<String> builders = new TreeSet<>();
        List<String> violations = new ArrayList<>();

        for (Path source : mainSources(root)) {
            String code = withoutComments(Files.readString(source));
            if (!isRefusalBuilder(code)) {
                continue;
            }
            String site = root.relativize(source).toString().replace('\\', '/');
            builders.add(source.getFileName().toString());
            for (String violation : violationsIn(code)) {
                violations.add(site + " writes a refusal path through " + REFUSAL_POINT + ") and also through "
                        + violation);
            }
        }

        assertEquals(List.of(), violations,
                "a refusal names a file as it exists and substitutes nothing; a locator normalizes. A class that "
                        + "names a path through WorkspaceRelativePathText must not also build it as a locator "
                        + "or by platform separator: split the class, or write the refusal through the point");
        assertTrue(builders.containsAll(KNOWN_REFUSAL_BUILDERS),
                () -> "the scan must discover the refusal builders it is known to guard; a scan that finds nothing "
                        + "proves nothing. Missing: " + missing(builders) + ". Found: " + builders);
    }

    @Test
    void aBudgetCallIsNeverGivenALocatorAsTheTextOfItsRefusal() throws IOException {
        Path root = repoRoot();
        Set<String> modulesSeen = new TreeSet<>();
        List<String> violations = new ArrayList<>();

        for (Path source : mainSources(root)) {
            String code = withoutComments(Files.readString(source));
            Matcher call = BUDGET_CALL.matcher(code);
            while (call.find()) {
                modulesSeen.add(moduleOf(root, source));
            }
            for (String violation : budgetCallsNamingALocator(code)) {
                violations.add(root.relativize(source).toString().replace('\\', '/') + " passes " + violation);
            }
        }

        assertEquals(List.of(), violations,
                "the source argument of a ProviderIngestionBudget method is the text of a refusal; a locator "
                        + "rewrites a backslash, which is legal in a Linux file name, and names another file");
        assertTrue(modulesSeen.containsAll(MODULES_CALLING_THE_BUDGET),
                () -> "the scan must see the budget calls of every module known to make them; a scan that finds "
                        + "nothing proves nothing. Seen in: " + modulesSeen);
    }

    @Test
    void openSpecSourceAttributionDoesNotDependOnTheLocatorItMustNotBuildARefusalFrom() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.morpheus.provider.openspec");

        noClasses()
                .that().haveNameMatching("com\\.morpheus\\.provider\\.openspec\\.OpenSpecSourceAttribution(\\$.*)?")
                .should().dependOnClassesThat().areAssignableTo(SourceLocator.class)
                .because("the file a refusal names is written as it exists, and a SourceLocator rewrites a "
                        + "backslash; the readers record the locator, the attribution names the file")
                .check(imported);
    }

    @Test
    void theScanRefusesEveryWayOfWritingARefusalPathThatSubstitutesOrReadsThePlatform() {
        String base = "class Refusal { String text(Path p) { return WorkspaceRelativePathText.of(p) + %s; } }";

        assertEquals(List.of("SourceLocator.file("), violationsIn(
                base.formatted("SourceLocator.file(p.toString()).value()")));
        assertEquals(List.of("new SourceLocator("), violationsIn(
                base.formatted("new SourceLocator(\"file\", \"x\")")));
        assertEquals(List.of("new SourcePath("), violationsIn(base.formatted("new SourcePath(p.toString())")));
        assertEquals(List.of("a backslash replaced by a slash"), violationsIn(
                base.formatted("p.toString().replace('\\\\', '/')")));
        assertEquals(List.of("a backslash replaced by a slash"), violationsIn(
                base.formatted("p.toString().replace(\"\\\\\", \"/\")")));
        assertEquals(List.of("a backslash replaced by a slash (replaceAll)"), violationsIn(
                base.formatted("p.toString().replaceAll(\"\\\\\\\\\", \"/\")")));
        assertEquals(List.of("File.separator"), violationsIn(base.formatted("File.separator")));
        assertEquals(List.of("getSeparator("), violationsIn(
                base.formatted("FileSystems.getDefault().getSeparator()")));
        assertEquals(List.of(), violationsIn(base.formatted("p.toString().replaceAll(\"\\\\s+\", \"/\")")),
                "a replaceAll of whitespace is not a backslash rewrite");
    }

    @Test
    void theScanSeesAClassThatImportsThePointStatically() {
        String statically = "import static com.morpheus.application.files.WorkspaceRelativePathText.of;\n"
                + "class Refusal { String text(Path p) { return of(p) + SourceLocator.file(\"x\"); } }";

        assertTrue(isRefusalBuilder(withoutComments(statically)));
        assertEquals(List.of("SourceLocator.file("), violationsIn(withoutComments(statically)));
    }

    @Test
    void theBudgetScanRefusesALocatorAsTheTextOfARefusalAndAcceptsTheRest() {
        assertEquals(List.of("budget.addEvidenceFragment(excerpt, source.value())"), budgetCallsNamingALocator(
                "budget.addEvidenceFragment(excerpt, source.value());"));
        assertEquals(List.of("budget.addBlocks(count, file.toString())"), budgetCallsNamingALocator(
                "budget.addBlocks(count, file.toString());"));
        assertEquals(List.of("budget.requireFiles(2, SourceLocator.file(x).value())"), budgetCallsNamingALocator(
                "budget.requireFiles(2, SourceLocator.file(x).value());"));

        assertEquals(List.of(), budgetCallsNamingALocator("""
                budget.addBlocks(specifications.size() + requirements.size(), "openspec/current");
                budget.addEvidenceFragment(excerpt);
                budget.addEvidenceFragment(block.raw(), Provider.SOURCE_FILE);
                budget.addEntities(1, "a, (b)");
                budget.requireFiles(files.size(), WorkspaceRelativePathText.of(path));
                """));
    }

    @Test
    void theScanReadsCodeAndNotTheProseAroundIt() {
        String prose = """
                /** A locator is built by SourceLocator.file(x), never here. */
                class Refusal {
                    // SourceLocator.file(x) and File.separator are what this class must not use
                    String text(Path p) { return WorkspaceRelativePathText.of(p); /* new SourceLocator("a", "b") */ }
                }
                """;

        assertEquals(List.of(), violationsIn(withoutComments(prose)));
        assertTrue(withoutComments(prose).contains(REFUSAL_POINT));
        assertEquals("String a = \"// not a comment\"; ",
                withoutComments("String a = \"// not a comment\"; // a comment\n").stripTrailing() + " ");
    }

    @Test
    void aClassThatNeverNamesThePointIsOutOfTheFirstScansReach() {
        String locatorBuilder = "class Locators { SourceLocator of(Path p) { return SourceLocator.file(p.toString()); } "
                + "String key(Path p) { return p.toString().replace('\\\\', '/'); } }";

        assertFalse(isRefusalBuilder(withoutComments(locatorBuilder)),
                "a locator or key builder is not a refusal builder and is not judged; this is the limit the "
                        + "Javadoc states, not an oversight");
    }

    private static boolean isRefusalBuilder(String code) {
        return code.contains(REFUSAL_POINT) || code.contains(REFUSAL_POINT_STATIC_IMPORT);
    }

    private static List<String> violationsIn(String code) {
        List<String> violations = new ArrayList<>();
        for (Forbidden forbidden : FORBIDDEN) {
            if (forbidden.pattern().matcher(code).find()) {
                violations.add(forbidden.description());
            }
        }
        return violations;
    }

    private static List<String> budgetCallsNamingALocator(String code) {
        List<String> violations = new ArrayList<>();
        Matcher call = BUDGET_CALL.matcher(code);
        while (call.find()) {
            List<String> arguments = topLevelArguments(code, call.end());
            if (call.group(1).equals("addEvidenceFragment") && arguments.size() == 1) {
                continue;
            }
            String text = arguments.getLast();
            if (text.endsWith(".value()") || text.endsWith(".toString()") || text.contains("SourceLocator")) {
                int start = code.lastIndexOf(' ', call.start() - 1) + 1;
                violations.add(code.substring(start, call.start()).strip() + "." + call.group(1) + "("
                        + String.join(", ", arguments) + ")");
            }
        }
        return violations;
    }

    /** The arguments of the call whose opening parenthesis precedes {@code from}; literals are read as literals. */
    private static List<String> topLevelArguments(String code, int from) {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        int index = from;
        while (index < code.length()) {
            char character = code.charAt(index);
            if (character == '"' || character == '\'') {
                StringBuilder literal = new StringBuilder();
                int end = copyLiteral(code, index, String.valueOf(character), literal);
                current.append(literal);
                index = end;
                continue;
            }
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
                index++;
                continue;
            }
            current.append(character);
            index++;
        }
        throw new IllegalStateException("unterminated call in a scanned source");
    }

    private static String missing(Set<String> builders) {
        Set<String> missing = new TreeSet<>(KNOWN_REFUSAL_BUILDERS);
        missing.removeAll(builders);
        return missing.toString();
    }

    private static String moduleOf(Path root, Path source) {
        return root.relativize(source).getName(0).toString();
    }

    /** The source without its comments. String, character and text-block literals are kept, and read as literals. */
    static String withoutComments(String source) {
        StringBuilder code = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (current == '/' && next == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
            } else if (current == '/' && next == '*') {
                int end = source.indexOf("*/", index + 2);
                int stop = end < 0 ? source.length() : end + 2;
                for (int position = index; position < stop; position++) {
                    if (source.charAt(position) == '\n') {
                        code.append('\n');
                    }
                }
                code.append(' ');
                index = stop;
            } else if (source.startsWith("\"\"\"", index)) {
                index = copyLiteral(source, index, "\"\"\"", code);
            } else if (current == '"') {
                index = copyLiteral(source, index, "\"", code);
            } else if (current == '\'') {
                index = copyLiteral(source, index, "'", code);
            } else {
                code.append(current);
                index++;
            }
        }
        return code.toString();
    }

    private static int copyLiteral(String source, int start, String delimiter, StringBuilder code) {
        code.append(delimiter);
        int index = start + delimiter.length();
        while (index < source.length()) {
            if (source.charAt(index) == '\\' && index + 1 < source.length()) {
                code.append(source, index, index + 2);
                index += 2;
            } else if (source.startsWith(delimiter, index)) {
                code.append(delimiter);
                return index + delimiter.length();
            } else {
                code.append(source.charAt(index));
                index++;
            }
        }
        return index;
    }

    private static List<Path> mainSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("morpheus-"))
                    .sorted()
                    .toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> tree = Files.walk(main)) {
                    tree.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".java"))
                            .sorted()
                            .forEach(sources::add);
                }
            }
        }
        return sources;
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

    private record Forbidden(String description, Pattern pattern) {
    }
}
