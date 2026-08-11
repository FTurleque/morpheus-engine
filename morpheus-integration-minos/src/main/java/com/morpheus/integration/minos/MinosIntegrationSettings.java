package com.morpheus.integration.minos;

import com.morpheus.application.security.ExternalJarIntegrity;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Resolves optional MINOS runtime settings without making configuration fatal to MORPHEUS bootstrap. */
public record MinosIntegrationSettings(
        Optional<Path> jarPath,
        String javaCommand,
        Optional<Path> homeDirectory,
        Duration timeout,
        Optional<String> configurationError) {

    public static final String JAR_PROPERTY = "morpheus.minos.jar";
    public static final String JAR_SHA256_PROPERTY = "morpheus.minos.jar.sha256";
    public static final String JAVA_PROPERTY = "morpheus.minos.java";
    public static final String HOME_PROPERTY = "morpheus.minos.home";
    public static final String TIMEOUT_PROPERTY = "morpheus.minos.timeoutSeconds";

    public static final String JAR_ENV = "MORPHEUS_MINOS_JAR";
    public static final String JAR_SHA256_ENV = "MORPHEUS_MINOS_JAR_SHA256";
    public static final String JAVA_ENV = "MORPHEUS_MINOS_JAVA";
    public static final String HOME_ENV = "MORPHEUS_MINOS_HOME";
    public static final String TIMEOUT_ENV = "MORPHEUS_MINOS_TIMEOUT_SECONDS";

    public static final int DEFAULT_TIMEOUT_SECONDS = 20;
    public static final int MAX_TIMEOUT_SECONDS = 120;

    public MinosIntegrationSettings {
        jarPath = Objects.requireNonNull(jarPath, "jarPath").map(MinosIntegrationSettings::normalize);
        javaCommand = requireNonBlank(javaCommand, "javaCommand");
        homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory").map(MinosIntegrationSettings::normalize);
        Objects.requireNonNull(timeout, "timeout");
        configurationError = Objects.requireNonNull(configurationError, "configurationError")
                .map(String::trim).filter(value -> !value.isEmpty());
        long seconds = timeout.toSeconds();
        if (seconds < 1 || seconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("timeout must be between 1 and " + MAX_TIMEOUT_SECONDS + " seconds");
        }
    }

    public static MinosIntegrationSettings resolve(Map<String, String> environment, Properties properties) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(properties, "properties");

        String rawJar = value(properties, JAR_PROPERTY, environment, JAR_ENV).orElse(null);
        String rawJarSha256 = value(properties, JAR_SHA256_PROPERTY, environment, JAR_SHA256_ENV).orElse(null);
        String javaCommand = value(properties, JAVA_PROPERTY, environment, JAVA_ENV).orElse("java");
        String rawHome = value(properties, HOME_PROPERTY, environment, HOME_ENV).orElse(null);
        String rawTimeout = value(properties, TIMEOUT_PROPERTY, environment, TIMEOUT_ENV)
                .orElse(Integer.toString(DEFAULT_TIMEOUT_SECONDS));

        Optional<String> error = Optional.empty();
        Optional<Path> jar = Optional.empty();
        Optional<Path> home = Optional.empty();
        int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

        try {
            if (rawJar != null) {
                Path candidate = normalize(Path.of(rawJar));
                if (!Files.isRegularFile(candidate)) {
                    error = Optional.of("MINOS JAR is not a file: " + candidate);
                } else if (rawJarSha256 != null) {
                    try {
                        jar = Optional.of(ExternalJarIntegrity.verifySha256(candidate, rawJarSha256));
                    } catch (IllegalArgumentException integrityFailure) {
                        error = Optional.of("MINOS JAR integrity verification failed: " + integrityFailure.getMessage());
                    }
                } else {
                    jar = Optional.of(candidate);
                }
            } else if (rawJarSha256 != null) {
                error = Optional.of("MINOS JAR SHA-256 pin requires MORPHEUS_MINOS_JAR/morpheus.minos.jar");
            }
            if (rawHome != null) {
                home = Optional.of(normalize(Path.of(rawHome)));
            }
        } catch (InvalidPathException failure) {
            error = Optional.of("invalid MINOS path: " + failure.getInput());
        }

        if (javaCommand == null || javaCommand.isBlank()) {
            error = Optional.of("MINOS Java command must not be blank");
            javaCommand = "java";
        }

        try {
            timeoutSeconds = Integer.parseInt(rawTimeout.trim());
            if (timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
                error = Optional.of("MINOS timeout must be between 1 and " + MAX_TIMEOUT_SECONDS + " seconds");
                timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
            }
        } catch (RuntimeException failure) {
            error = Optional.of("invalid MINOS timeout: " + rawTimeout);
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }

        return new MinosIntegrationSettings(jar, javaCommand, home, Duration.ofSeconds(timeoutSeconds), error);
    }

    public State state() {
        if (configurationError.isPresent()) {
            return State.INVALID;
        }
        return jarPath.isPresent() ? State.CONFIGURED : State.DISABLED;
    }

    public boolean enabled() {
        return state() == State.CONFIGURED;
    }

    public Map<String, String> processEnvironment() {
        return homeDirectory.map(path -> Map.of("MINOS_HOME", path.toString())).orElseGet(Map::of);
    }

    private static Optional<String> value(
            Properties properties,
            String propertyName,
            Map<String, String> environment,
            String environmentName) {
        String property = properties.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return Optional.of(property.trim());
        }
        String env = environment.get(environmentName);
        return env == null || env.isBlank() ? Optional.empty() : Optional.of(env.trim());
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    public enum State {
        DISABLED,
        CONFIGURED,
        INVALID
    }
}
