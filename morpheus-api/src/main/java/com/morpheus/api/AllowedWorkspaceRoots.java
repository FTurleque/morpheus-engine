package com.morpheus.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical allowlist separating remote WRITE authority from the server process filesystem authority. */
public final class AllowedWorkspaceRoots {
    private final List<Path> roots;

    private AllowedWorkspaceRoots(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    public static AllowedWorkspaceRoots of(List<Path> configuredRoots) {
        Objects.requireNonNull(configuredRoots, "configuredRoots");
        if (configuredRoots.isEmpty()) {
            throw new IllegalArgumentException("remote workspace roots must contain at least one server-configured directory");
        }
        List<Path> canonical = new ArrayList<>();
        for (Path configured : configuredRoots) {
            Objects.requireNonNull(configured, "workspace root");
            Path lexical = configured.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("remote workspace root must be a real directory: " + lexical);
            }
            try {
                Path real = lexical.toRealPath();
                if (canonical.stream().noneMatch(real::equals)) canonical.add(real);
            } catch (IOException failure) {
                throw new IllegalArgumentException("cannot canonicalize remote workspace root", failure);
            }
        }
        return new AllowedWorkspaceRoots(canonical.stream().sorted().toList());
    }

    public Path requireAllowedDirectory(Path requested) {
        Objects.requireNonNull(requested, "requested");
        for (Path component : requested) {
            if (component.toString().equals("..")) {
                throw new IllegalArgumentException("workspace traversal is not allowed");
            }
        }
        Path lexical = requested.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("workspace must be an existing real directory");
        }
        try {
            Path lexicalRoot = roots.stream()
                    .filter(lexical::startsWith)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "workspace is outside the server-configured allowed roots"));
            rejectSymbolicAncestors(lexicalRoot, lexical);
            Path real = lexical.toRealPath();
            if (!real.startsWith(lexicalRoot)) {
                throw new IllegalArgumentException("workspace is outside the server-configured allowed roots");
            }
            return real;
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot canonicalize requested workspace", failure);
        }
    }

    public Path requireAllowedDirectory(String requested) {
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("workspace is required");
        }
        try {
            return requireAllowedDirectory(Path.of(requested.trim()));
        } catch (java.nio.file.InvalidPathException failure) {
            throw new IllegalArgumentException("workspace is not a valid path", failure);
        }
    }

    public List<Path> roots() {
        return roots;
    }

    private void rejectSymbolicAncestors(Path root, Path candidate) throws IOException {
        Path current = root;
        for (Path component : root.relativize(candidate)) {
            current = current.resolve(component);
            Path noFollow = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path followed = current.toRealPath();
            if (Files.isSymbolicLink(current) || !noFollow.equals(followed)) {
                throw new IllegalArgumentException("symbolic workspace path is not allowed");
            }
        }
    }
}
