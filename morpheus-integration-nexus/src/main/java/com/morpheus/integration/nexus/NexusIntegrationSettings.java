package com.morpheus.integration.nexus;

import com.morpheus.application.security.ExternalJarIntegrity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Resolves optional NEXUS runner settings without making configuration fatal to MORPHEUS bootstrap. */
public record NexusIntegrationSettings(
        Optional<Path> jarPath,
        String javaCommand,
        Optional<Path> homeDirectory,
        Duration timeout,
        Optional<String> jarSha256,
        Optional<String> configurationError) {

    public static final String JAR_PROPERTY = "morpheus.nexus.jar";
    public static final String JAR_SHA256_PROPERTY = "morpheus.nexus.jar.sha256";
    public static final String JAVA_PROPERTY = "morpheus.nexus.java";
    public static final String HOME_PROPERTY = "morpheus.nexus.home";
    public static final String TIMEOUT_PROPERTY = "morpheus.nexus.timeoutSeconds";

    public static final String JAR_ENV = "MORPHEUS_NEXUS_JAR";
    public static final String JAR_SHA256_ENV = "MORPHEUS_NEXUS_JAR_SHA256";
    public static final String JAVA_ENV = "MORPHEUS_NEXUS_JAVA";
    public static final String HOME_ENV = "MORPHEUS_NEXUS_HOME";
    public static final String TIMEOUT_ENV = "MORPHEUS_NEXUS_TIMEOUT_SECONDS";

    public static final int DEFAULT_TIMEOUT_SECONDS = 20;
    public static final int MAX_TIMEOUT_SECONDS = 120;

    public NexusIntegrationSettings(
            Optional<Path> jarPath,
            String javaCommand,
            Optional<Path> homeDirectory,
            Duration timeout,
            Optional<String> configurationError) {
        this(jarPath, javaCommand, homeDirectory, timeout, Optional.empty(), configurationError);
    }

    public NexusIntegrationSettings {
        jarPath = Objects.requireNonNull(jarPath, "jarPath").map(NexusIntegrationSettings::normalize);
        javaCommand = requireAbsoluteLauncher(requireText(javaCommand, "javaCommand"));
        homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory").map(NexusIntegrationSettings::normalize);
        Objects.requireNonNull(timeout, "timeout");
        jarSha256 = Objects.requireNonNull(jarSha256, "jarSha256")
                .map(ExternalJarIntegrity::normalizeSha256);
        configurationError = Objects.requireNonNull(configurationError, "configurationError")
                .map(String::trim).filter(value -> !value.isEmpty());
        long seconds = timeout.toSeconds();
        if (seconds < 1 || seconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("timeout must be between 1 and " + MAX_TIMEOUT_SECONDS + " seconds");
        }
    }

    public static NexusIntegrationSettings resolve(Map<String, String> environment, Properties properties) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(properties, "properties");
        String rawJar = value(properties, JAR_PROPERTY, environment, JAR_ENV).orElse(null);
        String rawJarSha256 = value(properties, JAR_SHA256_PROPERTY, environment, JAR_SHA256_ENV).orElse(null);
        Optional<String> rawJava = value(properties, JAVA_PROPERTY, environment, JAVA_ENV);
        String javaCommand = runtimeLauncher();
        String rawHome = value(properties, HOME_PROPERTY, environment, HOME_ENV).orElse(null);
        String rawTimeout = value(properties, TIMEOUT_PROPERTY, environment, TIMEOUT_ENV)
                .orElse(Integer.toString(DEFAULT_TIMEOUT_SECONDS));

        List<String> errors = new ArrayList<>();
        Optional<Path> jar = Optional.empty();
        Optional<String> jarSha256 = Optional.empty();
        Optional<Path> home = Optional.empty();
        int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        try {
            if (rawJar != null) {
                Path candidate = normalize(Path.of(rawJar));
                if (!Files.isRegularFile(candidate)) {
                    errors.add("NEXUS runner JAR is not a file: " + candidate);
                } else if (rawJarSha256 != null) {
                    try {
                        jar = Optional.of(ExternalJarIntegrity.verifySha256(candidate, rawJarSha256));
                        jarSha256 = Optional.of(ExternalJarIntegrity.normalizeSha256(rawJarSha256));
                    } catch (IllegalArgumentException integrityFailure) {
                        errors.add("NEXUS JAR integrity verification failed: " + integrityFailure.getMessage());
                    }
                } else {
                    jar = Optional.of(candidate);
                }
            } else if (rawJarSha256 != null) {
                errors.add("NEXUS JAR SHA-256 pin requires MORPHEUS_NEXUS_JAR/morpheus.nexus.jar");
            }
            if (rawHome != null) {
                home = Optional.of(normalize(Path.of(rawHome)));
            }
        } catch (InvalidPathException failure) {
            errors.add("invalid NEXUS path: " + failure.getInput());
        }
        if (rawJava.isPresent()) {
            try {
                javaCommand = configuredLauncher(rawJava.orElseThrow());
            } catch (InvalidPathException failure) {
                errors.add("NEXUS Java command must be an absolute path");
            } catch (IllegalArgumentException refused) {
                errors.add("NEXUS Java command " + refused.getMessage());
            } catch (IOException unresolved) {
                errors.add("NEXUS Java command is not an executable file");
            }
        }
        if (rawJava.isEmpty() && rawJar != null && !Files.isRegularFile(Path.of(javaCommand))) {
            errors.add("NEXUS Java command is not configured and the MORPHEUS runtime carries no launcher");
        }

        try {
            timeoutSeconds = Integer.parseInt(rawTimeout.trim());
            if (timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
                errors.add("NEXUS timeout must be between 1 and " + MAX_TIMEOUT_SECONDS + " seconds");
                timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
            }
        } catch (RuntimeException failure) {
            errors.add("invalid NEXUS timeout: " + rawTimeout);
        }
        return new NexusIntegrationSettings(
                jar, javaCommand, home, Duration.ofSeconds(timeoutSeconds), jarSha256,
                errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors)));
    }

    public State state() {
        if (configurationError.isPresent()) {
            return State.INVALID;
        }
        return jarPath.isPresent() ? State.CONFIGURED : State.DISABLED;
    }

    /** External code execution is enabled only when the configured JAR is integrity-pinned. */
    public boolean enabled() {
        return state() == State.CONFIGURED && jarSha256.isPresent();
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
     * is commonly written without its {@code .exe}, which the operating system used to add: it is added here.
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

    /** A bare or relative command would be looked up on the search path when the peer is started. */
    private static String requireAbsoluteLauncher(String javaCommand) {
        try {
            if (Path.of(javaCommand).isAbsolute()) return javaCommand;
        } catch (InvalidPathException malformed) {
            throw new IllegalArgumentException("javaCommand must be an absolute path", malformed);
        }
        throw new IllegalArgumentException("javaCommand must be an absolute path");
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    public enum State { DISABLED, CONFIGURED, INVALID }
}
