package com.morpheus.application.composition;

/** Provider-neutral subject of an explicit composition conflict. */
public enum CompositionEntityType {
    PROJECT,
    SPECIFICATION,
    REQUIREMENT,
    SCENARIO,
    CHANGE,
    CONSTRAINT,
    DESIGN_DECISION,
    TASK,
    ACCEPTANCE_CRITERION,
    IDENTITY
}
