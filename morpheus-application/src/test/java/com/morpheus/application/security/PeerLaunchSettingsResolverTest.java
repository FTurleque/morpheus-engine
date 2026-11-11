package com.morpheus.application.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every case is resolved for both peers: the resolution is the same, and only the peer's name varies. */
class PeerLaunchSettingsResolverTest {
    private static final List<PeerLaunchSettingsResolver.Peer> PEERS = List.of(
            new PeerLaunchSettingsResolver.Peer("MINOS", "morpheus.minos", "MORPHEUS_MINOS"),
            new PeerLaunchSettingsResolver.Peer("NEXUS", "morpheus.nexus", "MORPHEUS_NEXUS"));

    @TempDir
    Path tempDirectory;

    @Test
    void theSettingNamesOfAPeerDeriveFromItsTwoPrefixes() {
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            String lower = peer.name().toLowerCase(Locale.ROOT);
            assertEquals(List.of("morpheus." + lower + ".jar", "morpheus." + lower + ".jar.sha256",
                            "morpheus." + lower + ".java", "morpheus." + lower + ".home",
                            "morpheus." + lower + ".timeoutSeconds"),
                    List.of(peer.jarProperty(), peer.jarSha256Property(), peer.javaProperty(), peer.homeProperty(),
                            peer.timeoutProperty()), peer.name());
            assertEquals(List.of("MORPHEUS_" + peer.name() + "_JAR", "MORPHEUS_" + peer.name() + "_JAR_SHA256",
                            "MORPHEUS_" + peer.name() + "_JAVA", "MORPHEUS_" + peer.name() + "_HOME",
                            "MORPHEUS_" + peer.name() + "_TIMEOUT_SECONDS"),
                    List.of(peer.jarEnvironment(), peer.jarSha256Environment(), peer.javaEnvironment(),
                            peer.homeEnvironment(), peer.timeoutEnvironment()), peer.name());
        }
    }

    @Test
    void aPeerNamedAloneTakesThePrefixesEveryPeerHas() {
        assertEquals(PEERS, List.of(PeerLaunchSettingsResolver.Peer.named("MINOS"),
                PeerLaunchSettingsResolver.Peer.named(" nexus ")));
    }

    @Test
    void aPeerWithoutANameIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new PeerLaunchSettingsResolver.Peer(" ", "morpheus.peer", "MORPHEUS_PEER"));
        assertEquals("name must not be blank", refused.getMessage());
    }

    @Test
    void anAbsentConfigurationDisablesThePeerWithoutError() {
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings settings = resolve(peer, Map.of(), Map.of());

            assertEquals(PeerLaunchSettings.State.DISABLED, settings.state(), peer.name());
            assertFalse(settings.enabled(), peer.name());
            assertEquals(Optional.empty(), settings.configurationError(), peer.name());
            assertEquals(Duration.ofSeconds(PeerLaunchSettings.DEFAULT_TIMEOUT_SECONDS), settings.timeout());
            assertTrue(Path.of(settings.javaCommand()).startsWith(Path.of(System.getProperty("java.home"))),
                    () -> peer.name() + " default launcher must sit under java.home: " + settings.javaCommand());
        }
    }

    @Test
    void aPinnedJarEnablesThePeerAndPropertiesOverrideTheEnvironment() throws Exception {
        Path jar = jar();
        Path home = Files.createDirectories(tempDirectory.resolve("home"));
        String pin = ExternalJarIntegrity.sha256(jar);
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            Properties properties = new Properties();
            properties.setProperty(peer.jarProperty(), jar.toString());
            properties.setProperty(peer.jarSha256Property(), " " + pin.toUpperCase(Locale.ROOT) + " ");
            properties.setProperty(peer.homeProperty(), home.toString());
            properties.setProperty(peer.timeoutProperty(), "37");
            Map<String, String> environment = Map.of(
                    peer.jarEnvironment(), tempDirectory.resolve("other.jar").toString(),
                    peer.timeoutEnvironment(), "never");

            PeerLaunchSettings settings = PeerLaunchSettingsResolver.resolve(peer, environment, properties);

            assertEquals(PeerLaunchSettings.State.CONFIGURED, settings.state(), peer.name());
            assertTrue(settings.enabled(), peer.name());
            assertEquals(Optional.of(jar.toAbsolutePath().normalize()), settings.jarPath());
            assertEquals(Optional.of(pin), settings.jarSha256());
            assertEquals(Optional.of(home.toAbsolutePath().normalize()), settings.homeDirectory());
            assertEquals(Duration.ofSeconds(37), settings.timeout());
        }
    }

    @Test
    void anUnpinnedJarIsValidAndStartsNothing() throws Exception {
        Path jar = jar();
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings settings = resolve(peer, Map.of(peer.jarEnvironment(), jar.toString()), Map.of());

            assertEquals(PeerLaunchSettings.State.CONFIGURED, settings.state(), peer.name());
            assertFalse(settings.enabled(), peer.name());
            assertEquals(Optional.empty(), settings.jarSha256(), peer.name());
        }
    }

    @Test
    void aJarThatIsNotAFileIsRefusedInTheSameWordsForEveryPeer() {
        Path missing = tempDirectory.resolve("missing.jar");
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings settings = resolve(peer, Map.of(), Map.of(peer.jarProperty(), missing.toString()));

            assertRefused(settings, peer.name() + " JAR is not a file: " + missing.toAbsolutePath().normalize());
            assertEquals(Optional.empty(), settings.jarPath(), peer.name());
        }
    }

    @Test
    void aJarThatIsNotAPathIsRefused() {
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings settings = resolve(peer, Map.of(), Map.of(peer.jarProperty(), "peer\u0000.jar"));

            assertRefused(settings, "invalid " + peer.name() + " path: peer\u0000.jar");
        }
    }

    @Test
    void aPinWithoutAJarNamesBothSettingsOfThePeer() {
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings settings = resolve(peer, Map.of(), Map.of(peer.jarSha256Property(), "0".repeat(64)));

            assertRefused(settings, peer.name() + " JAR SHA-256 pin requires " + peer.jarEnvironment() + "/"
                    + peer.jarProperty());
        }
    }

    @Test
    void aMalformedOrMismatchedPinIsRefusedAndNothingIsStarted() throws Exception {
        Path jar = jar();
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings malformed = resolve(peer, Map.of(),
                    Map.of(peer.jarProperty(), jar.toString(), peer.jarSha256Property(), "not-a-digest"));
            PeerLaunchSettings mismatched = resolve(peer, Map.of(),
                    Map.of(peer.jarProperty(), jar.toString(), peer.jarSha256Property(), "0".repeat(64)));

            assertRefused(malformed, peer.name() + " JAR integrity verification failed: expected SHA-256 must "
                    + "contain exactly 64 hexadecimal characters");
            assertTrue(mismatched.configurationError().orElseThrow().startsWith(
                            peer.name() + " JAR integrity verification failed: external JAR integrity mismatch"),
                    () -> peer.name() + ": " + mismatched.configurationError());
            assertFalse(malformed.enabled() || mismatched.enabled(), peer.name());
            assertEquals(Optional.empty(), mismatched.jarPath(), peer.name());
        }
    }

    @Test
    void aTimeoutOutOfBoundsOrNotANumberIsRefusedAndTheDefaultIsRetained() {
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            for (String outOfBounds : List.of("0", Integer.toString(PeerLaunchSettings.MAX_TIMEOUT_SECONDS + 1))) {
                PeerLaunchSettings settings = resolve(peer, Map.of(), Map.of(peer.timeoutProperty(), outOfBounds));

                assertRefused(settings, peer.name() + " timeout must be between 1 and "
                        + PeerLaunchSettings.MAX_TIMEOUT_SECONDS + " seconds");
                assertEquals(Duration.ofSeconds(PeerLaunchSettings.DEFAULT_TIMEOUT_SECONDS), settings.timeout());
            }
            PeerLaunchSettings notANumber = resolve(peer, Map.of(peer.timeoutEnvironment(), "soon"), Map.of());
            assertRefused(notANumber, "invalid " + peer.name() + " timeout: soon");
            assertEquals(Duration.ofSeconds(PeerLaunchSettings.DEFAULT_TIMEOUT_SECONDS), notANumber.timeout());
        }
    }

    @Test
    void bothBoundsOfTheTimeoutAreAccepted() {
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            for (int bound : List.of(1, PeerLaunchSettings.MAX_TIMEOUT_SECONDS)) {
                PeerLaunchSettings settings = resolve(peer, Map.of(),
                        Map.of(peer.timeoutProperty(), Integer.toString(bound)));

                assertEquals(Duration.ofSeconds(bound), settings.timeout(), peer.name());
                assertEquals(Optional.empty(), settings.configurationError(), peer.name());
            }
        }
    }

    @Test
    void aLauncherTheSearchPathWouldResolveIsRefused() throws Exception {
        Path jar = jar();
        Path missing = tempDirectory.resolve("no-java");
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings bare = resolve(peer, Map.of(),
                    Map.of(peer.jarProperty(), jar.toString(), peer.javaProperty(), "java"));
            PeerLaunchSettings absent = resolve(peer, Map.of(),
                    Map.of(peer.jarProperty(), jar.toString(), peer.javaProperty(), missing.toString()));

            assertRefused(bare, peer.name() + " Java command must be an absolute path");
            assertRefused(absent, peer.name() + " Java command is not an executable file");
            assertTrue(Path.of(bare.javaCommand()).isAbsolute(), peer.name());
        }
    }

    @Test
    void aConfiguredLauncherIsLaunchedFromItsRealFile() throws Exception {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path runtime = Files.isRegularFile(bin.resolve("java.exe")) ? bin.resolve("java.exe") : bin.resolve("java");
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings settings = resolve(peer, Map.of(peer.javaEnvironment(), runtime.toString()), Map.of());

            assertEquals(runtime.toRealPath().toString(), settings.javaCommand(), peer.name());
            assertEquals(Optional.empty(), settings.configurationError(), peer.name());
        }
    }

    @Test
    void everyRefusalOfOneConfigurationIsReportedTogether() {
        Path missing = tempDirectory.resolve("missing.jar");
        for (PeerLaunchSettingsResolver.Peer peer : PEERS) {
            PeerLaunchSettings settings = resolve(peer, Map.of(),
                    Map.of(peer.jarProperty(), missing.toString(), peer.timeoutProperty(), "0"));

            assertRefused(settings, peer.name() + " JAR is not a file: " + missing.toAbsolutePath().normalize() + "; "
                    + peer.name() + " timeout must be between 1 and " + PeerLaunchSettings.MAX_TIMEOUT_SECONDS
                    + " seconds");
        }
    }

    @Test
    void theSharedSettingsRefuseALauncherThatIsNotAbsoluteAndAnUnboundedTimeout() {
        IllegalArgumentException relative = assertThrows(IllegalArgumentException.class,
                () -> new PeerLaunchSettings(Optional.empty(), "java", Optional.empty(), Duration.ofSeconds(5),
                        Optional.empty(), Optional.empty()));
        String launcher = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        IllegalArgumentException unbounded = assertThrows(IllegalArgumentException.class,
                () -> new PeerLaunchSettings(Optional.empty(), launcher, Optional.empty(),
                        Duration.ofSeconds(PeerLaunchSettings.MAX_TIMEOUT_SECONDS + 1), Optional.empty(),
                        Optional.empty()));

        assertEquals("javaCommand must be an absolute path", relative.getMessage());
        assertEquals("timeout must be between 1 and " + PeerLaunchSettings.MAX_TIMEOUT_SECONDS + " seconds",
                unbounded.getMessage());
    }

    private Path jar() throws Exception {
        return Files.write(tempDirectory.resolve("peer.jar"), new byte[]{1, 2, 3, 4});
    }

    private static PeerLaunchSettings resolve(PeerLaunchSettingsResolver.Peer peer, Map<String, String> environment,
                                              Map<String, String> properties) {
        Properties jvmProperties = new Properties();
        properties.forEach(jvmProperties::setProperty);
        return PeerLaunchSettingsResolver.resolve(peer, environment, jvmProperties);
    }

    private static void assertRefused(PeerLaunchSettings settings, String error) {
        assertEquals(PeerLaunchSettings.State.INVALID, settings.state(), error);
        assertEquals(Optional.of(error), settings.configurationError());
        assertFalse(settings.enabled(), error);
    }
}
