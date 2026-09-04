package com.morpheus.integration.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A peer that records the fact it was launched, then waits.
 *
 * <p>Asserting "exactly one peer was started" cannot be done from inside MORPHEUS: a transport that started two
 * peers and kept the second reference looks, from the inside, exactly like a transport that started one. The
 * evidence has to come from the peers themselves, so each launch appends its PID to a file the test counts.</p>
 */
public final class FixtureLaunchRecordingMcpPeer {

    private FixtureLaunchRecordingMcpPeer() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) throw new IllegalArgumentException("launch record path is required");
        Path launches = Path.of(args[0]);
        synchronizedAppend(launches, ProcessHandle.current().pid() + System.lineSeparator());
        try (InputStream input = System.in) {
            while (input.read() != -1) {
                // A peer that never answers is enough: these tests exercise the transport lifecycle, not MCP.
            }
        }
    }

    /** Appends atomically, so two peers racing to record themselves both end up in the file. */
    private static void synchronizedAppend(Path launches, String line) throws IOException {
        Files.write(
                launches,
                line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
    }
}
