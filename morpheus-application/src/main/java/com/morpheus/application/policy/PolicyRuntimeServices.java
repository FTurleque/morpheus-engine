package com.morpheus.application.policy;

import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;

import java.util.Objects;

/**
 * The policy service graph, assembled once from the ports it needs.
 *
 * <p>Three adapters expose policy -- HTTP, CLI and MCP -- and each rebuilt the same eleven services from the
 * same seven ports, in the same order, by hand. Three copies of one wiring decision drift, and the one that
 * drifts is the one nobody looked at; the fact resolver in particular is only correct when every collaborator
 * is built from the same stores.</p>
 *
 * <p>This takes ports and returns services, so it stays inside the application layer: the adapters keep
 * deciding which implementations to open and how long to own them, which is theirs to decide, and stop
 * repeating what the wiring between them has to be.</p>
 */
public record PolicyRuntimeServices(PolicyPackService registry, PolicyEvaluationService evaluation) {

    public PolicyRuntimeServices {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(evaluation, "evaluation");
    }

    // Assembling the policy graph needs every port the graph reads from, and naming them is the point: a holder
    // would hide which store each service is wired to, which is the thing that has to stay right.
    @SuppressWarnings("java:S107")
    public static PolicyRuntimeServices from(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability,
            ExternalReferenceStore externalReferences,
            PortfolioStore portfolios,
            PolicyPackStore policies) {
        QueryExecutionService queries = new QueryExecutionService(snapshots, requirements, content, portfolios);
        ConstraintEvaluationQueryService constraints = new ConstraintEvaluationQueryService(snapshots, content);
        ChangeTransitionEvaluationService lifecycle = new ChangeTransitionEvaluationService(
                snapshots, content, requirements, traceability);
        QualityReportService quality = new QualityReportService(
                snapshots,
                new RequirementQualityService(snapshots, requirements, traceability),
                new TaskQualityService(snapshots, content, requirements, traceability),
                new AcceptanceQualityService(snapshots, content),
                new ChangeCompletenessService(snapshots, content, requirements, traceability),
                new DecisionReferenceQualityService(
                        snapshots, content, requirements, traceability, externalReferences));
        return new PolicyRuntimeServices(
                new PolicyPackService(policies),
                new PolicyEvaluationService(
                        policies, new DefaultPolicyFactResolver(constraints, lifecycle, quality, queries)));
    }
}
