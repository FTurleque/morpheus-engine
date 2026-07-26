package com.morpheus.application.composition;

import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** JSON-safe public projection of a persisted provider composition report. */
public record ProviderCompositionView(
        String snapshotId,
        int providerCount,
        int conflictCount,
        int unresolvedConflictCount,
        List<ContributionView> contributions,
        List<ConflictView> conflicts) {

    public ProviderCompositionView {
        snapshotId = requireNonBlank(snapshotId, "snapshotId");
        if (providerCount < 0 || conflictCount < 0 || unresolvedConflictCount < 0) {
            throw new IllegalArgumentException("composition counts must be >= 0");
        }
        contributions = List.copyOf(Objects.requireNonNull(contributions, "contributions"));
        conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
    }

    public static ProviderCompositionView from(KnowledgeSnapshotId snapshotId, ProviderCompositionReport report) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(report, "report");
        List<ContributionView> contributions = report.contributions().stream()
                .map(item -> new ContributionView(
                        item.providerId().value(),
                        item.precedence(),
                        item.required(),
                        item.status().name(),
                        item.itemCount(),
                        item.detail()))
                .toList();
        List<ConflictView> conflicts = report.conflicts().stream()
                .map(conflict -> new ConflictView(
                        conflict.entityKind().name(),
                        conflict.logicalKey(),
                        conflict.resolution().name(),
                        conflict.winner().map(ProviderCompositionView::contenderView),
                        conflict.contenders().stream().map(ProviderCompositionView::contenderView).toList(),
                        conflict.reason()))
                .toList();
        int unresolved = (int) report.conflicts().stream()
                .filter(item -> item.resolution() == ProviderConflictResolution.UNRESOLVED_EQUAL_PRECEDENCE)
                .count();
        return new ProviderCompositionView(
                snapshotId.toString(),
                report.providerCount(),
                report.conflicts().size(),
                unresolved,
                contributions,
                conflicts);
    }

    private static ContenderView contenderView(ProviderConflictContender contender) {
        return new ContenderView(
                contender.providerId().value(), contender.entityId(), contender.precedence());
    }

    public record ContributionView(
            String providerId,
            int precedence,
            boolean required,
            String status,
            int itemCount,
            Optional<String> detail) {
        public ContributionView {
            providerId = requireNonBlank(providerId, "providerId");
            status = requireNonBlank(status, "status");
            if (itemCount < 0) {
                throw new IllegalArgumentException("itemCount must be >= 0");
            }
            detail = Objects.requireNonNull(detail, "detail");
        }
    }

    public record ConflictView(
            String entityKind,
            String logicalKey,
            String resolution,
            Optional<ContenderView> winner,
            List<ContenderView> contenders,
            String reason) {
        public ConflictView {
            entityKind = requireNonBlank(entityKind, "entityKind");
            logicalKey = requireNonBlank(logicalKey, "logicalKey");
            resolution = requireNonBlank(resolution, "resolution");
            winner = Objects.requireNonNull(winner, "winner");
            contenders = List.copyOf(Objects.requireNonNull(contenders, "contenders"));
            reason = requireNonBlank(reason, "reason");
        }
    }

    public record ContenderView(String providerId, String entityId, int precedence) {
        public ContenderView {
            providerId = requireNonBlank(providerId, "providerId");
            entityId = requireNonBlank(entityId, "entityId");
        }
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
