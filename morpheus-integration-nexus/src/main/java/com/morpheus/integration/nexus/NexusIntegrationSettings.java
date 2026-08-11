package com.morpheus.integration.nexus;

import com.morpheus.application.security.ExternalJarIntegrity;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
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
        javaCommand = requireText(javaCommand, "javaCommand");
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
        String javaCommand = value(properties, JAVA_PROPERTY, environment, JAVA_ENV).orElse("java");
        String rawHome = value(properties, HOME_PROPERTY, environment, HOME_ENV).orElse(null);
        String rawTimeout = value(properties, TIMEOUT_PROPERTY, environment, TIMEOUT_ENV)
                .orElse(Integer.toString(DEFAULT_TIMEOUT_SECONDS));

        Optional<String> error = Optional.empty();
        Optional<Path> jar = Optional.empty();
        Optional<String> jarSha256 = Optional.empty();
        Optional<Path> home = Optional.empty();
        int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        try {
            if (rawJar != null) {
                Path candidate = normalize(Path.of(rawJar));
                if (!Files.isRegularFile(candidate)) {
                    error = Optional.of("NEXUS runner JAR is not a file: " + candidate);
                } else if (rawJarSha256 != null) {
                    try {
                        jar = Optional.of(ExternalJarIntegrity.verifySha256(candidate, rawJarSha256));
                        jarSha256 = Optional.of(ExternalJarIntegrity.normalizeSha256(rawJarSha256));
                    } catch (IllegalArgumentException integrityFailure) {
                        error = Optional.of("NEXUS JAR integrity verification failed: " + integrityFailure.getMessage());
                    }
                } else {
                    jar = Optional.of(candidate);
                }
            } else if (rawJarSha256 != null) {
                error = Optional.of("NEXUS JAR SHA-256 pin requires MORPHEUS_NEXUS_JAR/morpheus.nexus.jar");
            }
            if (rawHome != null) {
                home = Optional.of(normalize(Path.of(rawHome)));
            }
        } catch (InvalidPathException failure) {
            error = Optional.of("invalid NEXUS path: " + failure.getInput());
        }
        if (javaCommand == null || javaCommand.isBlank()) {
            error = Optional.of("NEXUS Java command must not be blank");
            javaCommand = "java";
        }
        try {
            timeoutSeconds = Integer.parseInt(rawTimeout.trim());
            if (timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
                error = Optional.of("NEXUS timeout must be between 1 and " + MAX_TIMEOUT_SECONDS + " seconds");
                timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
            }
        } catch (RuntimeException failure) {
            error = Optional.of("invalid NEXUS timeout: " + rawTimeout);
        }
        return new NexusIntegrationSettings(
                jar, javaCommand, home, Duration.ofSeconds(timeoutSeconds), jarSha256, error);
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

    private static Optional<String> value(
            Properties properties, String propertyName, Map<String, String> environment, String environmentName) {
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
