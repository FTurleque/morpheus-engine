package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginCompatibilityTest {
    private final ProviderPluginCompatibility compatibility = new ProviderPluginCompatibility();

    @Test
    void acceptsRuntimeInsideDeclaredRange() {
        var metadata = metadata("1.0.0", Optional.of("1.4.0"));

        assertTrue(compatibility.evaluate(metadata, "1.2.3").compatible());
    }

    @Test
    void rejectsRuntimeOlderThanMinimum() {
        var result = compatibility.evaluate(metadata("1.2.0", Optional.empty()), "1.1.9");

        assertFalse(result.compatible());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("MORPHEUS_VERSION_TOO_OLD")));
    }

    @Test
    void rejectsRuntimeNewerThanMaximum() {
        var result = compatibility.evaluate(metadata("1.0.0", Optional.of("1.1.0")), "1.1.1");

        assertFalse(result.compatible());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("MORPHEUS_VERSION_TOO_NEW")));
    }

    @Test
    void releaseBeatsItsPrereleaseAtSameNumericVersion() {
        assertTrue(compatibility.evaluate(metadata("1.0.0-rc.1", Optional.of("1.0.0")), "1.0.0").compatible());
    }

    private static ProviderPluginMetadata metadata(String minimum, Optional<String> maximum) {
        return new ProviderPluginMetadata(
                "compatibility-test",
                new ProviderId("compatibility-provider"),
                "1.0.0",
                ProviderSdk.API_VERSION,
                minimum,
                maximum);
    }
}
