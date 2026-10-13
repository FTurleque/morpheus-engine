package com.morpheus.cli;

import com.morpheus.application.operability.OperationalLogFileException;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.RotatingOperationalLogFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asked for a file, the operational log is written in the logs directory the product publishes, or the launcher says
 * why it cannot be.
 *
 * <p>The directory is resolved by the launcher, before anything else runs: the application module that owns the
 * operational runtime does not know the layout of the process.</p>
 */
class OperationalLogFileLaunchTest {
    @TempDir
    Path temporaryDirectory;

    private final List<Path> named = new ArrayList<>();

    @Test
    void noDirectoryIsNamedWhenTheModeWritesNoFile() {
        for (Map<String, String> environment : List.of(
                Map.<String, String>of(),
                Map.of(OperationalLogMode.ENVIRONMENT_VARIABLE, "off"),
                Map.of(OperationalLogMode.ENVIRONMENT_VARIABLE, "json"))) {
            assertEquals(Optional.empty(), MorpheusMain.refusedOperationalLogFile(
                    new String[] {"version"}, environment, System.getProperties(), named::add));
        }

        assertEquals(List.of(), named);
    }

    @Test
    void theDirectoryNamedIsTheOneThePathsCommandPrintsForTheSameArguments() {
        Path data = temporaryDirectory.resolve("data");
        Path elsewhere = temporaryDirectory.resolve("elsewhere");
        Properties properties = System.getProperties();

        for (Launch launch : List.of(
                new Launch(new String[] {"paths"}, Map.of("MORPHEUS_DATA_DIR", data.toString())),
                new Launch(new String[] {"--data-dir", data.toString(), "paths"}, Map.of()),
                new Launch(new String[] {"--data-dir", data.toString(), "paths"},
                        Map.of("MORPHEUS_LOGS_DIR", elsewhere.toString())))) {
            named.clear();
            Map<String, String> environment = new java.util.HashMap<>(launch.environment());
            environment.put(OperationalLogMode.ENVIRONMENT_VARIABLE, "file");

            assertEquals(Optional.empty(), MorpheusMain.refusedOperationalLogFile(
                    launch.arguments(), environment, properties, named::add));

            assertEquals(List.of(printedLogsDirectory(launch.arguments(), environment, properties)), named);
        }
        assertEquals(List.of(elsewhere), named);
    }

    @Test
    void aFileThatCannotBeWrittenIsTheRefusalOfTheLauncher() {
        Optional<String> refused = MorpheusMain.refusedOperationalLogFile(
                new String[] {"version"},
                Map.of(OperationalLogMode.ENVIRONMENT_VARIABLE, "file",
                        "MORPHEUS_LOGS_DIR", temporaryDirectory.toString()),
                System.getProperties(),
                directory -> {
                    throw new OperationalLogFileException(RotatingOperationalLogFile.REFUSAL + ": no", null);
                });

        assertEquals(Optional.of(RotatingOperationalLogFile.REFUSAL + ": no"), refused);
    }

    @Test
    void aProcessAskedForAFileRunsAndLeavesItInTheLogsDirectory() throws Exception {
        Path logs = temporaryDirectory.resolve("logs");

        Outcome outcome = launch(MorpheusMain.class, logs, "version");

        assertEquals(0, outcome.exitCode(), outcome::toString);
        assertEquals("", outcome.standardError(), outcome::toString);
        assertFalse(outcome.standardOutput().isBlank(), outcome::toString);
        assertTrue(Files.isRegularFile(logs.resolve(RotatingOperationalLogFile.FILE_NAME)));
    }

    @Test
    void whatAnEmbeddedLibraryLogsGoesToTheFileAndNotToStandardError() throws Exception {
        Path logs = temporaryDirectory.resolve("logs");

        Outcome outcome = launch(FixtureThirdPartyLogProcess.class, logs, logs.toString());

        assertEquals(0, outcome.exitCode(), outcome::toString);
        assertEquals("", outcome.standardError(), outcome::toString);
        assertEquals("", outcome.standardOutput(), outcome::toString);
        List<String> lines = Files.readAllLines(logs.resolve(RotatingOperationalLogFile.FILE_NAME));
        assertEquals(1, lines.size(), lines::toString);
        assertTrue(lines.get(0).contains("\"THIRD_PARTY_LOG\"")
                && lines.get(0).contains("handshake with peer failed"), lines::toString);
    }

    @Test
    void aProcessWhoseLogsDirectoryCannotBeOneStopsWithOneNamedLineBeforeAnythingRuns() throws Exception {
        Path occupied = Files.writeString(temporaryDirectory.resolve("logs"), "a file where the directory should be");

        Outcome outcome = launch(MorpheusMain.class, occupied, "version");

        assertEquals(CliExitCode.IO_ERROR.code(), outcome.exitCode(), outcome::toString);
        List<String> lines = outcome.standardError().lines().filter(line -> !line.isBlank()).toList();
        assertEquals(1, lines.size(), outcome::toString);
        assertTrue(lines.get(0).startsWith("MORPHEUS error: " + RotatingOperationalLogFile.REFUSAL + ": "),
                outcome::toString);
        assertEquals("", outcome.standardOutput().strip(), "nothing may run before the refusal");
    }

    private static Path printedLogsDirectory(String[] arguments, Map<String, String> environment, Properties properties) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        int exitCode = MorpheusMain.run(
                arguments,
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                environment,
                properties);
        assertEquals(0, exitCode, printed::toString);
        return printed.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.startsWith("logs="))
                .map(line -> Path.of(line.substring("logs=".length())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the paths command printed no logs directory: " + printed));
    }

    private static Outcome launch(Class<?> main, Path logsDirectory, String... arguments) throws Exception {
        Path launcher = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>(List.of(launcher.toString(), "-cp", classpath, main.getName()));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put(OperationalLogMode.ENVIRONMENT_VARIABLE, "file");
        builder.environment().put("MORPHEUS_LOGS_DIR", logsDirectory.toString());
        Process process = builder.start();
        process.getOutputStream().close();
        String standardOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String standardError = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the launched process did not end");
        return new Outcome(process.exitValue(), standardOutput, standardError);
    }

    private record Launch(String[] arguments, Map<String, String> environment) {
    }

    private record Outcome(int exitCode, String standardOutput, String standardError) {
    }
}
