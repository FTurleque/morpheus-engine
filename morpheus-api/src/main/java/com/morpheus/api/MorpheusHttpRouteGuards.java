package com.morpheus.api;

import java.util.List;

/** Shared local HTTP route guards preserving the stable API failure contract. */
final class MorpheusHttpRouteGuards {
    private MorpheusHttpRouteGuards() {
    }

    static void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw ApiFailure.methodNotAllowed("expected HTTP " + expected + " but received " + actual);
        }
    }

    static void requireExactSegments(List<String> segments, int expected) {
        if (segments.size() != expected) {
            throw ApiFailure.notFound("unknown API route");
        }
    }
}
