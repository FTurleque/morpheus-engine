package com.morpheus.application.store;

/** Base runtime failure exposed by the storage boundary. */
public class KnowledgeStoreException extends RuntimeException {
    public KnowledgeStoreException(String message) {
        super(message);
    }

    public KnowledgeStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
