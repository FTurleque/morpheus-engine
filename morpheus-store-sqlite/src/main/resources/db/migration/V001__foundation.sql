CREATE TABLE projects (
    id TEXT PRIMARY KEY,
    root_scheme TEXT NOT NULL,
    root_value TEXT NOT NULL
);

CREATE TABLE knowledge_snapshots (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    predecessor_id TEXT,
    state TEXT NOT NULL CHECK (state IN ('BUILDING','VALIDATING','READY','ACTIVE','FAILED','RETIRED')),
    source_revision TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (predecessor_id) REFERENCES knowledge_snapshots(id)
);

CREATE INDEX idx_knowledge_snapshots_project_state
    ON knowledge_snapshots(project_id, state);

CREATE UNIQUE INDEX uq_knowledge_snapshots_active_project
    ON knowledge_snapshots(project_id)
    WHERE state = 'ACTIVE';
