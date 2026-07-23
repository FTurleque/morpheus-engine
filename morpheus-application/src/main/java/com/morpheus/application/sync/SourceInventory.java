package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Complete immutable observation of the provider-relevant local sources for one project. */
public record SourceInventory(
        ProjectSpecificationId projectId,
        Optional<String> sourceRevision,
        Instant capturedAt,
        List<Entry> entries) {

    public SourceInventory {
        Objects.requireNonNull(projectId, "projectId");
        sourceRevision = normalizeRevision(sourceRevision);
        Objects.requireNonNull(capturedAt, "capturedAt");
        entries = Objects.requireNonNull(entries, "entries").stream()
                .peek(entry -> Objects.requireNonNull(entry, "entries item"))
                .sorted()
                .toList();
        Set<SourcePath> paths = new HashSet<>();
        for (Entry entry : entries) {
            if (!paths.add(entry.path())) {
                throw new IllegalArgumentException("duplicate source path: " + entry.path());
            }
        }
    }

    public boolean sameContentAs(SourceInventory other) {
        Objects.requireNonNull(other, "other");
        return projectId.equals(other.projectId()) && entries.equals(other.entries());
    }

    private static Optional<String> normalizeRevision(Optional<String> sourceRevision) {
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        return sourceRevision.map(String::trim).map(value -> {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("sourceRevision must not be blank when present");
            }
            return value;
        });
    }

    public record Entry(
            SourcePath path,
            SourceFingerprint fingerprint,
            long sizeBytes) implements Comparable<Entry> {
        public Entry {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(fingerprint, "fingerprint");
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes must be >= 0");
            }
        }

        public boolean sameContentAs(Entry other) {
            Objects.requireNonNull(other, "other");
            return fingerprint.equals(other.fingerprint) && sizeBytes == other.sizeBytes;
        }

        @Override
        public int compareTo(Entry other) {
            return path.compareTo(other.path);
        }
    }
}
