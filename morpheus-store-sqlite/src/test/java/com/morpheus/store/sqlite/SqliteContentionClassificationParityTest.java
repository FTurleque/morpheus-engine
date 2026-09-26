package com.morpheus.store.sqlite;

import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version sequence retry loop and the operational contention metrics must agree on what contention is.
 *
 * <p>They did not: the retry loop carried a private predicate that recognized only {@code SQLITE_BUSY} (code 5)
 * and that one spelling, while {@link SqliteFailureClassifier} recognized {@code SQLITE_LOCKED} (code 6) and
 * three further spellings. A locked-table failure was therefore counted as contention and not retried. The loop
 * now delegates to the classifier, which widens what it retries -- so what it must still refuse to retry is
 * pinned here alongside what it now accepts.</p>
 */
class SqliteContentionClassificationParityTest {
    private static final SqliteFailureClassifier CLASSIFIER = new SqliteFailureClassifier();

    @TempDir
    Path tempDir;

    @Test
    void theBusyCodeStaysContentionAfterTheRetryLoopDelegatesToTheClassifier() {
        assertEquals(
                Optional.of(OperationalEventCode.DATABASE_LOCKED),
                CLASSIFIER.classify(new SQLException("database is locked", "SQLITE_BUSY", 5)),
                "SQLITE_BUSY was the only contention the retry loop ever recognized; it must still be one");
    }

    @Test
    void theLockedCodeAndTheLockedTableSpellingAreNowContention() {
        assertEquals(
                Optional.of(OperationalEventCode.DATABASE_LOCKED),
                CLASSIFIER.classify(new SQLException("statement aborted", "SQLITE_LOCKED", 6)),
                "SQLITE_LOCKED is a lock-family failure the retry loop used to abandon on the first attempt");
        assertEquals(
                Optional.of(OperationalEventCode.DATABASE_LOCKED),
                CLASSIFIER.classify(new SQLException("database table is locked: projects", "SQLITE_LOCKED", 0)),
                "a lock reported only in the message must be retried like one reported in the vendor code");
        assertEquals(
                Optional.of(OperationalEventCode.DATABASE_LOCKED),
                CLASSIFIER.classify(new KnowledgeStoreException(
                        "Cannot allocate specification version sequence",
                        new SQLException("database table is locked", "SQLITE_LOCKED", 0))),
                "the retry loop only ever sees the wrapping KnowledgeStoreException, so the cause chain must be walked");
    }

    /**
     * Widening a retry predicate risks turning a decisive failure into a long wait: twenty-four attempts with
     * backoff cost more than four seconds before the caller learns anything. A missing table is not a lock, so it
     * must still surface on the first attempt.
     */
    @Test
    void aFailureOutsideTheLockFamilyIsNotRetried() throws Exception {
        Path database = tempDir.resolve("sequence-without-its-table.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (var projects = new SqliteSpecificationKnowledgeStore(database)) {
            projects.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("contention/parity")));
        }

        assertEquals(
                Optional.empty(),
                CLASSIFIER.classify(new SQLException("no such table: specification_version_sequences", "SQLITE_ERROR", 1)),
                "a schema failure is not contention and must not be classified as one");

        try (var store = new SqliteVersionedRequirementStore(database)) {
            try (var connection = SqliteDatabaseSecurity.open(database);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE specification_version_sequences");
            }

            long startedAt = System.nanoTime();
            assertThrows(KnowledgeStoreException.class, () -> store.nextSpecificationVersionSequence(projectId));
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

            assertTrue(elapsedMillis < 2_000L,
                    "a non-contention failure must surface at once; twenty-four backed-off attempts take over four "
                            + "seconds, and this one took " + elapsedMillis + "ms");
        }
    }
}
