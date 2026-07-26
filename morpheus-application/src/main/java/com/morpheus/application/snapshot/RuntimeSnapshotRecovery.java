package com.morpheus.application.snapshot;

import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit composition-root hook for stale technical candidate recovery. It is never triggered by read-only store access. */
public final class RuntimeSnapshotRecovery {
    private final SpecificationKnowledgeStore store;
    private final SnapshotRecoveryService recovery;
    private final SnapshotRecoveryPolicy policy;

    public RuntimeSnapshotRecovery(SpecificationKnowledgeStore store) {
        this(store, new SnapshotRecoveryService(store), SnapshotRecoveryPolicy.safeDefaults());
    }

    public RuntimeSnapshotRecovery(
            SpecificationKnowledgeStore store,
            SnapshotRecoveryService recovery,
            SnapshotRecoveryPolicy policy) {
        this.store = Objects.requireNonNull(store, "store");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public Map<ProjectSpecificationId, SnapshotRecoveryService.RecoveryReport> recoverAll(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant cutoff = policy.cutoffAt(now);
        LinkedHashMap<ProjectSpecificationId, SnapshotRecoveryService.RecoveryReport> reports = new LinkedHashMap<>();
        store.listProjects().stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .forEach(project -> reports.put(
                        project.id(),
                        recovery.recoverStaleCandidates(project.id(), cutoff)));
        return java.util.Collections.unmodifiableMap(reports);
    }
}
