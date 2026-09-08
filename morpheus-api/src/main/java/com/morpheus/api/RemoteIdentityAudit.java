package com.morpheus.api;

import com.morpheus.api.MorpheusRemoteIdentityFile.AuditRecord;
import com.morpheus.api.MorpheusRemoteIdentityFile.Mutation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Secret-free mutation evidence carried inside the identity snapshot.
 *
 * <p>The audit is evidence about credential mutations, never an authority over them. Preserving it strictly across
 * a write made it an authority: one unreadable historical line failed every later revoke and rotate while the
 * credential itself stayed valid, so the audit denied service to the operation it exists to record. Reading and
 * retaining are therefore two different operations here -- {@link #parseStrict(List)} reports what is on disk, and
 * {@link #salvage(List)} is what a mutation depends on.</p>
 *
 * <p>This component never touches the filesystem and never sees token material.</p>
 */
final class RemoteIdentityAudit {
    static final int MAX_AUDIT_RECORDS = 512;
    static final String AUDIT_PREFIX = "# audit|";

    /**
     * Subject of an {@link Mutation#AUDIT_QUARANTINED} entry.
     *
     * <p>It is a reserved name rather than an operator principal, and it is never accepted as an identity: the
     * identity parser only ever reads principals from identity lines, and the audit is a comment to it.</p>
     */
    static final String AUDIT_QUARANTINE_SUBJECT = "morpheus.audit";

    private RemoteIdentityAudit() {
    }

    /**
     * Reads the audit strictly, naming the first unreadable line.
     *
     * <p>This is the reporting surface: it says what is on disk rather than what can be salvaged from it, so a
     * corrupted history is visible instead of quietly shorter. The mutation path deliberately does not use it.</p>
     */
    static List<AuditRecord> parseStrict(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        List<AuditRecord> records = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (!line.startsWith(AUDIT_PREFIX)) continue;
            AuditRecord entry = readAudit(line);
            if (entry == null) {
                throw new IllegalArgumentException("invalid remote identity audit at line " + (index + 1));
            }
            records.add(entry);
        }
        return List.copyOf(records);
    }

    /**
     * Salvages the historical audit for retention, counting what it had to leave behind.
     *
     * <p>Unreadable entries are dropped rather than preserved, and their loss is itself recorded as an
     * {@link Mutation#AUDIT_QUARANTINED} entry by {@link #retain(RetainedAudit, List)}. Nothing from the rejected
     * line is carried into that record: a line that failed to parse is of unknown provenance, and the only safe
     * thing to say about it is that it existed.</p>
     */
    static RetainedAudit salvage(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        List<AuditRecord> records = new ArrayList<>();
        int quarantined = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (!line.startsWith(AUDIT_PREFIX)) continue;
            AuditRecord entry = readAudit(line);
            if (entry == null) quarantined++;
            else records.add(entry);
        }
        return new RetainedAudit(records, quarantined);
    }

    /**
     * Assembles the rolling window this write will persist: salvaged history, then the quarantine marker when
     * history was lost, then the records this mutation produced.
     *
     * <p>The window is trimmed from the front, so the records a mutation just produced are the last thing that can
     * ever be evicted.</p>
     */
    static List<AuditRecord> retain(RetainedAudit salvaged, List<AuditRecord> appended) {
        Objects.requireNonNull(salvaged, "salvaged");
        Objects.requireNonNull(appended, "appended");
        List<AuditRecord> retained = new ArrayList<>(salvaged.records());
        if (salvaged.quarantined() > 0) retained.add(quarantineRecord());
        retained.addAll(appended);
        int firstRetained = Math.max(0, retained.size() - MAX_AUDIT_RECORDS);
        return List.copyOf(retained.subList(firstRetained, retained.size()));
    }

    static List<String> format(List<AuditRecord> records) {
        Objects.requireNonNull(records, "records");
        return records.stream().map(RemoteIdentityAudit::formatAudit).toList();
    }

    static AuditRecord quarantineRecord() {
        return new AuditRecord(
                Instant.now(), Mutation.AUDIT_QUARANTINED, AUDIT_QUARANTINE_SUBJECT, MorpheusRemoteRole.ADMIN);
    }

    /** Returns {@code null} for an entry no reader can trust, without echoing any of its content. */
    private static AuditRecord readAudit(String line) {
        String[] fields = line.substring(AUDIT_PREFIX.length()).split("\\|", -1);
        if (fields.length != 4) return null;
        try {
            return new AuditRecord(
                    Instant.parse(fields[0]),
                    Mutation.valueOf(fields[1]),
                    fields[2],
                    MorpheusRemoteRole.valueOf(fields[3]));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static String formatAudit(AuditRecord auditRecord) {
        return AUDIT_PREFIX + auditRecord.at() + "|" + auditRecord.mutation().name() + "|"
                + auditRecord.principal() + "|" + auditRecord.role().name();
    }

    /** Historical audit entries that survived a read, and how many did not. */
    record RetainedAudit(List<AuditRecord> records, int quarantined) {
        RetainedAudit {
            records = List.copyOf(Objects.requireNonNull(records, "records"));
            if (quarantined < 0) throw new IllegalArgumentException("quarantined must not be negative");
        }
    }
}
