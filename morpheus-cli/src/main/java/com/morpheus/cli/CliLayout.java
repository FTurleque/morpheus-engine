package com.morpheus.cli;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Cross-platform runtime layout with explicit CLI/env overrides and deterministic defaults. */
public record CliLayout(Path dataDirectory, Path configDirectory, Path logsDirectory, Path databasePath) {
    public CliLayout {
        dataDirectory = absolute(dataDirectory, "dataDirectory");
        configDirectory = absolute(configDirectory, "configDirectory");
        logsDirectory = absolute(logsDirectory, "logsDirectory");
        databasePath = absolute(databasePath, "databasePath");
    }

    public static CliLayout resolve(
            Optional<Path> explicitData,
            Optional<Path> explicitConfig,
            Optional<Path> explicitDatabase,
            Map<String, String> environment,
            Properties properties) {
        Objects.requireNonNull(explicitData, "explicitData");
        Objects.requireNonNull(explicitConfig, "explicitConfig");
        Objects.requireNonNull(explicitDatabase, "explicitDatabase");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(properties, "properties");

        Path home = Path.of(properties.getProperty("user.home", "."));
        boolean windows = properties.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");

        Optional<Path> envData = envPath(environment, "MORPHEUS_DATA_DIR");
        Optional<Path> envConfig = envPath(environment, "MORPHEUS_CONFIG_DIR");
        Optional<Path> envDb = envPath(environment, "MORPHEUS_DB");

        Path data = explicitData.or(() -> envData).orElseGet(() -> windows
                ? envPath(environment, "LOCALAPPDATA").orElse(home.resolve("AppData/Local")).resolve("Morpheus")
                : envPath(environment, "XDG_DATA_HOME").orElse(home.resolve(".local/share")).resolve("morpheus"));

        Path config;
        if (explicitData.isPresent() && explicitConfig.isEmpty() && envConfig.isEmpty()) {
            config = data.resolve("config");
        } else {
            config = explicitConfig.or(() -> envConfig).orElseGet(() -> windows
                    ? envPath(environment, "APPDATA").orElse(home.resolve("AppData/Roaming")).resolve("Morpheus")
                    : envPath(environment, "XDG_CONFIG_HOME").orElse(home.resolve(".config")).resolve("morpheus"));
        }
        Path database = explicitDatabase.or(() -> envDb).orElse(data.resolve("morpheus.db"));
        return new CliLayout(data, config, data.resolve("logs"), database);
    }

    private static Optional<Path> envPath(Map<String, String> environment, String key) {
        return Optional.ofNullable(environment.get(key))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Path::of);
    }

    private static Path absolute(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}