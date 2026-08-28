package com.morpheus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusHttpRouteResponseTest {

    @Test
    void acceptsBoundaryHttpStatusesAndPreservesData() {
        Object data = new Object();

        MorpheusHttpRouteResponse lower = new MorpheusHttpRouteResponse(200, data);
        MorpheusHttpRouteResponse upper = new MorpheusHttpRouteResponse(599, data);

        assertEquals(200, lower.status());
        assertEquals(data, lower.data());
        assertEquals(599, upper.status());
        assertEquals(data, upper.data());
    }

    @Test
    void rejectsStatusBelowHttpResponseRange() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new MorpheusHttpRouteResponse(199, new Object()));

        assertEquals("route status must be between 200 and 599", failure.getMessage());
    }

    @Test
    void rejectsStatusAboveHttpResponseRange() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new MorpheusHttpRouteResponse(600, new Object()));

        assertEquals("route status must be between 200 and 599", failure.getMessage());
    }

    @Test
    void rejectsNullData() {
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new MorpheusHttpRouteResponse(200, null));

        assertEquals("data", failure.getMessage());
        assertNotNull(failure);
    }
}
