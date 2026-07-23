package com.morpheus.application.query.compact;

import com.morpheus.domain.diagnostic.DiagnosticSeverity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Shared typed DTOs used by compact M5 query views. */
public final class CompactQueryTypes {
    private CompactQueryTypes() {
    }

    public record QueryMetadata(int schemaVersion, String operation) {
        public QueryMetadata {
            if (schemaVersion <= 0) {
                throw new IllegalArgumentException("schemaVersion must be greater than zero");
            }
            operation = requireNonBlank(operation, "operation");
        }
    }

    public record SnapshotMetadata(
            String snapshotId,
            String projectId,
            String state,
            Optional<String> predecessorId,
            Optional<String> sourceRevision,
            String builtAt) {
        public SnapshotMetadata {
            snapshotId = requireNonBlank(snapshotId, "snapshotId");
            projectId = requireNonBlank(projectId, "projectId");
            state = requireNonBlank(state, "state");
            predecessorId = normalized(predecessorId, "predecessorId");
            sourceRevision = normalized(sourceRevision, "sourceRevision");
            builtAt = requireNonBlank(builtAt, "builtAt");
        }
    }

    public record PageMetadata(int offset, int limit, int totalMatches, boolean hasMore) {
        public PageMetadata {
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0");
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be greater than zero");
            }
            if (totalMatches < 0) {
                throw new IllegalArgumentException("totalMatches must be >= 0");
            }
        }
    }

    public record ProvenanceView(
            String providerId,
            Optional<String> providerVersion,
            String source,
            Optional<String> externalId,
            Optional<String> sourceRevision,
            String evidenceId) {
        public ProvenanceView {
            providerId = requireNonBlank(providerId, "providerId");
            providerVersion = normalized(providerVersion, "providerVersion");
            source = requireNonBlank(source, "source");
            externalId = normalized(externalId, "externalId");
            sourceRevision = normalized(sourceRevision, "sourceRevision");
            evidenceId = requireNonBlank(evidenceId, "evidenceId");
        }
    }

    public record SourceRangeView(int startLine, int endLine) {
        public SourceRangeView {
            if (startLine < 1 || endLine < startLine) {
                throw new IllegalArgumentException("invalid source range");
            }
        }
    }

    public record EvidenceView(
            String id,
            String source,
            Optional<SourceRangeView> range,
            Optional<String> excerptHash) {
        public EvidenceView {
            id = requireNonBlank(id, "id");
            source = requireNonBlank(source, "source");
            range = Objects.requireNonNull(range, "range");
            excerptHash = normalized(excerptHash, "excerptHash");
        }
    }

    public record RequirementView(
            String id,
            String specificationId,
            Optional<String> key,
            String title,
            String statement,
            ProvenanceView provenance) {
        public RequirementView {
            id = requireNonBlank(id, "id");
            specificationId = requireNonBlank(specificationId, "specificationId");
            key = normalized(key, "key");
            title = requireNonBlank(title, "title");
            statement = requireNonBlank(statement, "statement");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    public record ChangeView(
            String id,
            String projectId,
            Optional<String> key,
            String title,
            String intent,
            List<String> scope,
            List<String> outOfScope,
            List<String> risks,
            ProvenanceView provenance) {
        public ChangeView {
            id = requireNonBlank(id, "id");
            projectId = requireNonBlank(projectId, "projectId");
            key = normalized(key, "key");
            title = requireNonBlank(title, "title");
            intent = requireNonBlank(intent, "intent");
            scope = immutableStrings(scope, "scope");
            outOfScope = immutableStrings(outOfScope, "outOfScope");
            risks = immutableStrings(risks, "risks");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    public record ConstraintView(
            String id,
            String changeId,
            String statement,
            ProvenanceView provenance) {
        public ConstraintView {
            id = requireNonBlank(id, "id");
            changeId = requireNonBlank(changeId, "changeId");
            statement = requireNonBlank(statement, "statement");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    public record DesignDecisionView(
            String id,
            String changeId,
            String title,
            String decision,
            ProvenanceView provenance) {
        public DesignDecisionView {
            id = requireNonBlank(id, "id");
            changeId = requireNonBlank(changeId, "changeId");
            title = requireNonBlank(title, "title");
            decision = requireNonBlank(decision, "decision");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    public record ImplementationTaskView(
            String id,
            String changeId,
            Optional<String> key,
            String title,
            boolean completed,
            ProvenanceView provenance) {
        public ImplementationTaskView {
            id = requireNonBlank(id, "id");
            changeId = requireNonBlank(changeId, "changeId");
            key = normalized(key, "key");
            title = requireNonBlank(title, "title");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    public record TraceNodeView(String kind, String identity) {
        public TraceNodeView {
            kind = requireNonBlank(kind, "kind");
            identity = requireNonBlank(identity, "identity");
        }
    }

    public record TraceLinkView(
            String id,
            TraceNodeView source,
            String relation,
            TraceNodeView target,
            String origin,
            String resolution,
            List<String> evidenceIds) {
        public TraceLinkView {
            id = requireNonBlank(id, "id");
            Objects.requireNonNull(source, "source");
            relation = requireNonBlank(relation, "relation");
            Objects.requireNonNull(target, "target");
            origin = requireNonBlank(origin, "origin");
            resolution = requireNonBlank(resolution, "resolution");
            evidenceIds = immutableSortedStrings(evidenceIds, "evidenceIds");
        }
    }

    public record ExternalReferenceView(
            String linkId,
            String availability,
            Optional<String> referenceId,
            Optional<String> system,
            Optional<String> project,
            Optional<String> resourceType,
            Optional<String> externalId,
            Optional<String> revision) {
        public ExternalReferenceView {
            linkId = requireNonBlank(linkId, "linkId");
            availability = requireNonBlank(availability, "availability");
            referenceId = normalized(referenceId, "referenceId");
            system = normalized(system, "system");
            project = normalized(project, "project");
            resourceType = normalized(resourceType, "resourceType");
            externalId = normalized(externalId, "externalId");
            revision = normalized(revision, "revision");
        }
    }

    public record WarningView(
            CompactWarningCode code,
            DiagnosticSeverity severity,
            String message,
            Map<String, String> details) {
        public WarningView {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(severity, "severity");
            message = requireNonBlank(message, "message");
            Objects.requireNonNull(details, "details");
            TreeMap<String, String> sorted = new TreeMap<>();
            details.forEach((key, value) -> sorted.put(
                    requireNonBlank(key, "details key"),
                    Objects.requireNonNull(value, "details value")));
            details = Map.copyOf(sorted);
        }
    }

    private static Optional<String> normalized(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name)
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty());
    }

    private static List<String> immutableStrings(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        return values.stream().map(value -> requireNonBlank(value, name + " item")).toList();
    }

    private static List<String> immutableSortedStrings(List<String> values, String name) {
        return immutableStrings(values, name).stream().distinct().sorted().toList();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
