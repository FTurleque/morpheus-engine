package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the list of schema levels that published versions shipped complete.
 *
 * <p>{@code ReleasedSchemaUpgradeTest} upgrades one database per level declared in
 * {@code released-schema-levels.properties}. A release that nobody declared would leave its upgrade path unproved
 * without any test failing, so this rule compares two sets: the versions that have release notes under
 * {@code docs/user/release-notes/} and are lower than the product version, and the versions the declaration names.
 * The version in progress is exempt until it is released; the first build of the next version then fails until the
 * level just shipped is declared.</p>
 *
 * <p>The documentation side is textual by nature (a {@code .md} file name, {@code .claude/rules/architecture.md}): the
 * intent is "a released
 * version without a declared level must not pass", and no bytecode rule sees that file.</p>
 */
class ReleasedSchemaLevelsDeclarationTest {
    private static final String DECLARATION =
            "morpheus-store-sqlite/src/test/resources/released-schema-levels.properties";
    private static final String RELEASE_NOTES = "docs/user/release-notes";
    private static final String SCHEMA_MANAGER =
            "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java";
    private static final String CHECKSUMS =
            "morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteMigrationChecksumGoldenTest.java";
    private static final String FIRST_PUBLISHED_RELEASE = "1.0.0";
    private static final Pattern NOTES_NAME = Pattern.compile("RELEASE_NOTES_(\\d+(?:\\.\\d+)*)\\.md");
    private static final Pattern SCHEMA_VERSION_DECLARATION =
            Pattern.compile("SUPPORTED_SCHEMA_VERSION\\s*=\\s*(\\d+)");
    private static final Pattern PINNED_CHECKSUM = Pattern.compile("entry\\(\\s*(\\d+)\\s*,\\s*\"[0-9a-f]{64}\"");
    private static final Pattern PRODUCT_VERSION = Pattern.compile(
            "<artifactId>morpheus-engine</artifactId>\\s*<version>([^<]+)</version>");
    private static final Comparator<String> BY_VERSION = ReleasedSchemaLevelsDeclarationTest::compareVersions;

    @Test
    void everyReleasedVersionDeclaresTheSchemaLevelItShippedInTheRepository() throws IOException {
        Path root = repositoryRoot();
        Map<String, Integer> declared = declaredLevels(root);
        Set<String> noted = releaseNotesVersions(root);
        String product = productVersion(root);

        assertTrue(noted.size() >= 2, "expected the release notes of at least two versions under " + RELEASE_NOTES
                + " (read: " + noted + "); the rule would pass on an empty set");
        assertTrue(declared.size() >= 3, "the declaration holds " + declared + "; it must name every published version");
        assertEquals(List.of(), problems(noted, product, declared, supportedSchemaVersion(root), pinnedChecksums(root)));
    }

    @Test
    void aReleasedVersionWithoutADeclaredLevelNamesItselfInTheFailure() {
        Map<String, Integer> declared = Map.of("1.0.0", 12, "1.1.0", 15);

        List<String> problems = problems(
                Set.of("1.1.0", "1.2.0", "1.2.1"), "1.2.1", declared, 22, pinned(22));

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().contains("1.2.0"), problems.getFirst());
        assertTrue(problems.getFirst().contains("no declared schema level"), problems.getFirst());
    }

    @Test
    void theVersionInProgressIsNotRequiredToDeclareALevelUntilItIsReleased() {
        Map<String, Integer> declared = Map.of("1.0.0", 12, "1.1.0", 15);

        assertEquals(List.of(), problems(Set.of("1.1.0", "1.2.0"), "1.2.0", Map.of("1.0.0", 12, "1.1.0", 15), 22, pinned(22)));
        assertEquals(1, problems(Set.of("1.1.0", "1.2.0"), "1.2.1", declared, 22, pinned(22)).size(),
                "once the product version moves past 1.2.0, that release must declare its level");
    }

    @Test
    void theNextVersionFailsUntilTheLevelJustShippedIsDeclared() {
        Map<String, Integer> declared = Map.of("1.0.0", 12, "1.1.0", 15, "1.2.0", 15);

        assertEquals(List.of(), problems(Set.of("1.1.0", "1.2.0", "1.2.1"), "1.2.1", declared, 22, pinned(22)));
        List<String> afterTheRelease = problems(
                Set.of("1.1.0", "1.2.0", "1.2.1", "1.2.2"), "1.2.2", declared, 22, pinned(22));
        assertEquals(1, afterTheRelease.size(), afterTheRelease.toString());
        assertTrue(afterTheRelease.getFirst().contains("1.2.1"), afterTheRelease.getFirst());
    }

    @Test
    void theFirstPublishedReleaseStaysDeclaredEvenThoughItHasNoReleaseNotes() {
        List<String> problems = problems(Set.of("1.1.0"), "1.2.0", Map.of("1.1.0", 15), 22, pinned(22));

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().contains(FIRST_PUBLISHED_RELEASE), problems.getFirst());
    }

    @Test
    void aDeclaredLevelBeyondTheSupportedSchemaOrWithoutAPinnedChecksumIsRefused() {
        List<String> beyond = problems(
                Set.of("1.1.0"), "1.2.0", Map.of("1.0.0", 12, "1.1.0", 23), 22, pinned(22));
        assertEquals(1, beyond.size(), beyond.toString());
        assertTrue(beyond.getFirst().contains("1.1.0") && beyond.getFirst().contains("23"), beyond.getFirst());

        List<String> unpinned = problems(
                Set.of("1.1.0"), "1.2.0", Map.of("1.0.0", 12, "1.1.0", 15), 22, pinned(14));
        assertEquals(1, unpinned.size(), unpinned.toString());
        assertTrue(unpinned.getFirst().contains("1.1.0") && unpinned.getFirst().contains("15"), unpinned.getFirst());
    }

    @Test
    void versionsAreComparedNumericallyNotAlphabetically() {
        assertTrue(compareVersions("1.9.0", "1.10.0") < 0);
        assertTrue(compareVersions("1.2", "1.2.1") < 0);
        assertEquals(0, compareVersions("1.2.0", "1.2"));
        assertEquals(List.of(), problems(Set.of("1.9.0"), "1.10.0", Map.of("1.0.0", 12, "1.9.0", 15), 22, pinned(22)));
        assertEquals(List.of(), problems(Set.of("1.10.0"), "1.9.0", Map.of("1.0.0", 12), 22, pinned(22)),
                "1.10.0 is not lower than 1.9.0, so its level is not required yet");
    }

    private static List<String> problems(
            Set<String> releaseNotesVersions,
            String productVersion,
            Map<String, Integer> declared,
            int supportedSchemaVersion,
            Set<Integer> pinnedChecksums) {
        List<String> problems = new ArrayList<>();
        if (!declared.containsKey(FIRST_PUBLISHED_RELEASE)) {
            problems.add("the first published release " + FIRST_PUBLISHED_RELEASE
                    + " has no release notes and must keep its declared schema level");
        }
        Set<String> notedInOrder = new TreeSet<>(BY_VERSION);
        notedInOrder.addAll(releaseNotesVersions);
        for (String version : notedInOrder) {
            if (compareVersions(version, productVersion) < 0 && !declared.containsKey(version)) {
                problems.add("version " + version + " is released (it has release notes and is lower than "
                        + productVersion + ") and has no declared schema level; add it to " + DECLARATION);
            }
        }
        Map<String, Integer> declaredInOrder = new TreeMap<>(BY_VERSION);
        declaredInOrder.putAll(declared);
        for (Map.Entry<String, Integer> level : declaredInOrder.entrySet()) {
            if (level.getValue() > supportedSchemaVersion) {
                problems.add("version " + level.getKey() + " declares schema level " + level.getValue()
                        + ", above SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION " + supportedSchemaVersion);
            } else if (!pinnedChecksums.contains(level.getValue())) {
                problems.add("version " + level.getKey() + " declares schema level " + level.getValue()
                        + ", which has no pinned checksum in SqliteMigrationChecksumGoldenTest");
            }
        }
        return problems;
    }

    private static Set<Integer> pinned(int through) {
        Set<Integer> levels = new TreeSet<>();
        for (int level = 1; level <= through; level++) {
            levels.add(level);
        }
        return levels;
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            int leftPart = index < leftParts.length ? Integer.parseInt(leftParts[index]) : 0;
            int rightPart = index < rightParts.length ? Integer.parseInt(rightParts[index]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static Map<String, Integer> declaredLevels(Path root) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(root.resolve(DECLARATION))));
        Map<String, Integer> declared = new TreeMap<>(BY_VERSION);
        for (String version : properties.stringPropertyNames()) {
            declared.put(version.trim(), Integer.parseInt(properties.getProperty(version).trim()));
        }
        return declared;
    }

    private static Set<String> releaseNotesVersions(Path root) throws IOException {
        Set<String> versions = new TreeSet<>(BY_VERSION);
        try (Stream<Path> entries = Files.list(root.resolve(RELEASE_NOTES))) {
            for (Path entry : entries.toList()) {
                Matcher name = NOTES_NAME.matcher(entry.getFileName().toString());
                if (name.matches()) {
                    versions.add(name.group(1));
                }
            }
        }
        return versions;
    }

    private static String productVersion(Path root) throws IOException {
        Matcher version = PRODUCT_VERSION.matcher(Files.readString(root.resolve("pom.xml")));
        assertTrue(version.find(), "the root pom.xml must declare the product version after its artifactId");
        return version.group(1).trim().replace("-SNAPSHOT", "");
    }

    private static int supportedSchemaVersion(Path root) throws IOException {
        Matcher declaration = SCHEMA_VERSION_DECLARATION.matcher(Files.readString(root.resolve(SCHEMA_MANAGER)));
        assertTrue(declaration.find(), "SqliteSchemaManager must declare SUPPORTED_SCHEMA_VERSION");
        return Integer.parseInt(declaration.group(1));
    }

    private static Set<Integer> pinnedChecksums(Path root) throws IOException {
        Set<Integer> levels = new TreeSet<>();
        Matcher entry = PINNED_CHECKSUM.matcher(Files.readString(root.resolve(CHECKSUMS)));
        while (entry.find()) {
            levels.add(Integer.parseInt(entry.group(1)));
        }
        assertFalse(levels.isEmpty(), "SqliteMigrationChecksumGoldenTest must pin the checksum of every migration");
        return levels;
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
