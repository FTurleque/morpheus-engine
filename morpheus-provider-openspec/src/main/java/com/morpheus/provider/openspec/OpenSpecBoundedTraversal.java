package com.morpheus.provider.openspec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Bounded, no-follow workspace traversal shared by OpenSpec readers. */
final class OpenSpecBoundedTraversal {
    static final int MAX_DEPTH = 64;
    static final int MAX_VISITED_ENTRIES = 8_192;

    private OpenSpecBoundedTraversal() {
    }

    static Stream<Path> walk(Path root) throws IOException {
        return walk(root, MAX_DEPTH, MAX_VISITED_ENTRIES);
    }

    static Stream<Path> walk(Path root, int maxDepth, int maxVisitedEntries) throws IOException {
        Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxVisitedEntries < 1) {
            throw new IllegalArgumentException("maxVisitedEntries must be positive");
        }
        if (Files.isSymbolicLink(normalizedRoot)) {
            throw new IllegalArgumentException("OpenSpec traversal root must not be a symbolic link: " + normalizedRoot);
        }

        List<Path> bounded = new ArrayList<>();
        int visited = 0;
        try (Stream<Path> paths = Files.walk(normalizedRoot, maxDepth + 1)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                visited++;
                if (visited > maxVisitedEntries) {
                    throw new IllegalStateException(
                            "OpenSpec traversal exceeds visited-entry budget " + maxVisitedEntries + ": " + normalizedRoot);
                }
                int depth = path.equals(normalizedRoot) ? 0 : normalizedRoot.relativize(path).getNameCount();
                if (depth > maxDepth) {
                    throw new IllegalStateException(
                            "OpenSpec traversal exceeds maximum depth " + maxDepth + ": " + path);
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("OpenSpec traversal refuses symbolic link: " + path);
                }
                bounded.add(path);
            }
        }
        return List.copyOf(bounded).stream();
    }
}
