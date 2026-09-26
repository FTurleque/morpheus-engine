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
 * <p>The guard reads the help as printed, reduces every usage line to its minimal form -- optional groups
 * dropped, the first of each alternative kept, placeholders filled -- and runs it. A usage refusal (exit code 2)
 * means the help documents an invocation the parser refuses. Server launch lines are handed to their launch-option
 * parser instead of being started. A placeholder the guard cannot fill fails the test rather than skipping the line,
 * so a new help line is covered the day it is written.</p>
 *
 * <p>Exit code 2 is not only the parser's: several adapters also return it when the entity an invocation names
 * does not exist in the empty store the guard runs against (an unknown policy pack, portfolio or saved view). Those
 * refusals are listed by their exact prefix in {@link #STATE_REFUSALS}; any other usage refusal fails the guard.</p>
 *
 * <p>What it does not cover: the optional groups. A bracketed option that the parser does not recognise stays
 * invisible, because the minimal invocation never passes it.</p>
 */
class MorpheusHelpInvocationsParseTest {
    private static final List<String> STATE_REFUSALS = List.of(
            "MORPHEUS error [2]: unknown policy pack: ",
            "MORPHEUS error [2]: unknown policy pack version: ",
            "MORPHEUS error [2]: policy pack is not active in scope: ",
            "MORPHEUS error [2]: policy pack must be active before adding an override: ",
            "MORPHEUS error [2]: unknown saved view: ",
            "MORPHEUS error [2]: unknown portfolio: ",
            "MORPHEUS server usage error: migration would leave no ADMIN identity active after ");
    private static final String PROBE_WITHOUT_PIN =
            "  morpheus [--json] provider-plugins probe --directory PATH --plugin ID --workspace PATH";

    @TempDir
    Path tempDir;

    @Test
    void everyDocumentedInvocationIsAcceptedByItsParser() throws IOException {
        List<String> usages = documentedUsages(help());
        assertTrue(usages.size() > 40, "the help yielded too few usage lines to be a guard: " + usages);
        assertTrue(usages.stream().anyMatch(line -> line.contains("provider-plugins probe")),
                "the provider-plugins probe usage line is no longer found: " + usages);

        List<String> refused = new ArrayList<>();
        for (String usage : usages) {
            refusal(usage).ifPresent(reason -> refused.add(usage + "\n      -> " + reason));
        }

        assertEquals(List.of(), refused, "the help documents invocations the parser refuses");
    }

    @Test
    void theGuardRefusesTheProbeLineWithoutItsTrustedPin() throws IOException {
        String usage = minimalInvocation(PROBE_WITHOUT_PIN.trim());

        assertEquals("morpheus provider-plugins probe --directory PATH --plugin ID --workspace PATH", usage);
        assertTrue(refusal(usage).orElseThrow().contains("probe requires --sha256 HEX"),
                "the guard must see the refusal the help used to hide");
    }

    @Test
    void theMinimalInvocationDropsOptionsAndKeepsTheFirstAlternative() {
        assertEquals("morpheus policy override list --project ID",
                minimalInvocation("morpheus [--json] policy override list (--project ID | --portfolio ID)"));
        assertEquals("morpheus views get --id ID", minimalInvocation("morpheus [--json] views get|versions|execute --id ID"));
        assertEquals("morpheus server identity create --principal NAME --role READ",
                minimalInvocation("morpheus [layout] server identity create --principal NAME --role READ|WRITE|ADMIN"
                        + " [--expires-at ISO-8601] [--auth-file FILE]"));
        assertEquals("morpheus api --remote --host HOST",
                minimalInvocation("morpheus [layout] api --remote --host HOST [--workspace-root PATH [--x Y] ...]"));
    }

    private Optional<String> refusal(String usage) throws IOException {
        List<String> args = arguments(usage);
        String command = args.getFirst();
        if (command.equals("mcp") || command.equals("api")) {
            return launchRefusal(args);
        }
        List<String> invocation = new ArrayList<>(List.of("--data-dir", tempDir.resolve("data").toString()));
        invocation.addAll(args);
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            exitCode = MorpheusMain.run(invocation.toArray(String[]::new), out, err, Map.of(), properties());
        }
        String message = errBytes.toString(StandardCharsets.UTF_8).trim();
        return exitCode == CliExitCode.USAGE.code() && STATE_REFUSALS.stream().noneMatch(message::startsWith)
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

    static List<String> documentedUsages(String help) {
        List<String> usages = new ArrayList<>();
        boolean commands = false;
        for (String raw : help.replace("\r\n", "\n").split("\n")) {
            String line = raw.trim();
            if (line.equals("Commands:")) {
                commands = true;
                continue;
            }
            if (line.isEmpty()) {
                commands = false;
                continue;
            }
            if (line.startsWith("morpheus ") && !line.contains("<command>")) {
                usages.add(minimalInvocation(line));
            } else if (commands) {
                usages.add(minimalInvocation("morpheus " + line));
            }
        }
        return usages;
    }

    static String minimalInvocation(String line) {
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
        String text = kept.toString();
        int open;
        while ((open = text.indexOf('(')) >= 0) {
            int close = text.indexOf(')', open);
            String group = text.substring(open + 1, close);
            text = text.substring(0, open) + group.split("\\|")[0].trim() + text.substring(close + 1);
        }
        List<String> tokens = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            tokens.add(token.split("\\|")[0]);
        }
        return String.join(" ", tokens);
    }

    private String help() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
            assertEquals(CliExitCode.SUCCESS.code(),
                    MorpheusMain.run(new String[]{"help"}, out, err, Map.of(), properties()));
        }
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user.home", tempDir.resolve("home").toString());
        properties.setProperty("os.name", "Linux");
        return properties;
    }
}
