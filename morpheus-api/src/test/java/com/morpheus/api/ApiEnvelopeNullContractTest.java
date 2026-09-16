package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The envelope records the four extension routers now share reject every null component and copy {@code details}
 * defensively. Their twelve private predecessors did neither, so DT-17 (16/09/2026) tightened a contract on four
 * write surfaces at once. That difference is pinned here rather than left to be met in production.
 *
 * <p><strong>The decision.</strong> The stricter contract is adopted deliberately. It was examined path by path
 * before the migration, and it is reachable on exactly one of them:</p>
 *
 * <ul>
 *   <li><strong>The error path cannot produce a null at all</strong>, and this is a property of a type rather than
 *       an absence of evidence. Every {@code ApiError} the four routers build takes its arguments either from
 *       literals and {@code safeMessage}, which is null-safe by construction, or from an {@link ApiFailure}. That
 *       class is {@code final} and its every constructor runs {@code Objects.requireNonNull} on the message and the
 *       code and {@code Map.copyOf(Objects.requireNonNull(...))} on the details; its four-argument constructor has
 *       no caller at all, so {@code details} on these four routers is always {@code Map.of()}. No subclass can
 *       weaken this and no caller can bypass it, which is what the first two cases below assert.</li>
 *   <li><strong>The success path is where the change bites.</strong> {@code MorpheusQueryHttpRoutes} already
 *       rejected a null through its own {@code json(...)}, but the {@code Response} records of
 *       {@code MorpheusPolicyHttpRoutes} and {@code MorpheusPolicyManagementHttpRoutes} validate nothing, and
 *       {@code MorpheusReasoningHttpRoutes} passes a service result straight through. Before DT-17 a null there
 *       would have serialized {@code "data":null} inside a 200; now it raises and the request answers 500.</li>
 * </ul>
 *
 * <p>Turning the first into the second is the intended trade. A success envelope whose payload is null is a silent
 * degradation, which {@code .claude/rules/code-style.md} refuses in favour of an explicit failure, and it is the
 * kind of answer a client cannot distinguish from a real empty result. No live route produces one --
 * {@code ExtensionRoutesResponseWritingParityTest} exercises a success on each of the four contexts and none
 * changed -- so the guard is a guard, not a behaviour change anyone can observe today.</p>
 */
class ApiEnvelopeNullContractTest {

    @Test
    void apiFailureRefusesANullMessageCodeOrDetailsSoTheErrorPathCannotCarryOne() {
        assertThrows(NullPointerException.class, () -> new ApiFailure(400, "BAD_REQUEST", null));
        assertThrows(NullPointerException.class, () -> new ApiFailure(400, null, "message"));
        assertThrows(NullPointerException.class, () -> new ApiFailure(400, "BAD_REQUEST", "message", null));
    }

    @Test
    void everyApiFailureFactoryYieldsANonNullMessageCodeAndEmptyDetails() {
        for (ApiFailure failure : new ApiFailure[] {
                ApiFailure.badRequest("bad"),
                ApiFailure.notFound("missing"),
                ApiFailure.methodNotAllowed("method"),
                ApiFailure.conflict("conflict"),
                ApiFailure.unsupportedMediaType("media")}) {
            assertTrue(failure.getMessage() != null && !failure.getMessage().isBlank(), "message");
            assertTrue(failure.code() != null && !failure.code().isBlank(), "code");
            assertEquals(Map.of(), failure.details(), "no factory carries details, so the envelope shows {}");
        }
    }

    @Test
    void theSharedEnvelopeRecordsRejectEveryNullComponent() {
        assertThrows(NullPointerException.class, () -> new MorpheusHttpServer.ApiSuccess(null, "data"));
        assertThrows(NullPointerException.class, () -> new MorpheusHttpServer.ApiSuccess("v1", null));
        assertThrows(NullPointerException.class, () -> new MorpheusHttpServer.ApiError(null, "m", Map.of()));
        assertThrows(NullPointerException.class, () -> new MorpheusHttpServer.ApiError("CODE", null, Map.of()));
        assertThrows(NullPointerException.class, () -> new MorpheusHttpServer.ApiError("CODE", "m", null));
        assertThrows(NullPointerException.class, () -> new MorpheusHttpServer.ApiErrorEnvelope(null,
                new MorpheusHttpServer.ApiError("CODE", "m", Map.of())));
        assertThrows(NullPointerException.class, () -> new MorpheusHttpServer.ApiErrorEnvelope("v1", null));
    }

    /** The second half of the tightening: {@code details} is copied, so a caller's later mutation cannot reach it. */
    @Test
    void theSharedErrorRecordCopiesDetailsDefensively() {
        Map<String, Object> mutable = new HashMap<>(Map.of("field", "value"));
        MorpheusHttpServer.ApiError error = new MorpheusHttpServer.ApiError("CODE", "message", mutable);
        mutable.put("added", "after");

        assertEquals(Map.of("field", "value"), error.details());
        assertThrows(UnsupportedOperationException.class, () -> error.details().put("direct", "write"));
    }
}
