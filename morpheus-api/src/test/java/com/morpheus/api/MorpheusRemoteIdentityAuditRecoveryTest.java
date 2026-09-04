package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A corrupted audit history must never stand between an operator and a compromised credential.
 *
 * <p>The audit lives in the same file as the identities, as {@code # audit|} comment lines. Preserving it across
 * a mutation used to be strict, so one unreadable historical entry -- a partial write, a hand edit, a truncated
 * copy -- failed every subsequent mutation of that file. The credential itself stayed perfectly valid and
 * perfectly usable; only revoking it became impossible. These tests pin the ordering that makes that
 * impossible: credential state first, the ability to revoke and rotate second, and a faithful copy of an
 * unreadable history last.</p>
 */
class MorpheusRemoteIdentityAuditRecoveryTest {

    @TempDir
    Path temp;

    @Test
    void aMalformedHistoricalAuditDoesNotBlockRevocation() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        var compromised = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        corruptAudit(auth);

        MorpheusRemoteIdentityFile.revoke(auth, "reader");

        List<MorpheusRemoteIdentityFile.Identity> remaining = MorpheusRemoteIdentityFile.load(auth);
        assertEquals(List.of("admin"), remaining.stream()
                .map(MorpheusRemoteIdentityFile.Identity::principal)
                .toList());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(remaining, compromised.token()).isEmpty(),
                "the revoked credential must stop authenticating");
        assertTrue(MorpheusRemoteIdentityFile.authenticate(remaining, admin.token()).isPresent(),
                "revocation must not disturb the identities it was not aimed at");
    }

    @Test
    void aMalformedHistoricalAuditDoesNotBlockRotation() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        var original = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        corruptAudit(auth);

        var rotated = MorpheusRemoteIdentityFile.rotate(auth, "reader");

        assertNotEquals(original.token(), rotated.token());
        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(auth);
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, rotated.token()).isPresent());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, original.token()).isEmpty(),
                "the rotated-away credential must stop authenticating");
    }

    @Test
    void creationAlsoSurvivesACorruptedHistoryAndTheFileStaysParsableAfterwards() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        corruptAudit(auth);

        var added = MorpheusRemoteIdentityFile.create(auth, "writer", MorpheusRemoteRole.WRITE);

        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(auth);
        assertEquals(List.of("admin", "writer"), identities.stream()
                .map(MorpheusRemoteIdentityFile.Identity::principal)
                .toList());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, added.token()).isPresent());

        // The unreadable line is gone, so the strict reader works again on the very file it used to reject.
        List<MorpheusRemoteIdentityFile.AuditRecord> audit = MorpheusRemoteIdentityFile.audit(auth);
        assertTrue(audit.stream().anyMatch(record ->
                        record.mutation() == MorpheusRemoteIdentityFile.Mutation.AUDIT_QUARANTINED),
                "dropping unreadable history must itself be recorded, not left silent");
        assertEquals(MorpheusRemoteIdentityFile.Mutation.CREATE, audit.getLast().mutation());
        assertTrue(audit.size() <= MorpheusRemoteIdentityFile.MAX_AUDIT_RECORDS);
    }

    /**
     * Valid entries either side of an unreadable one are kept, and only the unreadable one is dropped.
     *
     * <p>Salvage that threw away the whole history would be a quieter version of the same problem: the operator
     * would still lose the evidence, only without being told which part was actually damaged.</p>
     */
    @Test
    void validEntriesAroundAnUnreadableOneAreRetained() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        insertAuditLineAfterFirstAuditEntry(auth, "# audit|not-an-instant|REVOKE|ghost|READ");

        MorpheusRemoteIdentityFile.revoke(auth, "reader");

        List<MorpheusRemoteIdentityFile.AuditRecord> audit = MorpheusRemoteIdentityFile.audit(auth);
        List<MorpheusRemoteIdentityFile.Mutation> mutations = audit.stream()
                .map(MorpheusRemoteIdentityFile.AuditRecord::mutation)
                .toList();
        assertEquals(
                List.of(
                        MorpheusRemoteIdentityFile.Mutation.CREATE,
                        MorpheusRemoteIdentityFile.Mutation.CREATE,
                        MorpheusRemoteIdentityFile.Mutation.AUDIT_QUARANTINED,
                        MorpheusRemoteIdentityFile.Mutation.REVOKE),
                mutations,
                "both surviving creations must be kept, with the loss recorded between them and the new entry");
        assertFalse(audit.stream().anyMatch(record -> record.principal().equals("ghost")));
    }

    /**
     * The quarantine happens once, because the damaged line is gone after the first mutation heals the file.
     */
    @Test
    void theQuarantineIsRecordedOnceRatherThanOnEveryLaterMutation() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        corruptAudit(auth);

        MorpheusRemoteIdentityFile.rotate(auth, "reader");
        MorpheusRemoteIdentityFile.revoke(auth, "reader");

        long quarantines = MorpheusRemoteIdentityFile.audit(auth).stream()
                .filter(record -> record.mutation() == MorpheusRemoteIdentityFile.Mutation.AUDIT_QUARANTINED)
                .count();
        assertEquals(1, quarantines);
    }

    /**
     * Neither the failure nor the evidence may repeat what it could not read.
     *
     * <p>An unreadable audit line is of unknown provenance -- a truncated write can leave the tail of anything in
     * it. Echoing it into a message or into the replacement record would turn a corrupted file into a disclosure
     * channel, so the only thing said about it is that it existed.</p>
     */
    @Test
    void neitherTheAuditReadFailureNorTheQuarantineEvidenceEchoesTheRejectedLine() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        String secret = "s3cret-bearer-material-that-must-not-be-echoed";
        insertAuditLineAfterFirstAuditEntry(auth, "# audit|" + secret + "|REVOKE|ghost|READ");

        IllegalArgumentException strictRead = assertThrows(
                IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.audit(auth));
        assertFalse(describe(strictRead).contains(secret),
                "the strict audit read must name the line, never quote it");

        MorpheusRemoteIdentityFile.create(auth, "writer", MorpheusRemoteRole.WRITE);

        String persisted = Files.readString(auth, StandardCharsets.UTF_8);
        assertFalse(persisted.contains(secret), "the rejected line must not survive into the healed file");
        assertTrue(persisted.contains("AUDIT_QUARANTINED|morpheus.audit|ADMIN"),
                "the loss must be recorded as a secret-free entry");
    }

    /** The salvaged history stays inside the same rolling window a healthy history is held to. */
    @Test
    void theSalvagedHistoryStaysBounded() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        corruptAudit(auth);

        for (int round = 0; round < MorpheusRemoteIdentityFile.MAX_AUDIT_RECORDS + 8; round++) {
            MorpheusRemoteIdentityFile.rotate(auth, "reader");
        }

        assertEquals(
                MorpheusRemoteIdentityFile.MAX_AUDIT_RECORDS,
                MorpheusRemoteIdentityFile.audit(auth).size());
    }

    /** A well-formed identity file whose audit tail was mangled by a partial write. */
    private void corruptAudit(Path auth) throws Exception {
        insertAuditLineAfterFirstAuditEntry(auth, "# audit|" + Instant.now() + "|REVOKE|truncated");
    }

    private void insertAuditLineAfterFirstAuditEntry(Path auth, String malformed) throws Exception {
        List<String> lines = Files.readAllLines(auth, StandardCharsets.UTF_8);
        Optional<Integer> firstAudit = firstAuditIndex(lines);
        int insertAt = firstAudit.orElseThrow(
                () -> new IllegalStateException("the fixture file must already carry an audit entry")) + 1;
        lines.add(insertAt, malformed);
        Files.writeString(auth, String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private Optional<Integer> firstAuditIndex(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith("# audit|")) return Optional.of(index);
        }
        return Optional.empty();
    }

    private String describe(Throwable failure) {
        StringBuilder described = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            described.append(current.getClass().getName()).append(':').append(current.getMessage()).append('\n');
        }
        return described.toString();
    }
}
