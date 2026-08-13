package com.morpheus.application.sync;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Local-first source traversal policy with conservative ignored paths and bounded filesystem work. */
public record SourceScanPolicy(
        Set<String> ignoredDirectoryNames,
        boolean followSymbolicLinks,
        int maxDepth,
        int maxDirectories,
        int maxFiles,
        long maxFileBytes,
        long maxAggregateBytes) {
    static final int DEFAULT_MAX_DEPTH = 128;
    static final int DEFAULT_MAX_DIRECTORIES = 50_000;
    static final int DEFAULT_MAX_FILES = 50_000;
    static final long DEFAULT_MAX_FILE_BYTES = 64L * 1024 * 1024;
    static final long DEFAULT_MAX_AGGREGATE_BYTES = 2L * 1024 * 1024 * 1024;

    private static final Set<String> DEFAULT_IGNORED_DIRECTORIES = Set.of(
            ".git", ".hg", ".svn", ".idea", ".gradle", ".morpheus",
            "target", "build", "dist", "node_modules");

    public SourceScanPolicy {
        Objects.requireNonNull(ignoredDirectoryNames, "ignoredDirectoryNames");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String name : ignoredDirectoryNames) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("ignored directory names must not be blank");
            }
            String candidate = name.trim().toLowerCase(Locale.ROOT);
            if (candidate.indexOf('/') >= 0 || candidate.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("ignored directory name must be a single path segment: " + name);
            }
            normalized.add(candidate);
        }
        if (followSymbolicLinks) {
            throw new IllegalArgumentException("symbolic-link traversal is not supported by the source scan policy");
        }
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be >= 1");
        if (maxDirectories < 1) throw new IllegalArgumentException("maxDirectories must be >= 1");
        if (maxFiles < 1) throw new IllegalArgumentException("maxFiles must be >= 1");
        if (maxFileBytes < 1) throw new IllegalArgumentException("maxFileBytes must be >= 1");
        if (maxAggregateBytes < 1) throw new IllegalArgumentException("maxAggregateBytes must be >= 1");
        if (maxFileBytes > maxAggregateBytes) {
            throw new IllegalArgumentException("maxFileBytes must not exceed maxAggregateBytes");
        }
        ignoredDirectoryNames = Set.copyOf(normalized);
    }

    public SourceScanPolicy(
            Set<String> ignoredDirectoryNames,
            boolean followSymbolicLinks,
            int maxDepth,
            int maxFiles,
            long maxFileBytes,
            long maxAggregateBytes) {
        this(ignoredDirectoryNames, followSymbolicLinks, maxDepth, DEFAULT_MAX_DIRECTORIES,
                maxFiles, maxFileBytes, maxAggregateBytes);
    }

    /** Compatibility constructor. Symbolic-link traversal remains deliberately denied. */
    public SourceScanPolicy(Set<String> ignoredDirectoryNames, boolean followSymbolicLinks) {
        this(ignoredDirectoryNames, followSymbolicLinks, DEFAULT_MAX_DEPTH, DEFAULT_MAX_DIRECTORIES,
                DEFAULT_MAX_FILES, DEFAULT_MAX_FILE_BYTES, DEFAULT_MAX_AGGREGATE_BYTES);
    }

    public static SourceScanPolicy safeDefaults() {
        return new SourceScanPolicy(DEFAULT_IGNORED_DIRECTORIES, false, DEFAULT_MAX_DEPTH,
                DEFAULT_MAX_DIRECTORIES, DEFAULT_MAX_FILES, DEFAULT_MAX_FILE_BYTES, DEFAULT_MAX_AGGREGATE_BYTES);
    }

    public boolean ignoresDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        Path name = directory.getFileName();
        return name != null && ignoredDirectoryNames.contains(name.toString().toLowerCase(Locale.ROOT));
    }
}
