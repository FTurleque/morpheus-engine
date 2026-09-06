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

        assertTrue(resolver.contains("$innoVersion = '7.0.2'"));
        assertTrue(resolver.contains("releases/download/is-7_0_2/$assetName"));
        assertTrue(resolver.contains("Get-AuthenticodeSignature -LiteralPath $resolved"));
        assertTrue(resolver.contains("$version.FileMajorPart -ne 7"));
        assertTrue(resolver.contains("$version.FileMinorPart -ne 0"));
        assertTrue(resolver.contains("$version.FileBuildPart -ne 2"));
        assertTrue(resolver.contains("elseif (-not $PinnedBootstrap)"));
        assertTrue(resolver.contains("Pyrsys B\\.V\\."));

        // Arbitrary operator/system candidates may never opt into bootstrap provenance.
        assertTrue(resolver.contains("Get-TrustedIsccPath -Path $env:MORPHEUS_ISCC -Strict"));
        assertFalse(resolver.contains("Get-TrustedIsccPath -Path $env:MORPHEUS_ISCC -Strict -PinnedBootstrap"));

        // Missing PE version metadata is tolerated only after a signed, pinned installer populated our
        // controlled compiler root; the extracted compiler itself is still signature/signer validated.
        assertTrue(resolver.contains("Pinned bootstrap compiler escaped controlled compiler root"));
        assertTrue(resolver.contains("Get-TrustedIsccPath -Path $iscc.FullName -Strict -PinnedBootstrap"));
        assertTrue(resolver.contains("Inno Setup bootstrap Authenticode signature is not valid"));
        assertTrue(resolver.contains("Unexpected Inno Setup signer"));
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
