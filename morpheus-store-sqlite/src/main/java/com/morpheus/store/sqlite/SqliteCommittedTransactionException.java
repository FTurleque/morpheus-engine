package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;

/**
 * Signals that SQLite durably committed the transaction but connection cleanup failed afterwards.
 * Callers must not retry the mutation as if it had rolled back.
 */
public final class SqliteCommittedTransactionException extends KnowledgeStoreException {
    public SqliteCommittedTransactionException(String message, Throwable cause) {
        super(message, cause);
    }

    public boolean committed() {
        return true;
    }
}
