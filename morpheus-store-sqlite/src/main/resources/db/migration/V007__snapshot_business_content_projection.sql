CREATE TABLE snapshot_business_content (
    snapshot_id TEXT PRIMARY KEY,
    specification_version_id TEXT NOT NULL,
    FOREIGN KEY (snapshot_id) REFERENCES knowledge_snapshots(id),
    FOREIGN KEY (specification_version_id) REFERENCES specification_versions(id)
);

CREATE TABLE snapshot_evidence (
    snapshot_id TEXT NOT NULL,
    evidence_id TEXT NOT NULL,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    range_start_line INTEGER,
    range_end_line INTEGER,
    excerpt_hash TEXT,
    PRIMARY KEY (snapshot_id, evidence_id),
    FOREIGN KEY (snapshot_id) REFERENCES snapshot_business_content(snapshot_id) ON DELETE CASCADE,
    CHECK ((range_start_line IS NULL AND range_end_line IS NULL)
        OR (range_start_line >= 1 AND range_end_line >= range_start_line))
);

CREATE TABLE snapshot_specifications (
    snapshot_id TEXT NOT NULL,
    specification_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    specification_key TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    provider_id TEXT NOT NULL,
    provider_version TEXT,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    external_id TEXT,
    source_revision TEXT,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, specification_id),
    FOREIGN KEY (snapshot_id) REFERENCES snapshot_business_content(snapshot_id) ON DELETE CASCADE,
    FOREIGN KEY (snapshot_id, evidence_id) REFERENCES snapshot_evidence(snapshot_id, evidence_id)
);

CREATE TABLE snapshot_scenarios (
    snapshot_id TEXT NOT NULL,
    scenario_id TEXT NOT NULL,
    requirement_id TEXT,
    title TEXT NOT NULL,
    action TEXT NOT NULL,
    expected_outcome TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    provider_version TEXT,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    external_id TEXT,
    source_revision TEXT,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, scenario_id),
    FOREIGN KEY (snapshot_id) REFERENCES snapshot_business_content(snapshot_id) ON DELETE CASCADE,
    FOREIGN KEY (snapshot_id, evidence_id) REFERENCES snapshot_evidence(snapshot_id, evidence_id)
);

CREATE TABLE snapshot_scenario_preconditions (
    snapshot_id TEXT NOT NULL,
    scenario_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    value TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, scenario_id, ordinal),
    FOREIGN KEY (snapshot_id, scenario_id) REFERENCES snapshot_scenarios(snapshot_id, scenario_id) ON DELETE CASCADE,
    CHECK (ordinal >= 0)
);

CREATE TABLE snapshot_changes (
    snapshot_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    change_key TEXT,
    title TEXT NOT NULL,
    intent TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    provider_version TEXT,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    external_id TEXT,
    source_revision TEXT,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, change_id),
    FOREIGN KEY (snapshot_id) REFERENCES snapshot_business_content(snapshot_id) ON DELETE CASCADE,
    FOREIGN KEY (snapshot_id, evidence_id) REFERENCES snapshot_evidence(snapshot_id, evidence_id)
);

CREATE TABLE snapshot_change_scope (
    snapshot_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    value TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, change_id, ordinal),
    FOREIGN KEY (snapshot_id, change_id) REFERENCES snapshot_changes(snapshot_id, change_id) ON DELETE CASCADE,
    CHECK (ordinal >= 0)
);

CREATE TABLE snapshot_change_out_of_scope (
    snapshot_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    value TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, change_id, ordinal),
    FOREIGN KEY (snapshot_id, change_id) REFERENCES snapshot_changes(snapshot_id, change_id) ON DELETE CASCADE,
    CHECK (ordinal >= 0)
);

CREATE TABLE snapshot_change_risks (
    snapshot_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    value TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, change_id, ordinal),
    FOREIGN KEY (snapshot_id, change_id) REFERENCES snapshot_changes(snapshot_id, change_id) ON DELETE CASCADE,
    CHECK (ordinal >= 0)
);

CREATE TABLE snapshot_constraints (
    snapshot_id TEXT NOT NULL,
    constraint_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    statement TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    provider_version TEXT,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    external_id TEXT,
    source_revision TEXT,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, constraint_id),
    FOREIGN KEY (snapshot_id, change_id) REFERENCES snapshot_changes(snapshot_id, change_id),
    FOREIGN KEY (snapshot_id, evidence_id) REFERENCES snapshot_evidence(snapshot_id, evidence_id)
);

CREATE TABLE snapshot_design_decisions (
    snapshot_id TEXT NOT NULL,
    design_decision_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    title TEXT NOT NULL,
    decision TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    provider_version TEXT,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    external_id TEXT,
    source_revision TEXT,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, design_decision_id),
    FOREIGN KEY (snapshot_id, change_id) REFERENCES snapshot_changes(snapshot_id, change_id),
    FOREIGN KEY (snapshot_id, evidence_id) REFERENCES snapshot_evidence(snapshot_id, evidence_id)
);

CREATE TABLE snapshot_implementation_tasks (
    snapshot_id TEXT NOT NULL,
    task_id TEXT NOT NULL,
    change_id TEXT NOT NULL,
    task_key TEXT,
    title TEXT NOT NULL,
    completed INTEGER NOT NULL,
    provider_id TEXT NOT NULL,
    provider_version TEXT,
    source_scheme TEXT NOT NULL,
    source_value TEXT NOT NULL,
    external_id TEXT,
    source_revision TEXT,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, task_id),
    FOREIGN KEY (snapshot_id, change_id) REFERENCES snapshot_changes(snapshot_id, change_id),
    FOREIGN KEY (snapshot_id, evidence_id) REFERENCES snapshot_evidence(snapshot_id, evidence_id),
    CHECK (completed IN (0, 1))
);

CREATE INDEX idx_snapshot_specifications_snapshot ON snapshot_specifications(snapshot_id);
CREATE INDEX idx_snapshot_scenarios_snapshot ON snapshot_scenarios(snapshot_id);
CREATE INDEX idx_snapshot_changes_snapshot ON snapshot_changes(snapshot_id);
CREATE INDEX idx_snapshot_constraints_change ON snapshot_constraints(snapshot_id, change_id);
CREATE INDEX idx_snapshot_design_decisions_change ON snapshot_design_decisions(snapshot_id, change_id);
CREATE INDEX idx_snapshot_implementation_tasks_change ON snapshot_implementation_tasks(snapshot_id, change_id);
