package com.morpheus.application.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
            UserPrincipal owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
            assertTrue(acl != null && acl.getAcl().stream().allMatch(entry -> entry.principal().equals(owner)));
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
        assumeTrue(view != null, "this file system has no ACL view");
        GroupPrincipal broad = broadGroupPrincipal();
        assumeTrue(broad != null, "no broad group could be resolved from its well-known SID");

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

        LocalWritePermissionHardener.LocalWritePermissionException refused = assertThrows(
                LocalWritePermissionHardener.LocalWritePermissionException.class,
                () -> new LocalWritePermissionHardener().hardenDirectory(existing));
        assertTrue(refused.getMessage().startsWith(
                        "Sensitive path ACL grants replacement or mutation rights to an untrusted principal: "),
                refused.getMessage());
        assertTrue(refused.getMessage().endsWith("(" + broad.getName() + ")"), refused.getMessage());
    }

    @Test
    void refusesNonRegularFileTargets() throws Exception {
        Path directory = tempDir.resolve("not-a-file");
        Files.createDirectory(directory);

        assertThrows(
                LocalWritePermissionHardener.LocalWritePermissionException.class,
                () -> new LocalWritePermissionHardener().hardenFile(directory));
    }

    /**
     * A broad group, found by its well-known SID rather than its name: "Everyone" is "Tout le monde" on a French
     * Windows, where looking it up by its English name resolved nothing and the test passed having verified nothing.
     * The JDK looks principals up by name only, so the SID is translated to the local name by the platform.
     */
    private GroupPrincipal broadGroupPrincipal() throws IOException {
        for (String sid : List.of("S-1-1-0", "S-1-5-32-545")) {
            String localName = localNameOfSid(sid);
            if (localName == null) continue;
            try {
                return FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByGroupName(localName);
            } catch (UserPrincipalNotFoundException ignored) {
                // Try the next well-known SID.
            }
        }
        return null;
    }

    /** Windows PowerShell ships with Windows, at a fixed place under the system root; the PATH is not relied on. */
    private static String windowsPowerShell() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null) return "powershell";
        Path shipped = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        return Files.isRegularFile(shipped) ? shipped.toString() : "powershell";
    }

    private static String localNameOfSid(String sid) {
        try {
            Process process = new ProcessBuilder(windowsPowerShell(), "-NoProfile", "-NonInteractive", "-Command",
                    "(New-Object System.Security.Principal.SecurityIdentifier('" + sid + "'))"
                            + ".Translate([System.Security.Principal.NTAccount]).Value")
                    .redirectErrorStream(true)
                    .start();
            // Bounded before reading: reading first would wait on the stream of a hung PowerShell without limit.
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() != 0 || output.isEmpty() ? null : output;
        } catch (IOException interruptedOrMissing) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
