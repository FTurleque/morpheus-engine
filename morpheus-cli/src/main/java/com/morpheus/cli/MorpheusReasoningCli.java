package com.morpheus.cli;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reasoning.ReasoningContracts;
import com.morpheus.application.reasoning.ReasoningContracts.Evidence;
import com.morpheus.application.reasoning.ReasoningContracts.EvidenceKind;
import com.morpheus.application.reasoning.ReasoningContracts.Request;
import com.morpheus.application.reasoning.ReasoningService;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only M27 CLI. Evidence and adapter selection are always explicit. */
final class MorpheusReasoningCli {
    private final ReasoningService service;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusReasoningCli() {
        this(ReasoningService.standard());
    }

    MorpheusReasoningCli(ReasoningService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    static boolean handles(String[] args) {
        return command(args).equals("reason");
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        try {
            Parsed parsed = parse(args);
            return switch (parsed.action()) {
                case "adapters" -> listAdapters(parsed.jsonOutput(), out);
                case "analyze" -> analyze(parsed, out);
                default -> throw new IllegalArgumentException("unknown reason action: " + parsed.action());
            };
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS usage error: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS assisted-reasoning error: " + safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
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
        boolean jsonOutput = false;
        String command = "";
        String action = "";
        Map<String, String> options = new LinkedHashMap<>();
        Map<String, String> parameters = new LinkedHashMap<>();
        List<String> evidence = new ArrayList<>();
        List<String> adapters = new ArrayList<>();
        List<String> tokens = new ArrayList<>(Arrays.asList(args));
        int maxClaims = ReasoningContracts.MAX_CLAIMS;

        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token.equals("--json")) {
                jsonOutput = true;
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
            if (action.isEmpty()) {
                action = token;
                continue;
            }
            int valueIndex = requireValue(tokens, index, token);
            String value = tokens.get(valueIndex);
            switch (token) {
                case "--question" -> putOnce(options, "question", value);
                case "--evidence" -> evidence.add(value);
                case "--adapter" -> adapters.add(value);
                case "--param" -> addAssignment(parameters, value, "--param");
                case "--max-claims" -> maxClaims = parseInteger(value, "--max-claims", 1, ReasoningContracts.MAX_CLAIMS);
                default -> throw new IllegalArgumentException("unknown reason option: " + token);
            }
            index = valueIndex;
        }

        if (!command.equals("reason")) {
            throw new IllegalArgumentException("expected reason command");
        }
        if (action.isEmpty()) {
            throw new IllegalArgumentException("reason requires analyze or adapters");
        }
        if (action.equals("adapters")) {
            if (!options.isEmpty() || !evidence.isEmpty() || !adapters.isEmpty() || !parameters.isEmpty()
                    || maxClaims != ReasoningContracts.MAX_CLAIMS) {
                throw new IllegalArgumentException("reason adapters does not accept analysis options");
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

    private static void putOnce(Map<String, String> options, String key, String value) {
        if (options.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("duplicate --" + key);
        }
    }

    private static int requireValue(List<String> tokens, int optionIndex, String option) {
        int valueIndex = optionIndex + 1;
        if (valueIndex >= tokens.size() || tokens.get(valueIndex).startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return valueIndex;
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

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
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
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing required option --" + name);
            }
            return value;
        }
    }
}
