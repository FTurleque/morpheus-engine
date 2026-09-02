package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the probe result IPC against pathname substitution.
 *
 * <p>The result pathname is handed to a child JVM that reopens it later. It must therefore never be usable as a
 * substitution target: the worker creates it atomically, and the parent refuses to read anything that is not a
 * regular file reached without following a link. These tests drive those invariants directly rather than racing a
 * timer, so they are deterministic.
 */
class ProviderProbeResultIpcTest {
    @TempDir
    Path directory;

    @Test
    void aResultIsWrittenAndReadBackUnchanged() throws Exception {
        Path result = directory.resolve("probe-result.properties");

        ProviderProbeResultCodec.write(result, sampleResult());
        ProviderProbeResult reloaded = ProviderProbeResultCodec.read(result);

        assertEquals("sample-provider", reloaded.providerId().value());
        assertEquals(ProviderProbeStatus.SUPPORTED, reloaded.status());
    }

    @Test
    void writingRefusesAPathnameAlreadyOccupiedByARegularFile() throws Exception {
        Path result = directory.resolve("probe-result.properties");
        Files.writeString(result, "planted by another local process", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> ProviderProbeResultCodec.write(result, sampleResult()),
                "an occupied result pathname must not be written through");
        assertEquals("planted by another local process", Files.readString(result),
                "the planted content must be left untouched");
    }

    @Test
    void writingRefusesAPathnameOccupiedByASymbolicLink() throws Exception {
        Path victim = directory.resolve("victim.txt");
        Files.writeString(victim, "original", StandardCharsets.UTF_8);
        Path result = directory.resolve("probe-result.properties");
        assumeTrue(createSymbolicLink(result, victim), "symbolic links are not creatable in this environment");

        assertThrows(IOException.class, () -> ProviderProbeResultCodec.write(result, sampleResult()),
                "a symlinked result pathname must not redirect the write");
        assertEquals("original", Files.readString(victim),
                "the symlink target must not be overwritten");
    }

    @Test
    void readingRefusesToFollowASymbolicLink() throws Exception {
        Path payload = directory.resolve("payload.properties");
        ProviderProbeResultCodec.write(payload, sampleResult());
        Path link = directory.resolve("linked-result.properties");
        assumeTrue(createSymbolicLink(link, payload), "symbolic links are not creatable in this environment");

        assertThrows(IOException.class, () -> ProviderProbeResultCodec.read(link),
                "the parent must not read a result reached through a link");
    }

    @Test
    void readingRefusesAResultLargerThanTheBound() throws Exception {
        Path oversized = directory.resolve("oversized.properties");
        Files.writeString(oversized, "x".repeat(ProviderProbeResultCodec.MAX_RESULT_BYTES + 1),
                StandardCharsets.UTF_8);

        IOException failure = assertThrows(IOException.class, () -> ProviderProbeResultCodec.read(oversized));
        assertTrue(failure.getMessage().contains("exceeds"), failure.getMessage());
    }

    @Test
    void readingRefusesAnAbsentResult() {
        assertThrows(IOException.class, () -> ProviderProbeResultCodec.read(directory.resolve("missing.properties")));
    }

    @Test
    void readingRefusesADirectoryInPlaceOfTheResult() throws Exception {
        Path asDirectory = directory.resolve("probe-result.properties");
        Files.createDirectory(asDirectory);

        assertThrows(IOException.class, () -> ProviderProbeResultCodec.read(asDirectory));
    }

    @Test
    void theStagingDirectoryIsRestrictedToItsOwnerAtCreationTime() throws Exception {
        java.nio.file.attribute.FileAttribute<?>[] attributes = ProviderPluginProbeProcess.ownerOnlyAttributes();

        assertEquals(1, attributes.length,
                "this platform must support either POSIX permissions or ACLs for the staging directory");
        String name = attributes[0].name();
        assertTrue(name.equals("posix:permissions") || name.equals("acl:acl"),
                () -> "unexpected staging attribute: " + name);

        Path staged = Files.createTempDirectory("morpheus-staging-attribute-check-", attributes);
        try {
            assertTrue(Files.isDirectory(staged, LinkOption.NOFOLLOW_LINKS));
            List<String> unexpected = principalsBeyondTheOwningAccount(staged);
            assertTrue(unexpected.isEmpty(),
                    () -> "the staging directory grants access beyond the owning account: " + unexpected);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /**
     * Returns the principals allowed on the directory that are neither its owner nor the account this JVM runs as.
     *
     * <p>Windows may record the owner as the administrators group rather than the granting user, so the check is
     * against both identities. Anything else - SYSTEM, Users, Authenticated Users - would mean the inherited
     * temporary-directory ACL survived, which is exactly what the staging attributes exist to replace.
     */
    private List<String> principalsBeyondTheOwningAccount(Path directory) throws IOException {
        var posix = Files.getFileAttributeView(
                directory, java.nio.file.attribute.PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            return posix.readAttributes().permissions().stream()
                    .map(Enum::name)
                    .filter(permission -> permission.startsWith("GROUP_") || permission.startsWith("OTHERS_"))
                    .toList();
        }
        var acl = Files.getFileAttributeView(
                directory, java.nio.file.attribute.AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) {
            return List.of();
        }
        String owner = Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS).getName();
        String runtimeAccount = FileSystems.getDefault()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"))
                .getName();
        return acl.getAcl().stream()
                .map(entry -> entry.principal().getName())
                .filter(principal -> !principal.equals(owner) && !principal.equals(runtimeAccount))
                .distinct()
                .toList();
    }

    private boolean createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return Files.isSymbolicLink(link);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            return false;
        }
    }

    private ProviderProbeResult sampleResult() {
        return new ProviderProbeResult(
                new ProviderId("sample-provider"),
                "1.0.0",
                ProviderProbeStatus.SUPPORTED,
                Optional.of("fixture"),
                Optional.of("1"),
                Optional.of(SourceLocator.file("sample/source")),
                ProviderCapabilitySet.of(),
                false,
                List.of());
    }

    @Test
    void aRegularFileResultIsAcceptedWithoutFollowingLinks() throws Exception {
        Path result = directory.resolve("probe-result.properties");
        ProviderProbeResultCodec.write(result, sampleResult());

        assertTrue(Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(result));
        assertEquals(ProviderProbeStatus.SUPPORTED, ProviderProbeResultCodec.read(result).status());
    }
}
