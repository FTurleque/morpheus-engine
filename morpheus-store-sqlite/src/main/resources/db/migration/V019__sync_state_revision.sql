-- Synchronization state is an authority written read-modify-write by concurrent syncs. Existing rows start at 1
-- (a row exists, revision 0 means "no row"); every write states the revision it read and advances it by one.
ALTER TABLE sync_state ADD COLUMN revision INTEGER NOT NULL DEFAULT 1;
