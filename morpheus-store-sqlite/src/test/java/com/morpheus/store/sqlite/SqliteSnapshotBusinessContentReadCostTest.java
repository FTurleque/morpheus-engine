package com.morpheus.store.sqlite;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingMode;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a snapshot's content costs a number of statements that does not depend on what the snapshot holds.
 *
 * <p>The reader ran one child query per parent row at seven places: the preconditions of each scenario; the scope,
 * out-of-scope and risks of each change; the blocking targets and supporting evidence of each constraint; the
 * verification evidence of each acceptance criterion. A read of a snapshot of two thousand changes prepared thousands
 * of statements, behind most read capabilities of the three transports. The count is asserted here, in the ordinary
 * suite, because the timing is only measured at night.</p>
 */
class SqliteSnapshotBusinessContentReadCostTest {
    private static final Instant T0 = Instant.parse("2026-10-13T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void theNumberOfStatementsOfAReadDoesNotGrowWithTheContent() throws Exception {
        Path database = tempDir.resolve("read-cost.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SnapshotBusinessContent small = content(projectId, 2);
        SnapshotBusinessContent large = content(projectId, 40);
        store(database, projectId, small);
        store(database, projectId, large);

        Read readOfSmall = read(database, small.snapshotId());
        Read readOfLarge = read(database, large.snapshotId());

        assertEquals(small, readOfSmall.content(), "the small snapshot is read back as it was stored");
        assertEquals(large, readOfLarge.content(), "the large snapshot is read back as it was stored");
        assertEquals(readOfSmall.statements(), readOfLarge.statements(), "a snapshot twenty times larger must not "
                + "cost more statements to read: small=" + readOfSmall.statements() + ", large="
                + readOfLarge.statements());
        assertTrue(readOfLarge.statements() <= 20, () -> "statements of one read: " + readOfLarge.statements());
    }

    /** An owner without any child value reads back with an empty list, not with its neighbour's values. */
    @Test
    void anOwnerWithoutChildValuesReadsBackEmptyBetweenTwoThatHaveSome() throws Exception {
        Path database = tempDir.resolve("sparse.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SnapshotBusinessContent full = content(projectId, 3);
        ChangeProposal middle = full.changes().get(1);
        List<ChangeProposal> changes = new ArrayList<>(full.changes());
        changes.set(1, new ChangeProposal(middle.id(), middle.projectId(), middle.key(), middle.title(),
                middle.intent(), List.of(), List.of(), List.of(), middle.provenance()));
        SnapshotBusinessContent sparse = new SnapshotBusinessContent(
                full.snapshotId(), full.specificationVersionId(), full.specifications(), full.scenarios(), changes,
                full.constraints(), full.designDecisions(), full.tasks(), full.acceptanceCriteria(), full.evidence());
        store(database, projectId, sparse);

        assertEquals(sparse, read(database, sparse.snapshotId()).content());
    }

    /**
     * Each of the seven grouped reads is served by the primary key of its table, in order, without a sort: the key
     * is (snapshot, owner, ordinal), which is exactly what the read asks for.
     */
    @Test
    void eachGroupedReadIsServedByThePrimaryKeyWithoutASort() throws Exception {
        Path database = tempDir.resolve("plans.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        store(database, projectId, content(projectId, 2));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            for (String[] read : new String[][] {
                    {"snapshot_scenario_preconditions", "scenario_id", "value"},
                    {"snapshot_change_scope", "change_id", "value"},
                    {"snapshot_change_out_of_scope", "change_id", "value"},
                    {"snapshot_change_risks", "change_id", "value"},
                    {"snapshot_constraint_blocking_targets", "constraint_id", "value"},
                    {"snapshot_constraint_supporting_evidence", "constraint_id", "value"},
                    {"snapshot_acceptance_verification_evidence", "acceptance_criterion_id", "evidence_id"}}) {
                String plan = plan(connection, "SELECT " + read[1] + ", " + read[2] + " FROM " + read[0]
                        + " WHERE snapshot_id = ? ORDER BY " + read[1] + ", ordinal");

                assertTrue(plan.contains("USING") && plan.contains("snapshot_id=?"), () -> read[0] + ": " + plan);
                assertTrue(!plan.contains("TEMP B-TREE"), () -> read[0] + " is sorted after the read: " + plan);
            }
        }
    }

    private static String plan(Connection connection, String sql) throws Exception {
        StringBuilder plan = new StringBuilder();
        try (var statement = connection.prepareStatement("EXPLAIN QUERY PLAN " + sql)) {
            statement.setString(1, "any-snapshot");
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    plan.append(result.getString("detail")).append(" | ");
                }
            }
        }
        return plan.toString();
    }

    private static Read read(Path database, KnowledgeSnapshotId snapshotId) throws Exception {
        AtomicInteger prepared = new AtomicInteger();
        try (Connection real = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            Connection counting = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement")) {
                            prepared.incrementAndGet();
                        }
                        try {
                            return method.invoke(real, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
            SnapshotBusinessContent content =
                    new SqliteSnapshotBusinessContentReader(counting).find(snapshotId).orElseThrow();
            return new Read(content, prepared.get());
        }
    }

    private static void store(Path database, ProjectSpecificationId projectId, SnapshotBusinessContent content) {
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var store = new SqliteSnapshotBusinessContentStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    content.snapshotId(), projectId, Optional.empty(), KnowledgeSnapshotState.READY,
                    Optional.of("revision-" + content.changes().size()), T0));
            versions.putSpecificationVersion(new SpecificationVersion(
                    content.specificationVersionId(), projectId, Optional.empty(),
                    Optional.of("provider-" + content.changes().size()),
                    Optional.of("revision-" + content.changes().size()), T0, Optional.empty()));
            versions.bindSnapshotVersion(
                    new SnapshotSpecificationVersionBinding(content.snapshotId(), content.specificationVersionId()));
            store.putSnapshotContent(content);
        }
    }

    /** A snapshot of {@code owners} scenarios, changes, constraints and criteria, each with several child values. */
    private static SnapshotBusinessContent content(ProjectSpecificationId projectId, int owners) {
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("specs/content.md"), Optional.empty(), Optional.empty());
        Evidence second = new Evidence(
                EvidenceId.generate(), SourceLocator.file("tests/content.txt"), Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("test-provider"), Optional.of("1.0"), SourceLocator.file("specs/content.md"),
                Optional.of("SOURCE"), Optional.of("revision"), evidence.id());
        List<Scenario> scenarios = new ArrayList<>();
        List<ChangeProposal> changes = new ArrayList<>();
        List<Constraint> constraints = new ArrayList<>();
        List<AcceptanceCriterion> criteria = new ArrayList<>();
        for (int index = 0; index < owners; index++) {
            RequirementId requirementId = RequirementId.generate();
            scenarios.add(new Scenario(
                    ScenarioId.generate(), Optional.of(requirementId), "Scenario " + index,
                    List.of("precondition a of " + index, "precondition b of " + index, "precondition c of " + index),
                    "action " + index, "outcome " + index, provenance));
            ChangeProposal change = new ChangeProposal(
                    ChangeId.generate(), projectId, Optional.of("CHG-" + index), "Change " + index, "Intent " + index,
                    List.of("scope b of " + index, "scope a of " + index),
                    List.of("out of scope of " + index),
                    List.of("risk b of " + index, "risk a of " + index),
                    provenance);
            changes.add(change);
            constraints.add(new Constraint(
                    ConstraintId.generate(), change.id(), "Constraint " + index,
                    ConstraintApplicability.APPLICABLE, ConstraintSeverity.ERROR, ConstraintSatisfaction.UNKNOWN,
                    new ConstraintBlockingPolicy(ConstraintBlockingMode.BLOCK_WHEN_VIOLATED,
                            List.of(ChangeLifecycleState.SPECIFIED, ChangeLifecycleState.PROPOSED)),
                    List.of(second.id(), evidence.id()),
                    provenance));
            criteria.add(new AcceptanceCriterion(
                    AcceptanceCriterionId.generate(), Optional.of(requirementId), Optional.of(change.id()),
                    "Criterion " + index, "condition " + index, VerificationStatus.VERIFIED,
                    List.of(second.id(), evidence.id()), provenance));
        }
        return new SnapshotBusinessContent(
                KnowledgeSnapshotId.generate(),
                SpecificationVersionId.generate(),
                List.of(),
                sortedLikeTheStore(scenarios, scenario -> scenario.id().toString()),
                sortedLikeTheStore(changes, change -> change.id().toString()),
                sortedLikeTheStore(constraints, constraint -> constraint.id().toString()),
                List.of(),
                List.of(),
                sortedLikeTheStore(criteria, criterion -> criterion.id().toString()),
                sortedLikeTheStore(List.of(evidence, second), item -> item.id().toString()));
    }

    /** The store returns each list in the order of its identifiers; the expected value is built in that order. */
    private static <T> List<T> sortedLikeTheStore(List<T> items, java.util.function.Function<T, String> identifier) {
        return items.stream().sorted(java.util.Comparator.comparing(identifier)).toList();
    }

    private record Read(SnapshotBusinessContent content, int statements) {
    }
}
