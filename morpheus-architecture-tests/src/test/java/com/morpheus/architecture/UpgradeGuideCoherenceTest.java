package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the operator's upgrade guide equal to what the SQLite store does when it meets a database of another
 * schema version.
 *
 * <p>A guide cannot read a constant, so it states the schema version and this test compares the statement with
 * {@code SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION}. The previous guide said the schema was unchanged and made
 * the backup optional; it stayed true of its own release and read as current through five later migrations,
 * because nothing compared it with the code.</p>
 */
class UpgradeGuideCoherenceTest {
    private static final Pattern GUIDE_NAME = Pattern.compile("UPGRADE_(\\d+(?:_\\d+)*)\\.md");
    private static final Pattern SCHEMA_VERSION_DECLARATION =
            Pattern.compile("SUPPORTED_SCHEMA_VERSION\\s*=\\s*(\\d+)");
    private static final Pattern MIGRATION_TOKEN = Pattern.compile("\\bV(\\d{3})\\b");
    private static final Pattern OPTIONAL_BACKUP = Pattern.compile("(?i)sauvegarde[^.\\n]*souhait");
    private static final String SCHEMA_MANAGER =
            "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java";
    private static final String FUTURE_SCHEMA_REFUSAL = "is newer than supported";
    private static final String BACKUP_HEADING = "## Sauvegarde obligatoire";
    private static final String INSTALLATION_HEADING = "## Installation";
    private static final String ROLLBACK_HEADING = "## Retour";

    @Test
    void theLatestUpgradeGuideStatesTheSchemaVersionTheCodeSupports() throws IOException {
        Path root = repositoryRoot();
        Guide latest = latest(guides(root));
        int supported = supportedSchemaVersion(root);

        int stated = -1;
        Matcher tokens = MIGRATION_TOKEN.matcher(latest.content());
        while (tokens.find()) {
            stated = Math.max(stated, Integer.parseInt(tokens.group(1)));
        }

        assertEquals(supported, stated,
                latest.name() + " states V" + String.format("%03d", stated) + " as the highest schema version while "
                        + "SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION is " + supported
                        + "; a migration was added, or removed, without the upgrade guide following");
    }

    @Test
    void theLatestUpgradeGuideMakesTheBackupMandatoryBeforeInstallingAndSaysWhichProgramTakesIt() throws IOException {
        Guide latest = latest(guides(repositoryRoot()));
        String guide = latest.content();

        int backup = guide.indexOf(BACKUP_HEADING);
        int installation = guide.indexOf(INSTALLATION_HEADING);
        assertTrue(backup >= 0, latest.name() + " must keep a section titled '" + BACKUP_HEADING + "'");
        assertTrue(installation >= 0, latest.name() + " must keep a section titled '" + INSTALLATION_HEADING + "'");
        assertTrue(backup < installation,
                latest.name() + " must place the mandatory backup before the installation step");

        String backupSection = guide.substring(backup, installation);
        assertTrue(backupSection.contains("server backup create"),
                latest.name() + " must name the command that takes the backup");
        assertTrue(backupSection.contains("server backup verify"),
                latest.name() + " must name the command that verifies the backup");
        assertTrue(backupSection.contains("avec la version déjà installée"),
                latest.name() + " must say the backup is taken with the version already installed");
        assertTrue(backupSection.contains("au schéma du programme qui l'exécute"),
                latest.name() + " must say a backup is brought to the schema of the program that takes it");
        assertFalse(OPTIONAL_BACKUP.matcher(guide).find(),
                latest.name() + " must not make the backup conditional on the operator's wish");
    }

    @Test
    void theLatestUpgradeGuideDescribesTheWayBackAndQuotesTheRefusalTheCodeGives() throws IOException {
        Path root = repositoryRoot();
        Guide latest = latest(guides(root));
        String guide = latest.content();

        assertTrue(Files.readString(root.resolve(SCHEMA_MANAGER)).contains(FUTURE_SCHEMA_REFUSAL),
                "SqliteSchemaManager no longer refuses a future schema with '" + FUTURE_SCHEMA_REFUSAL
                        + "'; the upgrade guide quotes that sentence and must follow the new wording");
        assertTrue(guide.contains(FUTURE_SCHEMA_REFUSAL),
                latest.name() + " must quote the refusal the program gives on a newer database: '" + FUTURE_SCHEMA_REFUSAL + "'");

        int rollback = guide.indexOf(ROLLBACK_HEADING);
        assertTrue(rollback >= 0, latest.name() + " must keep a section whose title starts with '" + ROLLBACK_HEADING + "'");
        String rollbackSection = guide.substring(rollback);
        assertTrue(rollbackSection.contains("server restore") && rollbackSection.contains("--confirm"),
                latest.name() + " must name the offline restore as the way back");
    }

    /**
     * The refusal of a future schema was added after 1.2.0 was published: 1.2.0 opens a database that 1.2.1 migrated
     * and runs on a schema it does not know. That is a fact about one published release, so it is pinned on the one
     * guide that describes leaving it, by name, and stays true of that file whatever guide comes next.
     */
    @Test
    void theGuideThatLeavesOneDotTwoDotZeroSaysThatReleaseDoesNotRefuseAMigratedDatabase() throws IOException {
        String guide = Files.readString(repositoryRoot().resolve("docs/user/UPGRADE_1_2_1.md"));

        assertTrue(guide.contains("1.2.0 ne refuse pas une base migrée"),
                "UPGRADE_1_2_1.md must say that 1.2.0 opens a migrated database without refusing it");
        assertTrue(guide.contains("Ne jamais démarrer 1.2.0 sur une base que 1.2.1 a ouverte"),
                "UPGRADE_1_2_1.md must forbid starting 1.2.0 on a database 1.2.1 has opened");
        assertTrue(guide.contains("backup schema version"),
                "UPGRADE_1_2_1.md must name the one check 1.2.0 does make, on a backup");
    }

    @Test
    void anEarlierUpgradeGuideNamesItsReleaseAndPointsToTheLatestGuide() throws IOException {
        List<Guide> guides = guides(repositoryRoot());
        Guide latest = latest(guides);
        assertTrue(guides.size() >= 2, "expected the latest upgrade guide and at least one earlier guide");

        for (Guide guide : guides) {
            if (guide == latest) {
                continue;
            }
            String opening = guide.content().lines().limit(12).reduce("", (left, right) -> left + right + "\n");
            assertTrue(opening.contains("](" + latest.name() + ")"),
                    guide.name() + " describes an earlier release and must link " + latest.name()
                            + " in its opening notice, so that what it says of the schema does not read as current");
        }
    }

    @Test
    void guideVersionsAreOrderedNumericallyNotAlphabetically() {
        Guide patch = new Guide("UPGRADE_1_2_1.md", List.of(1, 2, 1), "");
        Guide minor = new Guide("UPGRADE_1_2.md", List.of(1, 2), "");
        Guide nine = new Guide("UPGRADE_1_9.md", List.of(1, 9), "");
        Guide ten = new Guide("UPGRADE_1_10.md", List.of(1, 10), "");

        assertEquals(patch, latest(List.of(minor, patch)));
        assertEquals(ten, latest(List.of(ten, nine, patch, minor)));
    }

    private record Guide(String name, List<Integer> version, String content) {
    }

    private static final Comparator<Guide> BY_VERSION = (left, right) -> {
        for (int index = 0; index < Math.max(left.version().size(), right.version().size()); index++) {
            int leftPart = index < left.version().size() ? left.version().get(index) : 0;
            int rightPart = index < right.version().size() ? right.version().get(index) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    };

    private static Guide latest(List<Guide> guides) {
        return guides.stream().max(BY_VERSION).orElseThrow(
                () -> new AssertionError("no docs/user/UPGRADE_*.md found; the scan must not pass on an empty set"));
    }

    private static List<Guide> guides(Path root) throws IOException {
        List<Guide> guides = new ArrayList<>();
        try (var entries = Files.list(root.resolve("docs/user"))) {
            for (Path entry : entries.sorted().toList()) {
                Matcher name = GUIDE_NAME.matcher(entry.getFileName().toString());
                if (name.matches()) {
                    List<Integer> version = new ArrayList<>();
                    for (String part : name.group(1).split("_")) {
                        version.add(Integer.parseInt(part));
                    }
                    guides.add(new Guide(entry.getFileName().toString(), version, Files.readString(entry)));
                }
            }
        }
        return guides;
    }

    private static int supportedSchemaVersion(Path root) throws IOException {
        Matcher declaration = SCHEMA_VERSION_DECLARATION.matcher(Files.readString(root.resolve(SCHEMA_MANAGER)));
        assertTrue(declaration.find(), "SqliteSchemaManager must declare SUPPORTED_SCHEMA_VERSION");
        return Integer.parseInt(declaration.group(1));
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
