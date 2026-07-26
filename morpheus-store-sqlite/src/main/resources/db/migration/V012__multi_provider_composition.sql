CREATE TABLE composition_snapshot_state (
    snapshot_id TEXT PRIMARY KEY,
    primary_provider_id TEXT NOT NULL
);

CREATE TABLE composition_provider_state (
    snapshot_id TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    priority INTEGER NOT NULL,
    required INTEGER NOT NULL,
    available INTEGER NOT NULL,
    diagnostic_count INTEGER NOT NULL,
    PRIMARY KEY (snapshot_id, provider_id),
    FOREIGN KEY (snapshot_id) REFERENCES composition_snapshot_state(snapshot_id) ON DELETE CASCADE
);

CREATE TABLE composition_conflict (
    conflict_id INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    logical_key TEXT NOT NULL,
    field_name TEXT NOT NULL,
    resolution TEXT NOT NULL,
    selected_provider_id TEXT,
    reason TEXT NOT NULL,
    FOREIGN KEY (snapshot_id) REFERENCES composition_snapshot_state(snapshot_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX composition_conflict_unique
    ON composition_conflict(snapshot_id, entity_type, logical_key, field_name);

CREATE TABLE composition_conflict_candidate (
    conflict_id INTEGER NOT NULL,
    candidate_order INTEGER NOT NULL,
    provider_id TEXT NOT NULL,
    priority INTEGER NOT NULL,
    candidate_value TEXT NOT NULL,
    source_locator TEXT NOT NULL,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (conflict_id, candidate_order),
    FOREIGN KEY (conflict_id) REFERENCES composition_conflict(conflict_id) ON DELETE CASCADE
);
