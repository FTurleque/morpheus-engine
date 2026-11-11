-- A page of the CURRENT requirements of a snapshot is ordered the way UUID.compareTo orders identities: on signed
-- halves, so an identity whose text starts with '8' or above comes first. Each key below is that order as a text of
-- binary collation: '0' before an identity whose first digit is '8' or above, '1' before the others, then the
-- identity. The least significant half of an RFC 9562 identity always carries its variant in its top bits, so the
-- rest of the text already sorts in signed order. The columns are virtual: SQLite computes them from the identity,
-- no write path sets them, and the indexes below hold their values for every existing row.
ALTER TABLE requirement_versions ADD COLUMN requirement_order_key TEXT
    GENERATED ALWAYS AS ((CASE WHEN requirement_id >= '8' THEN '0' ELSE '1' END) || requirement_id) VIRTUAL;

ALTER TABLE requirement_versions ADD COLUMN identity_order_key TEXT
    GENERATED ALWAYS AS ((CASE WHEN entity_identity_id >= '8' THEN '0' ELSE '1' END) || entity_identity_id) VIRTUAL;

ALTER TABLE requirement_versions ADD COLUMN version_order_key TEXT
    GENERATED ALWAYS AS ((CASE WHEN entity_version_id >= '8' THEN '0' ELSE '1' END) || entity_version_id) VIRTUAL;

-- One index per order a page is read in. A page walks the CURRENT rows of its snapshot in the order of the page and
-- stops after its limit: the specification and the terms of a search filter the rows it walks, and no page sorts.
CREATE INDEX idx_requirement_versions_current_by_requirement
    ON requirement_versions(snapshot_id, requirement_order_key, version_order_key)
    WHERE temporal_state = 'CURRENT';

CREATE INDEX idx_requirement_versions_current_by_identity
    ON requirement_versions(snapshot_id, identity_order_key, version_order_key)
    WHERE temporal_state = 'CURRENT';
