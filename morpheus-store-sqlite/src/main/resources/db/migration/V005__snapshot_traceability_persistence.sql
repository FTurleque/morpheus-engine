CREATE TABLE traceability_links (
    link_id TEXT PRIMARY KEY,
    source_kind TEXT NOT NULL,
    source_identity_id TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    target_kind TEXT NOT NULL,
    target_identity_id TEXT NOT NULL,
    origin TEXT NOT NULL,
    resolution TEXT NOT NULL,
    confidence REAL,
    observed_at TEXT NOT NULL
);

CREATE TABLE traceability_link_evidence (
    link_id TEXT NOT NULL,
    evidence_id TEXT NOT NULL,
    PRIMARY KEY (link_id, evidence_id),
    FOREIGN KEY (link_id) REFERENCES traceability_links(link_id) ON DELETE CASCADE
);

CREATE TABLE snapshot_traceability_links (
    snapshot_id TEXT NOT NULL,
    link_id TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, link_id),
    FOREIGN KEY (snapshot_id) REFERENCES knowledge_snapshots(id) ON DELETE CASCADE,
    FOREIGN KEY (link_id) REFERENCES traceability_links(link_id) ON DELETE CASCADE
);

CREATE INDEX idx_traceability_links_source
    ON traceability_links(source_kind, source_identity_id, relation_type, link_id);

CREATE INDEX idx_traceability_links_target
    ON traceability_links(target_kind, target_identity_id, relation_type, link_id);

CREATE INDEX idx_snapshot_traceability_links_snapshot
    ON snapshot_traceability_links(snapshot_id, link_id);
