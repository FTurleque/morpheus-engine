package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A store test that states "the latest schema version" has to read it from the constant that declares it.
 *
 * <p>{@code SqliteSchemaMigrationTest} restated that version as a literal three times and named a test after a count
 * that had stopped being true two migrations earlier; each new migration cost three edits in a test that had nothing
 * to do with it. The rule is stated on the operation, not on a value: an {@code assertEquals} whose expected argument
 * is an integer literal and whose actual argument is the schema version read from a database
 * ({@code currentVersion(}) or the ledger count ({@code getInt("count")}). A value-keyed rule would refuse an unrelated
 * {@code assertEquals(21, list.size())} the day the constant reaches 21; this one never looks at it.</p>
 *
 * <p>A literal that names a frozen historical baseline is legitimate, so a test may be listed below by name with the
 * reason. An entry without a reason, or for a file that no longer holds such a literal, fails the suite: the list
 * cannot accumulate stale excuses. The rule is textual because it reads test sources; it does not see
 * {@code assertThat}, a {@code long} literal or an argument split across statements, and says so rather than claiming
 * more.</p>
 */
class SqliteStoreTestsDeriveTheSchemaVersionTest {
    private static final Pattern LITERAL_AGAINST_SCHEMA_STATE = Pattern.compile(
            "assertEquals\\(\\s*(\\d[\\d_]*)\\s*,\\s*([^;]*?(?:currentVersion\\(|getInt\\(\"count\"\\))[^;]*?)\\)\\s*;");

    /** File name to the reason its literal is a frozen baseline and not a restatement of the latest version. */
    private static final Map<String, String> FROZEN_BASELINES = Map.of(
            "R2UpgradeCompatibilityTest.java",
            "asserts the schema version of its frozen 1.0.0 fixture, which is what the fixture was, before upgrading it");

    @Test
    void noStoreTestComparesTheSchemaVersionOrTheLedgerCountWithALiteral() throws IOException {
        Path tests = repositoryRoot().resolve("morpheus-store-sqlite/src/test/java");
        List<Path> sources;
        try (Stream<Path> files = Files.walk(tests)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        assertTrue(sources.size() > 5, "too few store test sources scanned: the rule would pass on nothing");

        List<String> restatements = new ArrayList<>();
        TreeSet<String> filesWithALiteral = new TreeSet<>();
        int comparisonsOfTheScannedShape = 0;
        for (Path source : sources) {
            String text = Files.readString(source);
            Matcher matcher = LITERAL_AGAINST_SCHEMA_STATE.matcher(text);
            while (matcher.find()) {
                comparisonsOfTheScannedShape++;
                String name = source.getFileName().toString();
                filesWithALiteral.add(name);
                if (!FROZEN_BASELINES.containsKey(name)) {
                    int line = 1 + (int) text.substring(0, matcher.start()).chars().filter(c -> c == '\n').count();
                    restatements.add(name + ":" + line + " compares " + matcher.group(2).strip() + " with the literal "
                            + matcher.group(1));
                }
            }
        }
        assertTrue(comparisonsOfTheScannedShape > 0,
                "no assertion of the scanned shape exists: the rule would pass on nothing");

        FROZEN_BASELINES.forEach((file, reason) -> {
            assertFalse(reason.isBlank(), "a frozen-baseline exclusion needs its reason: " + file);
            assertTrue(filesWithALiteral.contains(file),
                    "this exclusion no longer excuses anything and must go: " + file);
        });
        assertTrue(restatements.isEmpty(),
                () -> "a store test must read SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION instead of restating it: "
                        + restatements);
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                String content = Files.readString(pom);
                if (content.contains("<artifactId>morpheus-engine</artifactId>") && content.contains("<modules>")) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
