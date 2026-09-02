package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins exact {@code application/json} admission.
 *
 * <p>The previous prefix test admitted every media type whose name merely started with {@code application/json},
 * so a request declaring {@code application/jsonp} or {@code application/json-patch+json} was decoded as if it had
 * declared JSON. Each rejected value below is a type a prefix test accepts.</p>
 */
class JsonMediaTypeTest {
    @Test
    void exactJsonTypesAreAccepted() {
        List<String> accepted = List.of(
                "application/json",
                "application/json; charset=utf-8",
                "application/json;charset=UTF-8",
                "APPLICATION/JSON",
                "Application/Json; Charset=UTF-8",
                "  application/json  ",
                "application/json ; charset = utf-8",
                "application/json; charset=\"utf-8\"",
                "application/json;charset=utf8");

        for (String header : accepted) {
            assertTrue(JsonMediaType.isJson(header), () -> "must accept Content-Type: " + header);
        }
    }

    @Test
    void typesThatMerelyStartWithApplicationJsonAreRejected() {
        List<String> rejected = List.of(
                "application/jsonp",
                "application/json-whatever",
                "application/jsonmalicious",
                "application/json-patch+json",
                "application/json5",
                "text/json",
                "text/plain",
                "application/javascript",
                "application/x-www-form-urlencoded",
                "multipart/form-data; boundary=x",
                "");

        for (String header : rejected) {
            assertFalse(JsonMediaType.isJson(header), () -> "must reject Content-Type: " + header);
        }
    }

    @Test
    void aMissingContentTypeIsRejected() {
        assertFalse(JsonMediaType.isJson(null), "an absent Content-Type must not be treated as JSON");
    }

    @Test
    void aDeclaredNonUtf8CharsetIsRejectedBecauseTheBodyIsDecodedAsUtf8() {
        assertFalse(JsonMediaType.isJson("application/json; charset=iso-8859-1"));
        assertFalse(JsonMediaType.isJson("application/json; charset=us-ascii"));
        assertFalse(JsonMediaType.isJson("application/json; charset=\"windows-1252\""));
    }

    @Test
    void unrelatedMimeParametersDoNotChangeAdmission() {
        assertTrue(JsonMediaType.isJson("application/json; version=1"));
        assertTrue(JsonMediaType.isJson("application/json; charset=utf-8; version=1"));
        assertFalse(JsonMediaType.isJson("application/jsonp; charset=utf-8"));
    }
}
