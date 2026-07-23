package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable audit record for a source removed from the current inventory. */
public record SourceArchiveRecord(
        ProjectSpecificationId projectId,
        SourceInventory.Entry source,
        Instant archivedAt,
        ArchiveReason reason,
        Optional<SourcePath> movedTo,
        Optional<String> sourceRevision) implements Comparable<SourceArchiveRecord> {

    public SourceArchiveRecord {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(archivedAt, "archivedAt");
        Objects.requireNonNull(reason, "reason");
        movedTo = Objects.requireNonNull(movedTo, "movedTo");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision").map(String::trim);
        if (reason == ArchiveReason.MOVED && movedTo.isEmpty()) {
            throw new IllegalArgumentException("MOVED archive requires movedTo");
        }
        if (reason == ArchiveReason.DELETED && movedTo.isPresent()) {
            throw new IllegalArgumentException("DELETED archive must not have movedTo");
        }
    }

    @Override
    public int compareTo(SourceArchiveRecord other) {
        int time = archivedAt.compareTo(other.archivedAt);
        if (time != 0) {
            return time;
        }
        int path = source.path().compareTo(other.source.path());
        if (path != 0) {
            return path;
        }
        return reason.compareTo(other.reason);
    }

    public enum ArchiveReason {
        DELETED,
        MOVED
    }
}
