package com.morpheus.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical allowlist separating remote WRITE authority from the server process filesystem authority. */
public final class AllowedWorkspaceRoots {
    private final List<AllowedRoot> roots;

    private AllowedWorkspaceRoots(List<AllowedRoot> roots) {
        this.roots = List.copyOf(roots);
    }

    public static AllowedWorkspaceRoots of(List<Path> configuredRoots) {
        Objects.requireNonNull(configuredRoots, "configuredRoots");
        if (configuredRoots.isEmpty()) {
            throw new IllegalArgumentException("remote workspace roots must contain at least one server-configured directory");
        }
        List<AllowedRoot> canonical = new ArrayList<>();
        for (Path configured : configuredRoots) {
            Objects.requireNonNull(configured, "workspace root");
            Path lexical = configured.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("remote workspace root must be a real directory: " + lexical);
            }
            try {
                Path real = lexical.toRealPath();
                if (canonical.stream().noneMatch(root -> root.real().equals(real))) {
                    canonical.add(AllowedRoot.capture(lexical, real));
                }
            } catch (IOException failure) {
                throw new IllegalArgumentException("cannot canonicalize remote workspace root", failure);
            }
        }
        return new AllowedWorkspaceRoots(canonical.stream()
                .sorted(Comparator.comparing(root -> root.real().toString()))
                .toList());
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
            Path real = lexical.toRealPath();
            AllowedRoot allowedRoot = roots.stream()
                    .filter(root -> lexical.startsWith(root.lexical()) || lexical.startsWith(root.real()))
                    .filter(root -> real.startsWith(root.real()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "workspace is outside the server-configured allowed roots"));
            allowedRoot.requireUnchanged();
            Path lexicalRoot = lexical.startsWith(allowedRoot.lexical())
                    ? allowedRoot.lexical()
                    : allowedRoot.real();
            rejectSymbolicAncestors(lexicalRoot, lexical);
            allowedRoot.requireUnchanged();
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
        return roots.stream().map(AllowedRoot::real).toList();
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

    private record AllowedRoot(Path lexical, Path real, RootIdentity identity) {
        private static AllowedRoot capture(Path lexical, Path real) throws IOException {
            return new AllowedRoot(lexical, real, RootIdentity.capture(real));
        }

        /**
         * The message names the condition, not the root.
         *
         * <p>This check runs while serving a request, and the remote facade renders an IllegalArgumentException
         * as a 400 carrying its message. Naming the root would publish a server-configured absolute pathname to a
         * caller who cannot select it; the operator resolving this reads the server's own configuration, where
         * the set of roots is already listed.</p>
         */
        private void requireUnchanged() throws IOException {
            if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(real)
                    || !identity.matches(RootIdentity.capture(real))) {
                throw new IllegalArgumentException("a server-configured workspace root changed after startup");
            }
        }
    }

    private record RootIdentity(Object fileKey, String owner, FileTime creationTime) {
        private static RootIdentity capture(Path directory) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(
                    directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return new RootIdentity(attributes.fileKey(), owner(directory), attributes.creationTime());
        }

        private boolean matches(RootIdentity current) {
            if (fileKey != null || current.fileKey != null) {
                return Objects.equals(fileKey, current.fileKey) && Objects.equals(owner, current.owner);
            }
            return Objects.equals(owner, current.owner) && Objects.equals(creationTime, current.creationTime);
        }

        private static String owner(Path directory) throws IOException {
            try {
                UserPrincipal principal = Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS);
                return principal == null ? null : principal.getName();
            } catch (UnsupportedOperationException unsupported) {
                return null;
            }
        }
    }
}
