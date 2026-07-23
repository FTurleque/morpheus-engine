package com.morpheus.application.quality;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.ExternalTraceabilityAvailability;
import com.morpheus.application.traceability.ExternalTraceabilityQueryService;
import com.morpheus.application.traceability.ExternalTraceabilityView;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic snapshot-scoped quality analysis for design decisions and external references. */
public final class DecisionReferenceQualityService {
    private static final Comparator<DesignDecision> DECISION_ORDER = Comparator.comparing(item -> item.id().toString());
    private static final Set<TraceabilityRelationType> ALL_RELATIONS = Set.copyOf(EnumSet.allOf(TraceabilityRelationType.class));

    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;
    private final VersionedRequirementStore requirementStore;
    private final TraceabilityStore traceabilityStore;
    private final ExternalTraceabilityQueryService externalQuery;

    public DecisionReferenceQualityService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore,
            ExternalReferenceStore referenceStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.traceabilityStore = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
        this.externalQuery = new ExternalTraceabilityQueryService(
                traceabilityStore,
                Objects.requireNonNull(referenceStore, "referenceStore"));
    }

    public Optional<DecisionReferenceQualityReport> assessActive(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId).map(this::assessPublished);
    }

    public DecisionReferenceQualityReport assessSnapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return assessPublished(snapshot);
    }

    private DecisionReferenceQualityReport assessPublished(KnowledgeSnapshotMetadata snapshot) {
        SnapshotBusinessContent content = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));

        List<DesignDecisionQualityAssessment> decisions = content.designDecisions().stream()
                .sorted(DECISION_ORDER)
                .map(decision -> assessDecision(snapshot, decision))
                .toList();

        List<ExternalReferenceQualityAssessment> externalReferences = externalLinks(snapshot, content).stream()
                .map(link -> externalQuery.inspect(snapshot.id(), link))
                .map(this::assessExternal)
                .toList();

        List<QualityFinding> findings = new ArrayList<>();
        decisions.forEach(item -> findings.addAll(item.findings()));
        externalReferences.forEach(item -> findings.addAll(item.findings()));

        return new DecisionReferenceQualityReport(snapshot, decisions, externalReferences, findings);
    }

    private DesignDecisionQualityAssessment assessDecision(
            KnowledgeSnapshotMetadata snapshot,
            DesignDecision decision) {
        TraceabilityEntityRef changeRef = new TraceabilityEntityRef(
                TraceabilityEntityKind.CHANGE,
                decision.changeId().value());
        boolean traced = traceabilityStore
                .outgoing(snapshot.id(), changeRef, Set.of(TraceabilityRelationType.DECIDED_BY))
                .stream()
                .anyMatch(link -> link.target().kind() == TraceabilityEntityKind.DESIGN_DECISION
                        && link.target().identity().equals(decision.id().value()));

        List<QualityFinding> findings = new ArrayList<>();
        if (!traced) {
            findings.add(new QualityFinding(
                    QualityFindingCode.DESIGN_DECISION_WITHOUT_TRACE,
                    DiagnosticSeverity.WARNING,
                    QualityEvidenceKind.DETERMINISTIC,
                    new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, decision.id().value()),
                    "Design decision has no persisted DECIDED_BY trace from its owning change",
                    Map.of(
                            "decisionId", decision.id().toString(),
                            "changeId", decision.changeId().toString(),
                            "snapshotId", snapshot.id().toString()),
                    Optional.empty(),
                    List.of(decision.provenance().evidenceId())));
        }

        findings.add(new QualityFinding(
                QualityFindingCode.DECISION_JUSTIFICATION_UNAVAILABLE,
                DiagnosticSeverity.INFO,
                QualityEvidenceKind.DETERMINISTIC,
                new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, decision.id().value()),
                "Explicit design-decision justification is unavailable in the normalized model",
                Map.of(
                        "decisionId", decision.id().toString(),
                        "status", DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL.name(),
                        "snapshotId", snapshot.id().toString()),
                Optional.empty(),
                List.of(decision.provenance().evidenceId())));

        return new DesignDecisionQualityAssessment(
                decision,
                traced,
                DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL,
                findings);
    }

    private ExternalReferenceQualityAssessment assessExternal(ExternalTraceabilityView view) {
        Optional<QualityFindingCode> code = switch (view.availability()) {
            case REFERENCE_RESOLVED -> Optional.empty();
            case REFERENCE_UNVALIDATED -> Optional.of(QualityFindingCode.EXTERNAL_REFERENCE_UNVALIDATED);
            case REFERENCE_UNRESOLVED -> Optional.of(QualityFindingCode.EXTERNAL_REFERENCE_UNRESOLVED);
            case REFERENCE_STALE -> Optional.of(QualityFindingCode.EXTERNAL_REFERENCE_STALE);
            case BROKEN_REFERENCE -> Optional.of(QualityFindingCode.EXTERNAL_REFERENCE_BROKEN);
        };

        if (code.isEmpty()) {
            return new ExternalReferenceQualityAssessment(view, List.of());
        }

        TraceabilityLink link = view.link();
        QualityFinding finding = new QualityFinding(
                code.orElseThrow(),
                DiagnosticSeverity.WARNING,
                QualityEvidenceKind.DETERMINISTIC,
                link.target(),
                externalMessage(view.availability()),
                Map.of(
                        "traceabilityLinkId", link.id().toString(),
                        "availability", view.availability().name(),
                        "sourceKind", link.source().kind().name(),
                        "sourceIdentity", link.source().identity().toString()),
                Optional.empty(),
                List.copyOf(link.evidenceIds()));
        return new ExternalReferenceQualityAssessment(view, List.of(finding));
    }

    private List<TraceabilityLink> externalLinks(
            KnowledgeSnapshotMetadata snapshot,
            SnapshotBusinessContent content) {
        List<TraceabilityEntityRef> roots = new ArrayList<>();
        roots.add(new TraceabilityEntityRef(TraceabilityEntityKind.PROJECT, snapshot.projectId().value()));
        content.specifications().forEach(item -> roots.add(new TraceabilityEntityRef(
                TraceabilityEntityKind.SPECIFICATION, item.id().value())));
        content.scenarios().forEach(item -> roots.add(new TraceabilityEntityRef(
                TraceabilityEntityKind.SCENARIO, item.id().value())));
        content.changes().forEach(item -> roots.add(new TraceabilityEntityRef(
                TraceabilityEntityKind.CHANGE, item.id().value())));
        content.constraints().forEach(item -> roots.add(new TraceabilityEntityRef(
                TraceabilityEntityKind.CONSTRAINT, item.id().value())));
        content.designDecisions().forEach(item -> roots.add(new TraceabilityEntityRef(
                TraceabilityEntityKind.DESIGN_DECISION, item.id().value())));
        content.tasks().forEach(item -> roots.add(new TraceabilityEntityRef(
                TraceabilityEntityKind.IMPLEMENTATION_TASK, item.id().value())));
        requirementStore.listRequirementVersions(snapshot.id()).stream()
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .map(RequirementVersionRecord::entityVersion)
                .map(version -> version.content().id())
                .forEach(id -> roots.add(new TraceabilityEntityRef(
                        TraceabilityEntityKind.REQUIREMENT, id.value())));

        Map<String, TraceabilityLink> byId = new LinkedHashMap<>();
        roots.stream()
                .distinct()
                .sorted()
                .forEach(root -> traceabilityStore.outgoing(snapshot.id(), root, ALL_RELATIONS).stream()
                        .filter(link -> link.target().kind() == TraceabilityEntityKind.EXTERNAL_REFERENCE)
                        .sorted(Comparator.comparing(link -> link.id().toString()))
                        .forEach(link -> byId.putIfAbsent(link.id().toString(), link)));

        return byId.values().stream()
                .sorted(Comparator.comparing(link -> link.id().toString()))
                .toList();
    }

    private String externalMessage(ExternalTraceabilityAvailability availability) {
        return switch (availability) {
            case REFERENCE_UNVALIDATED -> "External reference has not been validated";
            case REFERENCE_UNRESOLVED -> "External reference is unresolved";
            case REFERENCE_STALE -> "External reference is stale";
            case BROKEN_REFERENCE -> "External traceability link targets a missing reference";
            case REFERENCE_RESOLVED -> throw new IllegalArgumentException("resolved reference has no quality finding");
        };
    }

    private void requirePublished(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "quality analysis requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
    }
}
