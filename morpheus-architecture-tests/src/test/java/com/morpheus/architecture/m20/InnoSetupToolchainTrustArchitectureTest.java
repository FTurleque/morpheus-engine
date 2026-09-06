package com.morpheus.architecture.m20;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InnoSetupToolchainTrustArchitectureTest {

    @Test
    void installerCannotBypassPinnedSignedCompilerResolution() throws Exception {
        Path root = repoRoot();
        String builder = Files.readString(root.resolve("distribution/build-installer.ps1"));
        String resolver = Files.readString(root.resolve("distribution/ensure-inno-setup.ps1"));

        assertTrue(builder.contains("ensure-inno-setup.ps1"));
        assertFalse(builder.contains("Get-Command ISCC.exe"));
        assertFalse(builder.contains("Inno Setup $major\\ISCC.exe"));

        assertTrue(resolver.contains("Get-AuthenticodeSignature -LiteralPath $resolved"));
        assertTrue(resolver.contains("$version.FileMajorPart -ne 7"));
        assertTrue(resolver.contains("$version.FileMinorPart -ne 0"));
        assertTrue(resolver.contains("$version.FileBuildPart -ne 2"));
        assertTrue(resolver.contains("Pyrsys B\\.V\\."));
        assertTrue(resolver.contains("Get-TrustedIsccPath -Path $env:MORPHEUS_ISCC -Strict"));
        assertTrue(resolver.contains("Get-TrustedIsccPath -Path $iscc.FullName -Strict"));
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml")) && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
