package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusHttpQueryTest {

    @Test
    void nullAndBlankQueriesBehaveAsEmpty() {
        assertEquals(7, MorpheusHttpQuery.parse(null).intValue("limit", 7, 1, 10));
        assertEquals(11L, MorpheusHttpQuery.parse("   ").longValue("age", 11L, 1L, 20L));
        assertTrue(MorpheusHttpQuery.parse("").string("missing").isEmpty());
    }

    @Test
    void parseDecodesNamesValuesPlusSignsAndMissingEquals() {
        MorpheusHttpQuery query = MorpheusHttpQuery.parse(
                "owner%49d=REQ%2D1&label=hello+world&flag&&encoded%20key=value%2Bplus");

        assertEquals("REQ-1", query.required("ownerId"));
        assertEquals("hello world", query.required("label"));
        assertEquals("", query.string("flag").orElseThrow());
        assertEquals("value+plus", query.required("encoded key"));
    }

    @Test
    void duplicateParameterIsRejected() {
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("limit=1&limit=2"),
                "duplicate query parameter: limit");
    }

    @Test
    void blankDecodedParameterNameIsRejected() {
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("%20=value"),
                "query parameter name must not be blank");
    }

    @Test
    void requiredRejectsMissingAndBlankValues() {
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("present=value").required("missing"),
                "query parameter is required: missing");
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("ownerId=").required("ownerId"),
                "query parameter is required: ownerId");
    }

    @Test
    void intValueSupportsDefaultAndInclusiveBounds() {
        assertEquals(5, MorpheusHttpQuery.parse(null).intValue("limit", 5, 1, 10));
        assertEquals(1, MorpheusHttpQuery.parse("limit=1").intValue("limit", 5, 1, 10));
        assertEquals(10, MorpheusHttpQuery.parse("limit=10").intValue("limit", 5, 1, 10));
    }

    @Test
    void intValueRejectsMalformedAndOutOfRangeValues() {
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("limit=nope").intValue("limit", 5, 1, 10),
                "limit must be an integer");
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("limit=0").intValue("limit", 5, 1, 10),
                "limit must be between 1 and 10");
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("limit=11").intValue("limit", 5, 1, 10),
                "limit must be between 1 and 10");
    }

    @Test
    void longValueSupportsDefaultAndInclusiveBounds() {
        assertEquals(30L, MorpheusHttpQuery.parse(null).longValue("maxAgeMinutes", 30L, 1L, 60L));
        assertEquals(1L, MorpheusHttpQuery.parse("maxAgeMinutes=1")
                .longValue("maxAgeMinutes", 30L, 1L, 60L));
        assertEquals(60L, MorpheusHttpQuery.parse("maxAgeMinutes=60")
                .longValue("maxAgeMinutes", 30L, 1L, 60L));
    }

    @Test
    void longValueRejectsMalformedAndOutOfRangeValues() {
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("maxAgeMinutes=nope")
                        .longValue("maxAgeMinutes", 30L, 1L, 60L),
                "maxAgeMinutes must be an integer");
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("maxAgeMinutes=0")
                        .longValue("maxAgeMinutes", 30L, 1L, 60L),
                "maxAgeMinutes must be between 1 and 60");
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("maxAgeMinutes=61")
                        .longValue("maxAgeMinutes", 30L, 1L, 60L),
                "maxAgeMinutes must be between 1 and 60");
    }

    @Test
    void rejectUnknownAcceptsAllowedKeysAndRejectsOthers() {
        MorpheusHttpQuery allowed = MorpheusHttpQuery.parse("offset=0&limit=10");
        assertDoesNotThrow(() -> allowed.rejectUnknown(Set.of("offset", "limit")));

        assertBadRequest(
                () -> MorpheusHttpQuery.parse("offset=0&surprise=true")
                        .rejectUnknown(Set.of("offset", "limit")),
                "unknown query parameter: surprise");
    }

    private static void assertBadRequest(ThrowingRunnable action, String message) {
        ApiFailure failure = assertThrows(ApiFailure.class, action::run);
        assertEquals(400, failure.status());
        assertEquals("BAD_REQUEST", failure.code());
        assertEquals(message, failure.getMessage());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
