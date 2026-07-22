package com.morpheus.application.read;

/** Explicit outcome for one requested read category. */
public enum ReadCategoryStatus {
    READ,
    ABSENT,
    UNSUPPORTED,
    FAILED,
    PARTIAL
}
