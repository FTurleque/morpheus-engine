package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusHttpPathParserTest {

    private final MorpheusHttpPathParser parser = MorpheusHttpPathParser.api();

    @Test
    void apiRootVariantsProduceNoSegments() {
        assertEquals(List.of(), parser.segments("/api/v1"));
        assertEquals(List.of(), parser.segments("/api/v1/"));
    }

    @Test
    void onlyOneTrailingSlashIsIgnored() {
        assertInvalid("/api/v1//");
        assertInvalid("/api/v1/projects//");
    }

    @Test
    void childPathsPreserveOrderAndIgnoreOneTrailingSlash() {
        assertEquals(List.of("projects"), parser.segments("/api/v1/projects"));
        assertEquals(List.of("projects"), parser.segments("/api/v1/projects/"));
        assertEquals(
                List.of("projects", "project-1", "requirements"),
                parser.segments("/api/v1/projects/project-1/requirements"));
    }

    @Test
    void anEncodedSlashStaysInsideItsSegment() {
        assertEquals(
                List.of("projects", "project-1", "REQ/42"),
                parser.segments("/api/v1/projects/project%2D1/REQ%2F42"));
        assertEquals(List.of("a/b"), parser.segments("/api/v1/a%2fb"));
    }

    @Test
    void aDoublyEncodedCharacterIsDecodedOnce() {
        assertEquals(List.of("%2e%2e"), parser.segments("/api/v1/%252e%252e"));
        assertEquals(List.of("a%2Fb"), parser.segments("/api/v1/a%252Fb"));
    }

    @Test
    void aPlusSignIsAPlusSign() {
        assertEquals(List.of("a+b", "a+b", "a b"), parser.segments("/api/v1/a+b/a%2Bb/a%20b"));
    }

    @Test
    void anEscapeIsReadAsUtf8WhateverItsCase() {
        assertEquals(List.of("caf\u00e9", "caf\u00e9"), parser.segments("/api/v1/caf%C3%A9/caf%c3%a9"));
    }

    @Test
    void aMalformedEscapeIsRefused() {
        assertInvalid("/api/v1/%zz");
        assertInvalid("/api/v1/a%4");
        assertInvalid("/api/v1/a%");
        assertInvalid("/api/v1/%\uff11\uff11");
        assertInvalid("/api/v1/%ff");
        assertInvalid("/api/v1/%C3");
    }

    @Test
    void thePrefixIsMatchedBySegmentNotByText() {
        assertEquals(List.of("health"), parser.segments("/%61pi/v1/health"));
        assertUnknown("/api/v1health");
        assertUnknown("/api%2Fv1/health");
        assertUnknown("/api");
        assertUnknown("api/v1/health");
    }

    @Test
    void wrongPrefixIsRejectedWithExistingNotFoundContract() {
        ApiFailure failure = assertThrows(ApiFailure.class, () -> parser.segments("/other/projects"));

        assertEquals(404, failure.status());
        assertEquals("NOT_FOUND", failure.code());
        assertEquals("unknown API route: /other/projects", failure.getMessage());
    }

    @Test
    void emptySegmentInsidePathIsRejectedWithExistingNotFoundContract() {
        ApiFailure failure = assertThrows(
                ApiFailure.class,
                () -> parser.segments("/api/v1/projects//requirements"));

        assertEquals(404, failure.status());
        assertEquals("NOT_FOUND", failure.code());
        assertEquals("invalid API path", failure.getMessage());
    }

    private void assertInvalid(String rawPath) {
        ApiFailure failure = assertThrows(ApiFailure.class, () -> parser.segments(rawPath), rawPath);

        assertEquals(404, failure.status(), rawPath);
        assertEquals("invalid API path", failure.getMessage(), rawPath);
    }

    private void assertUnknown(String rawPath) {
        ApiFailure failure = assertThrows(ApiFailure.class, () -> parser.segments(rawPath), rawPath);

        assertEquals(404, failure.status(), rawPath);
        assertEquals("unknown API route: " + rawPath, failure.getMessage(), rawPath);
    }
}
