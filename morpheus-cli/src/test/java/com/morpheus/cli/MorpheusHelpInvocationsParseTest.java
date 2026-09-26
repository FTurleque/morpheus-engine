package com.morpheus.cli;

import com.morpheus.domain.identity.DomainIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every invocation the built-in help documents is one the parser accepts.
 *
 * <p>{@code morpheus help} advertised {@code provider-plugins probe} without {@code --sha256}, an option the parser
 * requires unconditionally: an operator who copied the line got a usage error. The documentation of reference was
 * right; only the embedded help was wrong, and nothing compared it to the parser.</p>
 *
 * <p>The guard reads both embedded helps as printed ({@code morpheus help} and {@code morpheus reason help}), expands
 * every usage line into the invocations it documents -- optional groups dropped, <em>every</em> branch of a command
 * alternative ({@code get|versions}) and of a parenthesised group ({@code (--project ID | --portfolio ID)}) kept,
 * placeholders filled -- and runs each one. A usage refusal (exit code 2) means the help documents an invocation the
 * parser refuses. Server launch lines are handed to their launch-option parser instead of being started. A placeholder
 * the guard cannot fill fails the test rather than skipping the line, so a new help line is covered the day it is
 * written.</p>
 *
 * <p>Exit code 2 is the parser's alone. Every invocation runs against its own empty store, so no line reads a state
 * another line wrote and the order of the help is irrelevant; a refusal about that empty state -- an entity that does
 * not exist, a policy pack that is not active in the scope, a server identity command run where no auth file exists
 * yet -- exits 3 or 4, never 2. Any usage refusal fails the guard, with no exemption list.</p>
 *
 * <p>What it does not prove. It proves that no documented invocation is refused <em>as usage</em>; an exit code other
 * than 2 counts as acceptance. So it cannot see a documented invocation that a command refuses only after resolving
 * state, when the store is empty: a command that looks an entity up before validating its options fails with 3 or 4
 * whatever the options are, and hides what the parser would have said next. Optional groups are never passed, so a
 * bracketed option the parser does not recognise stays invisible; value alternatives ({@code --role READ|WRITE|ADMIN})
 * are exercised on their first branch only, and any word that follows an option is taken for its value, so a command
 * alternative written after a boolean flag would be exercised on its first branch too.</p>
 */
class MorpheusHelpInvocationsParseTest {
    private static final String PROBE_WITHOUT_PIN =
            "  morpheus [--json] provider-plugins probe --directory PATH --plugin ID --workspace PATH";
    private static final String PACK_READS_WITH_AN_OPTIONAL_ID =
            "  morpheus [--json] policy pack list|get|versions [--id ID]";

    @TempDir
    Path tempDir;

    private int runs;

    @Test
    void everyDocumentedInvocationIsAcceptedByItsParser() throws IOException {
        Usages usages = documentedUsages(help());
        assertTrue(usages.commandBlock() > 10, "the Commands: block of the core help yielded too few usages: " + usages);
        assertTrue(usages.invocations().size() > 60, "the help yielded too few invocations to be a guard: " + usages);
        assertTrue(usages.invocations().stream().anyMatch(line -> line.contains("provider-plugins probe")),
                "the provider-plugins probe usage line is no longer found: " + usages);
        assertTrue(usages.invocations().stream().anyMatch(line -> line.startsWith("morpheus reason analyze")),
                "the reason help is no longer read: " + usages);

        List<String> refused = new ArrayList<>();
        for (String usage : usages.invocations()) {
            refusal(usage).ifPresent(reason -> refused.add(usage + "\n      -> " + reason));
        }

        assertEquals(List.of(), refused, "the help documents invocations the parser refuses");
    }

    @Test
    void theGuardRefusesTheProbeLineWithoutItsTrustedPin() throws IOException {
        List<String> usages = invocations(PROBE_WITHOUT_PIN.trim());

        assertEquals(List.of("morpheus provider-plugins probe --directory PATH --plugin ID --workspace PATH"), usages);
        assertTrue(refusal(usages.getFirst()).orElseThrow().contains("probe requires --sha256 HEX"),
                "the guard must see the refusal the help used to hide");
    }

    @Test
    void theGuardRefusesAPackReadDocumentedWithAnOptionalId() throws IOException {
        List<String> usages = invocations(PACK_READS_WITH_AN_OPTIONAL_ID.trim());

        assertEquals(List.of("morpheus policy pack list", "morpheus policy pack get", "morpheus policy pack versions"),
                usages);
        assertEquals(Optional.empty(), refusal(usages.get(0)));
        assertTrue(refusal(usages.get(1)).orElseThrow().contains("--id is required"),
                "a later branch of a command alternative must be exercised, not only the first");
    }

    @Test
    void anInvocationExpandsEveryCommandAlternativeAndKeepsTheFirstValue() {
        assertEquals(List.of("morpheus policy override list --project ID", "morpheus policy override list --portfolio ID"),
                invocations("morpheus [--json] policy override list (--project ID | --portfolio ID)"));
        assertEquals(List.of("morpheus views get --id ID", "morpheus views versions --id ID",
                        "morpheus views execute --id ID"),
                invocations("morpheus [--json] views get|versions|execute --id ID"));
        assertEquals(List.of("morpheus server identity create --principal NAME --role READ"),
                invocations("morpheus [layout] server identity create --principal NAME --role READ|WRITE|ADMIN"
                        + " [--expires-at ISO-8601] [--auth-file FILE]"));
        assertEquals(List.of("morpheus api --remote --host HOST"),
                invocations("morpheus [layout] api --remote --host HOST [--workspace-root PATH [--x Y] ...]"));
    }

    @Test
    void aBlankLineInsideTheCommandsBlockDoesNotEndIt() {
        Usages usages = documentedUsages("Commands:\n  version\n\n  paths\n\nEnvironment overrides:\n  MORPHEUS_DB\n");

        assertEquals(List.of("morpheus version", "morpheus paths"), usages.invocations());
        assertEquals(2, usages.commandBlock());
    }

    private Optional<String> refusal(String usage) throws IOException {
        List<String> args = arguments(usage);
        String command = args.getFirst();
        if (command.equals("mcp") || command.equals("api")) {
            return launchRefusal(args);
        }
        // Each invocation gets its own empty store: none reads a state another one wrote, so the order of the help
        // lines cannot change a verdict.
        List<String> invocation = new ArrayList<>(List.of("--data-dir", tempDir.resolve("data-" + ++runs).toString()));
        invocation.addAll(args);
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            exitCode = MorpheusMain.run(invocation.toArray(String[]::new), out, err, Map.of(), properties());
        }
        String message = errBytes.toString(StandardCharsets.UTF_8).trim();
        return exitCode == CliExitCode.USAGE.code()
                ? Optional.of(message)
                : Optional.empty();
    }

    private Optional<String> launchRefusal(List<String> args) {
        String[] argv = args.toArray(String[]::new);
        Map<String, String> environment = Map.of("MORPHEUS_SERVER_TLS_PASSWORD", "changeit");
        try {
            if (RemoteApiLaunchOptions.isRemoteApiCommand(argv)) {
                RemoteApiLaunchOptions.parse(argv, environment, properties());
            } else if (ApiLaunchOptions.isApiCommand(argv)) {
                ApiLaunchOptions.parse(argv, environment, properties());
            } else {
                assertTrue(McpLaunchOptions.isMcpCommand(argv), String.join(" ", args));
                McpLaunchOptions.parse(argv, environment, properties());
            }
            return Optional.empty();
        } catch (IllegalArgumentException failure) {
            return Optional.of(String.valueOf(failure.getMessage()));
        }
    }

    private List<String> arguments(String usage) throws IOException {
        List<String> tokens = List.of(usage.split(" "));
        List<String> args = new ArrayList<>();
        String option = "";
        for (String token : tokens.subList(1, tokens.size())) {
            args.add(isPlaceholder(token) ? value(option, token) : token);
            option = token.startsWith("--") ? token : "";
        }
        return args;
    }

    private static boolean isPlaceholder(String token) {
        return !token.startsWith("--") && token.equals(token.toUpperCase(Locale.ROOT))
                && token.chars().anyMatch(Character::isLetter);
    }

    private String value(String option, String placeholder) throws IOException {
        switch (option) {
            case "--entity":
                return "change";
            case "--start-type":
                return "PROJECT";
            case "--rules":
                return "new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0";
            case "--tls-keystore", "--file":
                return Files.writeString(tempDir.resolve("file-" + option.substring(2)), "x").toString();
            default:
                break;
        }
        return switch (placeholder) {
            case "ID" -> DomainIdentity.generate().toString();
            case "PATH" -> Files.createDirectories(tempDir.resolve("dir" + option)).toString();
            case "N", "PORT" -> option.equals("--port") ? "8765" : "1";
            case "HOST" -> "127.0.0.1";
            case "NAME", "TEXT", "KEY", "ID_OR_NAME" -> "value";
            case "STATE" -> "PROPOSED";
            case "HEX" -> "0".repeat(64);
            case "READ" -> "READ";
            case "DISABLE" -> "DISABLE";
            case "ISO-8601" -> "2099-01-01T00:00:00Z";
            case "URI_OR_PATH" -> Files.writeString(tempDir.resolve("stable.properties"), String.join("\n",
                    "version=99.0.0", "channel=stable", "artifactUri=https://example.invalid/morpheus-99.0.0.zip",
                    "sha256=" + "c".repeat(64), "")).toString();
            default -> throw new AssertionError("the help names a placeholder this guard cannot fill: "
                    + option + " " + placeholder);
        };
    }

    /**
     * The core help lists its commands without the {@code morpheus} prefix under {@code Commands:}; the block ends at
     * the next section header, never at a blank line, so that grouping the commands cannot make them vanish.
     */
    static Usages documentedUsages(String help) {
        List<String> invocations = new ArrayList<>();
        boolean commands = false;
        int commandBlock = 0;
        for (String raw : help.replace("\r\n", "\n").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!raw.startsWith(" ") && line.endsWith(":")) {
                commands = line.equals("Commands:");
                continue;
            }
            if (line.startsWith("morpheus ") && !line.contains("<command>")) {
                invocations.addAll(invocations(line));
            } else if (commands) {
                List<String> expanded = invocations("morpheus " + line);
                invocations.addAll(expanded);
                commandBlock += expanded.size();
            }
        }
        return new Usages(List.copyOf(invocations), commandBlock);
    }

    /** Optional groups dropped; every branch of a parenthesised group or of a command word kept; a value keeps its first. */
    static List<String> invocations(String line) {
        StringBuilder kept = new StringBuilder();
        int depth = 0;
        for (char character : line.toCharArray()) {
            if (character == '[') {
                depth++;
            } else if (character == ']') {
                depth--;
            } else if (depth == 0) {
                kept.append(character);
            }
        }
        return expandGroups(kept.toString().trim().replaceAll("\\s+", " "));
    }

    private static List<String> expandGroups(String text) {
        int open = text.indexOf('(');
        if (open < 0) {
            return expandWords(List.of(text.split(" ")), 0, "");
        }
        int close = text.indexOf(')', open);
        List<String> expanded = new ArrayList<>();
        for (String branch : text.substring(open + 1, close).split("\\|")) {
            String joined = (text.substring(0, open) + branch.trim() + text.substring(close + 1)).trim();
            expanded.addAll(expandGroups(joined.replaceAll("\\s+", " ")));
        }
        return expanded;
    }

    private static List<String> expandWords(List<String> tokens, int index, String prefix) {
        if (index == tokens.size()) {
            return List.of(prefix);
        }
        String token = tokens.get(index);
        boolean value = index > 0 && tokens.get(index - 1).startsWith("--");
        List<String> branches = value ? List.of(token.split("\\|")[0]) : List.of(token.split("\\|"));
        List<String> expanded = new ArrayList<>();
        for (String branch : branches) {
            expanded.addAll(expandWords(tokens, index + 1, prefix.isEmpty() ? branch : prefix + " " + branch));
        }
        return expanded;
    }

    private String help() {
        return printed("help") + printed("reason", "help");
    }

    private String printed(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
            assertEquals(CliExitCode.SUCCESS.code(), MorpheusMain.run(args, out, err, Map.of(), properties()));
        }
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user.home", tempDir.resolve("home").toString());
        properties.setProperty("os.name", "Linux");
        return properties;
    }

    record Usages(List<String> invocations, int commandBlock) {
    }
}
