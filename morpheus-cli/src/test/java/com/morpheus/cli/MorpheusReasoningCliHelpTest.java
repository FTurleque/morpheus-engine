package com.morpheus.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusReasoningCliHelpTest {

    @Test
    void dedicatedHelpDocumentsFactsOnlyAndExplicitAdapterSelection() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exit = new MorpheusReasoningCli().run(
                new String[]{"reason", "--help"},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertEquals(CliExitCode.SUCCESS.code(), exit);
        String help = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(help.contains("reason adapters"));
        assertTrue(help.contains("reason analyze"));
        assertTrue(help.contains("No --adapter means facts-only"));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }
}
