package com.morpheus.application.history;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.SpecificationId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.morpheus.application.history.PublishedHistoryStores.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementLogicalRollbackServiceTest {
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final SpecificationId specificationId = SpecificationId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final PublishedHistoryStores stores = new PublishedHistoryStores();
    private final RequirementLogicalRollbackService service = new RequirementLogicalRollbackService(stores, stores);

    @Test
    void thePlanRestoresTheRetiredProjectionWithOneDeltaPerChangedIdentity() {
        KnowledgeSnapshotMetadata retired = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata active = stores.publish(
                projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);
        stores.addRequirement(retired.id(), identity(1), specificationId, "Old wording");
        stores.addRequirement(active.id(), identity(1), specificationId, "New wording");
        stores.addRequirement(retired.id(), identity(2), specificationId, "Removed since");
        stores.addRequirement(active.id(), identity(3), specificationId, "Added since");
        stores.addRequirement(retired.id(), identity(4), specificationId, "Unchanged");
        stores.addRequirement(active.id(), identity(4), specificationId, "Unchanged");
        Map<DomainIdentity, RequirementDeltaId> deltaIds = deltaIds(identity(1), identity(2), identity(3));

        RequirementLogicalRollbackPlan plan = service.plan(request(retired.id(), deltaIds, " spec-a "));

        assertEquals(active, plan.currentSnapshot());
        assertEquals(retired, plan.targetHistoricalSnapshot());
        assertEquals(changeId, plan.changeId());
        assertFalse(plan.isNoOp());
        assertEquals(Map.of("spec-a", specificationId), plan.specificationIdsByKey());
        assertEquals(List.of(RequirementDeltaKind.MODIFIED, RequirementDeltaKind.ADDED, RequirementDeltaKind.REMOVED),
                plan.deltas().stream().map(RequirementDelta::kind).toList());
        RequirementDelta modified = plan.deltas().get(0);
        assertEquals(deltaIds.get(identity(1)), modified.id());
        assertEquals(Optional.of("Old wording"), modified.statement());
        assertEquals("spec-a", modified.specificationKey());
        assertEquals(Optional.of("Removed since"), plan.deltas().get(1).statement());
        assertTrue(plan.deltas().get(2).statement().isEmpty(), "a removal carries no statement");
        assertEquals(identity(3), plan.deltas().get(2).requirementId().value());
    }

    @Test
    void aTargetIdenticalToTheActiveProjectionPlansNothing() {
        KnowledgeSnapshotMetadata retired = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata active = stores.publish(
                projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);
        stores.addRequirement(retired.id(), identity(1), specificationId, "Same");
        stores.addRequirement(active.id(), identity(1), specificationId, "Same");

        RequirementLogicalRollbackPlan plan = service.plan(request(retired.id(), Map.of(), "spec-a"));

        assertTrue(plan.isNoOp());
        assertTrue(plan.specificationIdsByKey().isEmpty());
    }

    @Test
    void withoutAnActiveSnapshotThereIsNothingToRollBack() {
        KnowledgeSnapshotId target = KnowledgeSnapshotId.generate();

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.plan(request(target, Map.of(), "spec-a")));

        assertEquals("project has no ACTIVE snapshot: " + projectId, failure.getMessage());
    }

    @Test
    void aTargetOutsideTheLineageOrStillActiveIsRefused() {
        KnowledgeSnapshotMetadata active = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE);
        KnowledgeSnapshotMetadata detached = stores.publish(
                projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);

        PublishedHistoryException outside = assertThrows(PublishedHistoryException.class,
                () -> service.plan(request(detached.id(), Map.of(), "spec-a")));
        assertEquals("rollback target is not part of the current published lineage: " + detached.id(),
                outside.getMessage());
        PublishedHistoryException current = assertThrows(PublishedHistoryException.class,
                () -> service.plan(request(active.id(), Map.of(), "spec-a")));
        assertEquals("logical rollback target must be RETIRED: " + active.id(), current.getMessage());
    }

    @Test
    void theDeltaIdentifiersMustNameExactlyTheChangedIdentities() {
        KnowledgeSnapshotMetadata retired = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata active = stores.publish(
                projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);
        stores.addRequirement(active.id(), identity(1), specificationId, "Added since");

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.plan(request(retired.id(), deltaIds(identity(2)), "spec-a")));

        assertEquals("rollback RequirementDeltaId plan must match changed identities exactly; missing=["
                + identity(1) + "], extra=[" + identity(2) + "]", failure.getMessage());
    }

    @Test
    void aSpecificationWithoutAnExplicitKeyIsRefused() {
        KnowledgeSnapshotMetadata retired = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        stores.publish(projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);
        SpecificationId unmapped = SpecificationId.generate();
        stores.addRequirement(retired.id(), identity(1), unmapped, "Removed since");

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.plan(request(retired.id(), deltaIds(identity(1)), "spec-a")));

        assertEquals("no explicit specificationKey mapping for rollback SpecificationId: " + unmapped,
                failure.getMessage());
    }

    @Test
    void aRequirementThatMovedBetweenSpecificationsIsOutsideTheLogicalRollback() {
        KnowledgeSnapshotMetadata retired = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.RETIRED);
        KnowledgeSnapshotMetadata active = stores.publish(
                projectId, Optional.of(retired.id()), KnowledgeSnapshotState.ACTIVE);
        SpecificationId other = SpecificationId.generate();
        stores.addRequirement(retired.id(), identity(1), specificationId, "In the first");
        stores.addRequirement(active.id(), identity(1), other, "In the second");

        PublishedHistoryException failure = assertThrows(PublishedHistoryException.class,
                () -> service.plan(request(retired.id(), deltaIds(identity(1)), "spec-a")));

        assertEquals("cross-specification logical rollback requires a MOVED policy and is outside M3-S6: "
                + identity(1), failure.getMessage());
    }

    @Test
    void theRequestRefusesAmbiguousIdentifiersAndKeys() {
        RequirementDeltaId shared = RequirementDeltaId.generate();
        Map<DomainIdentity, RequirementDeltaId> duplicated = new LinkedHashMap<>();
        duplicated.put(identity(1), shared);
        duplicated.put(identity(2), shared);
        assertEquals("rollback RequirementDeltaId values must be unique", assertThrows(IllegalArgumentException.class,
                () -> new RequirementLogicalRollbackRequest(projectId, KnowledgeSnapshotId.generate(), changeId,
                        duplicated, Map.of())).getMessage());

        Map<SpecificationId, String> sameKey = new LinkedHashMap<>();
        sameKey.put(SpecificationId.generate(), "spec-a");
        sameKey.put(SpecificationId.generate(), " spec-a");
        assertEquals("rollback specification keys must map to exactly one SpecificationId: spec-a",
                assertThrows(IllegalArgumentException.class, () -> new RequirementLogicalRollbackRequest(
                        projectId, KnowledgeSnapshotId.generate(), changeId, Map.of(), sameKey)).getMessage());
        assertEquals("specificationKey must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new RequirementLogicalRollbackRequest(projectId, KnowledgeSnapshotId.generate(), changeId,
                        Map.of(), Map.of(SpecificationId.generate(), "  "))).getMessage());
    }

    @Test
    void aPlanCannotSpanTwoProjects() {
        KnowledgeSnapshotMetadata mine = stores.publish(projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE);
        KnowledgeSnapshotMetadata theirs = stores.publish(
                ProjectSpecificationId.generate(), Optional.empty(), KnowledgeSnapshotState.RETIRED);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new RequirementLogicalRollbackPlan(mine, theirs, changeId, List.of(), Map.of()));

        assertEquals("rollback snapshots must belong to the same project", failure.getMessage());
    }

    private RequirementLogicalRollbackRequest request(
            KnowledgeSnapshotId target,
            Map<DomainIdentity, RequirementDeltaId> deltaIds,
            String specificationKey) {
        return new RequirementLogicalRollbackRequest(
                projectId, target, changeId, deltaIds, Map.of(specificationId, specificationKey));
    }

    private static Map<DomainIdentity, RequirementDeltaId> deltaIds(DomainIdentity... identities) {
        Map<DomainIdentity, RequirementDeltaId> ids = new LinkedHashMap<>();
        for (DomainIdentity identity : identities) {
            ids.put(identity, RequirementDeltaId.generate());
        }
        return ids;
    }
}
