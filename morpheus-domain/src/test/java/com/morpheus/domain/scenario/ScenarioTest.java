package com.morpheus.domain.scenario;

import com.morpheus.domain.DomainFixtures;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.requirement.RequirementId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.morpheus.domain.OrderingAssertions.assertStrictlyOrdered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioTest {
    private static final String FIRST = "01920000-0000-7000-8000-000000000001";
    private static final String SECOND = "01920000-0000-7000-8000-000000000002";

    private final ScenarioId id = ScenarioId.generate();
    private final Optional<RequirementId> requirementId = Optional.of(RequirementId.generate());
    private final Provenance provenance = DomainFixtures.provenance();

    @Test
    void aScenarioKeepsItsStepsTrimmedAndItsPreconditionsAsGiven() {
        Scenario scenario = new Scenario(id, requirementId, " Valid login ", List.of(" a user exists "),
                " the user logs in ", " access is granted ", provenance);

        assertEquals(id, scenario.id());
        assertEquals(requirementId, scenario.requirementId());
        assertEquals("Valid login", scenario.title());
        assertEquals(List.of(" a user exists "), scenario.preconditions());
        assertEquals("the user logs in", scenario.action());
        assertEquals("access is granted", scenario.expectedOutcome());
        assertEquals(provenance, scenario.provenance());
    }

    @Test
    void aScenarioMayStandWithoutRequirement() {
        assertEquals(Optional.empty(),
                new Scenario(id, Optional.empty(), "t", List.of(), "a", "o", provenance).requirementId());
    }

    @Test
    void thePreconditionsAreACopyTheCallerCannotChangeAfterwards() {
        List<String> preconditions = new ArrayList<>(List.of("p"));
        Scenario scenario = new Scenario(id, requirementId, "t", preconditions, "a", "o", provenance);
        preconditions.add("q");

        assertEquals(List.of("p"), scenario.preconditions());
    }

    @Test
    void aBlankPreconditionIsRefused() {
        assertEquals("preconditions must not contain blank values", assertThrows(IllegalArgumentException.class,
                () -> new Scenario(id, requirementId, "t", List.of("p", "  "), "a", "o", provenance)).getMessage());
    }

    @Test
    void aBlankStepIsRefusedByName() {
        assertEquals("title must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new Scenario(id, requirementId, " ", List.of(), "a", "o", provenance)).getMessage());
        assertEquals("action must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new Scenario(id, requirementId, "t", List.of(), "", "o", provenance)).getMessage());
        assertEquals("expectedOutcome must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new Scenario(id, requirementId, "t", List.of(), "a", "\n", provenance)).getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new Scenario(null, requirementId, "t", List.of(), "a", "o", provenance)).getMessage());
        assertEquals("requirementId", assertThrows(NullPointerException.class,
                () -> new Scenario(id, null, "t", List.of(), "a", "o", provenance)).getMessage());
        assertEquals("preconditions", assertThrows(NullPointerException.class,
                () -> new Scenario(id, requirementId, "t", null, "a", "o", provenance)).getMessage());
        assertEquals("action", assertThrows(NullPointerException.class,
                () -> new Scenario(id, requirementId, "t", List.of(), null, "o", provenance)).getMessage());
        assertEquals("provenance", assertThrows(NullPointerException.class,
                () -> new Scenario(id, requirementId, "t", List.of(), "a", "o", null)).getMessage());
    }

    @Test
    void anIdentityIsTheTextItWasParsedFromAndOrdersByIt() {
        assertEquals(FIRST, ScenarioId.parse(FIRST).toString());
        assertEquals(ScenarioId.parse(FIRST), ScenarioId.parse(" " + FIRST));
        assertStrictlyOrdered(ScenarioId.parse(FIRST), ScenarioId.parse(SECOND));
    }

    @Test
    void twoGeneratedIdentitiesDifferAndAnAbsentValueIsRefused() {
        assertNotEquals(ScenarioId.generate(), ScenarioId.generate());
        assertEquals("value", assertThrows(NullPointerException.class, () -> new ScenarioId(null)).getMessage());
    }
}
