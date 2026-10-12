package com.morpheus.integration.nexus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The executable that starts the NEXUS peer never depends on the search path of the MORPHEUS process.
 *
 * <p>The launcher defaulted to the bare command {@code java}, which the operating system resolves through
 * {@code PATH}: another {@code java} placed first on that path would have been started as the peer. The default is
 * the launcher of the runtime MORPHEUS itself runs on, and a configured value has to name its file in full.</p>
 */
class NexusPeerLauncherResolutionTest {
    @TempDir
    Path tempDirectory;

    @Test
    void withoutAConfiguredLauncherThePeerRunsOnTheRuntimeOfThisJvm() {
        NexusIntegrationSettings settings = NexusIntegrationSettings.resolve(Map.of(), new Properties());

        Path launcher = Path.of(settings.javaCommand());
        assertTrue(launcher.isAbsolute(), () -> "the default launcher must be an absolute path: " + launcher);
        assertTrue(launcher.startsWith(Path.of(System.getProperty("java.home"))),
                () -> "the default launcher must sit under java.home: " + launcher);
        assertTrue(Files.isRegularFile(launcher), () -> "the default launcher must exist: " + launcher);
        assertTrue(settings.configurationError().isEmpty());
    }

    @Test
    void aSearchPathThatNamesAnotherJavaFirstChangesNothing() throws Exception {
        Path decoyDirectory = Files.createDirectory(tempDirectory.resolve("decoy"));
        Files.createFile(decoyDirectory.resolve("java"));
        Files.createFile(decoyDirectory.resolve("java.exe"));
        Map<String, String> environment = new HashMap<>();
        environment.put("PATH", decoyDirectory.toString());
        environment.put("Path", decoyDirectory.toString());

        NexusIntegrationSettings settings = NexusIntegrationSettings.resolve(environment, new Properties());

        assertEquals(NexusIntegrationSettings.resolve(Map.of(), new Properties()).javaCommand(),
                settings.javaCommand());
        assertFalse(settings.javaCommand().startsWith(decoyDirectory.toString()));
    }

    @Test
    void aBareCommandIsRefusedBecauseItWouldBeLookedUpOnTheSearchPath() {
        NexusIntegrationSettings settings = configured("java");

        assertEquals(NexusIntegrationSettings.State.INVALID, settings.state());
        assertFalse(settings.enabled());
        assertEquals("NEXUS Java command must be an absolute path", settings.configurationError().orElseThrow());
    }

    @Test
    void aRelativePathIsRefused() {
        NexusIntegrationSettings settings = configured("bin/java");

        assertEquals(NexusIntegrationSettings.State.INVALID, settings.state());
        assertEquals("NEXUS Java command must be an absolute path", settings.configurationError().orElseThrow());
    }

    @Test
    void aMissingFileAndADirectoryAreRefused() {
        NexusIntegrationSettings missing = configured(tempDirectory.resolve("no-such-java").toString());
        NexusIntegrationSettings directory = configured(tempDirectory.toString());

        assertEquals("NEXUS Java command is not an executable file", missing.configurationError().orElseThrow());
        assertEquals("NEXUS Java command is not an executable file", directory.configurationError().orElseThrow());
        assertFalse(missing.enabled());
        assertFalse(directory.enabled());
    }

    @Test
    void anAbsolutePathToAnExecutableIsAcceptedAndReportedAsTheFileItIs() throws Exception {
        Path launcher = Path.of(NexusIntegrationSettings.resolve(Map.of(), new Properties()).javaCommand());

        NexusIntegrationSettings settings = configured(launcher.toString());

        assertTrue(settings.configurationError().isEmpty(), () -> String.valueOf(settings.configurationError()));
        assertEquals(launcher.toRealPath().toString(), settings.javaCommand());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void aLinkIsResolvedAndTheRealFileIsWhatWouldRun() throws Exception {
        Path launcher = Path.of(NexusIntegrationSettings.resolve(Map.of(), new Properties()).javaCommand());
        Path link = Files.createSymbolicLink(tempDirectory.resolve("java-link"), launcher);

        NexusIntegrationSettings settings = configured(link.toString());

        assertTrue(settings.configurationError().isEmpty(), () -> String.valueOf(settings.configurationError()));
        assertEquals(launcher.toRealPath().toString(), settings.javaCommand());
    }

    private static NexusIntegrationSettings configured(String launcher) {
        Properties properties = new Properties();
        properties.setProperty(NexusIntegrationSettings.JAVA_PROPERTY, launcher);
        return NexusIntegrationSettings.resolve(Map.of(), properties);
    }
}
