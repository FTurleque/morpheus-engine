package com.morpheus.architecture.m24;

import com.morpheus.application.query.dsl.QueryCell;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryNullSemanticsContractTest {
    @Test
    void absentAndEmptyTextRemainDistinct() {
        QueryCell absent = QueryCell.optional("description", Optional.empty());
        QueryCell empty = QueryCell.scalar("description", "");

        assertTrue(absent.values().isEmpty());
        assertTrue(absent.first().isEmpty());
        assertEquals(List.of(""), empty.values());
        assertTrue(empty.first().isPresent());
        assertFalse(empty.values().isEmpty());
    }
}
