package com.morpheus.integration.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.TreeSet;

/**
 * A peer that records the names of the environment variables it was actually given, then waits.
 *
 * <p>What a child process inherits cannot be read from inside MORPHEUS without re-deriving it from the same code
 * under test. The peer is the only witness that does not share that code, so it writes what it received. The record
 * is published with an atomic move, so the test never reads a half-written list.</p>
 */
public final class FixtureEnvironmentRecordingMcpPeer {

    private FixtureEnvironmentRecordingMcpPeer() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) throw new IllegalArgumentException("environment record path is required");
        Path record = Path.of(args[0]);
        Path pending = record.resolveSibling(record.getFileName() + ".pending");
        Files.write(pending, String.join("\n", new TreeSet<>(System.getenv().keySet())).getBytes(StandardCharsets.UTF_8));
        Files.move(pending, record, StandardCopyOption.ATOMIC_MOVE);
        try (InputStream input = System.in) {
            while (input.read() != -1) {
                // The record is the whole purpose; the peer only has to stay alive until the transport closes it.
            }
        }
    }
}
