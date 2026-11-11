package com.morpheus.domain.decision;

import com.morpheus.domain.DomainFixtures;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.provenance.Provenance;
import org.junit.jupiter.api.Test;

import static com.morpheus.domain.OrderingAssertions.assertStrictlyOrdered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DesignDecisionTest {
    private static final String FIRST = "01920000-0000-7000-8000-000000000001";
    private static final String SECOND = "01920000-0000-7000-8000-000000000002";

    private final DesignDecisionId id = DesignDecisionId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final Provenance provenance = DomainFixtures.provenance();

    @Test
    void aDecisionKeepsItsTitleAndTextTrimmed() {
        DesignDecision decision = new DesignDecision(id, changeId, "  Use SQLite ", " Local first ", provenance);

        assertEquals(id, decision.id());
        assertEquals(changeId, decision.changeId());
        assertEquals("Use SQLite", decision.title());
        assertEquals("Local first", decision.decision());
        assertEquals(provenance, decision.provenance());
    }

    @Test
    void aBlankTitleOrDecisionIsRefusedByName() {
        assertEquals("title must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new DesignDecision(id, changeId, " ", "d", provenance)).getMessage());
        assertEquals("decision must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new DesignDecision(id, changeId, "t", "", provenance)).getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new DesignDecision(null, changeId, "t", "d", provenance)).getMessage());
        assertEquals("changeId", assertThrows(NullPointerException.class,
                () -> new DesignDecision(id, null, "t", "d", provenance)).getMessage());
        assertEquals("decision", assertThrows(NullPointerException.class,
                () -> new DesignDecision(id, changeId, "t", null, provenance)).getMessage());
        assertEquals("provenance", assertThrows(NullPointerException.class,
                () -> new DesignDecision(id, changeId, "t", "d", null)).getMessage());
    }

    @Test
    void anIdentityIsTheTextItWasParsedFromAndOrdersByIt() {
        assertEquals(FIRST, DesignDecisionId.parse(" " + FIRST + " ").toString());
        assertEquals(DesignDecisionId.parse(FIRST), DesignDecisionId.parse(FIRST));
        assertStrictlyOrdered(DesignDecisionId.parse(FIRST), DesignDecisionId.parse(SECOND));
    }

    @Test
    void twoGeneratedIdentitiesDifferAndAnAbsentValueIsRefused() {
        assertNotEquals(DesignDecisionId.generate(), DesignDecisionId.generate());
        assertEquals("value", assertThrows(NullPointerException.class, () -> new DesignDecisionId(null)).getMessage());
    }
}
