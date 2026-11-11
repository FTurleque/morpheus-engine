package com.morpheus.application.policy;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyConfigurationTest {
    private final PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
    private final PolicyIds.PackId packId = PolicyIds.PackId.generate();
    private final PolicyIds.VersionId versionId = PolicyIds.VersionId.generate();
    private final PolicyIds.RuleId ruleId = PolicyIds.RuleId.generate();
    private final Instant now = Instant.now();

    @Test
    void activationRejectsActorBeyondMaxLength() {
        String oversized = "a".repeat(PolicyBudgets.MAX_ACTOR + 1);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.Activation(
                scope, packId, versionId, 1L, oversized, now));
        assertEquals("actor exceeds " + PolicyBudgets.MAX_ACTOR + " characters", failure.getMessage());
    }

    @Test
    void overrideRejectsReasonBeyondMaxLength() {
        String oversized = "r".repeat(PolicyBudgets.MAX_REASON + 1);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.Override(
                scope, packId, ruleId, PolicyConfiguration.OverrideMode.DISABLE, oversized, "actor", 1L, now));
        assertEquals("reason exceeds " + PolicyBudgets.MAX_REASON + " characters", failure.getMessage());
    }

    @Test
    void overrideRejectsActorBeyondMaxLength() {
        String oversized = "a".repeat(PolicyBudgets.MAX_ACTOR + 1);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.Override(
                scope, packId, ruleId, PolicyConfiguration.OverrideMode.DISABLE, "reason", oversized, 1L, now));
        assertEquals("actor exceeds " + PolicyBudgets.MAX_ACTOR + " characters", failure.getMessage());
    }

    @Test
    void auditRecordRejectsReasonBeyondMaxLength() {
        String oversized = "r".repeat(PolicyBudgets.MAX_REASON + 1);
        var id = com.morpheus.domain.identity.DomainIdentity.generate();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.AuditRecord(
                id,
                PolicyConfiguration.AuditAction.CREATE,
                packId,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                "actor",
                oversized,
                now));
        assertEquals("reason exceeds " + PolicyBudgets.MAX_REASON + " characters", failure.getMessage());
    }

    @Test
    void overrideConstructsSuccessfullyWithinBounds() {
        var override = new PolicyConfiguration.Override(
                scope, packId, ruleId, PolicyConfiguration.OverrideMode.FORCE_BLOCK, "reason", "actor", 1L, now);

        org.junit.jupiter.api.Assertions.assertEquals("reason", override.reason());
        org.junit.jupiter.api.Assertions.assertEquals("actor", override.actor());
    }

    @Test
    void auditRecordConstructsSuccessfullyWithinBounds() {
        var audit = new PolicyConfiguration.AuditRecord(
                com.morpheus.domain.identity.DomainIdentity.generate(),
                PolicyConfiguration.AuditAction.CREATE,
                packId,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                "actor",
                "reason",
                now);

        org.junit.jupiter.api.Assertions.assertEquals("actor", audit.actor());
        org.junit.jupiter.api.Assertions.assertEquals("reason", audit.reason());
    }

    @Test
    void activationAcceptsActorAtExactMaxLength() {
        String maxLength = "a".repeat(PolicyBudgets.MAX_ACTOR);
        String tooLong = maxLength + "x";

        var activation = new PolicyConfiguration.Activation(scope, packId, versionId, 1L, maxLength, now);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new PolicyConfiguration.Activation(
                scope, packId, versionId, 1L, tooLong, now));
        assertEquals("actor exceeds " + PolicyBudgets.MAX_ACTOR + " characters", failure.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals(maxLength, activation.actor());
    }
}
