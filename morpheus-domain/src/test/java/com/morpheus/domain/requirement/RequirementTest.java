package com.morpheus.domain.requirement;

import com.morpheus.domain.DomainFixtures;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.specification.SpecificationId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementTest {
    private final RequirementId id = RequirementId.generate();
    private final SpecificationId specificationId = SpecificationId.generate();
    private final Provenance provenance = DomainFixtures.provenance();

    @Test
    void aRequirementKeepsItsTextAndKeyTrimmed() {
        Requirement requirement = new Requirement(id, specificationId, Optional.of(" REQ-1 "), " Login ",
                "  The system SHALL log in.  ", provenance);

        assertEquals(id, requirement.id());
        assertEquals(specificationId, requirement.specificationId());
        assertEquals(Optional.of("REQ-1"), requirement.key());
        assertEquals("Login", requirement.title());
        assertEquals("The system SHALL log in.", requirement.statement());
        assertEquals(provenance, requirement.provenance());
    }

    @Test
    void aBlankKeyIsReadAsNoKey() {
        assertEquals(Optional.empty(),
                new Requirement(id, specificationId, Optional.of(" "), "t", "s", provenance).key());
    }

    @Test
    void aBlankTitleOrStatementIsRefusedByName() {
        assertEquals("title must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new Requirement(id, specificationId, Optional.empty(), " ", "s", provenance)).getMessage());
        assertEquals("statement must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new Requirement(id, specificationId, Optional.empty(), "t", "", provenance)).getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new Requirement(null, specificationId, Optional.empty(), "t", "s", provenance)).getMessage());
        assertEquals("specificationId", assertThrows(NullPointerException.class,
                () -> new Requirement(id, null, Optional.empty(), "t", "s", provenance)).getMessage());
        assertEquals("key", assertThrows(NullPointerException.class,
                () -> new Requirement(id, specificationId, null, "t", "s", provenance)).getMessage());
        assertEquals("statement", assertThrows(NullPointerException.class,
                () -> new Requirement(id, specificationId, Optional.empty(), "t", null, provenance)).getMessage());
        assertEquals("provenance", assertThrows(NullPointerException.class,
                () -> new Requirement(id, specificationId, Optional.empty(), "t", "s", null)).getMessage());
    }
}
