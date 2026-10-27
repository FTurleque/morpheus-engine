package com.morpheus.cli.command;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.sdk.provider.ProviderPluginCandidate;
import com.morpheus.sdk.provider.ProviderPluginDiscoveryResult;
import com.morpheus.sdk.provider.ProviderPluginProbeOutcome;
import com.morpheus.sdk.provider.ProviderPluginService;
import com.morpheus.sdk.provider.ProviderPluginViews;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Explicit M22 provider-plugin discovery/probe commands. No plugin directory is scanned at startup. */
public final class MorpheusProviderPluginCli {
    private static final List<String> OPTIONS = List.of("directory", "plugin", "workspace", "sha256");
    private final ProviderPluginService service;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    public MorpheusProviderPluginCli(OperationalRecorder recorder) {
        this.service = new ProviderPluginService(recorder);
    }

    public static boolean handles(String[] args) {
        return GlobalArgs.command(args).equals("provider-plugins");
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        try {
            Parsed parsed = parse(args);
            return switch (parsed.action()) {
                case "discover" -> discover(parsed, out);
                case "probe" -> probe(parsed, out);
                default -> throw new IllegalArgumentException("unknown provider-plugins action: " + parsed.action());
            };
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS provider-plugin usage error: " + CliErrorLine.failureText(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS provider-plugin error: " + CliErrorLine.failureText(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private int discover(Parsed parsed, PrintStream out) {
        ProviderPluginDiscoveryResult result = service.discover(Path.of(parsed.required("directory")));
        if (parsed.json()) {
            out.println(json.toJson(ProviderPluginViews.discovery(result)));
        } else {
            out.println("directory=" + result.directory());
            out.println("compatible=" + result.compatibleCount());
            for (ProviderPluginCandidate candidate : result.candidates()) {
                String pluginId = candidate.metadata().map(metadata -> metadata.pluginId()).orElse("<invalid>");
                String providerId = candidate.metadata().map(metadata -> metadata.providerId().value()).orElse("<invalid>");
                out.println("plugin=" + pluginId
                        + " provider=" + providerId
                        + " status=" + candidate.status()
                        + " jar=" + candidate.jarPath());
                candidate.diagnostics().forEach(diagnostic ->
                        out.println("  diagnostic=" + diagnostic.severity() + ":" + diagnostic.code() + ":" + diagnostic.message()));
            }
            result.diagnostics().forEach(diagnostic ->
                    out.println("diagnostic=" + diagnostic.severity() + ":" + diagnostic.code() + ":" + diagnostic.message()));
        }
        return CliExitCode.SUCCESS.code();
    }

    private int probe(Parsed parsed, PrintStream out) {
        Path directory = Path.of(parsed.required("directory"));
        String plugin = parsed.required("plugin");
        Path workspace = Path.of(parsed.required("workspace"));
        ProviderPluginProbeOutcome outcome = service.probe(
                directory,
                plugin,
                workspace,
                parsed.required("sha256"));
        if (parsed.json()) {
            out.println(json.toJson(outcome));
        } else {
            out.println("pluginId=" + outcome.pluginId());
            out.println("jarPath=" + outcome.jarPath());
            out.println("success=" + outcome.success());
            outcome.probe().ifPresent(probe -> {
                out.println("providerId=" + probe.providerId());
                out.println("providerVersion=" + probe.providerVersion());
                out.println("status=" + probe.status());
                out.println("capabilities=" + probe.capabilities().values());
            });
            outcome.diagnostics().forEach(diagnostic ->
                    out.println("diagnostic=" + diagnostic.severity() + ":" + diagnostic.code() + ":" + diagnostic.message()));
        }
        return outcome.success() ? CliExitCode.SUCCESS.code() : CliExitCode.STATE_ERROR.code();
    }

    private static Parsed parse(String[] args) {
        GlobalArgs.Parsed global = GlobalArgs.parse(args);
        List<String> remaining = global.remaining();
        String command = remaining.isEmpty() ? "" : remaining.getFirst();
        String action = remaining.size() < 2 ? "" : remaining.get(1);
        CommandOptions given = CommandOptions.parse(remaining.subList(Math.min(2, remaining.size()), remaining.size()),
                CommandOptions.Declared.values(Set.copyOf(OPTIONS)));
        Map<String, String> options = new LinkedHashMap<>();
        OPTIONS.forEach(option -> given.asGiven(option).ifPresent(value -> options.put(option, value)));
        if (!command.equals("provider-plugins")) {
            throw new IllegalArgumentException("missing provider-plugins command");
        }
        if (!action.equals("discover") && !action.equals("probe")) {
            throw new IllegalArgumentException("provider-plugins action must be discover or probe");
        }
        if (!options.containsKey("directory")) {
            throw new IllegalArgumentException(action + " requires --directory PATH");
        }
        if (action.equals("probe")
                && (!options.containsKey("plugin") || !options.containsKey("workspace"))) {
            throw new IllegalArgumentException("probe requires --plugin ID and --workspace PATH");
        }
        if (action.equals("probe") && !options.containsKey("sha256")) {
            throw new IllegalArgumentException("probe requires --sha256 HEX");
        }
        if (action.equals("discover")
                && (options.containsKey("plugin") || options.containsKey("workspace") || options.containsKey("sha256"))) {
            throw new IllegalArgumentException("discover accepts only --directory PATH");
        }
        return new Parsed(global.json(), action, Map.copyOf(options));
    }

    private record Parsed(boolean json, String action, Map<String, String> options) {
        String required(String name) {
            String value = options.get(name);
            if (value == null) {
                throw new IllegalArgumentException("missing required option --" + name);
            }
            // A blank value never gets here: CommandOptions refuses it through OptionValue as it reads the arguments.
            return value;
        }
    }
}
