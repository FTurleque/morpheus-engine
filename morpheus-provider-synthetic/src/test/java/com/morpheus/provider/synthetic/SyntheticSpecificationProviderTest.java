package com.morpheus.provider.synthetic;

import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticSpecificationProviderTest {
    private final SyntheticSpecificationProvider provider = new SyntheticSpecificationProvider();

    @Test
    void recognizesSyntheticFixture() {
        var result = provider.probe(fixture("synthetic-basic"));

        assertEquals(ProviderProbeStatus.SUPPORTED, result.status());
        assertEquals(SyntheticSpecificationProvider.ID, result.providerId());
        assertEquals(SyntheticSpecificationProvider.SCHEMA, result.schema().orElseThrow());
        assertEquals("1", result.formatVersion().orElseThrow());
        assertFalse(result.remote());
        assertTrue(result.capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_REQUIREMENTS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_SCENARIOS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_CHANGES));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_CONSTRAINTS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_ACCEPTANCE_CRITERIA));
        assertTrue(result.capabilities().contains(ProviderCapability.WRITE_CHANGE));
    }

    @Test
    void reportsMissingSyntheticSourceAsUnsupported(@TempDir Path tempDir) {
        var result = provider.probe(tempDir);

        assertEquals(ProviderProbeStatus.UNSUPPORTED, result.status());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void reportsMalformedSyntheticSourceAsInvalid(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(SyntheticSpecificationProvider.SOURCE_FILE), "{not-json");

        var result = provider.probe(tempDir);

        assertEquals(ProviderProbeStatus.INVALID, result.status());
        assertEquals(DiagnosticCode.INVALID_SOURCE, result.diagnostics().getFirst().code());
    }

    @Test
    void reportsHostileDepthAsBoundedInvalidSource(@TempDir Path tempDir) throws IOException {
        String hostile = "{\"value\":"
                + "[".repeat(SyntheticJsonParser.MAX_DEPTH)
                + "0"
                + "]".repeat(SyntheticJsonParser.MAX_DEPTH)
                + "}";
        Files.writeString(tempDir.resolve(SyntheticSpecificationProvider.SOURCE_FILE), hostile);

        var result = provider.probe(tempDir);

        assertEquals(ProviderProbeStatus.INVALID, result.status());
        assertEquals(DiagnosticCode.INVALID_SOURCE, result.diagnostics().getFirst().code());
        assertTrue(result.diagnostics().getFirst().message().contains("maximum nesting depth"));
    }

    @Test
    void rejectsOversizedFileAtTheBoundedWorkspaceRead(@TempDir Path tempDir) throws IOException {
        Files.writeString(
                tempDir.resolve(SyntheticSpecificationProvider.SOURCE_FILE),
                " ".repeat(SyntheticJsonParser.MAX_INPUT_BYTES + 1));

        var result = provider.probe(tempDir);

        assertEquals(ProviderProbeStatus.INVALID, result.status());
        assertEquals(DiagnosticCode.INVALID_SOURCE, result.diagnostics().getFirst().code());
        assertTrue(result.diagnostics().getFirst().message().contains("maximum input size"));
    }

    private Path fixture(String name) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("experiments/m0/fixtures").resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate fixture " + name);
    }
}
