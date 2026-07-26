package com.morpheus.architecture.m19;

import com.morpheus.domain.identity.DomainIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

/** Deterministic M19 fixture generator shared by contract and performance gates. */
final class M19LargeFixtureSupport {
    static final long SEED = 1901L;
    static final int GATE_SOURCE_FILES = 5_000;
    static final int INCREMENTAL_CHANGED_FILES = 50;
    static final int GATE_REQUIREMENTS = 10_000;
    static final int GATE_TRACEABILITY_LINKS = 25_000;
    static final int GATE_RETAINED_SNAPSHOTS = 5;

    private static final long UUID_V7_TIMESTAMP_MILLIS = 1_784_900_000_000L;

    private M19LargeFixtureSupport() {
    }

    static SourceFixture generateSourceFixture(Path root, int fileCount, long seed) throws IOException {
        if (fileCount <= 0) {
            throw new IllegalArgumentException("fileCount must be greater than zero");
        }
        Files.createDirectories(root);
        SplittableRandom random = new SplittableRandom(seed);
        for (int index = 0; index < fileCount; index++) {
            Path directory = root.resolve("group-%03d".formatted(index % 100));
            Files.createDirectories(directory);
            Path file = directory.resolve("requirement-%05d.md".formatted(index));
            String marker = Long.toUnsignedString(random.nextLong(), 16);
            String body = deterministicBody(index, marker);
            Files.writeString(file, body, StandardCharsets.UTF_8);
        }
        return manifest(root);
    }

    static void mutateDeterministically(Path root, int changedFiles, long seed) throws IOException {
        if (changedFiles < 0) {
            throw new IllegalArgumentException("changedFiles must not be negative");
        }
        SplittableRandom random = new SplittableRandom(seed ^ 0x5A17E5D4L);
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        if (changedFiles > files.size()) {
            throw new IllegalArgumentException("changedFiles exceeds fixture size");
        }
        for (int index = 0; index < changedFiles; index++) {
            Path file = files.get(index);
            Files.writeString(
                    file,
                    Files.readString(file, StandardCharsets.UTF_8)
                            + "\nmutation=" + Long.toUnsignedString(random.nextLong(), 16) + "\n",
                    StandardCharsets.UTF_8);
        }
    }

    static SourceFixture manifest(Path root) throws IOException {
        MessageDigest digest = sha256();
        long bytes = 0L;
        int count = 0;
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            byte[] content = Files.readAllBytes(file);
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(content);
            digest.update((byte) '\n');
            bytes += content.length;
            count++;
        }
        return new SourceFixture(count, bytes, HexFormat.of().formatHex(digest.digest()));
    }

    static DomainIdentity deterministicIdentity(long namespace, long ordinal) {
        long timestamp = (UUID_V7_TIMESTAMP_MILLIS + Math.floorMod(namespace, 10_000L)) & 0x0000FFFFFFFFFFFFL;
        long randomA = Math.floorMod(namespace * 31L + ordinal, 1L << 12);
        long mostSignificantBits = (timestamp << 16) | (0x7L << 12) | randomA;
        long randomB = mix64(namespace ^ (ordinal * 0x9E3779B97F4A7C15L)) & 0x3FFFFFFFFFFFFFFFL;
        long leastSignificantBits = 0x8000000000000000L | randomB;
        return new DomainIdentity(new UUID(mostSignificantBits, leastSignificantBits));
    }

    static long percentile95Nanos(List<Long> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("samples must not be empty");
        }
        List<Long> sorted = samples.stream().sorted().toList();
        int rank = (int) Math.ceil(sorted.size() * 0.95d);
        return sorted.get(Math.max(0, rank - 1));
    }

    private static String deterministicBody(int index, String marker) {
        StringBuilder text = new StringBuilder(2_200);
        text.append("# Requirement %05d\n\n".formatted(index));
        text.append("key=REQ-%05d\n".formatted(index));
        text.append("marker=").append(marker).append('\n');
        text.append("statement=The deterministic M19 fixture shall preserve observable specification semantics.\n");
        while (text.length() < 2_100) {
            text.append("payload-%05d-%s\n".formatted(index, marker));
        }
        return text.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    record SourceFixture(int fileCount, long totalBytes, String sha256) {
        SourceFixture {
            if (fileCount < 0 || totalBytes < 0L) {
                throw new IllegalArgumentException("fixture counts must not be negative");
            }
            if (sha256 == null || sha256.length() != 64) {
                throw new IllegalArgumentException("sha256 must be a lowercase hexadecimal SHA-256 value");
            }
        }
    }
}
