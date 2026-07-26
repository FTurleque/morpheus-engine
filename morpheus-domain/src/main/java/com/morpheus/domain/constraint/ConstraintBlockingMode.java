package com.morpheus.domain.constraint;

/** Explicit policy mode controlling whether a constraint may block a lifecycle target. */
public enum ConstraintBlockingMode {
    NON_BLOCKING,
    BLOCK_WHEN_VIOLATED,
    UNKNOWN
}
