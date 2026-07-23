CREATE TABLE snapshot_external_references (
    snapshot_id TEXT NOT NULL,
    reference_id TEXT NOT NULL,
    owner_identity_id TEXT NOT NULL,
    system TEXT NOT NULL,
    project TEXT,
    resource_type TEXT NOT NULL,
    external_id TEXT NOT NULL,
    revision TEXT,
    resolution_state TEXT NOT NULL,
    resolution_reason TEXT NOT NULL,
    resolved_system TEXT,
    resolved_project TEXT,
    resolved_resource_type TEXT,
    resolved_external_id TEXT,
    resolved_revision TEXT,
    provenance_provider_id TEXT,
    provenance_provider_version TEXT,
    provenance_source_scheme TEXT,
    provenance_source_value TEXT,
    provenance_external_id TEXT,
    provenance_source_revision TEXT,
    provenance_evidence_id TEXT,
    PRIMARY KEY (snapshot_id, reference_id),
    FOREIGN KEY (snapshot_id) REFERENCES knowledge_snapshots(id) ON DELETE CASCADE
);

CREATE TABLE snapshot_external_reference_attributes (
    snapshot_id TEXT NOT NULL,
    reference_id TEXT NOT NULL,
    attribute_key TEXT NOT NULL,
    attribute_value TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, reference_id, attribute_key),
    FOREIGN KEY (snapshot_id, reference_id)
        REFERENCES snapshot_external_references(snapshot_id, reference_id) ON DELETE CASCADE
);

CREATE TABLE snapshot_external_reference_history (
    snapshot_id TEXT NOT NULL,
    reference_id TEXT NOT NULL,
    event_index INTEGER NOT NULL,
    previous_state TEXT NOT NULL,
    new_state TEXT NOT NULL,
    reason TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, reference_id, event_index),
    FOREIGN KEY (snapshot_id, reference_id)
        REFERENCES snapshot_external_references(snapshot_id, reference_id) ON DELETE CASCADE
);

CREATE INDEX idx_snapshot_external_references_owner
    ON snapshot_external_references(snapshot_id, owner_identity_id, reference_id);
