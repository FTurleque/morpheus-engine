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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Minimal deterministic IPC codec for an isolated provider probe subprocess. */
final class ProviderProbeResultCodec {
    static final int MAX_RESULT_BYTES = 1024 * 1024;
    static final int MAX_DIAGNOSTICS = 1024;
    static final int MAX_DETAILS_PER_DIAGNOSTIC = 256;

    private ProviderProbeResultCodec() {
    }

    static void write(Path path, ProviderProbeResult result) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("provider.id", result.providerId().value());
        properties.setProperty("provider.version", result.providerVersion());
        properties.setProperty("status", result.status().name());
        putOptional(properties, "schema", result.schema());
        putOptional(properties, "formatVersion", result.formatVersion());
        properties.setProperty("remote", Boolean.toString(result.remote()));
        properties.setProperty("capabilities", result.capabilities().values().stream()
                .map(Enum::name)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(""));

        properties.setProperty("source.present", Boolean.toString(result.sourceLocator().isPresent()));
        result.sourceLocator().ifPresent(source -> {
            properties.setProperty("source.scheme", source.scheme());
            properties.setProperty("source.value", source.value());
        });

        properties.setProperty("diagnostics.count", Integer.toString(result.diagnostics().size()));
        for (int index = 0; index < result.diagnostics().size(); index++) {
            Diagnostic diagnostic = result.diagnostics().get(index);
            String prefix = "diagnostic." + index + ".";
            properties.setProperty(prefix + "code", diagnostic.code().name());
            properties.setProperty(prefix + "severity", diagnostic.severity().name());
            properties.setProperty(prefix + "message", diagnostic.message());
            putOptional(properties, prefix + "source", diagnostic.source());
            List<Map.Entry<String, String>> details = diagnostic.details().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            properties.setProperty(prefix + "details.count", Integer.toString(details.size()));
            for (int detailIndex = 0; detailIndex < details.size(); detailIndex++) {
                Map.Entry<String, String> detail = details.get(detailIndex);
                properties.setProperty(prefix + "detail." + detailIndex + ".key", detail.getKey());
                properties.setProperty(prefix + "detail." + detailIndex + ".value", detail.getValue());
            }
        }

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        }
    }

    static ProviderProbeResult read(Path path) throws IOException {
        byte[] payload = readBounded(path);
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(payload), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String capabilitiesValue = properties.getProperty("capabilities", "").trim();
        ProviderCapabilitySet capabilities = capabilitiesValue.isEmpty()
                ? ProviderCapabilitySet.of()
                : ProviderCapabilitySet.copyOf(Arrays.stream(capabilitiesValue.split(","))
                        .map(ProviderCapability::valueOf)
                        .toList());

        Optional<SourceLocator> source = Boolean.parseBoolean(properties.getProperty("source.present", "false"))
                ? Optional.of(new SourceLocator(
                        required(properties, "source.scheme"),
                        required(properties, "source.value")))
                : Optional.empty();

        int diagnosticCount = boundedCount(properties, "diagnostics.count", MAX_DIAGNOSTICS);
        List<Diagnostic> diagnostics = new ArrayList<>(diagnosticCount);
        for (int index = 0; index < diagnosticCount; index++) {
            String prefix = "diagnostic." + index + ".";
            int detailCount = boundedCount(
                    properties,
                    prefix + "details.count",
                    MAX_DETAILS_PER_DIAGNOSTIC);
            Map<String, String> details = new LinkedHashMap<>();
            for (int detailIndex = 0; detailIndex < detailCount; detailIndex++) {
                details.put(
                        required(properties, prefix + "detail." + detailIndex + ".key"),
                        required(properties, prefix + "detail." + detailIndex + ".value"));
            }
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.valueOf(required(properties, prefix + "code")),
                    DiagnosticSeverity.valueOf(required(properties, prefix + "severity")),
                    required(properties, prefix + "message"),
                    details,
                    optional(properties, prefix + "source")));
        }

        return new ProviderProbeResult(
                new ProviderId(required(properties, "provider.id")),
                required(properties, "provider.version"),
                ProviderProbeStatus.valueOf(required(properties, "status")),
                optional(properties, "schema"),
                optional(properties, "formatVersion"),
                source,
                capabilities,
                Boolean.parseBoolean(required(properties, "remote")),
                diagnostics);
    }

    private static byte[] readBounded(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] payload = input.readNBytes(MAX_RESULT_BYTES + 1);
            if (payload.length > MAX_RESULT_BYTES) {
                throw new IOException(
                        "isolated provider probe result exceeds " + MAX_RESULT_BYTES + " bytes");
            }
            return payload;
        }
    }

    private static int boundedCount(Properties properties, String key, int maximum) throws IOException {
        String raw = properties.getProperty(key, "0");
        final int count;
        try {
            count = Integer.parseInt(raw);
        } catch (NumberFormatException malformed) {
            throw new IOException("isolated provider probe result has invalid count for " + key, malformed);
        }
        if (count < 0 || count > maximum) {
            throw new IOException(
                    "isolated provider probe result count for " + key + " must be between 0 and " + maximum);
        }
        return count;
    }

    private static void putOptional(Properties properties, String key, Optional<String> value) {
        properties.setProperty(key + ".present", Boolean.toString(value.isPresent()));
        value.ifPresent(item -> properties.setProperty(key + ".value", item));
    }

    private static Optional<String> optional(Properties properties, String key) {
        return Boolean.parseBoolean(properties.getProperty(key + ".present", "false"))
                ? Optional.of(required(properties, key + ".value"))
                : Optional.empty();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("isolated provider probe result is missing property: " + key);
        }
        return value;
    }
}
