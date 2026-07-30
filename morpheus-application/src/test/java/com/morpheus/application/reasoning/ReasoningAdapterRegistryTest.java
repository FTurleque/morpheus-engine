package com.morpheus.application.reasoning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasoningAdapterRegistryTest {

    @Test
    void standardRegistryAlwaysExposesDeterministicBuiltin() {
        assertTrue(ReasoningAdapterRegistry.standard().descriptors().stream()
                .anyMatch(descriptor -> descriptor.id().equals(EvidenceSynthesisReasoningAdapter.ID)));
    }

    @Test
    void brokenOptionalDescriptionDoesNotMakeCatalogUnavailable() {
        ReasoningAdapter broken = new ReasoningAdapter() {
            @Override
            public String id() {
                return "broken-description";
            }

            @Override
            public String description() {
                throw new IllegalStateException("simulated optional provider failure");
            }

            @Override
            public ReasoningContracts.AdapterResult reason(ReasoningContracts.AdapterRequest request) {
                return ReasoningContracts.AdapterResult.empty();
            }
        };
        ReasoningAdapter healthy = new EvidenceSynthesisReasoningAdapter();

        var descriptors = new ReasoningAdapterRegistry(List.of(broken, healthy)).descriptors();

        assertEquals(1, descriptors.size());
        assertEquals(EvidenceSynthesisReasoningAdapter.ID, descriptors.getFirst().id());
    }

    @Test
    void explicitRegistryRejectsDuplicateAdapterIdentity() {
        ReasoningAdapter first = new EvidenceSynthesisReasoningAdapter();
        ReasoningAdapter duplicate = new ReasoningAdapter() {
            @Override
            public String id() {
                return EvidenceSynthesisReasoningAdapter.ID;
            }

            @Override
            public ReasoningContracts.AdapterResult reason(ReasoningContracts.AdapterRequest request) {
                return ReasoningContracts.AdapterResult.empty();
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> new ReasoningAdapterRegistry(List.of(first, duplicate)));
    }
}
