CREATE TABLE change_lifecycle_state (
    project_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    state TEXT NOT NULL,
    abandonment_reason TEXT,
    revision INTEGER NOT NULL CHECK (revision >= 1),
    updated_at TEXT NOT NULL,
    last_mutation_id TEXT NOT NULL,
    PRIMARY KEY (project_id, change_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE change_lifecycle_mutation_audit (
    mutation_id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    command_fingerprint TEXT NOT NULL,
    from_state TEXT NOT NULL,
    target_state TEXT NOT NULL,
    target_abandonment_reason TEXT,
    from_revision INTEGER NOT NULL CHECK (from_revision >= 0),
    to_revision INTEGER NOT NULL CHECK (to_revision = from_revision + 1),
    actor TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    applied_at TEXT NOT NULL,
    UNIQUE (project_id, idempotency_key),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_change_lifecycle_audit_change
    ON change_lifecycle_mutation_audit(project_id, change_id, to_revision, mutation_id);
