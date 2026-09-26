package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pins that a supervisor can tell a clean end of input from a session the transport failed closed. Both used to
 * return 0.
 */
class MorpheusMcpServerExitCodeTest {

    @TempDir
    Path tempDirectory;

    @Test
    void theServerStillReportsZeroAfterACleanEndOfInput() {
        int exitCode = MorpheusMcpServer.run(
                tempDirectory.resolve("clean-eof.db"),
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream());

        assertEquals(MorpheusMcpServer.EXIT_END_OF_INPUT, exitCode);
    }

    @Test
    void theServerReportsANonZeroExitCodeAfterATransportFailure() {
        int exitCode = MorpheusMcpServer.run(
                tempDirectory.resolve("transport-failure.db"),
                new ByteArrayInputStream("this is not a JSON-RPC frame\n".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream());

        assertNotEquals(MorpheusMcpServer.EXIT_END_OF_INPUT, MorpheusMcpServer.EXIT_TRANSPORT_FAILURE);
        assertEquals(
                MorpheusMcpServer.EXIT_TRANSPORT_FAILURE,
                exitCode,
                "a session the transport failed closed must not report a clean exit");
    }
}
