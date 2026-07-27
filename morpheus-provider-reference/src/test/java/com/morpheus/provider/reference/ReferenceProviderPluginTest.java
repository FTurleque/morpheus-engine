package com.morpheus.provider.reference;

import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.sdk.provider.ProviderPluginMetadata;
import com.morpheus.sdk.provider.ProviderSdk;
import com.morpheus.sdk.provider.testkit.ProviderPluginContractAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceProviderPluginTest {
    @TempDir
    Path workspace;

    @Test
    void referencePluginPassesReusableProviderContract() throws Exception {
        Files.writeString(workspace.resolve(ReferenceSpecificationProvider.MARKER_FILE), "reference\n");
        var snapshot = ProviderPluginContractAssertions.verify(new ReferenceProviderPlugin(), workspace);

        assertTrue(snapshot.supportedProbe().supported());
        assertTrue(snapshot.supportedProbe().capabilities().contains(ProviderCapability.DISCOVER_PROJECT));
    }

    @Test
    void declarativeMetadataMatchesRuntimeMetadata() throws Exception {
        Properties properties = new Properties();
        try (var input = ReferenceProviderPlugin.class.getClassLoader().getResourceAsStream(ProviderSdk.METADATA_PATH)) {
            if (input == null) {
                throw new AssertionError("missing " + ProviderSdk.METADATA_PATH);
            }
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        ProviderPluginMetadata manifest = ProviderPluginMetadata.from(properties);
        assertEquals(ReferenceProviderPlugin.METADATA, manifest);
    }
}
