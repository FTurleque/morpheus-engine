package com.morpheus.domain.acceptance;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptanceCriterionTest {

    @Test
    void acceptanceCriterionIdentityRoundTrips() {
        AcceptanceCriterionId id = AcceptanceCriterionId.generate();

        assertEquals(id, AcceptanceCriterionId.parse(id.toString()));
    }

    @Test
    void acceptsRequirementScopedUnknownCriterionWithoutVerificationEvidence() {
        RequirementId requirementId = RequirementId.generate();
        Provenance provenance = provenance();

        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(requirementId),
                Optional.empty(),
                "  Session expires  ",
                "  The user is asked to authenticate again  ",
                VerificationStatus.UNKNOWN,
                List.of(),
                provenance);

        assertEquals(Optional.of(requirementId), criterion.requirementId());
        assertTrue(criterion.changeId().isEmpty());
        assertEquals("Session expires", criterion.title());
        assertEquals("The user is asked to authenticate again", criterion.condition());
        assertEquals(VerificationStatus.UNKNOWN, criterion.verificationStatus());
        assertTrue(criterion.verificationEvidenceIds().isEmpty());
        assertEquals(provenance.evidenceId(), criterion.provenance().evidenceId());
    }

    @Test
    void allowsExplicitRequirementAndChangeOwnershipTogether() {
        RequirementId requirementId = RequirementId.generate();
        ChangeId changeId = ChangeId.generate();

        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(requirementId),
                Optional.of(changeId),
                "Token renewal",
                "A valid refresh token produces a new access token",
                VerificationStatus.NOT_VERIFIED,
                List.of(),
                provenance());

        assertEquals(Optional.of(requirementId), criterion.requirementId());
        assertEquals(Optional.of(changeId), criterion.changeId());
    }

    @Test
    void rejectsCriterionWithoutRequirementOrChangeOwner() {
        assertThrows(IllegalArgumentException.class, () -> new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.empty(),
                Optional.empty(),
                "Orphan criterion",
                "This must never be ownerless",
                VerificationStatus.UNKNOWN,
                List.of(),
                provenance()));
    }

    @Test
    void requiresVerificationEvidenceForAffirmedVerificationStates() {
        for (VerificationStatus status : List.of(
                VerificationStatus.PARTIALLY_VERIFIED,
                VerificationStatus.VERIFIED,
                VerificationStatus.FAILED)) {
            assertThrows(IllegalArgumentException.class, () -> new AcceptanceCriterion(
                    AcceptanceCriterionId.generate(),
                    Optional.of(RequirementId.generate()),
                    Optional.empty(),
                    "Criterion",
                    "Condition",
                    status,
                    List.of(),
                    provenance()));
        }
    }

    @Test
    void keepsVerificationEvidenceSeparateAndCanonical() {
        EvidenceId first = EvidenceId.generate();
        EvidenceId second = EvidenceId.generate();
        List<EvidenceId> expected = new ArrayList<>(List.of(first, second));
        expected.sort(EvidenceId::compareTo);
        Provenance provenance = provenance();

        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.empty(),
                Optional.of(ChangeId.generate()),
                "Migration verified",
                "The migrated data matches the source data",
                VerificationStatus.VERIFIED,
                List.of(second, first),
                provenance);

        assertEquals(expected, criterion.verificationEvidenceIds());
        assertEquals(provenance.evidenceId(), criterion.provenance().evidenceId());
        assertNotEquals(provenance.evidenceId(), first);
        assertNotEquals(provenance.evidenceId(), second);
    }

    @Test
    void rejectsDuplicateVerificationEvidence() {
        EvidenceId evidenceId = EvidenceId.generate();

        assertThrows(IllegalArgumentException.class, () -> new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.empty(),
                Optional.of(ChangeId.generate()),
                "Criterion",
                "Condition",
                VerificationStatus.VERIFIED,
                List.of(evidenceId, evidenceId),
                provenance()));
    }

    @Test
    void rejectsBlankTitleAndCondition() {
        assertThrows(IllegalArgumentException.class, () -> new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(RequirementId.generate()),
                Optional.empty(),
                " ",
                "Condition",
                VerificationStatus.UNKNOWN,
                List.of(),
                provenance()));

        assertThrows(IllegalArgumentException.class, () -> new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(RequirementId.generate()),
                Optional.empty(),
                "Criterion",
                " ",
                VerificationStatus.UNKNOWN,
                List.of(),
                provenance()));
    }

    private static Provenance provenance() {
        return new Provenance(
                new ProviderId("test"),
                Optional.empty(),
                SourceLocator.file("specs/example.md"),
                Optional.empty(),
                Optional.empty(),
                EvidenceId.generate());
    }
}
