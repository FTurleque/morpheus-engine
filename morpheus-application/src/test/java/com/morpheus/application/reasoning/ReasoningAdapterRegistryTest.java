package com.morpheus.application.reasoning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasoningAdapterRegistryTest {

    @Test
    void standardRegistryAlwaysExposesDeterministicBuiltin() {
        assertTrue(ReasoningAdapterRegistry.standard(failure -> { }).descriptors().stream()
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

    /**
     * An adapter that is installed and broken used to disappear exactly as if it had never been installed: the user
     * read "unknown reasoning adapter" with nothing to say it had been there. Discovery still leaves it out, and now
     * says so to whoever listens.
     */
    @Test
    void anInstalledAdapterThatCannotBeBuiltIsLeftOutAndReported() {
        List<Throwable> setAside = new ArrayList<>();

        ReasoningAdapterRegistry registry = ReasoningAdapterRegistry.standard(setAside::add);

        assertTrue(registry.descriptors().stream()
                .anyMatch(descriptor -> descriptor.id().equals(EvidenceSynthesisReasoningAdapter.ID)));
        assertTrue(registry.descriptors().stream().noneMatch(descriptor -> descriptor.id().equals("fixture-broken")));
        assertEquals(1, setAside.size(), () -> "one broken provider is declared in the test resources: " + setAside);
        Throwable failure = setAside.get(0);
        Throwable origin = failure.getCause() == null ? failure : failure.getCause();
        assertEquals(FixtureBrokenReasoningAdapter.FAILURE, origin.getMessage());
    }

    @Test
    void anAdapterWhoseDescriptionFailsIsLeftOutOfTheCatalogAndReportedEachTime() {
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
        List<Throwable> setAside = new ArrayList<>();
        ReasoningAdapterRegistry registry = new ReasoningAdapterRegistry(
                List.of(broken, new EvidenceSynthesisReasoningAdapter()), setAside::add);

        assertEquals(1, registry.descriptors().size());
        assertEquals(1, registry.descriptors().size());

        assertEquals(2, setAside.size(), "each reading of the catalog that leaves the adapter out says so");
        assertEquals("simulated optional provider failure", setAside.get(0).getMessage());
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
