CREATE TABLE saved_views (
    id TEXT PRIMARY KEY,
    scope_kind TEXT NOT NULL CHECK (scope_kind IN ('PROJECT', 'PORTFOLIO')),
    scope_id TEXT NOT NULL,
    name TEXT NOT NULL,
    query_definition TEXT NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_saved_views_scope
    ON saved_views(scope_kind, scope_id, name, id);

CREATE TABLE saved_view_versions (
    saved_view_id TEXT NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    name TEXT NOT NULL,
    query_definition TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    recorded_at TEXT NOT NULL,
    PRIMARY KEY (saved_view_id, revision),
    FOREIGN KEY (saved_view_id) REFERENCES saved_views(id)
);

CREATE INDEX idx_saved_view_versions_identity
    ON saved_view_versions(saved_view_id, revision);
