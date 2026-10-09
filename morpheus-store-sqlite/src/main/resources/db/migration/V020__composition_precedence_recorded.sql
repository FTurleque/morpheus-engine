-- Composition is a union: a precedence is recorded, never applied. The persisted resolution value is renamed with the enum.
UPDATE composition_conflict SET resolution = 'PRECEDENCE_RECORDED' WHERE resolution = 'SELECTED_BY_PRECEDENCE';
