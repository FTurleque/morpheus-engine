package com.morpheus.application.read;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Shared fail-closed work budget for provider-owned specification ingestion. */
public record ProviderIngestionBudget(
        long maxDocumentBytes,
        int maxFiles,
        long maxAggregateBytes,
        int maxLines,
        int maxEntities,
        int maxEvidenceBytes) {

    public static final ProviderIngestionBudget DEFAULT = new ProviderIngestionBudget(
            2L * 1024 * 1024,
            2_000,
            32L * 1024 * 1024,
            100_000,
            100_000,
            512 * 1024);

    public ProviderIngestionBudget {
        requirePositive(maxDocumentBytes, "maxDocumentBytes");
        requirePositive(maxFiles, "maxFiles");
        requirePositive(maxAggregateBytes, "maxAggregateBytes");
        requirePositive(maxLines, "maxLines");
        requirePositive(maxEntities, "maxEntities");
        requirePositive(maxEvidenceBytes, "maxEvidenceBytes");
        if (maxDocumentBytes > maxAggregateBytes) {
            throw new IllegalArgumentException("maxDocumentBytes must not exceed maxAggregateBytes");
        }
    }

    public void requireDocumentBytes(long bytes, String source) {
        requireWithin(bytes, maxDocumentBytes, "document bytes", source);
    }

    public void requireFiles(long files, String source) {
        requireWithin(files, maxFiles, "file count", source);
    }

    public void requireAggregateBytes(long bytes, String source) {
        requireWithin(bytes, maxAggregateBytes, "aggregate bytes", source);
    }

    public void requireLines(long lines, String source) {
        requireWithin(lines, maxLines, "line count", source);
    }

    public void requireEntities(long entities, String source) {
        requireWithin(entities, maxEntities, "entity count", source);
    }

    public void requireEvidenceBytes(long bytes, String source) {
        requireWithin(bytes, maxEvidenceBytes, "evidence bytes", source);
    }

    public void requireUtf8Document(String text, String source) {
        Objects.requireNonNull(text, "text");
        requireDocumentBytes(text.getBytes(StandardCharsets.UTF_8).length, source);
        requireLines(text.lines().count(), source);
    }

    private static void requireWithin(long value, long maximum, String metric, String source) {
        if (value < 0) throw new IllegalArgumentException(metric + " must not be negative");
        if (value > maximum) {
            throw new IllegalArgumentException(
                    "provider ingestion " + metric + " exceeds budget for " + safeSource(source)
                            + ": " + value + " > " + maximum);
        }
    }

    private static String safeSource(String source) {
        return source == null || source.isBlank() ? "<unknown>" : source.trim();
    }

    private static void requirePositive(long value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be >= 1");
    }
}
