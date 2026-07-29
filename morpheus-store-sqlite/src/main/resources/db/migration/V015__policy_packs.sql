CREATE TABLE policy_packs (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    latest_version_number INTEGER NOT NULL CHECK (latest_version_number > 0),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE policy_pack_versions (
    pack_id TEXT NOT NULL,
    version_id TEXT PRIMARY KEY,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    encoded_version TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (pack_id) REFERENCES policy_packs(id),
    UNIQUE (pack_id, version_number)
);

CREATE INDEX idx_policy_pack_versions_pack
    ON policy_pack_versions(pack_id, version_number);

CREATE TABLE policy_pack_activations (
    scope_kind TEXT NOT NULL,
    scope_id TEXT NOT NULL,
    pack_id TEXT NOT NULL,
    version_id TEXT NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    actor TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (scope_kind, scope_id, pack_id),
    FOREIGN KEY (pack_id) REFERENCES policy_packs(id),
    FOREIGN KEY (version_id) REFERENCES policy_pack_versions(version_id)
);

CREATE INDEX idx_policy_pack_activations_scope
    ON policy_pack_activations(scope_kind, scope_id, pack_id);

CREATE TABLE policy_overrides (
    scope_kind TEXT NOT NULL,
    scope_id TEXT NOT NULL,
    pack_id TEXT NOT NULL,
    rule_id TEXT NOT NULL,
    mode TEXT NOT NULL,
    reason TEXT NOT NULL,
    actor TEXT NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    updated_at TEXT NOT NULL,
    PRIMARY KEY (scope_kind, scope_id, pack_id, rule_id),
    FOREIGN KEY (pack_id) REFERENCES policy_packs(id)
);

CREATE INDEX idx_policy_overrides_scope
    ON policy_overrides(scope_kind, scope_id, pack_id, rule_id);

CREATE TABLE policy_audit (
    id TEXT PRIMARY KEY,
    action TEXT NOT NULL,
    pack_id TEXT NOT NULL,
    version_id TEXT,
    rule_id TEXT,
    scope_kind TEXT,
    scope_id TEXT,
    actor TEXT NOT NULL,
    reason TEXT NOT NULL,
    at TEXT NOT NULL,
    FOREIGN KEY (pack_id) REFERENCES policy_packs(id)
);

CREATE INDEX idx_policy_audit_pack
    ON policy_audit(pack_id, at, id);