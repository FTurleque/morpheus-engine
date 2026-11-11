package com.morpheus.application.security;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * The launch settings of an external MCP peer: its JAR and SHA-256 pin, the launcher it is started with, its home
 * directory and the bound of each request. A record that breaks an invariant cannot be built, so the settings type of
 * each integration, which is built around this one, cannot either.
 */
public record PeerLaunchSettings(
        Optional<Path> jarPath,
        String javaCommand,
        Optional<Path> homeDirectory,
        Duration timeout,
        Optional<String> jarSha256,
        Optional<String> configurationError) {

    public static final int DEFAULT_TIMEOUT_SECONDS = 20;
    public static final int MAX_TIMEOUT_SECONDS = 120;

    public PeerLaunchSettings {
        jarPath = Objects.requireNonNull(jarPath, "jarPath").map(PeerLaunchSettings::normalize);
        javaCommand = requireAbsoluteLauncher(requireNonBlank(javaCommand, "javaCommand"));
        homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory").map(PeerLaunchSettings::normalize);
        Objects.requireNonNull(timeout, "timeout");
        jarSha256 = Objects.requireNonNull(jarSha256, "jarSha256").map(ExternalJarIntegrity::normalizeSha256);
        configurationError = Objects.requireNonNull(configurationError, "configurationError")
                .map(String::trim).filter(value -> !value.isEmpty());
        long seconds = timeout.toSeconds();
        if (seconds < 1 || seconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("timeout must be between 1 and " + MAX_TIMEOUT_SECONDS + " seconds");
        }
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

    /** A bare or relative command would be looked up on the search path when the peer is started. */
    private static String requireAbsoluteLauncher(String javaCommand) {
        try {
            if (Path.of(javaCommand).isAbsolute()) return javaCommand;
        } catch (InvalidPathException malformed) {
            throw new IllegalArgumentException("javaCommand must be an absolute path", malformed);
        }
        throw new IllegalArgumentException("javaCommand must be an absolute path");
    }

    static Path normalize(Path path) {
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
