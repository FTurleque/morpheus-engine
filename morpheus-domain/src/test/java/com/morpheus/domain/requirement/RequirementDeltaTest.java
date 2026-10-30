package com.morpheus.domain.requirement;

import com.morpheus.domain.DomainFixtures;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementDeltaTest {
    private final RequirementDeltaId id = RequirementDeltaId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final RequirementId requirementId = RequirementId.generate();
    private final Provenance provenance = DomainFixtures.provenance();

    @Test
    void anAddedDeltaKeepsItsTrimmedTextAndItsScenarios() {
        Scenario scenario = scenarioFor(Optional.of(requirementId));
        RequirementDelta delta = new RequirementDelta(id, changeId, RequirementDeltaKind.ADDED, " auth ",
                requirementId, Optional.of(" REQ-1 "), " Login ", Optional.of(" SHALL log in "), List.of(scenario),
                provenance);

        assertEquals(id, delta.id());
        assertEquals(changeId, delta.changeId());
        assertEquals(RequirementDeltaKind.ADDED, delta.kind());
        assertEquals("auth", delta.specificationKey());
        assertEquals(requirementId, delta.requirementId());
        assertEquals(Optional.of("REQ-1"), delta.key());
        assertEquals("Login", delta.title());
        assertEquals(Optional.of("SHALL log in"), delta.statement());
        assertEquals(List.of(scenario), delta.scenarios());
        assertEquals(provenance, delta.provenance());
    }

    @Test
    void aRemovedDeltaNeedsNoStatement() {
        RequirementDelta removed = delta(RequirementDeltaKind.REMOVED, Optional.of("   "), List.of());

        assertEquals(Optional.empty(), removed.statement());
    }

    @Test
    void anAddedOrModifiedDeltaWithoutStatementIsRefusedNamingItsKind() {
        assertEquals("ADDED requirement delta must contain a statement", assertThrows(IllegalArgumentException.class,
                () -> delta(RequirementDeltaKind.ADDED, Optional.empty(), List.of())).getMessage());
        assertEquals("MODIFIED requirement delta must contain a statement", assertThrows(
                IllegalArgumentException.class,
                () -> delta(RequirementDeltaKind.MODIFIED, Optional.of(" "), List.of())).getMessage());
    }

    @Test
    void aScenarioOfAnotherRequirementIsRefusedNamingTheScenario() {
        Scenario foreign = scenarioFor(Optional.of(RequirementId.generate()));

        assertEquals("delta scenario must reference the delta requirement identity: " + foreign.id(),
                assertThrows(IllegalArgumentException.class,
                        () -> delta(RequirementDeltaKind.MODIFIED, Optional.of("s"), List.of(foreign))).getMessage());
    }

    @Test
    void aScenarioWithoutRequirementIsRefusedNamingTheScenario() {
        Scenario orphan = scenarioFor(Optional.empty());

        assertEquals("delta scenario must reference the delta requirement identity: " + orphan.id(),
                assertThrows(IllegalArgumentException.class,
                        () -> delta(RequirementDeltaKind.REMOVED, Optional.empty(), List.of(orphan))).getMessage());
    }

    @Test
    void theScenariosAreACopyTheCallerCannotChangeAfterwards() {
        List<Scenario> scenarios = new ArrayList<>();
        RequirementDelta delta = delta(RequirementDeltaKind.REMOVED, Optional.empty(), scenarios);
        scenarios.add(scenarioFor(Optional.of(requirementId)));

        assertEquals(List.of(), delta.scenarios());
    }

    @Test
    void aBlankSpecificationKeyOrTitleIsRefusedByName() {
        assertEquals("specificationKey must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new RequirementDelta(id, changeId, RequirementDeltaKind.REMOVED, " ", requirementId,
                        Optional.empty(), "t", Optional.empty(), List.of(), provenance)).getMessage());
        assertEquals("title must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new RequirementDelta(id, changeId, RequirementDeltaKind.REMOVED, "spec", requirementId,
                        Optional.empty(), "", Optional.empty(), List.of(), provenance)).getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new RequirementDelta(null, changeId, RequirementDeltaKind.REMOVED, "spec", requirementId,
                        Optional.empty(), "t", Optional.empty(), List.of(), provenance)).getMessage());
        assertEquals("changeId", assertThrows(NullPointerException.class,
                () -> new RequirementDelta(id, null, RequirementDeltaKind.REMOVED, "spec", requirementId,
                        Optional.empty(), "t", Optional.empty(), List.of(), provenance)).getMessage());
        assertEquals("kind", assertThrows(NullPointerException.class,
                () -> new RequirementDelta(id, changeId, null, "spec", requirementId,
                        Optional.empty(), "t", Optional.empty(), List.of(), provenance)).getMessage());
        assertEquals("requirementId", assertThrows(NullPointerException.class,
                () -> new RequirementDelta(id, changeId, RequirementDeltaKind.REMOVED, "spec", null,
                        Optional.empty(), "t", Optional.empty(), List.of(), provenance)).getMessage());
        assertEquals("key", assertThrows(NullPointerException.class,
                () -> new RequirementDelta(id, changeId, RequirementDeltaKind.REMOVED, "spec", requirementId,
                        null, "t", Optional.empty(), List.of(), provenance)).getMessage());
        assertEquals("statement", assertThrows(NullPointerException.class,
                () -> new RequirementDelta(id, changeId, RequirementDeltaKind.REMOVED, "spec", requirementId,
                        Optional.empty(), "t", null, List.of(), provenance)).getMessage());
        assertEquals("scenarios", assertThrows(NullPointerException.class,
                () -> new RequirementDelta(id, changeId, RequirementDeltaKind.REMOVED, "spec", requirementId,
                        Optional.empty(), "t", Optional.empty(), null, provenance)).getMessage());
        assertEquals("provenance", assertThrows(NullPointerException.class,
                () -> new RequirementDelta(id, changeId, RequirementDeltaKind.REMOVED, "spec", requirementId,
                        Optional.empty(), "t", Optional.empty(), List.of(), null)).getMessage());
    }

    private RequirementDelta delta(RequirementDeltaKind kind, Optional<String> statement, List<Scenario> scenarios) {
        return new RequirementDelta(id, changeId, kind, "spec", requirementId, Optional.empty(), "Login", statement,
                scenarios, provenance);
    }

    private Scenario scenarioFor(Optional<RequirementId> owner) {
        return new Scenario(ScenarioId.generate(), owner, "s", List.of(), "act", "outcome", provenance);
    }
}
