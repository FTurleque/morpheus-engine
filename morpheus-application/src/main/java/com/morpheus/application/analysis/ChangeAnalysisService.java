package com.morpheus.application.analysis;

import com.morpheus.application.quality.AcceptanceCoverageStatus;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.TraceabilityPath;
import com.morpheus.application.traceability.TraceabilitySubgraph;
import com.morpheus.application.traceability.TraceabilityTraversalDirection;
import com.morpheus.application.traceability.TraceabilityTraversalService;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic functional/documentary impact analysis for one proposed change against one published baseline.
 *
 * <p>The service never promotes proposed content, never analyzes code and never invents traceability. Dependency
 * expansion uses only persisted {@code DEPENDS_ON} links from the baseline snapshot.</p>
 */
public final class ChangeAnalysisService {
    private static final Set<TraceabilityRelationType> DEPENDS_ON_ONLY = Set.of(TraceabilityRelationType.DEPENDS_ON);

    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;
    private final VersionedRequirementStore requirementStore;
    private final TraceabilityTraversalService traversalService;

    public ChangeAnalysisService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.traversalService = new TraceabilityTraversalService(
                Objects.requireNonNull(traceabilityStore, "traceabilityStore"));
    }

    public Optional<ChangeAnalysisResult> analyzeActive(ProposedChangeSet proposal, int maxDepth) {
        Objects.requireNonNull(proposal, "proposal");
        requirePositiveDepth(maxDepth);
        return snapshotStore.activeSnapshot(proposal.change().projectId())
                .map(snapshot -> analyze(snapshot, proposal, maxDepth));
    }

    public ChangeAnalysisResult analyzeSnapshot(
            KnowledgeSnapshotId snapshotId,
            ProposedChangeSet proposal,
            int maxDepth) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(proposal, "proposal");
        requirePositiveDepth(maxDepth);
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return analyze(snapshot, proposal, maxDepth);
    }

    private ChangeAnalysisResult analyze(
            KnowledgeSnapshotMetadata snapshot,
            ProposedChangeSet proposal,
            int maxDepth) {
        requirePublished(snapshot);
        if (!snapshot.projectId().equals(proposal.change().projectId())) {
            throw new KnowledgeStoreException("change analysis cannot cross project boundaries");
        }

        SnapshotBusinessContent baselineContent = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));
        Map<SpecificationId, String> specificationKeys = specificationKeys(baselineContent);
        Map<RequirementId, List<Scenario>> currentScenarios = scenariosByRequirement(baselineContent);

        List<RequirementChangeImpact> requirementImpacts = proposal.requirementDeltas().stream()
                .map(delta -> analyzeRequirement(snapshot, delta, specificationKeys, currentScenarios))
                .toList();

        List<ChangeAnalysisWarning> warnings = new ArrayList<>();
        requirementImpacts.forEach(impact -> warnings.addAll(impact.warnings()));
        List<ChangeDependencyImpact> dependencyImpacts = dependencyImpacts(
                snapshot.id(), requirementImpacts, maxDepth, warnings);

        warnings.add(new ChangeAnalysisWarning(
                ChangeAnalysisWarningCode.ACCEPTANCE_CRITERIA_UNAVAILABLE,
                DiagnosticSeverity.WARNING,
                Optional.empty(),
                "Acceptance criteria are unavailable in the normalized production model; scenarios are not converted into acceptance criteria",
                Map.of(
                        "changeId", proposal.change().id().toString(),
                        "snapshotId", snapshot.id().toString(),
                        "status", AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL.name())));

        List<ChangeAnalysisWarning> canonicalWarnings = new ArrayList<>(new LinkedHashSet<>(warnings));
        ChangeAnalysisSummary summary = summary(
                requirementImpacts,
                proposal,
                dependencyImpacts,
                canonicalWarnings.size());

        return new ChangeAnalysisResult(
                snapshot,
                proposal.change(),
                requirementImpacts,
                proposal.constraints(),
                proposal.designDecisions(),
                proposal.implementationTasks(),
                dependencyImpacts,
                AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL,
                canonicalWarnings,
                summary);
    }

    private RequirementChangeImpact analyzeRequirement(
            KnowledgeSnapshotMetadata snapshot,
            RequirementDelta delta,
            Map<SpecificationId, String> specificationKeys,
            Map<RequirementId, List<Scenario>> currentScenarios) {
        Optional<RequirementVersionRecord> current = requirementStore.currentRequirement(
                snapshot.id(), delta.requirementId().value());
        List<Scenario> baselineScenarios = currentScenarios.getOrDefault(delta.requirementId(), List.of());
        EnumSet<RequirementChangeField> changedFields = EnumSet.noneOf(RequirementChangeField.class);
        List<ChangeAnalysisWarning> warnings = new ArrayList<>();

        switch (delta.kind()) {
            case ADDED -> {
                changedFields.add(RequirementChangeField.PRESENCE);
                if (current.isPresent()) {
                    warnings.add(requirementWarning(
                            ChangeAnalysisWarningCode.ADDED_REQUIREMENT_ALREADY_CURRENT,
                            DiagnosticSeverity.WARNING,
                            delta.requirementId(),
                            "ADDED delta targets a requirement already present in the CURRENT baseline",
                            snapshot,
                            delta));
                } else {
                    warnings.add(requirementWarning(
                            ChangeAnalysisWarningCode.PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE,
                            DiagnosticSeverity.INFO,
                            delta.requirementId(),
                            "Proposed-only requirement has no published baseline node from which dependency traceability can be demonstrated",
                            snapshot,
                            delta));
                }
            }
            case MODIFIED -> {
                if (current.isEmpty()) {
                    changedFields.add(RequirementChangeField.PRESENCE);
                    warnings.add(requirementWarning(
                            ChangeAnalysisWarningCode.MODIFIED_REQUIREMENT_BASELINE_MISSING,
                            DiagnosticSeverity.WARNING,
                            delta.requirementId(),
                            "MODIFIED delta has no CURRENT baseline requirement to compare",
                            snapshot,
                            delta));
                } else {
                    compareModified(
                            current.orElseThrow().entityVersion().content(),
                            baselineScenarios,
                            delta,
                            specificationKeys,
                            snapshot,
                            changedFields,
                            warnings);
                    if (changedFields.isEmpty()) {
                        warnings.add(requirementWarning(
                                ChangeAnalysisWarningCode.MODIFIED_WITHOUT_DOCUMENTARY_CHANGE,
                                DiagnosticSeverity.INFO,
                                delta.requirementId(),
                                "MODIFIED delta does not change any normalized documentary field",
                                snapshot,
                                delta));
                    }
                }
            }
            case REMOVED -> {
                if (current.isPresent()) {
                    changedFields.add(RequirementChangeField.PRESENCE);
                } else {
                    warnings.add(requirementWarning(
                            ChangeAnalysisWarningCode.REMOVED_REQUIREMENT_BASELINE_MISSING,
                            DiagnosticSeverity.WARNING,
                            delta.requirementId(),
                            "REMOVED delta targets a requirement absent from the CURRENT baseline",
                            snapshot,
                            delta));
                }
            }
        }

        return new RequirementChangeImpact(delta, current, baselineScenarios, changedFields, warnings);
    }

    private void compareModified(
            Requirement current,
            List<Scenario> currentScenarios,
            RequirementDelta delta,
            Map<SpecificationId, String> specificationKeys,
            KnowledgeSnapshotMetadata snapshot,
            Set<RequirementChangeField> changedFields,
            List<ChangeAnalysisWarning> warnings) {
        String currentSpecificationKey = specificationKeys.get(current.specificationId());
        if (currentSpecificationKey == null) {
            warnings.add(requirementWarning(
                    ChangeAnalysisWarningCode.SPECIFICATION_KEY_UNRESOLVED,
                    DiagnosticSeverity.WARNING,
                    delta.requirementId(),
                    "CURRENT requirement specification cannot be resolved to a normalized specification key",
                    snapshot,
                    delta));
        } else if (!currentSpecificationKey.equals(delta.specificationKey())) {
            changedFields.add(RequirementChangeField.SPECIFICATION);
        }
        if (!current.key().equals(delta.key())) {
            changedFields.add(RequirementChangeField.KEY);
        }
        if (!current.title().equals(delta.title())) {
            changedFields.add(RequirementChangeField.TITLE);
        }
        if (!delta.statement().map(current.statement()::equals).orElse(false)) {
            changedFields.add(RequirementChangeField.STATEMENT);
        }
        if (!scenarioShapes(currentScenarios).equals(scenarioShapes(delta.scenarios()))) {
            changedFields.add(RequirementChangeField.SCENARIOS);
        }
    }

    private List<ChangeDependencyImpact> dependencyImpacts(
            KnowledgeSnapshotId snapshotId,
            List<RequirementChangeImpact> requirementImpacts,
            int maxDepth,
            List<ChangeAnalysisWarning> warnings) {
        List<ChangeDependencyImpact> impacts = new ArrayList<>();
        for (RequirementChangeImpact requirementImpact : requirementImpacts) {
            if (requirementImpact.currentRequirement().isEmpty()) {
                continue;
            }
            RequirementId requirementId = requirementImpact.delta().requirementId();
            TraceabilityEntityRef root = new TraceabilityEntityRef(
                    TraceabilityEntityKind.REQUIREMENT,
                    requirementId.value());
            collectDependencyDirection(
                    snapshotId,
                    requirementId,
                    root,
                    maxDepth,
                    TraceabilityTraversalDirection.OUTGOING,
                    DependencyImpactDirection.DEPENDENCY,
                    impacts,
                    warnings);
            collectDependencyDirection(
                    snapshotId,
                    requirementId,
                    root,
                    maxDepth,
                    TraceabilityTraversalDirection.INCOMING,
                    DependencyImpactDirection.DEPENDENT,
                    impacts,
                    warnings);
        }
        return impacts.stream()
                .distinct()
                .sorted(Comparator.comparing((ChangeDependencyImpact impact) -> impact.originRequirementId().toString())
                        .thenComparing(ChangeDependencyImpact::direction)
                        .thenComparing(impact -> impact.impactedEntity().toString())
                        .thenComparingInt(ChangeDependencyImpact::depth))
                .toList();
    }

    private void collectDependencyDirection(
            KnowledgeSnapshotId snapshotId,
            RequirementId requirementId,
            TraceabilityEntityRef root,
            int maxDepth,
            TraceabilityTraversalDirection traversalDirection,
            DependencyImpactDirection impactDirection,
            List<ChangeDependencyImpact> impacts,
            List<ChangeAnalysisWarning> warnings) {
        TraceabilitySubgraph subgraph = traversalService.traverse(
                snapshotId,
                root,
                maxDepth,
                traversalDirection,
                DEPENDS_ON_ONLY);
        for (TraceabilityEntityRef target : subgraph.nodes()) {
            if (target.equals(root)) {
                continue;
            }
            TraceabilityPath path = traversalService.findPath(
                            snapshotId,
                            root,
                            target,
                            maxDepth,
                            traversalDirection,
                            DEPENDS_ON_ONLY)
                    .orElseThrow(() -> new IllegalStateException(
                            "traceability traversal exposed a node without a path: " + target));
            impacts.add(new ChangeDependencyImpact(requirementId, impactDirection, target, path));
            if (path.steps().stream().anyMatch(step ->
                    step.link().resolution() != TraceabilityResolutionState.RESOLVED)) {
                warnings.add(new ChangeAnalysisWarning(
                        ChangeAnalysisWarningCode.TRACEABILITY_PATH_PARTIALLY_RESOLVED,
                        DiagnosticSeverity.WARNING,
                        Optional.of(requirementId),
                        "Dependency explanation path contains at least one non-resolved traceability link",
                        Map.of(
                                "direction", impactDirection.name(),
                                "depth", Integer.toString(path.steps().size()),
                                "target", target.toString(),
                                "snapshotId", snapshotId.toString())));
            }
        }
    }

    private ChangeAnalysisWarning requirementWarning(
            ChangeAnalysisWarningCode code,
            DiagnosticSeverity severity,
            RequirementId requirementId,
            String message,
            KnowledgeSnapshotMetadata snapshot,
            RequirementDelta delta) {
        return new ChangeAnalysisWarning(
                code,
                severity,
                Optional.of(requirementId),
                message,
                Map.of(
                        "changeId", delta.changeId().toString(),
                        "deltaId", delta.id().toString(),
                        "deltaKind", delta.kind().name(),
                        "snapshotId", snapshot.id().toString()));
    }

    private ChangeAnalysisSummary summary(
            List<RequirementChangeImpact> requirementImpacts,
            ProposedChangeSet proposal,
            List<ChangeDependencyImpact> dependencyImpacts,
            int warningCount) {
        int added = countKind(requirementImpacts, RequirementDeltaKind.ADDED);
        int modified = countKind(requirementImpacts, RequirementDeltaKind.MODIFIED);
        int removed = countKind(requirementImpacts, RequirementDeltaKind.REMOVED);
        int documentaryFields = requirementImpacts.stream()
                .mapToInt(impact -> (int) impact.changedFields().stream()
                        .filter(field -> field != RequirementChangeField.PRESENCE)
                        .count())
                .sum();
        int currentScenarioCount = requirementImpacts.stream()
                .mapToInt(impact -> impact.currentScenarios().size())
                .sum();
        int proposedScenarioCount = requirementImpacts.stream()
                .mapToInt(impact -> impact.proposedScenarios().size())
                .sum();
        int dependencies = (int) dependencyImpacts.stream()
                .filter(impact -> impact.direction() == DependencyImpactDirection.DEPENDENCY)
                .count();
        int dependents = (int) dependencyImpacts.stream()
                .filter(impact -> impact.direction() == DependencyImpactDirection.DEPENDENT)
                .count();
        return new ChangeAnalysisSummary(
                added,
                modified,
                removed,
                documentaryFields,
                currentScenarioCount,
                proposedScenarioCount,
                proposal.constraints().size(),
                proposal.designDecisions().size(),
                proposal.implementationTasks().size(),
                dependencies,
                dependents,
                warningCount);
    }

    private int countKind(List<RequirementChangeImpact> impacts, RequirementDeltaKind kind) {
        return (int) impacts.stream().filter(impact -> impact.delta().kind() == kind).count();
    }

    private Map<SpecificationId, String> specificationKeys(SnapshotBusinessContent content) {
        Map<SpecificationId, String> keys = new HashMap<>();
        content.specifications().forEach(specification -> keys.put(specification.id(), specification.key()));
        return Map.copyOf(keys);
    }

    private Map<RequirementId, List<Scenario>> scenariosByRequirement(SnapshotBusinessContent content) {
        Map<RequirementId, List<Scenario>> mutable = new HashMap<>();
        for (Scenario scenario : content.scenarios()) {
            scenario.requirementId().ifPresent(requirementId ->
                    mutable.computeIfAbsent(requirementId, ignored -> new ArrayList<>()).add(scenario));
        }
        Map<RequirementId, List<Scenario>> canonical = new HashMap<>();
        mutable.forEach((requirementId, scenarios) -> canonical.put(
                requirementId,
                scenarios.stream().sorted(Comparator.comparing(item -> item.id().toString())).toList()));
        return Map.copyOf(canonical);
    }

    private List<ScenarioShape> scenarioShapes(List<Scenario> scenarios) {
        return scenarios.stream()
                .map(scenario -> new ScenarioShape(
                        scenario.title(),
                        scenario.preconditions(),
                        scenario.action(),
                        scenario.expectedOutcome()))
                .sorted(Comparator.comparing(ScenarioShape::title)
                        .thenComparing(shape -> String.join("\u0000", shape.preconditions()))
                        .thenComparing(ScenarioShape::action)
                        .thenComparing(ScenarioShape::expectedOutcome))
                .toList();
    }

    private void requirePublished(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "change analysis requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
    }

    private void requirePositiveDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be greater than zero");
        }
    }

    private record ScenarioShape(
            String title,
            List<String> preconditions,
            String action,
            String expectedOutcome) {
        private ScenarioShape {
            Objects.requireNonNull(title, "title");
            preconditions = List.copyOf(Objects.requireNonNull(preconditions, "preconditions"));
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(expectedOutcome, "expectedOutcome");
        }
    }
}
