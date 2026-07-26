package com.morpheus.application.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Applies owner-only permissions when the host filesystem exposes POSIX or ACL controls. */
public final class LocalWritePermissionHardener {
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString("rw-------");

    public Result hardenDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        try {
            List<Path> created = createMissingDirectories(directory);
            if (created.isEmpty()) {
                return Result.PREEXISTING_PRESERVED;
            }
            boolean hardened = true;
            for (Path path : created) {
                hardened &= harden(path, true);
            }
            return hardened ? Result.HARDENED : Result.UNSUPPORTED;
        } catch (IOException exception) {
            throw new LocalWritePermissionException("Cannot harden local directory permissions", exception);
        }
    }

    public Result hardenFile(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("file must exist before hardening");
        }
        try {
            rejectSymbolicLink(file);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new LocalWritePermissionException("Refusing to harden a non-regular file");
            }
            return harden(file, false) ? Result.HARDENED : Result.UNSUPPORTED;
        } catch (IOException exception) {
            throw new LocalWritePermissionException("Cannot harden local file permissions", exception);
        }
    }

    private List<Path> createMissingDirectories(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        List<Path> missing = new ArrayList<>();
        Path current = normalized;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            missing.add(current);
            current = current.getParent();
        }
        if (current != null) {
            rejectSymbolicLink(current);
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new LocalWritePermissionException("Directory parent is not a regular directory");
            }
        }

        Collections.reverse(missing);
        List<Path> created = new ArrayList<>(missing.size());
        for (Path path : missing) {
            try {
                Files.createDirectory(path);
                created.add(path);
            } catch (FileAlreadyExistsException concurrentCreation) {
                rejectSymbolicLink(path);
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new LocalWritePermissionException("Directory path was concurrently replaced");
                }
            }
        }
        return List.copyOf(created);
    }

    private boolean harden(Path path, boolean directory) throws IOException {
        if (supportsPosix(path)) {
            Files.setPosixFilePermissions(path, directory ? OWNER_DIRECTORY : OWNER_FILE);
            return true;
        }
        return hardenAcl(path, directory);
    }

    private boolean supportsPosix(Path path) {
        return Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS) != null;
    }

    private boolean hardenAcl(Path path, boolean directory) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            return false;
        }
        UserPrincipal owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
        EnumSet<AclEntryPermission> permissions = EnumSet.allOf(AclEntryPermission.class);
        if (!directory) {
            permissions.remove(AclEntryPermission.DELETE_CHILD);
        }
        AclEntry ownerOnly = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(permissions)
                .build();
        view.setAcl(List.of(ownerOnly));
        return true;
    }

    private void rejectSymbolicLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new LocalWritePermissionException("Refusing to harden a symbolic-link path");
        }
    }

    public enum Result {
        HARDENED,
        PREEXISTING_PRESERVED,
        UNSUPPORTED
    }

    public static final class LocalWritePermissionException extends RuntimeException {
        public LocalWritePermissionException(String message) {
            super(message);
        }

        public LocalWritePermissionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
