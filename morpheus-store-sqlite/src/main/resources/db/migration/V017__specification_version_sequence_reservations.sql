CREATE TABLE specification_version_sequences (
    project_id TEXT PRIMARY KEY,
    last_sequence INTEGER NOT NULL CHECK(last_sequence >= 0),
    FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
);

INSERT INTO specification_version_sequences(project_id, last_sequence)
SELECT project_id, MAX(sequence)
FROM specification_versions
WHERE sequence IS NOT NULL
GROUP BY project_id;
