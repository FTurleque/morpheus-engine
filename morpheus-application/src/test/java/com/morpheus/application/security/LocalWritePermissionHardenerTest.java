package com.morpheus.application.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertFalse(Files.isSymbolicLink(directory));
        assertFalse(Files.isSymbolicLink(file));
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            assertEquals(LocalWritePermissionHardener.Result.HARDENED, directoryResult);
            assertEquals(LocalWritePermissionHardener.Result.HARDENED, fileResult);
            assertEquals(Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS));
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
}
