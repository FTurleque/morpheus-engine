package com.morpheus.cli.command;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.operability.SuppressedFailureFamily;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reasoning.ReasoningContracts.Evidence;
import com.morpheus.application.reasoning.ReasoningContracts.EvidenceKind;
import com.morpheus.application.reasoning.ReasoningContracts.Request;
import com.morpheus.application.reasoning.ReasoningContracts;
import com.morpheus.application.reasoning.ReasoningService;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only M27 CLI. Evidence and adapter selection are always explicit. */
final class MorpheusReasoningCli {
    private static final String OPT_QUESTION = "question";
    private static final String OPT_EVIDENCE = "evidence";
    private static final String OPT_ADAPTER = "adapter";
    private static final String OPT_PARAM = "param";
    private static final String OPT_MAX_CLAIMS = "max-claims";
    private final ReasoningService service;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusReasoningCli(OperationalRecorder recorder) {
        this(ReasoningService.standard(recorder.suppressing(SuppressedFailureFamily.ADAPTER_DISCOVERY)));
    }

    MorpheusReasoningCli(ReasoningService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    static boolean handles(String[] args) {
        return GlobalArgs.command(args).equals("reason");
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        try {
            Parsed parsed = parse(args);
            return switch (parsed.action()) {
                case "help", "--help" -> help(out);
                case "adapters" -> listAdapters(parsed.jsonOutput(), out);
                case "analyze" -> analyze(parsed, out);
                default -> throw new IllegalArgumentException("unknown reason action: " + parsed.action());
            };
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS usage error: " + CliErrorLine.failureText(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS assisted-reasoning error: " + CliErrorLine.failureText(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private int help(PrintStream out) {
        out.println("Evidence-backed assisted reasoning (M27, read-only):");
        out.println("  morpheus [--json] reason adapters");
        out.println("  morpheus [--json] reason analyze --question TEXT [--evidence SPEC] [--adapter ID] [--param k=v] [--max-claims N]");
        out.println("  evidence SPEC = id|kind|subject|statement[|key=value,key=value]");
        out.println("  kinds = PUBLISHED_FACT|SOURCE_EXCERPT|POLICY_RESULT|EXTERNAL_CONTEXT|OBSERVATION");
        out.println("  No --adapter means facts-only. Reasoning never mutates or promotes specification truth.");
        return CliExitCode.SUCCESS.code();
    }

    private int listAdapters(boolean jsonOutput, PrintStream out) {
        var adapters = service.adapters();
        if (jsonOutput) {
            out.println(json.toJson(adapters));
        } else if (adapters.isEmpty()) {
            out.println("No optional reasoning adapters are available.");
        } else {
            adapters.forEach(adapter -> out.println(adapter.id() + "\t" + adapter.description()));
        }
        return CliExitCode.SUCCESS.code();
    }

    private int analyze(Parsed parsed, PrintStream out) {
        List<Evidence> evidence = parsed.evidence().stream().map(MorpheusReasoningCli::parseEvidence).toList();
        var result = service.execute(new Request(
                parsed.required("question"),
                evidence,
                parsed.adapters(),
                parsed.parameters(),
                parsed.maxClaims()));
        if (parsed.jsonOutput()) {
            out.println(json.toJson(result));
        } else {
            out.println("question=" + result.question());
            out.println("evidence=" + result.evidence().size());
            out.println("facts=" + result.facts().size());
            out.println("inferences=" + result.inferences().size());
            out.println("heuristics=" + result.heuristics().size());
            out.println("suggestions=" + result.suggestions().size());
            out.println("assisted=" + result.assisted());
            out.println("mutated=" + result.mutated());
            result.executions().forEach(execution -> out.println(
                    "adapter=" + execution.adapterId()
                            + " status=" + execution.status()
                            + " acceptedClaims=" + execution.acceptedClaims()
                            + (execution.message().isBlank() ? "" : " message=" + execution.message())));
        }
        return CliExitCode.SUCCESS.code();
    }

    private static Evidence parseEvidence(String raw) {
        String[] parts = Objects.requireNonNull(raw, "evidence").split("\\|", -1);
        if (parts.length < 4 || parts.length > 5) {
            throw new IllegalArgumentException(
                    "--evidence requires id|kind|subject|statement[|key=value,key=value]");
        }
        Map<String, String> provenance = parts.length == 5 ? parseAssignments(parts[4]) : Map.of();
        return new Evidence(
                parts[0],
                enumValue(EvidenceKind.class, parts[1], "evidence kind"),
                parts[2],
                parts[3],
                provenance);
    }

    private static Parsed parse(String[] args) {
        GlobalArgs.Parsed global = GlobalArgs.parse(args);
        List<String> remaining = global.remaining();
        String command = remaining.isEmpty() ? "" : remaining.getFirst();
        String action = remaining.size() < 2 ? "" : remaining.get(1);
        CommandOptions given = CommandOptions.parse(remaining.subList(Math.min(2, remaining.size()), remaining.size()),
                CommandOptions.Declared.values(OPT_QUESTION, OPT_MAX_CLAIMS)
                        .andRepeated(OPT_EVIDENCE, OPT_ADAPTER, OPT_PARAM));
        boolean jsonOutput = global.json();
        Map<String, String> options = new LinkedHashMap<>();
        given.asGiven(OPT_QUESTION).ifPresent(question -> options.put(OPT_QUESTION, question));
        List<String> evidence = given.all(OPT_EVIDENCE);
        List<String> adapters = given.all(OPT_ADAPTER);
        Map<String, String> parameters = new LinkedHashMap<>();
        given.all(OPT_PARAM).forEach(parameter -> addAssignment(parameters, parameter, "--param"));
        int maxClaims = given.asGiven(OPT_MAX_CLAIMS)
                .map(raw -> parseInteger(raw, "--max-claims", 1, ReasoningContracts.MAX_CLAIMS))
                .orElse(ReasoningContracts.MAX_CLAIMS);

        if (!command.equals("reason")) {
            throw new IllegalArgumentException("expected reason command");
        }
        if (action.isEmpty()) {
            throw new IllegalArgumentException("reason requires help, analyze or adapters");
        }
        if (action.equals("adapters") || action.equals("help") || action.equals("--help")) {
            if (!options.isEmpty() || !evidence.isEmpty() || !adapters.isEmpty() || !parameters.isEmpty()
                    || maxClaims != ReasoningContracts.MAX_CLAIMS) {
                throw new IllegalArgumentException("reason " + action + " does not accept analysis options");
            }
        } else if (!action.equals("analyze")) {
            throw new IllegalArgumentException("unknown reason action: " + action);
        }
        if (action.equals("analyze") && !options.containsKey("question")) {
            throw new IllegalArgumentException("reason analyze requires --question TEXT");
        }
        return new Parsed(
                action,
                jsonOutput,
                Map.copyOf(options),
                List.copyOf(evidence),
                List.copyOf(adapters),
                Map.copyOf(parameters),
                maxClaims);
    }

    private static Map<String, String> parseAssignments(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String assignment : raw.split(",")) {
            addAssignment(result, assignment, "provenance");
        }
        return Map.copyOf(result);
    }

    private static void addAssignment(Map<String, String> target, String raw, String option) {
        int separator = raw.indexOf('=');
        if (separator <= 0 || separator == raw.length() - 1) {
            throw new IllegalArgumentException(option + " requires key=value");
        }
        String key = raw.substring(0, separator).trim();
        String value = raw.substring(separator + 1).trim();
        if (key.isEmpty() || value.isEmpty()) {
            throw new IllegalArgumentException(option + " requires non-blank key=value");
        }
        if (target.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("duplicate " + option + " key: " + key);
        }
    }

    private static int parseInteger(String raw, String option, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(raw);
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(option + " must be between " + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(option + " requires an integer", failure);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String name) {
        try {
            return Enum.valueOf(type, Objects.requireNonNull(raw, name).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid " + name + ": " + raw, failure);
        }
    }

    private record Parsed(
            String action,
            boolean jsonOutput,
            Map<String, String> options,
            List<String> evidence,
            List<String> adapters,
            Map<String, String> parameters,
            int maxClaims) {
        private Parsed {
            Objects.requireNonNull(action, "action");
            options = Map.copyOf(options);
            evidence = List.copyOf(evidence);
            adapters = List.copyOf(adapters);
            parameters = Map.copyOf(parameters);
        }

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
