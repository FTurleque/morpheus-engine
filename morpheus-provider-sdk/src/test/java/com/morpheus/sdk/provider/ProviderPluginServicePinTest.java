package com.morpheus.sdk.provider;

import com.morpheus.application.operability.OperationalRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A probe without a pin is refused by name before the plugin directory is read, as an activation without one is. */
class ProviderPluginServicePinTest {
    private static final String REFUSAL = "provider plugin probe requires a trusted SHA-256 pin";

    @TempDir
    Path directory;

    @Test
    void aProbeWithoutAPinIsRefusedByName() {
        ProviderPluginService service = new ProviderPluginService(OperationalRecorder.noop());

        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> service.probe(directory, "any-plugin", directory, null));

        assertEquals(REFUSAL, refused.getMessage());
    }

    @Test
    void aProbeWithABlankPinIsRefusedByName() {
        ProviderPluginService service = new ProviderPluginService(OperationalRecorder.noop());

        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> service.probe(directory, "any-plugin", directory, "  "));

        assertEquals(REFUSAL, refused.getMessage());
    }
}
