package com.morpheus.cli.command;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliErrorLineTest {

    @Test
    void aFailureIsWordedByItsMessage() {
        assertEquals("--project is required", CliErrorLine.failureText(new IllegalArgumentException("--project is required")));
    }

    @Test
    void aFailureWithoutAMessageIsWordedByItsTypeName() {
        assertEquals("IllegalStateException", CliErrorLine.failureText(new IllegalStateException()));
    }

    @Test
    void aFailureWithABlankMessageIsWordedByItsTypeName() {
        assertEquals("IllegalArgumentException", CliErrorLine.failureText(new IllegalArgumentException(" \t")));
    }

    @Test
    void theTextLineCarriesTheExitCodeAndTheMessage() {
        assertEquals("MORPHEUS error [2]: --project is required" + System.lineSeparator(),
                written(err -> CliErrorLine.write(err, false, CliExitCode.USAGE, "--project is required")));
    }

    @Test
    void theJsonLineCarriesTheExitCodeItsNameAndTheMessage() {
        assertEquals("{\"exitCode\":3,\"code\":\"NOT_FOUND\",\"message\":\"project not found\"}" + System.lineSeparator(),
                written(err -> CliErrorLine.write(err, true, CliExitCode.NOT_FOUND, "project not found")));
    }

    @Test
    void aRefusalIsTheTextLineUnlessTheInvocationAsksForJson() {
        int[] code = new int[1];

        String text = written(err -> code[0] = CliErrorLine.refuse(
                new String[] {"server", "restore"}, err, CliExitCode.STATE_ERROR, new IllegalStateException("busy")));
        String json = written(err -> CliErrorLine.refuse(
                new String[] {"--json", "server", "restore"}, err, CliExitCode.STATE_ERROR, new IllegalStateException()));

        assertEquals("MORPHEUS error [4]: busy" + System.lineSeparator(), text);
        assertEquals(CliExitCode.STATE_ERROR.code(), code[0]);
        assertEquals("{\"exitCode\":4,\"code\":\"STATE_ERROR\",\"message\":\"IllegalStateException\"}"
                + System.lineSeparator(), json);
    }

    @Test
    void aCommandRefusalWithoutAMessageIsWordedByItsTypeName(@org.junit.jupiter.api.io.TempDir java.nio.file.Path data) {
        int[] code = new int[1];

        String line = written(err -> code[0] = CliCommandRun.run(
                new String[] {"--data-dir", data.toString(), "anything"},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                err,
                java.util.Map.of(),
                new java.util.Properties(),
                (tokens, run) -> {
                    throw CliFailure.usage(null);
                }));

        assertEquals("MORPHEUS error [2]: CliFailure" + System.lineSeparator(), line);
        assertEquals(CliExitCode.USAGE.code(), code[0]);
    }

    private static String written(java.util.function.Consumer<PrintStream> writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream err = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            writer.accept(err);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
