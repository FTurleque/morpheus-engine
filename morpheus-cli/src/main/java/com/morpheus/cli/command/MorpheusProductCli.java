package com.morpheus.cli.command;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.product.UpdateCheckResult;
import com.morpheus.application.product.UpdateDiscoveryService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Product-integrity commands that never require opening the MORPHEUS knowledge store. */
public final class MorpheusProductCli {
    private static final String OPT_MANIFEST = "manifest";
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final OperationalRecorder recorder;

    public MorpheusProductCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        String command = GlobalArgs.command(args);
        return command.equals("reason")
                || command.equals("version")
                || command.equals("--version")
                || command.equals("product-info")
                || command.equals("update-check");
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        if (GlobalArgs.command(args).equals("reason")) {
            return new MorpheusReasoningCli(recorder).run(args, out, err);
        }
        try {
            Parsed parsed = parse(args);
            return switch (parsed.command()) {
                case "version", "--version" -> version(parsed.json(), out);
                case "product-info" -> productInfo(parsed.json(), out);
                case "update-check" -> updateCheck(parsed, out);
                default -> throw new IllegalArgumentException("unknown product command: " + parsed.command());
            };
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS usage error: " + CliErrorLine.failureText(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS product-integrity error: " + CliErrorLine.failureText(failure));
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
        String manifest = parsed.option(OPT_MANIFEST);
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
            out.println("attestationUri=" + result.attestationUri().map(URI::toString).orElse("none"));
            out.println("trustLevel=" + result.trustLevel());
            out.println("action=none (discovery is read-only; MORPHEUS never auto-installs updates)");
        }
        return CliExitCode.SUCCESS.code();
    }

    private static Parsed parse(String[] args) {
        GlobalArgs.Parsed global = GlobalArgs.parse(args);
        List<String> remaining = global.remaining();
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("missing product command");
        }
        String command = remaining.getFirst();
        CommandOptions options = CommandOptions.parse(
                remaining.subList(1, remaining.size()), CommandOptions.Declared.values(OPT_MANIFEST));
        if (command.equals("update-check") && !options.given(OPT_MANIFEST)) {
            throw new IllegalArgumentException("update-check requires --manifest URI_OR_PATH");
        }
        if (!command.equals("update-check") && options.given(OPT_MANIFEST)) {
            throw new IllegalArgumentException(command + " does not accept --manifest");
        }
        return new Parsed(command, global.json(),
                options.asGiven(OPT_MANIFEST).map(manifest -> Map.of(OPT_MANIFEST, manifest)).orElse(Map.of()));
    }

    private static URI explicitUri(String raw) {
        String value = Objects.requireNonNull(raw, "manifest").trim();
        if (value.matches("^[A-Za-z]:[\\\\/].*")) {
            return Path.of(value).toAbsolutePath().normalize().toUri();
        }
        URI parsed = URI.create(value);
        if (parsed.isAbsolute()) {
            return parsed;
        }
        return Path.of(value).toAbsolutePath().normalize().toUri();
    }

    private record Parsed(String command, boolean json, Map<String, String> options) {
        String option(String name) {
            String value = options.get(name);
            if (value == null) {
                throw new IllegalArgumentException("missing required option --" + name);
            }
            // A blank value never gets here: CommandOptions refuses it through OptionValue as it reads the arguments.
            return value;
        }
    }

    private record VersionView(String version) {
    }
}
