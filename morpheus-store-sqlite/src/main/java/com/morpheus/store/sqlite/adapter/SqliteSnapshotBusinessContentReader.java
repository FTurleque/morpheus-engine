package com.morpheus.store.sqlite.adapter;

import com.morpheus.application.store.SnapshotBusinessContent;
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
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.version.SpecificationVersionId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reads immutable snapshot business-content projections from SQLite rows. */
final class SqliteSnapshotBusinessContentReader {
    private final Connection connection;

    SqliteSnapshotBusinessContentReader(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    Optional<SnapshotBusinessContent> find(KnowledgeSnapshotId snapshotId) throws SQLException {
        SpecificationVersionId versionId;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT specification_version_id FROM snapshot_business_content WHERE snapshot_id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                versionId = SpecificationVersionId.parse(result.getString("specification_version_id"));
            }
        }

        return Optional.of(new SnapshotBusinessContent(
                snapshotId,
                versionId,
                readSpecifications(snapshotId),
                readScenarios(snapshotId),
                readChanges(snapshotId),
                readConstraints(snapshotId),
                readDesignDecisions(snapshotId),
                readTasks(snapshotId),
                readAcceptanceCriteria(snapshotId),
                readEvidence(snapshotId)));
    }

    Optional<ProjectSpecificationId> snapshotProject(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT project_id FROM knowledge_snapshots WHERE id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(ProjectSpecificationId.parse(result.getString("project_id")))
                        : Optional.empty();
            }
        }
    }

    Optional<SpecificationVersionId> snapshotVersion(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT specification_version_id FROM snapshot_specification_versions WHERE snapshot_id = ?")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(SpecificationVersionId.parse(result.getString("specification_version_id")))
                        : Optional.empty();
            }
        }
    }

    private List<Evidence> readEvidence(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_evidence WHERE snapshot_id = ? ORDER BY evidence_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<Evidence> items = new ArrayList<>();
                while (result.next()) {
                    int start = result.getInt("range_start_line");
                    boolean noRange = result.wasNull();
                    int end = result.getInt("range_end_line");
                    Optional<SourceRange> range = noRange
                            ? Optional.empty()
                            : Optional.of(new SourceRange(start, end));
                    items.add(new Evidence(
                            EvidenceId.parse(result.getString("evidence_id")),
                            new SourceLocator(result.getString("source_scheme"), result.getString("source_value")),
                            range,
                            Optional.ofNullable(result.getString("excerpt_hash"))));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<Specification> readSpecifications(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_specifications WHERE snapshot_id = ? ORDER BY specification_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<Specification> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new Specification(
                            SpecificationId.parse(result.getString("specification_id")),
                            ProjectSpecificationId.parse(result.getString("project_id")),
                            result.getString("specification_key"),
                            result.getString("title"),
                            Optional.ofNullable(result.getString("description")),
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<Scenario> readScenarios(KnowledgeSnapshotId snapshotId) throws SQLException {
        Map<String, List<String>> preconditions =
                readValuesByOwner("snapshot_scenario_preconditions", "scenario_id", "value", snapshotId);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_scenarios WHERE snapshot_id = ? ORDER BY scenario_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<Scenario> items = new ArrayList<>();
                while (result.next()) {
                    String requirementId = result.getString("requirement_id");
                    ScenarioId scenarioId = ScenarioId.parse(result.getString("scenario_id"));
                    items.add(new Scenario(
                            scenarioId,
                            requirementId == null
                                    ? Optional.empty()
                                    : Optional.of(RequirementId.parse(requirementId)),
                            result.getString("title"),
                            preconditions.getOrDefault(scenarioId.toString(), List.of()),
                            result.getString("action"),
                            result.getString("expected_outcome"),
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<ChangeProposal> readChanges(KnowledgeSnapshotId snapshotId) throws SQLException {
        Map<String, List<String>> scope =
                readValuesByOwner("snapshot_change_scope", "change_id", "value", snapshotId);
        Map<String, List<String>> outOfScope =
                readValuesByOwner("snapshot_change_out_of_scope", "change_id", "value", snapshotId);
        Map<String, List<String>> risks =
                readValuesByOwner("snapshot_change_risks", "change_id", "value", snapshotId);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_changes WHERE snapshot_id = ? ORDER BY change_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<ChangeProposal> items = new ArrayList<>();
                while (result.next()) {
                    ChangeId changeId = ChangeId.parse(result.getString("change_id"));
                    items.add(new ChangeProposal(
                            changeId,
                            ProjectSpecificationId.parse(result.getString("project_id")),
                            Optional.ofNullable(result.getString("change_key")),
                            result.getString("title"),
                            result.getString("intent"),
                            scope.getOrDefault(changeId.toString(), List.of()),
                            outOfScope.getOrDefault(changeId.toString(), List.of()),
                            risks.getOrDefault(changeId.toString(), List.of()),
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<Constraint> readConstraints(KnowledgeSnapshotId snapshotId) throws SQLException {
        Map<String, List<String>> blockingTargets =
                readValuesByOwner("snapshot_constraint_blocking_targets", "constraint_id", "value", snapshotId);
        Map<String, List<String>> supportingEvidenceIds =
                readValuesByOwner("snapshot_constraint_supporting_evidence", "constraint_id", "value", snapshotId);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_constraints WHERE snapshot_id = ? ORDER BY constraint_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<Constraint> items = new ArrayList<>();
                while (result.next()) {
                    ConstraintId constraintId = ConstraintId.parse(result.getString("constraint_id"));
                    List<ChangeLifecycleState> targets =
                            blockingTargets.getOrDefault(constraintId.toString(), List.of()).stream()
                                    .map(ChangeLifecycleState::valueOf)
                                    .toList();
                    List<EvidenceId> supportingEvidence =
                            supportingEvidenceIds.getOrDefault(constraintId.toString(), List.of()).stream()
                                    .map(EvidenceId::parse)
                                    .toList();
                    items.add(new Constraint(
                            constraintId,
                            ChangeId.parse(result.getString("change_id")),
                            result.getString("statement"),
                            ConstraintApplicability.valueOf(result.getString("applicability")),
                            ConstraintSeverity.valueOf(result.getString("severity")),
                            ConstraintSatisfaction.valueOf(result.getString("satisfaction")),
                            new ConstraintBlockingPolicy(
                                    ConstraintBlockingMode.valueOf(result.getString("blocking_mode")),
                                    targets),
                            supportingEvidence,
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<DesignDecision> readDesignDecisions(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_design_decisions WHERE snapshot_id = ? ORDER BY design_decision_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<DesignDecision> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new DesignDecision(
                            DesignDecisionId.parse(result.getString("design_decision_id")),
                            ChangeId.parse(result.getString("change_id")),
                            result.getString("title"),
                            result.getString("decision"),
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<ImplementationTask> readTasks(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_implementation_tasks WHERE snapshot_id = ? ORDER BY task_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<ImplementationTask> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new ImplementationTask(
                            TaskId.parse(result.getString("task_id")),
                            ChangeId.parse(result.getString("change_id")),
                            Optional.ofNullable(result.getString("task_key")),
                            result.getString("title"),
                            result.getInt("completed") != 0,
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<AcceptanceCriterion> readAcceptanceCriteria(KnowledgeSnapshotId snapshotId) throws SQLException {
        Map<String, List<String>> verificationEvidenceIds = readValuesByOwner(
                "snapshot_acceptance_verification_evidence", "acceptance_criterion_id", "evidence_id", snapshotId);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_acceptance_criteria WHERE snapshot_id = ? ORDER BY acceptance_criterion_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<AcceptanceCriterion> items = new ArrayList<>();
                while (result.next()) {
                    String requirementId = result.getString("requirement_id");
                    String changeId = result.getString("change_id");
                    AcceptanceCriterionId criterionId = AcceptanceCriterionId.parse(
                            result.getString("acceptance_criterion_id"));
                    items.add(new AcceptanceCriterion(
                            criterionId,
                            requirementId == null
                                    ? Optional.empty()
                                    : Optional.of(RequirementId.parse(requirementId)),
                            changeId == null
                                    ? Optional.empty()
                                    : Optional.of(ChangeId.parse(changeId)),
                            result.getString("title"),
                            result.getString("condition_text"),
                            VerificationStatus.valueOf(result.getString("verification_status")),
                            verificationEvidenceIds.getOrDefault(criterionId.toString(), List.of()).stream()
                                    .map(EvidenceId::parse)
                                    .toList(),
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    /**
     * The values of one child table for a whole snapshot, by owner, each owner's values in their stored order.
     *
     * <p>One statement, whatever the snapshot holds: it is read before the parents are, and an owner that has no
     * value is simply absent from the map. A value whose owner is not a parent of the snapshot is read and never
     * used, as the query per owner never asked for it.</p>
     */
    private Map<String, List<String>> readValuesByOwner(
            String table,
            String ownerColumn,
            String valueColumn,
            KnowledgeSnapshotId snapshotId) throws SQLException {
        String owner = SqliteSnapshotBusinessContentStore.requireSqlIdentifier(ownerColumn, "ownerColumn");
        String value = SqliteSnapshotBusinessContentStore.requireSqlIdentifier(valueColumn, "valueColumn");
        String sql = "SELECT " + owner + ", " + value
                + " FROM " + SqliteSnapshotBusinessContentStore.requireSqlIdentifier(table, "table")
                + " WHERE snapshot_id = ? ORDER BY " + owner + ", ordinal";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                Map<String, List<String>> valuesByOwner = new HashMap<>();
                while (result.next()) {
                    valuesByOwner.computeIfAbsent(result.getString(1), absent -> new ArrayList<>())
                            .add(result.getString(2));
                }
                valuesByOwner.replaceAll((ownerId, values) -> List.copyOf(values));
                return valuesByOwner;
            }
        }
    }

    private Provenance mapProvenance(ResultSet result) throws SQLException {
        return new Provenance(
                new ProviderId(result.getString("provider_id")),
                Optional.ofNullable(result.getString("provider_version")),
                new SourceLocator(result.getString("source_scheme"), result.getString("source_value")),
                Optional.ofNullable(result.getString("external_id")),
                Optional.ofNullable(result.getString("source_revision")),
                EvidenceId.parse(result.getString("evidence_id")));
    }
}
