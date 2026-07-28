package com.morpheus.application.query.dsl;

/** Provider-neutral business entity families addressable by the M24 DSL. */
public enum QueryEntityType {
    REQUIREMENT,
    SPECIFICATION,
    SCENARIO,
    CHANGE,
    CONSTRAINT,
    DESIGN_DECISION,
    TASK,
    ACCEPTANCE_CRITERION,
    EVIDENCE,
    PORTFOLIO_MEMBERSHIP,
    PORTFOLIO_REFERENCE
}
