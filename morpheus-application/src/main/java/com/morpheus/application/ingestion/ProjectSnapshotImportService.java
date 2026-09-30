package com.morpheus.application.ingestion;

import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.application.snapshot.SnapshotLifecycleService;
import com.morpheus.application.snapshot.SnapshotValidationResult;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.DeterministicTraceabilityDerivationService;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Publishes one complete provider-neutral normalized graph as a fresh snapshot.
 *
 * <p>This service intentionally performs a conservative full rebuild. The previously ACTIVE snapshot remains
 * observable until the candidate has been fully persisted and validated; activation is the final operation.</p>
 */
public final class ProjectSnapshotImportService {
    /** Leaves room for the two trailing counts inside {@link ServerLocationDisclosure#MAX_RELAYED_LENGTH}. */
    private static final int RELAYED_DIAGNOSTICS_BUDGET = ServerLocationDisclosure.MAX_RELAYED_LENGTH - 96;

    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;
    private final SnapshotBusinessContentStore contentStore;
    private final TraceabilityStore traceabilityStore;
    private final SnapshotLifecycleService lifecycle;
    private final DeterministicTraceabilityDerivationService traceabilityDerivation;

    public ProjectSnapshotImportService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore,
            SnapshotBusinessContentStore contentStore,
            TraceabilityStore traceabilityStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.traceabilityStore = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
        this.lifecycle = new SnapshotLifecycleService(snapshotStore);
        this.traceabilityDerivation = new DeterministicTraceabilityDerivationService();
    }

    public ProjectSnapshotImportResult publishFull(
            NormalizedProjectContent content,
            Optional<String> sourceRevision,
            Instant publishedAt) {
        Objects.requireNonNull(content, "content");
        sourceRevision = normalize(sourceRevision);
        Objects.requireNonNull(publishedAt, "publishedAt");

        snapshotStore.putProject(new ProjectStoreEntry(content.project().id(), content.project().rootLocator()));

        Optional<KnowledgeSnapshotMetadata> previousSnapshot = snapshotStore.activeSnapshot(content.project().id());
        Optional<SpecificationVersion> previousVersion = previousSnapshot.flatMap(snapshot ->
                requirementStore.findSnapshotVersion(snapshot.id())
                        .flatMap(binding -> requirementStore.findSpecificationVersion(binding.specificationVersionId())));
        KnowledgeSnapshotMetadata candidate = new KnowledgeSnapshotMetadata(
                KnowledgeSnapshotId.generate(),
                content.project().id(),
                previousSnapshot.map(KnowledgeSnapshotMetadata::id),
                KnowledgeSnapshotState.BUILDING,
                sourceRevision,
                publishedAt);

        try {
            // The candidate is the durable recovery anchor. Persist it before allocating/persisting the version so a
            // failed registration can never leave a specification version that is not bound to a durable candidate.
            lifecycle.registerBuilding(candidate);

            SpecificationVersion version = new SpecificationVersion(
                    SpecificationVersionId.generate(),
                    content.project().id(),
                    Optional.of(requirementStore.nextSpecificationVersionSequence(content.project().id())),
                    commonProviderVersion(content),
                    sourceRevision,
                    publishedAt,
                    previousVersion.map(SpecificationVersion::id));
            requirementStore.putSpecificationVersion(version);
            requirementStore.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(candidate.id(), version.id()));

            List<RequirementVersionRecord> requirements = content.requirements().stream()
                    .map(requirement -> new RequirementVersionRecord(
                            candidate.id(),
                            new EntityVersion<>(
                                    EntityVersionId.generate(),
                                    requirement.id().value(),
                                    version.id(),
                                    TemporalState.CURRENT,
                                    requirement)))
                    .toList();
            requirementStore.putRequirementVersions(requirements);

            contentStore.putSnapshotContent(new SnapshotBusinessContent(
                    candidate.id(),
                    version.id(),
                    content.specifications(),
                    content.scenarios(),
                    content.changes(),
                    content.constraints(),
                    content.designDecisions(),
                    content.tasks(),
                    content.acceptanceCriteria(),
                    content.evidence()));

            var links = traceabilityDerivation.derive(
                    content,
                    ignored -> Optional.of(TraceabilityLinkId.generate()),
                    publishedAt);
            traceabilityStore.putLinks(candidate.id(), links);

            KnowledgeSnapshotMetadata validated = lifecycle.validate(
                    candidate.id(), ignored -> validation(content.diagnostics()));
            if (validated.state() != KnowledgeSnapshotState.READY) {
                throw new KnowledgeStoreException(blockingDiagnosticsFailure(validated.state(), content.diagnostics()));
            }
            KnowledgeSnapshotMetadata active = lifecycle.activate(candidate.id());

            return new ProjectSnapshotImportResult(
                    active,
                    version,
                    requirements.size(),
                    links.size(),
                    content.diagnostics());
        } catch (RuntimeException failure) {
            markCandidateFailed(candidate.id(), failure);
            throw failure;
        }
    }

    private SnapshotValidationResult validation(List<Diagnostic> diagnostics) {
        List<String> errors = diagnostics.stream()
                .filter(item -> item.severity() == DiagnosticSeverity.ERROR)
                .map(item -> item.code() + ": " + item.message())
                .toList();
        if (!errors.isEmpty()) {
            return SnapshotValidationResult.invalid(errors);
        }
        List<String> warnings = diagnostics.stream()
                .filter(item -> item.severity() == DiagnosticSeverity.WARNING)
                .map(item -> item.code() + ": " + item.message())
                .toList();
        return warnings.isEmpty()
                ? SnapshotValidationResult.valid()
                : SnapshotValidationResult.validWithWarnings(warnings);
    }

    /**
     * The refusal names the blocking diagnostics, because a publication rejected without them leaves the caller no
     * way to find the file at fault. It reaches the CLI and, through the local HTTP server, remote callers, so it is
     * kept within what {@link ServerLocationDisclosure#isSafeToRelay} accepts: a diagnostic that names a server
     * location is withheld rather than scrubbed, those that do not fit are counted, and both counts are stated.
     */
    private String blockingDiagnosticsFailure(KnowledgeSnapshotState state, List<Diagnostic> diagnostics) {
        StringBuilder message = new StringBuilder(
                "normalized content contains blocking diagnostics; candidate snapshot is " + state);
        int withheld = 0;
        int notShown = 0;
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() != DiagnosticSeverity.ERROR) {
                continue;
            }
            String entry = diagnostic.code() + ": " + diagnostic.message();
            if (ServerLocationDisclosure.namesAServerLocation(entry)) {
                withheld++;
            } else if (message.length() + entry.length() + 2 > RELAYED_DIAGNOSTICS_BUDGET) {
                notShown++;
            } else {
                message.append("; ").append(entry);
            }
        }
        if (withheld > 0) {
            message.append("; ").append(withheld).append(" withheld because they name a server location");
        }
        if (notShown > 0) {
            message.append("; ").append(notShown).append(" more not shown");
        }
        return message.toString();
    }

    private Optional<String> commonProviderVersion(NormalizedProjectContent content) {
        return content.specifications().stream()
                .map(item -> item.provenance().providerVersion())
                .flatMap(Optional::stream)
                .distinct()
                .reduce((left, right) -> left.equals(right) ? left : "")
                .filter(value -> !value.isBlank());
    }

    private Optional<String> normalize(Optional<String> value) {
        return Objects.requireNonNull(value, "sourceRevision").map(String::trim).filter(candidate -> !candidate.isEmpty());
    }

    private void markCandidateFailed(KnowledgeSnapshotId snapshotId, RuntimeException originalFailure) {
        try {
            snapshotStore.findSnapshot(snapshotId).ifPresent(snapshot -> {
                if (snapshot.state() == KnowledgeSnapshotState.BUILDING
                        || snapshot.state() == KnowledgeSnapshotState.VALIDATING) {
                    snapshotStore.transitionSnapshotState(
                            snapshotId, snapshot.state(), KnowledgeSnapshotState.FAILED);
                }
            });
        } catch (RuntimeException stateFailure) {
            originalFailure.addSuppressed(stateFailure);
        }
    }
}
