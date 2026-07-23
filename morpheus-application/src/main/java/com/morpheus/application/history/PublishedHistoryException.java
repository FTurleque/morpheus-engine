package com.morpheus.application.history;

/** Signals an invalid or inconsistent published-history operation. */
public final class PublishedHistoryException extends RuntimeException {
    public PublishedHistoryException(String message) {
        super(message);
    }
}