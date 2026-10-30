package com.morpheus.domain.task;

import com.morpheus.domain.DomainFixtures;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.provenance.Provenance;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.morpheus.domain.OrderingAssertions.assertStrictlyOrdered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationTaskTest {
    private static final String FIRST = "01920000-0000-7000-8000-000000000001";
    private static final String SECOND = "01920000-0000-7000-8000-000000000002";

    private final TaskId id = TaskId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final Provenance provenance = DomainFixtures.provenance();

    @Test
    void aTaskKeepsItsTitleAndKeyTrimmedAndItsCompletion() {
        ImplementationTask task = new ImplementationTask(id, changeId, Optional.of(" 1.1 "), " Write the store ",
                true, provenance);

        assertEquals(id, task.id());
        assertEquals(changeId, task.changeId());
        assertEquals(Optional.of("1.1"), task.key());
        assertEquals("Write the store", task.title());
        assertTrue(task.completed());
        assertEquals(provenance, task.provenance());
    }

    @Test
    void aBlankKeyIsReadAsNoKey() {
        ImplementationTask task = new ImplementationTask(id, changeId, Optional.of("  "), "t", false, provenance);

        assertEquals(Optional.empty(), task.key());
        assertFalse(task.completed());
    }

    @Test
    void aBlankTitleIsRefusedByName() {
        assertEquals("title must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ImplementationTask(id, changeId, Optional.empty(), " ", false, provenance)).getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new ImplementationTask(null, changeId, Optional.empty(), "t", false, provenance)).getMessage());
        assertEquals("changeId", assertThrows(NullPointerException.class,
                () -> new ImplementationTask(id, null, Optional.empty(), "t", false, provenance)).getMessage());
        assertEquals("key", assertThrows(NullPointerException.class,
                () -> new ImplementationTask(id, changeId, null, "t", false, provenance)).getMessage());
        assertEquals("title", assertThrows(NullPointerException.class,
                () -> new ImplementationTask(id, changeId, Optional.empty(), null, false, provenance)).getMessage());
        assertEquals("provenance", assertThrows(NullPointerException.class,
                () -> new ImplementationTask(id, changeId, Optional.empty(), "t", false, null)).getMessage());
    }

    @Test
    void anIdentityIsTheTextItWasParsedFromAndOrdersByIt() {
        assertEquals(FIRST, TaskId.parse(FIRST).toString());
        assertEquals(TaskId.parse(FIRST), TaskId.parse(" " + FIRST));
        assertStrictlyOrdered(TaskId.parse(FIRST), TaskId.parse(SECOND));
    }

    @Test
    void twoGeneratedIdentitiesDifferAndAnAbsentValueIsRefused() {
        assertNotEquals(TaskId.generate(), TaskId.generate());
        assertEquals("value", assertThrows(NullPointerException.class, () -> new TaskId(null)).getMessage());
    }
}
