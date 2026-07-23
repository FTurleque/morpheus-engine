package com.morpheus.application.traceability;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Joins persisted external links with snapshot-scoped reference details without hiding broken links. */
public final class ExternalTraceabilityQueryService {
    private final TraceabilityStore traceabilityStore;
    private final ExternalReferenceStore referenceStore;

    public ExternalTraceabilityQueryService(
            TraceabilityStore traceabilityStore,
            ExternalReferenceStore referenceStore) {
        this.traceabilityStore = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
        this.referenceStore = Objects.requireNonNull(referenceStore, "referenceStore");
    }

    public List<ExternalTraceabilityView> outgoing(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef source,
            Set<TraceabilityRelationType> relationTypes) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(relationTypes, "relationTypes");
        return traceabilityStore.outgoing(snapshotId, source, relationTypes).stream()
                .filter(link -> link.target().kind() == TraceabilityEntityKind.EXTERNAL_REFERENCE)
                .map(link -> inspect(snapshotId, link))
                .toList();
    }

    public ExternalTraceabilityView inspect(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(link, "link");
        if (link.target().kind() != TraceabilityEntityKind.EXTERNAL_REFERENCE) {
            throw new IllegalArgumentException("link target is not an external reference");
        }
        ExternalReferenceId referenceId = new ExternalReferenceId(link.target().identity());
        Optional<ExternalReference> reference = referenceStore.findReference(snapshotId, referenceId);
        if (reference.isEmpty()) {
            return new ExternalTraceabilityView(
                    link,
                    Optional.empty(),
                    ExternalTraceabilityAvailability.BROKEN_REFERENCE);
        }
        ExternalReference value = reference.orElseThrow();
        return new ExternalTraceabilityView(link, reference, availability(value));
    }

    private ExternalTraceabilityAvailability availability(ExternalReference reference) {
        return switch (reference.resolutionState()) {
            case UNVALIDATED -> ExternalTraceabilityAvailability.REFERENCE_UNVALIDATED;
            case UNRESOLVED -> ExternalTraceabilityAvailability.REFERENCE_UNRESOLVED;
            case RESOLVED -> ExternalTraceabilityAvailability.REFERENCE_RESOLVED;
            case STALE -> ExternalTraceabilityAvailability.REFERENCE_STALE;
        };
    }
}
