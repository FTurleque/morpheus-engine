package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migrating non-expiring identities to an explicit deadline.
 *
 * <p>The three-field entry is a valid non-expiring credential and stays valid input: expiring one that an
 * operator never asked to change is how a remote server locks its own administrators out. The migration is
 * therefore explicit, reports what it would do before it does it, and never touches token material -- every
 * client that works today keeps working, it simply now has a deadline.</p>
 */
class MorpheusRemoteLegacyIdentityMigrationTest {
    private static final Instant DEADLINE = Instant.parse("2099-06-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2099-12-31T00:00:00Z");

    @TempDir
    Path temp;

    @Test
    void aDryRunReportsExactlyWhatWouldChangeAndChangesNothing() throws Exception {
        Path auth = legacyFile("breakglass|ADMIN", "reader|READ", "writer|WRITE");
        String before = Files.readString(auth);

        MorpheusRemoteIdentityFile.LegacyMigration report = MorpheusRemoteIdentityFile.migrateLegacyExpiry(
                auth, DEADLINE, Set.of("reader", "writer"), true);

        assertTrue(report.dryRun());
        assertEquals(DEADLINE, report.expiresAt());
        assertEquals(List.of("reader", "writer"), report.migrated());
        assertEquals(List.of("breakglass"), report.retained());
        assertEquals(before, Files.readString(auth), "a dry run must leave the file byte-identical");
    }

    /**
     * Migration adds a deadline and nothing else. Rotating here would invalidate every client's token at once,
     * which is the opposite of a migration an operator can run without an outage window.
     */
    @Test
    void migrationAddsAnExpiryWithoutRotatingAnyTokenMaterial() throws Exception {
        Path auth = legacyFile("breakglass|ADMIN", "reader|READ");
        byte[] readerHashBefore = identity(auth, "reader").tokenHash();

        MorpheusRemoteIdentityFile.LegacyMigration report = MorpheusRemoteIdentityFile.migrateLegacyExpiry(
                auth, DEADLINE, Set.of("reader"), false);

        assertFalse(report.dryRun());
        assertEquals(List.of("reader"), report.migrated());
        assertEquals(Optional.of(DEADLINE), identity(auth, "reader").expiresAt());
        assertArrayEqualsHash(readerHashBefore, identity(auth, "reader").tokenHash());
        assertEquals(Optional.empty(), identity(auth, "breakglass").expiresAt(),
                "an identity outside the selection must keep its current expiry policy");

        String persisted = Files.readString(auth);
        assertTrue(persisted.contains("EXPIRY_MIGRATED|reader"), "the migration must leave secret-free evidence");
        assertFalse(persisted.contains("legacy-token-reader"), "no token material may reach the file");
    }

    /** With no selection, every non-expiring identity is migrated -- except what would strand the server. */
    @Test
    void anUnfilteredMigrationCoversEveryNonExpiringIdentity() throws Exception {
        Path auth = legacyFile("reader|READ", "writer|WRITE");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN, LATER);

        MorpheusRemoteIdentityFile.LegacyMigration report =
                MorpheusRemoteIdentityFile.migrateLegacyExpiry(auth, DEADLINE, Set.of(), false);

        assertEquals(List.of("reader", "writer"), report.migrated());
        assertEquals(List.of(), report.retained(), "nothing non-expiring may remain after a full migration");
        assertEquals(Optional.of(LATER), identity(auth, "admin").expiresAt(),
                "an identity that already had an expiry must keep it");
    }

    /**
     * A remote server refuses to start without an active ADMIN, so a migration that leaves none after the
     * deadline is a lockout scheduled for that date. It is refused whole, not applied partially.
     */
    @Test
    void aMigrationThatWouldStrandEveryAdministratorIsRefusedWhole() throws Exception {
        Path auth = legacyFile("admin|ADMIN", "reader|READ");
        String before = Files.readString(auth);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                MorpheusRemoteIdentityFile.migrateLegacyExpiry(auth, DEADLINE, Set.of(), false));

        assertTrue(failure.getMessage().contains("no ADMIN identity active after"), failure.getMessage());
        assertEquals(before, Files.readString(auth), "a refused migration must change nothing at all");
    }

    /** The same refusal applies to an ADMIN whose own expiry would not outlive the deadline. */
    @Test
    void anAdministratorExpiringBeforeTheDeadlineDoesNotCountAsAWayBackIn() throws Exception {
        Path auth = legacyFile("reader|READ");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN, Instant.parse("2099-01-01T00:00:00Z"));

        assertThrows(IllegalArgumentException.class, () ->
                MorpheusRemoteIdentityFile.migrateLegacyExpiry(auth, DEADLINE, Set.of(), false));
    }

    @Test
    void aMigrationRefusesAPastDeadlineAndAnUnknownPrincipal() throws Exception {
        Path auth = legacyFile("breakglass|ADMIN", "reader|READ");

        assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.migrateLegacyExpiry(
                auth, Instant.parse("2000-01-01T00:00:00Z"), Set.of("reader"), false));
        assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.migrateLegacyExpiry(
                auth, DEADLINE, Set.of("absent"), true));
    }

    /** Migrating twice is a no-op, so an interrupted rollout can simply be run again. */
    @Test
    void aSecondMigrationFindsNothingLeftToDo() throws Exception {
        Path auth = legacyFile("breakglass|ADMIN", "reader|READ");
        MorpheusRemoteIdentityFile.migrateLegacyExpiry(auth, DEADLINE, Set.of("reader"), false);

        MorpheusRemoteIdentityFile.LegacyMigration again =
                MorpheusRemoteIdentityFile.migrateLegacyExpiry(auth, DEADLINE, Set.of("reader"), false);

        assertEquals(List.of(), again.migrated());
        assertEquals(Optional.of(DEADLINE), identity(auth, "reader").expiresAt());
    }

    /** A migrated credential still authenticates until its deadline, and stops afterwards. */
    @Test
    void aMigratedCredentialKeepsWorkingUntilItsNewDeadline() throws Exception {
        Path auth = legacyFile("breakglass|ADMIN", "reader|READ");
        MorpheusRemoteIdentityFile.migrateLegacyExpiry(auth, DEADLINE, Set.of("reader"), false);

        MorpheusRemoteIdentityFile.Identity reader = identity(auth, "reader");
        assertTrue(reader.isActiveAt(DEADLINE.minusSeconds(1)));
        assertTrue(reader.isExpiredAt(DEADLINE));
        assertTrue(MorpheusRemoteIdentityFile.authenticate(List.of(reader), "legacy-token-reader").isPresent(),
                "the token an operator already distributed must keep working");
    }

    private MorpheusRemoteIdentityFile.Identity identity(Path auth, String principal) {
        return MorpheusRemoteIdentityFile.load(auth).stream()
                .filter(candidate -> candidate.principal().equals(principal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing identity " + principal));
    }

    private void assertArrayEqualsHash(byte[] expected, byte[] actual) {
        assertEquals(
                java.util.HexFormat.of().formatHex(expected),
                java.util.HexFormat.of().formatHex(actual),
                "migration must not rotate token material");
    }

    /** Writes historical three-field entries, the format that carries no expiry at all. */
    private Path legacyFile(String... principalAndRole) throws Exception {
        Path auth = temp.resolve("legacy-auth.txt");
        StringBuilder content = new StringBuilder("# MORPHEUS remote identities" + System.lineSeparator());
        for (String entry : principalAndRole) {
            String principal = entry.substring(0, entry.indexOf('|'));
            content.append(entry)
                    .append('|')
                    .append(MorpheusRemoteIdentityFile.sha256Hex("legacy-token-" + principal))
                    .append(System.lineSeparator());
        }
        Files.writeString(auth, content.toString(), StandardCharsets.UTF_8);
        return auth;
    }
}
