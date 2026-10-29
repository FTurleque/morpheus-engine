package com.morpheus.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the AI governance surfaces -- {@code .claude/CLAUDE.md}, {@code .github/AI_GOVERNANCE.md}, the rules,
 * commands, agents and skills -- against stating a perishable fact: a SQLite schema version, an ADR or module
 * total. The surfaces are declared, and a declared surface that is missing fails instead of shrinking the scan.
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
