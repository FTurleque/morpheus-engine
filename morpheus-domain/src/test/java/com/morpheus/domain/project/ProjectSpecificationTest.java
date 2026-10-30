package com.morpheus.domain.project;

import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectSpecificationTest {
    private final ProjectSpecificationId id = ProjectSpecificationId.generate();
    private final SourceLocator root = SourceLocator.file("workspace");

    @Test
    void aProjectKeepsItsDisplayNameTrimmed() {
        ProjectSpecification project = new ProjectSpecification(id, "  Morpheus  ", root);

        assertEquals(id, project.id());
        assertEquals("Morpheus", project.displayName());
        assertEquals(root, project.rootLocator());
    }

    @Test
    void aBlankDisplayNameIsRefusedByName() {
        assertEquals("displayName must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ProjectSpecification(id, "   ", root)).getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new ProjectSpecification(null, "n", root)).getMessage());
        assertEquals("displayName", assertThrows(NullPointerException.class,
                () -> new ProjectSpecification(id, null, root)).getMessage());
        assertEquals("rootLocator", assertThrows(NullPointerException.class,
                () -> new ProjectSpecification(id, "n", null)).getMessage());
    }
}
