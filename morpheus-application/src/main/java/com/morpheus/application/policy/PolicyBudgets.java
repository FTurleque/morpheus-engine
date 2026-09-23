package com.morpheus.application.policy;

/** Centralized M25 safety and operability budgets. */
public final class PolicyBudgets {
    public static final int MAX_RULES_PER_PACK = 128;
    public static final int MAX_ACTIVE_PACKS_PER_SCOPE = 32;
    public static final int MAX_OVERRIDES_PER_SCOPE = 256;
    public static final int MAX_PACK_NAME = 160;
    public static final int MAX_RULE_DESCRIPTION = 512;
    public static final int MAX_DRY_RUN_EVALUATIONS = 4096;
    public static final int MAX_ACTOR = 256;
    public static final int MAX_REASON = 1024;
    /**
     * Constraint evaluations one constraint-guard fact may observe. Past it the fact is UNKNOWN: a guard that did
     * not see every constraint of its change cannot say that none blocks. The fact records one evidence entry per
     * observed evaluation, so this budget is also its evidence bound.
     */
    public static final int MAX_CONSTRAINT_EVALUATIONS_PER_FACT = 1_024;

    private PolicyBudgets() {
    }
}