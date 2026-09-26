package com.morpheus.domain.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderCapabilitySetTest {

    /**
     * Every capability, given in reverse: the salted order of the former {@code Set.copyOf} matched the declaration
     * order of a four-element subset about once in eight runs, so a small subset alone could pass on the old code.
     */
    @Test
    void everyCapabilityIteratesInDeclarationOrderWhenGivenInReverse() {
        List<ProviderCapability> reversed = new java.util.ArrayList<>(List.of(ProviderCapability.values()));
        java.util.Collections.reverse(reversed);

        assertEquals(List.of(ProviderCapability.values()),
                List.copyOf(ProviderCapabilitySet.copyOf(reversed).values()));
    }

    @Test
    void theSetIteratesInTheDeclarationOrderOfTheCapabilitiesWhateverTheOrderGiven() {
        ProviderCapabilitySet set = ProviderCapabilitySet.copyOf(List.of(
                ProviderCapability.INCREMENTAL_READ, ProviderCapability.READ_CHANGES,
                ProviderCapability.DISCOVER_PROJECT, ProviderCapability.READ_REQUIREMENTS));

        assertEquals(List.of(ProviderCapability.DISCOVER_PROJECT, ProviderCapability.READ_CHANGES,
                        ProviderCapability.READ_REQUIREMENTS, ProviderCapability.INCREMENTAL_READ),
                List.copyOf(set.values()));
        assertEquals("[DISCOVER_PROJECT, READ_CHANGES, READ_REQUIREMENTS, INCREMENTAL_READ]", set.values().toString());
    }

    @Test
    void theSetStaysImmutableAndEqualToAnySetOfTheSameCapabilities() {
        ProviderCapabilitySet set = ProviderCapabilitySet.of(ProviderCapability.READ_CHANGES, ProviderCapability.READ_HISTORY);

        assertThrows(UnsupportedOperationException.class, () -> set.values().add(ProviderCapability.READ_ARCHIVES));
        assertEquals(new ProviderCapabilitySet(Set.of(ProviderCapability.READ_HISTORY, ProviderCapability.READ_CHANGES)), set);
    }
}
