package com.morpheus.application.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves the launch settings of an external MCP peer from the JVM properties and the environment, alike for every
 * peer. A configuration problem never fails the resolution: it is joined into {@code configurationError}, the peer is
 * invalid, and MORPHEUS starts. Third-party code runs only from a JAR verified against its pin.
 */
public final class PeerLaunchSettingsResolver {
    private PeerLaunchSettingsResolver() {
    }

    /**
     * A peer, named by what differs between peers: its name in messages, and the prefixes of its JVM properties and
     * of its environment variables. Every setting name derives from these by one pattern.
     */
    public record Peer(String name, String propertyPrefix, String environmentPrefix) {
        public Peer {
            name = requireText(name, "name");
            propertyPrefix = requireText(propertyPrefix, "propertyPrefix");
            environmentPrefix = requireText(environmentPrefix, "environmentPrefix");
        }

        /**
         * The peer of that name, with the prefixes every MORPHEUS peer has: {@code morpheus.<peer>} for its JVM
         * properties, {@code MORPHEUS_<PEER>} for its environment variables (ADR-0128).
         */
        public static Peer named(String name) {
            String upper = requireText(name, "name").toUpperCase(Locale.ROOT);
            return new Peer(upper, "morpheus." + upper.toLowerCase(Locale.ROOT), "MORPHEUS_" + upper);
        }

        public String jarProperty() {
            return propertyPrefix + ".jar";
        }

        public String jarSha256Property() {
            return propertyPrefix + ".jar.sha256";
        }

        public String javaProperty() {
            return propertyPrefix + ".java";
        }

        public String homeProperty() {
            return propertyPrefix + ".home";
        }

        public String timeoutProperty() {
            return propertyPrefix + ".timeoutSeconds";
        }

        public String jarEnvironment() {
            return environmentPrefix + "_JAR";
        }

        public String jarSha256Environment() {
            return environmentPrefix + "_JAR_SHA256";
        }

        public String javaEnvironment() {
            return environmentPrefix + "_JAVA";
        }

        public String homeEnvironment() {
            return environmentPrefix + "_HOME";
        }

        public String timeoutEnvironment() {
            return environmentPrefix + "_TIMEOUT_SECONDS";
        }
    }

    public static PeerLaunchSettings resolve(Peer peer, Map<String, String> environment, Properties properties) {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(properties, "properties");
        String name = peer.name();

        String rawJar = value(properties, peer.jarProperty(), environment, peer.jarEnvironment()).orElse(null);
        String rawJarSha256 = value(properties, peer.jarSha256Property(), environment, peer.jarSha256Environment())
                .orElse(null);
        Optional<String> rawJava = value(properties, peer.javaProperty(), environment, peer.javaEnvironment());
        String rawHome = value(properties, peer.homeProperty(), environment, peer.homeEnvironment()).orElse(null);
        String rawTimeout = value(properties, peer.timeoutProperty(), environment, peer.timeoutEnvironment())
                .orElse(Integer.toString(PeerLaunchSettings.DEFAULT_TIMEOUT_SECONDS));

        List<String> errors = new ArrayList<>();
        Optional<Path> jar = Optional.empty();
        Optional<String> jarSha256 = Optional.empty();
        Optional<Path> home = Optional.empty();

        try {
            if (rawJar != null) {
                Path candidate = PeerLaunchSettings.normalize(Path.of(rawJar));
                if (!Files.isRegularFile(candidate)) {
                    errors.add(name + " JAR is not a file: " + candidate);
                } else if (rawJarSha256 != null) {
                    try {
                        jar = Optional.of(ExternalJarIntegrity.verifySha256(candidate, rawJarSha256));
                        jarSha256 = Optional.of(ExternalJarIntegrity.normalizeSha256(rawJarSha256));
                    } catch (IllegalArgumentException integrityFailure) {
                        errors.add(name + " JAR integrity verification failed: " + integrityFailure.getMessage());
                    }
                } else {
                    jar = Optional.of(candidate);
                }
            } else if (rawJarSha256 != null) {
                errors.add(name + " JAR SHA-256 pin requires " + peer.jarEnvironment() + "/" + peer.jarProperty());
            }
            if (rawHome != null) {
                home = Optional.of(PeerLaunchSettings.normalize(Path.of(rawHome)));
            }
        } catch (InvalidPathException failure) {
            errors.add("invalid " + name + " path: " + failure.getInput());
        }

        String javaCommand = launcher(name, rawJava, rawJar != null, errors);
        int timeoutSeconds = timeoutSeconds(name, rawTimeout, errors);

        return new PeerLaunchSettings(
                jar, javaCommand, home, Duration.ofSeconds(timeoutSeconds), jarSha256,
                errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors)));
    }

    /** A value out of bounds or not a number is refused, and the default bound is the one retained. */
    private static int timeoutSeconds(String name, String rawTimeout, List<String> errors) {
        int timeoutSeconds;
        try {
            timeoutSeconds = Integer.parseInt(rawTimeout.trim());
        } catch (NumberFormatException failure) {
            errors.add("invalid " + name + " timeout: " + rawTimeout);
            return PeerLaunchSettings.DEFAULT_TIMEOUT_SECONDS;
        }
        if (timeoutSeconds < 1 || timeoutSeconds > PeerLaunchSettings.MAX_TIMEOUT_SECONDS) {
            errors.add(name + " timeout must be between 1 and " + PeerLaunchSettings.MAX_TIMEOUT_SECONDS
                    + " seconds");
            return PeerLaunchSettings.DEFAULT_TIMEOUT_SECONDS;
        }
        return timeoutSeconds;
    }

    private static Optional<String> value(
            Properties properties, String propertyName, Map<String, String> environment, String environmentName) {
        String property = properties.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return Optional.of(property.trim());
        }
        String env = environment.get(environmentName);
        return env == null || env.isBlank() ? Optional.empty() : Optional.of(env.trim());
    }

    /**
     * The launcher the peer is started with. A refusal is added to {@code errors} and the launcher of the runtime is
     * kept, so that the settings stay well formed while they are invalid.
     */
    private static String launcher(String name, Optional<String> configured, boolean peerConfigured,
                                   List<String> errors) {
        String runtime = runtimeLauncher();
        if (configured.isEmpty()) {
            if (peerConfigured && !Files.isRegularFile(Path.of(runtime))) {
                errors.add(name + " Java command is not configured and the MORPHEUS runtime carries no launcher");
            }
            return runtime;
        }
        try {
            return configuredLauncher(configured.orElseThrow());
        } catch (InvalidPathException failure) {
            errors.add(name + " Java command must be an absolute path");
        } catch (IllegalArgumentException refused) {
            errors.add(name + " Java command " + refused.getMessage());
        } catch (IOException unresolved) {
            errors.add(name + " Java command is not an executable file");
        }
        return runtime;
    }

    /**
     * The launcher of the runtime this JVM runs on. A bare command would be looked up on the search path of the
     * MORPHEUS process, where another executable of that name can be placed first.
     */
    private static String runtimeLauncher() {
        Path bin = Path.of(System.getProperty("java.home")).resolve("bin");
        Path windowsLauncher = bin.resolve("java.exe");
        return (Files.isRegularFile(windowsLauncher) ? windowsLauncher : bin.resolve("java"))
                .toAbsolutePath().normalize().toString();
    }

    /**
     * A configured launcher names its file in full. A link is resolved rather than refused -- the usual launcher of a
     * Linux system is a chain of links -- and the real file is what is launched and reported. On Windows the launcher
     * is commonly written without its {@code .exe}, which the operating system adds when it searches: it is added
     * here.
     */
    private static String configuredLauncher(String configured) throws IOException {
        Path launcher = Path.of(configured);
        if (!launcher.isAbsolute()) {
            throw new IllegalArgumentException("must be an absolute path");
        }
        if (!Files.isRegularFile(launcher) && launcher.getFileName() != null) {
            Path windowsLauncher = launcher.resolveSibling(launcher.getFileName() + ".exe");
            if (Files.isRegularFile(windowsLauncher)) launcher = windowsLauncher;
        }
        if (!Files.isRegularFile(launcher) || !Files.isExecutable(launcher)) {
            throw new IllegalArgumentException("is not an executable file");
        }
        return launcher.toRealPath().toString();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
