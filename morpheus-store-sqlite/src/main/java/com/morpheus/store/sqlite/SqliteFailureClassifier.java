package com.morpheus.store.sqlite;

import com.morpheus.application.operability.OperationalEventCode;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Stable SQLite failure classification independent of localized high-level exception text. */
public final class SqliteFailureClassifier {
    private static final int SQLITE_BUSY = 5;
    private static final int SQLITE_LOCKED = 6;

    public Optional<OperationalEventCode> classify(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                int errorCode = sqlException.getErrorCode();
                if (errorCode == SQLITE_BUSY || errorCode == SQLITE_LOCKED) {
                    return Optional.of(OperationalEventCode.DATABASE_LOCKED);
                }
                String message = sqlException.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase(Locale.ROOT);
                    if (normalized.contains("database is locked")
                            || normalized.contains("database table is locked")
                            || normalized.contains("sqlite_busy")
                            || normalized.contains("sqlite_locked")) {
                        return Optional.of(OperationalEventCode.DATABASE_LOCKED);
                    }
                }
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
