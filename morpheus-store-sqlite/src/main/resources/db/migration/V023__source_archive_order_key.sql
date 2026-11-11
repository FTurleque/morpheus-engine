-- sync_source_archives.archived_at holds Instant.toString(), whose text does not sort by time when the fraction varies
-- in length (':00.500100Z' sorts before ':00.500Z', ':00Z' after both). archived_key is the instant as a fixed-width
-- text that does, the format of policy_audit.at_key: thirteen digits of epoch seconds, a dot, nine digits of
-- nanoseconds. The stored text of archived_at is untouched.
ALTER TABLE sync_source_archives ADD COLUMN archived_key TEXT;

UPDATE sync_source_archives
SET archived_key = printf('%013d', CAST(strftime('%s', substr(archived_at, 1, 19)) AS INTEGER)) || '.' ||
    substr(
        (CASE WHEN instr(archived_at, '.') > 0
              THEN substr(archived_at, instr(archived_at, '.') + 1, length(archived_at) - instr(archived_at, '.') - 1)
              ELSE '' END) || '000000000',
        1, 9);

-- The archives of a project are read one page at a time, by instant, path, reason, then fingerprint: the order of
-- SourceArchiveRecord. The index delivers that order, so a page stops after its limit instead of sorting the project.
CREATE INDEX idx_sync_source_archives_project_order
    ON sync_source_archives(project_id, archived_key, source_path, reason, fingerprint_sha256);

-- No statement searches the archives by the text of archived_at: the index on it is maintained on every write and
-- read by nothing.
DROP INDEX idx_sync_source_archives_project_time;
