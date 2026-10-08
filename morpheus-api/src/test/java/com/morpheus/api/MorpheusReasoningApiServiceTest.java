package com.morpheus.api;

import com.morpheus.application.reasoning.ReasoningAdapter;
import com.morpheus.application.reasoning.ReasoningAdapterRegistry;
import com.morpheus.application.reasoning.ReasoningContracts;
import com.morpheus.application.reasoning.ReasoningService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Only the conversion of the request maps a null to "invalid request": a null pointer raised by the service is an
 * internal defect, and reporting it to the client as its own mistake would hide it (SB-AUD-2).
 */
class MorpheusReasoningApiServiceTest {

    @Test
    void aNullInsideTheRequestIsAnInvalidRequest() {
        MorpheusReasoningApiService api = new MorpheusReasoningApiService(
                new ReasoningService(ReasoningAdapterRegistry.empty()));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> api.analyze(
                new MorpheusReasoningApiService.ReasoningRequest(null, List.of(), List.of(), null, null)));

        assertEquals("reasoning request contains a null value", failure.getMessage());
    }

    @Test
    void aNullPointerRaisedInsideTheServiceIsNotReportedAsAnInvalidRequest() {
        MorpheusReasoningApiService api = new MorpheusReasoningApiService(
                new ReasoningService(new ReasoningAdapterRegistry(List.of(new IdLosingAdapter()))));

        // The adapter fails, and the service then records the failure under an identifier the adapter no longer
        // gives: the NullPointerException comes from the service, not from anything the client sent.
        assertThrows(NullPointerException.class, () -> api.analyze(
                new MorpheusReasoningApiService.ReasoningRequest(
                        "Which requirement is covered?", List.of(), List.of("id-losing"), null, null)));
    }

    /** Answers its identifier once, for its registration, and null afterwards; its reasoning always fails. */
    private static final class IdLosingAdapter implements ReasoningAdapter {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String id() {
            return calls.getAndIncrement() == 0 ? "id-losing" : null;
        }

        @Override
        public ReasoningContracts.AdapterResult reason(ReasoningContracts.AdapterRequest request) {
            throw new IllegalStateException("adapter failure");
        }
    }
}
