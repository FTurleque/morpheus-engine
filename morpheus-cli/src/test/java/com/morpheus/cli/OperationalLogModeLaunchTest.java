package com.morpheus.cli;

import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.cli.command.CliExitCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An operational log mode the product does not know is refused by the launcher, before anything else runs.
 *
 * <p>The mode is read by a static initializer of the operational runtime. Refused there, it would surface as an
 * initialization error thrown from whichever class touched the runtime first; left to that initializer's old default,
 * it silently meant "no logs". The launcher reads it first, in a real process here, because the variable is the
 * process's and cannot be set from inside a test.</p>
 */
class OperationalLogModeLaunchTest {
    @Test
    void theLauncherNamesAnUnknownModeAndAcceptsTheKnownOnes() {
        Optional<String> refused = MorpheusMain.refusedOperationalLogMode(
                Map.of(OperationalLogMode.ENVIRONMENT_VARIABLE, "jsno"));

        assertTrue(refused.orElseThrow().startsWith(OperationalLogMode.REFUSAL), refused::toString);
        assertEquals(Optional.empty(), MorpheusMain.refusedOperationalLogMode(Map.of()));
        assertEquals(Optional.empty(), MorpheusMain.refusedOperationalLogMode(
                Map.of(OperationalLogMode.ENVIRONMENT_VARIABLE, " JSONL ")));
    }

    @Test
    void aProcessStartedWithAnUnknownModeExitsWithTheUsageCodeAndOneNamedLine() throws Exception {
        Outcome outcome = launch("jsno", "version");

        assertEquals(CliExitCode.USAGE.code(), outcome.exitCode(), outcome::toString);
        List<String> lines = outcome.standardError().lines().filter(line -> !line.isBlank()).toList();
        assertEquals(1, lines.size(), outcome::toString);
        assertTrue(lines.get(0).startsWith("MORPHEUS usage error: " + OperationalLogMode.REFUSAL), outcome::toString);
        assertFalse(outcome.standardError().contains("ExceptionInInitializerError"), outcome::toString);
        assertEquals("", outcome.standardOutput().strip(), "nothing may run before the refusal");
    }

    @Test
    void aProcessStartedWithAKnownModeOrNoneRuns() throws Exception {
        assertEquals(0, launch("jsonl", "version").exitCode());
        assertEquals(0, launch(null, "version").exitCode());
    }

    private static Outcome launch(String mode, String... arguments) throws Exception {
        Path launcher = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new java.util.ArrayList<>(
                List.of(launcher.toString(), "-cp", classpath, MorpheusMain.class.getName()));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        if (mode == null) {
            builder.environment().remove(OperationalLogMode.ENVIRONMENT_VARIABLE);
        } else {
            builder.environment().put(OperationalLogMode.ENVIRONMENT_VARIABLE, mode);
        }
        Process process = builder.start();
        process.getOutputStream().close();
        String standardOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String standardError = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the launched process did not end");
        return new Outcome(process.exitValue(), standardOutput, standardError);
    }

    private record Outcome(int exitCode, String standardOutput, String standardError) {
    }
}
