package com.morpheus.sdk.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginDiscoveryReplacementTest {

    @TempDir
    Path directory;

    @Test
    void candidateReplacedBetweenSelectionAndMetadataReadIsRejected() throws Exception {
        Path candidate = directory.resolve("provider.jar");
        Path replacement = directory.resolve("replacement.tmp");
        writeMetadataOnlyJar(candidate, metadata("original"));
        writeMetadataOnlyJar(replacement, metadata("replacement-with-different-size"));

        ProviderPluginDiscovery discovery = new ProviderPluginDiscovery(jar ->
                Files.move(replacement, jar, StandardCopyOption.REPLACE_EXISTING));

        assertChangedDuringScan(discovery.discover(directory));
    }

    @Test
    void identityComparisonRejectsEveryFailClosedAttributeChange() throws Exception {
        ProviderPluginDiscovery discovery = new ProviderPluginDiscovery();
        Attributes baseline = attributes(true, 100L, 1_000L, 2_000L, "key-a");

        assertTrue(sameIdentity(discovery, baseline, baseline));
        assertFalse(sameIdentity(discovery,
                attributes(false, 100L, 1_000L, 2_000L, "key-a"), baseline));
        assertFalse(sameIdentity(discovery, baseline,
                attributes(false, 100L, 1_000L, 2_000L, "key-a")));
        assertFalse(sameIdentity(discovery, baseline,
                attributes(true, 101L, 1_000L, 2_000L, "key-a")));
        assertFalse(sameIdentity(discovery, baseline,
                attributes(true, 100L, 1_001L, 2_000L, "key-a")));
        assertFalse(sameIdentity(discovery, baseline,
                attributes(true, 100L, 1_000L, 2_001L, "key-a")));
        assertFalse(sameIdentity(discovery, baseline,
                attributes(true, 100L, 1_000L, 2_000L, "key-b")));

        Attributes withoutFileKey = attributes(true, 100L, 1_000L, 2_000L, null);
        assertTrue(sameIdentity(discovery, withoutFileKey, withoutFileKey));
        assertFalse(sameIdentity(discovery, withoutFileKey, baseline));
    }

    @Test
    void regularCandidateGuardRejectsNonRegularPath() throws Exception {
        ProviderPluginDiscovery discovery = new ProviderPluginDiscovery();
        var method = ProviderPluginDiscovery.class.getDeclaredMethod("requireRegularCandidate", Path.class);
        method.setAccessible(true);

        InvocationTargetException failure = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(discovery, directory));

        assertTrue(failure.getCause() instanceof IOException);
    }

    private static boolean sameIdentity(
            ProviderPluginDiscovery discovery,
            BasicFileAttributes left,
            BasicFileAttributes right) throws Exception {
        var method = ProviderPluginDiscovery.class.getDeclaredMethod(
                "sameIdentity", BasicFileAttributes.class, BasicFileAttributes.class);
        method.setAccessible(true);
        return (boolean) method.invoke(discovery, left, right);
    }

    private static Attributes attributes(
            boolean regular,
            long size,
            long lastModifiedMillis,
            long creationMillis,
            Object fileKey) {
        return new Attributes(
                regular,
                size,
                FileTime.fromMillis(lastModifiedMillis),
                FileTime.fromMillis(creationMillis),
                fileKey);
    }

    private static void assertChangedDuringScan(ProviderPluginDiscoveryResult result) {
        assertEquals(1, result.candidates().size());
        ProviderPluginCandidate discovered = result.candidates().getFirst();
        assertEquals(ProviderPluginStatus.INVALID, discovered.status());
        assertTrue(discovered.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("PLUGIN_JAR_CHANGED_DURING_SCAN")));
    }

    private static Properties metadata(String pluginId) {
        Properties properties = new Properties();
        properties.setProperty("plugin.id", pluginId);
        properties.setProperty("provider.id", pluginId + "-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", "1");
        properties.setProperty("morpheus.minVersion", "1.0.0");
        return properties;
    }

    private static void writeMetadataOnlyJar(Path path, Properties properties) throws Exception {
        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        byte[] bytes = metadata.toString().getBytes(StandardCharsets.UTF_8);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            jar.write(bytes);
            jar.closeEntry();
        }
    }

    private record Attributes(
            boolean regular,
            long size,
            FileTime lastModifiedTime,
            FileTime creationTime,
            Object fileKey) implements BasicFileAttributes {
        @Override
        public FileTime lastAccessTime() {
            return lastModifiedTime;
        }

        @Override
        public boolean isRegularFile() {
            return regular;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public boolean isOther() {
            return !regular;
        }
    }
}
