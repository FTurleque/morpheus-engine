package com.morpheus.cli;

import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.cli.command.CliExitCode;
import com.morpheus.cli.launch.ApiLaunchOptions;
import com.morpheus.cli.launch.McpLaunchOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CLI serves exactly the command paths it declares, and the embedded help documents every one of them.
 *
 * <p>{@link CliCommandPaths} is the declaration the {@code cli} column of {@code contracts/public-surfaces.tsv} is
 * compared with. A declaration is only worth that comparison if it is what the CLI routes, so each declared path is
 * <em>run</em>, with no option, against an empty store: its answer may be any refusal about the options or the state,
 * but not the refusal of an unknown command or action. A path the declaration does not hold is refused, as an unknown
 * command, before any sub-command is asked.</p>
 *
 * <p><b>What it does not prove.</b> It accepts any answer that is not an "unknown" refusal, so a command that fails
 * for another reason passes; the options of a command are held by {@link MorpheusHelpInvocationsParseTest}. The
 * refusal is recognised by its wording ({@link #UNKNOWN}), so a sub-command that words its refusal differently would
 * be accepted: a new wording is added to the pattern when it is written. The launchers {@code api} and {@code mcp}
 * are handed to their launch-option parser rather than started.</p>
 */
class CliCommandPathsTest {
    /** The wordings by which a sub-command refuses a command, action or subcommand it does not route. */
    private static final Pattern UNKNOWN = Pattern.compile(
            "unknown command|unknown [a-z-]+ (action|subcommand|command)|"
                    + "server command must be|requires (an )?(action|subcommand)|export requires query or view");

    @TempDir
    Path tempDir;

    private int runs;

    @Test
    void everyDeclaredPathIsRoutedToABehaviour() {
        List<String> unrouted = new ArrayList<>();
        for (String path : CliCommandPaths.paths()) {
            Run run = run(List.of(path.split(" ")));
            if (UNKNOWN.matcher(run.err()).find()) {
                unrouted.add(path + " -> " + run.err().trim());
            }
        }

        assertEquals(List.of(), unrouted, "a declared path that no sub-command routes");
    }

    @Test
    void theDeclarationIsNotEmptyAndHasNoDuplicate() {
        List<String> paths = CliCommandPaths.paths();

        assertTrue(paths.size() > 50, "the declaration holds too few paths to be a guard: " + paths.size());
        assertEquals(paths.size(), new TreeSet<>(paths).size(), "a path is declared twice");
    }

    @Test
    void anUndeclaredPathIsRefusedAsAnUnknownCommandWhateverItsFamily() {
        List<String> accepted = new ArrayList<>();
        for (String family : families()) {
            Run run = run(List.of(family, "frobnicate"));
            if (run.exitCode() != CliExitCode.USAGE.code() || !run.err().contains("unknown command: " + family + " frobnicate")) {
                accepted.add(family + " frobnicate -> " + run.exitCode() + " " + run.err().trim());
            }
        }

        assertEquals(List.of(), accepted, "a path outside the declaration must be refused before its command is asked");
    }

    @Test
    void anUnknownFirstWordAndAnUnknownOptionAreRefusedAsUnknownCommands() {
        Run word = run(List.of("frobnicate"));
        Run option = run(List.of("--frobnicate"));
        Run nested = run(List.of("policy", "pack", "frobnicate"));

        assertEquals(CliExitCode.USAGE.code(), word.exitCode());
        assertTrue(word.err().contains("unknown command: frobnicate"), word.err());
        assertEquals(CliExitCode.USAGE.code(), option.exitCode());
        assertTrue(option.err().contains("unknown command: --frobnicate"), option.err());
        assertEquals(CliExitCode.USAGE.code(), nested.exitCode());
        assertTrue(nested.err().contains("unknown command: policy pack frobnicate"), nested.err());
    }

    @Test
    void aPathThatStopsShortOfADeclaredOneIsLeftToItsCommandToExplain() {
        for (List<String> incomplete : List.<List<String>>of(
                List.of("portfolio"), List.of("policy", "pack"), List.of("server", "backup"), List.of("export"))) {
            assertEquals(java.util.Optional.empty(), CliCommandPaths.refusalOf(incomplete.toArray(String[]::new)),
                    String.join(" ", incomplete));
        }
    }

    @Test
    void theGlobalOptionsAndTheHelpFormsDoNotChangeThePath() {
        for (List<String> declared : List.<List<String>>of(
                List.of("--json", "portfolio", "list"),
                List.of("--data-dir", "x", "--db", "y", "portfolio", "list", "--json"),
                List.of("--data-dir=x", "portfolio", "list"),
                List.of("--help"),
                List.of("-h"),
                List.of("--version"),
                List.of("--json"),
                List.of())) {
            assertEquals(java.util.Optional.empty(), CliCommandPaths.refusalOf(declared.toArray(String[]::new)),
                    String.join(" ", declared));
        }
    }

    @Test
    void everyDeclaredPathIsInAUsageLineOfTheEmbeddedHelp() {
        List<String> documented = MorpheusHelpInvocationsParseTest.documentedUsages(help()).invocations();
        assertTrue(documented.size() > 60, "the help yielded too few invocations to be a guard: " + documented.size());

        List<String> missing = new ArrayList<>();
        for (String path : CliCommandPaths.paths()) {
            if (path.equals("help") || path.equals("reason help")) {
                continue;
            }
            String usage = "morpheus " + path;
            if (documented.stream().noneMatch(line -> line.equals(usage) || line.startsWith(usage + " "))) {
                missing.add(path);
            }
        }

        assertEquals(List.of(), missing, "declared command paths that no usage line of the help documents");
    }

    @Test
    void aPathTheHelpDoesNotDocumentIsFoundMissing() {
        List<String> documented = MorpheusHelpInvocationsParseTest.documentedUsages(
                "Commands:\n  version\n  portfolio create --name NAME\n").invocations();

        assertEquals(List.of("morpheus version", "morpheus portfolio create --name NAME"), documented);
        assertFalse(documented.stream().anyMatch(line -> line.startsWith("morpheus portfolio members")));
    }

    // ---------------------------------------------------------------------------------------------

    /** The first word of every declared path that has more than one word: the commands that take an action. */
    private static List<String> families() {
        return CliCommandPaths.paths().stream()
                .filter(path -> path.contains(" "))
                .map(path -> path.substring(0, path.indexOf(' ')))
                .distinct()
                .toList();
    }

    private Run run(List<String> words) {
        if (words.getFirst().equals("api") || words.getFirst().equals("mcp")) {
            return launch(words);
        }
        List<String> invocation = new ArrayList<>(List.of("--data-dir", tempDir.resolve("data-" + ++runs).toString()));
        invocation.addAll(words);
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            exitCode = MorpheusMain.run(
                    OperationalRuntime.of(OperationalLogMode.OFF), invocation.toArray(String[]::new), out, err, Map.of(),
                    properties());
        }
        return new Run(exitCode, errBytes.toString(StandardCharsets.UTF_8));
    }

    private Run launch(List<String> words) {
        String[] argv = words.toArray(String[]::new);
        boolean routed = ApiLaunchOptions.isApiCommand(argv) || McpLaunchOptions.isMcpCommand(argv);
        return new Run(routed ? 0 : CliExitCode.USAGE.code(), routed ? "" : "unknown command: " + words.getFirst());
    }

    private String help() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
            assertEquals(CliExitCode.SUCCESS.code(), MorpheusMain.run(
                    OperationalRuntime.of(OperationalLogMode.OFF), new String[] {"help"}, out, err, Map.of(), properties()));
            assertEquals(CliExitCode.SUCCESS.code(), MorpheusMain.run(
                    OperationalRuntime.of(OperationalLogMode.OFF), new String[] {"reason", "help"}, out, err, Map.of(),
                    properties()));
        }
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user.home", tempDir.resolve("home").toString());
        properties.setProperty("os.name", "Linux");
        return properties;
    }

    private record Run(int exitCode, String err) {
    }
}
