ALTER TABLE snapshot_constraints ADD COLUMN applicability TEXT NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE snapshot_constraints ADD COLUMN severity TEXT NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE snapshot_constraints ADD COLUMN satisfaction TEXT NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE snapshot_constraints ADD COLUMN blocking_mode TEXT NOT NULL DEFAULT 'UNKNOWN';

CREATE TABLE snapshot_constraint_blocking_targets (
    snapshot_id TEXT NOT NULL,
    constraint_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    target_state TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, constraint_id, ordinal),
    FOREIGN KEY (snapshot_id, constraint_id)
        REFERENCES snapshot_constraints(snapshot_id, constraint_id) ON DELETE CASCADE,
    CHECK (ordinal >= 0)
);

CREATE TABLE snapshot_constraint_supporting_evidence (
    snapshot_id TEXT NOT NULL,
    constraint_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, constraint_id, ordinal),
    FOREIGN KEY (snapshot_id, constraint_id)
        REFERENCES snapshot_constraints(snapshot_id, constraint_id) ON DELETE CASCADE,
    FOREIGN KEY (snapshot_id, evidence_id)
        REFERENCES snapshot_evidence(snapshot_id, evidence_id),
    CHECK (ordinal >= 0)
);

CREATE INDEX idx_snapshot_constraint_blocking_targets
    ON snapshot_constraint_blocking_targets(snapshot_id, constraint_id);
CREATE INDEX idx_snapshot_constraint_supporting_evidence
    ON snapshot_constraint_supporting_evidence(snapshot_id, constraint_id);
