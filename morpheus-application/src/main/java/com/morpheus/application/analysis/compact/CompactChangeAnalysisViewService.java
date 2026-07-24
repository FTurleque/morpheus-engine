package com.morpheus.application.analysis.compact;

import com.morpheus.application.analysis.ChangeAnalysisResult;
import com.morpheus.application.analysis.ChangeAnalysisWarning;
import com.morpheus.application.analysis.ChangeDependencyImpact;
import com.morpheus.application.analysis.RequirementChangeImpact;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisView.AnalysisWarningView;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisView.DependencyImpactView;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisView.PathStepView;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisView.RequirementImpactView;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisView.ScenarioView;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.compact.CompactQueryTypes.ChangeView;
import com.morpheus.application.query.compact.CompactQueryTypes.ConstraintView;
import com.morpheus.application.query.compact.CompactQueryTypes.DesignDecisionView;
import com.morpheus.application.query.compact.CompactQueryTypes.ImplementationTaskView;
import com.morpheus.application.query.compact.CompactQueryTypes.ProvenanceView;
import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.RequirementView;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceLinkView;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceNodeView;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.traceability.TraceabilityPathStep;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Projects M8 rich analysis into a stable canonical JSON-ready view. */
public final class CompactChangeAnalysisViewService {
    private static final int SCHEMA_VERSION = 1;
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();

    public CompactChangeAnalysisView toView(ChangeAnalysisResult result) {
        Objects.requireNonNull(result, "result");
        return new CompactChangeAnalysisView(
                new QueryMetadata(SCHEMA_VERSION, "analyze_change"),
                snapshot(result.baselineSnapshot()),
                change(result.change()),
                result.summary(),
                result.requirementImpacts().stream().map(this::requirementImpact).toList(),
                result.constraints().stream().map(this::constraint).toList(),
                result.designDecisions().stream().map(this::decision).toList(),
                result.implementationTasks().stream().map(this::task).toList(),
                result.dependencyImpacts().stream().map(this::dependencyImpact).toList(),
                result.acceptanceCoverageStatus().name(),
                result.warnings().stream().map(this::warning).toList());
    }

    public String toJson(ChangeAnalysisResult result) {
        return serializer.toJson(toView(result));
    }

    public byte[] toUtf8(ChangeAnalysisResult result) {
        return serializer.toUtf8(toView(result));
    }

    private RequirementImpactView requirementImpact(RequirementChangeImpact impact) {
        var delta = impact.delta();
        return new RequirementImpactView(
                delta.id().toString(),
                delta.kind().name(),
                delta.requirementId().toString(),
                delta.specificationKey(),
                delta.key(),
                delta.title(),
                delta.statement(),
                provenance(delta.provenance()),
                impact.currentRequirement().map(this::requirement),
                impact.currentScenarios().stream().map(this::scenario).toList(),
                impact.proposedScenarios().stream().map(this::scenario).toList(),
                impact.changedFields().stream().map(Enum::name).sorted().toList(),
                impact.warnings().stream().map(this::warning).toList());
    }

    private DependencyImpactView dependencyImpact(ChangeDependencyImpact impact) {
        return new DependencyImpactView(
                impact.originRequirementId().toString(),
                impact.direction().name(),
                traceNode(impact.impactedEntity()),
                impact.depth(),
                impact.path().steps().stream().map(this::pathStep).toList());
    }

    private PathStepView pathStep(TraceabilityPathStep step) {
        return new PathStepView(traceNode(step.from()), traceNode(step.into()), traceLink(step.link()));
    }

    private AnalysisWarningView warning(ChangeAnalysisWarning warning) {
        return new AnalysisWarningView(
                warning.code().name(),
                warning.severity(),
                warning.requirementId().map(Object::toString),
                warning.message(),
                warning.details());
    }

    private SnapshotMetadata snapshot(KnowledgeSnapshotMetadata snapshot) {
        return new SnapshotMetadata(
                snapshot.id().toString(),
                snapshot.projectId().toString(),
                snapshot.state().name(),
                snapshot.predecessorId().map(Object::toString),
                snapshot.sourceRevision(),
                snapshot.createdAt().toString());
    }

    private RequirementView requirement(RequirementVersionRecord record) {
        var version = record.entityVersion();
        var requirement = version.content();
        return new RequirementView(
                requirement.id().toString(),
                version.id().toString(),
                version.specificationVersionId().toString(),
                version.temporalState().name(),
                requirement.specificationId().toString(),
                requirement.key(),
                requirement.title(),
                requirement.statement(),
                provenance(requirement.provenance()));
    }

    private ScenarioView scenario(Scenario scenario) {
        return new ScenarioView(
                scenario.id().toString(),
                scenario.requirementId().map(Object::toString),
                scenario.title(),
                scenario.preconditions(),
                scenario.action(),
                scenario.expectedOutcome(),
                provenance(scenario.provenance()));
    }

    private ChangeView change(ChangeProposal change) {
        return new ChangeView(
                change.id().toString(),
                change.projectId().toString(),
                change.key(),
                change.title(),
                change.intent(),
                change.scope(),
                change.outOfScope(),
                change.risks(),
                provenance(change.provenance()));
    }

    private ConstraintView constraint(Constraint constraint) {
        return new ConstraintView(
                constraint.id().toString(),
                constraint.changeId().toString(),
                constraint.statement(),
                provenance(constraint.provenance()));
    }

    private DesignDecisionView decision(DesignDecision decision) {
        return new DesignDecisionView(
                decision.id().toString(),
                decision.changeId().toString(),
                decision.title(),
                decision.decision(),
                provenance(decision.provenance()));
    }

    private ImplementationTaskView task(ImplementationTask task) {
        return new ImplementationTaskView(
                task.id().toString(),
                task.changeId().toString(),
                task.key(),
                task.title(),
                task.completed(),
                provenance(task.provenance()));
    }

    private ProvenanceView provenance(Provenance provenance) {
        return new ProvenanceView(
                provenance.providerId().toString(),
                provenance.providerVersion(),
                provenance.source().toString(),
                provenance.externalId(),
                provenance.sourceRevision(),
                provenance.evidenceId().toString());
    }

    private TraceNodeView traceNode(TraceabilityEntityRef ref) {
        return new TraceNodeView(ref.kind().name(), ref.identity().toString());
    }

    private TraceLinkView traceLink(TraceabilityLink link) {
        return new TraceLinkView(
                link.id().toString(),
                traceNode(link.source()),
                link.relationType().name(),
                traceNode(link.target()),
                link.origin().name(),
                link.resolution().name(),
                link.evidenceIds().stream().map(Object::toString).sorted().toList());
    }
}
