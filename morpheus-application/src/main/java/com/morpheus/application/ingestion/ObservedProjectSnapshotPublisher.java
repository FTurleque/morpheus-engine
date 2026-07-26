package com.morpheus.application.ingestion;

import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalRecorder;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Adds local publication timing/diagnostics without changing ProjectSnapshotImportService semantics. */
public final class ObservedProjectSnapshotPublisher {
    private final ProjectSnapshotImportService delegate;
    private final OperationalRecorder recorder;

    public ObservedProjectSnapshotPublisher(
            ProjectSnapshotImportService delegate,
            OperationalRecorder recorder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public ProjectSnapshotImportResult publishFull(
            NormalizedProjectContent content,
            String sourceRevision,
            Instant createdAt) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(createdAt, "createdAt");
        OperationalRecorder.Operation operation = recorder.begin(
                "snapshot.publish",
                OperationalEventCode.SNAPSHOT_PUBLISH_STARTED,
                Map.of("projectId", content.project().id().toString()));
        try {
            ProjectSnapshotImportResult result = delegate.publishFull(content, sourceRevision, createdAt);
            recorder.metrics().add("snapshot.publish.requirement_count", result.requirementCount());
            recorder.metrics().add("snapshot.publish.traceability_link_count", result.traceabilityLinkCount());
            operation.success(
                    OperationalEventCode.SNAPSHOT_PUBLISH_COMPLETED,
                    Map.of(
                            "snapshotId", result.snapshot().id().toString(),
                            "requirementCount", Integer.toString(result.requirementCount()),
                            "traceabilityLinkCount", Integer.toString(result.traceabilityLinkCount())));
            return result;
        } catch (RuntimeException failure) {
            operation.failure(
                    OperationalEventCode.SNAPSHOT_PUBLISH_FAILED,
                    Map.of("errorType", failure.getClass().getSimpleName()));
            throw failure;
        }
    }
}
