package com.morpheus.application.policy;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyConfigurationTest {
    private final PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
    private final PolicyIds.PackId packId = PolicyIds.PackId.generate();
    private final PolicyIds.VersionId versionId = PolicyIds.VersionId.generate();
    private final PolicyIds.RuleId ruleId = PolicyIds.RuleId.generate();

    @Test
    void activationRejectsActorBeyondMaxLength() {
        String oversized = "a".repeat(PolicyBudgets.MAX_ACTOR + 1);

        assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.Activation(
                scope, packId, versionId, 1L, oversized, Instant.now()));
    }

    @Test
    void overrideRejectsReasonBeyondMaxLength() {
        String oversized = "r".repeat(PolicyBudgets.MAX_REASON + 1);

        assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.Override(
                scope, packId, ruleId, PolicyConfiguration.OverrideMode.DISABLE, oversized, "actor", 1L,
                Instant.now()));
    }

    @Test
    void overrideRejectsActorBeyondMaxLength() {
        String oversized = "a".repeat(PolicyBudgets.MAX_ACTOR + 1);

        assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.Override(
                scope, packId, ruleId, PolicyConfiguration.OverrideMode.DISABLE, "reason", oversized, 1L,
                Instant.now()));
    }

    @Test
    void auditRecordRejectsReasonBeyondMaxLength() {
        String oversized = "r".repeat(PolicyBudgets.MAX_REASON + 1);

        assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.AuditRecord(
                com.morpheus.domain.identity.DomainIdentity.generate(),
                PolicyConfiguration.AuditAction.CREATE,
                packId,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                "actor",
                oversized,
                Instant.now()));
    }

    @Test
    void activationAcceptsActorAtExactMaxLength() {
        String maxLength = "a".repeat(PolicyBudgets.MAX_ACTOR);

        var activation = new PolicyConfiguration.Activation(scope, packId, versionId, 1L, maxLength, Instant.now());

        assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.Activation(
                scope, packId, versionId, 1L, maxLength + "x", Instant.now()));
        org.junit.jupiter.api.Assertions.assertEquals(maxLength, activation.actor());
    }
}
