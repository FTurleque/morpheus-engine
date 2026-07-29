package com.morpheus.architecture.m25;

import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.memory.MemoryPolicyPackStore;
import com.morpheus.store.sqlite.SqlitePolicyPackStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyPersistenceParityTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T11:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteExposeSameVersionActivationOverrideAndAuditSemantics() {
        Snapshot memory = exercise(new MemoryPolicyPackStore());
        try (SqlitePolicyPackStore sqlite = new SqlitePolicyPackStore(tempDir.resolve("parity.db"))) {
            Snapshot persistent = exercise(sqlite);
            assertEquals(memory, persistent);
        }
    }

    @Test
    void sqliteV015SurvivesCloseAndReopen() {
        Path database = tempDir.resolve("reopen.db");
        PolicyIds.PackId packId;
        PolicyIds.VersionId activeVersion;
        PolicyIds.RuleId ruleId;
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());

        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            PolicyPackService service = new PolicyPackService(store, CLOCK);
            var definition = service.create("Governance", List.of(rule()), "alice", "create");
            service.versions(definition.id()).getFirst();
            var updated = service.update(definition.id(), 1, "Governance 2", List.of(rule()), "alice", "update");
            var v2 = service.versions(updated.id()).getLast();
            service.activate(scope, definition.id(), v2.versionId(), 0, "alice", "activate");
            ruleId = v2.rules().getFirst().id();
            service.putOverride(scope, definition.id(), ruleId,
                    PolicyConfiguration.OverrideMode.FORCE_WARN, 0, "alice", "temporary waiver");
            packId = definition.id();
            activeVersion = v2.versionId();
        }

        try (SqlitePolicyPackStore reopened = new SqlitePolicyPackStore(database)) {
            PolicyPackService service = new PolicyPackService(reopened, CLOCK);
            assertEquals(2, service.get(packId).revision());
            assertEquals(2, service.versions(packId).size());
            assertEquals(activeVersion, service.activation(scope, packId).orElseThrow().versionId());
            assertEquals(ruleId, service.overrides(scope).getFirst().ruleId());
            assertEquals(4, service.audit(packId).size());
        }
    }

    private Snapshot exercise(PolicyPackStore store) {
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        var definition = service.create("Governance", List.of(rule()), "alice", "create");
        var first = service.versions(definition.id()).getFirst();
        var secondDefinition = service.update(definition.id(), 1, "Governance v2", List.of(rule()), "alice", "update");
        var second = service.versions(definition.id()).getLast();
        service.activate(scope, definition.id(), second.versionId(), 0, "alice", "activate");
        service.putOverride(scope, definition.id(), second.rules().getFirst().id(),
                PolicyConfiguration.OverrideMode.FORCE_WARN, 0, "alice", "waiver");

        assertEquals(1, first.versionNumber());
        assertEquals(2, second.versionNumber());
        assertTrue(!first.versionId().equals(second.versionId()));
        return new Snapshot(
                secondDefinition.revision(),
                secondDefinition.latestVersionNumber(),
                service.versions(definition.id()).size(),
                service.activations(scope).size(),
                service.overrides(scope).size(),
                service.audit(definition.id()).size(),
                service.activation(scope, definition.id()).orElseThrow().revision(),
                service.overrides(scope).getFirst().revision());
    }

    private PolicyRule rule() {
        return new PolicyRule(
                PolicyIds.RuleId.generate(),
                "Lifecycle guard",
                PolicyRule.Kind.LIFECYCLE_GUARD,
                PolicyRule.Severity.WARNING,
                new PolicyRule.LifecycleGuard(
                        ChangeId.generate(), ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED));
    }

    private record Snapshot(
            long definitionRevision,
            long latestVersion,
            int versionCount,
            int activationCount,
            int overrideCount,
            int auditCount,
            long activationRevision,
            long overrideRevision) {
    }
}