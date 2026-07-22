package com.morpheus.domain.snapshot;

/** Observable lifecycle of a knowledge snapshot, validated during M0. */
public enum KnowledgeSnapshotState {
    BUILDING,
    VALIDATING,
    READY,
    ACTIVE,
    FAILED,
    RETIRED
}
