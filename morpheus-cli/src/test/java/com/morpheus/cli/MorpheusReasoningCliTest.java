package com.morpheus.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusReasoningCliTest {

    @Test
    void factsOnlyAnalysisIsReadOnlyWhenNoAdapterIsSelected() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = new MorpheusReasoningCli().run(new String[]{
                        "--json", "reason", "analyze",
                        "--question", "What is authoritative?",
                        "--evidence", "fact-1|PUBLISHED_FACT|specification|Published history is authoritative|source=snapshot"
                }, stream(stdout), stream(stderr));

        assertEquals(CliExitCode.SUCCESS.code(), exit);
        String json = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"facts\""));
        assertTrue(json.contains("\"assisted\":false"));
        assertTrue(json.contains("\"mutated\":false"));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void explicitBuiltInAdapterProducesSeparatedClaims() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = new MorpheusReasoningCli().run(new String[]{
                        "--json", "reason", "analyze",
                        "--question", "Can remote mode be enabled safely?",
                        "--evidence", "fact-1|PUBLISHED_FACT|remote|TLS is required|source=published",
                        "--evidence", "fact-2|PUBLISHED_FACT|remote|Authentication is required|source=published",
                        "--adapter", "builtin-evidence-synthesis-v1"
                }, stream(stdout), stream(stderr));

        assertEquals(CliExitCode.SUCCESS.code(), exit);
        String json = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"inferences\""));
        assertTrue(json.contains("\"heuristics\""));
        assertTrue(json.contains("\"assisted\":true"));
        assertTrue(json.contains("\"mutated\":false"));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void unknownAdapterIsAnExplicitUsageError() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = new MorpheusReasoningCli().run(new String[]{
                        "reason", "analyze", "--question", "Question", "--adapter", "missing"
                }, stream(new ByteArrayOutputStream()), stream(stderr));

        assertEquals(CliExitCode.USAGE.code(), exit);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("unknown reasoning adapter"));
    }

    private static PrintStream stream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }
}
