CREATE TABLE snapshot_acceptance_criteria (
    snapshot_id TEXT NOT NULL,
    acceptance_criterion_id TEXT NOT NULL,
    requirement_id TEXT,
    change_id TEXT,
    title TEXT NOT NULL,
    condition_text TEXT NOT NULL,
    verification_status TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    provider_version TEXT,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    external_id TEXT,
    source_revision TEXT,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, acceptance_criterion_id),
    FOREIGN KEY (snapshot_id) REFERENCES snapshot_business_content(snapshot_id) ON DELETE CASCADE,
    FOREIGN KEY (snapshot_id, change_id) REFERENCES snapshot_changes(snapshot_id, change_id),
    FOREIGN KEY (snapshot_id, evidence_id) REFERENCES snapshot_evidence(snapshot_id, evidence_id),
    CHECK (requirement_id IS NOT NULL OR change_id IS NOT NULL),
    CHECK (verification_status IN ('NOT_VERIFIED', 'PARTIALLY_VERIFIED', 'VERIFIED', 'FAILED', 'UNKNOWN'))
);

CREATE TABLE snapshot_acceptance_verification_evidence (
    snapshot_id TEXT NOT NULL,
    acceptance_criterion_id TEXT NOT NULL,
    evidence_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    PRIMARY KEY (snapshot_id, acceptance_criterion_id, ordinal),
    UNIQUE (snapshot_id, acceptance_criterion_id, evidence_id),
    FOREIGN KEY (snapshot_id, acceptance_criterion_id)
        REFERENCES snapshot_acceptance_criteria(snapshot_id, acceptance_criterion_id) ON DELETE CASCADE,
    FOREIGN KEY (snapshot_id, evidence_id)
        REFERENCES snapshot_evidence(snapshot_id, evidence_id),
    CHECK (ordinal >= 0)
);

CREATE INDEX idx_snapshot_acceptance_requirement
    ON snapshot_acceptance_criteria(snapshot_id, requirement_id);
CREATE INDEX idx_snapshot_acceptance_change
    ON snapshot_acceptance_criteria(snapshot_id, change_id);
CREATE INDEX idx_snapshot_acceptance_status
    ON snapshot_acceptance_criteria(snapshot_id, verification_status);
