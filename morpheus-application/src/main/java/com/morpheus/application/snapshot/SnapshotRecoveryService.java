package com.morpheus.application.snapshot;

import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Marks stale technical candidates left by interrupted work as FAILED without touching published history.
 * The explicit cutoff prevents a concurrent process from invalidating a fresh candidate merely because it exists.
 */
public final class SnapshotRecoveryService {
    private final SpecificationKnowledgeStore store;
    private final OperationalRecorder recorder;

    public SnapshotRecoveryService(SpecificationKnowledgeStore store) {
        this(store, OperationalRecorder.noop());
    }

    public SnapshotRecoveryService(SpecificationKnowledgeStore store, OperationalRecorder recorder) {
        this.store = Objects.requireNonNull(store, "store");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public RecoveryReport recoverStaleCandidates(ProjectSpecificationId projectId, Instant staleAtOrBefore) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(staleAtOrBefore, "staleAtOrBefore");
        OperationalRecorder.Operation operation = recorder.begin(
                "snapshot.recovery",
                OperationalEventCode.RECOVERY_STARTED,
                Map.of("projectId", projectId.toString(), "staleAtOrBefore", staleAtOrBefore.toString()));
        try {
            List<KnowledgeSnapshotId> recovered = new ArrayList<>();
            List<KnowledgeSnapshotId> raced = new ArrayList<>();
            for (KnowledgeSnapshotMetadata snapshot : store.listSnapshots(projectId)) {
                if (!isRecoverable(snapshot, staleAtOrBefore)) {
                    continue;
                }
                try {
                    store.transitionSnapshotState(snapshot.id(), snapshot.state(), KnowledgeSnapshotState.FAILED);
                    recovered.add(snapshot.id());
                    recorder.metrics().increment("snapshot.recovery.candidate_failed");
                } catch (SnapshotConflictException conflict) {
                    raced.add(snapshot.id());
                    recorder.metrics().increment("snapshot.recovery.race");
                }
            }
            RecoveryReport report = new RecoveryReport(recovered, raced);
            operation.success(
                    OperationalEventCode.RECOVERY_COMPLETED,
                    Map.of(
                            "recoveredCount", Integer.toString(report.recoveredCandidates().size()),
                            "raceCount", Integer.toString(report.racedCandidates().size())));
            return report;
        } catch (RuntimeException failure) {
            operation.failure(
                    OperationalEventCode.RECOVERY_FAILED,
                    Map.of("errorType", failure.getClass().getSimpleName()));
            throw failure;
        }
    }

    private boolean isRecoverable(KnowledgeSnapshotMetadata snapshot, Instant staleAtOrBefore) {
        boolean technicalState = snapshot.state() == KnowledgeSnapshotState.BUILDING
                || snapshot.state() == KnowledgeSnapshotState.VALIDATING;
        return technicalState && !snapshot.createdAt().isAfter(staleAtOrBefore);
    }

    public record RecoveryReport(
            List<KnowledgeSnapshotId> recoveredCandidates,
            List<KnowledgeSnapshotId> racedCandidates) {
        public RecoveryReport {
            recoveredCandidates = List.copyOf(Objects.requireNonNull(recoveredCandidates, "recoveredCandidates"));
            racedCandidates = List.copyOf(Objects.requireNonNull(racedCandidates, "racedCandidates"));
        }
    }
}
