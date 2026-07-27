package com.morpheus.cli;

import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.product.UpdateCheckResult;
import com.morpheus.application.product.UpdateDiscoveryService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Product-integrity commands that never require opening the MORPHEUS knowledge store. */
final class MorpheusProductCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    static boolean handles(String[] args) {
        String command = command(args);
        return command.equals("version")
                || command.equals("--version")
                || command.equals("product-info")
                || command.equals("update-check");
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        try {
            Parsed parsed = parse(args);
            return switch (parsed.command()) {
                case "version", "--version" -> version(parsed.json(), out);
                case "product-info" -> productInfo(parsed.json(), out);
                case "update-check" -> updateCheck(parsed, out);
                default -> throw new IllegalArgumentException("unknown product command: " + parsed.command());
            };
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS usage error: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS product-integrity error: " + safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private int version(boolean jsonOutput, PrintStream out) {
        String version = ProductMetadata.version();
        if (jsonOutput) {
            out.println(json.toJson(new VersionView(version)));
        } else {
            out.println("MORPHEUS " + version);
        }
        return CliExitCode.SUCCESS.code();
    }

    private int productInfo(boolean jsonOutput, PrintStream out) {
        ProductMetadata.ProductInfo info = ProductMetadata.current();
        if (jsonOutput) {
            out.println(json.toJson(info));
        } else {
            out.println("name=" + info.name());
            out.println("version=" + info.version());
            out.println("apiVersion=" + info.apiVersion());
            out.println("updateChannel=" + info.updateChannel());
        }
        return CliExitCode.SUCCESS.code();
    }

    private int updateCheck(Parsed parsed, PrintStream out) {
        String manifest = parsed.option("manifest");
        URI manifestUri = explicitUri(manifest);
        UpdateCheckResult result = new UpdateDiscoveryService().check(manifestUri);
        if (parsed.json()) {
            out.println(json.toJson(result));
        } else {
            out.println("currentVersion=" + result.currentVersion());
            out.println("availableVersion=" + result.availableVersion());
            out.println("channel=" + result.channel());
            out.println("updateAvailable=" + result.updateAvailable());
            out.println("artifactUri=" + result.artifactUri());
            out.println("sha256=" + result.sha256());
            out.println("manifestUri=" + result.manifestUri());
            out.println("action=none (discovery is read-only; MORPHEUS never auto-installs updates)");
        }
        return CliExitCode.SUCCESS.code();
    }

    private static Parsed parse(String[] args) {
        boolean json = false;
        String command = "";
        List<String> tokens = new ArrayList<>(Arrays.asList(args));
        java.util.Map<String, String> options = new java.util.LinkedHashMap<>();
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token.equals("--json")) {
                json = true;
                continue;
            }
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                index = requireValue(tokens, index, token);
                continue;
            }
            if (command.isEmpty()) {
                command = token;
                continue;
            }
            if (token.equals("--manifest")) {
                int valueIndex = requireValue(tokens, index, token);
                options.put("manifest", tokens.get(valueIndex));
                index = valueIndex;
                continue;
            }
            throw new IllegalArgumentException("unknown option for " + command + ": " + token);
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("missing product command");
        }
        if (command.equals("update-check") && !options.containsKey("manifest")) {
            throw new IllegalArgumentException("update-check requires --manifest URI_OR_PATH");
        }
        if (!command.equals("update-check") && !options.isEmpty()) {
            throw new IllegalArgumentException(command + " does not accept --manifest");
        }
        return new Parsed(command, json, java.util.Map.copyOf(options));
    }

    private static int requireValue(List<String> tokens, int optionIndex, String option) {
        int valueIndex = optionIndex + 1;
        if (valueIndex >= tokens.size() || tokens.get(valueIndex).startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return valueIndex;
    }

    private static String command(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--json")) {
                continue;
            }
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                index++;
                continue;
            }
            return token;
        }
        return "";
    }

    private static URI explicitUri(String raw) {
        String value = Objects.requireNonNull(raw, "manifest").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("manifest must not be blank");
        }
        if (value.matches("^[A-Za-z]:[\\\\/].*")) {
            return Path.of(value).toAbsolutePath().normalize().toUri();
        }
        URI parsed = URI.create(value);
        if (parsed.isAbsolute()) {
            return parsed;
        }
        return Path.of(value).toAbsolutePath().normalize().toUri();
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record Parsed(String command, boolean json, java.util.Map<String, String> options) {
        String option(String name) {
            String value = options.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing required option --" + name);
            }
            return value;
        }
    }

    private record VersionView(String version) {
    }
}
