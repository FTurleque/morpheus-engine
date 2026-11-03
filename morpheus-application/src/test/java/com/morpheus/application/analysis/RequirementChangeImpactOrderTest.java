package com.morpheus.application.analysis;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scenario identities are generated, so two scenarios read in the same millisecond carry identities in no particular
 * order: the order of an impact's scenarios is the order of their source keys, never the order of their identities.
 */
class RequirementChangeImpactOrderTest {
    private static final EvidenceId EVIDENCE = EvidenceId.generate();
    private static final RequirementId REQUIREMENT = RequirementId.generate();
    private static final ScenarioId LOWER = new ScenarioId(DomainIdentity.parse("01900000-0000-7000-8000-000000000001"));
    private static final ScenarioId HIGHER = new ScenarioId(DomainIdentity.parse("01900000-0000-7000-8000-000000000002"));

    @Test
    void proposedAndCurrentScenariosFollowTheirSourceKeysAndNotTheirGeneratedIdentities() {
        Scenario expire = scenario(HIGHER, "scenario:auth/expire-a-standard-inactive-session", "Expire");
        Scenario preserve = scenario(LOWER, "scenario:auth/preserve-an-opted-in-session", "Preserve");

        RequirementChangeImpact impact = new RequirementChangeImpact(delta(List.of(preserve, expire)),
                Optional.empty(), List.of(preserve, expire), Set.of(), List.of());

        assertEquals(List.of("Expire", "Preserve"), impact.proposedScenarios().stream().map(Scenario::title).toList());
        assertEquals(List.of("Expire", "Preserve"), impact.currentScenarios().stream().map(Scenario::title).toList());
    }

    @Test
    void scenariosWithoutASourceKeyFollowTheirTitleThenTheirIdentity() {
        Scenario second = scenario(LOWER, null, "Second");
        Scenario first = scenario(HIGHER, null, "First");
        Scenario firstAgain = scenario(LOWER, null, "First");

        RequirementChangeImpact impact = new RequirementChangeImpact(delta(List.of(second, first, firstAgain)),
                Optional.empty(), List.of(), Set.of(), List.of());

        assertEquals(List.of(LOWER, HIGHER, LOWER),
                impact.proposedScenarios().stream().map(Scenario::id).toList());
        assertEquals(List.of("First", "First", "Second"),
                impact.proposedScenarios().stream().map(Scenario::title).toList());
    }

    private static RequirementDelta delta(List<Scenario> scenarios) {
        return new RequirementDelta(RequirementDeltaId.generate(), ChangeId.generate(), RequirementDeltaKind.MODIFIED,
                "auth", REQUIREMENT, Optional.of("auth"), "Auth", Optional.of("statement"), scenarios, provenance(null));
    }

    private static Scenario scenario(ScenarioId id, String externalId, String title) {
        return new Scenario(id, Optional.of(REQUIREMENT), title, List.of("given"), "when", "then",
                provenance(externalId));
    }

    private static Provenance provenance(String externalId) {
        return new Provenance(new ProviderId("openspec"), Optional.of("1"), SourceLocator.file("specs/auth.md"),
                Optional.ofNullable(externalId), Optional.empty(), EVIDENCE);
    }
}
