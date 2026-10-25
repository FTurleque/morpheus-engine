package com.morpheus.api.transport;

import java.util.List;

/** Shared local HTTP route guards preserving the stable API failure contract. */
public final class MorpheusHttpRouteGuards {
    private MorpheusHttpRouteGuards() {
    }

    public static void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw ApiFailure.methodNotAllowed("expected HTTP " + expected + " but received " + actual);
        }
    }

    public static void requireExactSegments(List<String> segments, int expected) {
        if (segments.size() != expected) {
            throw ApiFailure.notFound("unknown API route");
        }
    }
}
