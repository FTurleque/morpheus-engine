package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlitePolicyVersionSequenceIntegrityTest {

    @TempDir
    Path tempDir;

    @Test
    void schemaRejectsPolicyRevisionAndVersionNumberJumps() throws Exception {
        Path database = tempDir.resolve("policy-sequence.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // Apply the canonical schema including V018.
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO policy_packs(id, name, revision, latest_version_number, created_at, updated_at)
                    VALUES ('pack', 'Pack', 1, 1, '2026-09-06T17:00:00Z', '2026-09-06T17:00:00Z')
                    """);
            statement.executeUpdate("""
                    INSERT INTO policy_pack_versions(pack_id, version_id, version_number, encoded_version, created_at)
                    VALUES ('pack', 'version-1', 1, 'encoded', '2026-09-06T17:00:00Z')
                    """);

            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    UPDATE policy_packs
                    SET revision = 3, latest_version_number = 3, updated_at = '2026-09-06T17:00:01Z'
                    WHERE id = 'pack'
                    """));

            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO policy_pack_versions(pack_id, version_id, version_number, encoded_version, created_at)
                    VALUES ('pack', 'version-2', 2, 'encoded', '2026-09-06T17:00:01Z')
                    """));
        }
    }
}
