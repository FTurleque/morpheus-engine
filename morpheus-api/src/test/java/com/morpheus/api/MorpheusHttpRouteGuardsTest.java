package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusHttpRouteGuardsTest {

    @Test
    void matchingMethodPasses() {
        assertDoesNotThrow(() -> MorpheusHttpRouteGuards.requireMethod("GET", "GET"));
    }

    @Test
    void mismatchedMethodPreservesStableFailure() {
        ApiFailure failure = assertThrows(
                ApiFailure.class,
                () -> MorpheusHttpRouteGuards.requireMethod("POST", "GET"));

        assertEquals(405, failure.status());
        assertEquals("METHOD_NOT_ALLOWED", failure.code());
        assertEquals("expected HTTP GET but received POST", failure.getMessage());
        assertTrue(failure.details().isEmpty());
    }

    @Test
    void exactSegmentCountPasses() {
        assertDoesNotThrow(() -> MorpheusHttpRouteGuards.requireExactSegments(List.of("projects", "p1", "sync"), 3));
    }

    @Test
    void wrongSegmentCountPreservesStableFailure() {
        ApiFailure failure = assertThrows(
                ApiFailure.class,
                () -> MorpheusHttpRouteGuards.requireExactSegments(List.of("projects", "p1"), 3));

        assertEquals(404, failure.status());
        assertEquals("NOT_FOUND", failure.code());
        assertEquals("unknown API route", failure.getMessage());
        assertTrue(failure.details().isEmpty());
    }
}
