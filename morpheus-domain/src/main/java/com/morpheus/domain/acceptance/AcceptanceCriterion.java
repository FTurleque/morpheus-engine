package com.morpheus.domain.acceptance;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.requirement.RequirementId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Provider-neutral acceptance condition with explicit verification state and evidence. */
public record AcceptanceCriterion(
        AcceptanceCriterionId id,
        Optional<RequirementId> requirementId,
        Optional<ChangeId> changeId,
        String title,
        String condition,
        VerificationStatus verificationStatus,
        List<EvidenceId> verificationEvidenceIds,
        Provenance provenance) {

    public AcceptanceCriterion {
        Objects.requireNonNull(id, "id");
        requirementId = Objects.requireNonNull(requirementId, "requirementId");
        changeId = Objects.requireNonNull(changeId, "changeId");
        if (requirementId.isEmpty() && changeId.isEmpty()) {
            throw new IllegalArgumentException("acceptance criterion must reference a requirement, a change, or both");
        }
        title = requireNonBlank(title, "title");
        condition = requireNonBlank(condition, "condition");
        Objects.requireNonNull(verificationStatus, "verificationStatus");
        verificationEvidenceIds = canonicalEvidenceIds(verificationEvidenceIds);
        Objects.requireNonNull(provenance, "provenance");

        if (requiresVerificationEvidence(verificationStatus) && verificationEvidenceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "verification evidence is required for status " + verificationStatus);
        }
    }

    private static boolean requiresVerificationEvidence(VerificationStatus status) {
        return status == VerificationStatus.PARTIALLY_VERIFIED
                || status == VerificationStatus.VERIFIED
                || status == VerificationStatus.FAILED;
    }

    private static List<EvidenceId> canonicalEvidenceIds(List<EvidenceId> evidenceIds) {
        Objects.requireNonNull(evidenceIds, "verificationEvidenceIds");
        List<EvidenceId> copy = evidenceIds.stream()
                .peek(item -> Objects.requireNonNull(item, "verificationEvidenceIds item"))
                .sorted()
                .toList();
        Set<EvidenceId> seen = new HashSet<>();
        for (EvidenceId evidenceId : copy) {
            if (!seen.add(evidenceId)) {
                throw new IllegalArgumentException("duplicate verification evidence identity: " + evidenceId);
            }
        }
        return List.copyOf(copy);
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
