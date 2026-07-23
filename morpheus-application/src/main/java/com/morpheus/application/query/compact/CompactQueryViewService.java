package com.morpheus.application.query.compact;

import com.morpheus.application.query.ChangeContextResult;
import com.morpheus.application.query.RequirementSearchPage;
import com.morpheus.application.query.compact.CompactQueryTypes.ChangeView;
import com.morpheus.application.query.compact.CompactQueryTypes.ConstraintView;
import com.morpheus.application.query.compact.CompactQueryTypes.DesignDecisionView;
import com.morpheus.application.query.compact.CompactQueryTypes.EvidenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.ExternalReferenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.ImplementationTaskView;
import com.morpheus.application.query.compact.CompactQueryTypes.PageMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.ProvenanceView;
import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.RequirementView;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.SourceRangeView;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceLinkView;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceNodeView;
import com.morpheus.application.query.compact.CompactQueryTypes.WarningView;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.traceability.ExternalTraceabilityView;
import com.morpheus.application.traceability.TraceRequirementResult;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Projects rich M5 query results into stable compact exposure DTOs. */
public final class CompactQueryViewService {
    private static final int SCHEMA_VERSION = 1;

    private final SnapshotBusinessContentStore contentStore;

    public CompactQueryViewService(SnapshotBusinessContentStore contentStore) {
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
    }

    public CompactRequirementSearchView requirementSearch(RequirementSearchPage page) {
        Objects.requireNonNull(page, "page");

        Collector collector = collector(page.snapshot());
        List<RequirementView> requirements = page.items().stream()
                .sorted(Comparator.comparing(record -> record.entityVersion().content().id()))
                .map(record -> requirement(record, collector))
                .toList();
        List<EvidenceView> evidence = collector.evidence();

        return new CompactRequirementSearchView(
                metadata("find_requirements"),
                snapshot(page.snapshot()),
                page.query().text(),
                new PageMetadata(
                        page.pageRequest().offset(),
                        page.pageRequest().limit(),
                        page.totalMatches(),
                        page.hasMore()),
                requirements,
                evidence,
                collector.warnings());
    }

    public CompactTraceRequirementView traceRequirement(TraceRequirementResult result) {
        Objects.requireNonNull(result, "result");
        Collector collector = collector(result.snapshot());

        RequirementView requirement = requirement(result.requirement(), collector);
        List<TraceLinkView> links = result.subgraph().links().stream()
                .sorted(Comparator.comparing(TraceabilityLink::id))
                .map(link -> traceLink(link, collector))
                .toList();
        List<ExternalReferenceView> externalReferences = result.externalLinks().stream()
                .sorted(Comparator.comparing(view -> view.link().id()))
                .map(view -> externalReference(view, collector))
                .toList();
        List<EvidenceView> evidence = collector.evidence();

        return new CompactTraceRequirementView(
                metadata("trace_requirement"),
                snapshot(result.snapshot()),
                requirement,
                result.subgraph().nodes().stream().map(this::traceNode).sorted(TRACE_NODE_ORDER).toList(),
                links,
                externalReferences,
                evidence,
                collector.warnings());
    }

    public CompactChangeContextView changeContext(ChangeContextResult result) {
        Objects.requireNonNull(result, "result");
        Collector collector = collector(result.snapshot());

        Optional<ChangeView> change = result.change().map(value -> change(value, collector));
        if (change.isEmpty()) {
            collector.warning(
                    CompactWarningCode.CHANGE_NOT_FOUND,
                    "Change is not present in the published snapshot",
                    Map.of(
                            "changeId", result.changeId().toString(),
                            "snapshotId", result.snapshot().id().toString()));
        }

        List<RequirementView> affectedRequirements = result.affectedRequirements().stream()
                .sorted(Comparator.comparing(record -> record.entityVersion().content().id()))
                .map(record -> requirement(record, collector))
                .toList();
        Set<String> resolvedRequirementIds = affectedRequirements.stream()
                .map(RequirementView::id)
                .collect(Collectors.toUnmodifiableSet());

        List<TraceLinkView> affectedRequirementLinks = result.affectedRequirementLinks().stream()
                .sorted(Comparator.comparing(TraceabilityLink::id))
                .peek(link -> {
                    String requirementId = link.target().identity().toString();
                    if (!resolvedRequirementIds.contains(requirementId)) {
                        collector.warning(
                                CompactWarningCode.AFFECTED_REQUIREMENT_UNRESOLVED,
                                "AFFECTS target has no CURRENT requirement occurrence in the snapshot",
                                Map.of(
                                        "linkId", link.id().toString(),
                                        "requirementId", requirementId,
                                        "snapshotId", result.snapshot().id().toString()));
                    }
                })
                .map(link -> traceLink(link, collector))
                .toList();

        List<ConstraintView> constraints = result.constraints().stream()
                .sorted(Comparator.comparing(Constraint::id))
                .map(value -> constraint(value, collector))
                .toList();
        List<DesignDecisionView> decisions = result.designDecisions().stream()
                .sorted(Comparator.comparing(DesignDecision::id))
                .map(value -> decision(value, collector))
                .toList();
        List<ImplementationTaskView> tasks = result.implementationTasks().stream()
                .sorted(Comparator.comparing(ImplementationTask::id))
                .map(value -> task(value, collector))
                .toList();
        List<TraceLinkView> links = result.subgraph().links().stream()
                .sorted(Comparator.comparing(TraceabilityLink::id))
                .map(link -> traceLink(link, collector))
                .toList();
        List<ExternalReferenceView> externalReferences = result.externalLinks().stream()
                .sorted(Comparator.comparing(view -> view.link().id()))
                .map(view -> externalReference(view, collector))
                .toList();
        List<EvidenceView> evidence = collector.evidence();

        return new CompactChangeContextView(
                metadata("get_change_context"),
                snapshot(result.snapshot()),
                result.changeId().toString(),
                change,
                affectedRequirementLinks,
                affectedRequirements,
                constraints,
                decisions,
                tasks,
                result.subgraph().nodes().stream().map(this::traceNode).sorted(TRACE_NODE_ORDER).toList(),
                links,
                externalReferences,
                evidence,
                collector.warnings());
    }

    private QueryMetadata metadata(String operation) {
        return new QueryMetadata(SCHEMA_VERSION, operation);
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

    private RequirementView requirement(RequirementVersionRecord record, Collector collector) {
        var entityVersion = record.entityVersion();
        var requirement = entityVersion.content();
        return new RequirementView(
                requirement.id().toString(),
                entityVersion.id().toString(),
                entityVersion.specificationVersionId().toString(),
                entityVersion.temporalState().name(),
                requirement.specificationId().toString(),
                requirement.key(),
                requirement.title(),
                requirement.statement(),
                collector.provenance(requirement.provenance()));
    }

    private ChangeView change(ChangeProposal change, Collector collector) {
        return new ChangeView(
                change.id().toString(),
                change.projectId().toString(),
                change.key(),
                change.title(),
                change.intent(),
                change.scope(),
                change.outOfScope(),
                change.risks(),
                collector.provenance(change.provenance()));
    }

    private ConstraintView constraint(Constraint constraint, Collector collector) {
        return new ConstraintView(
                constraint.id().toString(),
                constraint.changeId().toString(),
                constraint.statement(),
                collector.provenance(constraint.provenance()));
    }

    private DesignDecisionView decision(DesignDecision decision, Collector collector) {
        return new DesignDecisionView(
                decision.id().toString(),
                decision.changeId().toString(),
                decision.title(),
                decision.decision(),
                collector.provenance(decision.provenance()));
    }

    private ImplementationTaskView task(ImplementationTask task, Collector collector) {
        return new ImplementationTaskView(
                task.id().toString(),
                task.changeId().toString(),
                task.key(),
                task.title(),
                task.completed(),
                collector.provenance(task.provenance()));
    }

    private TraceNodeView traceNode(TraceabilityEntityRef ref) {
        return new TraceNodeView(ref.kind().name(), ref.identity().toString());
    }

    private TraceLinkView traceLink(TraceabilityLink link, Collector collector) {
        link.evidenceIds().forEach(id -> collector.referenceEvidence(id.toString()));
        return new TraceLinkView(
                link.id().toString(),
                traceNode(link.source()),
                link.relationType().name(),
                traceNode(link.target()),
                link.origin().name(),
                link.resolution().name(),
                link.evidenceIds().stream().map(Object::toString).sorted().toList());
    }

    private ExternalReferenceView externalReference(ExternalTraceabilityView view, Collector collector) {
        view.link().evidenceIds().forEach(id -> collector.referenceEvidence(id.toString()));
        collector.externalWarning(view);

        Optional<ExternalReference> reference = view.reference();
        Optional<ProvenanceView> provenance = reference.flatMap(ExternalReference::provenance)
                .map(collector::provenance);
        return new ExternalReferenceView(
                view.link().id().toString(),
                view.availability().name(),
                Optional.of(view.link().target().identity().toString()),
                reference.map(item -> item.target().system()),
                reference.flatMap(item -> item.target().project()),
                reference.map(item -> item.target().resourceType()),
                reference.map(item -> item.target().externalId()),
                reference.flatMap(item -> item.target().revision()),
                provenance);
    }

    private Collector collector(KnowledgeSnapshotMetadata snapshot) {
        SnapshotBusinessContent content = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection for compact view: " + snapshot.id()));
        if (!content.snapshotId().equals(snapshot.id())) {
            throw new KnowledgeStoreException("business-content projection belongs to another snapshot: " + content.snapshotId());
        }
        return new Collector(content);
    }

    private static final Comparator<TraceNodeView> TRACE_NODE_ORDER = Comparator
            .comparing(TraceNodeView::kind)
            .thenComparing(TraceNodeView::identity);

    private static final class Collector {
        private final Map<String, Evidence> evidenceById;
        private final Set<String> referencedEvidenceIds = new TreeSet<>();
        private final Map<String, WarningView> warningsByKey = new TreeMap<>();

        private Collector(SnapshotBusinessContent content) {
            this.evidenceById = content.evidence().stream().collect(Collectors.toUnmodifiableMap(
                    evidence -> evidence.id().toString(),
                    evidence -> evidence));
        }

        private ProvenanceView provenance(Provenance provenance) {
            referenceEvidence(provenance.evidenceId().toString());
            return new ProvenanceView(
                    provenance.providerId().toString(),
                    provenance.providerVersion(),
                    provenance.source().toString(),
                    provenance.externalId(),
                    provenance.sourceRevision(),
                    provenance.evidenceId().toString());
        }

        private void referenceEvidence(String evidenceId) {
            referencedEvidenceIds.add(Objects.requireNonNull(evidenceId, "evidenceId"));
        }

        private List<EvidenceView> evidence() {
            List<EvidenceView> result = new java.util.ArrayList<>();
            for (String evidenceId : referencedEvidenceIds) {
                Evidence evidence = evidenceById.get(evidenceId);
                if (evidence == null) {
                    warning(
                            CompactWarningCode.EVIDENCE_NOT_FOUND,
                            "Referenced evidence is not available in the snapshot business-content projection",
                            Map.of("evidenceId", evidenceId));
                    continue;
                }
                result.add(new EvidenceView(
                        evidence.id().toString(),
                        evidence.source().toString(),
                        evidence.range().map(range -> new SourceRangeView(range.startLine(), range.endLine())),
                        evidence.excerptHash()));
            }
            return List.copyOf(result);
        }

        private void externalWarning(ExternalTraceabilityView view) {
            CompactWarningCode code = switch (view.availability()) {
                case REFERENCE_UNVALIDATED -> CompactWarningCode.EXTERNAL_REFERENCE_UNVALIDATED;
                case REFERENCE_UNRESOLVED -> CompactWarningCode.EXTERNAL_REFERENCE_UNRESOLVED;
                case REFERENCE_STALE -> CompactWarningCode.EXTERNAL_REFERENCE_STALE;
                case BROKEN_REFERENCE -> CompactWarningCode.EXTERNAL_REFERENCE_BROKEN;
                case REFERENCE_RESOLVED -> null;
            };
            if (code == null) {
                return;
            }
            warning(
                    code,
                    "External reference is not fully resolved",
                    Map.of(
                            "availability", view.availability().name(),
                            "linkId", view.link().id().toString(),
                            "referenceId", view.link().target().identity().toString()));
        }

        private void warning(CompactWarningCode code, String message, Map<String, String> details) {
            TreeMap<String, String> canonicalDetails = new TreeMap<>(details);
            String key = code.name() + "|" + canonicalDetails.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("|"));
            warningsByKey.putIfAbsent(
                    key,
                    new WarningView(code, DiagnosticSeverity.WARNING, message, canonicalDetails));
        }

        private List<WarningView> warnings() {
            return List.copyOf(warningsByKey.values());
        }
    }
}
