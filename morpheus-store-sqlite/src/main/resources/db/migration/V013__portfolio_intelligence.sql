CREATE TABLE portfolios (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE portfolio_memberships (
    portfolio_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    workspace_scheme TEXT,
    workspace_value TEXT,
    repository_scheme TEXT,
    repository_value TEXT,
    provider_ids TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE','MISSING','DISABLED')),
    first_registered_at TEXT NOT NULL,
    last_observed_at TEXT NOT NULL,
    PRIMARY KEY (portfolio_id, project_id),
    FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolio_memberships_project
    ON portfolio_memberships(project_id, portfolio_id);

CREATE TABLE portfolio_cross_project_references (
    id TEXT PRIMARY KEY,
    portfolio_id TEXT NOT NULL,
    source_project_id TEXT NOT NULL,
    source_entity_type TEXT NOT NULL,
    source_entity_id TEXT NOT NULL,
    target_project_id TEXT NOT NULL,
    target_entity_type TEXT NOT NULL,
    target_entity_id TEXT NOT NULL,
    relation TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    source_locator_scheme TEXT,
    source_locator_value TEXT,
    evidence_id TEXT,
    observed_at TEXT NOT NULL,
    FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE,
    FOREIGN KEY (portfolio_id, source_project_id)
        REFERENCES portfolio_memberships(portfolio_id, project_id),
    FOREIGN KEY (portfolio_id, target_project_id)
        REFERENCES portfolio_memberships(portfolio_id, project_id)
);

CREATE INDEX idx_portfolio_reference_source
    ON portfolio_cross_project_references(portfolio_id, source_project_id, source_entity_type, source_entity_id);

CREATE INDEX idx_portfolio_reference_target
    ON portfolio_cross_project_references(portfolio_id, target_project_id, target_entity_type, target_entity_id);

CREATE TABLE portfolio_freshness (
    portfolio_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('FRESH','STALE','MISSING','UNKNOWN')),
    observed_at TEXT NOT NULL,
    revision TEXT,
    explanation TEXT,
    PRIMARY KEY (portfolio_id, project_id),
    FOREIGN KEY (portfolio_id, project_id)
        REFERENCES portfolio_memberships(portfolio_id, project_id)
);
