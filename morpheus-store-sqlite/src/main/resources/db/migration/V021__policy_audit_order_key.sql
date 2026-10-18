-- policy_audit.at holds Instant.toString(), whose text does not sort by time when the fraction varies in length
-- (':00.500100Z' sorts before ':00.500Z', ':00Z' after both). at_key is the instant as a fixed-width text that does:
-- thirteen digits of epoch seconds, a dot, nine digits of nanoseconds. The stored text of at is untouched.
ALTER TABLE policy_audit ADD COLUMN at_key TEXT;

UPDATE policy_audit
SET at_key = printf('%013d', CAST(strftime('%s', substr(at, 1, 19)) AS INTEGER)) || '.' ||
    substr(
        (CASE WHEN instr(at, '.') > 0
              THEN substr(at, instr(at, '.') + 1, length(at) - instr(at, '.') - 1)
              ELSE '' END) || '000000000',
        1, 9);

CREATE INDEX idx_policy_audit_pack_order
    ON policy_audit(pack_id, at_key, id);
