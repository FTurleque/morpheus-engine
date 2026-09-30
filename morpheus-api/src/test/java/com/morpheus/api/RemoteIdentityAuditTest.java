package com.morpheus.api;

import com.morpheus.api.MorpheusRemoteIdentityFile.AuditRecord;
import com.morpheus.api.MorpheusRemoteIdentityFile.Mutation;
import com.morpheus.api.RemoteIdentityAudit.RetainedAudit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit history is evidence, and evidence must never become an authority over the operation it records.
 *
 * <p>The strict reader exists so an operator can see a corrupted history; the salvaging reader exists so a
 * corrupted history cannot stand between that operator and a compromised credential. These two readings of the
 * same lines are deliberately different, and the difference is what these tests pin.</p>
 */
class RemoteIdentityAuditTest {
    private static final Instant AT = Instant.parse("2026-09-08T10:15:30Z");

    @Test
    void nonAuditLinesAreIgnoredByBothReaders() {
        List<String> lines = List.of(
                RemoteIdentityCodec.HEADER,
                "ops|ADMIN|" + "a".repeat(64),
                "",
                "# an ordinary comment");

        assertTrue(RemoteIdentityAudit.parseStrict(lines).isEmpty());
        assertEquals(new RetainedAudit(List.of(), 0), RemoteIdentityAudit.salvage(lines));
    }

    @Test
    void aWellFormedEntryRoundTripsThroughFormatAndBothReaders() {
        AuditRecord record = new AuditRecord(AT, Mutation.ROTATE, "ops", MorpheusRemoteRole.ADMIN);

        List<String> lines = RemoteIdentityAudit.format(List.of(record));

        assertEquals(List.of("# audit|" + AT + "|ROTATE|ops|ADMIN"), lines);
        assertEquals(List.of(record), RemoteIdentityAudit.parseStrict(lines));
        assertEquals(new RetainedAudit(List.of(record), 0), RemoteIdentityAudit.salvage(lines));
    }

    @Test
    void theStrictReaderNamesTheFirstUnreadableLine() {
        List<String> lines = new ArrayList<>();
        lines.add(RemoteIdentityCodec.HEADER);
        lines.add("# audit|" + AT + "|CREATE|ops|ADMIN");
        lines.add("# audit|truncated");

        assertTrue(assertThrows(IllegalArgumentException.class, () -> RemoteIdentityAudit.parseStrict(lines))
                .getMessage().contains("invalid remote identity audit at line 3"));
    }

    @Test
    void everyShapeOfCorruptionIsSalvagedRatherThanTrusted() {
        List<String> corrupted = List.of(
                "# audit|too|few",
                "# audit|" + AT + "|CREATE|ops|ADMIN|extra",
                "# audit|not-an-instant|CREATE|ops|ADMIN",
                "# audit|" + AT + "|NOT_A_MUTATION|ops|ADMIN",
                "# audit|" + AT + "|CREATE|not a principal|ADMIN",
                "# audit|" + AT + "|CREATE|ops|SUPERUSER");

        RetainedAudit salvaged = RemoteIdentityAudit.salvage(corrupted);

        assertTrue(salvaged.records().isEmpty());
        assertEquals(corrupted.size(), salvaged.quarantined());
        for (String line : corrupted) {
            assertThrows(IllegalArgumentException.class, () -> RemoteIdentityAudit.parseStrict(List.of(line)));
        }
    }

    @Test
    void salvagingKeepsReadableHistoryAndCountsWhatItDropped() {
        AuditRecord readable = new AuditRecord(AT, Mutation.CREATE, "ops", MorpheusRemoteRole.ADMIN);

        RetainedAudit salvaged = RemoteIdentityAudit.salvage(List.of(
                "# audit|" + AT + "|CREATE|ops|ADMIN",
                "# audit|garbage",
                "# audit|" + AT + "|REVOKE|ops|ADMIN"));

        assertEquals(List.of(readable, new AuditRecord(AT, Mutation.REVOKE, "ops", MorpheusRemoteRole.ADMIN)),
                salvaged.records());
        assertEquals(1, salvaged.quarantined());
    }

    @Test
    void droppingHistoryLeavesEvidenceThatNamesAReservedSubjectAndQuotesNothing() {
        RetainedAudit salvaged = RemoteIdentityAudit.salvage(List.of("# audit|secret-looking-garbage"));
        AuditRecord mutation = new AuditRecord(AT, Mutation.REVOKE, "ops", MorpheusRemoteRole.ADMIN);

        List<AuditRecord> retained = RemoteIdentityAudit.retain(salvaged, List.of(mutation));

        assertEquals(2, retained.size());
        assertEquals(Mutation.AUDIT_QUARANTINED, retained.get(0).mutation());
        assertEquals(RemoteIdentityAudit.AUDIT_QUARANTINE_SUBJECT, retained.get(0).principal());
        assertEquals(mutation, retained.get(1));
        assertFalse(retained.toString().contains("secret-looking-garbage"),
                "a line that failed to parse is of unknown provenance and must never be echoed");
    }

    @Test
    void intactHistoryProducesNoQuarantineMarker() {
        RetainedAudit salvaged = RemoteIdentityAudit.salvage(List.of("# audit|" + AT + "|CREATE|ops|ADMIN"));

        List<AuditRecord> retained = RemoteIdentityAudit.retain(
                salvaged, List.of(new AuditRecord(AT, Mutation.ROTATE, "ops", MorpheusRemoteRole.ADMIN)));

        assertEquals(2, retained.size());
        assertFalse(retained.stream().anyMatch(record -> record.mutation() == Mutation.AUDIT_QUARANTINED));
    }

    @Test
    void theWindowIsTrimmedFromTheFrontSoTheNewestEvidenceAlwaysSurvives() {
        List<AuditRecord> history = new ArrayList<>();
        for (int index = 0; index < RemoteIdentityAudit.MAX_AUDIT_RECORDS + 64; index++) {
            history.add(new AuditRecord(AT.plusSeconds(index), Mutation.CREATE, "p" + index, MorpheusRemoteRole.READ));
        }
        AuditRecord newest = new AuditRecord(AT.plusSeconds(100_000), Mutation.REVOKE, "ops", MorpheusRemoteRole.ADMIN);

        List<AuditRecord> retained = RemoteIdentityAudit.retain(new RetainedAudit(history, 0), List.of(newest));

        assertEquals(RemoteIdentityAudit.MAX_AUDIT_RECORDS, retained.size());
        assertEquals(newest, retained.get(retained.size() - 1));
        assertFalse(retained.contains(history.get(0)), "the oldest evidence is what a full window gives up");
    }

    /**
     * A mutation that alone overflows the window still keeps its own records: evicting the evidence a write just
     * produced would make the bound erase exactly what it exists to preserve.
     */
    @Test
    void aMutationLargerThanTheWindowKeepsItsOwnMostRecentRecords() {
        List<AuditRecord> appended = new ArrayList<>();
        for (int index = 0; index < RemoteIdentityAudit.MAX_AUDIT_RECORDS + 8; index++) {
            appended.add(new AuditRecord(
                    AT.plusSeconds(index), Mutation.EXPIRY_MIGRATED, "p" + index, MorpheusRemoteRole.READ));
        }

        List<AuditRecord> retained = RemoteIdentityAudit.retain(new RetainedAudit(List.of(), 0), appended);

        assertEquals(RemoteIdentityAudit.MAX_AUDIT_RECORDS, retained.size());
        assertEquals(appended.get(appended.size() - 1), retained.get(retained.size() - 1));
    }

    /**
     * The quarantine marker names a principal, so the one thing it must never do is read back as one.
     *
     * <p>It is written as a comment, and the identity parser only ever reads principals from identity lines --
     * that is what keeps a reserved audit subject from becoming a credential nobody created.</p>
     */
    @Test
    void theQuarantineRecordIsAnAuditCommentAndNeverAnIdentityEntry() {
        List<String> lines = RemoteIdentityAudit.format(List.of(RemoteIdentityAudit.quarantineRecord()));

        assertTrue(lines.get(0).startsWith(RemoteIdentityAudit.AUDIT_PREFIX));
        assertTrue(lines.get(0).contains(RemoteIdentityAudit.AUDIT_QUARANTINE_SUBJECT));
        assertTrue(RemoteIdentityCodec.parse(lines).isEmpty(),
                "an audit line must never be parsed as a credential");
    }

    @Test
    void aNegativeQuarantineCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RetainedAudit(List.of(), -1));
        assertThrows(NullPointerException.class, () -> new RetainedAudit(null, 0));
    }
}
