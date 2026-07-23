CREATE TABLE sync_state (
    project_id TEXT PRIMARY KEY,
    last_attempt_at TEXT,
    last_successful_sync_at TEXT,
    last_observed_change_at TEXT,
    source_revision TEXT,
    last_successful_mode TEXT,
    pending_full_rebuild_reason TEXT,
    inventory_captured_at TEXT,
    current_source_count INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE sync_inventory_entries (
    project_id TEXT NOT NULL,
    source_path TEXT NOT NULL,
    fingerprint_sha256 TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    PRIMARY KEY(project_id, source_path),
    FOREIGN KEY(project_id) REFERENCES sync_state(project_id) ON DELETE CASCADE
);

CREATE TABLE sync_source_archives (
    project_id TEXT NOT NULL,
    source_path TEXT NOT NULL,
    fingerprint_sha256 TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    archived_at TEXT NOT NULL,
    reason TEXT NOT NULL,
    moved_to_path TEXT,
    source_revision TEXT,
    PRIMARY KEY(project_id, source_path, fingerprint_sha256, archived_at, reason),
    FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_sync_inventory_entries_project ON sync_inventory_entries(project_id);
CREATE INDEX idx_sync_source_archives_project_time ON sync_source_archives(project_id, archived_at);
