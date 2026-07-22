CREATE TABLE entity_identity_bindings (
    provider_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    external_id TEXT NOT NULL,
    domain_identity TEXT NOT NULL,
    PRIMARY KEY (provider_id, entity_type, external_id)
);

CREATE INDEX idx_entity_identity_bindings_domain_identity
    ON entity_identity_bindings(domain_identity);
