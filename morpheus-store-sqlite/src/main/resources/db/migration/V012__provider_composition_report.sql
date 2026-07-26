CREATE TABLE snapshot_provider_composition (
    snapshot_id TEXT PRIMARY KEY,
    FOREIGN KEY (snapshot_id) REFERENCES knowledge_snapshots(id) ON DELETE CASCADE
);

CREATE TABLE snapshot_provider_contribution (
    snapshot_id TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    precedence INTEGER NOT NULL,
    required INTEGER NOT NULL CHECK (required IN (0, 1)),
    status TEXT NOT NULL,
    item_count INTEGER NOT NULL CHECK (item_count >= 0),
    detail TEXT,
    PRIMARY KEY (snapshot_id, provider_id),
    FOREIGN KEY (snapshot_id) REFERENCES snapshot_provider_composition(snapshot_id) ON DELETE CASCADE
);

CREATE TABLE snapshot_provider_conflict (
    snapshot_id TEXT NOT NULL,
    conflict_ordinal INTEGER NOT NULL CHECK (conflict_ordinal >= 0),
    entity_kind TEXT NOT NULL,
    logical_key TEXT NOT NULL,
    resolution TEXT NOT NULL,
    winner_provider_id TEXT,
    winner_entity_id TEXT,
    winner_precedence INTEGER,
    reason TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, conflict_ordinal),
    UNIQUE (snapshot_id, entity_kind, logical_key),
    FOREIGN KEY (snapshot_id) REFERENCES snapshot_provider_composition(snapshot_id) ON DELETE CASCADE
);

CREATE TABLE snapshot_provider_conflict_contender (
    snapshot_id TEXT NOT NULL,
    conflict_ordinal INTEGER NOT NULL,
    provider_id TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    precedence INTEGER NOT NULL,
    PRIMARY KEY (snapshot_id, conflict_ordinal, provider_id),
    FOREIGN KEY (snapshot_id, conflict_ordinal)
        REFERENCES snapshot_provider_conflict(snapshot_id, conflict_ordinal) ON DELETE CASCADE
);

CREATE INDEX idx_provider_composition_contribution
    ON snapshot_provider_contribution(snapshot_id, precedence DESC, provider_id);

CREATE INDEX idx_provider_composition_conflict
    ON snapshot_provider_conflict(snapshot_id, entity_kind, logical_key);
