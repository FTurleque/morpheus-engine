package com.morpheus.provider.reference;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceProviderPluginTest {
    @TempDir
    Path workspace;

    @Test
    void referencePluginPassesReusableProviderContractAndReadsNormalizedContent() throws Exception {
        Files.writeString(workspace.resolve(ReferenceSpecificationProvider.MARKER_FILE), "reference\n");
        var snapshot = ProviderPluginContractAssertions.verify(new ReferenceProviderPlugin(), workspace);

        assertTrue(snapshot.supportedProbe().supported());
        assertTrue(snapshot.supportedProbe().capabilities().contains(ProviderCapability.DISCOVER_PROJECT));
        assertTrue(snapshot.supportedProbe().capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS));

        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        EntityIdentityResolver identities = stableResolver();
        var result = snapshot.contentReader().read(
                new ProviderReadRequest(workspace, projectId, java.util.Set.of(ReadCategory.CURRENT_SPECIFICATIONS)),
                identities);

        assertEquals(ReferenceSpecificationProvider.ID, result.providerId());
        assertEquals(1, result.content().orElseThrow().specifications().size());
        assertEquals("reference-current", result.content().orElseThrow().specifications().getFirst().key());
        assertEquals(
                ReadCategoryStatus.READ,
                result.report(ReadCategory.CURRENT_SPECIFICATIONS).orElseThrow().status());
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

    private static EntityIdentityResolver stableResolver() {
        var identities = new ConcurrentHashMap<String, DomainIdentity>();
        return (providerId, entityType, externalId) -> identities.computeIfAbsent(
                providerId.value() + ":" + entityType + ":" + externalId,
                ignored -> DomainIdentity.generate());
    }
}
