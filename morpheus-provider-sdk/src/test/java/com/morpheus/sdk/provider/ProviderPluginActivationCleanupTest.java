package com.morpheus.sdk.provider;

import com.morpheus.application.security.ExternalJarIntegrity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that a plugin failing outside the ordinary exception taxonomy still releases what activation acquired.
 *
 * <p>Activation opens a URLClassLoader over a staged copy of the JAR and then runs third-party code: service
 * lookup, class initialization, and three factory calls. A static initializer that throws arrives as
 * {@link ExceptionInInitializerError} -- neither a {@code ServiceConfigurationError} nor a
 * {@code RuntimeException}, so it used to escape before the classloader was closed. On Windows an open
 * classloader keeps the JAR handle, which makes the staged copy undeletable, so the leak is a file as well as a
 * classloader.</p>
 */
class ProviderPluginActivationCleanupTest {
    private static final String STAGING_PREFIX = "morpheus-trusted-plugin-";

    @TempDir
    Path directory;

    /**
     * The activator calls metadata(), createProvider() and createContentReader() after ServiceLoader has already
     * handed back the instance. Nothing wraps an Error raised there, so it left the block with its own type --
     * past a catch that named only ServiceConfigurationError and RuntimeException.
     */
    @Test
    void anErrorFromAPluginFactoryCallStillReleasesTheClassloaderAndStagedJar() throws Exception {
        Path jar = pluginJar(TestLinkageFailureProviderPlugin.class);
        ProviderPluginCandidate candidate = new ProviderPluginDiscovery().discover(directory).candidates().getFirst();
        assertTrue(candidate.compatible(), candidate.diagnostics().toString());
        String sha256 = ExternalJarIntegrity.sha256(jar);

        Set<Path> stagedBefore = stagedPluginCopies();

        NoClassDefFoundError raised = assertThrows(
                NoClassDefFoundError.class,
                () -> new ProviderPluginActivator().activate(candidate, sha256));
        assertEquals(
                "com/example/ProviderDependencyMissingAtRuntime",
                raised.getMessage(),
                "a linkage error must keep its own type so the service layer can report it as one");

        assertNoStagedCopyLeaked(stagedBefore);
    }

    /**
     * ServiceLoader wraps whatever a provider throws while being instantiated, so a failing static initializer
     * arrives as a ServiceConfigurationError. Pinned here so the two failure shapes stay distinguishable.
     */
    @Test
    void aPluginWhoseClassInitializationFailsIsReportedAsAnActivationFailure() throws Exception {
        Path jar = pluginJar(TestStaticInitFailureProviderPlugin.class);
        ProviderPluginCandidate candidate = new ProviderPluginDiscovery().discover(directory).candidates().getFirst();
        assertTrue(candidate.compatible(), candidate.diagnostics().toString());
        String sha256 = ExternalJarIntegrity.sha256(jar);

        Set<Path> stagedBefore = stagedPluginCopies();

        IllegalStateException refused = assertThrows(
                IllegalStateException.class,
                () -> new ProviderPluginActivator().activate(candidate, sha256));
        assertTrue(refused.getCause() instanceof java.util.ServiceConfigurationError,
                () -> "expected ServiceLoader to wrap the initializer failure, got: " + refused.getCause());

        assertNoStagedCopyLeaked(stagedBefore);
    }

    /** The same guarantee on the ordinary path, so the two cannot drift apart. */
    @Test
    void aPluginThatDisagreesWithItsManifestAlsoReleasesTheStagedJar() throws Exception {
        Path jar = pluginJar(TestMismatchedProviderPlugin.class);
        ProviderPluginCandidate candidate = new ProviderPluginDiscovery().discover(directory).candidates().getFirst();
        assertTrue(candidate.compatible(), candidate.diagnostics().toString());
        String sha256 = ExternalJarIntegrity.sha256(jar);

        Set<Path> stagedBefore = stagedPluginCopies();

        IllegalStateException refused = assertThrows(
                IllegalStateException.class,
                () -> new ProviderPluginActivator().activate(candidate, sha256));
        assertTrue(refused.getMessage().contains("provider plugin activation failed for"));

        assertNoStagedCopyLeaked(stagedBefore);
    }

    private Path pluginJar(Class<?> pluginClass) throws IOException {
        Path jar = directory.resolve("cleanup-plugin.jar");
        Properties properties = new Properties();
        properties.setProperty("plugin.id", "manifest-plugin");
        properties.setProperty("provider.id", "manifest-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", Integer.toString(ProviderSdk.API_VERSION));
        properties.setProperty("morpheus.minVersion", "1.0.0");

        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/services/" + MorpheusProviderPlugin.class.getName()));
            output.write((pluginClass.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private static void assertNoStagedCopyLeaked(Set<Path> before) throws IOException {
        Set<Path> leaked = stagedPluginCopies();
        leaked.removeAll(before);
        assertTrue(
                leaked.isEmpty(),
                () -> "activation left a staged plugin JAR behind: " + leaked);
    }

    private static Set<Path> stagedPluginCopies() throws IOException {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"));
        if (!Files.isDirectory(temp)) {
            return new HashSet<>();
        }
        try (Stream<Path> entries = Files.list(temp)) {
            return entries
                    .filter(path -> path.getFileName().toString().startsWith(STAGING_PREFIX))
                    .collect(Collectors.toCollection(HashSet::new));
        }
    }

}
