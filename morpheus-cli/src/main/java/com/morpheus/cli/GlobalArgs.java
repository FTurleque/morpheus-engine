package com.morpheus.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared {@code --json}/{@code --data-dir}/{@code --config-dir}/{@code --db} global-flag parsing.
 *
 * <p>Every CLI adapter recognizes the same four global flags ahead of its own command-specific
 * arguments; this is the single implementation they all delegate to.</p>
 */
final class GlobalArgs {
    private GlobalArgs() {
    }

    /** Returns the first non-global-flag token, or {@code ""} if none is present. */
    static String command(String[] args) {
        int index = 0;
        while (index < args.length) {
            String token = args[index];
            index++;
            boolean isValueFlag = isValueFlag(token);
            if (isValueFlag) {
                index++;
            }
            if (!"--json".equals(token) && !isValueFlag) {
                return token;
            }
        }
        return "";
    }

    static Parsed parse(String[] args) {
        boolean json = false;
        Optional<Path> data = Optional.empty();
        Optional<Path> config = Optional.empty();
        Optional<Path> database = Optional.empty();
        List<String> remaining = new ArrayList<>();
        int index = 0;
        while (index < args.length) {
            String token = args[index];
            index++;
            switch (token) {
                case "--json" -> json = true;
                case "--data-dir" -> {
                    data = Optional.of(Path.of(requireValue(args, index, token)));
                    index++;
                }
                case "--config-dir" -> {
                    config = Optional.of(Path.of(requireValue(args, index, token)));
                    index++;
                }
                case "--db" -> {
                    database = Optional.of(Path.of(requireValue(args, index, token)));
                    index++;
                }
                default -> remaining.add(token);
            }
        }
        return new Parsed(json, data, config, database, List.copyOf(remaining));
    }

    private static boolean isValueFlag(String token) {
        return "--data-dir".equals(token) || "--config-dir".equals(token) || "--db".equals(token);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    record Parsed(
            boolean json,
            Optional<Path> dataDirectory,
            Optional<Path> configDirectory,
            Optional<Path> databasePath,
            List<String> remaining) {
    }
}
