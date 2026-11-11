package com.morpheus.architecture;

import com.morpheus.application.security.ExternalJarIntegrity;
import com.morpheus.integration.minos.MinosIntegrationSettings;
import com.morpheus.integration.nexus.NexusIntegrationSettings;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launch settings of MINOS and NEXUS are resolved by one implementation, and the two peers are alike on the same
 * inputs.
 *
 * <p>The two settings types decide whether MORPHEUS starts third-party code: the JAR, its pin, the launcher, the home
 * directory and the time bound. Each integration contributes its peer's name and the names of its settings; the
 * resolution, its refusals and its bounds live once, beside the pin verification. This module is the only one that
 * sees both integrations, so it is where they are compared: the same table of configurations is resolved for each
 * peer, and the results must be equal once the peer's name is abstracted. A rule on dependencies keeps a settings type
 * from resolving anything itself.</p>
 */
class McpPeerSettingsResolutionArchitectureTest {
    private static final JavaClasses SETTINGS_TYPES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importClasses(MinosIntegrationSettings.class, MinosIntegrationSettings.State.class,
                    NexusIntegrationSettings.class, NexusIntegrationSettings.State.class);

    private static final List<PeerUnderTest> PEERS = List.of(
            new PeerUnderTest("MINOS", MinosIntegrationSettings.class,
                    names(MinosIntegrationSettings.JAR_PROPERTY, MinosIntegrationSettings.JAR_SHA256_PROPERTY,
                            MinosIntegrationSettings.JAVA_PROPERTY, MinosIntegrationSettings.HOME_PROPERTY,
                            MinosIntegrationSettings.TIMEOUT_PROPERTY),
                    names(MinosIntegrationSettings.JAR_ENV, MinosIntegrationSettings.JAR_SHA256_ENV,
                            MinosIntegrationSettings.JAVA_ENV, MinosIntegrationSettings.HOME_ENV,
                            MinosIntegrationSettings.TIMEOUT_ENV),
                    (environment, properties) -> {
                        MinosIntegrationSettings settings = MinosIntegrationSettings.resolve(environment, properties);
                        return new Outcome(settings.state().name(), settings.enabled(), settings.jarPath(),
                                settings.javaCommand(), settings.homeDirectory(), settings.timeout(),
                                settings.jarSha256(), settings.configurationError());
                    }),
            new PeerUnderTest("NEXUS", NexusIntegrationSettings.class,
                    names(NexusIntegrationSettings.JAR_PROPERTY, NexusIntegrationSettings.JAR_SHA256_PROPERTY,
                            NexusIntegrationSettings.JAVA_PROPERTY, NexusIntegrationSettings.HOME_PROPERTY,
                            NexusIntegrationSettings.TIMEOUT_PROPERTY),
                    names(NexusIntegrationSettings.JAR_ENV, NexusIntegrationSettings.JAR_SHA256_ENV,
                            NexusIntegrationSettings.JAVA_ENV, NexusIntegrationSettings.HOME_ENV,
                            NexusIntegrationSettings.TIMEOUT_ENV),
                    (environment, properties) -> {
                        NexusIntegrationSettings settings = NexusIntegrationSettings.resolve(environment, properties);
                        return new Outcome(settings.state().name(), settings.enabled(), settings.jarPath(),
                                settings.javaCommand(), settings.homeDirectory(), settings.timeout(),
                                settings.jarSha256(), settings.configurationError());
                    }));

    @TempDir
    Path tempDirectory;

    @Test
    void theSettingsTypesOfTheIntegrationsResolveNothingThemselves() {
        noClasses()
                .that().belongToAnyOf(MinosIntegrationSettings.class, NexusIntegrationSettings.class)
                .should().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.FileSystems")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.FileSystem")
                .orShould().dependOnClassesThat().resideInAnyPackage("java.nio.file.attribute..",
                        "java.nio.file.spi..", "java.nio.channels..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.File")
                .orShould().dependOnClassesThat().belongToAnyOf(ExternalJarIntegrity.class)
                .because("the launch settings of an MCP peer are resolved once, by the resolver beside the pin "
                        + "verification; a settings type contributes its peer's name and setting names only")
                .check(SETTINGS_TYPES);
    }

    @Test
    void bothPeersDeclareTheSameSettings() {
        Map<String, Map<String, String>> declared = new TreeMap<>();
        for (PeerUnderTest peer : PEERS) {
            declared.put(peer.name(), peer.publicConstants());
        }
        Map<String, String> minos = declared.get("MINOS");
        Map<String, String> nexus = declared.get("NEXUS");
        TreeSet<String> onOneSideOnly = new TreeSet<>(minos.keySet());
        onOneSideOnly.addAll(nexus.keySet());
        onOneSideOnly.removeIf(name -> minos.containsKey(name) && nexus.containsKey(name));
        assertTrue(onOneSideOnly.isEmpty(), () -> "a setting is declared for one peer only: " + onOneSideOnly
                + " (MINOS " + minos.keySet() + ", NEXUS " + nexus.keySet() + ")");
        assertEquals(minos, nexus, "the two peers name their settings by one pattern, the peer's name aside");

        for (PeerUnderTest peer : PEERS) {
            String lower = peer.name().toLowerCase(java.util.Locale.ROOT);
            assertEquals(List.of("morpheus." + lower + ".jar", "morpheus." + lower + ".jar.sha256",
                            "morpheus." + lower + ".java", "morpheus." + lower + ".home",
                            "morpheus." + lower + ".timeoutSeconds"),
                    List.copyOf(peer.properties().values()), () -> peer.name() + " property names");
            assertEquals(List.of("MORPHEUS_" + peer.name() + "_JAR", "MORPHEUS_" + peer.name() + "_JAR_SHA256",
                            "MORPHEUS_" + peer.name() + "_JAVA", "MORPHEUS_" + peer.name() + "_HOME",
                            "MORPHEUS_" + peer.name() + "_TIMEOUT_SECONDS"),
                    List.copyOf(peer.environment().values()), () -> peer.name() + " environment names");
        }
    }

    @Test
    void bothPeersResolveTheSameInputsAlike() throws IOException {
        Fixture fixture = Fixture.create(tempDirectory);
        List<String> differences = new ArrayList<>();
        for (Row row : rows(fixture)) {
            Map<String, Outcome> outcomes = new TreeMap<>();
            for (PeerUnderTest peer : PEERS) {
                outcomes.put(peer.name(), peer.resolve(row).abstractedFrom(peer.name()));
            }
            if (!outcomes.get("MINOS").equals(outcomes.get("NEXUS"))) {
                differences.add("input '" + row.label() + "': MINOS " + outcomes.get("MINOS")
                        + " / NEXUS " + outcomes.get("NEXUS"));
            }
        }
        assertTrue(differences.isEmpty(),
                () -> "the two peers resolve the same input differently:\n" + String.join("\n", differences));
    }

    @Test
    void theTableCoversEveryStateEveryMalformedSettingAndEveryBound() throws IOException {
        Fixture fixture = Fixture.create(tempDirectory);
        PeerUnderTest minos = PEERS.get(0);
        Map<String, Outcome> byLabel = new TreeMap<>();
        for (Row row : rows(fixture)) {
            byLabel.put(row.label(), minos.resolve(row).abstractedFrom(minos.name()));
        }
        TreeSet<String> states = new TreeSet<>();
        byLabel.values().forEach(outcome -> states.add(outcome.state()));
        assertEquals(new TreeSet<>(List.of("CONFIGURED", "DISABLED", "INVALID")), states);
        assertTrue(byLabel.get("pinned jar").enabled(), "a pinned JAR enables the peer");
        assertTrue(!byLabel.get("unpinned jar").enabled() && byLabel.get("unpinned jar").state().equals("CONFIGURED"),
                "an unpinned JAR is valid and starts nothing");
        assertEquals(Duration.ofSeconds(MinosIntegrationSettings.MAX_TIMEOUT_SECONDS),
                byLabel.get("timeout at the maximum").timeout());
        assertEquals(Duration.ofSeconds(1), byLabel.get("timeout at the minimum").timeout());
        assertEquals(Duration.ofSeconds(MinosIntegrationSettings.DEFAULT_TIMEOUT_SECONDS),
                byLabel.get("timeout above the maximum").timeout());
        for (String malformed : List.of("jar not a file", "jar not a path", "pin without jar", "pin malformed",
                "pin mismatch", "timeout not a number", "timeout below the minimum", "timeout above the maximum",
                "launcher relative", "launcher not a file")) {
            assertEquals("INVALID", byLabel.get(malformed).state(), () -> malformed + " must be refused");
        }
    }

    private static List<Row> rows(Fixture fixture) {
        String launcher = fixture.runtimeLauncher().toString();
        return List.of(
                Row.properties("absent", Map.of()),
                Row.properties("blank values", Map.of(Setting.JAR, "  ", Setting.JAR_SHA256, " ", Setting.JAVA, "",
                        Setting.HOME, " ", Setting.TIMEOUT, " ")),
                Row.properties("pinned jar", Map.of(Setting.JAR, fixture.jar().toString(),
                        Setting.JAR_SHA256, fixture.pin().toUpperCase(java.util.Locale.ROOT),
                        Setting.HOME, fixture.home().toString(), Setting.TIMEOUT, "37")),
                Row.environment("unpinned jar", Map.of(Setting.JAR, fixture.jar().toString())),
                Row.both("property over environment",
                        Map.of(Setting.JAR, fixture.jar().toString(), Setting.TIMEOUT, "41"),
                        Map.of(Setting.JAR, fixture.missing().toString(), Setting.TIMEOUT, "zero")),
                Row.properties("jar not a file", Map.of(Setting.JAR, fixture.missing().toString())),
                Row.properties("jar not a path", Map.of(Setting.JAR, "jar\u0000name.jar")),
                Row.properties("pin without jar", Map.of(Setting.JAR_SHA256, fixture.pin())),
                Row.properties("pin malformed", Map.of(Setting.JAR, fixture.jar().toString(),
                        Setting.JAR_SHA256, "not-a-digest")),
                Row.properties("pin mismatch", Map.of(Setting.JAR, fixture.jar().toString(),
                        Setting.JAR_SHA256, "0".repeat(64))),
                Row.properties("timeout not a number", Map.of(Setting.TIMEOUT, "soon")),
                Row.properties("timeout below the minimum", Map.of(Setting.TIMEOUT, "0")),
                Row.properties("timeout at the minimum", Map.of(Setting.TIMEOUT, "1")),
                Row.properties("timeout at the maximum",
                        Map.of(Setting.TIMEOUT, Integer.toString(MinosIntegrationSettings.MAX_TIMEOUT_SECONDS))),
                Row.properties("timeout above the maximum",
                        Map.of(Setting.TIMEOUT, Integer.toString(MinosIntegrationSettings.MAX_TIMEOUT_SECONDS + 1))),
                Row.properties("launcher configured", Map.of(Setting.JAR, fixture.jar().toString(),
                        Setting.JAVA, launcher)),
                Row.properties("launcher relative", Map.of(Setting.JAR, fixture.jar().toString(),
                        Setting.JAVA, "java")),
                Row.properties("launcher not a file", Map.of(Setting.JAR, fixture.jar().toString(),
                        Setting.JAVA, fixture.missing().toString())));
    }

    private static Map<Setting, String> names(String jar, String jarSha256, String java, String home, String timeout) {
        Map<Setting, String> names = new EnumMap<>(Setting.class);
        names.put(Setting.JAR, jar);
        names.put(Setting.JAR_SHA256, jarSha256);
        names.put(Setting.JAVA, java);
        names.put(Setting.HOME, home);
        names.put(Setting.TIMEOUT, timeout);
        return names;
    }

    private enum Setting { JAR, JAR_SHA256, JAVA, HOME, TIMEOUT }

    private record Row(String label, Map<Setting, String> properties, Map<Setting, String> environment) {
        static Row properties(String label, Map<Setting, String> properties) {
            return new Row(label, properties, Map.of());
        }

        static Row environment(String label, Map<Setting, String> environment) {
            return new Row(label, Map.of(), environment);
        }

        static Row both(String label, Map<Setting, String> properties, Map<Setting, String> environment) {
            return new Row(label, properties, environment);
        }
    }

    private record Outcome(String state, boolean enabled, Optional<Path> jarPath, String javaCommand,
                           Optional<Path> homeDirectory, Duration timeout, Optional<String> jarSha256,
                           Optional<String> configurationError) {
        Outcome abstractedFrom(String peer) {
            String lower = peer.toLowerCase(java.util.Locale.ROOT);
            return new Outcome(state, enabled, jarPath, javaCommand, homeDirectory, timeout, jarSha256,
                    configurationError.map(error -> error.replace(peer, "<PEER>").replace(lower, "<peer>")));
        }
    }

    private record PeerUnderTest(String name, Class<?> settingsType, Map<Setting, String> properties,
                                 Map<Setting, String> environment,
                                 BiFunction<Map<String, String>, Properties, Outcome> resolver) {
        Outcome resolve(Row row) {
            Properties properties = new Properties();
            row.properties().forEach((setting, value) -> properties.setProperty(properties().get(setting), value));
            Map<String, String> environment = new TreeMap<>();
            row.environment().forEach((setting, value) -> environment.put(environment().get(setting), value));
            return resolver.apply(environment, properties);
        }

        Map<String, String> publicConstants() {
            Map<String, String> constants = new TreeMap<>();
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            Function<String, String> abstracted = value -> value.replace(name, "<PEER>").replace(lower, "<peer>");
            for (Field field : settingsType.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                    continue;
                }
                try {
                    constants.put(field.getName(), abstracted.apply(String.valueOf(field.get(null))));
                } catch (IllegalAccessException unreadable) {
                    throw new AssertionError(settingsType.getName() + "." + field.getName() + " is unreadable",
                            unreadable);
                }
            }
            return constants;
        }
    }

    private record Fixture(Path jar, String pin, Path home, Path missing, Path runtimeLauncher) {
        static Fixture create(Path directory) throws IOException {
            Path jar = Files.write(directory.resolve("peer.jar"), new byte[]{1, 2, 3, 4});
            Path home = Files.createDirectories(directory.resolve("home"));
            Path bin = Path.of(System.getProperty("java.home"), "bin");
            Path launcher = Files.isRegularFile(bin.resolve("java.exe")) ? bin.resolve("java.exe")
                    : bin.resolve("java");
            return new Fixture(jar, ExternalJarIntegrity.sha256(jar), home, directory.resolve("missing.jar"),
                    launcher);
        }
    }
}
