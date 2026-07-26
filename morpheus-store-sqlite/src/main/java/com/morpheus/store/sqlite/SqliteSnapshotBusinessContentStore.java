package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite adapter for snapshot-owned non-Requirement business-content projection. */
public final class SqliteSnapshotBusinessContentStore implements SnapshotBusinessContentStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteSnapshotBusinessContentStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path absolutePath = databasePath.toAbsolutePath().normalize();
        Connection opened = null;
        try {
            Path parent = absolutePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            opened = DriverManager.getConnection("jdbc:sqlite:" + absolutePath);
            configure(opened);
            new SqliteSchemaManager().migrate(opened);
            this.connection = opened;
        } catch (SQLException | IOException | RuntimeException exception) {
            closeQuietly(opened);
            if (exception instanceof KnowledgeStoreException knowledgeStoreException) {
                throw knowledgeStoreException;
            }
            throw new KnowledgeStoreException("Cannot initialize SQLite snapshot business content store", exception);
        }
    }

    @Override
    public synchronized void putSnapshotContent(SnapshotBusinessContent content) {
        ensureOpen();
        Objects.requireNonNull(content, "content");
        try {
            ProjectSpecificationId projectId = snapshotProject(content.snapshotId())
                    .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + content.snapshotId()));
            SpecificationVersionId boundVersion = snapshotVersion(content.snapshotId())
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "snapshot has no specification version binding: " + content.snapshotId()));
            if (!boundVersion.equals(content.specificationVersionId())) {
                throw new KnowledgeStoreException("business content does not match snapshot specification version");
            }
            validateProjectOwnership(content, projectId);

            Optional<SnapshotBusinessContent> existing = findSnapshotContentInternal(content.snapshotId());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().equals(content)) {
                    throw new KnowledgeStoreException("snapshot business content collision: " + content.snapshotId());
                }
                return;
            }

            boolean previousAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                insertManifest(content);
                insertEvidence(content);
                insertSpecifications(content);
                insertScenarios(content);
                insertChanges(content);
                insertConstraints(content);
                insertDesignDecisions(content);
                insertTasks(content);
                insertAcceptanceCriteria(content);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollbackQuietly();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store snapshot business content " + content.snapshotId(), exception);
        }
    }

    @Override
    public synchronized Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
        ensureOpen();
        Objects.requireNonNull(snapshotId, "snapshotId");
        try {
            return findSnapshotContentInternal(snapshotId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read snapshot business content " + snapshotId, exception);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            connection.close();
            closed = true;
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot close SQLite snapshot business content store", exception);
        }
    }

    private Optional<SnapshotBusinessContent> findSnapshotContentInternal(KnowledgeSnapshotId snapshotId) throws SQLException {
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
                            readOrderedValues(
                                    "snapshot_scenario_preconditions",
                                    "scenario_id",
                                    snapshotId,
                                    scenarioId.toString()),
                            result.getString("action"),
                            result.getString("expected_outcome"),
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<ChangeProposal> readChanges(KnowledgeSnapshotId snapshotId) throws SQLException {
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
                            readOrderedValues(
                                    "snapshot_change_scope", "change_id", snapshotId, changeId.toString()),
                            readOrderedValues(
                                    "snapshot_change_out_of_scope", "change_id", snapshotId, changeId.toString()),
                            readOrderedValues(
                                    "snapshot_change_risks", "change_id", snapshotId, changeId.toString()),
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<Constraint> readConstraints(KnowledgeSnapshotId snapshotId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM snapshot_constraints WHERE snapshot_id = ? ORDER BY constraint_id")) {
            statement.setString(1, snapshotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<Constraint> items = new ArrayList<>();
                while (result.next()) {
                    ConstraintId constraintId = ConstraintId.parse(result.getString("constraint_id"));
                    List<ChangeLifecycleState> targets = readOrderedValues(
                                    "snapshot_constraint_blocking_targets",
                                    "constraint_id",
                                    snapshotId,
                                    constraintId.toString()).stream()
                            .map(ChangeLifecycleState::valueOf)
                            .toList();
                    List<EvidenceId> supportingEvidence = readOrderedValues(
                                    "snapshot_constraint_supporting_evidence",
                                    "constraint_id",
                                    snapshotId,
                                    constraintId.toString()).stream()
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
                            readAcceptanceVerificationEvidence(snapshotId, criterionId),
                            mapProvenance(result)));
                }
                return List.copyOf(items);
            }
        }
    }

    private List<EvidenceId> readAcceptanceVerificationEvidence(
            KnowledgeSnapshotId snapshotId,
            AcceptanceCriterionId criterionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT evidence_id
                FROM snapshot_acceptance_verification_evidence
                WHERE snapshot_id = ? AND acceptance_criterion_id = ?
                ORDER BY ordinal
                """)) {
            statement.setString(1, snapshotId.toString());
            statement.setString(2, criterionId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<EvidenceId> evidenceIds = new ArrayList<>();
                while (result.next()) {
                    evidenceIds.add(EvidenceId.parse(result.getString("evidence_id")));
                }
                return List.copyOf(evidenceIds);
            }
        }
    }

    private void insertOrderedValues(
            String table,
            String ownerColumn,
            KnowledgeSnapshotId snapshotId,
            String ownerId,
            List<String> values) throws SQLException {
        String sql = "INSERT INTO " + table
                + "(snapshot_id, " + ownerColumn + ", ordinal, value) VALUES (?, ?, ?, ?)";
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

    private List<String> readOrderedValues(
            String table,
            String ownerColumn,
            KnowledgeSnapshotId snapshotId,
            String ownerId) throws SQLException {
        String sql = "SELECT value FROM " + table
                + " WHERE snapshot_id = ? AND " + ownerColumn + " = ? ORDER BY ordinal";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshotId.toString());
            statement.setString(2, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (result.next()) {
                    values.add(result.getString("value"));
                }
                return List.copyOf(values);
            }
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

    private Provenance mapProvenance(ResultSet result) throws SQLException {
        return new Provenance(
                new ProviderId(result.getString("provider_id")),
                Optional.ofNullable(result.getString("provider_version")),
                new SourceLocator(result.getString("source_scheme"), result.getString("source_value")),
                Optional.ofNullable(result.getString("external_id")),
                Optional.ofNullable(result.getString("source_revision")),
                EvidenceId.parse(result.getString("evidence_id")));
    }

    private Optional<ProjectSpecificationId> snapshotProject(KnowledgeSnapshotId snapshotId) throws SQLException {
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

    private Optional<SpecificationVersionId> snapshotVersion(KnowledgeSnapshotId snapshotId) throws SQLException {
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

    private void validateProjectOwnership(SnapshotBusinessContent content, ProjectSpecificationId projectId) {
        content.specifications().forEach(specification -> {
            if (!specification.projectId().equals(projectId)) {
                throw new KnowledgeStoreException("specification belongs to another project: " + specification.id());
            }
        });
        content.changes().forEach(change -> {
            if (!change.projectId().equals(projectId)) {
                throw new KnowledgeStoreException("change belongs to another project: " + change.id());
            }
        });
    }

    private void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original storage error.
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new KnowledgeStoreException("SQLite snapshot business content store is closed");
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Initialization is already failing.
        }
    }
}
