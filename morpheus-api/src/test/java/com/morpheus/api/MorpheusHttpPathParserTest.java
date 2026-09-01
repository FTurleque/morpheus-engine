package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusHttpPathParserTest {

    private final MorpheusHttpPathParser parser = new MorpheusHttpPathParser(MorpheusHttpServer.API_PREFIX);

    @Test
    void apiRootVariantsProduceNoSegments() {
        assertEquals(List.of(), parser.segments("/api/v1"));
        assertEquals(List.of(), parser.segments("/api/v1/"));
        assertEquals(List.of(), parser.segments("/api/v1//"));
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
    void pathSegmentsAreUrlDecodedAfterSplitting() {
        assertEquals(
                List.of("projects", "project-1", "REQ/42", "hello world"),
                parser.segments("/api/v1/projects/project%2D1/REQ%2F42/hello+world"));
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

    @Test
    void constructorRejectsNullPrefix() {
        assertThrows(NullPointerException.class, () -> new MorpheusHttpPathParser(null));
    }
}
