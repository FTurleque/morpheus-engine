package com.morpheus.application.security;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Applies and verifies owner-controlled permissions for local sensitive storage. */
public final class LocalWritePermissionHardener {
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString("rw-------");
    private static final int UNIX_STICKY_BIT = 01000;
    private static final Set<AclEntryPermission> TARGET_DIRECTORY_MUTATION_PERMISSIONS = EnumSet.of(
            AclEntryPermission.WRITE_DATA,
            AclEntryPermission.APPEND_DATA,
            AclEntryPermission.WRITE_NAMED_ATTRS,
            AclEntryPermission.WRITE_ATTRIBUTES,
            AclEntryPermission.DELETE,
            AclEntryPermission.DELETE_CHILD,
            AclEntryPermission.WRITE_ACL,
            AclEntryPermission.WRITE_OWNER);
    private static final Set<AclEntryPermission> ANCESTOR_REPLACEMENT_PERMISSIONS = EnumSet.of(
            AclEntryPermission.DELETE,
            AclEntryPermission.DELETE_CHILD,
            AclEntryPermission.WRITE_ACL,
            AclEntryPermission.WRITE_OWNER);

    private final DirectoryCreationObserver creationObserver;
    private final Set<UserPrincipal> trustedAclPrincipals;
    private final UserPrincipal runtimePrincipal;

    public LocalWritePermissionHardener() {
        this(DirectoryCreationObserver.NONE);
    }

    LocalWritePermissionHardener(DirectoryCreationObserver creationObserver) {
        this(
                creationObserver,
                resolveTrustedWindowsPrincipals(),
                resolveCurrentRuntimePrincipal());
    }

    private LocalWritePermissionHardener(
            DirectoryCreationObserver creationObserver,
            Set<UserPrincipal> trustedAclPrincipals,
            UserPrincipal runtimePrincipal) {
        this.creationObserver = Objects.requireNonNull(creationObserver, "creationObserver");
        this.trustedAclPrincipals = Set.copyOf(Objects.requireNonNull(trustedAclPrincipals, "trustedAclPrincipals"));
        this.runtimePrincipal = runtimePrincipal;
    }

    public Result hardenDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        Path normalized = directory.toAbsolutePath().normalize();
        try {
            List<Path> created = createMissingDirectories(normalized);
            if (created.isEmpty()) {
                requireWriteProtectedDirectory(normalized);
                return Result.PREEXISTING_PRESERVED;
            }
            for (Path path : created) {
                requireHardened(path, true);
            }
            requireWriteProtectedDirectory(normalized);
            return Result.HARDENED;
        } catch (IOException exception) {
            throw new LocalWritePermissionException("Cannot harden local directory permissions", exception);
        }
    }

    public Result hardenFile(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("file must exist before hardening");
        }
        Path normalized = file.toAbsolutePath().normalize();
        try {
            rejectSymbolicLink(normalized);
            if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new LocalWritePermissionException("Refusing to harden a non-regular file");
            }
            requireHardened(normalized, false);
            Path parent = normalized.getParent();
            if (parent != null) {
                // A POSIX sticky parent (for example /tmp) protects an owner-controlled file entry from
                // cross-user replacement. This exception is deliberately file-only: a sensitive directory itself
                // still cannot rely on sticky semantics because arbitrary entries could be created inside it.
                requireWriteProtectedDirectory(parent, true);
            }
            return Result.HARDENED;
        } catch (IOException exception) {
            throw new LocalWritePermissionException("Cannot harden local file permissions", exception);
        }
    }

    /**
     * Verifies the sensitive directory and every ancestor that could replace an entry in its pathname.
     * POSIX writable ancestors are accepted only when the sticky bit prevents cross-owner replacement.
     * ACL-backed filesystems are inspected fail-closed for non-trusted principals with mutation rights.
     */
    public Result requireWriteProtectedDirectory(Path directory) {
        return requireWriteProtectedDirectory(directory, false);
    }

    private Result requireWriteProtectedDirectory(Path directory, boolean allowStickyDirectParent) {
        Objects.requireNonNull(directory, "directory");
        Path normalized = directory.toAbsolutePath().normalize();
        try {
            rejectSymbolicLink(normalized);
            if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new LocalWritePermissionException("Sensitive parent must be a regular directory");
            }
            UserPrincipal sensitiveOwner = Files.getOwner(normalized, LinkOption.NOFOLLOW_LINKS);
            Path current = normalized;
            boolean sensitiveDirectory = true;
            while (current != null) {
                rejectSymbolicLink(current);
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new LocalWritePermissionException("Sensitive path ancestor must be a regular directory: " + current);
                }
                if (supportsPosix(current)) {
                    requireProtectedPosixDirectory(
                            current,
                            sensitiveDirectory,
                            sensitiveOwner,
                            allowStickyDirectParent && sensitiveDirectory);
                } else {
                    AclFileAttributeView acl = Files.getFileAttributeView(
                            current, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                    if (acl == null) {
                        throw new LocalWritePermissionException(
                                "Sensitive path permissions cannot be verified on this filesystem: " + current);
                    }
                    requireProtectedAclDirectory(current, acl, sensitiveDirectory, sensitiveOwner);
                }
                sensitiveDirectory = false;
                current = current.getParent();
            }
            return Result.WRITE_PROTECTED;
        } catch (IOException exception) {
            throw new LocalWritePermissionException("Cannot validate sensitive parent directory permissions", exception);
        }
    }

    private List<Path> createMissingDirectories(Path normalized) throws IOException {
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
        List<Path> createdOrRaced = new ArrayList<>(missing.size());
        for (Path path : missing) {
            try {
                creationObserver.beforeCreate(path);
                Files.createDirectory(path);
            } catch (FileAlreadyExistsException concurrentCreation) {
                rejectSymbolicLink(path);
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new LocalWritePermissionException("Directory path was concurrently replaced");
                }
            }
            createdOrRaced.add(path);
        }
        return List.copyOf(createdOrRaced);
    }

    private void requireHardened(Path path, boolean directory) throws IOException {
        if (!harden(path, directory)) {
            throw new LocalWritePermissionException(
                    "Sensitive local storage requires POSIX or ACL permission controls: " + path);
        }
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

    private void requireProtectedPosixDirectory(
            Path path,
            boolean sensitiveDirectory,
            UserPrincipal sensitiveOwner,
            boolean stickyAllowedForSensitiveDirectory) throws IOException {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        boolean writableByOthers = permissions.contains(PosixFilePermission.GROUP_WRITE)
                || permissions.contains(PosixFilePermission.OTHERS_WRITE);
        if (!writableByOthers) {
            return;
        }
        boolean stickyProtectionAllowed = !sensitiveDirectory || stickyAllowedForSensitiveDirectory;
        if (!stickyProtectionAllowed || !hasUnixStickyBit(path)) {
            throw new LocalWritePermissionException(
                    "Sensitive path must not be replaceable by group or other users: " + path);
        }
        UserPrincipal owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
        if (!samePrincipal(owner, sensitiveOwner) && !isTrustedPosixAdministrator(owner)) {
            throw new LocalWritePermissionException(
                    "Writable sticky ancestor is controlled by an untrusted owner: " + path);
        }
    }

    private boolean hasUnixStickyBit(Path path) {
        try {
            Object raw = Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS);
            return raw instanceof Number mode && (mode.intValue() & UNIX_STICKY_BIT) != 0;
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException unavailable) {
            return false;
        }
    }

    private boolean isTrustedPosixAdministrator(UserPrincipal principal) {
        String name = principal.getName();
        return name.equals("root") || name.equals("0");
    }

    private void requireProtectedAclDirectory(
            Path path,
            AclFileAttributeView view,
            boolean sensitiveDirectory,
            UserPrincipal sensitiveOwner) throws IOException {
        UserPrincipal owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
        Set<AclEntryPermission> dangerous = sensitiveDirectory
                ? TARGET_DIRECTORY_MUTATION_PERMISSIONS
                : ANCESTOR_REPLACEMENT_PERMISSIONS;
        for (AclEntry entry : view.getAcl()) {
            if (entry.type() != AclEntryType.ALLOW || entry.flags().contains(AclEntryFlag.INHERIT_ONLY)) {
                continue;
            }
            if (isTrustedAclPrincipal(entry.principal(), owner, sensitiveOwner)) {
                continue;
            }
            if (!Collections.disjoint(entry.permissions(), dangerous)) {
                throw new LocalWritePermissionException(
                        "Sensitive path ACL grants replacement or mutation rights to an untrusted principal: "
                                + path + " (" + entry.principal().getName() + ")");
            }
        }
    }

    private boolean isTrustedAclPrincipal(
            UserPrincipal principal,
            UserPrincipal owner,
            UserPrincipal sensitiveOwner) {
        if (samePrincipal(principal, owner)
                || samePrincipal(principal, sensitiveOwner)
                || isCurrentRuntimeUser(principal)) {
            return true;
        }
        return trustedAclPrincipals.stream().anyMatch(trusted -> samePrincipal(principal, trusted));
    }

    private boolean isCurrentRuntimeUser(UserPrincipal principal) {
        return runtimePrincipal != null && samePrincipal(principal, runtimePrincipal);
    }

    private boolean samePrincipal(UserPrincipal left, UserPrincipal right) {
        return samePrincipalIdentity(left, right);
    }

    /** Identity comparison intentionally does not fall back to display-name equality. */
    static boolean samePrincipalIdentity(UserPrincipal left, UserPrincipal right) {
        return Objects.requireNonNull(left, "left").equals(Objects.requireNonNull(right, "right"));
    }

    private static Set<UserPrincipal> resolveTrustedWindowsPrincipals() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return Set.of();
        }
        UserPrincipalLookupService lookup = FileSystems.getDefault().getUserPrincipalLookupService();
        Set<UserPrincipal> resolved = new LinkedHashSet<>();
        for (String name : List.of(
                "NT AUTHORITY\\SYSTEM",
                "SYSTEM",
                "NT SERVICE\\TrustedInstaller",
                "CREATOR OWNER",
                "CREATEUR PROPRIETAIRE",
                "PROPRIETAIRE CREATEUR")) {
            resolveUserPrincipal(lookup, name, resolved);
        }
        for (String name : List.of(
                "BUILTIN\\Administrators",
                "BUILTIN\\Administrateurs")) {
            resolveGroupPrincipal(lookup, name, resolved);
        }
        return Set.copyOf(resolved);
    }

    private static UserPrincipal resolveCurrentRuntimePrincipal() {
        String runtimeUser = System.getProperty("user.name", "").trim();
        if (runtimeUser.isEmpty()) return null;
        try {
            return FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(runtimeUser);
        } catch (IOException | UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static void resolveUserPrincipal(
            UserPrincipalLookupService lookup,
            String name,
            Set<UserPrincipal> resolved) {
        try {
            resolved.add(lookup.lookupPrincipalByName(name));
        } catch (UserPrincipalNotFoundException ignored) {
            // Localized or unavailable well-known principal: fail closed by not trusting it.
        } catch (IOException | UnsupportedOperationException ignored) {
            // Principal lookup is optional; ACL validation remains fail-closed for unresolved identities.
        }
    }

    private static void resolveGroupPrincipal(
            UserPrincipalLookupService lookup,
            String name,
            Set<UserPrincipal> resolved) {
        try {
            resolved.add(lookup.lookupPrincipalByGroupName(name));
        } catch (UserPrincipalNotFoundException ignored) {
            // Localized or unavailable well-known principal: fail closed by not trusting it.
        } catch (IOException | UnsupportedOperationException ignored) {
            // Principal lookup is optional; ACL validation remains fail-closed for unresolved identities.
        }
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

    @FunctionalInterface
    interface DirectoryCreationObserver {
        DirectoryCreationObserver NONE = ignored -> { };

        void beforeCreate(Path path) throws IOException;
    }

    public enum Result {
        HARDENED,
        PREEXISTING_PRESERVED,
        WRITE_PROTECTED,
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
