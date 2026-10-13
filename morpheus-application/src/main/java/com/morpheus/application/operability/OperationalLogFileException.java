package com.morpheus.application.operability;

/** The operational log file was asked for and cannot be written where the layout puts it. */
public final class OperationalLogFileException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OperationalLogFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
