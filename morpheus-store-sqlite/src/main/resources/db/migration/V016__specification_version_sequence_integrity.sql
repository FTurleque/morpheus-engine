WITH ranked AS (
    SELECT
        id,
        project_id,
        sequence,
        created_at,
        ROW_NUMBER() OVER (
            PARTITION BY project_id, sequence
            ORDER BY created_at, id
        ) AS duplicate_rank
    FROM specification_versions
    WHERE sequence IS NOT NULL
),
project_max AS (
    SELECT project_id, COALESCE(MAX(sequence), 0) AS max_sequence
    FROM specification_versions
    GROUP BY project_id
),
repairs AS (
    SELECT
        ranked.id,
        project_max.max_sequence + ROW_NUMBER() OVER (
            PARTITION BY ranked.project_id
            ORDER BY ranked.sequence, ranked.created_at, ranked.id
        ) AS new_sequence
    FROM ranked
    JOIN project_max ON project_max.project_id = ranked.project_id
    WHERE ranked.duplicate_rank > 1
)
UPDATE specification_versions
SET sequence = (
    SELECT repairs.new_sequence
    FROM repairs
    WHERE repairs.id = specification_versions.id
)
WHERE id IN (SELECT id FROM repairs);

CREATE UNIQUE INDEX uq_specification_versions_project_sequence
    ON specification_versions(project_id, sequence)
    WHERE sequence IS NOT NULL;
