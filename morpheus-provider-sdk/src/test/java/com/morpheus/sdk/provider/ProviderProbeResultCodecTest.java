package com.morpheus.sdk.provider;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderProbeResultCodecTest {
    @TempDir
    Path directory;

    @Test
    void preservesCompleteProbeResultAcrossProcessBoundary() throws Exception {
        ProviderProbeResult expected = new ProviderProbeResult(
                new ProviderId("isolated-provider"),
                "2.4.1",
                ProviderProbeStatus.SUPPORTED,
                Optional.of("openspec"),
                Optional.of("1.0"),
                Optional.of(SourceLocator.file("openspec/spec.md")),
                ProviderCapabilitySet.of(ProviderCapability.values()),
                false,
                List.of(new Diagnostic(
                        DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                        DiagnosticSeverity.WARNING,
                        "optional capability unavailable",
                        Map.of("capability", "example", "reason", "fixture"),
                        Optional.of("openspec/spec.md"))));

        Path result = directory.resolve("probe.properties");
        ProviderProbeResultCodec.write(result, expected);

        assertEquals(expected, ProviderProbeResultCodec.read(result));
    }
}
