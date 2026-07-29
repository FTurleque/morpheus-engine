package com.morpheus.application.policy;

/** Centralized M25 safety and operability budgets. */
public final class PolicyBudgets {
    public static final int MAX_RULES_PER_PACK = 128;
    public static final int MAX_ACTIVE_PACKS_PER_SCOPE = 32;
    public static final int MAX_OVERRIDES_PER_SCOPE = 256;
    public static final int MAX_PACK_NAME = 160;
    public static final int MAX_RULE_DESCRIPTION = 512;
    public static final int MAX_DRY_RUN_EVALUATIONS = 4096;

    private PolicyBudgets() {
    }
}