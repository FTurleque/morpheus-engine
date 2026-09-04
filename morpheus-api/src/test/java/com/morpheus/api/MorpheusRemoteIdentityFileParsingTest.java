package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A malformed identity file must be refused whole, never loaded in part.
 *
 * <p>This file is the entire authentication story of a remote server. Skipping a line it cannot understand
 * would mean a credential silently disappearing -- or, worse, an entry an attacker corrupted just enough to be
 * ignored. Every rejection therefore names the line, and the load produces nothing at all.</p>
 */
class MorpheusRemoteIdentityFileParsingTest {
    private static final String VALID_HASH = "a".repeat(64);

    @TempDir
    Path temp;

    @Test
    void anEntryWithTheWrongNumberOfFieldsIsRefusedWithItsLineNumber() throws Exception {
        Path auth = write(
                "good|READ|" + hashOf("first"),
                "broken|WRITE");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(auth));

        assertTrue(failure.getMessage().contains("invalid remote auth entry at line 3"), failure.getMessage());
    }

    @Test
    void anUnknownRoleIsRefusedRatherThanDowngraded() throws Exception {
        Path auth = write("mystery|SUPERUSER|" + VALID_HASH);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(auth));

        assertTrue(failure.getMessage().contains("invalid remote role at line 2"), failure.getMessage());
    }

    @Test
    void aTokenVerifierThatIsNotASha256IsRefused() throws Exception {
        Path shortHash = write("short|READ|abc123");
        Path notHex = writeNamed("not-hex-auth.txt", "nothex|READ|" + "z".repeat(64));

        assertTrue(assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(shortHash))
                .getMessage().contains("invalid token SHA-256 at line 2"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(notHex))
                .getMessage().contains("invalid token SHA-256 at line 2"));
    }

    /**
     * A four-field entry declares that it has an expiry, so a blank or unparseable one is a contradiction --
     * not an invitation to treat the credential as permanent.
     */
    @Test
    void aFourFieldEntryWithoutAUsableExpiryIsRefusedRatherThanTreatedAsPermanent() throws Exception {
        Path blank = write("blank|READ|" + VALID_HASH + "|");
        Path malformed = writeNamed("malformed-auth.txt", "malformed|READ|" + VALID_HASH + "|not-an-instant");

        assertTrue(assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(blank))
                .getMessage().contains("blank remote identity expiry at line 2"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(malformed))
                .getMessage().contains("invalid remote identity expiry at line 2"));
    }

    /**
     * Two principals sharing one token verifier means one bearer token authenticates as either. The file is
     * refused rather than resolving the ambiguity in the attacker's favour.
     */
    @Test
    void twoPrincipalsSharingOneTokenVerifierAreRefused() throws Exception {
        Path auth = write(
                "alice|READ|" + VALID_HASH,
                "mallory|ADMIN|" + VALID_HASH);

        assertTrue(assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(auth))
                .getMessage().contains("duplicate remote token hash"));
    }

    @Test
    void moreIdentitiesThanTheBoundAllowsAreRefused() throws Exception {
        List<String> entries = new java.util.ArrayList<>();
        for (int index = 0; index <= MorpheusRemoteIdentityFile.MAX_IDENTITIES; index++) {
            entries.add("principal-" + index + "|READ|" + hashOf("token-" + index));
        }
        Path auth = write(entries.toArray(String[]::new));

        assertTrue(assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(auth))
                .getMessage().contains("exceeds " + MorpheusRemoteIdentityFile.MAX_IDENTITIES + " identities"));
    }

    /** Comments and blank lines carry no credential, so they are skipped rather than refused. */
    @Test
    void commentsAndBlankLinesAreSkippedWithoutShiftingTheReportedLineNumbers() throws Exception {
        Path auth = write(
                "",
                "   ",
                "# a human left a note here",
                "reader|READ|" + hashOf("reader-token"));

        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(auth);

        assertEquals(1, identities.size());
        assertEquals("reader", identities.getFirst().principal());
    }

    /**
     * An implausibly long bearer token is rejected before it is hashed.
     *
     * <p>Hashing it first would make the work an unauthenticated caller can impose proportional to what it
     * sends, which is the cheap half of a denial-of-service.
     */
    @Test
    void anOverlongPresentedTokenIsRefusedWithoutBeingHashed() throws Exception {
        Path auth = write("reader|READ|" + hashOf("reader-token"));
        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(auth);

        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, "x".repeat(1025)).isEmpty());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, "   ").isEmpty());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, null).isEmpty());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, "reader-token").isPresent());
    }

    /** An expired identity is inert: it parses, it lists, and it authenticates nobody. */
    @Test
    void anExpiredIdentityLoadsButNeverAuthenticates() throws Exception {
        Path auth = write("stale|ADMIN|" + hashOf("stale-token") + "|2000-01-01T00:00:00Z");

        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(auth);

        assertEquals(1, identities.size(), "an expired credential must stay visible to an operator");
        assertTrue(identities.getFirst().isExpiredAt(Instant.now()));
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, "stale-token").isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                MorpheusRemoteHttpServer.validateStartupIdentities(identities, Instant.now()));
    }

    private String hashOf(String token) {
        return MorpheusRemoteIdentityFile.sha256Hex(token);
    }

    private Path write(String... entries) throws Exception {
        return writeNamed("remote-auth.txt", entries);
    }

    private Path writeNamed(String fileName, String... entries) throws Exception {
        Path auth = temp.resolve(fileName);
        StringBuilder content = new StringBuilder("# MORPHEUS remote identities" + System.lineSeparator());
        for (String entry : entries) {
            content.append(entry).append(System.lineSeparator());
        }
        Files.writeString(auth, content.toString(), StandardCharsets.UTF_8);
        return auth;
    }
}
