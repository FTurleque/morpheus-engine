package com.morpheus.architecture;

import com.morpheus.application.product.ProductMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the AI governance surfaces -- {@code .claude/CLAUDE.md}, {@code .github/AI_GOVERNANCE.md}, the rules,
 * commands, agents and skills -- against stating a perishable fact: a SQLite schema version, an ADR or module
 * total. A fact that has a source a test can read is compared to it instead: a version written as a root POM
 * property, a ratchet copied under its key, the current product version stated in prose. The surfaces are declared, and a declared surface that is missing fails
 * instead of shrinking the scan.
 */
class GovernanceSurfaceCoherenceTest {
    private static final Pattern SCHEMA_VERSION_DECLARATION =
            Pattern.compile("SUPPORTED_SCHEMA_VERSION\\s*=\\s*(\\d+)");
    private static final Pattern SCHEMA_VERSION_LITERAL =
            Pattern.compile("SUPPORTED_SCHEMA_VERSION\\s*=\\s*\\d+");
    /**
     * A total that grows with the repository, stated on a page that describes the current contract:
     * "98 fichiers" ADR, "17 modules". The count belongs in a glob at answer time, not on the page.
     */
    private static final Pattern ADR_OR_MODULE_TOTAL = Pattern.compile(
            "(?i)\\b\\d{1,4}\\s+(?:fichiers?|ADRs?|modules?)\\b");
    /**
     * A schema version stated in prose on an active surface: "schema 15", "schéma V017", or the constant
     * followed by its value in words rather than by an assignment. Historical pages are never scanned with it.
     */
    private static final Pattern SCHEMA_VERSION_PROSE = Pattern.compile(
            "(?i)(?:sch[eé]ma|schema)\\s+(?:supported\\s+|support[eé]e?\\s+)?V?\\d+"
                    + "|SUPPORTED_SCHEMA_VERSION[^\\n]{0,40}?\\bV?\\d+");
    /**
     * The AI governance surfaces the checks below scan. {@code .claude/CLAUDE.md} is the always-loaded entry point,
     * so a stale fact on it propagates furthest; {@code .github/AI_GOVERNANCE.md} maps what is loaded and by what.
     */
    private static final List<String> GOVERNANCE_PAGES = List.of(".claude/CLAUDE.md", ".github/AI_GOVERNANCE.md");
    private static final List<String> GOVERNANCE_DIRECTORIES =
            List.of(".claude/commands", ".claude/agents", ".claude/rules");
    /**
     * Skills are the one AI surface that does not live one file deep: Claude Code loads
     * {@code .claude/skills/<name>/SKILL.md} and ignores anything else under that root. Listing the directory the
     * way the three above are listed would therefore have found no Markdown at all and reported the whole surface
     * as absent, so the collection descends one level -- and refuses a skill written in a shape that is never
     * loaded, which would otherwise be governance nobody applies and nobody scans.
     */
    private static final String GOVERNANCE_SKILL_DIRECTORY = ".claude/skills";
    private static final String SKILL_FILE = "SKILL.md";
    /** A version written as the root POM writes it: {@code <jacoco.version>0.8.16</jacoco.version>}. */
    private static final Pattern VERSION_PROPERTY_ELEMENT =
            Pattern.compile("<([A-Za-z0-9_-][A-Za-z0-9_.-]*\\.version)>([^<\\s]+)</\\1>");
    /** A version written as an assignment of the property: {@code jacoco.version=0.8.16}. */
    private static final Pattern VERSION_PROPERTY_ASSIGNMENT =
            Pattern.compile("(?<![\\w.{$-])([A-Za-z0-9_-][A-Za-z0-9_.-]*\\.version)=([^\\s`,;)<]+)");
    /** A version written as two code spans joined by an equals sign: {@code `jacoco.version` = `0.8.16`}. */
    private static final Pattern VERSION_PROPERTY_SPAN_EQUALITY =
            Pattern.compile("`([A-Za-z0-9_-][A-Za-z0-9_.-]*\\.version)`[ \\t]*=[ \\t]*`([^`\\s]+)`");
    /**
     * A table cell that is exactly the code span of a property name. In a table row, a later cell that is exactly the
     * code span of a value ({@link #VERSION_VALUE_CELL}) is the version written for it:
     * {@code | `jacoco.version` | `0.8.16` | ... |}. A row that names the property and describes it holds no version.
     */
    private static final Pattern VERSION_PROPERTY_NAME_CELL =
            Pattern.compile("`([A-Za-z0-9_-][A-Za-z0-9_.-]*\\.version)`");
    private static final Pattern VERSION_VALUE_CELL = Pattern.compile("`(\\d[^`\\s]*)`");
    /**
     * A fence as CommonMark reads one: indented by three spaces at most, and for backticks with no backtick after the
     * run -- {@code ```` ``` ````} at the start of a line is a code span, not a fence.
     */
    private static final Pattern CODE_FENCE = Pattern.compile("^ {0,3}(`{3,}(?=[^`]*$)|~{3,})");
    /**
     * The folders of {@code docs/} that hold dated records, as {@code .claude/rules/documentation.md} names them: a
     * record cites the versions of its date and is never rewritten, so it is not compared to the POM.
     */
    private static final List<String> DATED_RECORD_DIRECTORIES = List.of("docs/project/audits/",
            "docs/project/validation/", "docs/project/roadmap/", "docs/user/release-notes/");
    private static final Pattern POM_PROPERTY = Pattern.compile("<([A-Za-z0-9_.-]+)>([^<]*)</\\1>");
    /**
     * A statement of the <em>current</em> product version: one of five markers that only ever introduce the version
     * the reactor builds -- the artifact coordinates {@code `...:morpheus-engine`}, {@code baseline},
     * {@code actuellement}, {@code version courante}, {@code version produit} -- followed, at most across spaces and
     * an opening parenthesis, by a version set in bold or in a code span: {@code **1.2.2**}, {@code (`1.2.2`)}.
     *
     * <p>A version without that marker and that emphasis is a dated record and is not read: "livré dans 1.2.0",
     * "la 1.2.1 publiée" name the release a past fact belongs to and stay true when the reactor moves on. A
     * dependency version never follows one of the markers, and one written as a POM property is compared by
     * {@link #everyVersionAGovernancePageWritesAsAPomPropertyEqualsThePom}.</p>
     */
    private static final Pattern CURRENT_PRODUCT_VERSION_CLAIM = Pattern.compile(
            "(?i)(?:morpheus-engine`|\\bbaseline|\\bactuellement|\\bversion courante|\\bversion produit)"
                    + "[ \\t]*\\(?[ \\t]*(\\*\\*|`)(\\d+\\.\\d+\\.\\d+(?:-[A-Za-z0-9.]+)?)\\1");
    private static final Pattern ROOT_PROJECT_VERSION =
            Pattern.compile("<artifactId>morpheus-engine</artifactId>\\s*<version>([^<\\s]+)</version>");
    private static final String PRODUCT_VERSION_KEY = "project.version";
    private static final String RATCHETS = "config/m21-quality-ratchets.properties";
    /**
     * A table row whose first cell is a ratchet key in a code span; the first number of the next cell is the copy.
     * The keys of the ratchet file all end in {@code Minimum} or {@code Maximum}, so a row naming a retired key is
     * still read, and refused.
     */
    private static final Pattern RATCHET_ROW = Pattern.compile(
            "(?m)^\\|\\s*`([a-z][A-Za-z]*(?:Minimum|Maximum))`\\s*\\|\\s*([0-9]+(?:\\.[0-9]+)?)");

    @Test
    void activeGovernanceDocumentationNeverPinsTheSqliteSchemaVersion() throws Exception {
        Path root = RepositoryRoot.locate();
        String manager = Files.readString(root.resolve(
                "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/runtime/SqliteSchemaManager.java"));
        Matcher declaration = SCHEMA_VERSION_DECLARATION.matcher(manager);
        assertTrue(declaration.find(), "SqliteSchemaManager must declare SUPPORTED_SCHEMA_VERSION");

        String maintenance = Files.readString(root.resolve(
                "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/runtime/SqliteServerMaintenance.java"));
        assertTrue(maintenance.contains("SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION"),
                "maintenance must consume the declared constant rather than restate the version");

        // Active governance surfaces describe the *current* contract, so a literal here silently rots.
        // Historical snapshots under docs/project/validation and docs/project/roadmap legitimately record past versions.
        for (Path page : activeGovernanceSurfaces(root)) {
            String content = Files.readString(page);
            Matcher pinned = SCHEMA_VERSION_LITERAL.matcher(content);
            assertFalse(pinned.find(),
                    () -> root.relativize(page) + " pins SUPPORTED_SCHEMA_VERSION to a literal; derive it from "
                            + "SqliteSchemaManager instead so the check cannot describe a stale contract");

            // The assignment form is only one way to state the version. An active surface that *describes* the
            // current schema in prose - "schema 15" in a report template, "constate a 17" in a rule - rots the
            // same way while sailing past a check that only looks for the constant.
            Matcher prose = SCHEMA_VERSION_PROSE.matcher(content);
            assertFalse(prose.find(),
                    () -> root.relativize(page) + " states a current SQLite schema version in prose ("
                            + describeFirstMatch(SCHEMA_VERSION_PROSE, content)
                            + "); active surfaces must read SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION instead");
        }
    }

    /**
     * The surface list refuses a missing surface rather than scan less: every check iterating it would otherwise
     * keep passing on a narrower population. Proven on a copy, one surface removed at a time.
     */
    @Test
    void theGovernanceSurfaceListRefusesAMissingSurfaceInsteadOfShrinking(@TempDir Path corpus) throws Exception {
        for (String page : GOVERNANCE_PAGES) {
            Files.createDirectories(corpus.resolve(page).getParent());
            Files.writeString(corpus.resolve(page), "# page\n");
        }
        for (String directory : GOVERNANCE_DIRECTORIES) {
            Files.createDirectories(corpus.resolve(directory));
            Files.writeString(corpus.resolve(directory).resolve("rule.md"), "# rule\n");
        }
        Path skill = corpus.resolve(GOVERNANCE_SKILL_DIRECTORY).resolve("a-skill");
        Files.createDirectories(skill);
        Files.writeString(skill.resolve(SKILL_FILE), "# skill\n");
        assertEquals(GOVERNANCE_PAGES.size() + GOVERNANCE_DIRECTORIES.size() + 1,
                activeGovernanceSurfaces(corpus).size(), "a complete corpus must be scanned whole");

        for (String page : GOVERNANCE_PAGES) {
            Path file = corpus.resolve(page);
            Files.delete(file);
            AssertionError refusal = assertThrows(AssertionError.class, () -> activeGovernanceSurfaces(corpus));
            assertTrue(refusal.getMessage().contains(page), () -> "the refusal must name " + page);
            Files.writeString(file, "# page\n");
        }
        for (String directory : GOVERNANCE_DIRECTORIES) {
            Path rule = corpus.resolve(directory).resolve("rule.md");
            Files.delete(rule);
            AssertionError refusal = assertThrows(AssertionError.class, () -> activeGovernanceSurfaces(corpus));
            assertTrue(refusal.getMessage().contains(directory), () -> "the refusal must name " + directory);
            Files.writeString(rule, "# rule\n");
        }

        Files.delete(skill.resolve(SKILL_FILE));
        AssertionError emptied = assertThrows(AssertionError.class, () -> activeGovernanceSurfaces(corpus));
        assertTrue(emptied.getMessage().contains("a-skill/" + SKILL_FILE),
                () -> "the refusal must name the skill folder that lost its " + SKILL_FILE);
        Files.writeString(skill.resolve(SKILL_FILE), "# skill\n");

        // A skill written as a loose file is never loaded by Claude Code, so it would be governance that applies
        // to nothing while escaping every check above. Refused rather than ignored.
        Path loose = corpus.resolve(GOVERNANCE_SKILL_DIRECTORY).resolve("written-as-a-loose-file.md");
        Files.writeString(loose, "# not a skill\n");
        AssertionError misplaced = assertThrows(AssertionError.class, () -> activeGovernanceSurfaces(corpus));
        assertTrue(misplaced.getMessage().contains("written-as-a-loose-file.md"),
                () -> "the refusal must name the file written outside a skill folder");
        Files.delete(loose);

        assertEquals(GOVERNANCE_PAGES.size() + GOVERNANCE_DIRECTORIES.size() + 1,
                activeGovernanceSurfaces(corpus).size(), "the corpus must be whole again once each shape is fixed");
    }

    /**
     * Active governance surfaces must not state a total that changes when the repository grows.
     *
     * <p>{@code rules/meta.md} says never to recopy a perishable total, yet the ADR rule recopied one, and it had
     * rotted into an ambiguity: "98 fichiers" counted 97 numbered ADRs plus the directory's own README, so a
     * reader following it would have cited 98 ADRs. The instruction to count is what belongs on these pages.</p>
     */
    @Test
    void activeGovernanceSurfacesDoNotRestateAPerishableAdrOrModuleTotal() throws Exception {
        Path root = RepositoryRoot.locate();
        long numberedAdrs;
        try (var files = Files.list(root.resolve("docs/architecture/adr"))) {
            numberedAdrs = files.filter(path -> path.getFileName().toString().matches("[A-Z]+-\\d{4}-.*\\.md")).count();
        }
        assertTrue(numberedAdrs > 0, "the ADR directory must contain numbered decision records");

        for (Path page : activeGovernanceSurfaces(root)) {
            String content = Files.readString(page);
            assertFalse(ADR_OR_MODULE_TOTAL.matcher(content).find(),
                    () -> root.relativize(page) + " restates a perishable total (\""
                            + describeFirstMatch(ADR_OR_MODULE_TOTAL, content)
                            + "\"); count docs/architecture/adr/*-[0-9][0-9][0-9][0-9]-*.md or pom.xml <module> entries instead");
        }
    }

    /**
     * The developer platform guide describes the current contract, so it must not pin the runtime's maximum
     * schema version. It said the maximum was V016 and that anything above 16 is refused, while the manager had
     * moved to 17 -- a reader following it would have concluded a valid database was rejected.
     */
    @Test
    void theRemotePlatformGuideDerivesTheSchemaCeilingFromTheDeclaringConstant() throws Exception {
        Path root = RepositoryRoot.locate();
        String guide = Files.readString(root.resolve("docs/developer/REMOTE_SERVER_PLATFORM.md"));

        int section = guide.indexOf("## Schéma SQLite");
        assertTrue(section >= 0, "the guide must keep its SQLite schema section");
        String schemaSection = guide.substring(section, Math.min(guide.length(), section + 2000));

        assertTrue(schemaSection.contains("SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION"),
                "the documented ceiling must point at the constant that declares it");
        assertFalse(Pattern.compile("version maximale supportée par le runtime\\s*:\\s*\\*\\*V?\\d+")
                        .matcher(schemaSection).find(),
                "the guide must not restate the runtime's maximum schema version as a literal");
    }

    /**
     * The audit command must tell its reader where the normative value lives, not just avoid restating it.
     * A template with an empty placeholder and no instruction invites the next reader to fill in a remembered
     * number, which is how the stale "schema 15" line survived the previous correction.
     */
    @Test
    void theSecurityAuditCommandDerivesTheSqliteSchemaVersionFromTheDeclaringConstant() throws Exception {
        Path root = RepositoryRoot.locate();
        String command = Files.readString(root.resolve(".claude/commands/security-audit.md"));

        assertTrue(command.contains("SUPPORTED_SCHEMA_VERSION"),
                "the audit command must point at the declaring constant");
        assertTrue(command.contains("SqliteSchemaManager.java"),
                "the audit command must name the file that declares the normative version");
        assertTrue(command.contains("SqliteSchemaManager>"),
                "the audit report template must carry a placeholder rather than a version number");
    }

    /**
     * A version a governance page writes in the shape of a root POM property equals that property's value.
     *
     * <p>{@code .claude/rules/build.md} announced {@code jacoco.version=0.8.15} for a whole revision after the POM
     * moved to 0.8.16: a bump touches {@code pom.xml} alone, and the page every agent loads kept the old value. No
     * list of properties is kept here: every occurrence of either shape is compared, so a page that copies one more
     * version is held without a change to this test.</p>
     */
    @Test
    void everyVersionAGovernancePageWritesAsAPomPropertyEqualsThePom() throws Exception {
        Path root = RepositoryRoot.locate();
        List<StatedValue> stated = new ArrayList<>();
        for (Path page : activeGovernanceSurfaces(root)) {
            stated.addAll(statedVersions(relative(root, page), Files.readString(page)));
        }
        requireAgreement(stated, rootPomProperties(Files.readString(root.resolve("pom.xml"))), "pom.xml",
                "a governance page writes no version in the shape of a root POM property: the scan reads nothing");
    }

    /**
     * A version a living page of {@code docs/} writes in the shape of a property the root POM declares equals that
     * property's value.
     *
     * <p>{@code docs/developer/CODE_AUDIT.md} and {@code SPOTBUGS.md} gave SpotBugs and PIT versions that the POM had
     * left behind: the governance scan above never read {@code docs/}. The pages are not listed: every Markdown page
     * Git tracks under {@code docs/}, outside the dated records, is read. Unlike a governance page, a living page may
     * write no version at all -- {@code .claude/rules/build.md} asks it to name the property -- so an empty scan is
     * refused on the pages it reads, never on the versions it finds.</p>
     *
     * <p>Planted violation, 11/11/2026: the row {@code | `spotbugs.maven.plugin.version` | `4.10.4.1` |} put back in
     * {@code docs/developer/CODE_AUDIT.md} failed this method naming the page, the property, {@code 4.10.4.1} and the
     * POM's {@code 4.10.5.0}; reverted.</p>
     */
    @Test
    void everyVersionALivingDocumentationPageWritesAsAPomPropertyEqualsThePom() throws Exception {
        Path root = RepositoryRoot.locate();
        Set<String> tracked = Set.copyOf(TrackedFiles.list(root));
        assertFalse(livingDocumentationPages(root, tracked).isEmpty(),
                "no living page of docs/ is read: the version scan would pass on nothing");
        Map<String, String> pom = rootPomProperties(Files.readString(root.resolve("pom.xml")));
        refuseDisagreements(statedLivingDocumentationVersions(root, tracked, pom), pom, "pom.xml");
    }

    @Test
    void onlyALivingTrackedPageOfDocsIsComparedAndOnlyOnAPropertyThePomDeclares(@TempDir Path corpus)
            throws IOException {
        String stale = "`spotbugs.maven.plugin.version=4.10.4.1`\n";
        Map<String, String> pages = new TreeMap<>(Map.of(
                "docs/developer/LIVING.md", stale,
                "docs/project/audits/AUDIT_2026-10-19.md", stale,
                "docs/user/release-notes/RELEASE_NOTES_1.2.1.md", stale,
                "docs/developer/TRANSCRIPT.md", "```properties\n" + stale + "```\n",
                "docs/architecture/risks/register.md", "déclarent `data.version=5.6`.\n",
                "docs/project/rapport-supervisor/rapport.md", stale));
        for (Map.Entry<String, String> page : pages.entrySet()) {
            Path file = corpus.resolve(page.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, page.getValue());
        }
        Set<String> tracked = pages.keySet().stream()
                .filter(page -> !page.startsWith("docs/project/rapport-supervisor/"))
                .collect(Collectors.toSet());
        Map<String, String> pom = rootPomProperties("""
                <project><properties>
                    <spotbugs.maven.plugin.version>4.10.5.0</spotbugs.maven.plugin.version>
                </properties></project>
                """);

        List<StatedValue> stated = statedLivingDocumentationVersions(corpus, tracked, pom);
        assertEquals(List.of(new StatedValue("docs/developer/LIVING.md", "spotbugs.maven.plugin.version", "4.10.4.1")),
                stated, "the dated records, the fenced block, the other artifact's key and the untracked report "
                        + "are not read");

        AssertionError refusal = assertThrows(AssertionError.class, () -> refuseDisagreements(stated, pom, "pom.xml"));
        assertTrue(refusal.getMessage().contains(
                "docs/developer/LIVING.md spotbugs.maven.plugin.version states 4.10.4.1, pom.xml says 4.10.5.0"),
                refusal::getMessage);
        assertEquals(stale, Files.readString(corpus.resolve("docs/developer/LIVING.md")),
                "the check reads pages, it never writes them");

        assertFalse(withoutFencedBlocks("page.md", "````\n~~~\n```\n" + stale + "````\n").contains("4.10.4.1"),
                "a block closes only on a fence of its own character and at least its length");
        assertTrue(withoutFencedBlocks("page.md", "  ```` ``` ```` ouvre un bloc.\n" + stale).contains("4.10.4.1"),
                "a code span at the start of a line is not a fence");
        AssertionError unclosed = assertThrows(AssertionError.class,
                () -> withoutFencedBlocks("docs/developer/OPEN.md", "```\n" + stale));
        assertTrue(unclosed.getMessage().startsWith("docs/developer/OPEN.md opens a fenced code block that it never "
                + "closes"), unclosed::getMessage);
    }

    @Test
    void bothShapesOfAVersionAreComparedAndAPropertyThePomDoesNotDeclareIsRefused() {
        Map<String, String> pom = rootPomProperties("""
                <project><properties>
                    <jacoco.version>0.8.16</jacoco.version>
                </properties></project>
                """);
        List<StatedValue> stated = statedVersions(".claude/rules/build.md", """
                <jacoco.version>0.8.15</jacoco.version>
                `jacoco.version=0.8.14`, `${project.version}` and <version>${junit.version}</version>.
                `retired.version=1.0`.
                """);
        assertEquals(3, stated.size(), "the element, the assignment and the retired property are each read");

        AssertionError refusal = assertThrows(AssertionError.class,
                () -> requireAgreement(stated, pom, "pom.xml", "nothing read"));
        assertTrue(refusal.getMessage().contains(
                ".claude/rules/build.md jacoco.version states 0.8.15, pom.xml says 0.8.16"), refusal::getMessage);
        assertTrue(refusal.getMessage().contains(
                ".claude/rules/build.md jacoco.version states 0.8.14, pom.xml says 0.8.16"), refusal::getMessage);
        assertTrue(refusal.getMessage().contains(
                ".claude/rules/build.md retired.version states 1.0, pom.xml declares no such key"),
                refusal::getMessage);

        requireAgreement(statedVersions("page.md", "`jacoco.version=0.8.16`."), pom, "pom.xml", "nothing read");
    }

    @Test
    void aTableRowAndAnEqualityOfCodeSpansAreReadWhileATableThatNamesThePropertyIsNot() {
        Map<String, String> pom = rootPomProperties("""
                <project><properties>
                    <spotbugs.maven.plugin.version>4.10.5.0</spotbugs.maven.plugin.version>
                    <pitest.maven.plugin.version>1.30.2</pitest.maven.plugin.version>
                </properties></project>
                """);
        List<StatedValue> stated = statedVersions("docs/developer/CODE_AUDIT.md", """
                | Propriété | Version | Rôle |
                |---|---|---|
                | `pitest.maven.plugin.version` | `1.30.0` | plugin Maven PIT |
                | `spotbugs.maven.plugin.version` | `4.10.5.0` | plugin Maven SpotBugs |

                | Version épinglée | `spotbugs.maven.plugin.version` = `4.10.4.1` |

                | Propriété | Rôle |
                |---|---|
                | `spotbugs.maven.plugin.version` | plugin Maven SpotBugs |
                | `pitest.maven.plugin.version` | [`pom.xml`](../../pom.xml), `<properties>` |
                """);
        assertEquals(3, stated.size(), () -> "the two rows with a value and the equality are read, the table that "
                + "names the properties without a value is not: " + stated);

        AssertionError refusal = assertThrows(AssertionError.class,
                () -> requireAgreement(stated, pom, "pom.xml", "nothing read"));
        assertTrue(refusal.getMessage().contains(
                "docs/developer/CODE_AUDIT.md pitest.maven.plugin.version states 1.30.0, pom.xml says 1.30.2"),
                refusal::getMessage);
        assertTrue(refusal.getMessage().contains(
                "docs/developer/CODE_AUDIT.md spotbugs.maven.plugin.version states 4.10.4.1, pom.xml says 4.10.5.0"),
                refusal::getMessage);
        assertFalse(refusal.getMessage().contains("states 4.10.5.0"), "a row that agrees with the POM is not refused");
    }

    @Test
    void aVersionScanThatReadsNothingFailsInsteadOfPassing() {
        AssertionError refusal = assertThrows(AssertionError.class,
                () -> requireAgreement(statedVersions("page.md", "JaCoCo 0.8.16, `mcp-sdk` 2.1.0"),
                        Map.of("jacoco.version", "0.8.16"), "pom.xml", "the scan reads nothing"));
        assertEquals("the scan reads nothing", refusal.getMessage());
    }

    /**
     * A current product version a governance page states equals the version the root POM builds, which is the one
     * {@link ProductMetadata#version()} reports.
     *
     * <p>{@code .claude/CLAUDE.md} and {@code .claude/rules/governance.md} name the product version in prose so that
     * an agent knows which baseline it works on; a version bump touches the POM, and nothing compared these pages to
     * it. Only a statement shaped by {@link #CURRENT_PRODUCT_VERSION_CLAIM} is read: a dated record keeps the release
     * it belongs to.</p>
     */
    @Test
    void everyCurrentProductVersionAGovernancePageStatesEqualsTheRootPom() throws Exception {
        Path root = RepositoryRoot.locate();
        String pomVersion = rootProjectVersion(Files.readString(root.resolve("pom.xml")));
        if (!ProductMetadata.developmentRuntime()) {
            assertEquals(pomVersion, ProductMetadata.version(),
                    "ProductMetadata must report the version the root POM builds");
        }
        List<StatedValue> stated = new ArrayList<>();
        for (Path page : activeGovernanceSurfaces(root)) {
            stated.addAll(statedProductVersions(relative(root, page), Files.readString(page)));
        }
        requireAgreement(stated, Map.of(PRODUCT_VERSION_KEY, pomVersion), "pom.xml",
                "no governance page states the current product version in the shape of CURRENT_PRODUCT_VERSION_CLAIM:"
                        + " the scan reads nothing");
    }

    @Test
    void aCurrentVersionStatementIsComparedWhileADatedReleaseAndADependencyVersionAreNot() {
        List<StatedValue> stated = statedProductVersions(".claude/CLAUDE.md", """
                `io.github.fturleque:morpheus-engine` **1.2.3** — Specification & Intent Intelligence Engine
                Java 21 · Maven 3.10.0
                Dernier milestone livré : **M28** (livré dans 1.2.0). La baseline **1.2.3**, qui porte un schéma
                plus récent que la 1.2.1 publiée, est une passe corrective.
                `ProductMetadata` est la seule source (actuellement `1.2.2`) ; la version courante (`1.2.2`).
                `mcp-sdk.version=2.1.0`, `junit.version=6.1.3`, Maven < 3.9.16, baseline ADR-0133.
                """);
        assertEquals(4, stated.size(), () -> "the four current statements are read, nothing else: " + stated);

        AssertionError refusal = assertThrows(AssertionError.class,
                () -> requireAgreement(stated, Map.of(PRODUCT_VERSION_KEY, "1.2.2"), "pom.xml", "nothing read"));
        assertTrue(refusal.getMessage().contains(
                ".claude/CLAUDE.md project.version states 1.2.3, pom.xml says 1.2.2"), refusal::getMessage);
        assertFalse(refusal.getMessage().contains("1.2.0"), "a dated release is a record, not a statement");
        assertFalse(refusal.getMessage().contains("1.2.1"), "a published release is a record, not a statement");
        assertFalse(refusal.getMessage().contains("2.1.0"), "a dependency version is not the product version");

        AssertionError empty = assertThrows(AssertionError.class,
                () -> requireAgreement(statedProductVersions("page.md", "livré dans 1.2.0, la 1.2.1 publiée"),
                        Map.of(PRODUCT_VERSION_KEY, "1.2.2"), "pom.xml", "the scan reads nothing"));
        assertEquals("the scan reads nothing", empty.getMessage());

        assertEquals("1.2.2", rootProjectVersion("""
                <project><groupId>io.github.fturleque</groupId>
                    <artifactId>morpheus-engine</artifactId>
                    <version>1.2.2</version>
                    <dependencies><dependency><version>9.9.9</version></dependency></dependencies>
                </project>
                """), "the reactor version is the one under the root coordinates");
    }

    /**
     * A ratchet a rule page copies in a table row, under the name of its key, equals the key.
     *
     * <p>The validators read {@code config/m21-quality-ratchets.properties} and nothing else; the copy in
     * {@code .claude/rules/testing.md} is what an agent reads first. Only a table row whose first cell is a key in a
     * code span is compared: the dated paragraphs of the same page report past qualifications and stay as written.</p>
     */
    @Test
    void everyRatchetARulePageCopiesUnderItsKeyEqualsTheKey() throws Exception {
        Path root = RepositoryRoot.locate();
        Path rules = root.resolve(".claude/rules");
        List<StatedValue> stated = new ArrayList<>();
        for (Path page : activeGovernanceSurfaces(root)) {
            if (page.getParent().equals(rules)) {
                stated.addAll(statedRatchets(relative(root, page), Files.readString(page)));
            }
        }
        requireAgreement(stated, ratchets(Files.readString(root.resolve(RATCHETS))), RATCHETS,
                "no page of .claude/rules copies a ratchet in a table row under its key: the scan reads nothing");
    }

    @Test
    void aRatchetRowIsComparedByValueAndARetiredKeyIsRefusedWhileADatedParagraphIsNotRead() {
        Map<String, String> file = ratchets("testsMinimum=2876\naggregateLineCoverageMinimum=0.926\n");
        List<StatedValue> stated = statedRatchets(".claude/rules/testing.md", """
                | Clé | Valeur constatée |
                |---|---|
                | `testsMinimum` | 2877 (exécutions de méthodes déclarées) |
                | `aggregateLineCoverageMinimum` | 0.9260 (92.6%) |
                | `retiredMinimum` | 12 |

                Le 29/10/2026, `testsMinimum` passe à 1234, relevé daté.
                """);
        assertEquals(3, stated.size(), "the three rows are read and the dated paragraph is not");

        AssertionError refusal = assertThrows(AssertionError.class,
                () -> requireAgreement(stated, file, RATCHETS, "nothing read"));
        assertTrue(refusal.getMessage().contains(
                ".claude/rules/testing.md testsMinimum states 2877, " + RATCHETS + " says 2876"), refusal::getMessage);
        assertTrue(refusal.getMessage().contains(
                ".claude/rules/testing.md retiredMinimum states 12, " + RATCHETS + " declares no such key"),
                refusal::getMessage);
        assertFalse(refusal.getMessage().contains("aggregateLineCoverageMinimum"),
                "0.9260 and 0.926 are the same value");
        assertFalse(refusal.getMessage().contains("1234"), "a dated paragraph is a record, not a copy");

        AssertionError empty = assertThrows(AssertionError.class,
                () -> requireAgreement(statedRatchets("page.md", "`testsMinimum` vaut 2876."), file, RATCHETS,
                        "the scan reads nothing"));
        assertEquals("the scan reads nothing", empty.getMessage());
    }

    /** A value a page states, under the name of the key that holds it at its source. */
    private record StatedValue(String page, String key, String value) {
    }

    private static List<StatedValue> statedVersions(String page, String content) {
        List<StatedValue> stated = new ArrayList<>();
        for (Pattern shape : List.of(VERSION_PROPERTY_ELEMENT, VERSION_PROPERTY_ASSIGNMENT,
                VERSION_PROPERTY_SPAN_EQUALITY)) {
            Matcher matcher = shape.matcher(content);
            while (matcher.find()) {
                stated.add(new StatedValue(page, matcher.group(1), matcher.group(2).replaceAll("\\.+$", "")));
            }
        }
        for (String line : content.split("\\R")) {
            String row = line.strip();
            if (row.startsWith("|")) {
                statedVersionInTableRow(page, row).ifPresent(stated::add);
            }
        }
        return stated;
    }

    /** The version a table row writes: a cell that names a property, then a later cell that holds a value. */
    private static Optional<StatedValue> statedVersionInTableRow(String page, String row) {
        String property = null;
        for (String cell : row.split("\\|")) {
            String text = cell.strip();
            if (property == null) {
                Matcher name = VERSION_PROPERTY_NAME_CELL.matcher(text);
                if (name.matches()) {
                    property = name.group(1);
                }
            } else {
                Matcher value = VERSION_VALUE_CELL.matcher(text);
                if (value.matches()) {
                    return Optional.of(new StatedValue(page, property, value.group(1)));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The versions the living pages of {@code docs/} write in the shape of a property the root POM declares. A key the
     * POM does not declare belongs to another artifact -- Dependency-Check's {@code data.version}, a plugin manifest --
     * and is not read; a fenced code block transcribes a console or an example file and is not read either.
     */
    private static List<StatedValue> statedLivingDocumentationVersions(Path root, Set<String> tracked,
            Map<String, String> pom) throws IOException {
        List<StatedValue> stated = new ArrayList<>();
        for (Path page : livingDocumentationPages(root, tracked)) {
            String name = relative(root, page);
            for (StatedValue value : statedVersions(name, withoutFencedBlocks(name, Files.readString(page)))) {
                if (pom.containsKey(value.key())) {
                    stated.add(value);
                }
            }
        }
        return stated;
    }

    /**
     * Every Markdown page under {@code docs/} that Git tracks, outside the dated records. The population is a rule,
     * not a list: a page added under {@code docs/} is read without a change here, and a local report that Git ignores
     * -- the code supervisor writes under {@code docs/project/rapport-supervisor/} -- is not part of the repository.
     */
    private static List<Path> livingDocumentationPages(Path root, Set<String> tracked) {
        return RepositoryTree.files(root).stream()
                .filter(file -> {
                    String path = relative(root, file);
                    return path.startsWith("docs/") && path.endsWith(".md") && tracked.contains(path)
                            && DATED_RECORD_DIRECTORIES.stream().noneMatch(path::startsWith);
                })
                .toList();
    }

    /**
     * The page without the text of its fenced code blocks. A block closes on a fence of the same character, at least as
     * long as the one that opened it; a block left open would hide the rest of the page from the scan, so it is refused.
     */
    private static String withoutFencedBlocks(String page, String content) {
        StringBuilder prose = new StringBuilder();
        String opening = null;
        for (String line : content.split("\\R", -1)) {
            Matcher fence = CODE_FENCE.matcher(line);
            boolean isFence = fence.find();
            if (opening == null && isFence) {
                opening = fence.group(1);
            } else if (opening != null && isFence && fence.group(1).charAt(0) == opening.charAt(0)
                    && fence.group(1).length() >= opening.length()) {
                opening = null;
            } else if (opening == null) {
                prose.append(line);
            }
            prose.append('\n');
        }
        if (opening != null) {
            throw new AssertionError(page + " opens a fenced code block that it never closes: the rest of the page "
                    + "would be hidden from the version scan");
        }
        return prose.toString();
    }

    private static List<StatedValue> statedProductVersions(String page, String content) {
        List<StatedValue> stated = new ArrayList<>();
        Matcher claim = CURRENT_PRODUCT_VERSION_CLAIM.matcher(content);
        while (claim.find()) {
            stated.add(new StatedValue(page, PRODUCT_VERSION_KEY, claim.group(2)));
        }
        return stated;
    }

    private static String rootProjectVersion(String pom) {
        Matcher version = ROOT_PROJECT_VERSION.matcher(pom);
        assertTrue(version.find(), "the root POM must declare the morpheus-engine coordinates with their version");
        return version.group(1);
    }

    private static List<StatedValue> statedRatchets(String page, String content) {
        List<StatedValue> stated = new ArrayList<>();
        Matcher row = RATCHET_ROW.matcher(content);
        while (row.find()) {
            stated.add(new StatedValue(page, row.group(1), row.group(2)));
        }
        return stated;
    }

    /**
     * Fails naming every page, key and pair of values that disagree, and fails on an empty scan: a check that read no
     * copy would pass on a page that no longer has the shape it reads.
     */
    private static void requireAgreement(List<StatedValue> stated, Map<String, String> source, String sourceName,
            String emptyScan) {
        if (stated.isEmpty()) {
            throw new AssertionError(emptyScan);
        }
        refuseDisagreements(stated, source, sourceName);
    }

    /** Fails naming every page, key and pair of values that disagree; an empty list agrees. */
    private static void refuseDisagreements(List<StatedValue> stated, Map<String, String> source, String sourceName) {
        List<String> disagreements = new ArrayList<>();
        for (StatedValue value : stated) {
            String expected = source.get(value.key());
            if (expected == null) {
                disagreements.add(value.page() + " " + value.key() + " states " + value.value() + ", " + sourceName
                        + " declares no such key");
            } else if (!sameValue(value.value(), expected)) {
                disagreements.add(value.page() + " " + value.key() + " states " + value.value() + ", " + sourceName
                        + " says " + expected);
            }
        }
        if (!disagreements.isEmpty()) {
            throw new AssertionError("a page copies a value that its source no longer holds; update the "
                    + "page from " + sourceName + ", never the other way round:\n  "
                    + String.join("\n  ", disagreements));
        }
    }

    private static boolean sameValue(String stated, String expected) {
        if (stated.equals(expected)) {
            return true;
        }
        try {
            return new BigDecimal(stated).compareTo(new BigDecimal(expected)) == 0;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** The properties of the root POM's first {@code <properties>} block, the one that holds the versions. */
    private static Map<String, String> rootPomProperties(String pom) {
        int start = pom.indexOf("<properties>");
        int end = pom.indexOf("</properties>", start);
        assertTrue(start >= 0 && end > start, "the root POM must declare a <properties> block");
        Map<String, String> properties = new TreeMap<>();
        Matcher entry = POM_PROPERTY.matcher(pom.substring(start, end));
        while (entry.find()) {
            properties.put(entry.group(1), entry.group(2).strip());
        }
        return properties;
    }

    private static Map<String, String> ratchets(String content) {
        Map<String, String> keys = new TreeMap<>();
        for (String line : content.split("\\R")) {
            int equals = line.indexOf('=');
            if (!line.isBlank() && !line.startsWith("#") && equals > 0) {
                keys.put(line.substring(0, equals).strip(), line.substring(equals + 1).strip());
            }
        }
        return keys;
    }

    private static String relative(Path root, Path page) {
        return root.relativize(page).toString().replace('\\', '/');
    }

    /** Quotes the offending text so a failure names what to remove instead of only where to look. */
    private static String describeFirstMatch(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group().strip() : "";
    }

    /**
     * The pages and directories every governance check above scans.
     *
     * <p>A missing surface is refused, not skipped. The list used to tolerate an absent page or directory, so
     * deleting one narrowed every check that iterates it while each of them kept passing -- the population defect
     * A-03 removed from the coverage gate. A surface retired on purpose leaves {@link #GOVERNANCE_PAGES} or
     * {@link #GOVERNANCE_DIRECTORIES} in the same change.</p>
     */
    private static List<Path> activeGovernanceSurfaces(Path root) throws IOException {
        List<Path> surfaces = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String page : GOVERNANCE_PAGES) {
            Path file = root.resolve(page);
            if (Files.isRegularFile(file)) {
                surfaces.add(file);
            } else {
                missing.add(page);
            }
        }
        for (String directory : GOVERNANCE_DIRECTORIES) {
            Path base = root.resolve(directory);
            List<Path> pages = List.of();
            if (Files.isDirectory(base)) {
                try (var entries = Files.list(base)) {
                    pages = entries.filter(path -> path.toString().endsWith(".md")).sorted().toList();
                }
            }
            if (pages.isEmpty()) {
                missing.add(directory + "/*.md");
            }
            surfaces.addAll(pages);
        }
        surfaces.addAll(collectSkillSurfaces(root, missing));
        if (!missing.isEmpty()) {
            throw new AssertionError("declared governance surface(s) absent: " + String.join(", ", missing)
                    + "; retire a surface by removing it from the declared list, never by letting the scan shrink");
        }
        return surfaces;
    }

    /**
     * The {@code SKILL.md} of every skill folder, in name order.
     *
     * <p>A skill is only loaded from {@code .claude/skills/&lt;name&gt;/SKILL.md}. Two shapes are therefore refused
     * rather than skipped: a folder without its {@code SKILL.md}, and a loose {@code .md} sitting directly under
     * the skills root. Both would be governance that Claude Code never reads and that no check here would ever
     * scan -- the quietest way for a rule to stop existing.</p>
     */
    private static List<Path> collectSkillSurfaces(Path root, List<String> missing) throws IOException {
        Path skillsRoot = root.resolve(GOVERNANCE_SKILL_DIRECTORY);
        List<Path> skills = new ArrayList<>();
        if (Files.isDirectory(skillsRoot)) {
            List<Path> entries;
            try (var stream = Files.list(skillsRoot)) {
                entries = stream.sorted().toList();
            }
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    Path skillFile = entry.resolve(SKILL_FILE);
                    if (Files.isRegularFile(skillFile)) {
                        skills.add(skillFile);
                    } else {
                        missing.add(GOVERNANCE_SKILL_DIRECTORY + "/" + name + "/" + SKILL_FILE);
                    }
                } else if (name.endsWith(".md")) {
                    missing.add(GOVERNANCE_SKILL_DIRECTORY + "/" + name
                            + " (a skill is loaded from <name>/" + SKILL_FILE + ", never from a loose file)");
                }
            }
        }
        if (skills.isEmpty()) {
            missing.add(GOVERNANCE_SKILL_DIRECTORY + "/*/" + SKILL_FILE);
        }
        return skills;
    }
}
