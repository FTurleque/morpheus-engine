CREATE TABLE specification_versions (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    sequence INTEGER,
    provider_version TEXT,
    source_revision TEXT,
    created_at TEXT NOT NULL,
    predecessor_id TEXT,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (predecessor_id) REFERENCES specification_versions(id),
    CHECK (sequence IS NULL OR sequence > 0)
);

CREATE TABLE snapshot_specification_versions (
    snapshot_id TEXT PRIMARY KEY,
    specification_version_id TEXT NOT NULL,
    FOREIGN KEY (snapshot_id) REFERENCES knowledge_snapshots(id) ON DELETE CASCADE,
    FOREIGN KEY (specification_version_id) REFERENCES specification_versions(id),
    UNIQUE (snapshot_id, specification_version_id)
);

CREATE TABLE requirement_versions (
    entity_version_id TEXT PRIMARY KEY,
    entity_identity_id TEXT NOT NULL,
    requirement_id TEXT NOT NULL,
    specification_id TEXT NOT NULL,
    specification_version_id TEXT NOT NULL,
    snapshot_id TEXT NOT NULL,
    temporal_state TEXT NOT NULL CHECK (temporal_state IN ('CURRENT','PROPOSED','HISTORICAL')),
    requirement_key TEXT,
    title TEXT NOT NULL,
    statement TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    provider_version TEXT,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    external_id TEXT,
    source_revision TEXT,
    evidence_id TEXT NOT NULL,
    FOREIGN KEY (snapshot_id, specification_version_id)
        REFERENCES snapshot_specification_versions(snapshot_id, specification_version_id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_requirement_versions_current_snapshot_identity
    ON requirement_versions(snapshot_id, entity_identity_id)
    WHERE temporal_state = 'CURRENT';

CREATE INDEX idx_requirement_versions_snapshot
    ON requirement_versions(snapshot_id);

CREATE INDEX idx_requirement_versions_identity
    ON requirement_versions(entity_identity_id);
