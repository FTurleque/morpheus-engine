package com.morpheus.store.sqlite;

import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Writes one immutable snapshot business-content projection inside the caller transaction. */
final class SqliteSnapshotBusinessContentWriter {
    private final Connection connection;

    SqliteSnapshotBusinessContentWriter(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    void insert(SnapshotBusinessContent content) throws SQLException {
        insertManifest(content);
        insertEvidence(content);
        insertSpecifications(content);
        insertScenarios(content);
        insertChanges(content);
        insertConstraints(content);
        insertDesignDecisions(content);
        insertTasks(content);
        insertAcceptanceCriteria(content);
    }

    private void insertManifest(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO snapshot_business_content(snapshot_id, specification_version_id) VALUES (?, ?)")) {
            statement.setString(1, content.snapshotId().toString());
            statement.setString(2, content.specificationVersionId().toString());
            statement.executeUpdate();
        }
    }

    private void insertEvidence(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_evidence(
                    snapshot_id, evidence_id, source_scheme, source_value,
                    range_start_line, range_end_line, excerpt_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Evidence evidence : content.evidence()) {
                statement.setString(1, content.snapshotId().toString());
                statement.setString(2, evidence.id().toString());
                statement.setString(3, evidence.source().scheme());
                statement.setString(4, evidence.source().value());
                if (evidence.range().isPresent()) {
                    statement.setInt(5, evidence.range().orElseThrow().startLine());
                    statement.setInt(6, evidence.range().orElseThrow().endLine());
                } else {
                    statement.setNull(5, java.sql.Types.INTEGER);
                    statement.setNull(6, java.sql.Types.INTEGER);
                }
                statement.setString(7, evidence.excerptHash().orElse(null));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertSpecifications(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_specifications(
                    snapshot_id, specification_id, project_id, specification_key, title, description,
                    provider_id, provider_version, source_scheme, source_value, external_id, source_revision, evidence_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Specification specification : content.specifications()) {
                statement.setString(1, content.snapshotId().toString());
                statement.setString(2, specification.id().toString());
                statement.setString(3, specification.projectId().toString());
                statement.setString(4, specification.key());
                statement.setString(5, specification.title());
                statement.setString(6, specification.description().orElse(null));
                bindProvenance(statement, 7, specification.provenance());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertScenarios(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_scenarios(
                    snapshot_id, scenario_id, requirement_id, title, action, expected_outcome,
                    provider_id, provider_version, source_scheme, source_value, external_id, source_revision, evidence_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Scenario scenario : content.scenarios()) {
                statement.setString(1, content.snapshotId().toString());
                statement.setString(2, scenario.id().toString());
                statement.setString(3, scenario.requirementId().map(RequirementId::toString).orElse(null));
                statement.setString(4, scenario.title());
                statement.setString(5, scenario.action());
                statement.setString(6, scenario.expectedOutcome());
                bindProvenance(statement, 7, scenario.provenance());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        for (Scenario scenario : content.scenarios()) {
            insertOrderedValues(
                    "snapshot_scenario_preconditions",
                    "scenario_id",
                    content.snapshotId(),
                    scenario.id().toString(),
                    scenario.preconditions());
        }
    }

    private void insertChanges(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_changes(
                    snapshot_id, change_id, project_id, change_key, title, intent,
                    provider_id, provider_version, source_scheme, source_value, external_id, source_revision, evidence_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ChangeProposal change : content.changes()) {
                statement.setString(1, content.snapshotId().toString());
                statement.setString(2, change.id().toString());
                statement.setString(3, change.projectId().toString());
                statement.setString(4, change.key().orElse(null));
                statement.setString(5, change.title());
                statement.setString(6, change.intent());
                bindProvenance(statement, 7, change.provenance());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        for (ChangeProposal change : content.changes()) {
            insertOrderedValues(
                    "snapshot_change_scope",
                    "change_id",
                    content.snapshotId(),
                    change.id().toString(),
                    change.scope());
            insertOrderedValues(
                    "snapshot_change_out_of_scope",
                    "change_id",
                    content.snapshotId(),
                    change.id().toString(),
                    change.outOfScope());
            insertOrderedValues(
                    "snapshot_change_risks",
                    "change_id",
                    content.snapshotId(),
                    change.id().toString(),
                    change.risks());
        }
    }

    private void insertConstraints(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_constraints(
                    snapshot_id, constraint_id, change_id, statement,
                    applicability, severity, satisfaction, blocking_mode,
                    provider_id, provider_version, source_scheme, source_value, external_id, source_revision, evidence_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Constraint constraint : content.constraints()) {
                statement.setString(1, content.snapshotId().toString());
                statement.setString(2, constraint.id().toString());
                statement.setString(3, constraint.changeId().toString());
                statement.setString(4, constraint.statement());
                statement.setString(5, constraint.applicability().name());
                statement.setString(6, constraint.severity().name());
                statement.setString(7, constraint.satisfaction().name());
                statement.setString(8, constraint.blockingPolicy().mode().name());
                bindProvenance(statement, 9, constraint.provenance());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        for (Constraint constraint : content.constraints()) {
            insertOrderedValues(
                    "snapshot_constraint_blocking_targets",
                    "constraint_id",
                    content.snapshotId(),
                    constraint.id().toString(),
                    constraint.blockingPolicy().targetStates().stream().map(Enum::name).toList());
            insertOrderedValues(
                    "snapshot_constraint_supporting_evidence",
                    "constraint_id",
                    content.snapshotId(),
                    constraint.id().toString(),
                    constraint.supportingEvidenceIds().stream().map(EvidenceId::toString).toList());
        }
    }

    private void insertDesignDecisions(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_design_decisions(
                    snapshot_id, design_decision_id, change_id, title, decision,
                    provider_id, provider_version, source_scheme, source_value, external_id, source_revision, evidence_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (DesignDecision decision : content.designDecisions()) {
                statement.setString(1, content.snapshotId().toString());
                statement.setString(2, decision.id().toString());
                statement.setString(3, decision.changeId().toString());
                statement.setString(4, decision.title());
                statement.setString(5, decision.decision());
                bindProvenance(statement, 6, decision.provenance());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertTasks(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_implementation_tasks(
                    snapshot_id, task_id, change_id, task_key, title, completed,
                    provider_id, provider_version, source_scheme, source_value, external_id, source_revision, evidence_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ImplementationTask task : content.tasks()) {
                statement.setString(1, content.snapshotId().toString());
                statement.setString(2, task.id().toString());
                statement.setString(3, task.changeId().toString());
                statement.setString(4, task.key().orElse(null));
                statement.setString(5, task.title());
                statement.setInt(6, task.completed() ? 1 : 0);
                bindProvenance(statement, 7, task.provenance());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertAcceptanceCriteria(SnapshotBusinessContent content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_acceptance_criteria(
                    snapshot_id, acceptance_criterion_id, requirement_id, change_id,
                    title, condition_text, verification_status,
                    provider_id, provider_version, source_scheme, source_value, external_id, source_revision, evidence_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (AcceptanceCriterion criterion : content.acceptanceCriteria()) {
                statement.setString(1, content.snapshotId().toString());
                statement.setString(2, criterion.id().toString());
                statement.setString(3, criterion.requirementId().map(RequirementId::toString).orElse(null));
                statement.setString(4, criterion.changeId().map(ChangeId::toString).orElse(null));
                statement.setString(5, criterion.title());
                statement.setString(6, criterion.condition());
                statement.setString(7, criterion.verificationStatus().name());
                bindProvenance(statement, 8, criterion.provenance());
                statement.addBatch();
            }
            statement.executeBatch();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO snapshot_acceptance_verification_evidence(
                    snapshot_id, acceptance_criterion_id, evidence_id, ordinal
                ) VALUES (?, ?, ?, ?)
                """)) {
            for (AcceptanceCriterion criterion : content.acceptanceCriteria()) {
                for (int index = 0; index < criterion.verificationEvidenceIds().size(); index++) {
                    statement.setString(1, content.snapshotId().toString());
                    statement.setString(2, criterion.id().toString());
                    statement.setString(3, criterion.verificationEvidenceIds().get(index).toString());
                    statement.setInt(4, index);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void insertOrderedValues(
            String table,
            String ownerColumn,
            KnowledgeSnapshotId snapshotId,
            String ownerId,
            List<String> values) throws SQLException {
        String sql = "INSERT INTO " + SqliteSnapshotBusinessContentStore.requireSqlIdentifier(table, "table")
                + "(snapshot_id, " + SqliteSnapshotBusinessContentStore.requireSqlIdentifier(ownerColumn, "ownerColumn")
                + ", ordinal, value) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(1, snapshotId.toString());
                statement.setString(2, ownerId);
                statement.setInt(3, index);
                statement.setString(4, values.get(index));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private int bindProvenance(PreparedStatement statement, int startIndex, Provenance provenance) throws SQLException {
        statement.setString(startIndex, provenance.providerId().value());
        statement.setString(startIndex + 1, provenance.providerVersion().orElse(null));
        statement.setString(startIndex + 2, provenance.source().scheme());
        statement.setString(startIndex + 3, provenance.source().value());
        statement.setString(startIndex + 4, provenance.externalId().orElse(null));
        statement.setString(startIndex + 5, provenance.sourceRevision().orElse(null));
        statement.setString(startIndex + 6, provenance.evidenceId().toString());
        return startIndex + 7;
    }
}
