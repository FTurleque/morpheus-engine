package com.morpheus.domain.specification;

import com.morpheus.domain.DomainFixtures;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.morpheus.domain.OrderingAssertions.assertStrictlyOrdered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpecificationTest {
    private static final String FIRST = "01920000-0000-7000-8000-000000000001";
    private static final String SECOND = "01920000-0000-7000-8000-000000000002";

    private final SpecificationId id = SpecificationId.generate();
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final Provenance provenance = DomainFixtures.provenance();

    @Test
    void aSpecificationKeepsItsTextTrimmed() {
        Specification specification = new Specification(id, projectId, " auth ", " Authentication ",
                Optional.of(" Who may log in. "), provenance);

        assertEquals(id, specification.id());
        assertEquals(projectId, specification.projectId());
        assertEquals("auth", specification.key());
        assertEquals("Authentication", specification.title());
        assertEquals(Optional.of("Who may log in."), specification.description());
        assertEquals(provenance, specification.provenance());
    }

    @Test
    void aBlankDescriptionIsReadAsNoDescription() {
        assertEquals(Optional.empty(),
                new Specification(id, projectId, "k", "t", Optional.of("  "), provenance).description());
    }

    @Test
    void aBlankKeyOrTitleIsRefusedByName() {
        assertEquals("key must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new Specification(id, projectId, " ", "t", Optional.empty(), provenance)).getMessage());
        assertEquals("title must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new Specification(id, projectId, "k", "", Optional.empty(), provenance)).getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new Specification(null, projectId, "k", "t", Optional.empty(), provenance)).getMessage());
        assertEquals("projectId", assertThrows(NullPointerException.class,
                () -> new Specification(id, null, "k", "t", Optional.empty(), provenance)).getMessage());
        assertEquals("key", assertThrows(NullPointerException.class,
                () -> new Specification(id, projectId, null, "t", Optional.empty(), provenance)).getMessage());
        assertEquals("description", assertThrows(NullPointerException.class,
                () -> new Specification(id, projectId, "k", "t", null, provenance)).getMessage());
        assertEquals("provenance", assertThrows(NullPointerException.class,
                () -> new Specification(id, projectId, "k", "t", Optional.empty(), null)).getMessage());
    }

    @Test
    void anIdentityIsTheTextItWasParsedFromAndOrdersByIt() {
        assertEquals(FIRST, SpecificationId.parse(FIRST).toString());
        assertEquals(SpecificationId.parse(FIRST), SpecificationId.parse(FIRST + " "));
        assertStrictlyOrdered(SpecificationId.parse(FIRST), SpecificationId.parse(SECOND));
    }

    @Test
    void twoGeneratedIdentitiesDifferAndAnAbsentValueIsRefused() {
        assertNotEquals(SpecificationId.generate(), SpecificationId.generate());
        assertEquals("value", assertThrows(NullPointerException.class, () -> new SpecificationId(null)).getMessage());
    }
}
