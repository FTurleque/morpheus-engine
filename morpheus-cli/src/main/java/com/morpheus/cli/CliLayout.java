package com.morpheus.cli;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Cross-platform runtime layout with explicit CLI/env overrides and deterministic production defaults. */
public record CliLayout(
        Path dataDirectory,
        Path configDirectory,
        Path logsDirectory,
        Path backupsDirectory,
        Path databasePath) {
    public CliLayout {
        dataDirectory = absolute(dataDirectory, "dataDirectory");
        configDirectory = absolute(configDirectory, "configDirectory");
        logsDirectory = absolute(logsDirectory, "logsDirectory");
        backupsDirectory = absolute(backupsDirectory, "backupsDirectory");
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
        boolean windows = properties.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

        Optional<Path> envData = envPath(environment, "MORPHEUS_DATA_DIR");
        Optional<Path> envConfig = envPath(environment, "MORPHEUS_CONFIG_DIR");
        Optional<Path> envDb = envPath(environment, "MORPHEUS_DB");
        Optional<Path> envLogs = envPath(environment, "MORPHEUS_LOGS_DIR");
        Optional<Path> envBackups = envPath(environment, "MORPHEUS_BACKUPS_DIR");

        Path data;
        Path config;
        Path logs;
        Path backups;

        if (explicitData.isPresent()) {
            data = explicitData.orElseThrow();
            config = explicitConfig.or(() -> envConfig).orElse(data.resolve("config"));
            logs = envLogs.orElse(data.resolve("logs"));
            backups = envBackups.orElse(data.resolve("backups"));
        } else if (envData.isPresent()) {
            data = envData.orElseThrow();
            config = explicitConfig.or(() -> envConfig).orElse(data.resolve("config"));
            logs = envLogs.orElse(data.resolve("logs"));
            backups = envBackups.orElse(data.resolve("backups"));
        } else if (windows) {
            Path productRoot = envPath(environment, "LOCALAPPDATA")
                    .orElse(home.resolve("AppData/Local"))
                    .resolve("MORPHEUS");
            data = productRoot.resolve("data");
            config = explicitConfig.or(() -> envConfig).orElse(productRoot.resolve("config"));
            logs = envLogs.orElse(productRoot.resolve("logs"));
            backups = envBackups.orElse(productRoot.resolve("backups"));
        } else {
            data = envPath(environment, "XDG_DATA_HOME")
                    .orElse(home.resolve(".local/share"))
                    .resolve("morpheus");
            config = explicitConfig.or(() -> envConfig).orElseGet(() -> envPath(environment, "XDG_CONFIG_HOME")
                    .orElse(home.resolve(".config"))
                    .resolve("morpheus"));
            Path stateRoot = envPath(environment, "XDG_STATE_HOME")
                    .orElse(home.resolve(".local/state"))
                    .resolve("morpheus");
            logs = envLogs.orElse(stateRoot.resolve("logs"));
            backups = envBackups.orElse(stateRoot.resolve("backups"));
        }

        Path database = explicitDatabase.or(() -> envDb).orElse(data.resolve("morpheus.db"));
        return new CliLayout(data, config, logs, backups, database);
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
