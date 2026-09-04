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

    @Test
    void aQueryExactlyAtTheTotalBudgetIsAcceptedAndOneByteMoreIsRefused() {
        String atLimit = queryOfExactly(HttpQueryBudget.MAX_QUERY_BYTES, "x");
        assertEquals(4, countParameters(atLimit));
        assertDoesNotThrow(() -> MorpheusHttpQuery.parse(atLimit));

        assertBadRequest(
                () -> MorpheusHttpQuery.parse(atLimit + "x"),
                "query string exceeds " + HttpQueryBudget.MAX_QUERY_BYTES + " bytes");
    }

    /**
     * The budget is counted in UTF-8 bytes, like every other MORPHEUS input budget.
     *
     * <p>Counting characters instead would let a multi-byte query weigh twice what it was allowed, which is
     * exactly the discrepancy a byte budget exists to close.</p>
     */
    @Test
    void theTotalBudgetCountsUtf8BytesRatherThanCharacters() {
        String multibyte = queryOfExactly(HttpQueryBudget.MAX_QUERY_BYTES / 2 + 8, "é");
        assertTrue(multibyte.length() < HttpQueryBudget.MAX_QUERY_BYTES,
                "the query must be comfortably within budget when it is counted in characters");

        assertBadRequest(
                () -> MorpheusHttpQuery.parse(multibyte),
                "query string exceeds " + HttpQueryBudget.MAX_QUERY_BYTES + " bytes");

        String withinBudget = "é".repeat(64);
        assertEquals(withinBudget, MorpheusHttpQuery.parse("q=" + withinBudget).required("q"));
    }

    @Test
    void theParameterCountIsBoundedAtItsLimit() {
        StringBuilder atLimit = new StringBuilder();
        for (int index = 0; index < HttpQueryBudget.MAX_PARAMETERS; index++) {
            if (index > 0) atLimit.append('&');
            atLimit.append("p").append(index).append("=v");
        }
        MorpheusHttpQuery accepted = MorpheusHttpQuery.parse(atLimit.toString());
        assertEquals("v", accepted.required("p0"));
        assertEquals("v", accepted.required("p" + (HttpQueryBudget.MAX_PARAMETERS - 1)));

        String overLimit = atLimit + "&overflow=v";
        assertBadRequest(
                () -> MorpheusHttpQuery.parse(overLimit),
                "query string exceeds " + HttpQueryBudget.MAX_PARAMETERS + " parameters");
    }

    @Test
    void parameterNamesAndValuesAreBoundedIndependently() {
        String longestName = "n".repeat(HttpQueryBudget.MAX_PARAMETER_NAME_BYTES);
        assertEquals("v", MorpheusHttpQuery.parse(longestName + "=v").required(longestName));
        assertBadRequest(
                () -> MorpheusHttpQuery.parse(longestName + "n=v"),
                "query parameter name exceeds " + HttpQueryBudget.MAX_PARAMETER_NAME_BYTES + " bytes");

        String longestValue = "v".repeat(HttpQueryBudget.MAX_PARAMETER_VALUE_BYTES);
        assertEquals(longestValue, MorpheusHttpQuery.parse("q=" + longestValue).required("q"));
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("q=" + longestValue + "v"),
                "query parameter value exceeds " + HttpQueryBudget.MAX_PARAMETER_VALUE_BYTES + " bytes");
    }

    /**
     * A value is bounded before it is decoded, on the encoded text.
     *
     * <p>Percent-decoding only ever shrinks a slice, so a raw slice within budget cannot decode into one that is
     * not -- which is what lets the check happen before the allocation it guards rather than after it.</p>
     */
    @Test
    void anOversizedValueIsRefusedOnItsEncodedFormBeforeItIsDecoded() {
        String encoded = "%41".repeat(HttpQueryBudget.MAX_PARAMETER_VALUE_BYTES / 2);
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("q=" + encoded),
                "query parameter value exceeds " + HttpQueryBudget.MAX_PARAMETER_VALUE_BYTES + " bytes");
    }

    @Test
    void invalidPercentEncodingIsAClientErrorRatherThanAnInternalOne() {
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("q=%zz"),
                "query parameter uses an invalid percent-encoding");
        assertBadRequest(
                () -> MorpheusHttpQuery.parse("q=%"),
                "query parameter uses an invalid percent-encoding");
    }

    @Test
    void emptySeparatorsAndTrailingAmpersandsStayWithinTheParameterBudget() {
        MorpheusHttpQuery query = MorpheusHttpQuery.parse("&&a=1&&&b=2&");

        assertEquals("1", query.required("a"));
        assertEquals("2", query.required("b"));
    }

    /**
     * A query of exactly {@code characters} characters, spread over four parameters.
     *
     * <p>The total budget cannot be exercised with one parameter, because the per-value budget is smaller and
     * would be the bound that fires. Four is what every route stays under, so this is also a realistic shape.</p>
     */
    private static String queryOfExactly(int characters, String filler) {
        int parameters = 4;
        int overhead = parameters * "pN=".length() + parameters - 1;
        int payload = characters - overhead;
        StringBuilder query = new StringBuilder();
        for (int index = 0; index < parameters; index++) {
            if (index > 0) query.append('&');
            int share = payload / parameters + (index < payload % parameters ? 1 : 0);
            query.append('p').append(index).append('=').append(filler.repeat(share));
        }
        return query.toString();
    }

    private static int countParameters(String query) {
        return query.split("&").length;
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
