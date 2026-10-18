-- Eleven indexes repeat the leading columns of another index of their table: the index SQLite builds for a PRIMARY KEY or
-- a UNIQUE constraint already answers every search they answer, and each one is maintained on every write of its table.
-- DROP INDEX has no IF EXISTS on purpose: a database that reaches this script has replayed the scripts that created them.
DROP INDEX idx_saved_view_versions_identity;
DROP INDEX idx_snapshot_traceability_links_snapshot;
DROP INDEX idx_policy_pack_activations_scope;
DROP INDEX idx_policy_overrides_scope;
DROP INDEX idx_policy_pack_versions_pack;
DROP INDEX idx_sync_inventory_entries_project;
DROP INDEX idx_snapshot_constraint_blocking_targets;
DROP INDEX idx_snapshot_constraint_supporting_evidence;
DROP INDEX idx_snapshot_specifications_snapshot;
DROP INDEX idx_snapshot_scenarios_snapshot;
DROP INDEX idx_snapshot_changes_snapshot;

-- The two reads of the CURRENT requirement versions of a snapshot return them in entity version order, the order the
-- port promises. The partial index delivers that order, so the bounded read stops after its limit instead of sorting
-- every CURRENT row of the snapshot.
CREATE INDEX idx_requirement_versions_current_order
    ON requirement_versions(snapshot_id, entity_version_id)
    WHERE temporal_state = 'CURRENT';

-- The saved views of a scope are listed by name without regard to case, then by id. The index carries that collation,
-- so it serves the scope filter, the order and the count that enforces the per-scope budget.
DROP INDEX idx_saved_views_scope;
CREATE INDEX idx_saved_views_scope
    ON saved_views(scope_kind, scope_id, name COLLATE NOCASE, id);
