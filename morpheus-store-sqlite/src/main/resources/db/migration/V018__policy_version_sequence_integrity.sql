CREATE TRIGGER trg_policy_packs_revision_step
BEFORE UPDATE OF revision, latest_version_number ON policy_packs
FOR EACH ROW
WHEN NEW.revision <> OLD.revision + 1
   OR NEW.latest_version_number <> OLD.latest_version_number + 1
BEGIN
    SELECT RAISE(ABORT, 'policy update must advance revision and version by exactly one');
END;

CREATE TRIGGER trg_policy_pack_versions_latest
BEFORE INSERT ON policy_pack_versions
FOR EACH ROW
WHEN NEW.version_number <> (
    SELECT latest_version_number
    FROM policy_packs
    WHERE id = NEW.pack_id
)
BEGIN
    SELECT RAISE(ABORT, 'policy version number must match pack latest version number');
END;
