package com.morpheus.application.composition;

/**
 * How a set of provider observations of one logical key relates. Composition is a union: every observation stays
 * published as its own entity, so no value is ever "selected" over another; {@code PRECEDENCE_RECORDED} says which
 * provider ranks first, {@code IDENTICAL} says the providers agree, {@code UNRESOLVED} says the top providers disagree.
 */
public enum CompositionResolution {
    IDENTICAL,
    PRECEDENCE_RECORDED,
    UNRESOLVED
}
