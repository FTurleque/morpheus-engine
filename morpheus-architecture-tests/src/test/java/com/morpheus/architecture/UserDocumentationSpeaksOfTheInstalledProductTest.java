package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps {@code docs/user/} readable without the repository: a user page speaks of the installed product, its
 * commands and its configuration files.
 *
 * <p>{@code .claude/rules/documentation.md} forbids there a commit hash, a test counter, an issue or pull request
 * number, a test or gate name, a {@code ./mvnw} command and a {@code src/} path. The rule was prose only, and a
 * review of 2026-10-13 found it broken on five pages by sentences older than the rule. The check is textual
 * because Markdown offers no other hold ({@code .claude/rules/architecture.md}): it forbids the mention, which is the
 * intention.</p>
 *
 * <p>The release notes of a published version are dated records and are not rewritten; they are exempt by name.
 * The note of a version still in preparation is an active page and is scanned.</p>
 */
class UserDocumentationSpeaksOfTheInstalledProductTest {
    private static final String USER_DOCUMENTATION = "docs/user";
    private static final Set<String> DATED_RELEASE_NOTES = Set.of(
            "docs/user/release-notes/RELEASE_NOTES_1.1.0.md",
            "docs/user/release-notes/RELEASE_NOTES_1.2.0.md");

    private static final Map<String, Pattern> DEVELOPER_EVIDENCE = developerEvidence();

    @Test
    void noActiveUserPageCarriesDeveloperEvidence() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> pages = activePages(root);
        assertFalse(pages.isEmpty(), "no active page found under " + USER_DOCUMENTATION
                + "; the scan must not pass on an empty set");

        List<String> findings = new ArrayList<>();
        for (Path page : pages) {
            List<String> lines = Files.readAllLines(page);
            for (int index = 0; index < lines.size(); index++) {
                for (String kind : kindsFoundIn(lines.get(index))) {
                    findings.add(relative(root, page) + ":" + (index + 1) + " " + kind);
                }
            }
        }

        assertEquals(List.of(), findings,
                "a page of docs/user/ must stay readable without the repository; say what the user observes and "
                        + "move the evidence to docs/developer/ or docs/project/");
    }

    @Test
    void everyDatedExemptionIsAReleaseNoteThatStillExists() throws IOException {
        Path root = RepositoryRoot.locate();

        for (String exempt : DATED_RELEASE_NOTES) {
            assertTrue(Files.isRegularFile(root.resolve(exempt)),
                    exempt + " is exempt from the scan but no longer exists; remove the exemption");
            assertTrue(exempt.startsWith(USER_DOCUMENTATION + "/release-notes/RELEASE_NOTES_"),
                    exempt + " is exempt from the scan but is not a release note; only a dated record is exempt");
        }
    }

    @Test
    void eachKindOfDeveloperEvidenceIsRecognised() {
        assertEquals(List.of("commit hash"),
                kindsFoundIn("Code testé : `7e8caacff567f51354fcb88bd7505a6d135071c0`."));
        assertEquals(List.of("issue or pull request number"), kindsFoundIn("artefacts (suivi #185, critère"));
        assertEquals(List.of("test or gate class name"), kindsFoundIn("garde `PageBoundsAgreementTest`"));
        assertEquals(List.of("test or gate class name"), kindsFoundIn("le budget `M19QueryPerformanceGate`"));
        assertEquals(List.of("Maven wrapper command"), kindsFoundIn("./mvnw clean verify"));
        assertEquals(List.of("source path"), kindsFoundIn("voir morpheus-api/src/main/java/com/morpheus/api"));
        assertEquals(List.of("build output path"), kindsFoundIn("écrit dans `morpheus-api/target/profile.txt`"));
        assertEquals(List.of("validator script"), kindsFoundIn("Les validateurs `scripts/validate-m25.*`"));
        assertEquals(List.of("test counter"), kindsFoundIn("TOTAL            418/418 PASS"));
        assertEquals(List.of("test counter"), kindsFoundIn("M23  507 tests | architecture 195"));
    }

    @Test
    void theVocabularyOfTheInstalledProductIsLeftAlone() {
        List<String> productLines = List.of(
                "sha256: 9f86d081884c7d659a2feabf0ea0a2c5d3e0a7b1c1f0c3a4b5d6e7f8091a2b3c",
                "admin|ADMIN|7c4a8d09ca3762af...",
                "voir [les codes de sortie](CLI.md#20-codes-de-sortie) et [l'audit](#3-audit)",
                "morpheus --json portfolio add-project --workspace /src",
                "`policy evaluate` rend `0` sur `PASS` et `WARN`",
                "HTTP 429 PRIVILEGED_CONCURRENCY_LIMIT",
                "Une ligne conserve son `ProjectSpecificationId`.",
                "Strict-Transport-Security: max-age=31536000");

        for (String line : productLines) {
            assertEquals(List.of(), kindsFoundIn(line), "refused although it describes the installed product: " + line);
        }
    }

    private static Map<String, Pattern> developerEvidence() {
        Map<String, Pattern> kinds = new LinkedHashMap<>();
        kinds.put("commit hash", Pattern.compile("(?<![0-9a-fA-F])[0-9a-f]{40}(?![0-9a-fA-F])"));
        kinds.put("issue or pull request number", Pattern.compile("(?<![\\w(/&])#\\d+\\b(?!-)"));
        kinds.put("test or gate class name", Pattern.compile("\\b[A-Z][a-z0-9]+[A-Za-z0-9]*(?:Tests?|Gate)\\b"));
        kinds.put("Maven wrapper command", Pattern.compile("mvnw"));
        kinds.put("source path", Pattern.compile("\\bsrc/(?:main|test)\\b"));
        kinds.put("build output path", Pattern.compile("[\\w-]+/target/"));
        kinds.put("validator script", Pattern.compile("scripts/validate-"));
        kinds.put("test counter", Pattern.compile("\\b\\d+/\\d+ PASS\\b|(?i:\\b\\d+ tests\\b)"));
        return kinds;
    }

    private static List<String> kindsFoundIn(String line) {
        List<String> kinds = new ArrayList<>();
        DEVELOPER_EVIDENCE.forEach((kind, pattern) -> {
            if (pattern.matcher(line).find()) {
                kinds.add(kind);
            }
        });
        return kinds;
    }

    private static List<Path> activePages(Path root) throws IOException {
        try (var entries = Files.walk(root.resolve(USER_DOCUMENTATION))) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(entry -> entry.getFileName().toString().endsWith(".md"))
                    .filter(entry -> !DATED_RELEASE_NOTES.contains(relative(root, entry)))
                    .sorted()
                    .toList();
        }
    }

    private static String relative(Path root, Path page) {
        return root.relativize(page).toString().replace('\\', '/');
    }
}
