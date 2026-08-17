package com.morpheus.application.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWritePermissionHardenerTest {

    @TempDir
    Path tempDir;

    @Test
    void hardensDirectoryAndFileToOwnerOnlyWhereFilesystemSupportsIt() throws Exception {
        LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
        Path directory = tempDir.resolve("private");
        LocalWritePermissionHardener.Result directoryResult = hardener.hardenDirectory(directory);
        Path file = directory.resolve("morpheus.db");
        Files.writeString(file, "db");
        LocalWritePermissionHardener.Result fileResult = hardener.hardenFile(file);

        assertEquals(LocalWritePermissionHardener.Result.HARDENED, directoryResult);
        assertEquals(LocalWritePermissionHardener.Result.HARDENED, fileResult);
        assertFalse(Files.isSymbolicLink(directory));
        assertFalse(Files.isSymbolicLink(file));
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            assertEquals(Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            assertTrue(acl != null && acl.getAcl().stream().allMatch(entry -> entry.principal().equals(Files.getOwner(file))));
        }
    }

    @Test
    void refusesSymbolicLinkTargetsWhenPlatformAllowsCreatingOne() throws Exception {
        LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
        Path target = tempDir.resolve("target.txt");
        Files.writeString(target, "target");
        Path link = tempDir.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, target);
        } catch (Exception unavailable) {
            return;
        }

        assertThrows(LocalWritePermissionHardener.LocalWritePermissionException.class, () -> hardener.hardenFile(link));
    }

    @Test
    void preservesPreexistingParentPermissionsWhenNotWritableByOthers() throws Exception {
        LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
        Path existing = tempDir.resolve("user-owned-parent");
        Files.createDirectory(existing);

        PosixFileAttributeView posix = Files.getFileAttributeView(
                existing, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        Set<PosixFilePermission> before = null;
        if (posix != null) {
            before = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE);
            Files.setPosixFilePermissions(existing, before);
        }

        assertEquals(
                LocalWritePermissionHardener.Result.PREEXISTING_PRESERVED,
                hardener.hardenDirectory(existing));
        if (before != null) {
            assertEquals(before, Files.getPosixFilePermissions(existing, LinkOption.NOFOLLOW_LINKS));
        }
    }

    @Test
    void refusesPreexistingPosixDirectoryWritableByOtherUsers() throws Exception {
        Path existing = tempDir.resolve("shared-parent");
        Files.createDirectory(existing);
        PosixFileAttributeView posix = Files.getFileAttributeView(
                existing, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix == null) return;

        Files.setPosixFilePermissions(existing, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.GROUP_EXECUTE));

        assertThrows(
                LocalWritePermissionHardener.LocalWritePermissionException.class,
                () -> new LocalWritePermissionHardener().hardenDirectory(existing));
    }

    @Test
    void refusesWritablePosixAncestorEvenWhenSensitiveChildIsOwnerOnly() throws Exception {
        Path shared = tempDir.resolve("shared-ancestor");
        Files.createDirectory(shared);
        PosixFileAttributeView posix = Files.getFileAttributeView(
                shared, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix == null) return;

        Files.setPosixFilePermissions(shared, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.GROUP_EXECUTE));

        Path sensitive = shared.resolve("private");
        assertThrows(
                LocalWritePermissionHardener.LocalWritePermissionException.class,
                () -> new LocalWritePermissionHardener().hardenDirectory(sensitive));
    }

    @Test
    void hardensDirectoryThatAppearsInCreateRace() throws Exception {
        Path raced = tempDir.resolve("raced");
        AtomicBoolean injected = new AtomicBoolean();
        LocalWritePermissionHardener hardener = new LocalWritePermissionHardener(path -> {
            if (path.equals(raced) && injected.compareAndSet(false, true)) {
                Files.createDirectory(path);
                PosixFileAttributeView posix = Files.getFileAttributeView(
                        path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (posix != null) {
                    Files.setPosixFilePermissions(path, EnumSet.allOf(PosixFilePermission.class));
                }
            }
        });

        assertEquals(LocalWritePermissionHardener.Result.HARDENED, hardener.hardenDirectory(raced));
        assertTrue(injected.get());
        PosixFileAttributeView posix = Files.getFileAttributeView(
                raced, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            assertEquals(Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE),
                    Files.getPosixFilePermissions(raced, LinkOption.NOFOLLOW_LINKS));
        }
    }

    @Test
    void refusesAclDirectoryGrantingMutationToBroadPrincipalWhenAclIsAvailable() throws Exception {
        Path existing = tempDir.resolve("acl-shared-parent");
        Files.createDirectory(existing);
        AclFileAttributeView view = Files.getFileAttributeView(existing, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) return;
        GroupPrincipal broad = broadGroupPrincipal();
        if (broad == null) return;

        List<AclEntry> acl = new ArrayList<>(view.getAcl());
        acl.add(0, AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(broad)
                .setPermissions(
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.DELETE_CHILD)
                .build());
        view.setAcl(acl);

        assertThrows(
                LocalWritePermissionHardener.LocalWritePermissionException.class,
                () -> new LocalWritePermissionHardener().hardenDirectory(existing));
    }

    @Test
    void refusesNonRegularFileTargets() throws Exception {
        Path directory = tempDir.resolve("not-a-file");
        Files.createDirectory(directory);

        assertThrows(
                LocalWritePermissionHardener.LocalWritePermissionException.class,
                () -> new LocalWritePermissionHardener().hardenFile(directory));
    }

    private GroupPrincipal broadGroupPrincipal() throws IOException {
        for (String name : List.of("Everyone", "BUILTIN\\Users", "Users")) {
            try {
                return FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByGroupName(name);
            } catch (UserPrincipalNotFoundException ignored) {
                // Try the next well-known spelling. ACL-backed CI currently resolves at least one on Windows.
            }
        }
        return null;
    }
}
