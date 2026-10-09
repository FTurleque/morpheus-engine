package com.morpheus.architecture;

import com.morpheus.application.read.ProviderProjectRoot;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.Source;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The project root a reader publishes is the workspace root it received, spelled by one point.
 *
 * <p>Publication compares that root with the one the project was registered under. The structured-markdown
 * reader once published the file it read instead, and every markdown-only workspace failed to publish on a
 * store collision. Three mechanisms hold the rule because none sees all of it (ADR-0103; ADR-0028, amendments
 * PRV-1 and PRV-7):</p>
 *
 * <ul>
 *   <li><b>The source scan</b> holds the argument. No rule on bytecode can say which argument a constructor
 *   received, so the text reads the third argument of every construction of {@code ProjectSpecification}, simple
 *   or qualified, in every {@code morpheus-provider-*} module, and requires it to be
 *   {@code ProviderProjectRoot.locator(<received root>)} <em>and nothing more</em>: the parenthesis opened by the
 *   single point must close the argument, and what it encloses must be recognized by one of the
 *   {@link ReceivedRootForm admitted forms}, which are the scan's logic and not only its message. A name is
 *   followed to every binding it has in the file. A constructor reference, whose argument the text cannot read, is
 *   refused. The source is read once with its unicode escapes translated and its comments removed.</li>
 *   <li><b>The ArchUnit rule</b> sees a construction or a constructor reference whatever its spelling, and requires
 *   the single point in <em>the same code unit</em>, once per construction. It counts calls; it does not read the
 *   argument, so a construction only the rule sees is held to the co-location of a {@code locator} call, not to
 *   what that call's result becomes. {@code morpheus-provider-reference} is a test dependency of this module so that
 *   the rule sees all four reader modules, and a test holds the rule and the scan to the same source files.</li>
 *   <li><b>The behavioural tests</b> hold what the reader actually publishes. A guard requires every source file
 *   that constructs a {@code ProjectSpecification} to have, in its own module, a test that mentions that reader,
 *   {@code rootLocator()} and an expected root, or that hands a plugin creating it to the published contract.</li>
 * </ul>
 *
 * <p>What none of the three covers, written so that nobody reads more into a green build, and frozen by
 * {@link #theScanAcceptsTheDivergencesItDoesNotFollow}:</p>
 *
 * <ul>
 *   <li>a value that reaches a <b>parameter</b> is accepted as received: the scan does not follow it into the
 *   caller that passed it. The OpenSpec readers' package-private {@code read(Path workspaceRoot, ..., budget)} is
 *   such a method, and so would be a caller passing it {@code root.resolve("openspec")};</li>
 *   <li>a name is followed within its own file only, and without scopes. A binding of the same name in another
 *   method is held to the same forms (strict on purpose: a reader that reuses the name for another path must
 *   rename it), and a parameter of the same name in another method is enough for a name bound nowhere in the file
 *   -- an inherited field, say -- to pass;</li>
 *   <li>the receiver of {@code .workspaceRoot()} must be a parameter or a name bound only to such names, but its
 *   type is not read;</li>
 *   <li>a parameter is recognized by its declaration {@code (Type name,} or {@code , Type name)}: an untyped lambda
 *   parameter is not one, and a root obtained through it is refused;</li>
 *   <li>the behavioural-test guard checks mentions: a test that constructs the reader, reads {@code rootLocator()}
 *   and spells an expected root passes whether or not it compares them, or runs; it is per source file, not per
 *   construction;</li>
 *   <li>a construction by reflection is seen by neither the scan nor the rule;</li>
 *   <li>the single point written qualified or statically imported is refused, not admitted.</li>
 * </ul>
 */
class ProviderProjectRootArchitectureTest {
    private static final Pattern CONSTRUCTION = Pattern.compile(
            "\\bnew\\s+(?:com\\s*\\.\\s*morpheus\\s*\\.\\s*domain\\s*\\.\\s*project\\s*\\.\\s*)?"
                    + "ProjectSpecification\\s*\\(");
    private static final Pattern CONSTRUCTOR_REFERENCE =
            Pattern.compile("(?<![A-Za-z0-9_$])ProjectSpecification\\s*::\\s*new\\b");
    private static final String CONSTRUCTOR_REFERENCE_ARGUMENT = "<constructor reference>";
    private static final String SINGLE_POINT = "ProviderProjectRoot.locator(";
    private static final String NULL_CHECK_CALL = "Objects.requireNonNull(";
    private static final String CONTRACT_READ = "ProviderPluginContractAssertions.verifyRead(";
    private static final String PUBLISHED_ROOT = ".rootLocator()";
    private static final List<String> EXPECTED_ROOTS = List.of(SINGLE_POINT, "SourceLocator.file(");
    private static final List<String> READER_MODULES = List.of(
            "morpheus-provider-markdown",
            "morpheus-provider-openspec",
            "morpheus-provider-reference",
            "morpheus-provider-synthetic");

    private static final String IDENTIFIER = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final Pattern NAME_PATTERN = Pattern.compile("(?:this\\s*\\.\\s*)?(" + IDENTIFIER + ")");
    private static final Pattern REQUEST_ACCESSOR_PATTERN = Pattern.compile(
            "((?:this\\s*\\.\\s*)?" + IDENTIFIER + ")\\s*\\.\\s*workspaceRoot\\s*\\(\\s*\\)");
    private static final Pattern NORMALIZING_SUFFIX =
            Pattern.compile("(?s)(.*)\\.\\s*(?:toAbsolutePath|normalize)\\s*\\(\\s*\\)");
    private static final String TYPE = "[A-Za-z_$][A-Za-z0-9_$.]*(?:\\s*<[^()]*?>)?(?:\\s*\\[\\s*\\])*";
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("(?<!\\\\)((?:\\\\\\\\)*)\\\\u+([0-9a-fA-F]{4})");
    private static final Pattern RECORD_PATTERN = Pattern.compile("\\b(?:instanceof|case)\\s+" + TYPE + "\\s*\\(");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    /**
     * The spellings of the received root the scan admits. Each constant recognizes its own form, and the scan asks
     * them in order: the list a refusal prints is the list the scan applies.
     *
     * <p>The set is written out rather than left to an expression that refuses whatever it did not foresee, so that
     * widening it is a decision written here with its reason. The first two are admitted directly inside
     * {@code ProviderProjectRoot.locator(...)}; the last two only in a binding the scan follows, because inside the
     * argument they would be a second spelling of the normalization the single point owns.</p>
     */
    enum ReceivedRootForm {
        /**
         * {@code <request>.workspaceRoot()}: the accessor of the read request returns the root it carries, unaltered;
         * the receiver must itself be received -- a parameter, or a name bound only to such names -- because a
         * request built from a derived path carries that path. Written by the markdown and synthetic readers, and
         * bound by the OpenSpec content reader and the reference reader.
         */
        REQUEST_ACCESSOR(true, "<request>.workspaceRoot() with <request> a parameter or bound only to one") {
            @Override
            Verdict judge(String expression, String source, Set<String> followed) {
                Matcher accessor = REQUEST_ACCESSOR_PATTERN.matcher(expression);
                return accessor.matches()
                        ? Verdict.recognized(receiverRefusal(accessor.group(1), source, followed))
                        : Verdict.NOT_THIS_FORM;
            }
        },
        /**
         * {@code <name>} or {@code this.<name>}: admitted when every binding the name has in the file is admitted,
         * and, when it has none, only if the file declares it as a parameter. Written by the OpenSpec readers
         * ({@code root}) and the reference reader ({@code workspace}).
         */
        NAME(true, "<name> or this.<name>, a parameter or bound only to admitted forms") {
            @Override
            Verdict judge(String expression, String source, Set<String> followed) {
                Matcher name = NAME_PATTERN.matcher(expression);
                return name.matches()
                        ? Verdict.recognized(nameRefusal(name.group(1), source, followed))
                        : Verdict.NOT_THIS_FORM;
            }
        },
        /**
         * {@code Objects.requireNonNull(<admitted>, "<message>")} returns its first argument unchanged. Bound by the
         * OpenSpec readers.
         */
        NULL_CHECK(false, "Objects.requireNonNull(<admitted>, \"<message>\")") {
            @Override
            Verdict judge(String expression, String source, Set<String> followed) {
                if (!expression.startsWith(NULL_CHECK_CALL)
                        || closingParenthesis(expression, NULL_CHECK_CALL.length() - 1) != expression.length() - 1) {
                    return Verdict.NOT_THIS_FORM;
                }
                List<String> arguments = topLevelArguments(expression, NULL_CHECK_CALL.length());
                if (arguments.size() != 2 || !arguments.get(1).startsWith("\"") || !arguments.get(1).endsWith("\"")) {
                    return Verdict.NOT_THIS_FORM;
                }
                return Verdict.recognized(receivedRootRefusal(arguments.getFirst(), source, false, followed));
            }
        },
        /**
         * {@code <admitted>.toAbsolutePath()} and {@code <admitted>.normalize()}: the single point applies both
         * again, and both are idempotent, so the published locator is the one the received root yields. Bound by
         * the OpenSpec readers, which normalize the root once for their own file access.
         */
        ABSOLUTE_OR_NORMALIZED(false, "<admitted>.toAbsolutePath(), <admitted>.normalize()") {
            @Override
            Verdict judge(String expression, String source, Set<String> followed) {
                Matcher suffix = NORMALIZING_SUFFIX.matcher(expression);
                return suffix.matches()
                        ? Verdict.recognized(receivedRootRefusal(suffix.group(1).strip(), source, false, followed))
                        : Verdict.NOT_THIS_FORM;
            }
        };

        private final boolean insideTheSinglePoint;
        private final String spelling;

        ReceivedRootForm(boolean insideTheSinglePoint, String spelling) {
            this.insideTheSinglePoint = insideTheSinglePoint;
            this.spelling = spelling;
        }

        abstract Verdict judge(String expression, String source, Set<String> followed);

        static List<String> admitted(boolean insideTheSinglePoint) {
            List<String> spellings = new ArrayList<>();
            for (ReceivedRootForm form : values()) {
                if (form.insideTheSinglePoint || !insideTheSinglePoint) {
                    spellings.add(form.spelling);
                }
            }
            return spellings;
        }
    }

    private record Verdict(boolean isThisForm, String refusal) {
        static final Verdict NOT_THIS_FORM = new Verdict(false, null);

        static Verdict recognized(String refusal) {
            return new Verdict(true, refusal);
        }
    }

    @Test
    void everyProviderPublishesItsProjectRootThroughTheSinglePoint() throws IOException {
        Scan scan = Scan.of(repoRoot());

        assertEquals(List.of(), scan.violations(),
                "a provider publishes as project root the workspace root it received, written inline as "
                        + SINGLE_POINT + "<received root>) in the ProjectSpecification it constructs, and nothing "
                        + "more; any other root makes its own publication collide with the registered project");
        assertEquals(List.of(), scan.unseen(READER_MODULES),
                "a scan that finds nothing proves nothing; found: " + scan.sitesByModule());
    }

    /**
     * PRV-7: the scan used to check only that the argument started with the single point, so a reader resolving a
     * subdirectory inside it passed, and PRV-1 could recur as it was.
     */
    @Test
    void theScanRefusesARootResolvedInsideTheSinglePoint(@TempDir Path repository) throws IOException {
        reader(repository, "morpheus-provider-seventh", "SeventhReader",
                "ProviderProjectRoot.locator(request.workspaceRoot().resolve(\"openspec\"))", "");

        Scan scan = Scan.of(repository);

        assertEquals(1, scan.violations().size(), () -> "expected one refusal: " + scan.violations());
        String violation = scan.violations().getFirst();
        assertTrue(violation.startsWith("morpheus-provider-seventh/src/main/java/com/example/SeventhReader.java "
                        + "publishes project root "
                        + "'ProviderProjectRoot.locator(request.workspaceRoot().resolve(\"openspec\"))'"),
                violation);
        assertTrue(violation.contains(
                        "'request.workspaceRoot().resolve(\"openspec\")' is none of the admitted forms"),
                violation);
    }

    /**
     * Every other way to publish a root derived from the received one, each refused on its own: around the single
     * point, through a name, through a request built from a derived path, through a pattern variable, a
     * parenthesized assignment or a unicode escape, and through a construction the scan used not to read.
     */
    @Test
    void theScanRefusesEveryDerivedRootInsideOrAroundTheSinglePoint(@TempDir Path repository) throws IOException {
        Map<String, List<String>> refused = new TreeMap<>();
        refused.put("AParent", List.of("ProviderProjectRoot.locator(request.workspaceRoot().getParent())", ""));
        refused.put("BChainedAfter",
                List.of("ProviderProjectRoot.locator(request.workspaceRoot()).resolve(\"x\")", ""));
        refused.put("CNormalizedInside",
                List.of("ProviderProjectRoot.locator(request.workspaceRoot().normalize())", ""));
        refused.put("DNested", List.of(
                "ProviderProjectRoot.locator(Path.of(ProviderProjectRoot.locator(request.workspaceRoot()).value()))",
                ""));
        refused.put("EBoundToASubdirectory", List.of("ProviderProjectRoot.locator(root)",
                "Path root = request.workspaceRoot().resolve(\"openspec\");"));
        refused.put("FReboundLater", List.of("ProviderProjectRoot.locator(root)",
                "Path root = request.workspaceRoot(); root = root.getParent();"));
        refused.put("GBoundThroughAnotherName", List.of("ProviderProjectRoot.locator(root)",
                "Path base = request.workspaceRoot().resolve(\"x\"); Path root = base.toAbsolutePath().normalize();"));
        refused.put("HBoundByALoop", List.of("ProviderProjectRoot.locator(root)",
                "for (Path root : List.of(request.workspaceRoot())) { }"));
        refused.put("INotTheSinglePoint", List.of("SourceLocator.file(request.workspaceRoot().toString())", ""));
        refused.put("JDerivedRequest", List.of("ProviderProjectRoot.locator(sub.workspaceRoot())",
                "ProviderReadRequest sub = ProviderReadRequest.all(request.workspaceRoot().resolve(\"openspec\"), id);"));
        refused.put("KDerivedRequestThroughAField", List.of("ProviderProjectRoot.locator(this.sub.workspaceRoot())",
                "this.sub = ProviderReadRequest.all(request.workspaceRoot().resolve(\"openspec\"), id);"));
        refused.put("LInstanceofPattern", List.of("ProviderProjectRoot.locator(workspaceRoot)",
                "if (request.workspaceRoot().resolve(\"openspec\") instanceof Path workspaceRoot) { }"));
        refused.put("MSwitchPattern", List.of("ProviderProjectRoot.locator(workspaceRoot)",
                "switch (request.workspaceRoot().resolve(\"openspec\")) { case Path workspaceRoot -> { } }"));
        refused.put("NRecordPattern", List.of("ProviderProjectRoot.locator(workspaceRoot)",
                "if (wrapped instanceof Wrapped(Path workspaceRoot)) { }"));
        refused.put("OParenthesizedAssignment", List.of("ProviderProjectRoot.locator(root)",
                "Path root = request.workspaceRoot(); (root) = root.resolve(\"openspec\");"));
        refused.put("PUnicodeEscapedName", List.of("ProviderProjectRoot.locator(root)",
                "Path root = request.workspaceRoot(); \\u0072oot = root.resolve(\"openspec\");"));
        refused.put("QInheritedField", List.of("ProviderProjectRoot.locator(inherited)", ""));
        refused.put("RUntypedLambdaParameter", List.of("ProviderProjectRoot.locator(given.workspaceRoot())",
                "java.util.function.Consumer<ProviderReadRequest> use = given -> { };"));
        for (Map.Entry<String, List<String>> entry : refused.entrySet()) {
            reader(repository, "morpheus-provider-derived", entry.getKey(),
                    entry.getValue().get(0), entry.getValue().get(1));
        }
        Path sources = repository.resolve("morpheus-provider-derived/src/main/java/com/example");
        Files.writeString(sources.resolve("SQualifiedConstruction.java"), """
                final class SQualifiedConstruction {
                    Object read(ProviderReadRequest request) {
                        return new com.morpheus.domain.project.ProjectSpecification(request.projectId(), "n",
                                SourceLocator.file(ProviderProjectRoot.locator(request.workspaceRoot()).value()
                                        + "/openspec"));
                    }
                }
                """);
        Files.writeString(sources.resolve("TConstructorReference.java"), """
                final class TConstructorReference {
                    Maker read(ProviderReadRequest request) {
                        return ProjectSpecification::new;
                    }
                }
                """);
        refused.put("SQualifiedConstruction", List.of());
        refused.put("TConstructorReference", List.of());

        Scan scan = Scan.of(repository);

        List<String> refusedReaders = new ArrayList<>();
        for (String violation : scan.violations()) {
            String site = violation.substring(0, violation.indexOf(".java"));
            refusedReaders.add(site.substring(site.lastIndexOf('/') + 1));
        }
        assertEquals(List.copyOf(refused.keySet()), refusedReaders,
                () -> "each derived root must be refused exactly once:\n" + String.join("\n", scan.violations()));
    }

    /**
     * The forms the four reader modules write today, spelled as they write them, plus the spellings a reader may
     * legitimately add: a comment inside the argument or the construction, and a field bound from a parameter.
     * The real tree is checked by {@link #everyProviderPublishesItsProjectRootThroughTheSinglePoint}; this replays
     * the forms so that the acceptance stays proved when a reader changes.
     */
    @Test
    void theScanAcceptsTheFormsTheReadersWrite(@TempDir Path repository) throws IOException {
        reader(repository, "morpheus-provider-markdown", "MarkdownLike",
                "ProviderProjectRoot.locator(request.workspaceRoot())", "");
        reader(repository, "morpheus-provider-openspec", "OpenSpecLike",
                "ProviderProjectRoot.locator(root)",
                "Path root = Objects.requireNonNull(workspaceRoot, \"workspaceRoot\").toAbsolutePath().normalize();");
        reader(repository, "morpheus-provider-openspec", "OpenSpecContentLike",
                "ProviderProjectRoot.locator(root)", "Path root = request.workspaceRoot();");
        reader(repository, "morpheus-provider-reference", "ReferenceLike",
                "ProviderProjectRoot.locator(workspace)", "Path workspace = request.workspaceRoot();");
        reader(repository, "morpheus-provider-synthetic", "SyntheticLike",
                "ProviderProjectRoot.locator(\n                        request.workspaceRoot())", "");
        reader(repository, "morpheus-provider-synthetic", "CommentedLike",
                "ProviderProjectRoot.locator(/* don't resolve: it's the received root */ request.workspaceRoot())",
                "");
        Path sources = repository.resolve("morpheus-provider-synthetic/src/main/java/com/example");
        Files.writeString(sources.resolve("FieldLike.java"), """
                final class FieldLike {
                    private final Path root;

                    FieldLike(Path root) {
                        this.root = root;
                    }

                    Object read(ProviderReadRequest request) {
                        return new ProjectSpecification(request.projectId(), "n", // it's the received root
                                ProviderProjectRoot.locator(this.root));
                    }
                }
                """);

        Scan scan = Scan.of(repository);

        assertEquals(List.of(), scan.violations());
        assertEquals(List.of(), scan.unseen(READER_MODULES));
        assertEquals(7, scan.constructions().size(), () -> "every construction must be read: " + scan.sitesByModule());
    }

    /**
     * What the scan does not follow, frozen so that a change in either direction is seen: a divergent value handed
     * to a parameter by its caller, a name bound nowhere in the file that shares its name with a parameter of
     * another method, and a request parameter whatever its type. Each is written in the class Javadoc and in the
     * ADR-0028 amendment PRV-7; the behavioural tests are what hold them.
     */
    @Test
    void theScanAcceptsTheDivergencesItDoesNotFollow(@TempDir Path repository) throws IOException {
        Path sources = Files.createDirectories(repository.resolve("morpheus-provider-residual/src/main/java/com/x"));
        Files.writeString(sources.resolve("ThroughTheCaller.java"), """
                final class ThroughTheCaller {
                    Object read(ProviderReadRequest request) {
                        return read(request.workspaceRoot().resolve("openspec"), request);
                    }

                    Object read(Path root, ProviderReadRequest request) {
                        return new ProjectSpecification(request.projectId(), "n", ProviderProjectRoot.locator(root));
                    }
                }
                """);
        Files.writeString(sources.resolve("InheritedFieldSharingAParameterName.java"), """
                final class InheritedFieldSharingAParameterName extends Base {
                    void other(Path inherited) { }

                    Object read(ProviderReadRequest request) {
                        return new ProjectSpecification(request.projectId(), "n",
                                ProviderProjectRoot.locator(inherited));
                    }
                }
                """);
        Files.writeString(sources.resolve("AnyTypeOfRequest.java"), """
                final class AnyTypeOfRequest {
                    Object read(SomethingElse request) {
                        return new ProjectSpecification(id, "n", ProviderProjectRoot.locator(request.workspaceRoot()));
                    }
                }
                """);

        Scan scan = Scan.of(repository);

        assertEquals(3, scan.constructions().size(), () -> "every construction must be read: " + scan.sitesByModule());
        assertEquals(List.of(), scan.violations());
    }

    /** A required module in which the scan sees no construction is refused, whatever the others hold. */
    @Test
    void aScanThatFindsNothingInARequiredModuleIsRefused(@TempDir Path repository) throws IOException {
        reader(repository, "morpheus-provider-markdown", "MarkdownLike",
                "ProviderProjectRoot.locator(request.workspaceRoot())", "");
        Path empty = Files.createDirectories(repository.resolve("morpheus-provider-openspec/src/main/java/com/example"));
        Files.writeString(empty.resolve("NoConstruction.java"), "class NoConstruction { }");

        Scan scan = Scan.of(repository);

        assertEquals(List.of(), scan.violations());
        assertEquals(
                List.of("morpheus-provider-openspec", "morpheus-provider-reference", "morpheus-provider-synthetic"),
                scan.unseen(READER_MODULES));
    }

    /** A construction the scan cannot delimit fails the scan, and the failure names the file. */
    @Test
    void aConstructionTheScanCannotDelimitNamesItsFile(@TempDir Path repository) throws IOException {
        Path sources = Files.createDirectories(repository.resolve("morpheus-provider-broken/src/main/java/com/x"));
        Files.writeString(sources.resolve("Unterminated.java"),
                "class Unterminated { Object o = new ProjectSpecification(id, \"n\", root; }");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> Scan.of(repository));

        assertTrue(failure.getMessage().startsWith("morpheus-provider-broken/src/main/java/com/x/Unterminated.java: "),
                failure.getMessage());
    }

    @Test
    void everyClassThatConstructsAProjectSpecificationObtainsItsRootFromTheSinglePoint() {
        projectRootRule().check(PRODUCTION);
    }

    /**
     * The rule is per code unit and per construction: a second construction not beside its own call to the single
     * point is refused, and so is a construction whose class calls the single point only in another method, and a
     * constructor reference. The class-wide rule it replaces accepted the first two and did not see the third.
     */
    @Test
    void theRuleRefusesAConstructionWithoutTheSinglePointInItsOwnCodeUnit() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(
                OneRootForTwoConstructions.class,
                RootObtainedInAnotherMethod.class,
                ConstructorReferenceWithoutTheSinglePoint.class,
                RootObtainedBesideEachConstruction.class);

        EvaluationResult result = projectRootRule().evaluate(fixtures);

        Set<String> refused = new TreeSet<>();
        for (String detail : result.getFailureReport().getDetails()) {
            refused.add(detail.substring(0, detail.indexOf(' ')));
        }
        assertEquals(new TreeSet<>(List.of(
                        OneRootForTwoConstructions.class.getName() + ".both(java.nio.file.Path)",
                        RootObtainedInAnotherMethod.class.getName() + ".construct(java.nio.file.Path)",
                        ConstructorReferenceWithoutTheSinglePoint.class.getName() + ".maker()")),
                refused, () -> String.join("\n", result.getFailureReport().getDetails()));
    }

    /**
     * The rule is only as wide as the classes it can see. Holding it to the source files the scan reads means a
     * reader module missing from this module's test classpath is refused by name instead of escaping the rule, as
     * {@code morpheus-provider-reference} did until PRV-7.
     */
    @Test
    void theRuleSeesEverySourceFileTheScanReads() throws IOException {
        Set<String> scanned = Scan.of(repoRoot()).constructingSources();
        Set<String> imported = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            if (constructsAProjectSpecification(javaClass)) {
                imported.add(sourceFileOf(javaClass));
            }
        }

        assertFalse(scanned.isEmpty(), "the scan found no construction");
        TreeSet<String> escaping = new TreeSet<>(scanned);
        escaping.removeAll(imported);
        TreeSet<String> unscanned = new TreeSet<>(imported);
        unscanned.removeAll(scanned);
        assertTrue(escaping.isEmpty() && unscanned.isEmpty(),
                () -> "the rule must see exactly the source files the scan reads; outside the architecture-test "
                        + "classpath, so escaping the rule: " + escaping + "; constructing outside any "
                        + "morpheus-provider-* module the scan reads: " + unscanned);
    }

    @Test
    void everyClassThatConstructsAProjectSpecificationHasATestThatReadsTheRootItPublishes() throws IOException {
        Path root = repoRoot();
        assertEquals(List.of(), untestedReaders(root, Scan.of(root)),
                "the behavioural tests are what hold the published root; a reader without one is held only by "
                        + "the spelling of its source");
    }

    /**
     * Both ways a reader test can read the published root are accepted. A reader whose own module has neither is
     * refused by name: a test elsewhere, a test that never reads the root, one that mentions it only in a comment,
     * and one that reads it without spelling any expected root.
     */
    @Test
    void aReaderWithoutATestOfItsPublishedRootIsRefused(@TempDir Path repository) throws IOException {
        reader(repository, "morpheus-provider-direct", "DirectReader",
                "ProviderProjectRoot.locator(request.workspaceRoot())", "");
        test(repository, "morpheus-provider-direct", "DirectReaderTest", "var content = new DirectReader().read(r); "
                + "assertEquals(ProviderProjectRoot.locator(w), content.project().rootLocator());");
        reader(repository, "morpheus-provider-plugin", "PluginReader",
                "ProviderProjectRoot.locator(request.workspaceRoot())", "");
        Files.writeString(repository.resolve("morpheus-provider-plugin/src/main/java/com/example/SomePlugin.java"),
                "class SomePlugin { Object createContentReader() { return new PluginReader(); } }");
        test(repository, "morpheus-provider-plugin", "SomePluginTest", "var snapshot = verify(new SomePlugin(), w); "
                + "ProviderPluginContractAssertions.verifyRead(snapshot, w, id);");
        reader(repository, "morpheus-provider-untested", "UntestedReader",
                "ProviderProjectRoot.locator(request.workspaceRoot())", "");
        test(repository, "morpheus-provider-untested", "UntestedReaderTest",
                "var content = new UntestedReader().read(r); assertTrue(content.isPresent());");
        reader(repository, "morpheus-provider-elsewhere", "ElsewhereReader",
                "ProviderProjectRoot.locator(request.workspaceRoot())", "");
        test(repository, "morpheus-provider-untested", "ElsewhereReaderTest", "var content = new ElsewhereReader()"
                + ".read(r); assertEquals(ProviderProjectRoot.locator(w), content.project().rootLocator());");
        reader(repository, "morpheus-provider-mentioned", "MentionedReader",
                "ProviderProjectRoot.locator(request.workspaceRoot())", "");
        test(repository, "morpheus-provider-mentioned", "MentionedReaderTest", "new MentionedReader(); "
                + "// .rootLocator() is never read, ProviderProjectRoot.locator( is never called\n");
        reader(repository, "morpheus-provider-unexpected", "UnexpectedReader",
                "ProviderProjectRoot.locator(request.workspaceRoot())", "");
        test(repository, "morpheus-provider-unexpected", "UnexpectedReaderTest",
                "var content = new UnexpectedReader().read(r); assertNotNull(content.project().rootLocator());");

        assertEquals(List.of(
                        "morpheus-provider-elsewhere/src/main/java/com/example/ElsewhereReader.java",
                        "morpheus-provider-mentioned/src/main/java/com/example/MentionedReader.java",
                        "morpheus-provider-unexpected/src/main/java/com/example/UnexpectedReader.java",
                        "morpheus-provider-untested/src/main/java/com/example/UntestedReader.java"),
                untestedReaders(repository, Scan.of(repository)));
    }

    private static ArchRule projectRootRule() {
        return classes()
                .that(DescribedPredicate.describe("construct a ProjectSpecification or reference its constructor",
                        ProviderProjectRootArchitectureTest::constructsAProjectSpecification))
                .should(obtainTheRootBesideEachConstruction())
                .because("a reader publishes the workspace root it received as project root, and "
                        + "ProviderProjectRoot is the only place that root is spelled; a second spelling "
                        + "is how the structured-markdown reader came to publish a file");
    }

    private static List<JavaAccess<?>> constructionsFrom(JavaClass javaClass) {
        List<JavaAccess<?>> constructions = new ArrayList<>();
        javaClass.getConstructorCallsFromSelf().stream()
                .filter(call -> call.getTargetOwner().isEquivalentTo(ProjectSpecification.class))
                .forEach(constructions::add);
        javaClass.getConstructorReferencesFromSelf().stream()
                .filter(reference -> reference.getTargetOwner().isEquivalentTo(ProjectSpecification.class))
                .forEach(constructions::add);
        return constructions;
    }

    private static boolean constructsAProjectSpecification(JavaClass javaClass) {
        return !constructionsFrom(javaClass).isEmpty();
    }

    private static boolean isSinglePoint(JavaMethodCall call) {
        return call.getTargetOwner().isEquivalentTo(ProviderProjectRoot.class) && call.getName().equals("locator");
    }

    private static ArchCondition<JavaClass> obtainTheRootBesideEachConstruction() {
        return new ArchCondition<>("call ProviderProjectRoot.locator in each code unit that constructs a "
                + "ProjectSpecification or references its constructor, at least once per construction") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Map<String, JavaCodeUnit> units = new TreeMap<>();
                Map<String, Integer> constructions = new TreeMap<>();
                for (JavaAccess<?> construction : constructionsFrom(javaClass)) {
                    units.put(construction.getOrigin().getFullName(), construction.getOrigin());
                    constructions.merge(construction.getOrigin().getFullName(), 1, Integer::sum);
                }
                for (Map.Entry<String, JavaCodeUnit> unit : units.entrySet()) {
                    long singlePoints = unit.getValue().getMethodCallsFromSelf().stream()
                            .filter(ProviderProjectRootArchitectureTest::isSinglePoint)
                            .count();
                    int constructed = constructions.get(unit.getKey());
                    events.add(new SimpleConditionEvent(javaClass, singlePoints >= constructed,
                            unit.getKey() + " constructs " + constructed + " ProjectSpecification and calls "
                                    + "ProviderProjectRoot.locator " + singlePoints + " time(s)"));
                }
            }
        };
    }

    private static String sourceFileOf(JavaClass javaClass) {
        String fileName = javaClass.getSource()
                .flatMap(Source::getFileName)
                .orElseThrow(() -> new IllegalStateException(javaClass.getName() + " carries no source file name"));
        return javaClass.getPackageName().replace('.', '/') + "/" + fileName;
    }

    /**
     * A reader source file is tested when a test of its own module constructs it, mentions {@code rootLocator()} and
     * spells an expected root ({@code ProviderProjectRoot.locator(} or {@code SourceLocator.file(}), or hands a
     * plugin whose source constructs it to the published contract, which compares the published root with the
     * received one ({@code ProviderPluginContractAssertionsTest} proves that comparison refuses a file). Comments are
     * removed first, so a mention in a comment does not count; a mention in code is all that is checked.
     */
    private static List<String> untestedReaders(Path repository, Scan scan) throws IOException {
        List<String> untested = new ArrayList<>();
        for (Construction construction : scan.constructions()) {
            Path module = repository.resolve(construction.module());
            String reader = simpleName(construction.source());
            Set<String> creators = new TreeSet<>();
            for (Path source : javaSources(module.resolve("src/main/java"))) {
                if (readSource(source).contains("new " + reader + "(")) {
                    creators.add(simpleName(source));
                }
            }
            boolean tested = false;
            for (Path test : javaSources(module.resolve("src/test/java"))) {
                String text = readSource(test);
                boolean direct = text.contains("new " + reader + "(") && text.contains(PUBLISHED_ROOT)
                        && EXPECTED_ROOTS.stream().anyMatch(text::contains);
                boolean contract = text.contains(CONTRACT_READ)
                        && creators.stream().anyMatch(creator -> text.contains("new " + creator + "("));
                tested |= direct || contract;
            }
            if (!tested && !untested.contains(construction.site())) {
                untested.add(construction.site());
            }
        }
        return untested;
    }

    private static String simpleName(Path source) {
        return source.getFileName().toString().replaceFirst("\\.java$", "");
    }

    private record Construction(String module, Path source, String site, String rootArgument) {
    }

    private record Scan(List<Construction> constructions, List<String> violations) {
        static Scan of(Path repository) throws IOException {
            List<Construction> constructions = new ArrayList<>();
            List<String> violations = new ArrayList<>();
            try (Stream<Path> modules = Files.list(repository)) {
                for (Path module : modules
                        .filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith("morpheus-provider-"))
                        .sorted()
                        .toList()) {
                    for (Path source : javaSources(module.resolve("src/main/java"))) {
                        String text = readSource(source);
                        String site = repository.relativize(source).toString().replace('\\', '/');
                        List<String> rootArguments;
                        try {
                            rootArguments = rootArguments(text);
                        } catch (IllegalStateException failure) {
                            throw new IllegalStateException(site + ": " + failure.getMessage(), failure);
                        }
                        for (String rootArgument : rootArguments) {
                            constructions.add(new Construction(
                                    module.getFileName().toString(), source, site, rootArgument));
                            String refusal = refusalOf(rootArgument, text);
                            if (refusal != null) {
                                violations.add(site + " publishes project root '" + rootArgument + "': " + refusal);
                            }
                        }
                    }
                }
            }
            return new Scan(constructions, violations);
        }

        Map<String, List<String>> sitesByModule() {
            Map<String, List<String>> sites = new TreeMap<>();
            for (Construction construction : constructions) {
                sites.computeIfAbsent(construction.module(), ignored -> new ArrayList<>()).add(construction.site());
            }
            return sites;
        }

        List<String> unseen(List<String> requiredModules) {
            List<String> unseen = new ArrayList<>(requiredModules);
            unseen.removeAll(sitesByModule().keySet());
            return unseen;
        }

        Set<String> constructingSources() {
            String sourceRoot = "/src/main/java/";
            Set<String> sources = new TreeSet<>();
            for (Construction construction : constructions) {
                String site = construction.site();
                sources.add(site.substring(site.indexOf(sourceRoot) + sourceRoot.length()));
            }
            return sources;
        }
    }

    private static String refusalOf(String argument, String source) {
        if (argument.equals(CONSTRUCTOR_REFERENCE_ARGUMENT)) {
            return "a constructor reference hides the root argument from this scan; construct it inline";
        }
        if (!argument.startsWith(SINGLE_POINT)) {
            return "it is not obtained from " + SINGLE_POINT + "...)";
        }
        int close = closingParenthesis(argument, SINGLE_POINT.length() - 1);
        if (close < 0) {
            return "the parenthesis opened by " + SINGLE_POINT + " is never closed";
        }
        if (close != argument.length() - 1) {
            return "'" + argument.substring(close + 1) + "' follows the single point, whose result must be the whole "
                    + "argument";
        }
        return receivedRootRefusal(
                argument.substring(SINGLE_POINT.length(), close).strip(), source, true, new HashSet<>());
    }

    private static String receivedRootRefusal(
            String expression, String source, boolean insideTheSinglePoint, Set<String> followed) {
        for (ReceivedRootForm form : ReceivedRootForm.values()) {
            if (insideTheSinglePoint && !form.insideTheSinglePoint) {
                continue;
            }
            Verdict verdict = form.judge(expression, source, followed);
            if (verdict.isThisForm()) {
                return verdict.refusal();
            }
        }
        return "'" + expression + "' is none of the admitted forms " + ReceivedRootForm.admitted(insideTheSinglePoint);
    }

    private static String nameRefusal(String name, String source, Set<String> followed) {
        if (!followed.add("root " + name)) {
            return null;
        }
        List<String> bindings = bindingsOf(name, source);
        if (bindings.isEmpty() && !declaresParameter(name, source)) {
            return "'" + name + "' is neither a parameter nor bound in this file, so its value is not visible here";
        }
        for (String binding : bindings) {
            String refusal = receivedRootRefusal(binding, source, false, followed);
            if (refusal != null) {
                return "'" + name + "' is bound to '" + binding + "': " + refusal;
            }
        }
        return null;
    }

    private static String receiverRefusal(String receiver, String source, Set<String> followed) {
        Matcher named = NAME_PATTERN.matcher(receiver);
        if (!named.matches()) {
            return "the request '" + receiver + "' is not a name";
        }
        String name = named.group(1);
        if (!followed.add("request " + name)) {
            return null;
        }
        List<String> bindings = bindingsOf(name, source);
        if (bindings.isEmpty() && !declaresParameter(name, source)) {
            return "the request '" + name + "' is neither a parameter nor bound in this file";
        }
        for (String binding : bindings) {
            if (!NAME_PATTERN.matcher(binding).matches()) {
                return "the request '" + name + "' is bound to '" + binding + "', and a request built here may carry "
                        + "another root than the one received";
            }
            String refusal = receiverRefusal(binding, source, followed);
            if (refusal != null) {
                return refusal;
            }
        }
        return null;
    }

    /** Whether the file declares {@code name} as a typed parameter, of a method, a constructor or a lambda. */
    private static boolean declaresParameter(String name, String source) {
        return Pattern.compile("[(,]\\s*(?:final\\s+)?(?:@[A-Za-z0-9_$.]+\\s+)*" + TYPE + "(?:\\s*\\.\\.\\.)?\\s+"
                        + Pattern.quote(name) + "\\s*[,)]")
                .matcher(source).find();
    }

    /**
     * Every value the file binds to {@code name}: the initializer of a declaration or an assignment, parenthesized
     * or not, including one to a field of that name. A for-each variable, a pattern variable of {@code instanceof}
     * or of a {@code case}, and a record-pattern component are bindings too, and are never admitted.
     */
    private static List<String> bindingsOf(String name, String source) {
        String quoted = Pattern.quote(name);
        List<String> bindings = new ArrayList<>();
        Matcher assignment = Pattern.compile("(?<![A-Za-z0-9_$])" + quoted + "(?:\\s*\\))*\\s*=(?!=)").matcher(source);
        while (assignment.find()) {
            bindings.add(initializerFrom(source, assignment.end()));
        }
        addEach(bindings, "<element of a for-each loop>",
                Pattern.compile("\\bfor\\s*\\([^;)]*?(?<![A-Za-z0-9_$])" + quoted + "\\s*:(?!:)"), source);
        addEach(bindings, "<pattern variable of instanceof>",
                Pattern.compile("\\binstanceof\\s+(?:final\\s+)?" + TYPE + "\\s+" + quoted + "\\b"), source);
        addEach(bindings, "<pattern variable of a case>",
                Pattern.compile("\\bcase\\s+" + TYPE + "\\s+" + quoted + "\\s*(?:->|:|,|when\\b)"), source);
        Pattern component = Pattern.compile("[A-Za-z0-9_$>\\]]\\s+" + quoted + "\\s*[,)]");
        Matcher recordPattern = RECORD_PATTERN.matcher(source);
        while (recordPattern.find()) {
            int close = closingParenthesis(source, recordPattern.end() - 1);
            String components = source.substring(recordPattern.end() - 1, close < 0 ? source.length() : close + 1);
            if (component.matcher(components).find()) {
                bindings.add("<component of a record pattern>");
            }
        }
        return bindings;
    }

    private static void addEach(List<String> bindings, String binding, Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            bindings.add(binding);
        }
    }

    private static String initializerFrom(String text, int start) {
        int depth = 0;
        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                index = endOfLiteral(text, index);
            } else if (character == '(' || character == '{' || character == '[') {
                depth++;
            } else if (character == ')' || character == '}' || character == ']') {
                if (depth == 0) {
                    return text.substring(start, index).strip();
                }
                depth--;
            } else if ((character == ';' || character == ',') && depth == 0) {
                return text.substring(start, index).strip();
            }
        }
        return text.substring(start).strip();
    }

    /** The third argument of every construction in order, and a marker for every constructor reference. */
    private static List<String> rootArguments(String text) {
        List<String> arguments = new ArrayList<>();
        Matcher construction = CONSTRUCTION.matcher(text);
        while (construction.find()) {
            List<String> split = topLevelArguments(text, construction.end());
            arguments.add(split.size() == 3 ? split.get(2) : "<" + split.size() + " arguments>");
        }
        Matcher reference = CONSTRUCTOR_REFERENCE.matcher(text);
        while (reference.find()) {
            arguments.add(CONSTRUCTOR_REFERENCE_ARGUMENT);
        }
        return arguments;
    }

    private static List<String> topLevelArguments(String text, int afterOpeningParenthesis) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int argumentStart = afterOpeningParenthesis;
        for (int index = afterOpeningParenthesis; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                index = endOfLiteral(text, index);
            } else if (character == '(') {
                depth++;
            } else if (character == ')') {
                if (depth == 0) {
                    arguments.add(text.substring(argumentStart, index).strip());
                    return arguments;
                }
                depth--;
            } else if (character == ',' && depth == 0) {
                arguments.add(text.substring(argumentStart, index).strip());
                argumentStart = index + 1;
            }
        }
        throw new IllegalStateException("unterminated argument list of a ProjectSpecification construction");
    }

    /** The index of the parenthesis closing the one at {@code openingParenthesis}, or -1; literals are skipped. */
    private static int closingParenthesis(String text, int openingParenthesis) {
        int depth = 0;
        for (int index = openingParenthesis; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                index = endOfLiteral(text, index);
            } else if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** The index of the last quote of the string, text block or character literal opened at {@code start}. */
    private static int endOfLiteral(String text, int start) {
        char quote = text.charAt(start);
        if (quote == '"' && text.startsWith("\"\"\"", start)) {
            int end = text.indexOf("\"\"\"", start + 3);
            return end < 0 ? text.length() - 1 : end + 2;
        }
        for (int index = start + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\\') {
                index++;
            } else if (character == quote) {
                return index;
            }
        }
        return text.length() - 1;
    }

    /**
     * A source as the compiler reads it, for the purposes of this scan: unicode escapes translated first, as javac
     * does, then every comment replaced by a space, literals left intact.
     */
    private static String readSource(Path source) throws IOException {
        return withoutComments(withUnicodeEscapesTranslated(Files.readString(source)));
    }

    private static String withUnicodeEscapesTranslated(String raw) {
        Matcher escape = UNICODE_ESCAPE.matcher(raw);
        StringBuilder translated = new StringBuilder();
        while (escape.find()) {
            escape.appendReplacement(translated, Matcher.quoteReplacement(
                    escape.group(1) + (char) Integer.parseInt(escape.group(2), 16)));
        }
        escape.appendTail(translated);
        return translated.toString();
    }

    private static String withoutComments(String text) {
        StringBuilder code = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                int end = Math.min(endOfLiteral(text, index), text.length() - 1);
                code.append(text, index, end + 1);
                index = end + 1;
            } else if (text.startsWith("//", index)) {
                int end = text.indexOf('\n', index);
                code.append(' ');
                index = end < 0 ? text.length() : end;
            } else if (text.startsWith("/*", index)) {
                int end = text.indexOf("*/", index + 2);
                code.append(' ');
                index = end < 0 ? text.length() : end + 2;
            } else {
                code.append(character);
                index++;
            }
        }
        return code.toString();
    }

    private static void reader(Path repository, String module, String name, String rootArgument, String binding)
            throws IOException {
        Path sources = Files.createDirectories(repository.resolve(module).resolve("src/main/java/com/example"));
        Files.writeString(sources.resolve(name + ".java"), """
                package com.example;

                final class %s {
                    ProjectSpecification read(ProviderReadRequest request, Path workspaceRoot) {
                        %s
                        return new ProjectSpecification(
                                request.projectId(), "name", %s);
                    }
                }
                """.formatted(name, binding, rootArgument));
    }

    private static void test(Path repository, String module, String name, String body) throws IOException {
        Path tests = Files.createDirectories(repository.resolve(module).resolve("src/test/java/com/example"));
        Files.writeString(tests.resolve(name + ".java"), "class " + name + " { @Test void reads() { " + body + " } }");
    }

    private static List<Path> javaSources(Path sources) throws IOException {
        if (!Files.isDirectory(sources)) {
            return List.of();
        }
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

    private interface Maker {
        ProjectSpecification make(ProjectSpecificationId id, String displayName, SourceLocator rootLocator);
    }

    private static final class OneRootForTwoConstructions {
        List<ProjectSpecification> both(Path workspace) {
            return List.of(
                    new ProjectSpecification(ProjectSpecificationId.generate(), "first",
                            ProviderProjectRoot.locator(workspace)),
                    new ProjectSpecification(ProjectSpecificationId.generate(), "second",
                            Objects.requireNonNull(null)));
        }
    }

    private static final class RootObtainedInAnotherMethod {
        Object root(Path workspace) {
            return ProviderProjectRoot.locator(workspace);
        }

        ProjectSpecification construct(Path workspace) {
            return new ProjectSpecification(ProjectSpecificationId.generate(), "name", Objects.requireNonNull(null));
        }
    }

    private static final class ConstructorReferenceWithoutTheSinglePoint {
        Maker maker() {
            return ProjectSpecification::new;
        }
    }

    private static final class RootObtainedBesideEachConstruction {
        List<ProjectSpecification> both(Path workspace) {
            return List.of(
                    new ProjectSpecification(ProjectSpecificationId.generate(), "first",
                            ProviderProjectRoot.locator(workspace)),
                    new ProjectSpecification(ProjectSpecificationId.generate(), "second",
                            ProviderProjectRoot.locator(workspace)));
        }
    }
}
