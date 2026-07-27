package com.morpheus.application.sync;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Local-first source traversal policy with conservative ignored paths and no link following by default. */
public record SourceScanPolicy(Set<String> ignoredDirectoryNames, boolean followSymbolicLinks) {
    private static final Set<String> DEFAULT_IGNORED_DIRECTORIES = Set.of(
            ".git",
            ".hg",
            ".svn",
            ".idea",
            ".gradle",
            ".morpheus",
            "target",
            "build",
            "dist",
            "node_modules");

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
        ignoredDirectoryNames = Set.copyOf(normalized);
    }

    public static SourceScanPolicy safeDefaults() {
        return new SourceScanPolicy(DEFAULT_IGNORED_DIRECTORIES, false);
    }

    public boolean ignoresDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        Path name = directory.getFileName();
        return name != null && ignoredDirectoryNames.contains(name.toString().toLowerCase(Locale.ROOT));
    }
}
