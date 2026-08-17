package com.morpheus.application.read;

import com.morpheus.application.files.SafeWorkspaceFileResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Shared fail-closed work budget for provider-owned specification ingestion. */
public record ProviderIngestionBudget(
        long maxDocumentBytes,
        int maxFiles,
        long maxAggregateBytes,
        int maxLines,
        int maxBlocks,
        int maxEntities,
        int maxEvidenceBytes) {

    public static final ProviderIngestionBudget DEFAULT = new ProviderIngestionBudget(
            1L * 1024 * 1024,
            2_000,
            32L * 1024 * 1024,
            100_000,
            100_000,
            100_000,
            512 * 1024);

    public ProviderIngestionBudget {
        requirePositive(maxDocumentBytes, "maxDocumentBytes");
        requirePositive(maxFiles, "maxFiles");
        requirePositive(maxAggregateBytes, "maxAggregateBytes");
        requirePositive(maxLines, "maxLines");
        requirePositive(maxBlocks, "maxBlocks");
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

    public void requireBlocks(long blocks, String source) {
        requireWithin(blocks, maxBlocks, "block count", source);
    }

    public void requireEvidenceBytes(long bytes, String source) {
        requireWithin(bytes, maxEvidenceBytes, "evidence bytes", source);
    }

    public void requireUtf8Document(String text, String source) {
        Objects.requireNonNull(text, "text");
        requireDocumentBytes(utf8Bytes(text), source);
        requireLines(text.lines().count(), source);
    }

    public Session open(SafeWorkspaceFileResolver files) {
        return new Session(this, files);
    }

    /** One ingestion attempt. Counters advance only after every check for the current item succeeds. */
    public static final class Session {
        private final ProviderIngestionBudget budget;
        private final SafeWorkspaceFileResolver files;
        private long fileCount;
        private long aggregateBytes;
        private long lineCount;
        private long blockCount;
        private long entityCount;
        private long evidenceBytes;

        private Session(ProviderIngestionBudget budget, SafeWorkspaceFileResolver files) {
            this.budget = Objects.requireNonNull(budget, "budget");
            this.files = Objects.requireNonNull(files, "files");
        }

        public String readDocument(Path relativePath) throws IOException {
            return read(relativePath, budget.maxDocumentBytes, false);
        }

        public String readEvidence(Path relativePath) throws IOException {
            return read(relativePath, Math.min(budget.maxDocumentBytes, budget.maxEvidenceBytes), true);
        }

        private String read(Path relativePath, long itemMaximum, boolean evidence) throws IOException {
            String source = relativePath.toString();
            budget.requireFiles(Math.addExact(fileCount, 1), source);
            long aggregateRemaining = budget.maxAggregateBytes - aggregateBytes;
            if (aggregateRemaining < 1) {
                throw exceeded("aggregate bytes", source, Math.addExact(aggregateBytes, 1), budget.maxAggregateBytes);
            }
            long evidenceRemaining = evidence ? budget.maxEvidenceBytes - evidenceBytes : Long.MAX_VALUE;
            if (evidence && evidenceRemaining < 1) {
                throw exceeded("evidence bytes", source, Math.addExact(evidenceBytes, 1), budget.maxEvidenceBytes);
            }
            long effectiveItemMaximum = Math.min(itemMaximum, evidenceRemaining);
            int readMaximum = (int) Math.min(Integer.MAX_VALUE, Math.min(effectiveItemMaximum, aggregateRemaining));
            String text;
            try {
                text = files.readUtf8(relativePath, readMaximum);
            } catch (IllegalArgumentException failure) {
                if (failure.getMessage() != null
                        && failure.getMessage().contains("exceeds maximum input size")) {
                    if (aggregateRemaining <= effectiveItemMaximum) {
                        throw exceeded("aggregate bytes", source, budget.maxAggregateBytes + 1, budget.maxAggregateBytes);
                    }
                    if (evidence && evidenceRemaining <= itemMaximum) {
                        throw exceeded("evidence bytes", source, budget.maxEvidenceBytes + 1, budget.maxEvidenceBytes);
                    }
                    String metric = evidence && itemMaximum == budget.maxEvidenceBytes
                            ? "evidence bytes"
                            : "document bytes";
                    long maximum = metric.equals("evidence bytes") ? budget.maxEvidenceBytes : budget.maxDocumentBytes;
                    throw exceeded(metric, source, maximum + 1, maximum);
                }
                throw failure;
            }
            long bytes = utf8Bytes(text);
            long lines = text.lines().count();
            budget.requireDocumentBytes(bytes, source);
            budget.requireAggregateBytes(Math.addExact(aggregateBytes, bytes), source);
            budget.requireLines(Math.addExact(lineCount, lines), source);
            if (evidence) {
                budget.requireEvidenceBytes(Math.addExact(evidenceBytes, bytes), source);
            }
            fileCount++;
            aggregateBytes += bytes;
            lineCount += lines;
            if (evidence) evidenceBytes += bytes;
            return text;
        }

        public void addBlocks(long count, String source) {
            if (count < 0) throw new IllegalArgumentException("block count must not be negative");
            budget.requireBlocks(Math.addExact(blockCount, count), source);
            blockCount += count;
        }

        public void addEntities(long count, String source) {
            if (count < 0) throw new IllegalArgumentException("entity count must not be negative");
            budget.requireEntities(Math.addExact(entityCount, count), source);
            entityCount += count;
        }

        public void addEvidenceFragment(String fragment, String source) {
            long bytes = utf8Bytes(Objects.requireNonNull(fragment, "fragment"));
            long nextEvidenceBytes = Math.addExact(evidenceBytes, bytes);
            budget.requireEvidenceBytes(nextEvidenceBytes, source);
            evidenceBytes = nextEvidenceBytes;
        }

        /** Checks a discovered batch before it is fully collected; does not reserve or mutate the session. */
        public void requireAdditionalFiles(long count, String source) {
            if (count < 0) throw new IllegalArgumentException("file count must not be negative");
            budget.requireFiles(Math.addExact(fileCount, count), source);
        }

        public long remainingFiles() {
            return budget.maxFiles - fileCount;
        }

        public long fileCount() { return fileCount; }
        public long aggregateBytes() { return aggregateBytes; }
        public long lineCount() { return lineCount; }
        public long blockCount() { return blockCount; }
        public long entityCount() { return entityCount; }
        public long evidenceBytes() { return evidenceBytes; }
    }

    private static void requireWithin(long value, long maximum, String metric, String source) {
        if (value < 0) throw new IllegalArgumentException(metric + " must not be negative");
        if (value > maximum) {
            throw exceeded(metric, source, value, maximum);
        }
    }

    private static ProviderIngestionLimitException exceeded(
            String metric, String source, long value, long maximum) {
        return new ProviderIngestionLimitException(
                "provider ingestion " + metric + " exceeds budget for " + safeSource(source)
                        + ": " + value + " > " + maximum);
    }

    static long utf8Bytes(String value) {
        long bytes = 0;
        for (int offset = 0; offset < value.length(); offset++) {
            char current = value.charAt(offset);
            if (current <= 0x7f) bytes++;
            else if (current <= 0x7ff) bytes += 2;
            else if (Character.isHighSurrogate(current)
                    && offset + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(offset + 1))) {
                bytes += 4;
                offset++;
            } else if (Character.isSurrogate(current)) bytes++;
            else bytes += 3;
        }
        return bytes;
    }

    private static String safeSource(String source) {
        return source == null || source.isBlank() ? "<unknown>" : source.trim();
    }

    private static void requirePositive(long value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be >= 1");
    }
}
