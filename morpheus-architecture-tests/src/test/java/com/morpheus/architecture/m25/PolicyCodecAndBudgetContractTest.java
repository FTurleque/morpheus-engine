package com.morpheus.architecture.m25;

import com.morpheus.application.policy.PolicyBudgets;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPack;
import com.morpheus.application.policy.PolicyPackCodec;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyCodecAndBudgetContractTest {

    @Test
    void codecRoundTripIsDeterministicAndRejectsTrailingData() {
        PolicyPack.Version version = version(List.of(rule()));
        PolicyPackCodec codec = new PolicyPackCodec();

        String first = codec.encode(version);
        String second = codec.encode(version);
        PolicyPack.Version decoded = codec.decode(first);

        assertEquals(first, second);
        assertEquals(version, decoded);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(first + "AA"));
    }

    @Test
    void packRejectsMoreThanOneHundredTwentyEightRulesBeforePersistence() {
        List<PolicyRule> rules = new ArrayList<>();
        for (int index = 0; index <= PolicyBudgets.MAX_RULES_PER_PACK; index++) {
            rules.add(rule());
        }
        assertThrows(IllegalArgumentException.class, () -> version(rules));
    }

    private PolicyPack.Version version(List<PolicyRule> rules) {
        return new PolicyPack.Version(
                PolicyIds.PackId.generate(),
                PolicyIds.VersionId.generate(),
                1,
                "Governance",
                rules,
                Instant.parse("2026-07-29T12:00:00Z"));
    }

    private PolicyRule rule() {
        return new PolicyRule(
                PolicyIds.RuleId.generate(),
                "Constraint guard",
                PolicyRule.Kind.CONSTRAINT_GUARD,
                PolicyRule.Severity.BLOCKER,
                new PolicyRule.ConstraintGuard(ChangeId.generate(), ChangeLifecycleState.IMPLEMENTING));
    }
}